package com.jrstudio.svyazsbogom.data

import com.jrstudio.svyazsbogom.AppConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ContactApi {
    @POST("api/message")
    suspend fun sendMessage(@Body request: SendMessageRequest): SendMessageResponse

    @GET("api/conversation")
    suspend fun conversation(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String,
        @Query("channel") channel: String = "developer"
    ): ConversationResponse

    @POST("api/feedback")
    suspend fun feedback(@Body request: FeedbackRequest): BasicResponse

    @POST("api/delete")
    suspend fun deleteConversation(@Body request: DeleteConversationRequest): BasicResponse

    @POST("api/persona")
    suspend fun createPersona(@Body request: PersonaCreateRequest): PersonaCreateResponse

    @POST("api/persona/message")
    suspend fun personaMessage(@Body request: PersonaMessageRequest): PersonaMessageResponse

    @GET("api/wallet")
    suspend fun wallet(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): WalletResponse

    @POST("api/wallet/claim-daily")
    suspend fun claimDaily(@Body request: CoinActionRequest): CoinActionResponse

    @POST("api/wallet/reserve/give")
    suspend fun giveReserve(@Body request: CoinActionRequest): CoinActionResponse

    @POST("api/wallet/reserve/take")
    suspend fun takeReserve(@Body request: CoinActionRequest): CoinActionResponse

    @POST("api/ai/chat")
    suspend fun aiChat(@Body request: AiChatRequest): AiChatResponse

    @POST("api/agent/plan")
    suspend fun agentPlan(@Body request: AgentPlanRequest): AgentPlanResponse

    @POST("api/memory")
    suspend fun saveMemory(@Body request: MemorySaveRequest): BasicResponse

    // G-OS Core v0.1
    @GET("api/gos/state")
    suspend fun gosState(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): GosStateResponse

    @GET("api/gos/spaces")
    suspend fun gosSpaces(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): GosSpacesResponse

    @POST("api/gos/spaces")
    suspend fun gosCreateSpace(@Body request: GosSpaceCreateRequest): GosSpaceCreateResponse

    @GET("api/gos/personas")
    suspend fun gosPersonas(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String,
        @Query("spaceId") spaceId: String? = null
    ): GosPersonasResponse

    @POST("api/gos/personas")
    suspend fun gosCreatePersona(@Body request: GosPersonaCreateRequest): GosPersonaCreateResponse

    @POST("api/gos/tasks/run")
    suspend fun gosRunTask(@Body request: GosTaskRunRequest): GosTaskRunResponse

    @GET("api/gos/memory")
    suspend fun gosMemory(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): GosMemoryResponse

    @GET("api/gos/fitness")
    suspend fun gosFitness(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): GosFitnessResponse
}

object ApiClient {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: ContactApi = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ContactApi::class.java)
}
