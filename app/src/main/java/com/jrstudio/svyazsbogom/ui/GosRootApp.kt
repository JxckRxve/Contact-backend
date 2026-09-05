package com.jrstudio.svyazsbogom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrstudio.svyazsbogom.data.*
import com.jrstudio.svyazsbogom.util.installIdentity
import kotlinx.coroutines.launch

private val GosBg = Color(0xFF030106)
private val GosSurface = Color(0xFF0D0713)
private val GosSurface2 = Color(0xFF15101D)
private val GosPurple = Color(0xFF8F4CFF)
private val GosPurpleBright = Color(0xFFB76CFF)
private val GosText = Color(0xFFF8F5FF)
private val GosMuted = Color(0xFFA79CAF)
private val GosGreen = Color(0xFF92E6B7)
private val GosRed = Color(0xFFFF9AA8)

private enum class GosScreen { HOME, DIAGNOSTICS, SPACES, PERSONAS, TASK }

private data class GosDiagnosticItem(
    val label: String,
    val detail: String,
    val ok: Boolean?
)

@Composable
fun GosRootApp() {
    var legacyContact by remember { mutableStateOf(false) }

    if (legacyContact) {
        BackHandler { legacyContact = false }
        Box(Modifier.fillMaxSize()) {
            SvyazSBogomApp()
            Surface(
                color = Color.Black.copy(alpha = .72f),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(10.dp)
                    .clickable { legacyContact = false }
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = GosText, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("G-OS", color = GosText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        GosControlCenter(onOpenLegacy = { legacyContact = true })
    }
}

@Composable
private fun GosControlCenter(onOpenLegacy: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { installIdentity(context) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(GosScreen.HOME) }
    var spaces by remember { mutableStateOf<List<GosSpace>>(emptyList()) }
    var personas by remember { mutableStateOf<List<GosPersona>>(emptyList()) }
    var selectedSpace by remember { mutableStateOf<GosSpace?>(null) }
    var selectedPersona by remember { mutableStateOf<GosPersona?>(null) }
    var stateInfo by remember { mutableStateOf<GosStateResponse?>(null) }
    val configuredProvider = stateInfo?.providers?.firstOrNull { it.configured }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadCore() {
        loading = true
        error = null
        runCatching {
            val state = ApiClient.api.gosState(identity.conversationId, identity.installSecret)
            val spaceList = ApiClient.api.gosSpaces(identity.conversationId, identity.installSecret)
            stateInfo = state
            if (spaceList.ok) spaces = spaceList.items else error = spaceList.error ?: "Core unavailable"
        }.onFailure {
            error = it.message ?: "Cannot connect to G-OS Core"
        }
        loading = false
    }

    suspend fun loadPersonas(space: GosSpace) {
        loading = true
        error = null
        runCatching {
            ApiClient.api.gosPersonas(identity.conversationId, identity.installSecret, space.id)
        }.onSuccess {
            personas = if (it.ok) it.items else emptyList()
            if (!it.ok) error = it.error ?: "Cannot load personas"
        }.onFailure { error = it.message ?: "Cannot load personas" }
        loading = false
    }

    LaunchedEffect(Unit) { loadCore() }

    BackHandler(enabled = screen != GosScreen.HOME) {
        screen = when (screen) {
            GosScreen.TASK -> GosScreen.PERSONAS
            GosScreen.PERSONAS -> GosScreen.SPACES
            GosScreen.SPACES -> GosScreen.HOME
            GosScreen.DIAGNOSTICS -> GosScreen.HOME
            GosScreen.HOME -> GosScreen.HOME
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1A0832), GosBg, Color.Black),
                    radius = 1250f
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (screen) {
            GosScreen.HOME -> GosHome(
                stateInfo = stateInfo,
                modelProvider = configuredProvider,
                loading = loading,
                error = error,
                onSpaces = { screen = GosScreen.SPACES },
                onDiagnostics = { screen = GosScreen.DIAGNOSTICS },
                onLegacy = onOpenLegacy,
                onRefresh = { scope.launch { loadCore() } }
            )
            GosScreen.DIAGNOSTICS -> GosDiagnostics(
                identityId = identity.conversationId,
                identitySecret = identity.installSecret,
                onBack = { screen = GosScreen.HOME },
                onCoreChanged = { scope.launch { loadCore() } }
            )
            GosScreen.SPACES -> GosSpaces(
                spaces = spaces,
                loading = loading,
                error = error,
                onBack = { screen = GosScreen.HOME },
                onSelect = { space ->
                    selectedSpace = space
                    selectedPersona = null
                    screen = GosScreen.PERSONAS
                    scope.launch { loadPersonas(space) }
                }
            )
            GosScreen.PERSONAS -> GosPersonas(
                identityId = identity.conversationId,
                identitySecret = identity.installSecret,
                space = selectedSpace,
                personas = personas,
                loading = loading,
                error = error,
                onBack = { screen = GosScreen.SPACES },
                onReload = { selectedSpace?.let { space -> scope.launch { loadPersonas(space) } } },
                onSelect = { persona ->
                    selectedPersona = persona
                    screen = GosScreen.TASK
                }
            )
            GosScreen.TASK -> GosTaskRunner(
                identityId = identity.conversationId,
                identitySecret = identity.installSecret,
                space = selectedSpace,
                persona = selectedPersona,
                modelProvider = configuredProvider,
                onBack = { screen = GosScreen.PERSONAS },
                onCoreChanged = { scope.launch { loadCore() } }
            )
        }
    }
}

@Composable
private fun GosHome(
    stateInfo: GosStateResponse?,
    modelProvider: GosModelProvider?,
    loading: Boolean,
    error: String?,
    onSpaces: () -> Unit,
    onDiagnostics: () -> Unit,
    onLegacy: () -> Unit,
    onRefresh: () -> Unit
) {
    val counts = stateInfo?.counts ?: GosCounts()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(GosPurple.copy(alpha = .15f), CircleShape)
                    .border(1.dp, GosPurpleBright.copy(alpha = .45f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("G", color = GosText, fontSize = 20.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("G-OS", color = GosText, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("PERSONAL AI OPERATING SYSTEM", color = GosPurpleBright, fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh", tint = GosMuted) }
        }

        Spacer(Modifier.height(22.dp))
        Text("CONTROL CENTER", color = GosMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Build the system from real loops.", color = GosText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("USER → SPACE → PERSONA → TASK → RESULT → MEMORY → EXPERIENCE → FITNESS", color = GosPurpleBright, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GosMetric("SPACES", counts.spaces.toString(), Modifier.weight(1f))
            GosMetric("PERSONAS", counts.personas.toString(), Modifier.weight(1f))
            GosMetric("TASKS", counts.tasks.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GosMetric("MEMORY", counts.memories.toString(), Modifier.weight(1f))
            GosMetric("EXPERIENCE", counts.experienceEvents.toString(), Modifier.weight(1f))
            GosMetric("FITNESS", counts.fitnessRecords.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        GosModelStatus(modelProvider = modelProvider, loading = loading)

        Spacer(Modifier.height(14.dp))
        GosNavCard(
            icon = Icons.Rounded.MonitorHeart,
            title = "CORE DIAGNOSTICS",
            subtitle = "Verify auth, Render Core, Spaces, Persona API and ModelProvider",
            badge = "SELF-TEST",
            onClick = onDiagnostics
        )
        GosNavCard(
            icon = Icons.Rounded.Hub,
            title = "SPACES / PERSONAS",
            subtitle = "Run the first real G-OS Core loop",
            badge = if (loading) "CONNECTING" else "CORE",
            onClick = onSpaces
        )
        GosNavCard(
            icon = Icons.Rounded.Link,
            title = "CONTACT DNA",
            subtitle = "Legacy Persona, Developer, Unload, Higher",
            badge = "LEGACY",
            onClick = onLegacy
        )
        GosNavCard(
            icon = Icons.Rounded.Paid,
            title = "MONEY",
            subtitle = "First Evolution Lab after Core is READY",
            badge = "LOCKED",
            onClick = {}
        )
        GosNavCard(
            icon = Icons.Rounded.Science,
            title = "EVOLUTION / REALITY / MULTIVERSE",
            subtitle = "Architecture retained. Not simulated as finished features.",
            badge = "LAB",
            onClick = {}
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = GosRed, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "CORE ${stateInfo?.version ?: "0.1"} • BUILD → RUN → TEST → FIX → SAVE → NEXT",
            color = GosMuted,
            fontSize = 8.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun GosDiagnostics(
    identityId: String,
    identitySecret: String,
    onBack: () -> Unit,
    onCoreChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var lastRun by remember { mutableStateOf<Long?>(null) }
    var items by remember { mutableStateOf<List<GosDiagnosticItem>>(emptyList()) }
    var fatalError by remember { mutableStateOf<String?>(null) }

    suspend fun runSelfTest() {
        running = true
        fatalError = null
        items = emptyList()
        val result = mutableListOf<GosDiagnosticItem>()

        runCatching {
            val core = ApiClient.api.gosState(identityId, identitySecret)
            if (!core.ok) error(core.error ?: "Core state returned not-ok")
            result += GosDiagnosticItem("INSTALL IDENTITY / AUTH", "Install-scoped identity accepted by Core", true)
            result += GosDiagnosticItem("RENDER / G-OS CORE", "Core v${core.version ?: "0.1"} responded", true)

            val spaces = ApiClient.api.gosSpaces(identityId, identitySecret)
            if (!spaces.ok) error(spaces.error ?: "Spaces API returned not-ok")
            val home = spaces.items.firstOrNull { it.key == "HOME" }
            result += GosDiagnosticItem(
                "BASE SPACES",
                if (home != null) "${spaces.items.size} Spaces available; HOME present" else "HOME Space missing",
                home != null
            )

            if (home != null) {
                val personas = ApiClient.api.gosPersonas(identityId, identitySecret, home.id)
                result += GosDiagnosticItem(
                    "PERSONA API",
                    if (personas.ok) "Connected; ${personas.items.size} Persona(s) in HOME" else (personas.error ?: "Persona API failed"),
                    personas.ok
                )
            } else {
                result += GosDiagnosticItem("PERSONA API", "Skipped because HOME is missing", false)
            }

            val provider = core.providers.firstOrNull { it.configured }
            result += GosDiagnosticItem(
                "MODEL PROVIDER",
                if (provider != null) "${provider.label} • ${provider.model ?: "model"} • CONNECTED" else "API WAITING — expected until the secret key is available",
                if (provider != null) true else null
            )
            result += GosDiagnosticItem(
                "FULL TASK LOOP",
                if (provider != null) "Ready for explicit real Task Runner test" else "Blocked only by external model API key",
                if (provider != null) true else null
            )
        }.onFailure {
            fatalError = it.message ?: "Core diagnostics failed"
            if (result.isEmpty()) {
                result += GosDiagnosticItem("CORE CONNECTION", fatalError ?: "Connection failed", false)
            }
        }

        items = result
        lastRun = System.currentTimeMillis()
        running = false
        onCoreChanged()
    }

    LaunchedEffect(Unit) { runSelfTest() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GosHeader("CORE DIAGNOSTICS", "No paid AI calls", onBack)

        Surface(
            color = GosSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, GosPurple.copy(alpha = .2f), RoundedCornerShape(18.dp))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("SELF-TEST", color = GosPurpleBright, fontSize = 9.sp, letterSpacing = 1.2.sp)
                Text("Checks the real runtime instead of trusting UI buttons.", color = GosText, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                Text("Model execution is never triggered here, so this test cannot spend API tokens.", color = GosMuted, fontSize = 9.5.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { scope.launch { runSelfTest() } },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GosPurple),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.MonitorHeart, null)
            Spacer(Modifier.width(7.dp))
            Text(if (running) "TESTING CORE…" else "RUN SELF-TEST")
        }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = GosPurpleBright)
        lastRun?.let { Text("Last run: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))}", color = GosMuted, fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp)) }
        fatalError?.let { Text(it, color = GosRed, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp)) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            items(items, key = { it.label }) { item ->
                Surface(color = GosSurface2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (item.ok) {
                                true -> Icons.Rounded.CheckCircle
                                false -> Icons.Rounded.Cancel
                                null -> Icons.Rounded.HourglassTop
                            },
                            null,
                            tint = when (item.ok) {
                                true -> GosGreen
                                false -> GosRed
                                null -> GosPurpleBright
                            },
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, color = GosText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Text(item.detail, color = GosMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        GosBadge(when (item.ok) { true -> "PASS"; false -> "FAIL"; null -> "WAIT" })
                    }
                }
            }
        }
    }
}

@Composable
private fun GosSpaces(
    spaces: List<GosSpace>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSelect: (GosSpace) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GosHeader("SPACES", "Choose operating context", onBack)
        if (loading && spaces.isEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GosPurple)
        error?.let { Text(it, color = GosRed, fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(start = 0.dp, top = 12.dp, end = 0.dp, bottom = 30.dp)) {
            items(spaces.sortedWith(compareBy<GosSpace> { it.status != "CORE" }.thenBy { it.name }), key = { it.id }) { space ->
                Surface(
                    color = GosSurface.copy(alpha = .94f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GosPurple.copy(alpha = .2f), RoundedCornerShape(20.dp)).clickable { onSelect(space) }
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (space.status == "LAB") Icons.Rounded.Science else Icons.Rounded.GridView, null, tint = if (space.status == "LAB") GosMuted else GosPurpleBright)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(space.name, color = GosText, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                            Text(space.key, color = GosMuted, fontSize = 9.sp)
                        }
                        GosBadge(space.status)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = GosMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun GosPersonas(
    identityId: String,
    identitySecret: String,
    space: GosSpace?,
    personas: List<GosPersona>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onSelect: (GosPersona) -> Unit
) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        GosHeader(space?.name ?: "PERSONAS", "Persona System", onBack)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${personas.size} ACTIVE", color = GosMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = !creating }) {
                Icon(if (creating) Icons.Rounded.Close else Icons.Rounded.Add, null, tint = GosPurpleBright)
                Spacer(Modifier.width(4.dp))
                Text(if (creating) "CANCEL" else "NEW PERSONA", color = GosPurpleBright, fontSize = 10.sp)
            }
        }

        if (creating && space != null) {
            Surface(color = GosSurface2, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("AGENT GENOME v0.1", color = GosPurpleBright, fontSize = 9.sp, letterSpacing = 1.sp)
                    GosInput(name, { name = it }, "Name", singleLine = true)
                    GosInput(role, { role = it }, "Role — analyst, designer, hunter…", singleLine = true)
                    GosInput(personality, { personality = it }, "Personality / operating style", minLines = 2)
                    Button(
                        onClick = {
                            saving = true
                            localError = null
                            scope.launch {
                                runCatching {
                                    ApiClient.api.gosCreatePersona(
                                        GosPersonaCreateRequest(
                                            conversationId = identityId,
                                            installSecret = identitySecret,
                                            spaceId = space.id,
                                            name = name.trim(),
                                            role = role.trim(),
                                            personality = personality.trim()
                                        )
                                    )
                                }.onSuccess {
                                    if (it.ok) {
                                        creating = false
                                        name = ""; role = ""; personality = ""
                                        onReload()
                                    } else localError = it.error ?: "Persona creation failed"
                                }.onFailure { localError = it.message ?: "Persona creation failed" }
                                saving = false
                            }
                        },
                        enabled = name.isNotBlank() && role.isNotBlank() && personality.isNotBlank() && !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = GosPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (saving) "CREATING…" else "CREATE PERSONA") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        (localError ?: error)?.let { Text(it, color = GosRed, fontSize = 10.sp, modifier = Modifier.padding(vertical = 5.dp)) }
        if (loading && personas.isEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GosPurple)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 30.dp)) {
            items(personas, key = { it.id }) { persona ->
                Surface(
                    color = GosSurface.copy(alpha = .94f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, GosPurple.copy(alpha = .22f), RoundedCornerShape(20.dp)).clickable { onSelect(persona) }
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(GosPurple.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Psychology, null, tint = GosPurpleBright, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(persona.name, color = GosText, fontWeight = FontWeight.Bold)
                                Text(persona.role.uppercase(), color = GosPurpleBright, fontSize = 9.sp)
                            }
                            GosBadge("LVL ${persona.level}")
                        }
                        Text(persona.personality, color = GosMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text("XP ${persona.xp} • GEN ${persona.genome.generation} • ${persona.genome.planning.uppercase()}", color = GosMuted.copy(alpha = .75f), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (personas.isEmpty() && !loading && !creating) {
                item {
                    Text("No Persona in this Space yet. Create the first specialist.", color = GosMuted, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GosTaskRunner(
    identityId: String,
    identitySecret: String,
    space: GosSpace?,
    persona: GosPersona?,
    modelProvider: GosModelProvider?,
    onBack: () -> Unit,
    onCoreChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<GosTaskRunResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        GosHeader(persona?.name ?: "TASK", space?.name ?: "Task Runner", onBack)
        if (persona == null || space == null) {
            Text("Space or Persona missing.", color = GosRed)
            return@Column
        }

        Surface(color = GosSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, null, tint = GosPurpleBright)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(persona.role.uppercase(), color = GosPurpleBright, fontSize = 9.sp)
                    Text("${space.name} / ${persona.genome.communicationStyle}", color = GosText, fontSize = 12.sp)
                }
                GosBadge("GEN ${persona.genome.generation}")
            }
        }

        Spacer(Modifier.height(10.dp))
        GosModelStatus(modelProvider = modelProvider, loading = false)

        Spacer(Modifier.height(14.dp))
        Text("TASK", color = GosMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        GosInput(input, { input = it }, "Give this Persona a concrete task…", minLines = 5)
        Button(
            onClick = {
                running = true
                response = null
                error = null
                scope.launch {
                    runCatching {
                        ApiClient.api.gosRunTask(
                            GosTaskRunRequest(
                                conversationId = identityId,
                                installSecret = identitySecret,
                                spaceId = space.id,
                                personaId = persona.id,
                                input = input.trim()
                            )
                        )
                    }.onSuccess {
                        response = it
                        if (!it.ok) error = it.error ?: "Task failed"
                        else onCoreChanged()
                    }.onFailure { error = it.message ?: "Task failed" }
                    running = false
                }
            },
            enabled = input.isNotBlank() && !running && modelProvider != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GosPurple),
            shape = RoundedCornerShape(17.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    running -> "RUNNING CORE LOOP…"
                    modelProvider == null -> "WAITING FOR API"
                    else -> "RUN TASK"
                }
            )
        }

        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = GosPurpleBright)
        if (modelProvider == null) {
            Text("Core is ready. AI execution is locked until the API key is connected.", color = GosMuted, fontSize = 9.5.sp, modifier = Modifier.padding(top = 8.dp))
        }
        error?.let { Text(it, color = GosRed, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp)) }

        response?.takeIf { it.ok }?.let { out ->
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    GosResultBlock("RESULT", out.task?.result ?: "", Icons.Rounded.AutoAwesome)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GosSignal("MEMORY", if (out.memory != null) "SAVED" else "SKIPPED", out.memory != null, Modifier.weight(1f))
                        GosSignal("EXPERIENCE", if (out.experience != null) "LOGGED" else "NONE", out.experience != null, Modifier.weight(1f))
                        GosSignal("FITNESS", if ((out.fitness?.success ?: 0.0) > 0) "SUCCESS" else "FAIL", (out.fitness?.success ?: 0.0) > 0, Modifier.weight(1f))
                    }
                }
                item {
                    Surface(color = GosSurface2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("FITNESS RECORD", color = GosPurpleBright, fontSize = 9.sp)
                            Text("success ${out.fitness?.success ?: 0.0} • ${out.fitness?.timeMs ?: 0} ms • cost ${out.fitness?.cost ?: 0.0}", color = GosText, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                            Text("XP ${out.persona?.xp ?: persona.xp} • LEVEL ${out.persona?.level ?: persona.level}", color = GosMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GosHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = GosMuted) }
        Column(Modifier.weight(1f)) {
            Text(title, color = GosText, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            Text(subtitle, color = GosMuted, fontSize = 9.sp)
        }
        Text("G-OS", color = GosPurpleBright, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun GosMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = GosSurface.copy(alpha = .9f), shape = RoundedCornerShape(15.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(value, color = GosText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, color = GosMuted, fontSize = 7.5.sp, letterSpacing = .6.sp)
        }
    }
}

@Composable
private fun GosModelStatus(modelProvider: GosModelProvider?, loading: Boolean) {
    val online = modelProvider != null
    Surface(
        color = if (online) GosGreen.copy(alpha = .08f) else GosSurface2,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (online) GosGreen.copy(alpha = .28f) else GosPurple.copy(alpha = .18f), RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (online) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                null,
                tint = if (online) GosGreen else GosMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        loading -> "MODEL CHECKING"
                        online -> "MODEL CONNECTED"
                        else -> "MODEL OFFLINE / API WAITING"
                    },
                    color = if (online) GosGreen else GosText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .6.sp
                )
                Text(
                    if (online) "${modelProvider?.label} • ${modelProvider?.model ?: "model"}" else "Core works without pretending an AI call is available.",
                    color = GosMuted,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            GosBadge(if (online) "CONNECTED" else "WAITING")
        }
    }
}

@Composable
private fun GosNavCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, badge: String, onClick: () -> Unit) {
    Surface(
        color = GosSurface.copy(alpha = .92f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, GosPurple.copy(alpha = .18f), RoundedCornerShape(18.dp)).clickable { onClick() }
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (badge == "LOCKED" || badge == "LAB") GosMuted else GosPurpleBright, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = GosText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = GosMuted, fontSize = 9.5.sp, maxLines = 2)
            }
            GosBadge(badge)
        }
    }
}

@Composable
private fun GosBadge(text: String) {
    Surface(color = GosPurple.copy(alpha = .13f), shape = RoundedCornerShape(100.dp)) {
        Text(text, color = GosPurpleBright, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun GosInput(value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = false, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 8,
        placeholder = { Text(placeholder, color = GosMuted, fontSize = 11.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GosPurpleBright,
            unfocusedBorderColor = GosPurple.copy(alpha = .28f),
            cursorColor = GosPurpleBright,
            focusedTextColor = GosText,
            unfocusedTextColor = GosText,
            focusedContainerColor = GosSurface,
            unfocusedContainerColor = GosSurface
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GosResultBlock(label: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = GosSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = GosPurpleBright, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = GosPurpleBright, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Text(text, color = GosText, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun GosSignal(label: String, value: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(color = GosSurface2, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = GosMuted, fontSize = 7.sp)
            Text(value, color = if (positive) GosGreen else GosRed, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
