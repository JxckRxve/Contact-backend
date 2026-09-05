package com.jrstudio.svyazsbogom.data

data class GosSpace(
    val id: String,
    val userId: String? = null,
    val key: String,
    val name: String,
    val status: String = "CORE",
    val isSystem: Boolean = false
)

data class GosSpacesResponse(
    val ok: Boolean,
    val items: List<GosSpace> = emptyList(),
    val error: String? = null
)

data class GosSpaceCreateRequest(
    val conversationId: String,
    val installSecret: String,
    val name: String,
    val key: String? = null,
    val status: String = "CORE"
)

data class GosSpaceCreateResponse(
    val ok: Boolean,
    val item: GosSpace? = null,
    val error: String? = null
)

data class GosGenome(
    val version: String = "0.1",
    val role: String = "assistant",
    val prompt: String = "",
    val planning: String = "direct",
    val memoryStrategy: String = "scoped_relevant_recent",
    val model: String = "auto",
    val tools: List<String> = emptyList(),
    val communicationStyle: String = "natural",
    val parentPersonaId: String? = null,
    val generation: Int = 0,
    val mutations: List<String> = emptyList()
)

data class GosPersona(
    val id: String,
    val userId: String? = null,
    val spaceId: String,
    val name: String,
    val role: String,
    val personality: String = "",
    val tools: List<String> = emptyList(),
    val xp: Int = 0,
    val level: Int = 1,
    val status: String = "active",
    val genome: GosGenome = GosGenome()
)

data class GosPersonasResponse(
    val ok: Boolean,
    val items: List<GosPersona> = emptyList(),
    val error: String? = null
)

data class GosPersonaCreateRequest(
    val conversationId: String,
    val installSecret: String,
    val spaceId: String,
    val name: String,
    val role: String,
    val personality: String,
    val tools: List<String> = emptyList(),
    val prompt: String = "",
    val planning: String = "direct",
    val model: String = "auto",
    val communicationStyle: String = "natural"
)

data class GosPersonaCreateResponse(
    val ok: Boolean,
    val persona: GosPersona? = null,
    val error: String? = null
)

data class GosTask(
    val id: String,
    val userId: String? = null,
    val spaceId: String,
    val personaId: String,
    val input: String,
    val status: String,
    val result: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val cost: Double = 0.0,
    val error: String? = null,
    val createdAt: Long = 0,
    val completedAt: Long? = null
)

data class GosMemory(
    val id: String,
    val userId: String,
    val text: String,
    val type: String = "episodic",
    val scope: String = "task",
    val spaceId: String? = null,
    val personaId: String? = null,
    val taskId: String? = null,
    val source: String? = null,
    val createdAt: Long = 0
)

data class GosExperienceEvent(
    val id: String,
    val userId: String,
    val spaceId: String? = null,
    val personaId: String? = null,
    val taskId: String? = null,
    val type: String,
    val createdAt: Long = 0
)

data class GosFitnessRecord(
    val id: String,
    val userId: String,
    val spaceId: String? = null,
    val personaId: String? = null,
    val taskId: String? = null,
    val success: Double = 0.0,
    val quality: Double? = null,
    val timeMs: Long = 0,
    val cost: Double = 0.0,
    val revenue: Double = 0.0,
    val ownerTimeMs: Long = 0,
    val error: String? = null,
    val createdAt: Long = 0
)

data class GosTaskRunRequest(
    val conversationId: String,
    val installSecret: String,
    val spaceId: String,
    val personaId: String,
    val input: String,
    val quality: String = "auto",
    val ownerTimeMs: Long = 0,
    val revenue: Double = 0.0
)

data class GosPersonaProgress(
    val id: String,
    val xp: Int = 0,
    val level: Int = 1
)

data class GosTaskRunResponse(
    val ok: Boolean,
    val cycle: List<String> = emptyList(),
    val task: GosTask? = null,
    val memory: GosMemory? = null,
    val experience: GosExperienceEvent? = null,
    val fitness: GosFitnessRecord? = null,
    val persona: GosPersonaProgress? = null,
    val error: String? = null
)

data class GosMemoryResponse(
    val ok: Boolean,
    val items: List<GosMemory> = emptyList(),
    val error: String? = null
)

data class GosFitnessResponse(
    val ok: Boolean,
    val items: List<GosFitnessRecord> = emptyList(),
    val error: String? = null
)

data class GosCounts(
    val spaces: Int = 0,
    val personas: Int = 0,
    val tasks: Int = 0,
    val memories: Int = 0,
    val experienceEvents: Int = 0,
    val fitnessRecords: Int = 0
)

data class GosStateResponse(
    val ok: Boolean,
    val version: String? = null,
    val counts: GosCounts = GosCounts(),
    val error: String? = null
)
