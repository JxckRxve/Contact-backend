package com.jrstudio.svyazsbogom.data

data class SendMessageRequest(
    val conversationId: String,
    val installSecret: String,
    val text: String,
    val topic: String? = null,
    val channel: String = "developer"
)

data class SendMessageResponse(
    val ok: Boolean,
    val messageId: String? = null,
    val sessionStatus: String? = null
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val createdAt: Long
)

data class ConversationResponse(
    val ok: Boolean,
    val messages: List<ChatMessage> = emptyList(),
    val sessionStatus: String = "open",
    val sessionNumber: Int = 1,
    val isFirstSession: Boolean = true
)

data class FeedbackRequest(
    val conversationId: String,
    val installSecret: String,
    val helped: Boolean
)

data class BasicResponse(val ok: Boolean)

data class DeleteConversationRequest(
    val conversationId: String,
    val installSecret: String
)

data class PersonaCreateRequest(
    val conversationId: String,
    val installSecret: String,
    val name: String,
    val relationship: String,
    val description: String,
    val speech: String,
    val memories: String,
    val neverSay: String
)

data class PersonaProfile(
    val name: String,
    val relationship: String,
    val description: String,
    val speech: String,
    val memories: String,
    val neverSay: String
)

data class PersonaCreateResponse(
    val ok: Boolean,
    val personaId: String? = null,
    val profile: PersonaProfile? = null
)

data class PersonaMessageRequest(
    val conversationId: String,
    val installSecret: String,
    val personaId: String,
    val text: String
)

data class PersonaMessageResponse(
    val ok: Boolean,
    val reply: String? = null,
    val error: String? = null
)

data class WalletResponse(
    val ok: Boolean,
    val balance: Int = 150,
    val reserve: Int = 0,
    val startingCoins: Int = 150,
    val dailyCoins: Int = 5
)

data class CoinActionRequest(
    val conversationId: String,
    val installSecret: String,
    val amount: Int? = null
)

data class CoinActionResponse(
    val ok: Boolean,
    val claimed: Boolean? = null,
    val taken: Boolean? = null,
    val delta: Int? = null,
    val balance: Int = 0,
    val reserve: Int = 0,
    val error: String? = null
)

data class AiChatRequest(
    val conversationId: String,
    val installSecret: String,
    val text: String,
    val task: String = "chat",
    val quality: String = "auto",
    val personaId: String? = null
)

data class AiChatResponse(
    val ok: Boolean,
    val reply: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val cost: Int = 0,
    val balance: Int = 0,
    val error: String? = null,
    val needed: Int? = null
)

data class AgentPlanRequest(
    val conversationId: String,
    val installSecret: String,
    val goal: String,
    val context: String = "",
    val quality: String = "auto"
)

data class AgentPlanResponse(
    val ok: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val plan: Map<String, Any?>? = null,
    val cost: Int = 0,
    val balance: Int = 0,
    val status: String? = null,
    val error: String? = null
)

data class MemorySaveRequest(
    val conversationId: String,
    val installSecret: String,
    val text: String,
    val scope: String = "user"
)
