package com.example.lovebug_project.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Error reporting and monitoring utility for Supabase operations
 * Provides structured logging and error tracking capabilities
 */
object ErrorReporter {
    
    private const val TAG = "SupabaseError"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * Log and track Supabase operation errors
     */
    fun logSupabaseError(
        operation: String,
        error: Throwable,
        context: Map<String, Any?> = emptyMap()
    ) {
        val timestamp = dateFormat.format(Date())
        val errorDetails = buildString {
            appendLine("🚨 SUPABASE ERROR REPORT")
            appendLine("Timestamp: $timestamp")
            appendLine("Operation: $operation")
            appendLine("Error Type: ${error.javaClass.simpleName}")
            appendLine("Message: ${error.message}")
            if (context.isNotEmpty()) {
                appendLine("Context:")
                context.forEach { (key, value) ->
                    appendLine("  $key: $value")
                }
            }
            appendLine("Stack Trace:")
            appendLine(error.stackTraceToString())
            appendLine("=".repeat(50))
        }
        
        Log.e(TAG, errorDetails)
        
        // In a production app, you might send this to Firebase Crashlytics, Bugsnag, etc.
        // For now, we'll just log it locally
        
        // Async error reporting (placeholder for future implementation)
        CoroutineScope(Dispatchers.IO).launch {
            reportToAnalytics(operation, error, context)
        }
    }
    
    /**
     * Track performance metrics for Supabase operations
     */
    fun trackPerformance(
        operationName: String,
        duration: Long,
        success: Boolean,
        recordCount: Int = 0
    ) {
        val status = if (success) "SUCCESS" else "FAILURE"
        Log.i("SupabasePerf", 
            "⚡ PERFORMANCE: $operationName | $duration ms | $status | Records: $recordCount"
        )
        
        // In production, send to analytics service
        CoroutineScope(Dispatchers.IO).launch {
            reportPerformanceMetrics(operationName, duration, success, recordCount)
        }
    }
    
    /**
     * Log successful operations for monitoring
     */
    fun logSuccess(operation: String, details: String = "") {
        Log.d("SupabaseSuccess", "✅ $operation${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    /**
     * Create context map for error reporting
     */
    fun createContext(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
        return mapOf(*pairs)
    }
    
    private suspend fun reportToAnalytics(
        operation: String,
        error: Throwable,
        context: Map<String, Any?>
    ) {
        // Placeholder for analytics reporting
        // In production, implement:
        // - Firebase Crashlytics
        // - Custom analytics endpoint
        // - Error aggregation service
    }
    
    private suspend fun reportPerformanceMetrics(
        operationName: String,
        duration: Long,
        success: Boolean,
        recordCount: Int
    ) {
        // Placeholder for performance metrics
        // In production, implement:
        // - Firebase Performance Monitoring
        // - Custom metrics endpoint
        // - APM tools integration
    }
}

/**
 * Extension function to measure operation duration
 */
suspend inline fun <T> measureOperation(
    operationName: String,
    crossinline operation: suspend () -> Result<T>
): Result<T> {
    val startTime = System.currentTimeMillis()
    return try {
        val result = operation()
        val duration = System.currentTimeMillis() - startTime
        
        when {
            result.isSuccess -> {
                ErrorReporter.trackPerformance(operationName, duration, true)
                ErrorReporter.logSuccess(operationName)
            }
            result.isFailure -> {
                ErrorReporter.trackPerformance(operationName, duration, false)
                result.exceptionOrNull()?.let { error ->
                    ErrorReporter.logSupabaseError(operationName, error)
                }
            }
        }
        result
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        ErrorReporter.trackPerformance(operationName, duration, false)
        ErrorReporter.logSupabaseError(operationName, e)
        Result.failure(e)
    }
}