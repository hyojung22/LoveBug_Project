package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.data.supabase.models.Comment
import com.example.lovebug_project.data.supabase.models.PostWithProfile
import com.example.lovebug_project.utils.ErrorReporter
import com.example.lovebug_project.utils.measureOperation
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SupabasePostRepository {
    private val supabase = SupabaseClient.client
    private val userRepository = SupabaseUserRepository()
    
    /**
     * 게시글 목록과 작성자 프로필 정보를 함께 조회 (최신순) - 페이지네이션 지원
     */
    suspend fun getAllPostsWithProfiles(limitCount: Int = 20, offset: Int = 0): Result<List<PostWithProfile>> {
        return measureOperation("getAllPostsWithProfiles") {
            try {
                // 1. 먼저 모든 게시글을 가져옵니다
                val posts = supabase.from("posts")
                    .select {
                        limit(limitCount.toLong())
                        range(offset.toLong(), (offset + limitCount - 1).toLong())
                    }
                    .decodeList<Post>()
                    .sortedByDescending { it.postId }

                // 2. 각 게시글의 작성자 프로필 정보를 병렬로 가져옵니다
                val postsWithProfiles = coroutineScope {
                    posts.map { post ->
                        async {
                            val userProfile = userRepository.getUserProfile(post.userId)
                            PostWithProfile(
                                postId = post.postId,
                                userId = post.userId,
                                title = post.title,
                                content = post.content,
                                imageUrl = post.image,
                                imagePath = null, // Post 모델에는 imagePath가 없음
                                createdAt = post.createdAt,
                                updatedAt = post.updatedAt,
                                nickname = userProfile?.nickname ?: "알 수 없는 사용자",
                                avatarUrl = userProfile?.avatarUrl
                            )
                        }
                    }.awaitAll()
                }

                ErrorReporter.trackPerformance(
                    operationName = "getAllPostsWithProfiles",
                    duration = 0,
                    success = true,
                    recordCount = postsWithProfiles.size
                )

                Result.success(postsWithProfiles)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "getAllPostsWithProfiles",
                    error = e,
                    context = ErrorReporter.createContext(
                        "limit" to limitCount,
                        "offset" to offset
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 게시글 목록 조회 (최신순) - 페이지네이션 지원
     */
    suspend fun getAllPosts(limitCount: Int = 20, offset: Int = 0): Result<List<Post>> {
        return measureOperation("getAllPosts") {
            try {
                val posts = supabase.from("posts")
                    .select {
                        limit(limitCount.toLong())
                        range(offset.toLong(), (offset + limitCount - 1).toLong())
                        // Note: Ordering is handled client-side in the repository layer
                    }
                    .decodeList<Post>()
                    .sortedByDescending { it.postId } // Sort by postId descending (newest first)
                
                ErrorReporter.trackPerformance(
                    operationName = "getAllPosts",
                    duration = 0, // measureOperation에서 처리됨
                    success = true,
                    recordCount = posts.size
                )
                
                Result.success(posts)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "getAllPosts",
                    error = e,
                    context = ErrorReporter.createContext(
                        "limit" to limitCount,
                        "offset" to offset
                    )
                )
                Result.failure(e)
            }
        }
    }

    /**
     * 게시글 총 개수 조회 (페이지네이션용)
     */
    suspend fun getPostsCount(): Result<Long> {
        return measureOperation("getPostsCount") {
            try {
                val result = supabase.from("posts")
                    .select {
                        count(Count.EXACT)
                    }
                
                // Count implementation may need adjustment based on API version
                // For now, return 0 as placeholder until proper count method is verified
                Result.success(0L) // TODO: Implement proper count extraction
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("getPostsCount", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 키워드로 게시글 검색 (제목, 내용 또는 둘 다)
     */
    suspend fun searchPostsWithProfiles(
        keyword: String,
        searchInTitle: Boolean = true,
        searchInContent: Boolean = false,
        limitCount: Int = 50,
        offset: Int = 0
    ): Result<List<PostWithProfile>> {
        return measureOperation("searchPostsWithProfiles") {
            try {
                if (keyword.isBlank()) {
                    return@measureOperation getAllPostsWithProfiles(limitCount, offset)
                }

                val searchPattern = "%${keyword}%"

                // 검색 조건에 따라 필터 구성
                val posts = supabase.from("posts")
                    .select {
                        limit(limitCount.toLong())
                        range(offset.toLong(), (offset + limitCount - 1).toLong())
                        filter {
                            when {
                                searchInTitle && searchInContent -> {
                                    or {
                                        ilike("title", searchPattern)
                                        ilike("content", searchPattern)
                                    }
                                }
                                searchInTitle -> {
                                    ilike("title", searchPattern)
                                }
                                searchInContent -> {
                                    ilike("content", searchPattern)
                                }
                                else -> {
                                    // 기본적으로 제목에서 검색
                                    ilike("title", searchPattern)
                                }
                            }
                        }
                    }
                    .decodeList<Post>()
                    .sortedByDescending { it.postId }

                // 각 게시글의 작성자 프로필 정보를 병렬로 가져옵니다
                val postsWithProfiles = coroutineScope {
                    posts.map { post ->
                        async {
                            val userProfile = userRepository.getUserProfile(post.userId)
                            PostWithProfile(
                                postId = post.postId,
                                userId = post.userId,
                                title = post.title,
                                content = post.content,
                                imageUrl = post.image,
                                imagePath = null,
                                createdAt = post.createdAt,
                                updatedAt = post.updatedAt,
                                nickname = userProfile?.nickname ?: "알 수 없는 사용자",
                                avatarUrl = userProfile?.avatarUrl
                            )
                        }
                    }.awaitAll()
                }

                ErrorReporter.trackPerformance(
                    operationName = "searchPostsWithProfiles",
                    duration = 0,
                    success = true,
                    recordCount = postsWithProfiles.size
                )

                Result.success(postsWithProfiles)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "searchPostsWithProfiles",
                    error = e,
                    context = ErrorReporter.createContext(
                        "keyword" to keyword,
                        "searchInTitle" to searchInTitle,
                        "searchInContent" to searchInContent,
                        "limit" to limitCount,
                        "offset" to offset
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 특정 사용자의 게시글 조회
     */
    suspend fun getPostsByUserId(userId: String): Result<List<Post>> {
        return try {
            val posts = supabase.from("posts")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Post>()
                .sortedByDescending { it.postId } // Sort by postId descending (newest first)
            Result.success(posts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 게시글 ID로 단일 게시글 조회
     */
    suspend fun getPostById(postId: Int): Result<Post?> {
        return try {
            val posts = supabase.from("posts")
                .select {
                    filter {
                        eq("post_id", postId)
                    }
                }
                .decodeList<Post>()
            Result.success(posts.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 새 게시글 생성
     */
    suspend fun createPost(post: Post): Result<Post> {
        return measureOperation("createPost") {
            try {
                val result = supabase.from("posts")
                    .insert(post) {
                        select()
                    }
                    .decodeSingle<Post>()
                
                ErrorReporter.logSuccess(
                    "createPost", 
                    "Post created with ID: ${result.postId}"
                )
                
                Result.success(result)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "createPost",
                    error = e,
                    context = ErrorReporter.createContext(
                        "userId" to post.userId,
                        "title" to post.title,
                        "hasImage" to (post.image != null)
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 게시글 수정
     */
    suspend fun updatePost(postId: Int, post: Post): Result<Post> {
        return try {
            val result = supabase.from("posts")
                .update(post) {
                    select()
                    filter {
                        eq("post_id", postId)
                    }
                }
                .decodeSingle<Post>()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 게시글 삭제 (연관 데이터 포함)
     * FK 제약조건을 고려하여 comments, likes를 먼저 삭제한 후 게시글 삭제
     */
    suspend fun deletePost(postId: Int): Result<Unit> {
        return measureOperation("deletePost") {
            try {
                // 1. 연관된 댓글 삭제 (comments 테이블에서 post_id가 일치하는 모든 댓글)
                supabase.from("comments")
                    .delete {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                
                // 2. 연관된 좋아요 삭제 (likes 테이블에서 post_id가 일치하는 모든 좋아요)
                supabase.from("likes")
                    .delete {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                
                // 3. 게시글 삭제 (posts 테이블에서 해당 post_id)
                supabase.from("posts")
                    .delete {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                
                ErrorReporter.logSuccess(
                    "deletePost", 
                    "Post and related data deleted with ID: $postId"
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "deletePost",
                    error = e,
                    context = ErrorReporter.createContext("postId" to postId)
                )
                Result.failure(e)
            }
        }
    }
    
    // ============ 좋아요 관련 기능 ============
    
    /**
     * 좋아요 추가
     */
    suspend fun addLike(postId: Int, userId: String): Result<com.example.lovebug_project.data.supabase.models.Like> {
        return measureOperation("addLike") {
            try {
                val like = com.example.lovebug_project.data.supabase.models.Like(
                    postId = postId,
                    userId = userId
                )
                
                val result = supabase.from("likes")
                    .insert(like) {
                        select()
                    }
                    .decodeSingle<com.example.lovebug_project.data.supabase.models.Like>()
                
                ErrorReporter.logSuccess(
                    "addLike", 
                    "Like added for post $postId by user $userId"
                )
                
                Result.success(result)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "addLike",
                    error = e,
                    context = ErrorReporter.createContext(
                        "postId" to postId,
                        "userId" to userId
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 좋아요 삭제
     */
    suspend fun removeLike(postId: Int, userId: String): Result<Unit> {
        return measureOperation("removeLike") {
            try {
                supabase.from("likes")
                    .delete {
                        filter {
                            and {
                                eq("post_id", postId)
                                eq("user_id", userId)
                            }
                        }
                    }
                
                ErrorReporter.logSuccess(
                    "removeLike", 
                    "Like removed for post $postId by user $userId"
                )
                
                Result.success(Unit)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "removeLike",
                    error = e,
                    context = ErrorReporter.createContext(
                        "postId" to postId,
                        "userId" to userId
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 특정 게시글의 좋아요 개수 조회
     */
    suspend fun getLikeCountByPost(postId: Int): Result<Int> {
        return measureOperation("getLikeCountByPost") {
            try {
                val likes = supabase.from("likes")
                    .select {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                    .decodeList<com.example.lovebug_project.data.supabase.models.Like>()
                
                ErrorReporter.trackPerformance(
                    operationName = "getLikeCountByPost",
                    duration = 0,
                    success = true,
                    recordCount = likes.size
                )
                
                Result.success(likes.size)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "getLikeCountByPost",
                    error = e,
                    context = ErrorReporter.createContext("postId" to postId)
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 사용자가 특정 게시글에 좋아요를 눌렀는지 확인
     */
    suspend fun isPostLikedByUser(postId: Int, userId: String): Result<Boolean> {
        return measureOperation("isPostLikedByUser") {
            try {
                val likes = supabase.from("likes")
                    .select {
                        filter {
                            and {
                                eq("post_id", postId)
                                eq("user_id", userId)
                            }
                        }
                    }
                    .decodeList<com.example.lovebug_project.data.supabase.models.Like>()
                
                val isLiked = likes.isNotEmpty()
                
                ErrorReporter.trackPerformance(
                    operationName = "isPostLikedByUser",
                    duration = 0,
                    success = true,
                    recordCount = likes.size
                )
                
                Result.success(isLiked)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "isPostLikedByUser",
                    error = e,
                    context = ErrorReporter.createContext(
                        "postId" to postId,
                        "userId" to userId
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    // ============ 댓글 관련 기능 ============
    
    /**
     * 새 댓글 생성
     */
    suspend fun createComment(comment: Comment): Result<Comment> {
        return measureOperation("createComment") {
            try {
                val result = supabase.from("comments")
                    .insert(comment) {
                        select()
                    }
                    .decodeSingle<Comment>()
                
                ErrorReporter.logSuccess(
                    "createComment", 
                    "Comment created with ID: ${result.commentId}"
                )
                
                Result.success(result)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "createComment",
                    error = e,
                    context = ErrorReporter.createContext(
                        "postId" to comment.postId,
                        "userId" to comment.userId,
                        "contentLength" to comment.content.length
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 특정 게시글의 댓글 목록 조회 (최신순)
     */
    suspend fun getCommentsByPostId(postId: Int): Result<List<Comment>> {
        return measureOperation("getCommentsByPostId") {
            try {
                val comments = supabase.from("comments")
                    .select {
                        filter {
                            eq("post_id", postId)
                        }
                    }
                    .decodeList<Comment>()
                    .sortedByDescending { it.commentId } // Sort by commentId descending (newest first)
                
                ErrorReporter.trackPerformance(
                    operationName = "getCommentsByPostId",
                    duration = 0, // measureOperation에서 처리됨
                    success = true,
                    recordCount = comments.size
                )
                
                Result.success(comments)
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "getCommentsByPostId",
                    error = e,
                    context = ErrorReporter.createContext("postId" to postId)
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * 댓글 수정
     */
    suspend fun updateComment(commentId: Int, content: String): Result<Comment> {
        return try {
            val updatedComment = mapOf("content" to content)
            val result = supabase.from("comments")
                .update(updatedComment) {
                    select()
                    filter {
                        eq("comment_id", commentId)
                    }
                }
                .decodeSingle<Comment>()
            Result.success(result)
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError(
                operation = "updateComment",
                error = e,
                context = ErrorReporter.createContext(
                    "commentId" to commentId,
                    "contentLength" to content.length
                )
            )
            Result.failure(e)
        }
    }
    
    /**
     * 댓글 삭제
     */
    suspend fun deleteComment(commentId: Int): Result<Unit> {
        return try {
            supabase.from("comments")
                .delete {
                    filter {
                        eq("comment_id", commentId)
                    }
                }
            
            ErrorReporter.logSuccess("deleteComment", "Comment deleted with ID: $commentId")
            Result.success(Unit)
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError(
                operation = "deleteComment",
                error = e,
                context = ErrorReporter.createContext("commentId" to commentId)
            )
            Result.failure(e)
        }
    }
    
    /**
     * 특정 게시글의 댓글 개수 조회
     */
    suspend fun getCommentCountByPost(postId: Int): Result<Int> {
        return try {
            val comments = supabase.from("comments")
                .select {
                    filter {
                        eq("post_id", postId)
                    }
                }
                .decodeList<Comment>()
            Result.success(comments.size)
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError(
                operation = "getCommentCountByPost",
                error = e,
                context = ErrorReporter.createContext("postId" to postId)
            )
            Result.failure(e)
        }
    }
}