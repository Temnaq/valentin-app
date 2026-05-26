package com.example

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// Heart particle for the dynamic fluid canvas background
data class HeartParticle(
    val id: Long,
    val x: Float,
    var y: Float,
    val size: Float,
    val alpha: Float,
    val speedY: Float,
    val shiftX: Float,
    val driftSpeed: Float,
    var rotation: Float,
    val color: Color
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ValentineAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Reusable Extension function for Liquid Glass (Glassmorphism) look
fun Modifier.glassmorphic(
    cornerRadius: androidx.compose.ui.unit.Dp = 28.dp,
    borderWidth: androidx.compose.ui.unit.Dp = 1.2.dp
): Modifier = this
    .shadow(
        elevation = 20.dp,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = Color(0xFFFF4D6D).copy(alpha = 0.35f),
        spotColor = Color(0xFFFF2A54).copy(alpha = 0.45f)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.26f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.65f),
                Color.White.copy(alpha = 0.15f),
                Color(0xFFFFB3C1).copy(alpha = 0.35f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

@Composable
fun ValentineAppScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE) }

    // Read stored items
    var latestMessage by remember {
        mutableStateOf(prefs.getString("latest_message", "") ?: "")
    }
    var latestMessageTime by remember {
        mutableStateOf(prefs.getLong("latest_message_time", 0L))
    }
    var timerEndTime by remember {
        mutableStateOf(prefs.getLong("timer_end_time", 0L))
    }
    var timerIntervalSeconds by remember {
        mutableStateOf(prefs.getLong("timer_interval_seconds", 60L))
    }
    var timerIsActive by remember {
        mutableStateOf(prefs.getBoolean("timer_is_active", false))
    }

    // Set fallback initial message if empty
    if (latestMessage.isEmpty()) {
        latestMessage = "Добро пожаловать в приложение Валентинка! Пусть твоё сердце всегда будет согрето любовью и нежностью. ❤️"
        latestMessageTime = System.currentTimeMillis()
        prefs.edit().apply {
            putString("latest_message", latestMessage)
            putLong("latest_message_time", latestMessageTime)
            apply()
        }
    }

    // Notification permission check for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Уведомления успешно включены! ❤️", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Без уведомлений вы пропустите тайные валентинки!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Dynamic state polling (checks shared preference changes)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val updatedMsg = prefs.getString("latest_message", "") ?: ""
            val updatedTime = prefs.getLong("latest_message_time", 0L)
            val updatedEndTime = prefs.getLong("timer_end_time", 0L)
            val updatedInterval = prefs.getLong("timer_interval_seconds", 60L)
            val updatedIsActive = prefs.getBoolean("timer_is_active", false)

            if (updatedMsg != latestMessage) {
                latestMessage = updatedMsg
            }
            if (updatedTime != latestMessageTime) {
                latestMessageTime = updatedTime
            }
            if (updatedEndTime != timerEndTime) {
                timerEndTime = updatedEndTime
            }
            if (updatedInterval != timerIntervalSeconds) {
                timerIntervalSeconds = updatedInterval
            }
            if (updatedIsActive != timerIsActive) {
                timerIsActive = updatedIsActive
            }
        }
    }

    // Heart particles states
    val localHearts = remember { ArrayList<HeartParticle>() }
    var heartsState by remember { mutableStateOf(emptyList<HeartParticle>()) }

    // Ambient Floating Hearts Physics Loop
    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (true) {
            withFrameMillis { frameTime ->
                val nowTime = System.nanoTime()
                val elapsedSec = (nowTime - lastTime) / 1_000_000_000f
                lastTime = nowTime

                synchronized(localHearts) {
                    val updatedHearts = ArrayList<HeartParticle>()

                    // 1. Update existing hearts positions and velocities
                    for (i in 0 until localHearts.size) {
                        val heart = localHearts[i]
                        val nextY = heart.y - (heart.speedY * 130f * elapsedSec)
                        if (nextY >= -150f) {
                            val wobble = Math.sin((frameTime / 450.0) * heart.driftSpeed).toFloat() * heart.shiftX * 0.8f
                            val nextX = heart.x + wobble
                            val nextRot = heart.rotation + (30f * elapsedSec)

                            updatedHearts.add(
                                heart.copy(
                                    y = nextY,
                                    x = nextX,
                                    rotation = nextRot
                                )
                            )
                        }
                    }

                    localHearts.clear()
                    localHearts.addAll(updatedHearts)

                    // 2. Generate ambient background hearts
                    if (localHearts.size < 25) {
                        val colors = listOf(
                            Color(0xFFFF3354),
                            Color(0xFFFF5C75),
                            Color(0xFFFF8597),
                            Color(0xFFFFB3C1),
                            Color(0xFFDDA0DD), // Plum / Lilac accent
                            Color(0xFFE8AEFF)
                        )
                        localHearts.add(
                            HeartParticle(
                                id = UUID.randomUUID().mostSignificantBits,
                                x = Random.nextInt(-50, 1250).toFloat(),
                                y = 2200f + Random.nextInt(0, 500).toFloat(),
                                size = Random.nextInt(14, 38).toFloat(),
                                alpha = Random.nextFloat() * (0.75f - 0.25f) + 0.25f,
                                speedY = Random.nextFloat() * (2.4f - 0.7f) + 0.7f,
                                shiftX = Random.nextInt(1, 8).toFloat(),
                                driftSpeed = Random.nextFloat() * (2.8f - 0.9f) + 0.9f,
                                rotation = Random.nextInt(0, 360).toFloat(),
                                color = colors[Random.nextInt(colors.size)]
                            )
                        )
                    }

                    // Publish the complete updated frame state at once
                    heartsState = ArrayList(localHearts)
                }
            }
        }
    }

    // Heart splash/burst effect on click or tap
    fun spawnHeartBurst(centerX: Float, centerY: Float, count: Int = 16) {
        val colors = listOf(
            Color(0xFFFF0A35),
            Color(0xFFFF4D6D),
            Color(0xFFFF758F),
            Color(0xFFFFCCD5),
            Color(0xFFEB5E28), // warm sunset orange
            Color(0xFFFF8FA3),
            Color(0xFFD62246)
        )
        synchronized(localHearts) {
            repeat(count) {
                val angle = Random.nextFloat() * 2f * 3.1415927f
                val magnitude = Random.nextFloat() * (12f - 3f) + 3f
                val speedX = Math.cos(angle.toDouble()).toFloat() * magnitude
                val speedY = Math.sin(angle.toDouble()).toFloat() * magnitude - 3.2f // upward tilt
                localHearts.add(
                    HeartParticle(
                        id = UUID.randomUUID().mostSignificantBits,
                        x = centerX,
                        y = centerY,
                        size = Random.nextInt(16, 35).toFloat(),
                        alpha = 1.0f,
                        speedY = speedY * -0.55f + 1.8f,
                        shiftX = speedX * 3.8f,
                        driftSpeed = Random.nextFloat() * (3.3f - 1.2f) + 1.2f,
                        rotation = Random.nextInt(0, 360).toFloat(),
                        color = colors[Random.nextInt(colors.size)]
                    )
                )
            }
            heartsState = ArrayList(localHearts)
        }
    }

    // Base background with deep rich cosmos-romantic gradients for stunning glassmorphic output
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2B0A1A), // Dark velvet mystery
                        Color(0xFF4C1027), // Deep wild cherry
                        Color(0xFF1B0711)  // Bottom cosmos black-rose
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    spawnHeartBurst(offset.x, offset.y, count = 12)
                }
            }
    ) {
        // Floating Hearts underlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            heartsState.forEach { heart ->
                drawHeart(
                    x = heart.x,
                    y = heart.y,
                    sz = heart.size,
                    color = heart.color,
                    alpha = heart.alpha,
                    rotation = heart.rotation
                )
            }
        }

        // Main User Interface Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Elegant glowing Header
            AppHeaderSection()

            // Notification permission banner if missing on Android 13+
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRequestBanner {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Liquid Glass Valentine Card
            ValentineCard(
                messageText = latestMessage,
                timestamp = latestMessageTime,
                onCardClick = { x, y ->
                    spawnHeartBurst(x + 100f, y + 120f, count = 25)
                }
            )

            // Infinite Looping Timer Controller (Liquid Glass Design)
            TimerControlSection(
                timerEndTime = timerEndTime,
                timerIntervalSeconds = timerIntervalSeconds,
                timerIsActive = timerIsActive,
                onStartTimer = { seconds ->
                    // Set and trigger the recurring Alarm cycle via our central scheduler
                    AlarmScheduler.schedule(context, seconds)

                    // Immediate feedback explosion!
                    spawnHeartBurst(600f, 950f, count = 24)

                    Toast.makeText(
                        context,
                        "Таймер запущен на повторение каждые ${formatDurationRussian(seconds)}! ❤️",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onCancelTimer = {
                    AlarmScheduler.cancel(context)
                    Toast.makeText(context, "Бесконечный таймер остановлен.", Toast.LENGTH_SHORT).show()
                }
            )

            // Romantic signature info box in matching style
            LiquidGlassInfoBox()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AppHeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "💖",
                fontSize = 24.sp,
                modifier = Modifier.scale(1.1f)
            )
            Text(
                "Валентинка",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Serif,
                color = Color(0xFFFF4D6D)
            )
            Text(
                "🎀",
                fontSize = 24.sp,
                modifier = Modifier.scale(1.1f)
            )
        }
        Text(
            "Тёплые объятия в виде уведомлений на вашем телефоне",
            fontSize = 13.sp,
            color = Color(0xFFFFB3C1),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp)
        )
    }
}

@Composable
fun PermissionRequestBanner(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(cornerRadius = 24.dp)
            .padding(18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Включить уведомления",
                    tint = Color(0xFFFF4D6D)
                )
                Text(
                    "Подключить уведомления",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                "Чтобы приложение могло отправлять вам милые сюрпризы в цикле по таймеру, разрешите, пожалуйста, отправку уведомлений.",
                fontSize = 13.sp,
                color = Color(0xFFFFD6E0),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF4D6D).copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .testTag("request_permission_button")
            ) {
                Text(
                    "Разрешить отправку ❤️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ValentineCard(
    messageText: String,
    timestamp: Long,
    onCardClick: (Float, Float) -> Unit
) {
    var cardScale by remember { mutableStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = cardScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            cardScale = 1f
        }
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Tappable Liquid Glass Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(animatedScale)
                .glassmorphic(cornerRadius = 28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cardScale = 0.92f
                        onCardClick(offset.x, offset.y)
                    }
                }
                .padding(26.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive floating big red glowing heart
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💝",
                        fontSize = 34.sp,
                        modifier = Modifier.scale(1.1f)
                    )
                }

                // Main card frase
                Text(
                    text = messageText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Divider(
                    color = Color.White.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Bottom timestamp label in matching colors
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "💌",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Актуальная фраза: ${formatTime(timestamp)}",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB3C1),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Text(
            "✨ Нажмите на открытку, чтобы выпустить искры любви! ✨",
            fontSize = 11.sp,
            color = Color(0xFFFFB3C1).copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TimerControlSection(
    timerEndTime: Long,
    timerIntervalSeconds: Long,
    timerIsActive: Boolean,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit
) {
    // Current user local slider selection
    var selectedMinutesState by remember { mutableStateOf(10f) }
    var secondsRemaining by remember { mutableStateOf(0L) }

    // Real-time Countdown sync with precise system millisecond ticks
    LaunchedEffect(timerEndTime, timerIsActive) {
        if (timerIsActive) {
            while (true) {
                val currentNow = System.currentTimeMillis()
                if (timerEndTime > currentNow) {
                    secondsRemaining = (timerEndTime - currentNow) / 1000
                } else {
                    secondsRemaining = 0L
                }
                delay(1000)
            }
        } else {
            secondsRemaining = 0L
        }
    }

    // Dynamic translation helper to change seconds values
    fun translateSecondsToSelectionValue(seconds: Long): Float {
        // Translate popular intervals to minutes sliders values
        return (seconds / 60f).coerceIn(1f, 1440f)
    }

    // Set slider position on init/update
    LaunchedEffect(timerIntervalSeconds) {
        selectedMinutesState = translateSecondsToSelectionValue(timerIntervalSeconds)
    }

    // Selected seconds represented by Slider position
    val computedSeconds = remember(selectedMinutesState) {
        (selectedMinutesState.toLong() * 60L).coerceIn(10L, 86400L)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(cornerRadius = 28.dp)
            .padding(22.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "⏳",
                    fontSize = 22.sp
                )
                Text(
                    "Волшебный стеклянный таймер",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            AnimatedVisibility(visible = !timerIsActive) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Настройте автоповтор, чтобы телефон бесконечно и бережно присылал вам новые милые валентинки через заданный интервал:",
                        fontSize = 13.sp,
                        color = Color(0xFFFFD6E0),
                        lineHeight = 18.sp
                    )

                    // Continuous Custom Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Интервал доставки:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "каждые ${formatDurationRussian(computedSeconds)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF4D6D)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Slider(
                            value = selectedMinutesState,
                            onValueChange = { selectedMinutesState = it },
                            valueRange = 1f..1440f, // 1 minute to 24 hours (1440 mins)
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF4D6D),
                                activeTrackColor = Color(0xFFFF758F),
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.testTag("delay_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1 мин", fontSize = 10.sp, color = Color(0xFFFFB3C1))
                            Text("12 часов", fontSize = 10.sp, color = Color(0xFFFFB3C1))
                            Text("24 часа", fontSize = 10.sp, color = Color(0xFFFFB3C1))
                        }
                    }

                    // Expansive choices of presets
                    Text(
                        "Быстрый выбор красивого пресета:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB3C1)
                    )

                    // Presets Grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "10 сек (Тест)",
                                selected = timerIntervalSeconds == 10L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 0.166f // around 10 seconds
                                    onStartTimer(10L) 
                                }
                            )
                            PresetChip(
                                label = "1 мин",
                                selected = timerIntervalSeconds == 60L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 1f
                                    onStartTimer(60L)
                                }
                            )
                            PresetChip(
                                label = "15 мин",
                                selected = timerIntervalSeconds == 900L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 15f
                                    onStartTimer(900L)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "1 час",
                                selected = timerIntervalSeconds == 3600L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 60f
                                    onStartTimer(3600L)
                                }
                            )
                            PresetChip(
                                label = "3 часа",
                                selected = timerIntervalSeconds == 10800L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 180f
                                    onStartTimer(10800L)
                                }
                            )
                            PresetChip(
                                label = "6 часов",
                                selected = timerIntervalSeconds == 21600L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 360f
                                    onStartTimer(21600L)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetChip(
                                label = "12 часов",
                                selected = timerIntervalSeconds == 43200L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 720f
                                    onStartTimer(43200L)
                                }
                            )
                            PresetChip(
                                label = "24 часа (Сутки)",
                                selected = timerIntervalSeconds == 86400L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 1440f
                                    onStartTimer(86400L)
                                }
                            )
                        }
                    }

                    // Start timer action
                    Button(
                        onClick = { onStartTimer(computedSeconds) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4D6D).copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(top = 4.dp)
                            .testTag("start_timer_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Запустить"
                            )
                            Text(
                                "Активировать автоповтор ❤️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = timerIsActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Автоповтор активен! Следующее приятное уведомление прилетит по таймеру. Каждые ${formatDurationRussian(timerIntervalSeconds)}.",
                        fontSize = 13.sp,
                        color = Color(0xFFFFD6E0),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Countdown visual indicator with beautiful glass circle background
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .padding(12.dp)
                    ) {
                        // Smooth anim fraction calculation
                        val fraction = if (timerIntervalSeconds > 0) {
                            (secondsRemaining.toFloat() / timerIntervalSeconds.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        CircularProgressIndicator(
                            progress = fraction,
                            strokeWidth = 6.dp,
                            color = Color(0xFFFF4D6D),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Интервал",
                                fontSize = 11.sp,
                                color = Color(0xFFFFB3C1),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatSecondsToCountdown(secondsRemaining),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = formatDurationRussian(timerIntervalSeconds),
                                fontSize = 9.sp,
                                color = Color(0xFFFFB3C1).copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Cancel loop trigger
                    OutlinedButton(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = borderStrokeWhiteGlass(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("cancel_timer_button")
                    ) {
                        Text(
                            "Остановить автоповтор ⏱️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Color(0xFFFF4D6D).copy(alpha = 0.8f) 
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFFFF4D6D) else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color(0xFFFFD6E0)
        )
    }
}

@Composable
fun LiquidGlassInfoBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(cornerRadius = 24.dp)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "О приложении",
                tint = Color(0xFFFFB3C1),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Как это устроено?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Установите таймер один раз, и телефон будет регулярно присылать вам милые любовные послания. Каждое новое уведомление сохраняется в вашей красивой открытке сверху с летающими сердечками! 🌸",
                    fontSize = 12.sp,
                    color = Color(0xFFFFD6E0),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Helpers
fun borderStrokeWhiteGlass() = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.4f))

fun formatDurationRussian(seconds: Long): String {
    if (seconds < 60) {
        return "$seconds сек"
    }
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    val remainingSeconds = seconds % 60

    return when {
        hours > 0 -> {
            if (remainingMinutes > 0) "$hours ч $remainingMinutes мин" else "$hours ч"
        }
        else -> {
            if (remainingSeconds > 0) "$minutes мин $remainingSeconds сек" else "$minutes мин"
        }
    }
}

fun formatSecondsToCountdown(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}

fun formatTime(millis: Long): String {
    if (millis == 0L) return "Не определено"
    val sdf = SimpleDateFormat("HH:mm:ss dd.MM", Locale.getDefault())
    return sdf.format(Date(millis))
}

// Draw heart paths elegantly inside Canvas with rotation and scaling
fun DrawScope.drawHeart(
    x: Float,
    y: Float,
    sz: Float,
    color: Color,
    alpha: Float,
    rotation: Float
) {
    val scaleFactor = sz / 24f
    val path = Path().apply {
         // Smooth bezier curve centered at (0,0)
         moveTo(0f, -8f)
         cubicTo(-5.5f, -14.5f, -12f, -10f, -12f, -3.5f)
         cubicTo(-12.5f, 3.5f, -5f, 7.5f, 0f, 12f)
         cubicTo(5f, 7.5f, 12.5f, 3.5f, 12f, -3.5f)
         cubicTo(12f, -10f, 5.5f, -14.5f, 0f, -8f)
         close()
    }

    withTransform({
        translate(x, y)
        rotate(rotation)
        scale(scaleFactor, scaleFactor)
    }) {
        drawPath(
            path = path,
            color = color,
            alpha = alpha
        )
    }
}
