package com.jrstudio.svyazsbogom.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrstudio.svyazsbogom.AppConfig
import com.jrstudio.svyazsbogom.data.*
import com.jrstudio.svyazsbogom.util.installIdentity
import com.jrstudio.svyazsbogom.util.mayNeedUrgentHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { HOME, DEVELOPER, PERSONA, UNLOAD, HIGHER, SETTINGS }
private enum class ThemeMode { VIOLET_VOID, OBSIDIAN, MIDNIGHT, SOFT_LAVENDER }
private enum class BackgroundMode { CLEAN, DUST, STARS, FOG }
private enum class MotionMode { REDUCED, NORMAL, CINEMATIC }

data class ContactPalette(
    val bg0: Color,
    val bg1: Color,
    val surface: Color,
    val surface2: Color,
    val accent: Color,
    val accentBright: Color,
    val soft: Color,
    val text: Color,
    val muted: Color,
    val danger: Color = Color(0xFFFF99A7)
)

private data class UiSettings(
    val theme: ThemeMode = ThemeMode.VIOLET_VOID,
    val background: BackgroundMode = BackgroundMode.STARS,
    val motion: MotionMode = MotionMode.NORMAL,
    val accentIntensity: Float = 1f,
    val haptics: Boolean = true,
    val messageAnimations: Boolean = true,
    val fontScale: Float = 1f
)

private class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("contact_ui_v04", Context.MODE_PRIVATE)

    fun load(): UiSettings = UiSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.VIOLET_VOID.name)!!) }.getOrDefault(ThemeMode.VIOLET_VOID),
        background = runCatching { BackgroundMode.valueOf(prefs.getString("background", BackgroundMode.STARS.name)!!) }.getOrDefault(BackgroundMode.STARS),
        motion = runCatching { MotionMode.valueOf(prefs.getString("motion", MotionMode.NORMAL.name)!!) }.getOrDefault(MotionMode.NORMAL),
        accentIntensity = prefs.getFloat("accent", 1f),
        haptics = prefs.getBoolean("haptics", true),
        messageAnimations = prefs.getBoolean("messageAnimations", true),
        fontScale = prefs.getFloat("fontScale", 1f)
    )

    fun save(s: UiSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("background", s.background.name)
            .putString("motion", s.motion.name)
            .putFloat("accent", s.accentIntensity)
            .putBoolean("haptics", s.haptics)
            .putBoolean("messageAnimations", s.messageAnimations)
            .putFloat("fontScale", s.fontScale)
            .apply()
    }
}

private fun paletteFor(mode: ThemeMode): ContactPalette = when (mode) {
    ThemeMode.VIOLET_VOID -> ContactPalette(
        Color(0xFF030106), Color(0xFF08020F), Color(0xFF100A18), Color(0xFF17101F),
        Color(0xFF8F4CFF), Color(0xFFB76CFF), Color(0xFFD7B6FF), Color(0xFFF8F5FF), Color(0xFFA79CAF)
    )
    ThemeMode.OBSIDIAN -> ContactPalette(
        Color.Black, Color(0xFF070707), Color(0xFF101010), Color(0xFF181818),
        Color(0xFFEAEAEA), Color.White, Color(0xFFB7B7B7), Color.White, Color(0xFF969696)
    )
    ThemeMode.MIDNIGHT -> ContactPalette(
        Color(0xFF02040B), Color(0xFF060B18), Color(0xFF0B1223), Color(0xFF111B31),
        Color(0xFF6D78FF), Color(0xFF929BFF), Color(0xFFB8BEFF), Color(0xFFF5F7FF), Color(0xFF99A2BD)
    )
    ThemeMode.SOFT_LAVENDER -> ContactPalette(
        Color(0xFF0A0710), Color(0xFF15101D), Color(0xFF1B1424), Color(0xFF251B31),
        Color(0xFFB49BFF), Color(0xFFD0C0FF), Color(0xFFE2D8FF), Color(0xFFFBF9FF), Color(0xFFB8AEC2)
    )
}

@Composable
fun SvyazSBogomApp() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showSplash by remember { mutableStateOf(true) }
    val palette = paletteFor(settings.theme)

    fun updateSettings(next: UiSettings) {
        settings = next
        store.save(next)
    }

    LaunchedEffect(Unit) {
        delay(if (settings.motion == MotionMode.REDUCED) 700 else 1800)
        showSplash = false
    }

    ContactBackground(settings, palette) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (showSplash) {
                SplashScreen(settings, palette)
            } else {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        when (settings.motion) {
                            MotionMode.REDUCED -> fadeIn(tween(120)) togetherWith fadeOut(tween(100))
                            MotionMode.NORMAL -> (fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 18 }) togetherWith fadeOut(tween(160))
                            MotionMode.CINEMATIC -> (fadeIn(tween(340)) + slideInVertically(tween(380)) { it / 12 } + scaleIn(tween(380), initialScale = .985f)) togetherWith fadeOut(tween(220))
                        }
                    },
                    label = "contact-navigation"
                ) { target ->
                    when (target) {
                        Screen.HOME -> HomeScreen(settings, palette, onOpen = { screen = it }, onSettings = { screen = Screen.SETTINGS })
                        Screen.DEVELOPER -> HumanContactScreen(
                            title = "DEVELOPER",
                            subtitle = "Связь с разработчиком",
                            channel = "developer",
                            intro = "Не по приложению. По жизни.",
                            badge = "LIVE HUMAN",
                            settings = settings,
                            palette = palette,
                            onBack = { screen = Screen.HOME },
                            onSettings = { screen = Screen.SETTINGS }
                        )
                        Screen.HIGHER -> HumanContactScreen(
                            title = "HIGHER",
                            subtitle = "Выше",
                            channel = "higher",
                            intro = "Скажи то, что не можешь сказать никому.",
                            badge = "PRIVATE",
                            settings = settings,
                            palette = palette,
                            onBack = { screen = Screen.HOME },
                            onSettings = { screen = Screen.SETTINGS }
                        )
                        Screen.PERSONA -> PersonaScreen(settings, palette, onBack = { screen = Screen.HOME }, onSettings = { screen = Screen.SETTINGS })
                        Screen.UNLOAD -> UnloadScreen(settings, palette, onBack = { screen = Screen.HOME }, onSettings = { screen = Screen.SETTINGS })
                        Screen.SETTINGS -> SettingsScreen(settings, palette, onChange = ::updateSettings, onBack = { screen = Screen.HOME })
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(settings: UiSettings, palette: ContactPalette) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(if (settings.motion == MotionMode.REDUCED) 180 else 650)) + scaleIn(tween(650), initialScale = .92f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContactMark(126.dp, palette, animated = settings.motion != MotionMode.REDUCED)
                Spacer(Modifier.height(22.dp))
                Text("C O N T A C T", color = palette.text, fontSize = 31.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(7.dp))
                Text("ESTABLISH CONTACT", color = palette.soft, fontSize = 11.sp, letterSpacing = 3.sp)
            }
        }
    }
}

@Composable
private fun HomeScreen(settings: UiSettings, palette: ContactPalette, onOpen: (Screen) -> Unit, onSettings: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки", tint = palette.soft) }
        }

        ContactMark(86.dp, palette, animated = settings.motion != MotionMode.REDUCED)
        Spacer(Modifier.height(8.dp))
        Text("C O N T A C T", color = palette.text, fontSize = 26.sp, letterSpacing = 4.5.sp, fontWeight = FontWeight.Light)
        Text("ESTABLISH CONTACT", color = palette.soft, fontSize = 10.sp, letterSpacing = 2.5.sp)
        Spacer(Modifier.height(10.dp))
        Text("Скажи. Тебя услышат.", color = palette.text, fontFamily = FontFamily.Serif, fontSize = (22 * settings.fontScale).sp)
        Spacer(Modifier.height(16.dp))

        Entrance(entered, 0, settings.motion) {
            PortalCard("💬", "DEVELOPER", "Связь с разработчиком", "Не по приложению. По жизни.", "FREE", palette, settings) { onOpen(Screen.DEVELOPER) }
        }
        Entrance(entered, 70, settings.motion) {
            PortalCard("🪞", "PERSONA", "Любая личность", "Создай ИИ-образ человека из своих воспоминаний.", "AI", palette, settings) { onOpen(Screen.PERSONA) }
        }
        Entrance(entered, 140, settings.motion) {
            PortalCard("🌫", "UNLOAD", "Сбросить груз", "Персональная приватная сессия 45–60 минут.", "5 000 ₽", palette, settings) { onOpen(Screen.UNLOAD) }
        }
        Entrance(entered, 210, settings.motion) {
            PortalCard("✦", "HIGHER", "Выше", "Для слов, которые трудно сказать кому-либо.", "PRIVATE", palette, settings) { onOpen(Screen.HIGHER) }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Анонимно • конфиденциально • человеческий контакт",
            color = palette.muted,
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun Entrance(visible: Boolean, delayMs: Int, motion: MotionMode, content: @Composable () -> Unit) {
    val duration = if (motion == MotionMode.CINEMATIC) 340 else 230
    AnimatedVisibility(
        visible = visible,
        enter = if (motion == MotionMode.REDUCED) fadeIn(tween(120)) else fadeIn(tween(duration, delayMillis = delayMs)) + slideInVertically(tween(duration, delayMillis = delayMs)) { 18 }
    ) {
        content()
    }
}

@Composable
private fun PortalCard(
    emoji: String,
    title: String,
    subtitle: String,
    detail: String,
    badge: String,
    palette: ContactPalette,
    settings: UiSettings,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = palette.surface.copy(alpha = .92f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .border(1.dp, palette.accent.copy(alpha = .23f * settings.accentIntensity.coerceIn(.5f, 1.4f)), RoundedCornerShape(22.dp))
            .clickable {
                if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(palette.accent.copy(alpha = .11f), CircleShape)
                    .border(1.dp, palette.accentBright.copy(alpha = .43f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 21.sp) }

            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = palette.text, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    ContactBadge(badge, palette)
                }
                Text(subtitle, color = palette.soft, fontSize = 12.sp)
                Text(detail, color = palette.muted, fontSize = 10.5.sp, lineHeight = 14.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = palette.soft)
        }
    }
}

@Composable
private fun ContactBadge(text: String, palette: ContactPalette) {
    Surface(color = palette.accent.copy(alpha = .12f), shape = RoundedCornerShape(100.dp)) {
        Text(text, color = palette.soft, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun HumanContactScreen(
    title: String,
    subtitle: String,
    channel: String,
    intro: String,
    badge: String,
    settings: UiSettings,
    palette: ContactPalette,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    val identity = remember { installIdentity(context) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        attachmentUri = uri
    }

    suspend fun refresh() {
        runCatching { ApiClient.api.conversation(identity.conversationId, identity.installSecret, channel) }
            .onSuccess { if (it.ok) messages = it.messages }
    }

    LaunchedEffect(channel) {
        while (true) {
            refresh()
            delay(7000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Header(title, subtitle, badge, palette, onBack, onSettings)

        val lastIsHuman = messages.lastOrNull()?.role == "human"
        val status = when {
            sending -> "СООБЩЕНИЕ ОТПРАВЛЯЕТСЯ"
            messages.isEmpty() -> "ГОТОВ К CONTACT"
            lastIsHuman -> "CONTACT УСТАНОВЛЕН"
            else -> "ОЖИДАЕМ ОТВЕТ"
        }
        ContactStatusPill(status, palette)

        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactMark(104.dp, palette, animated = settings.motion == MotionMode.CINEMATIC)
                Spacer(Modifier.height(18.dp))
                Text(intro, color = palette.text, fontFamily = FontFamily.Serif, fontSize = (23 * settings.fontScale).sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(9.dp))
                Text(
                    if (channel == "developer") "Отвечает живой человек. Первый контакт бесплатно." else "Поэтический приватный режим. Не буквальная связь с высшими силами.",
                    color = palette.muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { MessageBubble(it, palette) }
            }
        }

        if (urgent) CrisisCard(palette)

        attachmentUri?.let { uri ->
            AttachmentChip(uri, palette) { attachmentUri = null }
            Spacer(Modifier.height(7.dp))
        }

        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(
                onClick = {
                    filePicker.launch(arrayOf(
                        "image/*",
                        "application/pdf",
                        "text/plain",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    ))
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(palette.surface2, CircleShape)
                    .border(1.dp, palette.accent.copy(alpha = .25f), CircleShape)
            ) { Icon(Icons.Rounded.AttachFile, "Вложить файл", tint = palette.soft) }

            Spacer(Modifier.width(8.dp))

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 5,
                placeholder = { Text("Скажи, что у тебя на сердце…", color = palette.muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentBright,
                    unfocusedBorderColor = palette.accent.copy(alpha = .25f),
                    cursorColor = palette.soft,
                    focusedContainerColor = palette.surface.copy(alpha = .86f),
                    unfocusedContainerColor = palette.surface.copy(alpha = .70f)
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val text = draft.trim()
                if (text.isNotEmpty() && !sending) {
                    urgent = urgent || mayNeedUrgentHelp(text)
                    sending = true
                    if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        runCatching {
                            ApiClient.api.sendMessage(SendMessageRequest(identity.conversationId, identity.installSecret, text, channel = channel))
                        }.onSuccess {
                            draft = ""
                            attachmentUri = null
                            refresh()
                        }.onFailure {
                            error = "Не удалось установить контакт. Попробуй ещё раз."
                        }
                        sending = false
                    }
                }
            },
            enabled = draft.isNotBlank() && !sending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Send, null)
            Spacer(Modifier.width(8.dp))
            Text(if (messages.isEmpty()) "УСТАНОВИТЬ CONTACT" else "ОТПРАВИТЬ", letterSpacing = 1.sp)
        }

        error?.let { Text(it, color = palette.danger, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)) }
        Text(
            "CONTACT не заменяет медицинскую, психотерапевтическую или экстренную помощь.",
            color = palette.muted,
            fontSize = 8.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun AttachmentChip(uri: Uri, palette: ContactPalette, onRemove: () -> Unit) {
    Surface(color = palette.surface2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, tint = palette.soft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(uri.lastPathSegment ?: "Вложение", color = palette.text, fontSize = 11.sp, maxLines = 1)
                Text("Выбрано локально • загрузка на сервер появится в следующем обновлении", color = palette.muted, fontSize = 8.5.sp)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "Убрать", tint = palette.muted) }
        }
    }
}

@Composable
private fun PersonaScreen(settings: UiSettings, palette: ContactPalette, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { installIdentity(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var speech by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf("") }
    var neverSay by remember { mutableStateOf("") }
    var personaId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val prompts = listOf(
        Triple("Как его звали?", "Имя или то, как ты его называл.", name),
        Triple("Кем он был для тебя?", "Друг, мама, бывший партнёр, персонаж — как ты это ощущал.", relationship),
        Triple("Каким ты его помнишь?", "Характер, реакции, ценности, юмор.", description),
        Triple("Как он говорил?", "Манера речи, обращения, любимые выражения.", speech),
        Triple("Что между вами важно?", "Истории, события, общие воспоминания.", memories),
        Triple("Чего он точно не сказал бы?", "Границы делают PERSONA точнее.", neverSay)
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
        Header("PERSONA", "Создать личность", "AI", palette, onBack, onSettings)

        if (personaId == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Восстановим образ постепенно.", color = palette.text, fontFamily = FontFamily.Serif, fontSize = (26 * settings.fontScale).sp)
                Text("PERSONA — ИИ-образ из твоих воспоминаний. Это не сам человек.", color = palette.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp, bottom = 20.dp))

                LinearProgressIndicator(
                    progress = { (step + 1f) / prompts.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = palette.accentBright,
                    trackColor = palette.surface2
                )
                Spacer(Modifier.height(18.dp))

                val question = prompts[step].first
                val hint = prompts[step].second
                val value = when (step) { 0 -> name; 1 -> relationship; 2 -> description; 3 -> speech; 4 -> memories; else -> neverSay }

                Text(question, color = palette.soft, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, color = palette.muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        when (step) { 0 -> name = it; 1 -> relationship = it; 2 -> description = it; 3 -> speech = it; 4 -> memories = it; 5 -> neverSay = it }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accentBright, unfocusedBorderColor = palette.accent.copy(alpha = .25f)),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (step < prompts.lastIndex) step++ else {
                            loading = true
                            scope.launch {
                                runCatching {
                                    ApiClient.api.createPersona(PersonaCreateRequest(identity.conversationId, identity.installSecret, name, relationship, description, speech, memories, neverSay))
                                }.onSuccess { personaId = it.personaId }
                                    .onFailure { error = "Не удалось сохранить PERSONA." }
                                loading = false
                            }
                        }
                    },
                    enabled = value.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (step == prompts.lastIndex) "СОЗДАТЬ PERSONA" else "ДАЛЬШЕ") }

                Spacer(Modifier.height(14.dp))
                Surface(color = palette.surface.copy(alpha = .8f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, palette.accent.copy(alpha = .18f), RoundedCornerShape(18.dp))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🪞 PERSONA VISUAL — СЛЕДУЮЩИЙ ШАГ", color = palette.soft, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("По описанию будет постепенно собираться визуальный образ человека, а потом можно будет сравнить его с фото, которое ты сам загрузишь.", color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
                error?.let { Text(it, color = palette.danger, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        } else {
            Text(name.ifBlank { "PERSONA" }, color = palette.text, fontFamily = FontFamily.Serif, fontSize = 28.sp, modifier = Modifier.padding(top = 10.dp))
            Text("ИИ-образ из твоих воспоминаний", color = palette.soft, fontSize = 10.sp)

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(chat) { (role, text) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (role == "you") Arrangement.End else Arrangement.Start) {
                        Surface(color = if (role == "you") palette.accent.copy(alpha = .25f) else palette.surface2, shape = RoundedCornerShape(17.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                            Text(text, color = palette.text, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Напиши ${name.ifBlank { "PERSONA" }}…", color = palette.muted) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.accentBright, unfocusedBorderColor = palette.accent.copy(alpha = .25f)),
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val text = draft.trim(); val id = personaId
                    if (text.isNotEmpty() && id != null && !loading) {
                        chat = chat + ("you" to text); draft = ""; loading = true
                        scope.launch {
                            runCatching { ApiClient.api.personaMessage(PersonaMessageRequest(identity.conversationId, identity.installSecret, id, text)) }
                                .onSuccess { chat = chat + ("persona" to (it.reply ?: "AI-провайдер пока не подключён.")) }
                                .onFailure { chat = chat + ("persona" to "Контакт с AI-провайдером пока недоступен.") }
                            loading = false
                        }
                    }
                },
                enabled = draft.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) { Text("УСТАНОВИТЬ CONTACT") }
            Text("Это симуляция личности, а не реальный человек или его сознание.", color = palette.muted, fontSize = 8.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun UnloadScreen(settings: UiSettings, palette: ContactPalette, onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
        Header("UNLOAD", "Сбросить груз", "5 000 ₽", palette, onBack, onSettings)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ContactRing(140.dp, palette)
            Spacer(Modifier.height(20.dp))
            Text("Не неси это один.", color = palette.text, fontFamily = FontFamily.Serif, fontSize = (28 * settings.fontScale).sp)
            Spacer(Modifier.height(7.dp))
            Text("Приватная персональная сессия с живым собеседником.", color = palette.muted, textAlign = TextAlign.Center, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Surface(color = palette.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().border(1.dp, palette.accent.copy(alpha = .28f), RoundedCornerShape(22.dp))) {
                Column(Modifier.padding(19.dp)) {
                    Text("45–60 МИНУТ", color = palette.soft, letterSpacing = 1.2.sp, fontSize = 11.sp)
                    Text("5 000 ₽", color = palette.text, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(12.dp))
                    Text("✦ Анонимно\n✦ Конфиденциально\n✦ Без осуждения\n✦ Живой собеседник\n✦ Можно просто выговориться", color = palette.text, lineHeight = 24.sp, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.UNLOAD_PAYMENT_URL))) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(18.dp)
            ) { Text("ОПЛАТИТЬ 5 000 ₽", fontWeight = FontWeight.SemiBold) }
            Text("Оплата — за персональную сессию и время собеседника, а не за прощение, лечение или религиозную услугу.", color = palette.muted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun SettingsScreen(settings: UiSettings, palette: ContactPalette, onChange: (UiSettings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
        Header("SETTINGS", "Внешний вид и поведение", "v0.4", palette, onBack, null)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SettingsSection("ЦВЕТОВАЯ СХЕМА", palette) {
                    val names = listOf(
                        ThemeMode.VIOLET_VOID to "Violet Void",
                        ThemeMode.OBSIDIAN to "Obsidian",
                        ThemeMode.MIDNIGHT to "Midnight",
                        ThemeMode.SOFT_LAVENDER to "Soft Lavender"
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEach { (mode, label) -> ChoicePill(label, settings.theme == mode, palette) { onChange(settings.copy(theme = mode)) } }
                    }
                }
            }
            item {
                SettingsSection("ФОН", palette) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackgroundMode.entries.forEach { mode -> ChoicePill(mode.name, settings.background == mode, palette) { onChange(settings.copy(background = mode)) } }
                    }
                }
            }
            item {
                SettingsSection("АНИМАЦИИ", palette) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MotionMode.entries.forEach { mode -> ChoicePill(mode.name, settings.motion == mode, palette) { onChange(settings.copy(motion = mode)) } }
                    }
                }
            }
            item {
                SettingsSection("ИНТЕНСИВНОСТЬ СВЕЧЕНИЯ", palette) {
                    Slider(value = settings.accentIntensity, onValueChange = { onChange(settings.copy(accentIntensity = it)) }, valueRange = .55f..1.35f, colors = SliderDefaults.colors(thumbColor = palette.accentBright, activeTrackColor = palette.accent))
                }
            }
            item {
                SettingsToggle("Haptic feedback", "Лёгкая тактильная реакция на ключевые действия", settings.haptics, palette) { onChange(settings.copy(haptics = it)) }
            }
            item {
                SettingsToggle("Анимация сообщений", "Плавное появление новых сообщений", settings.messageAnimations, palette) { onChange(settings.copy(messageAnimations = it)) }
            }
            item {
                SettingsSection("РАЗМЕР ТЕКСТА", palette) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoicePill("S", settings.fontScale < .95f, palette) { onChange(settings.copy(fontScale = .88f)) }
                        ChoicePill("M", settings.fontScale in .95f..1.08f, palette) { onChange(settings.copy(fontScale = 1f)) }
                        ChoicePill("L", settings.fontScale > 1.08f, palette) { onChange(settings.copy(fontScale = 1.14f)) }
                    }
                }
            }
            item {
                Surface(color = palette.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Text("ПРИВАТНОСТЬ", color = palette.soft, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("PERSONA всегда остаётся ИИ-образом по твоим воспоминаниям. HIGHER не заявляет о сверхъестественной связи. Удаление и экспорт данных будем расширять следующими версиями.", color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, palette: ContactPalette, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = palette.surface.copy(alpha = .9f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = palette.soft, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, palette: ContactPalette, onClick: () -> Unit) {
    Surface(color = if (selected) palette.accent.copy(alpha = .22f) else palette.surface2, shape = RoundedCornerShape(100.dp), modifier = Modifier.border(1.dp, if (selected) palette.accentBright.copy(alpha = .55f) else palette.accent.copy(alpha = .13f), RoundedCornerShape(100.dp)).clickable(onClick = onClick)) {
        Text(text, color = if (selected) palette.text else palette.muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, palette: ContactPalette, onChange: (Boolean) -> Unit) {
    Surface(color = palette.surface.copy(alpha = .9f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = palette.muted, fontSize = 9.5.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = palette.text, checkedTrackColor = palette.accent))
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, badge: String, palette: ContactPalette, onBack: () -> Unit, onSettings: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = palette.soft) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = palette.text, fontSize = 19.sp, letterSpacing = 1.7.sp)
                Spacer(Modifier.width(7.dp))
                ContactBadge(badge, palette)
            }
            Text(subtitle, color = palette.soft, fontSize = 10.sp)
        }
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки", tint = palette.soft) }
    }
}

@Composable
private fun ContactStatusPill(text: String, palette: ContactPalette) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(color = palette.surface2.copy(alpha = .88f), shape = RoundedCornerShape(100.dp)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(palette.accentBright, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(text, color = palette.soft, fontSize = 8.5.sp, letterSpacing = .7.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, palette: ContactPalette) {
    val mine = msg.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if (mine) palette.accent.copy(alpha = .28f) else palette.surface2, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 310.dp)) {
            Text(msg.text, color = palette.text, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun CrisisCard(palette: ContactPalette) {
    Surface(color = Color(0xFF271116), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Если есть непосредственный риск причинить вред себе или другому человеку, CONTACT не подходит для срочной помощи. Обратись в местную экстренную службу (например, 112 в РФ/ЕС) или к человеку рядом.", color = Color(0xFFFFD0D4), fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(13.dp))
    }
}

@Composable
private fun ContactMark(size: Dp, palette: ContactPalette, animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "contact-mark")
    val pulse by transition.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (animated) 1800 else 100000), repeatMode = RepeatMode.Reverse),
        label = "contact-mark-pulse"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(brush = Brush.radialGradient(listOf(palette.accent.copy(alpha = .28f * pulse), Color.Transparent), center = c, radius = this.size.minDimension / 2), radius = this.size.minDimension / 2, center = c)
            drawCircle(palette.accentBright.copy(alpha = .92f), radius = this.size.minDimension * .085f, center = c)
            drawLine(palette.soft.copy(alpha = .75f + .2f * pulse), Offset(c.x, this.size.height * .06f), Offset(c.x, this.size.height * .94f), strokeWidth = 1.6.dp.toPx())
        }
    }
}

@Composable
private fun ContactRing(size: Dp, palette: ContactPalette) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension * .34f
            drawCircle(palette.accent.copy(alpha = .14f), radius = r * 1.55f)
            drawCircle(palette.soft, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx()))
        }
    }
}

@Composable
private fun ContactBackground(settings: UiSettings, palette: ContactPalette, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "ambient-phase")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.bg0, palette.bg1, palette.bg0)))) {
        Canvas(Modifier.fillMaxSize()) {
            when (settings.background) {
                BackgroundMode.CLEAN -> Unit
                BackgroundMode.STARS -> {
                    val stars = listOf(.08f to .11f, .19f to .24f, .34f to .09f, .48f to .19f, .67f to .12f, .83f to .28f, .93f to .08f, .71f to .52f, .23f to .61f, .89f to .76f, .42f to .81f)
                    stars.forEachIndexed { i, p -> drawCircle(palette.soft.copy(alpha = if (i % 3 == 0) .40f else .17f), radius = if (i % 3 == 0) 1.4.dp.toPx() else .8.dp.toPx(), center = Offset(size.width * p.first, size.height * p.second)) }
                }
                BackgroundMode.DUST -> {
                    repeat(22) { i ->
                        val x = ((i * 47) % 100) / 100f
                        val baseY = ((i * 29) % 100) / 100f
                        val y = (baseY + phase * .08f) % 1f
                        drawCircle(palette.soft.copy(alpha = .09f + (i % 3) * .025f), radius = (.6f + (i % 4) * .22f).dp.toPx(), center = Offset(size.width * x, size.height * y))
                    }
                }
                BackgroundMode.FOG -> {
                    drawCircle(brush = Brush.radialGradient(listOf(palette.accent.copy(alpha = .11f), Color.Transparent), center = Offset(size.width * (.25f + .12f * phase), size.height * .28f), radius = size.width * .55f), radius = size.width * .55f, center = Offset(size.width * (.25f + .12f * phase), size.height * .28f))
                    drawCircle(brush = Brush.radialGradient(listOf(palette.accentBright.copy(alpha = .07f), Color.Transparent), center = Offset(size.width * (.82f - .1f * phase), size.height * .68f), radius = size.width * .48f), radius = size.width * .48f, center = Offset(size.width * (.82f - .1f * phase), size.height * .68f))
                }
            }
        }
        content()
    }
}
