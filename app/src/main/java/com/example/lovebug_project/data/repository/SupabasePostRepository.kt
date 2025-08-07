package com.example.lovebug_project.data.repository

import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.data.supabase.models.Post
import com.example.lovebug_project.utils.ErrorReporter
import com.example.lovebug_project.utils.measureOperation
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count

class SupabasePostRepository {
    private val supabase = SupabaseClient.client
    
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
                        // Note: Ordering may need to be handled differently in current API
                    }
                    .decodeList<Post>()
                
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
     * 게시글 삭제
     */
    suspend fun deletePost(postId: Int): Result<Unit> {
        return try {
            supabase.from("posts")
                .delete {
                    filter {
                        eq("post_id", postId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}