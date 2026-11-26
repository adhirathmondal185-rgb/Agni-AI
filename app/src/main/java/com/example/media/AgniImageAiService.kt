package com.example.media

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "AgniImageAiService"
private const val IMAGE_TIMEOUT_MS = 60_000L

/**
 * Runtime AI image generation and image-to-image editing via Firebase AI Logic.
 * Uses lazy model initialization, realistic 60s timeouts, and transparent error propagation
 * with zero mocked/fallback drawings.
 */
class AgniImageAiService {

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun generateImage(prompt: String): Bitmap {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "Please describe the image you want to create." }

        return try {
            withTimeout(IMAGE_TIMEOUT_MS) {
                val response = imageModel.generateContent(trimmed)
                val bitmap = response.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image

                bitmap ?: throw IllegalStateException(
                    "No image was returned by the AI model. Try rephrasing your prompt."
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI image generation timed out after ${IMAGE_TIMEOUT_MS}ms", e)
            throw IllegalStateException("Image generation timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI image generation failed", e)
            throw e
        }
    }

    suspend fun editImage(source: Bitmap, instruction: String): Bitmap {
        val trimmed = instruction.trim()
        require(trimmed.isNotEmpty()) { "Please describe how you want AI to edit this photo." }

        return try {
            withTimeout(IMAGE_TIMEOUT_MS) {
                val promptContent = content {
                    image(source)
                    text(trimmed)
                }
                val response = imageModel.generateContent(promptContent)
                val bitmap = response.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                    ?.image

                bitmap ?: throw IllegalStateException(
                    "The AI editor did not return an edited image. Try a clearer instruction."
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI image edit timed out after ${IMAGE_TIMEOUT_MS}ms", e)
            throw IllegalStateException("AI image edit timed out after 60 seconds. Please retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI image edit failed", e)
            throw e
        }
    }
}
