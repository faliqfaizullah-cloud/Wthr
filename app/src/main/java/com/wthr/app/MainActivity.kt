@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.wthr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlin.math.abs
import kotlin.random.Random

// Palette taken from the video
val Maroon = Color(0xFF780000); val Red = Color(0xFFFF1F43); val Lilac = Color(0xFFE9C9F7)
val Brown = Color(0xFF5B4632); val Cream = Color(0xFFF5E3C3)
val TealDark = Color(0xFF006B72); val TealLight = Color(0xFF74E2E5)
// Swap for a bundled condensed font (e.g. Anton in res/font) to match the video even closer
val Display = FontFamily.SansSerif

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Haptics.init(this)
        WeatherWorker.schedule(this)          // background weather refresh every 30 min
        askBackgroundUsage()
        setContent { WthrApp() }
    }
    /** One-time system prompt: allow Wthr to keep refreshing in the background. */
    private fun askBackgroundUsage() {
        val prefs = getSharedPreferences("wthr", 0)
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("asked_bg", false)) {
            prefs.edit().putBoolean("asked_bg", true).apply()
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }
    }
    override fun onPause() { super.onPause(); Haptics.cancel() }
}

/** Spring bounce 1 -> 0 on every key change. A haptic thump fires at each impact, softer each time. */
@Composable
fun rememberBounce(key: Any?, enabled: Boolean = true): Animatable<Float, AnimationVector1D> {
    val a = remember { Animatable(1f) }
    LaunchedEffect(key, enabled) {
        if (!enabled) { a.snapTo(1f); return@LaunchedEffect }
        a.snapTo(1f)
        val job = launch {
            var last = 1f; var peak = 1f
            snapshotFlow { a.value }.collect {
                peak = maxOf(peak, abs(it))
                if ((last > 0 && it <= 0) || (last < 0 && it >= 0)) {
                    Haptics.bounce(peak); peak = 0f
                }
                last = it
            }
        }
        a.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
        job.cancel()
    }
    return a
}

@Composable
fun Txt(s: String, size: Int, color: Color, w: FontWeight = FontWeight.Black, mod: Modifier = Modifier,
        ls: Float = -1f, lh: Float = 0.88f, align: TextAlign? = null) =
    androidx.compose.material3.Text(s, mod, color, size.sp, fontFamily = Display, fontWeight = w,
        letterSpacing = ls.sp, lineHeight = (size * lh).sp, textAlign = align)

@Composable
fun WthrApp() {
    val pager = rememberPagerState { 3 }
    LaunchedEffect(pager.currentPage) { Haptics.tick() }
    val view = LocalView.current
    val page = pager.currentPage
    SideEffect {
        val win = (view.context as? Activity)?.window
        if (win != null) WindowCompat.getInsetsController(win, view).apply {
            isAppearanceLightStatusBars = page == 0; isAppearanceLightNavigationBars = page == 0 }
    }
    HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
        val active = pager.currentPage == p
        when (p) {
            0 -> WeatherScreen(active)
            1 -> WelcomeScreen(active)
            else -> FlightsScreen(active)
        }
    }
}

// ---------------- 1. WEATHER (white, live data for the user's country) ----------------
val WordOff = Color(0xFFD9D3D3)

@Composable
fun WeatherScreen(active: Boolean) {
    val ctx = LocalContext.current
    var w by remember { mutableStateOf(WeatherRepo.cached(ctx)) }
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { WeatherRepo.fetch(ctx) }
        if (fresh != null) { w = fresh; WthrWidget.updateAll(ctx) }
    }
    var sel by remember { mutableStateOf("NOW") }
    var hourSet by remember { mutableIntStateOf(0) }
    LaunchedEffect(w?.kind) { sel = when (w?.kind) { 1 -> "RAIN"; 0, 2 -> "CLEAR"; 3 -> "CLOUDY"; else -> "NOW" } }
    val raining = active && sel == "RAIN"
    val temp by animateIntAsState(w?.temp ?: 0, tween(700, easing = FastOutSlowInEasing), label = "t")
    val enter = rememberBounce("w$active", active)
    val tap = rememberBounce(sel)

    // Haptic rain: random light drops + occasional thunder rumble
    LaunchedEffect(raining) {
        var n = 0
        while (raining) {
            Haptics.drop(); delay(Random.nextLong(35, 150))
            if (++n % 60 == 0) Haptics.rumble()
        }
    }
    val hrs = w?.hours ?: List(9) { "--" }
    val ht = w?.hTemps ?: List(9) { 0 }
    val hc = w?.hCodes ?: List(9) { 0 }
    val s0 = hourSet * 3
    val place = w?.let { listOf(it.city, it.country).filter { x -> x.isNotBlank() }.joinToString(", ") } ?: "Locating…"

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt("DESTINATION WEATHER", 11, Maroon, ls = 0f)
                Txt("/001", 11, Maroon, ls = 0f)
            }
            if (raining) RainOverlay()
            Column(Modifier.align(Alignment.CenterStart).graphicsLayer { translationY = enter.value * 120f }) {
                listOf("WEATHER", "NOW", "CLOUDY", "RAIN", "CLEAR", "FORECAST").forEach { word ->
                    val on = word == sel
                    Box(Modifier.pointerInput(word) { detectTapGestures { sel = word; Haptics.click() } }
                        .graphicsLayer { val sc = if (on) 1f + tap.value * 0.06f else 1f; scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }) {
                        Txt(word, 66, if (on) Maroon else WordOff)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (w == null) "--°" else "$temp°", 34, Maroon, ls = 0f)
            Txt(place, 12, Maroon, FontWeight.Medium, Modifier.padding(top = 4.dp), 0f)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("←") { hourSet = (hourSet + 2) % 3; Haptics.click() }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth()) { (0..2).forEach { Txt(hrs[s0 + it], 12, Maroon, FontWeight.Medium, Modifier.weight(1f), 0f, align = TextAlign.Center) } }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) { (0..2).forEach { Txt(glyph(kindOf(hc[s0 + it], w?.isDay ?: true)), 20, Maroon, FontWeight.Normal, Modifier.weight(1f), 0f, align = TextAlign.Center) } }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) { (0..2).forEach { Txt("${ht[s0 + it]}°", 14, Maroon, FontWeight.ExtraBold, Modifier.weight(1f), 0f, align = TextAlign.Center) } }
                }
                RoundBtn("→") { hourSet = (hourSet + 1) % 3; Haptics.click() }
            }
        }
    }
}

@Composable
fun RoundBtn(s: String, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.82f else 1f, spring(0.3f, Spring.StiffnessMedium), label = "b")
    Box(Modifier.size(38.dp).scale(sc).clip(CircleShape).background(Maroon)
        .pointerInput(Unit) { detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center) { Txt(s, 18, Lilac, ls = 0f) }
}

@Composable
fun RainOverlay() {
    val drops = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat()) } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { t = it / 1e9f } }
    Canvas(Modifier.fillMaxSize()) {
        drops.forEach { (x, o, sp) ->
            val y = ((t * sp * 0.9f + o) % 1f) * size.height
            drawLine(Maroon.copy(alpha = 0.35f), Offset(x * size.width, y), Offset(x * size.width - 4f, y + 38f), 3f, StrokeCap.Round)
        }
    }
}

// ---------------- 2. WELCOME ----------------
@Composable
fun WelcomeScreen(active: Boolean) {
    val b = rememberBounce("p$active", active)
    var min by remember { mutableIntStateOf(52) }
    LaunchedEffect(active) { while (active) { delay(2500); min = if (min <= 40) 59 else min - 1 } }
    val progress by animateFloatAsState(0.6f + (59 - min) * 0.01f, tween(600), label = "pr")
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Maroon).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Txt("WELCOME", 74, Lilac, ls = -3f)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.graphicsLayer { translationY = -b.value * 260f; rotationZ = b.value * 14f }.width(200.dp)) { BoardingPass() }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("ROUTE", "LUGGAGE", "PASS").forEachIndexed { i, s ->
                Box(Modifier.pointerInput(i) { detectTapGestures { tab = i; Haptics.click() } }) {
                    Txt(s, 12, if (tab == i) Lilac else Lilac.copy(alpha = .6f), FontWeight.ExtraBold, ls = 0f) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Brown).padding(18.dp).navigationBarsPadding(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt("Arrival in", 11, Cream, FontWeight.Medium, ls = 0f)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt("$min", 44, Cream, ls = 0f); Spacer(Modifier.width(6.dp)); Txt("min", 18, Cream, FontWeight.Medium, Modifier.padding(bottom = 4.dp), 0f)
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt("14:30", 10, Cream, FontWeight.Medium, ls = 0f); Txt("18:25", 10, Cream, FontWeight.Medium, ls = 0f) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt("JFK", 24, Cream, ls = 0f); Txt(" New York", 10, Cream, FontWeight.Medium, ls = 0f)
                    Spacer(Modifier.weight(1f)); Txt("✈", 20, Cream, FontWeight.Normal, ls = 0f); Spacer(Modifier.weight(1f))
                    Txt("Paris ", 10, Cream, FontWeight.Medium, ls = 0f); Txt("CDG", 24, Cream, ls = 0f)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Cream))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun BoardingPass() {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("New York", 8, Color.DarkGray, FontWeight.Medium, ls = 0f); Txt("Paris", 8, Color.DarkGray, FontWeight.Medium, ls = 0f) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt("JFK", 22, Color(0xFF3A1A12), ls = 0f); Txt("CDG", 22, Color(0xFF3A1A12), ls = 0f) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Txt("14:30", 11, Color(0xFF3A1A12), ls = 0f); Txt("June 5, 2025", 7, Color.DarkGray, FontWeight.Medium, ls = 0f) }
            Column(horizontalAlignment = Alignment.End) { Txt("18:25", 11, Color(0xFF3A1A12), ls = 0f); Txt("June 5, 2025", 7, Color.DarkGray, FontWeight.Medium, ls = 0f) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            repeat(16) { Box(Modifier.size(if (it == 0 || it == 15) 12.dp else 4.dp).clip(CircleShape).background(Maroon)) }
        }
        Spacer(Modifier.height(10.dp))
        val bars = remember { List(34) { Random(it + 7).nextInt(1, 4) } }
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            var x = 0f; val unit = size.width / (bars.sum() * 2f)
            bars.forEach { w -> drawRect(Color(0xFF3A1A12), Offset(x, 0f), androidx.compose.ui.geometry.Size(w * unit, size.height)); x += w * unit * 2 }
        }
    }
}

// ---------------- 3. UPCOMING FLIGHTS ----------------
@Composable
fun FlightsScreen(active: Boolean) {
    val b = rememberBounce("f$active", active)
    var boarding by remember { mutableIntStateOf(30) }
    LaunchedEffect(active) { while (active) { delay(1800); boarding = if (boarding >= 38) 28 else boarding + 1 } }
    var open by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(TealDark).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Circle { Txt("14:30", 24, TealDark, ls = 0f); Txt("June 5, 2025", 8, TealDark, FontWeight.Medium, ls = 0f) }
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(TealLight, style = Stroke(10f))
                    drawArc(TealDark, -90f, 360f * boarding / 100f, false, style = Stroke(10f, cap = StrokeCap.Round))
                }
                Box(Modifier.size(80.dp).clip(CircleShape).background(TealLight), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("Boarding", 8, TealDark, FontWeight.Medium, ls = 0f); Txt("$boarding%", 26, TealDark, ls = 0f) }
                }
            }
            Circle { Txt("18:25", 10, TealDark, FontWeight.Medium, ls = 0f); Txt("✈", 24, TealDark, FontWeight.Normal, ls = 0f); Txt("JFK", 10, TealDark, ls = 0f) }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(TealLight)) {
            Txt("Step into a world where the vibrant colors of fresh produce dance on your plate, and the exquisite flavors of vegetarian cuisine take center stage.",
                11, TealDark, FontWeight.Medium, Modifier.padding(horizontal = 26.dp, vertical = 14.dp), 0f, 1.2f, TextAlign.Center)
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.graphicsLayer { translationY = -b.value * 500f; rotationZ = b.value * -12f }.offset(y = 56.dp)) { GateCard(open) { open = !open; Haptics.click() } }
                Txt("UPCOMING\nFLIGHTS", 78, TealDark, ls = -3f, lh = 0.82f, align = TextAlign.Center)
                Txt("/002", 11, TealDark, FontWeight.Medium, Modifier.padding(vertical = 18.dp), 0f)
            }
        }
    }
}

@Composable fun Circle(c: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.size(84.dp).clip(CircleShape).background(TealLight), Arrangement.Center, Alignment.CenterHorizontally, content = c)

@Composable
fun GateCard(open: Boolean, onArrow: () -> Unit) {
    Box(Modifier.size(width = 140.dp, height = 190.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        Box(Modifier.padding(10.dp).size(6.dp).clip(CircleShape).background(TealDark).border(1.dp, TealLight, CircleShape))
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(TealDark)
            .pointerInput(Unit) { detectTapGestures { onArrow() } }, contentAlignment = Alignment.Center) { Txt("↗", 11, Color.White, ls = 0f) }
        Txt("✈", 100, TealLight, FontWeight.Normal, Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 2.dp).rotate(0f), 0f)
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Txt("D14", 40, TealDark, ls = -1f)
            Txt(if (open) "Gate open" else "Gate closed", 13, TealDark, FontWeight.ExtraBold, ls = 0f)
            Txt("Departure in 26 min", 10, TealDark, FontWeight.Medium, ls = 0f)
        }
    }
}
