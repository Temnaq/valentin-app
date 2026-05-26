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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.text.style.TextOverflow
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
                ValentineAppScreen()
            }
        }
    }
}

@Composable
fun ValentineAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("valentinka_prefs", Context.MODE_PRIVATE) }

    // Navigation Tab Selection State
    var selectedTab by remember { mutableStateOf(0) }

    // Read stored active message states
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

    // Initialize Room database & reactive stream
    val db = remember { DatabaseProvider.getDatabase(context) }
    val historyItems by db.valentineHistoryDao().getAllHistory().collectAsState(initial = emptyList())

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

    // Auto-bootstrap Room history table with the initial message if totally empty
    LaunchedEffect(historyItems) {
        if (historyItems.isEmpty()) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                db.valentineHistoryDao().insertHistoryItem(
                    ValentineHistoryItem(
                        message = "Добро пожаловать в приложение Валентинка! Пусть твоё сердце всегда будет согрето любовью и нежностью. ❤️",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
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

    // Dynamic state polling (checks shared preference changes & updates the current screen view)
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

    // Heart particles state lists for rendering engine
    val localHearts = remember { ArrayList<HeartParticle>() }
    var heartsState by remember { mutableStateOf(emptyList<HeartParticle>()) }

    // Ambient Floating Hearts Physics Loop inside Coroutine
    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (true) {
            withFrameMillis { frameTime ->
                val nowTime = System.nanoTime()
                val elapsedSec = (nowTime - lastTime) / 1_000_000_000f
                lastTime = nowTime

                synchronized(localHearts) {
                    val updatedHearts = ArrayList<HeartParticle>()

                    // 1. Move and wobble floating hearts upwards
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

                    // 2. Spawn ambient background hearts in bounds
                    if (localHearts.size < 25) {
                        val colors = listOf(
                            Color(0xFFFF3354),
                            Color(0xFFFF5C75),
                            Color(0xFFFF8597),
                            Color(0xFFFFB3C1),
                            Color(0xFFDDA0DD),
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

                    // Update drawing state
                    heartsState = ArrayList(localHearts)
                }
            }
        }
    }

    // Tap/touch feedback explosion handler
    fun spawnHeartBurst(centerX: Float, centerY: Float, count: Int = 16) {
        val colors = listOf(
            Color(0xFFFF0A35),
            Color(0xFFFF4D6D),
            Color(0xFFFF758F),
            Color(0xFFFFCCD5),
            Color(0xFFEB5E28),
            Color(0xFFFF8FA3),
            Color(0xFFD62246)
        )
        synchronized(localHearts) {
            repeat(count) {
                val angle = Random.nextFloat() * 2f * 3.1415927f
                val magnitude = Random.nextFloat() * (12f - 3f) + 3f
                val speedX = Math.cos(angle.toDouble()).toFloat() * magnitude
                val speedY = Math.sin(angle.toDouble()).toFloat() * magnitude - 3.2f
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

    // Material 3 Responsive layout Scaffold with Bottom Tab Switcher
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent, // Let global backgrounds & particles show through
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .testTag("app_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Открытка") },
                    label = { Text("Открытка") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("tab_card")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Таймер") },
                    label = { Text("Таймер") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("tab_timer")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "История") },
                    label = { Text("История") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("tab_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        spawnHeartBurst(offset.x, offset.y, count = 12)
                    }
                }
        ) {
            // Heart particle floating canvas background
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

            // Outer Screen Container carrying header & Crossfaded tab contents
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header remains globally locked at top for dynamic branding
                AppHeaderSection()

                // OS Notification Permission Banner if missing
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRequestBanner {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Smooth sliding tab crossfade container
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { currentTab ->
                    when (currentTab) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Full Visual Greeting Card
                                ValentineCard(
                                    messageText = latestMessage,
                                    timestamp = latestMessageTime,
                                    onCardClick = { x, y ->
                                        spawnHeartBurst(x + 100f, y + 120f, count = 25)
                                    }
                                )

                                // Educational context
                                M3InfoBox()
                            }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Magical Repeating Timer
                                TimerControlSection(
                                    timerEndTime = timerEndTime,
                                    timerIntervalSeconds = timerIntervalSeconds,
                                    timerIsActive = timerIsActive,
                                    onStartTimer = { seconds ->
                                        AlarmScheduler.schedule(context, seconds)
                                        spawnHeartBurst(600f, 950f, count = 24)
                                        Toast.makeText(
                                            context,
                                            "Автоповтор запущен на каждые ${formatDurationRussian(seconds)}! ❤️",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onCancelTimer = {
                                        AlarmScheduler.cancel(context)
                                        Toast.makeText(context, "Автоповтор остановлен.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                        2 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Room Database History List (History Tab)
                                ValentineHistorySection(
                                    historyList = historyItems,
                                    currentActiveMessage = latestMessage,
                                    onSelectMessage = { selectedItem ->
                                        latestMessage = selectedItem.message
                                        latestMessageTime = selectedItem.timestamp
                                        prefs.edit().apply {
                                            putString("latest_message", selectedItem.message)
                                            putLong("latest_message_time", selectedItem.timestamp)
                                            apply()
                                        }
                                        spawnHeartBurst(500f, 400f, count = 18)
                                        Toast.makeText(context, "Открытка установлена в превью! Переключитесь на первую вкладку, чтобы увидеть её подробно. 💌", Toast.LENGTH_LONG).show()
                                    },
                                    onClearHistory = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            db.valentineHistoryDao().clearHistory()
                                            db.valentineHistoryDao().insertHistoryItem(
                                                ValentineHistoryItem(
                                                    message = "Добро пожаловать в приложение Валентинка! Пусть твоё сердце всегда будет согрето любовью и нежностью. ❤️",
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        Toast.makeText(context, "История очищена.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "💖",
                fontSize = 24.sp,
                modifier = Modifier.scale(1.15f)
            )
            Text(
                "Валентинка",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "🎀",
                fontSize = 24.sp,
                modifier = Modifier.scale(1.15f)
            )
        }
        Text(
            "Рассылка тёплых и обнимающих слов на телефон",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
        )
    }
}

@Composable
fun PermissionRequestBanner(onRequest: () -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Включить уведомления",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Включить уведомления",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Чтобы приложение отправляло новые валентинки в фоновом режиме, разрешите уведомления в системе.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(38.dp)
                    .testTag("request_permission_button")
            ) {
                Text(
                    "Разрешить отправку ❤️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
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
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        // Tappable elevated Material 3 card container
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .scale(animatedScale)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        cardScale = 0.94f
                        onCardClick(offset.x, offset.y)
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                // Large visual romantic card icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💝",
                        fontSize = 32.sp,
                        modifier = Modifier.scale(1.1f)
                    )
                }

                // Main highlighted romantic quote
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 26.sp,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Timestamp label
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text(
            "✨ Нажмите на открытку для взрыва искорок! ✨",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
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
    var selectedMinutesState by remember { mutableStateOf(10f) }
    var secondsRemaining by remember { mutableStateOf(0L) }

    // Synchronize countdown with background ticking timer
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

    LaunchedEffect(timerIntervalSeconds) {
        selectedMinutesState = (timerIntervalSeconds / 60f).coerceIn(1f, 1440f)
    }

    val computedSeconds = remember(selectedMinutesState) {
        (selectedMinutesState.toLong() * 60L).coerceIn(10L, 86400L)
    }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "⏳",
                    fontSize = 24.sp
                )
                Text(
                    "Волшебный таймер",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedVisibility(visible = !timerIsActive) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Создайте автоповтор, чтобы новые ласковые открытки и ласковые уведомления прилетали автоматически через выбранное время:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
                                "Интервал:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "каждые ${formatDurationRussian(computedSeconds)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Slider(
                            value = selectedMinutesState,
                            onValueChange = { selectedMinutesState = it },
                            valueRange = 1f..1440f, // 1 minute to 24 hours
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.testTag("delay_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1 мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("12 часов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("24 часа", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    // Expansive Presets Section
                    Text(
                        "Быстрые интервалы автоповтора:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Presets Grid List
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PresetChip(
                                label = "10 сек (Тест)",
                                selected = timerIntervalSeconds == 10L && !timerIsActive,
                                onClick = {
                                    selectedMinutesState = 0.166f
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { onStartTimer(computedSeconds) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
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
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = timerIsActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Автоповтор активен! Новые пожелания летят на устройство каждые ${formatDurationRussian(timerIntervalSeconds)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    // Countdown Progress Loader Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(130.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .padding(8.dp)
                    ) {
                        val fraction = if (timerIntervalSeconds > 0) {
                            (secondsRemaining.toFloat() / timerIntervalSeconds.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        CircularProgressIndicator(
                            progress = { fraction },
                            strokeWidth = 5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Интервал",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatSecondsToCountdown(secondsRemaining),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatDurationRussian(timerIntervalSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("cancel_timer_button")
                    ) {
                        Text(
                            "Остановить автоповтор ⏱️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ValentineHistorySection(
    historyList: List<ValentineHistoryItem>,
    currentActiveMessage: String,
    onSelectMessage: (ValentineHistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📚",
                        fontSize = 22.sp
                    )
                    Text(
                        "История валентинок",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (historyList.size > 1) {
                    IconButton(
                        onClick = onClearHistory,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Очистить историю"
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            // History list scroll container fills space, enabling seamless touch interactions
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "История пока пуста. Ожидайте новых уведомлений. 🌸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    historyList.forEach { historyItem ->
                        val isCurrentActive = historyItem.message == currentActiveMessage
                        
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            onClick = { onSelectMessage(historyItem) },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isCurrentActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                width = if (isCurrentActive) 1.5.dp else 1.dp,
                                brush = if (isCurrentActive) {
                                    Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                                } else {
                                    Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isCurrentActive) "💖" else "💌",
                                    fontSize = 18.sp
                                )
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = historyItem.message,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatTime(historyItem.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "💡 Нажмите на любую старую валентинку в списке, чтобы показать её в главном превью первой вкладки!",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun M3InfoBox() {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "О приложении",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Как это устроено?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Установите автоповтор в следующей вкладке! Приложение будет радовать вас милыми записками в уведомлениях. Все полученные открытки навсегда сохраняются в истории!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// Helpers
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

// Draw heart paths inside Canvas with rotation and scaling
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
