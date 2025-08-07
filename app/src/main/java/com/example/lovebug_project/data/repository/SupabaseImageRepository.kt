package com.example.lovebug_project.data.repository

import android.content.Context
import android.net.Uri
import com.example.lovebug_project.data.supabase.SupabaseClient
import com.example.lovebug_project.utils.ErrorReporter
import com.example.lovebug_project.utils.measureOperation
import io.github.jan.supabase.storage.storage
import io.ktor.http.*
import java.io.InputStream
import java.util.*

/**
 * Supabase Storage integration for image uploads and management
 */
class SupabaseImageRepository {
    
    private val supabase = SupabaseClient.client
    private val storage = supabase.storage
    
    companion object {
        private const val BUCKET_NAME = "images"
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    }
    
    /**
     * Upload image to Supabase Storage
     * @param context Android context for content resolver
     * @param imageUri Local image URI
     * @param userId User ID for organizing uploads
     * @return Result with public URL of uploaded image
     */
    suspend fun uploadPostImage(
        context: Context,
        imageUri: Uri,
        userId: String
    ): Result<String> {
        return measureOperation("uploadPostImage") {
            try {
                // Generate unique filename
                val timestamp = System.currentTimeMillis()
                val extension = getFileExtension(context, imageUri)
                val fileName = "posts/$userId/$timestamp.$extension"
                
                // Read image data
                val inputStream: InputStream = context.contentResolver.openInputStream(imageUri)
                    ?: return@measureOperation Result.failure(Exception("Cannot open image file"))
                
                val imageBytes = inputStream.readBytes()
                inputStream.close()
                
                // Validate file size
                if (imageBytes.size > MAX_FILE_SIZE) {
                    return@measureOperation Result.failure(
                        Exception("Image file too large. Maximum size: ${MAX_FILE_SIZE / (1024 * 1024)}MB")
                    )
                }
                
                // Upload to Supabase Storage
                val uploadResult = storage.from(BUCKET_NAME).upload(fileName, imageBytes) {
                    contentType = ContentType.Image.JPEG // Default to JPEG
                    upsert = false // Don't overwrite existing files
                }
                
                // Get public URL
                val publicUrl = storage.from(BUCKET_NAME).publicUrl(fileName)
                
                ErrorReporter.logSuccess(
                    "uploadPostImage",
                    "Image uploaded: $fileName (${imageBytes.size} bytes)"
                )
                
                Result.success(publicUrl)
                
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "uploadPostImage",
                    error = e,
                    context = ErrorReporter.createContext(
                        "userId" to userId,
                        "imageUri" to imageUri.toString()
                    )
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete image from Supabase Storage
     */
    suspend fun deletePostImage(imageUrl: String): Result<Unit> {
        return measureOperation("deletePostImage") {
            try {
                // Extract file path from URL
                val fileName = extractFileNameFromUrl(imageUrl)
                if (fileName == null) {
                    return@measureOperation Result.failure(
                        Exception("Invalid image URL format")
                    )
                }
                
                // Delete from storage
                storage.from(BUCKET_NAME).delete(fileName)
                
                ErrorReporter.logSuccess("deletePostImage", "Deleted: $fileName")
                Result.success(Unit)
                
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError(
                    operation = "deletePostImage", 
                    error = e,
                    context = ErrorReporter.createContext("imageUrl" to imageUrl)
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get public image URL 
     * Note: Image transformations are not supported in publicUrl method
     * For image optimization, consider client-side processing or CDN
     */
    fun getOptimizedImageUrl(
        imageUrl: String,
        width: Int = 800,
        height: Int = 600,
        quality: Int = 80
    ): String {
        return try {
            val fileName = extractFileNameFromUrl(imageUrl)
            if (fileName != null) {
                storage.from(BUCKET_NAME).publicUrl(fileName)
            } else {
                imageUrl // Return original if extraction fails
            }
        } catch (e: Exception) {
            ErrorReporter.logSupabaseError("getOptimizedImageUrl", e)
            imageUrl // Return original URL on error
        }
    }
    
    /**
     * Create storage bucket if it doesn't exist (for setup)
     */
    suspend fun initializeBucket(): Result<Unit> {
        return measureOperation("initializeBucket") {
            try {
                // Try to create bucket - will fail if it already exists, which is fine
                try {
                    storage.createBucket(BUCKET_NAME)
                    ErrorReporter.logSuccess("initializeBucket", "Bucket '$BUCKET_NAME' created successfully")
                } catch (e: Exception) {
                    // Bucket likely already exists or creation failed
                    ErrorReporter.logSuccess("initializeBucket", "Bucket '$BUCKET_NAME' already exists or creation failed: ${e.message}")
                }
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                ErrorReporter.logSupabaseError("initializeBucket", e)
                Result.failure(e)
            }
        }
    }
    
    private fun getFileExtension(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
        } ?: "jpg"
    }
    
    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            // Extract file path from Supabase Storage URL
            // Format: https://{project}.supabase.co/storage/v1/object/public/{bucket}/{path}
            val parts = url.split("/")
            val bucketIndex = parts.indexOf("public")
            if (bucketIndex >= 0 && bucketIndex + 2 < parts.size) {
                parts.subList(bucketIndex + 2, parts.size).joinToString("/")
            } else null
        } catch (e: Exception) {
            null
        }
    }
}