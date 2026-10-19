package com.fitnessapp.pulse.data.remote

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val tools: List<ToolDefinition>? = null
)

data class ChatMessage(
    val role: String,
    val content: Any? = null, // String for text-only, List<ContentPart> for text+image, or null for a tool-call-only assistant message
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null
)

sealed class ContentPart {
    data class Text(val type: String = "text", val text: String) : ContentPart()
    data class ImageUrl(val type: String = "image_url", val image_url: ImageUrlData) : ContentPart()
}

data class ImageUrlData(val url: String)

/** An OpenAI-format function tool definition; [parameters] is a raw JSON Schema object. */
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

/** [arguments] is a JSON-encoded string, per the OpenAI function-calling spec. */
data class FunctionCall(
    val name: String,
    val arguments: String
)

data class ChatResponse(val choices: List<Choice>)
data class Choice(val message: ResponseMessage)
data class ResponseMessage(val role: String, val content: String?, val tool_calls: List<ToolCall>? = null)