package com.sandolpin.santopimedia35.remember

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas

// ===== 棒グラフ用データ =====
data class BarEntry(
    val label: String,       // 曜日ラベル（日・月・火…）
    val dateLabel: String,   // 日付ラベル（〇/〇）
    val valueMs: Long,       // 再生時間(ms)
    val isToday: Boolean = false,
    val dateMs: Long = 0L    // この日の開始ms（タップ時表示用）
)

// ===== 円グラフ用データ =====
data class PieEntry(
    val label: String,
    val packageName: String,
    val valueMs: Long,
    val color: Color
)

// ===== 週の開始Msを計算 =====
private fun weekStartMs(weekOffset: Int = 0): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
    }
    return cal.timeInMillis
}

// 週の情報（曜日・日付・今日フラグ）
data class DayInfo(val dayLabel: String, val dateLabel: String, val isToday: Boolean)

private fun weekDayInfoList(weekOffset: Int = 0): List<DayInfo> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance()
    return (0..6).map {
        val isToday = cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val dow = listOf("日","月","火","水","木","金","土")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val date = "${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.DAY_OF_MONTH)}"
        val info = DayInfo(dayLabel = if (isToday) "今日" else dow, dateLabel = date, isToday = isToday)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        info
    }
}

// 週範囲テキスト (例: "5/5～5/11")
fun weekRangeLabel(weekOffset: Int = 0): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = "${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val end = "${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    return "$start～$end"
}

private fun dayStartMs(weekOffset: Int, dayIndex: Int): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        add(Calendar.DAY_OF_YEAR, dayIndex)
    }
    return cal.timeInMillis
}

// 棒グラフ用カラーパレット（アプリ別）
val PIE_COLORS = listOf(
    Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05),
    Color(0xFF34A853), Color(0xFF9C27B0), Color(0xFFFF5722),
    Color(0xFF00BCD4), Color(0xFF607D8B)
)

// ===== GraphScreen =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit
) {
    var period by remember { mutableStateOf(RankingPeriod.WEEK) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    // 棒グラフ用: 週オフセット（0=今週、-1=先週…）
    var weekOffset by remember { mutableStateOf(0) }

    var barEntries by remember { mutableStateOf<List<BarEntry>>(emptyList()) }
    var pieEntries by remember { mutableStateOf<List<PieEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // データ集計
    LaunchedEffect(period, weekOffset) {
        scope.launch {
            val allHistory = withContext(Dispatchers.IO) { viewModel.getAllHistory() }

            // ===== 棒グラフ: 1週間の日別再生時間 =====
            val dayInfos = weekDayInfoList(weekOffset)
            val bars = (0..6).map { dayIdx ->
                val fromMs = dayStartMs(weekOffset, dayIdx)
                val toMs   = fromMs + 24 * 60 * 60 * 1000L - 1L
                val dayTime = allHistory
                    .filter { it.playedAtMs in fromMs..toMs }
                    .sumOf { it.playTimeMs }
                val info = dayInfos[dayIdx]
                BarEntry(
                    label     = info.dayLabel,
                    dateLabel = info.dateLabel,
                    valueMs   = dayTime,
                    isToday   = info.isToday,
                    dateMs    = fromMs
                )
            }
            barEntries = bars

            // ===== 円グラフ: 期間中のアプリ別使用率 =====
            val startMs = periodStartMs(period)
            val endMs   = periodEndMs(period)
            val filtered = allHistory.filter { it.playedAtMs in startMs..endMs }
            val appMap = mutableMapOf<String, Pair<String, Long>>()
            for (e in filtered) {
                val key = e.packageName
                val prev = appMap[key]?.second ?: 0L
                appMap[key] = Pair(e.appLabel.ifEmpty { e.packageName }, prev + e.playTimeMs)
            }
            val sorted = appMap.entries.sortedByDescending { it.value.second }
            pieEntries = sorted.mapIndexed { i, entry ->
                PieEntry(
                    label      = entry.value.first,
                    packageName = entry.key,
                    valueMs    = entry.value.second,
                    color      = PIE_COLORS[i % PIE_COLORS.size]
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("グラフ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = {
                    Box {
                        OutlinedButton(
                            onClick = { periodMenuExpanded = true },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(period.label, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = periodMenuExpanded,
                            onDismissRequest = { periodMenuExpanded = false }
                        ) {
                            RankingPeriod.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.label) },
                                    onClick = { period = p; periodMenuExpanded = false },
                                    leadingIcon = if (p == period) ({
                                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                    }) else null
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 棒グラフ =====
            item {
                GraphCard(title = "聴いた時間（長押しで詳細）") {
                    if (barEntries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        // 週範囲表示
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "聴いた時間",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                weekRangeLabel(weekOffset),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        var tappedEntry by remember { mutableStateOf<BarEntry?>(null) }
                        BarChart(
                            entries = barEntries,
                            barColor = MaterialTheme.colorScheme.primary,
                            todayColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            onTap = { entry -> tappedEntry = if (tappedEntry == entry) null else entry }
                        )
                        // タップ時のポップアップ表示
                        tappedEntry?.let { entry ->
                            val totalSec = entry.valueMs / 1_000L
                            val h = totalSec / 3_600L
                            val m = (totalSec % 3_600L) / 60L
                            val s = totalSec % 60L
                            val timeStr = when {
                                h > 0 && m > 0 -> "${h}時間${m}分"
                                h > 0          -> "${h}時間"
                                m > 0          -> "${m}分"
                                else           -> "${s}秒"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    "${entry.dateLabel}  $timeStr",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        // 前の週ナビ
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { weekOffset-- },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Rounded.ChevronLeft, null,
                                    modifier = Modifier.size(16.dp))
                                Text("前の週", fontSize = 12.sp)
                            }
                            if (weekOffset < 0) {
                                TextButton(
                                    onClick = { weekOffset++ },
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("次の週", fontSize = 12.sp)
                                    Icon(Icons.Rounded.ChevronRight, null,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ===== 円グラフ =====
            item {
                GraphCard(title = "アプリの使用率") {
                    if (pieEntries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                            Text("データなし",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                fontSize = 13.sp)
                        }
                    } else {
                        PieChart(entries = pieEntries)
                        Spacer(Modifier.height(12.dp))
                        PieLegend(entries = pieEntries)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ===== グラフカードラッパー =====
@Composable
fun GraphCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outline.copy(0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

// ===== 棒グラフ =====
@Composable
fun BarChart(
    entries: List<BarEntry>,
    barColor: Color,
    todayColor: Color,
    labelColor: Color,
    onTap: (BarEntry) -> Unit = {}
) {
    val maxMs = entries.maxOfOrNull { it.valueMs }?.coerceAtLeast(1L) ?: 1L

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "barAnim"
    )

    // Y軸ラベル: maxMsから「きりのいい上限値」を決定し等分割
    // 棒の高さは maxMs ではなく yMaxMs 基準で描画することでグリッドと一致させる
    val maxMins = maxMs / 60_000L  // 最大値を分単位に変換
    val yMaxMs: Long
    val yStepMs: Long
    val yStepCount: Int
    when {
        maxMins <= 10L  -> { yMaxMs = 10 * 60_000L;  yStepMs = 5 * 60_000L;   yStepCount = 2 }
        maxMins <= 20L  -> { yMaxMs = 20 * 60_000L;  yStepMs = 10 * 60_000L;  yStepCount = 2 }
        maxMins <= 30L  -> { yMaxMs = 30 * 60_000L;  yStepMs = 15 * 60_000L;  yStepCount = 2 }
        maxMins <= 60L  -> { yMaxMs = 60 * 60_000L;  yStepMs = 30 * 60_000L;  yStepCount = 2 }
        maxMins <= 90L  -> { yMaxMs = 90 * 60_000L;  yStepMs = 30 * 60_000L;  yStepCount = 3 }
        maxMins <= 120L -> { yMaxMs = 120 * 60_000L; yStepMs = 60 * 60_000L;  yStepCount = 2 }
        maxMins <= 180L -> { yMaxMs = 180 * 60_000L; yStepMs = 60 * 60_000L;  yStepCount = 3 }
        maxMins <= 360L -> { yMaxMs = 360 * 60_000L; yStepMs = 120 * 60_000L; yStepCount = 3 }
        maxMins <= 720L -> { yMaxMs = 720 * 60_000L; yStepMs = 240 * 60_000L; yStepCount = 3 }
        else            -> { yMaxMs = 1440 * 60_000L;yStepMs = 480 * 60_000L; yStepCount = 3 }
    }
    // Y軸ラベル文字列（0 から yMaxMs まで yStepCount+1 本）
    val yLabels = (0..yStepCount).map { i ->
        val valMs = yStepMs * i
        val valMins = valMs / 60_000L
        if (valMins >= 60L) "${valMins / 60}h" else "${valMins}m"
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface

    // ラベル描画に使うPaint
    val labelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val todayPaint = remember(todayColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }

    Column {
        Text("時間", fontSize = 10.sp, color = labelColor,
            modifier = Modifier.padding(bottom = 2.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Y軸目盛り（固定幅）
            Column(
                modifier = Modifier.width(28.dp).height(160.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                yLabels.reversed().forEach { label ->
                    Text(label, fontSize = 9.sp, color = labelColor)
                }
            }

            // グラフ本体 + X軸ラベルをCanvasで一体描画
            // → 棒の中心とラベルの中心が完全一致
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(200.dp)  // グラフ160dp + ラベル40dp
                    .pointerInput(entries) {
                        // タップで棒を選択
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed) continue
                                // 指を離した位置でタップ判定
                                val tapX: Float = change.position.x
                                val chartW: Float = size.width.toFloat()
                                val barCount = entries.size
                                val totalGap = chartW * 0.30f
                                val barW = (chartW - totalGap) / barCount
                                val gap  = totalGap / (barCount + 1)
                                val idx = entries.indices.firstOrNull { i ->
                                    val barLeftX = gap + i * (barW + gap)
                                    tapX in barLeftX..(barLeftX + barW)
                                }
                                idx?.let { onTap(entries[it]) }
                                change.consume()
                            }
                        }
                    }
            ) {
                val chartW   = size.width
                val chartH   = 160.dp.toPx()   // グラフ領域
                val labelH   = 40.dp.toPx()    // ラベル領域
                val barCount = entries.size
                val totalGap = chartW * 0.30f
                val barW     = (chartW - totalGap) / barCount
                val gap      = totalGap / (barCount + 1)

                val dateFontSize  = with(density) { 9.sp.toPx() }
                val dayFontSize   = with(density) { 11.sp.toPx() }

                // グリッド線（yStepCountに基づく等間隔）
                repeat(yLabels.size) { i ->
                    val y = chartH * (1f - i.toFloat() / yStepCount)
                    drawLine(
                        color = onSurface.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end   = Offset(chartW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 棒 & ラベル
                entries.forEachIndexed { i, entry ->
                    val barCenterX = gap + i * (barW + gap) + barW / 2f
                    val barLeft    = gap + i * (barW + gap)

                    // 棒
                    val heightFrac = (entry.valueMs.toFloat() / yMaxMs.toFloat()) * animProgress
                    val barH = chartH * heightFrac.coerceIn(0f, 1f)
                    val color = if (entry.isToday) todayColor else barColor
                    if (barH > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(barLeft, chartH - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }

                    // X軸ラベル（日付・曜日）をCanvasのdrawContext.canvasで描画
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val paint = if (entry.isToday) todayPaint else labelPaint
                    val paintColor = if (entry.isToday)
                        AndroidColor.argb(
                            (todayColor.alpha * 255).toInt(),
                            (todayColor.red * 255).toInt(),
                            (todayColor.green * 255).toInt(),
                            (todayColor.blue * 255).toInt()
                        )
                    else
                        AndroidColor.argb(
                            (labelColor.alpha * 255).toInt(),
                            (labelColor.red * 255).toInt(),
                            (labelColor.green * 255).toInt(),
                            (labelColor.blue * 255).toInt()
                        )

                    // 日付（上段）
                    paint.textSize = dateFontSize
                    paint.color = paintColor
                    nativeCanvas.drawText(
                        entry.dateLabel,
                        barCenterX,
                        chartH + dateFontSize + 4.dp.toPx(),
                        paint
                    )
                    // 曜日（下段）
                    paint.textSize = dayFontSize
                    nativeCanvas.drawText(
                        entry.label,
                        barCenterX,
                        chartH + dateFontSize + dayFontSize + 8.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}

// ===== 円グラフ =====
@Composable
fun PieChart(entries: List<PieEntry>) {
    val totalMs = entries.sumOf { it.valueMs }.coerceAtLeast(1L)

    // アニメーション
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "pieAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 40.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            var startAngle = -90f

            entries.forEach { entry ->
                val fraction = entry.valueMs.toFloat() / totalMs
                val sweepAngle = 360f * fraction * animProgress
                drawArc(
                    color = entry.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweepAngle
            }
        }

        // 中央テキスト（合計時間）
        val totalMs2 = entries.sumOf { it.valueMs }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatListenedTime(totalMs2),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "合計",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

// ===== 円グラフ凡例 =====
@Composable
fun PieLegend(entries: List<PieEntry>) {
    val context = LocalContext.current
    val totalMs = entries.sumOf { it.valueMs }.coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            val pct = (entry.valueMs.toFloat() / totalMs * 100).toInt()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // アプリアイコン
                val icon = remember(entry.packageName) {
                    try { context.packageManager.getApplicationIcon(entry.packageName) }
                    catch (e: Exception) { null }
                }
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(entry.color, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(Modifier.width(8.dp))
                // カラーバー
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(entry.color, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    entry.label,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$pct%  ${formatListenedTime(entry.valueMs)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Drawable → Bitmap 拡張
private fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable) return bitmap
    val bmp = android.graphics.Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}