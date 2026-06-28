package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String // Hex/Base64 string
)

data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null
)

data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") key: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST("v1beta/models/gemini-3.5-flash:streamGenerateContent")
    @retrofit2.http.Streaming
    suspend fun generateContentStream(
        @Query("key") key: String,
        @Body request: GeminiRequest
    ): okhttp3.ResponseBody
}

object GeminiNetwork {
    @Volatile var activeModel: String = "gemini-3.5-flash"

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(240, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(240, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val originalUrl = request.url
            var urlString = originalUrl.toString()
            
            val model = activeModel
            // Replace model in url with current activeModel value
            if (urlString.contains("gemini-3.5-flash")) {
                urlString = urlString.replace("gemini-3.5-flash", model)
            } else if (urlString.contains("gemini-2.5-flash")) {
                urlString = urlString.replace("gemini-2.5-flash", model)
            } else if (urlString.contains("gemini-1.5-flash")) {
                urlString = urlString.replace("gemini-1.5-flash", model)
            }
            
            val finalRequest = if (urlString != originalUrl.toString()) {
                request.newBuilder().url(urlString).build()
            } else {
                request
            }
            
            android.util.Log.d("GeminiNetwork", "Executing request with model: $model, URL: $urlString")
            chain.proceed(finalRequest)
        }
        .build()

    val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    fun streamGenerateContent(apiKey: String, request: GeminiRequest): Flow<String> = flow {
        val responseBody = api.generateContentStream(apiKey, request)
        responseBody.byteStream().bufferedReader().use { reader ->
            var line: String?
            var jsonAccumulator = ""
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (l.isEmpty()) continue
                jsonAccumulator += l
                
                var cleaned = jsonAccumulator.trim()
                if (cleaned.startsWith("[")) cleaned = cleaned.substring(1).trim()
                if (cleaned.startsWith(",")) cleaned = cleaned.substring(1).trim()
                if (cleaned.endsWith("]")) cleaned = cleaned.substring(0, cleaned.length - 1).trim()
                
                if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                    try {
                        val adapter = moshi.adapter(GeminiResponse::class.java)
                        val response = adapter.fromJson(cleaned)
                        val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (text != null) {
                            emit(text)
                        }
                        jsonAccumulator = ""
                    } catch (e: Exception) {
                        // keep accumulating
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

