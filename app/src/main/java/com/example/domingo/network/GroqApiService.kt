package com.example.domingo.network

import com.example.domingo.model.GroqRequest
import com.example.domingo.model.GroqResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GroqRequest(
    val model: String,
    val messages: List<Any>,
    val max_tokens: Int? = 1000,
    val temperature: Double? = 0.7
)

data class GroqResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: MessageContent
)

data class MessageContent(
    val content: String
)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: Any
    ): GroqResponse
}