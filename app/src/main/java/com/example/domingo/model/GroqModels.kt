package com.example.domingo.model

import com.google.gson.annotations.SerializedName

data class Message(
    val role: String,
    val content: String
)

data class GroqResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class GroqRequest(
    val model: String = "llama-3.2-11b-vision-preview",
    val messages: List<MessageVision>
)

data class MessageVision(
    val role: String,
    val content: List<Any>
)

data class TextContent(val type: String = "text", val text: String)
data class ImageContent(val type: String = "image_url", val image_url: ImageUrl)
data class ImageUrl(val url: String)