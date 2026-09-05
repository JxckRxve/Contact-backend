package com.jrstudio.svyazsbogom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrstudio.svyazsbogom.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MemBg = Color(0xFF030106)
private val MemSurface = Color(0xFF0D0713)
private val MemSurface2 = Color(0xFF15101D)
private val MemPurple = Color(0xFF8F4CFF)
private val MemPurpleBright = Color(0xFFB76CFF)
private val MemText = Color(0xFFF8F5FF)
private val MemMuted = Color(0xFFA79CAF)
private val MemGreen = Color(0xFF92E6B7)
private val MemRed = Color(0xFFFF9AA8)

private enum class MemoryScopeMode { GLOBAL, SPACE, AGENT }

@Composable
fun GosMemoryScreen(
    identityId: String,
    identitySecret: String,
    spaces: List<GosSpace>,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var schema by remember { mutableStateOf(GosMemorySchema()) }
    var personas by remember { mutableStateOf<List<GosPersona>>(emptyList()) }
    var memories by remember { mutableStateOf<List<GosMemoryV01Item>>(emptyList()) }
    var ranked by remember { mutableStateOf<List<GosMemoryRankedItem>>(emptyList()) }
    var selectedType by remember { mutableStateOf("personal") }
    var scopeMode by remember { mutableStateOf(MemoryScopeMode.GLOBAL) }
    var selectedSpaceId by remember { mutableStateOf<String?>(spaces.firstOrNull { it.key == "HOME" }?.id) }
    var selectedPersonaId by remember { mutableStateOf<String?>(null) }
    var memoryText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var retrieving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun effectiveSpaceId(): String? = when (scopeMode) {
        MemoryScopeMode.GLOBAL -> null
        MemoryScopeMode.SPACE -> selectedSpaceId
        MemoryScopeMode.AGENT -> personas.firstOrNull { it.id == selectedPersonaId }?.spaceId
    }

    fun effectivePersonaId(): String? = if (scopeMode == MemoryScopeMode.AGENT) selectedPersonaId else null

    suspend fun loadAll() {
        loading = true
        error = null
        runCatching {
            val schemaResponse = GosMemoryV01Client.api.schema(identityId, identitySecret)
            val personaResponse = ApiClient.api.gosPersonas(identityId, identitySecret, null)
            val memoryResponse = GosMemoryV01Client.api.list(identityId, identitySecret, limit = 100)
            if (!schemaResponse.ok) error(schemaResponse.error ?: "Memory schema unavailable")
            if (!personaResponse.ok) error(personaResponse.error ?: "Persona list unavailable")
            if (!memoryResponse.ok) error(memoryResponse.error ?: "Memory list unavailable")
            schema = schemaResponse.schema
            personas = personaResponse.items
            memories = memoryResponse.items
        }.onFailure { error = it.message ?: "Memory Core unavailable" }
        loading = false
    }

    suspend fun saveMemory() {
        saving = true
        error = null
        notice = null
        val spaceId = effectiveSpaceId()
        val personaId = effectivePersonaId()
        runCatching {
            GosMemoryV01Client.api.create(
                GosMemoryCreateRequest(
                    conversationId = identityId,
                    installSecret = identitySecret,
                    text = memoryText.trim(),
                    type = selectedType,
                    spaceId = spaceId,
                    personaId = personaId,
                    ttlMinutes = if (selectedType == "working") schema.workingDefaultTtlMinutes else null
                )
            )
        }.onSuccess {
            if (it.ok) {
                memoryText = ""
                notice = "MEMORY SAVED • ${it.item?.type?.uppercase() ?: selectedType.uppercase()}"
                val list = GosMemoryV01Client.api.list(identityId, identitySecret, limit = 100)
                if (list.ok) memories = list.items
                onChanged()
            } else error = it.error ?: "Memory save failed"
        }.onFailure { error = it.message ?: "Memory save failed" }
        saving = false
    }

    suspend fun retrieve() {
        retrieving = true
        error = null
        notice = null
        ranked = emptyList()
        runCatching {
            GosMemoryV01Client.api.retrieve(
                conversationId = identityId,
                installSecret = identitySecret,
                query = query.trim(),
                spaceId = effectiveSpaceId(),
                personaId = effectivePersonaId(),
                limit = 12
            )
        }.onSuccess {
            if (it.ok) {
                ranked = it.items
                notice = "RETRIEVAL • ${it.items.size} ITEM(S)"
            } else error = it.error ?: "Retrieval failed"
        }.onFailure { error = it.message ?: "Retrieval failed" }
        retrieving = false
    }

    fun selectType(type: String) {
        selectedType = type
        when (type) {
            "personal" -> scopeMode = MemoryScopeMode.GLOBAL
            "space" -> scopeMode = MemoryScopeMode.SPACE
            "agent" -> scopeMode = MemoryScopeMode.AGENT
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MemBg)
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = MemMuted) }
            Column(Modifier.weight(1f)) {
                Text("MEMORY CORE", color = MemText, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text("v${schema.version} • scoped retrieval without AI", color = MemMuted, fontSize = 9.sp)
            }
            IconButton(onClick = { scope.launch { loadAll() } }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = MemPurpleBright) }
        }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MemPurpleBright)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            item {
                Surface(
                    color = MemSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MemPurple.copy(alpha = .22f), RoundedCornerShape(18.dp))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, null, tint = MemPurpleBright)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ACTIVE MEMORY TYPES", color = MemText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(schema.active.joinToString(" • ") { it.uppercase() }, color = MemPurpleBright, fontSize = 8.5.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("PLACEHOLDERS", color = MemMuted, fontSize = 8.sp, letterSpacing = 1.sp)
                        Text(schema.placeholder.joinToString(" • ") { it.uppercase() }, color = MemMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }

            item {
                Surface(color = MemSurface2, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("WRITE MEMORY", color = MemPurpleBright, fontSize = 9.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("personal", "working", "episodic").forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectType(type) },
                                    label = { Text(type.uppercase(), fontSize = 7.5.sp) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("agent", "space").forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectType(type) },
                                    label = { Text(type.uppercase(), fontSize = 7.5.sp) }
                                )
                            }
                        }

                        if (selectedType == "working" || selectedType == "episodic") {
                            Text("SCOPE", color = MemMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MemoryScopeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = scopeMode == mode,
                                        onClick = { scopeMode = mode },
                                        label = { Text(mode.name, fontSize = 7.5.sp) }
                                    )
                                }
                            }
                        }

                        if (scopeMode == MemoryScopeMode.SPACE || selectedType == "space") {
                            Text("SPACE", color = MemMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                items(spaces, key = { it.id }) { space ->
                                    FilterChip(
                                        selected = selectedSpaceId == space.id,
                                        onClick = { selectedSpaceId = space.id },
                                        label = { Text(space.name, fontSize = 7.5.sp) }
                                    )
                                }
                            }
                        }

                        if (scopeMode == MemoryScopeMode.AGENT || selectedType == "agent") {
                            Text("PERSONA", color = MemMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
                            if (personas.isEmpty()) {
                                Text("Create a Persona first.", color = MemMuted, fontSize = 9.sp)
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    items(personas, key = { it.id }) { persona ->
                                        FilterChip(
                                            selected = selectedPersonaId == persona.id,
                                            onClick = { selectedPersonaId = persona.id },
                                            label = { Text(persona.name, fontSize = 7.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = memoryText,
                            onValueChange = { memoryText = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = { Text("Something G-OS should remember…", color = MemMuted, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MemPurpleBright,
                                unfocusedBorderColor = MemPurple.copy(alpha = .28f),
                                focusedTextColor = MemText,
                                unfocusedTextColor = MemText,
                                cursorColor = MemPurpleBright
                            ),
                            shape = RoundedCornerShape(15.dp)
                        )
                        if (selectedType == "working") {
                            Text("WORKING memory expires after ${schema.workingDefaultTtlMinutes / 60}h by default.", color = MemMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Button(
                            onClick = { scope.launch { saveMemory() } },
                            enabled = memoryText.isNotBlank() && !saving && when {
                                selectedType == "space" || scopeMode == MemoryScopeMode.SPACE -> selectedSpaceId != null
                                selectedType == "agent" || scopeMode == MemoryScopeMode.AGENT -> selectedPersonaId != null
                                else -> true
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MemPurple),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Rounded.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (saving) "SAVING…" else "SAVE MEMORY")
                        }
                    }
                }
            }

            item {
                Surface(color = MemSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("RETRIEVAL PREVIEW", color = MemPurpleBright, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("Shows what context would be injected into a Persona before any model call.", color = MemMuted, fontSize = 8.5.sp, modifier = Modifier.padding(top = 3.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            singleLine = true,
                            placeholder = { Text("Query: evidence, money, design…", color = MemMuted, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MemPurpleBright,
                                unfocusedBorderColor = MemPurple.copy(alpha = .28f),
                                focusedTextColor = MemText,
                                unfocusedTextColor = MemText,
                                cursorColor = MemPurpleBright
                            ),
                            shape = RoundedCornerShape(15.dp)
                        )
                        Button(
                            onClick = { scope.launch { retrieve() } },
                            enabled = query.isNotBlank() && !retrieving,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MemPurple),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Rounded.Search, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (retrieving) "RANKING…" else "PREVIEW RETRIEVAL")
                        }
                    }
                }
            }

            if (ranked.isNotEmpty()) {
                item { Text("RANKED CONTEXT", color = MemText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                items(ranked, key = { it.memory.id }) { rankedItem ->
                    RankedMemoryCard(rankedItem)
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT MEMORY", color = MemText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text("${memories.size}", color = MemPurpleBright, fontSize = 10.sp)
                }
            }

            if (memories.isEmpty() && !loading) {
                item { Text("No memories yet.", color = MemMuted, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
            }
            items(memories, key = { it.id }) { memory -> MemoryCard(memory, spaces, personas) }

            notice?.let { message ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(MemGreen.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MemGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(message, color = MemGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            error?.let { message ->
                item { Text(message, color = MemRed, fontSize = 9.5.sp, modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable
private fun MemoryCard(memory: GosMemoryV01Item, spaces: List<GosSpace>, personas: List<GosPersona>) {
    val spaceName = spaces.firstOrNull { it.id == memory.spaceId }?.name
    val personaName = personas.firstOrNull { it.id == memory.personaId }?.name
    Surface(color = MemSurface2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MemPurple.copy(alpha = .13f), shape = RoundedCornerShape(100.dp)) {
                    Text(memory.type.uppercase(), color = MemPurpleBright, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text(memory.scope.uppercase(), color = MemMuted, fontSize = 7.5.sp, modifier = Modifier.weight(1f))
                Text(formatTime(memory.createdAt), color = MemMuted, fontSize = 7.5.sp)
            }
            Text(memory.text, color = MemText, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
            val scopeText = listOfNotNull(spaceName?.let { "SPACE $it" }, personaName?.let { "PERSONA $it" }).joinToString(" • ")
            if (scopeText.isNotBlank()) Text(scopeText, color = MemMuted, fontSize = 7.5.sp, modifier = Modifier.padding(top = 6.dp))
            memory.expiresAt?.let { Text("EXPIRES ${formatTime(it)}", color = MemMuted, fontSize = 7.5.sp, modifier = Modifier.padding(top = 3.dp)) }
        }
    }
}

@Composable
private fun RankedMemoryCard(item: GosMemoryRankedItem) {
    Surface(
        color = MemSurface2,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MemPurple.copy(alpha = .2f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SCORE ${"%.2f".format(Locale.US, item.score)}", color = MemPurpleBright, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(item.memory.type.uppercase(), color = MemMuted, fontSize = 8.sp)
            }
            Text(item.memory.text, color = MemText, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            Text(item.reasons.joinToString(" • "), color = MemMuted, fontSize = 7.5.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun formatTime(ts: Long): String = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ts))
