package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class DictionaryEntry(
    @Json(name = "word") val word: String,
    @Json(name = "phonetic") val phonetic: String? = null,
    @Json(name = "phonetics") val phonetics: List<DictionaryPhonetic>? = null,
    @Json(name = "meanings") val meanings: List<DictionaryMeaning>
)

data class DictionaryPhonetic(
    @Json(name = "text") val text: String? = null,
    @Json(name = "audio") val audio: String? = null
)

data class DictionaryMeaning(
    @Json(name = "partOfSpeech") val partOfSpeech: String,
    @Json(name = "definitions") val definitions: List<DictionaryDefinition>,
    @Json(name = "synonyms") val synonyms: List<String>? = null,
    @Json(name = "antonyms") val antonyms: List<String>? = null
)

data class DictionaryDefinition(
    @Json(name = "definition") val definition: String,
    @Json(name = "example") val example: String? = null,
    @Json(name = "synonyms") val synonyms: List<String>? = null,
    @Json(name = "antonyms") val antonyms: List<String>? = null
)

interface DictionaryApi {
    @GET("api/v2/entries/en/{word}")
    suspend fun getDefinition(@Path("word") word: String): List<DictionaryEntry>
}

object DictionaryNetwork {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.dictionaryapi.dev/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: DictionaryApi = retrofit.create(DictionaryApi::class.java)
}
