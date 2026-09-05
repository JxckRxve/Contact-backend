package com.jrstudio.svyazsbogom.data

import com.jrstudio.svyazsbogom.AppConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class GosPersonaUpdateRequest(
    val conversationId: String,
    val installSecret: String,
    val spaceId: String? = null,
    val name: String? = null,
    val role: String? = null,
    val personality: String? = null,
    val tools: List<String>? = null,
    val prompt: String? = null,
    val planning: String? = null,
    val model: String? = null,
    val communicationStyle: String? = null
)

data class GosPersonaCloneRequest(
    val conversationId: String,
    val installSecret: String,
    val name: String? = null,
    val spaceId: String? = null
)

data class GosPersonaArchiveRequest(
    val conversationId: String,
    val installSecret: String
)

data class GosPersonaMutationResponse(
    val ok: Boolean,
    val persona: GosPersona? = null,
    val error: String? = null
)

interface GosPersonaV02Api {
    @PATCH("api/gos/personas/{personaId}")
    suspend fun updatePersona(
        @Path("personaId") personaId: String,
        @Body request: GosPersonaUpdateRequest
    ): GosPersonaMutationResponse

    @POST("api/gos/personas/{personaId}/clone")
    suspend fun clonePersona(
        @Path("personaId") personaId: String,
        @Body request: GosPersonaCloneRequest
    ): GosPersonaMutationResponse

    @POST("api/gos/personas/{personaId}/archive")
    suspend fun archivePersona(
        @Path("personaId") personaId: String,
        @Body request: GosPersonaArchiveRequest
    ): GosPersonaMutationResponse
}

object GosPersonaV02Client {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val api: GosPersonaV02Api = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GosPersonaV02Api::class.java)
}
