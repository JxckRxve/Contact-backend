package com.jrstudio.svyazsbogom.data

import com.jrstudio.svyazsbogom.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class GosMemoryV01Item(
    val id: String,
    val userId: String,
    val text: String,
    val type: String = "episodic",
    val scope: String = "episode",
    val spaceId: String? = null,
    val personaId: String? = null,
    val taskId: String? = null,
    val source: String? = null,
    val createdAt: Long = 0,
    val expiresAt: Long? = null
)

data class GosMemorySchema(
    val version: String = "0.1",
    val active: List<String> = emptyList(),
    val placeholder: List<String> = emptyList(),
    val workingDefaultTtlMinutes: Int = 1440
)

data class GosMemorySchemaResponse(
    val ok: Boolean,
    val schema: GosMemorySchema = GosMemorySchema(),
    val error: String? = null
)

data class GosMemoryCreateRequest(
    val conversationId: String,
    val installSecret: String,
    val text: String,
    val type: String,
    val spaceId: String? = null,
    val personaId: String? = null,
    val taskId: String? = null,
    val ttlMinutes: Int? = null,
    val source: String = "android"
)

data class GosMemoryCreateResponse(
    val ok: Boolean,
    val item: GosMemoryV01Item? = null,
    val error: String? = null
)

data class GosMemoryListResponse(
    val ok: Boolean,
    val items: List<GosMemoryV01Item> = emptyList(),
    val error: String? = null
)

data class GosMemoryRankedItem(
    val memory: GosMemoryV01Item,
    val score: Double = 0.0,
    val reasons: List<String> = emptyList()
)

data class GosMemoryRetrieveResponse(
    val ok: Boolean,
    val query: String = "",
    val spaceId: String? = null,
    val personaId: String? = null,
    val items: List<GosMemoryRankedItem> = emptyList(),
    val error: String? = null
)

interface GosMemoryV01Api {
    @GET("api/gos/memory/schema")
    suspend fun schema(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String
    ): GosMemorySchemaResponse

    @GET("api/gos/memory")
    suspend fun list(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String,
        @Query("type") type: String? = null,
        @Query("spaceId") spaceId: String? = null,
        @Query("personaId") personaId: String? = null,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 100
    ): GosMemoryListResponse

    @POST("api/gos/memory")
    suspend fun create(@Body request: GosMemoryCreateRequest): GosMemoryCreateResponse

    @GET("api/gos/memory/retrieve")
    suspend fun retrieve(
        @Query("conversationId") conversationId: String,
        @Query("installSecret") installSecret: String,
        @Query("q") query: String,
        @Query("spaceId") spaceId: String? = null,
        @Query("personaId") personaId: String? = null,
        @Query("limit") limit: Int = 12
    ): GosMemoryRetrieveResponse
}

object GosMemoryV01Client {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val api: GosMemoryV01Api = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GosMemoryV01Api::class.java)
}
