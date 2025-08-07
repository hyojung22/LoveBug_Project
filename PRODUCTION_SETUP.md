# 🚀 LoveBug 프로젝트 프로덕션 배포 가이드

Room → Supabase 마이그레이션 후 안전한 프로덕션 배포를 위한 종합 가이드입니다.

## 📋 배포 전 체크리스트

### 🔒 1. 보안 구성 (필수)

#### API 키 보안 설정
```bash
# gradle.properties 파일 생성 (반드시 .gitignore에 포함)
echo "SUPABASE_URL=https://your-project-id.supabase.co" > gradle.properties
echo "SUPABASE_ANON_KEY=your-actual-anon-key" >> gradle.properties
```

#### Row Level Security (RLS) 정책 설정
Supabase 대시보드에서 다음 SQL 실행:

```sql
-- 1. Posts 테이블 RLS 활성화
ALTER TABLE posts ENABLE ROW LEVEL SECURITY;

-- 2. 사용자는 자신의 게시글만 관리 가능
CREATE POLICY "Users can manage their own posts" ON posts
FOR ALL USING (auth.uid() = user_id);

-- 3. 모든 사용자는 게시글 읽기 가능
CREATE POLICY "Anyone can view posts" ON posts
FOR SELECT USING (true);

-- 4. Expenses 테이블 RLS
ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage their own expenses" ON expenses
FOR ALL USING (auth.uid() = user_id);

-- 5. Chat 관련 테이블 RLS
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can access their chats" ON chats
FOR ALL USING (
  auth.uid() = user1_id OR auth.uid() = user2_id
);

ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can access messages in their chats" ON chat_messages
FOR ALL USING (
  EXISTS (
    SELECT 1 FROM chats 
    WHERE chats.id = chat_messages.chat_id 
    AND (chats.user1_id = auth.uid() OR chats.user2_id = auth.uid())
  )
);
```

#### 네트워크 보안 설정
`app/src/main/res/xml/network_security_config.xml` 생성:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">supabase.co</domain>
    </domain-config>
</network-security-config>
```

`AndroidManifest.xml`에 추가:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### 🗄️ 2. 데이터베이스 스키마 설정

Supabase 대시보드에서 다음 테이블 생성:

```sql
-- Posts 테이블
CREATE TABLE posts (
    post_id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    image_url TEXT,
    image_path TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
CREATE INDEX idx_posts_title ON posts USING gin(to_tsvector('english', title));

-- Expenses 테이블
CREATE TABLE expenses (
    expense_id SERIAL PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    expense_date DATE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_expenses_user_id ON expenses(user_id);
CREATE INDEX idx_expenses_date ON expenses(expense_date DESC);

-- User profiles 테이블
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username VARCHAR(50) UNIQUE NOT NULL,
    nickname VARCHAR(100),
    profile_image TEXT,
    shared_saving_stats BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Comments 테이블
CREATE TABLE comments (
    comment_id SERIAL PRIMARY KEY,
    post_id INTEGER REFERENCES posts(post_id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Likes 테이블
CREATE TABLE likes (
    like_id SERIAL PRIMARY KEY,
    post_id INTEGER REFERENCES posts(post_id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(post_id, user_id)
);

-- Chats 테이블
CREATE TABLE chats (
    id SERIAL PRIMARY KEY,
    user1_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    user2_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user1_id, user2_id)
);

-- Chat messages 테이블
CREATE TABLE chat_messages (
    message_id SERIAL PRIMARY KEY,
    chat_id INTEGER REFERENCES chats(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### 📦 3. Storage 설정

#### Storage 버킷 생성
Supabase Storage에서 다음 버킷 생성:

1. **post-images** (Public access)
   - Max file size: 5MB
   - Allowed MIME types: image/jpeg, image/png, image/webp

2. **profile-images** (Public access)
   - Max file size: 2MB
   - Allowed MIME types: image/jpeg, image/png

#### Storage 정책 설정
```sql
-- Post images 업로드 정책
CREATE POLICY "Users can upload post images" ON storage.objects
FOR INSERT WITH CHECK (
  bucket_id = 'post-images' AND 
  auth.role() = 'authenticated' AND
  (storage.foldername(name))[1] = auth.uid()::text
);

-- Post images 읽기 정책 (모든 사용자 가능)
CREATE POLICY "Anyone can view post images" ON storage.objects
FOR SELECT USING (bucket_id = 'post-images');

-- 사용자는 자신의 images만 삭제 가능
CREATE POLICY "Users can delete their own post images" ON storage.objects
FOR DELETE USING (
  bucket_id = 'post-images' AND 
  auth.role() = 'authenticated' AND
  (storage.foldername(name))[1] = auth.uid()::text
);
```

### 🔧 4. 앱 구성 업데이트

#### 새로운 Repository 사용으로 MyApplication.kt 업데이트
```kotlin
class MyApplication : Application() {
    companion object {
        // Enhanced repository manager with caching
        lateinit var repositoryManager: SupabaseRepositoryManager
            private set
        
        // Legacy compatibility
        val authRepository get() = repositoryManager.authRepository
        val expenseRepository get() = repositoryManager.expenseRepository
        
        // Enhanced repositories
        val cachedPostRepository get() = repositoryManager.cachedPostRepository
        val imageRepository get() = repositoryManager.imageRepository
        val realtimeRepository = SupabaseRealtimeRepository()
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize repository manager with application context
        repositoryManager = SupabaseRepositoryManager.getInstance(this)
        
        // Initialize error reporting
        // In production, configure with actual analytics/crash reporting service
    }
}
```

#### BoardWriteActivity 이미지 업로드 개선
```kotlin
// BoardWriteActivity.kt에서 이미지 업로드 부분 교체
private suspend fun uploadImageIfNeeded(imageUri: Uri?): String? {
    return imageUri?.let { uri ->
        val currentUserId = AuthHelper.getSupabaseUserId(this) ?: return null
        val result = MyApplication.imageRepository.uploadPostImage(this, uri, currentUserId)
        result.fold(
            onSuccess = { imageUrl -> 
                Toast.makeText(this, "이미지 업로드 완료", Toast.LENGTH_SHORT).show()
                imageUrl
            },
            onFailure = { exception ->
                Toast.makeText(this, "이미지 업로드 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                null
            }
        )
    }
}
```

## 🚀 5. 배포 절차

### 개발 환경 검증
```bash
# 1. 빌드 테스트
./gradlew assembleDebug

# 2. 린트 검사
./gradlew lint

# 3. 유닛 테스트 실행
./gradlew testDebugUnitTest

# 4. 통합 테스트 (선택사항)
./gradlew connectedAndroidTest
```

### 릴리즈 빌드 준비
```bash
# 1. gradle.properties에 프로덕션 키 설정
SUPABASE_URL=https://your-production-id.supabase.co
SUPABASE_ANON_KEY=your-production-anon-key

# 2. 서명 키 설정 (release 빌드용)
# keystore 파일 생성 및 gradle.properties에 설정

# 3. 릴리즈 빌드
./gradlew assembleRelease
```

### APK 배포
1. Google Play Console에 APK 업로드
2. 내부 테스트 → 클로즈드 테스트 → 오픈 베타 → 프로덕션 순서로 배포

## 📊 6. 모니터링 및 유지보수

### 성능 모니터링
```kotlin
// 성능 모니터링 초기화 (Application.onCreate)
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Firebase Performance Monitoring
        // FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true
        
        // Crashlytics 설정
        // FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        
        // 정기적으로 캐시 정리
        lifecycleScope.launch {
            while (true) {
                delay(TimeUnit.HOURS.toMillis(6)) // 6시간마다
                repositoryManager.clearAllCaches()
            }
        }
    }
}
```

### 로그 모니터링
프로덕션 환경에서 ErrorReporter를 실제 서비스와 연동:

```kotlin
// ErrorReporter.kt 프로덕션 설정
private suspend fun reportToAnalytics(operation: String, error: Throwable, context: Map<String, Any?>) {
    try {
        // Firebase Crashlytics
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("operation", operation)
            context.forEach { (key, value) ->
                setCustomKey(key, value.toString())
            }
            recordException(error)
        }
        
        // Custom analytics endpoint
        // analyticsService.reportError(operation, error, context)
        
    } catch (e: Exception) {
        Log.e("ErrorReporter", "Failed to report analytics", e)
    }
}
```

## 🔄 7. 데이터 마이그레이션

Room → Supabase 데이터 이전이 필요한 경우:

```kotlin
class DataMigrationService {
    suspend fun migrateExistingData() {
        try {
            // 1. Room 데이터베이스에서 기존 데이터 읽기
            val roomPosts = roomDatabase.postDao().getAllPosts()
            val roomExpenses = roomDatabase.expenseDao().getAllExpenses()
            
            // 2. Supabase로 데이터 이전
            roomPosts.forEach { roomPost ->
                val supabasePost = Post(
                    userId = convertUserIdToUuid(roomPost.userId),
                    title = roomPost.title,
                    content = roomPost.content,
                    image = roomPost.image,
                    createdAt = roomPost.createdAt
                )
                MyApplication.cachedPostRepository.createPost(supabasePost)
            }
            
            // 3. 이전 완료 후 Room 데이터 정리
            // roomDatabase.clearAllTables()
            
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("DataMigration", e)
        }
    }
}
```

## 🚨 8. 긴급 대응 계획

### 롤백 전략
1. **즉시 롤백**: 이전 APK 버전으로 복원
2. **데이터베이스 롤백**: Supabase 백업에서 복원
3. **API 키 교체**: 키가 유출된 경우 즉시 새 키 발급

### 모니터링 지표
- **응답 시간**: API 호출 < 2초
- **에러율**: < 1%
- **크래시율**: < 0.1%
- **캐시 히트율**: > 80%

## 🎯 9. 최적화 권장사항

### 성능 최적화
- **이미지 최적화**: WebP 형식 사용, 적절한 해상도 설정
- **캐시 전략**: 자주 접근하는 데이터 캐시 TTL 조정
- **배치 처리**: 여러 API 호출을 배치로 처리

### 사용자 경험 개선
- **오프라인 지원**: 중요 데이터 로컬 캐시
- **실시간 알림**: Realtime 구독으로 라이브 업데이트
- **로딩 상태**: 적절한 로딩 인디케이터

---

## ✅ 체크리스트 요약

- [ ] API 키 보안 설정 완료
- [ ] RLS 정책 적용 완료
- [ ] 데이터베이스 스키마 생성 완료
- [ ] Storage 버킷 및 정책 설정 완료
- [ ] 앱 코드 업데이트 완료
- [ ] 테스트 검증 완료
- [ ] 모니터링 시스템 구축 완료
- [ ] 배포 준비 완료

이 가이드를 따라 진행하면 안전하고 확장 가능한 프로덕션 환경을 구축할 수 있습니다! 🎉