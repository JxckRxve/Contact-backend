package com.jrstudio.svyazsbogom.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrstudio.svyazsbogom.AppConfig
import com.jrstudio.svyazsbogom.data.*
import com.jrstudio.svyazsbogom.ui.theme.*
import com.jrstudio.svyazsbogom.util.installIdentity
import com.jrstudio.svyazsbogom.util.mayNeedUrgentHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { HOME, DEVELOPER, PERSONA, UNLOAD, HIGHER }

@Composable
fun SvyazSBogomApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    DivineBackground {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen { screen = it }
                Screen.DEVELOPER -> HumanContactScreen(
                    title = "DEVELOPER",
                    subtitle = "Связь с разработчиком",
                    channel = "developer",
                    intro = "Не по приложению. По жизни.",
                    onBack = { screen = Screen.HOME }
                )
                Screen.HIGHER -> HumanContactScreen(
                    title = "HIGHER",
                    subtitle = "Выше",
                    channel = "higher",
                    intro = "Скажи то, что не можешь сказать никому.",
                    onBack = { screen = Screen.HOME }
                )
                Screen.PERSONA -> PersonaScreen(onBack = { screen = Screen.HOME })
                Screen.UNLOAD -> UnloadScreen(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (Screen) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(34.dp))
        ContactMark(96.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            "C O N T A C T",
            color = DivineWhite,
            fontSize = 28.sp,
            letterSpacing = 5.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            "ESTABLISH CONTACT",
            color = DivineLavender,
            fontSize = 11.sp,
            letterSpacing = 2.6.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Скажи. Тебя услышат.",
            color = DivineWhite,
            fontFamily = FontFamily.Serif,
            fontSize = 23.sp
        )
        Spacer(Modifier.height(24.dp))

        PortalCard(
            icon = Icons.Rounded.Person,
            title = "DEVELOPER",
            subtitle = "Связь с разработчиком",
            detail = "Не по приложению. По жизни. Первый контакт бесплатно.",
            onClick = { onOpen(Screen.DEVELOPER) }
        )
        PortalCard(
            icon = Icons.Rounded.Face,
            title = "PERSONA",
            subtitle = "Любая личность",
            detail = "Создай ИИ-образ человека из своих воспоминаний.",
            onClick = { onOpen(Screen.PERSONA) }
        )
        PortalCard(
            icon = Icons.Rounded.Cloud,
            title = "UNLOAD",
            subtitle = "Сбросить груз",
            detail = "Персональная сессия • 45–60 минут • 5 000 ₽",
            onClick = { onOpen(Screen.UNLOAD) }
        )
        PortalCard(
            icon = Icons.Rounded.AutoAwesome,
            title = "HIGHER",
            subtitle = "Выше",
            detail = "Для слов, которые не получается сказать никому.",
            onClick = { onOpen(Screen.HIGHER) }
        )

        Spacer(Modifier.weight(1f))
        Text(
            "Анонимно • конфиденциально • человеческий контакт",
            color = DivineMuted,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}

@Composable
private fun PortalCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF100A18).copy(alpha = .92f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, DivineViolet.copy(alpha = .28f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(DivinePurple.copy(alpha = .16f), CircleShape)
                    .border(1.dp, DivineViolet.copy(alpha = .55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = DivineLavender)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = DivineWhite, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
                Text(subtitle, color = DivineLavender, fontSize = 13.sp)
                Text(detail, color = DivineMuted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = DivineLavender)
        }
    }
}

@Composable
private fun HumanContactScreen(
    title: String,
    subtitle: String,
    channel: String,
    intro: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val identity = remember { installIdentity(context) }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        runCatching {
            ApiClient.api.conversation(identity.conversationId, identity.installSecret, channel)
        }.onSuccess { if (it.ok) messages = it.messages }
    }

    LaunchedEffect(channel) {
        while (true) {
            refresh()
            delay(7000)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header(title, subtitle, onBack)

        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactMark(110.dp)
                Spacer(Modifier.height(20.dp))
                Text(intro, color = DivineWhite, fontFamily = FontFamily.Serif, fontSize = 24.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (channel == "developer")
                        "Отвечает живой человек. Первый контакт бесплатно."
                    else
                        "Поэтический режим живого диалога. Это не буквальная связь с высшими силами.",
                    color = DivineMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { MessageBubble(it) }
            }
        }

        if (urgent) CrisisCard()

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            placeholder = { Text("Скажи, что у тебя на сердце…", color = DivineMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DivineViolet,
                unfocusedBorderColor = DivineViolet.copy(alpha = .26f),
                cursorColor = DivineLavender
            ),
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(9.dp))
        Button(
            onClick = {
                val text = draft.trim()
                if (text.isNotEmpty() && !sending) {
                    urgent = urgent || mayNeedUrgentHelp(text)
                    sending = true
                    scope.launch {
                        runCatching {
                            ApiClient.api.sendMessage(
                                SendMessageRequest(
                                    identity.conversationId,
                                    identity.installSecret,
                                    text,
                                    channel = channel
                                )
                            )
                        }.onSuccess {
                            draft = ""
                            refresh()
                        }.onFailure {
                            error = "Не удалось установить контакт. Попробуй ещё раз."
                        }
                        sending = false
                    }
                }
            },
            enabled = draft.isNotBlank() && !sending,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DivinePurple),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Send, null)
            Spacer(Modifier.width(8.dp))
            Text(if (messages.isEmpty()) "УСТАНОВИТЬ CONTACT" else "ОТПРАВИТЬ", letterSpacing = 1.sp)
        }
        error?.let { Text(it, color = Color(0xFFFFA8B0), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        Text(
            "CONTACT не заменяет медицинскую, психотерапевтическую или экстренную помощь.",
            color = DivineMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun PersonaScreen(onBack: () -> Unit) {
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
    var chat by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val prompts = listOf(
        Triple("Как его звали?", "Имя или то, как ты его называл.", name),
        Triple("Кем он был для тебя?", "Например: друг, мама, бывший партнёр, персонаж.", relationship),
        Triple("Каким ты его помнишь?", "Характер, реакции, ценности, юмор.", description),
        Triple("Как он говорил?", "Манера речи, обращения, любимые выражения.", speech),
        Triple("Что между вами важно?", "Истории, события, общие воспоминания.", memories),
        Triple("Чего он точно не сказал бы?", "Границы личности помогают сделать образ точнее.", neverSay)
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header("PERSONA", "Создать личность", onBack)

        if (personaId == null) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Восстановим образ постепенно.",
                    color = DivineWhite,
                    fontFamily = FontFamily.Serif,
                    fontSize = 27.sp
                )
                Text(
                    "PERSONA — это ИИ-образ, созданный из твоих воспоминаний. Это не сам человек.",
                    color = DivineMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                val question = prompts[step].first
                val hint = prompts[step].second
                Text(question, color = DivineLavender, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, color = DivineMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 7.dp))

                val value = when(step) {
                    0 -> name; 1 -> relationship; 2 -> description; 3 -> speech; 4 -> memories; else -> neverSay
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        when(step) {
                            0 -> name = it
                            1 -> relationship = it
                            2 -> description = it
                            3 -> speech = it
                            4 -> memories = it
                            5 -> neverSay = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DivineViolet,
                        unfocusedBorderColor = DivineViolet.copy(alpha=.25f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (step < prompts.lastIndex) step++
                        else {
                            loading = true
                            scope.launch {
                                runCatching {
                                    ApiClient.api.createPersona(
                                        PersonaCreateRequest(
                                            identity.conversationId, identity.installSecret,
                                            name, relationship, description, speech, memories, neverSay
                                        )
                                    )
                                }.onSuccess {
                                    personaId = it.personaId
                                }.onFailure {
                                    error = "Не удалось сохранить PERSONA."
                                }
                                loading = false
                            }
                        }
                    },
                    enabled = value.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DivinePurple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (step == prompts.lastIndex) "СОЗДАТЬ PERSONA" else "ДАЛЬШЕ")
                }
                Text(
                    "${step + 1} / ${prompts.size}",
                    color = DivineMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                error?.let { Text(it, color = Color(0xFFFFA8B0), fontSize = 11.sp) }
            }
        } else {
            Text(
                name.ifBlank { "PERSONA" },
                color = DivineWhite,
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text("ИИ-образ из твоих воспоминаний", color = DivineLavender, fontSize = 11.sp)

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(chat) { (role, text) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (role == "you") Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (role == "you") DivinePurple.copy(alpha=.28f) else Color(0xFF15101D),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Text(text, color = DivineWhite, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Напиши ${name.ifBlank { "PERSONA" }}…", color = DivineMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DivineViolet,
                    unfocusedBorderColor = DivineViolet.copy(alpha=.25f)
                ),
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    val id = personaId
                    if (text.isNotEmpty() && id != null && !loading) {
                        chat = chat + ("you" to text)
                        draft = ""
                        loading = true
                        scope.launch {
                            runCatching {
                                ApiClient.api.personaMessage(
                                    PersonaMessageRequest(
                                        identity.conversationId,
                                        identity.installSecret,
                                        id,
                                        text
                                    )
                                )
                            }.onSuccess {
                                chat = chat + ("persona" to (it.reply ?: "AI-провайдер пока не подключён."))
                            }.onFailure {
                                chat = chat + ("persona" to "Контакт с AI-провайдером пока недоступен.")
                            }
                            loading = false
                        }
                    }
                },
                enabled = draft.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DivinePurple)
            ) { Text("УСТАНОВИТЬ CONTACT") }
            Text(
                "Это симуляция личности, а не реальный человек или его сознание.",
                color = DivineMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun UnloadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Header("UNLOAD", "Сбросить груз", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactRing(150.dp)
            Spacer(Modifier.height(24.dp))
            Text("Не неси это один.", color = DivineWhite, fontFamily = FontFamily.Serif, fontSize = 29.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Приватная персональная сессия с живым собеседником.",
                color = DivineMuted,
                textAlign = TextAlign.Center,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(22.dp))
            Surface(
                color = Color(0xFF100A18),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, DivineViolet.copy(alpha=.3f), RoundedCornerShape(22.dp))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("45–60 МИНУТ", color = DivineLavender, letterSpacing = 1.3.sp)
                    Text("5 000 ₽", color = DivineWhite, fontSize = 38.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(14.dp))
                    Text("✦ Анонимно\n✦ Конфиденциально\n✦ Без осуждения\n✦ Живой собеседник\n✦ Можно просто выговориться",
                        color = DivineWhite, lineHeight = 27.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.UNLOAD_PAYMENT_URL)))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DivinePurple),
                shape = RoundedCornerShape(18.dp)
            ) { Text("ОПЛАТИТЬ 5 000 ₽", fontWeight = FontWeight.SemiBold) }
            Text(
                "Оплата — за персональную сессию и время собеседника, а не за «прощение», лечение или религиозную услугу.",
                color = DivineMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, null, tint = DivineLavender)
        }
        Column {
            Text(title, color = DivineWhite, fontSize = 21.sp, letterSpacing = 2.sp)
            Text(subtitle, color = DivineLavender, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val mine = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (mine) DivinePurple.copy(alpha=.32f) else Color(0xFF15101D),
            shape = RoundedCornerShape(17.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(msg.text, color = DivineWhite, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(13.dp))
        }
    }
}

@Composable
private fun CrisisCard() {
    Surface(
        color = Color(0xFF271116),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Text(
            "Если есть непосредственный риск причинить вред себе или другому человеку, CONTACT не подходит для срочной помощи. Обратись в местную экстренную службу (например, 112 в РФ/ЕС) или к человеку рядом.",
            color = Color(0xFFFFD0D4),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun ContactMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(DivineViolet.copy(alpha=.45f), Color.Transparent),
                    center = c,
                    radius = this.size.minDimension/2
                ),
                radius = this.size.minDimension/2,
                center = c
            )
            drawCircle(DivineLavender.copy(alpha=.9f), radius = this.size.minDimension*.12f, center = c)
            drawLine(
                DivineLavender,
                Offset(c.x, this.size.height*.05f),
                Offset(c.x, this.size.height*.95f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun ContactRing(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension * .34f
            drawCircle(DivineViolet.copy(alpha=.18f), radius=r*1.5f)
            drawCircle(DivineLavender, radius=r, style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
        }
    }
}

@Composable
private fun DivineBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF020105), Color(0xFF08020F), Color(0xFF030106))
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stars = listOf(
                .08f to .11f, .19f to .24f, .34f to .09f, .48f to .19f,
                .67f to .12f, .83f to .28f, .93f to .08f, .71f to .52f,
                .23f to .61f, .89f to .76f, .42f to .81f
            )
            stars.forEachIndexed { i, p ->
                drawCircle(
                    DivineLavender.copy(alpha = if (i%3==0) .48f else .2f),
                    radius = if (i%3==0) 1.5.dp.toPx() else .8.dp.toPx(),
                    center = Offset(size.width*p.first, size.height*p.second)
                )
            }
        }
        content()
    }
}
