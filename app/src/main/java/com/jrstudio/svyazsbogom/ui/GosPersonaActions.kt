package com.jrstudio.svyazsbogom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrstudio.svyazsbogom.data.*
import kotlinx.coroutines.launch

private val PersonaPurple = Color(0xFFB76CFF)
private val PersonaRed = Color(0xFFFF9AA8)
private val PersonaMuted = Color(0xFFA79CAF)

@Composable
fun GosPersonaActions(
    identityId: String,
    identitySecret: String,
    persona: GosPersona,
    onReload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { editing = true }, modifier = Modifier.weight(1f), enabled = !busy) {
            Text("EDIT", color = PersonaPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = {
                busy = true; error = null; notice = null
                scope.launch {
                    runCatching {
                        GosPersonaV02Client.api.clonePersona(
                            persona.id,
                            GosPersonaCloneRequest(identityId, identitySecret, "${persona.name} Copy")
                        )
                    }.onSuccess {
                        if (it.ok) { notice = "CLONED"; onReload() } else error = it.error ?: "Clone failed"
                    }.onFailure { error = it.message ?: "Clone failed" }
                    busy = false
                }
            },
            modifier = Modifier.weight(1f),
            enabled = !busy
        ) { Text(if (busy) "…" else "CLONE", color = PersonaPurple, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        TextButton(onClick = { confirmArchive = true }, modifier = Modifier.weight(1f), enabled = !busy) {
            Text("ARCHIVE", color = PersonaRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }

    notice?.let { Text(it, color = PersonaPurple, fontSize = 8.sp) }
    error?.let { Text(it, color = PersonaRed, fontSize = 8.sp) }

    if (editing) {
        var name by remember(persona.id) { mutableStateOf(persona.name) }
        var role by remember(persona.id) { mutableStateOf(persona.role) }
        var personality by remember(persona.id) { mutableStateOf(persona.personality) }
        var planning by remember(persona.id) { mutableStateOf(persona.genome.planning) }
        var communication by remember(persona.id) { mutableStateOf(persona.genome.communicationStyle) }
        var tools by remember(persona.id) { mutableStateOf(persona.tools.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { if (!busy) editing = false },
            title = { Text("EDIT PERSONA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(role, { role = it }, label = { Text("Role") }, singleLine = true)
                    OutlinedTextField(personality, { personality = it }, label = { Text("Personality") }, minLines = 2)
                    OutlinedTextField(planning, { planning = it }, label = { Text("Planning") }, singleLine = true)
                    OutlinedTextField(communication, { communication = it }, label = { Text("Communication style") }, singleLine = true)
                    OutlinedTextField(tools, { tools = it }, label = { Text("Tools, comma-separated") }, singleLine = true)
                    Text("GENOME v${persona.genome.version} • GEN ${persona.genome.generation}", color = PersonaMuted, fontSize = 9.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            runCatching {
                                GosPersonaV02Client.api.updatePersona(
                                    persona.id,
                                    GosPersonaUpdateRequest(
                                        conversationId = identityId,
                                        installSecret = identitySecret,
                                        name = name.trim(),
                                        role = role.trim(),
                                        personality = personality.trim(),
                                        tools = tools.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                        planning = planning.trim(),
                                        communicationStyle = communication.trim()
                                    )
                                )
                            }.onSuccess {
                                if (it.ok) { editing = false; notice = "UPDATED"; onReload() }
                                else error = it.error ?: "Update failed"
                            }.onFailure { error = it.message ?: "Update failed" }
                            busy = false
                        }
                    },
                    enabled = !busy && name.isNotBlank() && role.isNotBlank() && personality.isNotBlank()
                ) { Text(if (busy) "SAVING…" else "SAVE") }
            },
            dismissButton = { TextButton(onClick = { editing = false }, enabled = !busy) { Text("CANCEL") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmArchive = false },
            title = { Text("ARCHIVE ${persona.name}?") },
            text = { Text("History is preserved. The Persona disappears from active lists and can be restored later when resurrection/archives are wired.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            runCatching {
                                GosPersonaV02Client.api.archivePersona(
                                    persona.id,
                                    GosPersonaArchiveRequest(identityId, identitySecret)
                                )
                            }.onSuccess {
                                if (it.ok) { confirmArchive = false; onReload() }
                                else error = it.error ?: "Archive failed"
                            }.onFailure { error = it.message ?: "Archive failed" }
                            busy = false
                        }
                    },
                    enabled = !busy
                ) { Text("ARCHIVE", color = PersonaRed) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }, enabled = !busy) { Text("CANCEL") } }
        )
    }
}
