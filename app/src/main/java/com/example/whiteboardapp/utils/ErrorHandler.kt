package com.example.whiteboardapp.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.IOException

/**
 * A centralized utility for handling common exceptions and displaying user-friendly messages.
 */
object ErrorHandler {

    /**
     * Handles a given exception by showing an appropriate dialog or toast.
     *
     * @param context The context for displaying UI elements.
     * @param exception The caught exception.
     * @param userMessage A generic message to show for unhandled error types.
     * @param retry An optional lambda to be executed if the user taps a "Retry" button.
     */
    fun handleException(
        context: Context,
        exception: Throwable,
        userMessage: String = "An error occurred",
        retry: (() -> Unit)? = null
    ) {
        when (exception) {
            is OutOfMemoryError -> handleOutOfMemory(context, retry)
            is IOException -> handleIOException(context, exception, retry)
            else -> handleGenericError(context, userMessage)
        }

        // Log for debugging
        exception.printStackTrace()
    }

    private fun handleOutOfMemory(context: Context, retry: (() -> Unit)?) {
        AlertDialog.Builder(context)
            .setTitle("Memory Warning")
            .setMessage("The app is running low on memory. Try closing some objects or saving your work.")
            .setPositiveButton("Clear & Retry") { _, _ ->
                System.gc()
                retry?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleIOException(context: Context, exception: IOException, retry: (() -> Unit)?) {
        AlertDialog.Builder(context)
            .setTitle("Storage Error")
            .setMessage("Failed to read/write file: ${exception.message}")
            .setPositiveButton("Retry") { _, _ -> retry?.invoke() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleGenericError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}