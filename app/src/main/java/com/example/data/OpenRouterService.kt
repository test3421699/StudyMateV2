package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OpenRouterNetwork {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun streamChatCompletions(apiKey: String, jsonBody: String): Flow<String> = flow {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://github.com/aistudio")
            .addHeader("X-Title", "StudyMate Pro")
            .post(jsonBody.toRequestBody(mediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errStr = response.body?.string() ?: ""
            var message = "HTTP ${response.code}"
            try {
                val json = JSONObject(errStr)
                val errObj = json.optJSONObject("error")
                val msg = errObj?.optString("message")
                if (!msg.isNullOrEmpty()) message = msg
            } catch (e: Exception) {
                if (errStr.isNotBlank()) message = errStr
            }
            throw Exception("OpenRouter Error: $message")
        }

        val body = response.body ?: throw Exception("Empty response body from OpenRouter")
        body.byteStream().bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.startsWith("data: ")) {
                    val dataStr = l.substring(6).trim()
                    if (dataStr == "[DONE]") break
                    try {
                        val jsonObj = JSONObject(dataStr)
                        val choices = jsonObj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content")
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore malformed lines
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
