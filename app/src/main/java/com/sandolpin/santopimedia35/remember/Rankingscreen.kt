package com.sandolpin.santopimedia35.remember

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ===== 期間フィルター =====
enum class RankingPeriod(val label: String) {
    TODAY("きょう"),
    WEEK("今週"),
    MONTH("今月"),
    LAST_MONTH("先月"),
    YEAR("今年")
}

// ===== ランキング集計データ =====
data class ArtistRankItem(
    val artist: String,
    val albumArtUri: String?,
    val trackCount: Int,
    val playTimeMs: Long
)

data class TrackRankItem(
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playCount: Int,
    val playTimeMs: Long
)

data class AppRankItem(
    val appLabel: String,
    val packageName: String,
    val playTimeMs: Long,
    val trackCount: Int
)

data class RankingSummary(
    val totalPlayTimeMs: Long,
    val totalTrackCount: Int,
    val appRanking: List<AppRankItem>,
    val appTrackRanking: List<AppRankItem>
)

// ===== 期間のstartMsを計算 =====
fun periodStartMs(period: RankingPeriod): Long {
    val cal = Calendar.getInstance()
    return when (period) {
        RankingPeriod.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        RankingPeriod.WEEK -> {
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        RankingPeriod.MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        RankingPeriod.LAST_MONTH -> {
            cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        RankingPeriod.YEAR -> {
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }
}

fun periodEndMs(period: RankingPeriod): Long {
    if (period != RankingPeriod.LAST_MONTH) return Long.MAX_VALUE
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis - 1L
}

// ===== 集計関数 =====
fun computeRanking(
    history: List<PlayHistoryEntity>,
    period: RankingPeriod
): Triple<RankingSummary, List<ArtistRankItem>, List<TrackRankItem>> {
    val startMs = periodStartMs(period)
    val endMs   = periodEndMs(period)
    val filtered = history.filter { it.playedAtMs in startMs..endMs }

    val totalPlayTime = filtered.sumOf { it.playTimeMs }
    val totalCount    = filtered.size

    // アプリランキング（再生時間）
    val appTimeMap = mutableMapOf<String, Pair<String, Long>>()
    val appCountMap = mutableMapOf<String, Pair<String, Int>>()
    for (e in filtered) {
        val key = e.packageName
        val prev = appTimeMap[key]?.second ?: 0L
        appTimeMap[key] = Pair(e.appLabel.ifEmpty { e.packageName }, prev + e.playTimeMs)
        val prevC = appCountMap[key]?.second ?: 0
        appCountMap[key] = Pair(e.appLabel.ifEmpty { e.packageName }, prevC + 1)
    }
    val appRanking = appTimeMap.entries
        .sortedByDescending { it.value.second }
        .map { AppRankItem(it.value.first, it.key, it.value.second,
            appCountMap[it.key]?.second ?: 0) }
    val appTrackRanking = appCountMap.entries
        .sortedByDescending { it.value.second }
        .map { AppRankItem(it.value.first, it.key, appTimeMap[it.key]?.second ?: 0L,
            it.value.second) }

    val summary = RankingSummary(totalPlayTime, totalCount, appRanking, appTrackRanking)

    // アーティストランキング
    data class ArtistAcc(var count: Int, var time: Long, var artUri: String?)
    val artistMap = mutableMapOf<String, ArtistAcc>()
    for (e in filtered) {
        val key = e.artist.ifEmpty { "不明" }
        val acc = artistMap.getOrPut(key) { ArtistAcc(0, 0L, null) }
        acc.count++
        acc.time += e.playTimeMs
        if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
    }
    val artistRanking = artistMap.entries
        .sortedByDescending { it.value.time }
        .map { ArtistRankItem(it.key, it.value.artUri, it.value.count, it.value.time) }

    // 曲ランキング（再生回数）
    data class TrackAcc(var count: Int, var time: Long, val artist: String, var artUri: String?)
    val trackMap = mutableMapOf<String, TrackAcc>()
    for (e in filtered) {
        val key = "${e.title}||${e.artist}"
        val acc = trackMap.getOrPut(key) {
            TrackAcc(0, 0L, e.artist.ifEmpty { "不明" }, null)
        }
        acc.count++
        acc.time += e.playTimeMs
        if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
    }
    val trackRanking = trackMap.entries
        .sortedByDescending { it.value.count }
        .map { TrackRankItem(
            it.key.substringBefore("||"),
            it.value.artist,
            it.value.artUri,
            it.value.count,
            it.value.time
        ) }

    return Triple(summary, artistRanking, trackRanking)
}

// ===== RankingScreen =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit,
    onArtistClick: (ArtistRankItem) -> Unit = {}
) {
    val historyList by viewModel.historyList.collectAsState()
    var period by remember { mutableStateOf(RankingPeriod.WEEK) }
    var periodMenuExpanded by remember { mutableStateOf(false) }

    // 集計（IOスレッドで）
    var summary by remember { mutableStateOf<RankingSummary?>(null) }
    var artistRanking by remember { mutableStateOf<List<ArtistRankItem>>(emptyList()) }
    var trackRanking  by remember { mutableStateOf<List<TrackRankItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(historyList, period) {
        scope.launch {
            val allHistory = withContext(Dispatchers.IO) {
                viewModel.getAllHistory()
            }
            val (s, a, t) = computeRanking(allHistory, period)
            summary = s
            artistRanking = a
            trackRanking  = t
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ランキング・レポート", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = {
                    // 期間トグル
                    Box {
                        OutlinedButton(
                            onClick = { periodMenuExpanded = true },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(period.label, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowDropDown, null,
                                modifier = Modifier.size(18.dp))
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
                                        Icon(Icons.Rounded.Check, null,
                                            modifier = Modifier.size(16.dp))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== サマリーカード =====
            item {
                summary?.let { s ->
                    SummaryCard(
                        period = period,
                        summary = s,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                } ?: run {
                    Box(Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }

            // ===== アーティストランキング =====
            item {
                RankingSection(
                    title = "アーティストランキング",
                    icon = Icons.Rounded.Person
                ) {
                    if (artistRanking.isEmpty()) {
                        Text("データなし", color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                    } else {
                        artistRanking.take(10).forEachIndexed { i, item ->
                            ArtistRankRow(
                                rank = i + 1,
                                item = item,
                                onClick = { onArtistClick(item) }
                            )
                            if (i < minOf(9, artistRanking.size - 1)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            }
                        }
                    }
                }
            }

            // ===== 最も聴いた曲ランキング =====
            item {
                RankingSection(
                    title = "最も聴いた曲数",
                    icon = Icons.Rounded.MusicNote
                ) {
                    if (trackRanking.isEmpty()) {
                        Text("データなし", color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                    } else {
                        trackRanking.take(10).forEachIndexed { i, item ->
                            TrackRankRow(rank = i + 1, item = item)
                            if (i < minOf(9, trackRanking.size - 1)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ===== サマリーカード =====
@Composable
fun SummaryCard(
    period: RankingPeriod,
    summary: RankingSummary,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val periodLabel = when (period) {
        RankingPeriod.TODAY      -> "きょう"
        RankingPeriod.WEEK       -> "今週"
        RankingPeriod.MONTH      -> "今月"
        RankingPeriod.LAST_MONTH -> "先月"
        RankingPeriod.YEAR       -> "今年"
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 基本情報（常時表示）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${periodLabel}聴いた時間",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    Text(
                        formatListenedTime(summary.totalPlayTimeMs),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${periodLabel}聴いた曲数",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                    Text(
                        "${summary.totalTrackCount}曲",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // 展開ボタン
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp
                        else Icons.Rounded.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }

            // 展開コンテンツ（アプリ別集計）
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit  = shrinkVertically(tween(300)) + fadeOut(tween(300))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                    Spacer(Modifier.height(12.dp))

                    // 聴いた時間が多いアプリ
                    if (summary.appRanking.isNotEmpty()) {
                        AppRankingMini(
                            title = "聴いた時間が多いアプリ",
                            items = summary.appRanking.take(5),
                            valueSelector = { formatListenedTime(it.playTimeMs) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // 聴いた曲数が多いアプリ
                    if (summary.appTrackRanking.isNotEmpty()) {
                        AppRankingMini(
                            title = "聴いた曲数が多いアプリ",
                            items = summary.appTrackRanking.take(5),
                            valueSelector = { "${it.trackCount}曲" }
                        )
                    }
                }
            }
        }
    }
}

// ===== アプリ別ミニランキング =====
@Composable
fun AppRankingMini(
    title: String,
    items: List<AppRankItem>,
    valueSelector: (AppRankItem) -> String
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.04f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // アプリアイコン
                    val icon = remember(item.packageName) {
                        try { context.packageManager.getApplicationIcon(item.packageName) }
                        catch (e: Exception) { null }
                    }
                    if (icon != null) {
                        androidx.compose.foundation.Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Icon(Icons.Rounded.Android, null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item.appLabel,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        valueSelector(item),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                    )
                }
            }
        }
    }
}

// ===== セクションラッパー（塗りつぶしなし・縁取り）=====
@Composable
fun RankingSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    var collapsed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { collapsed = !collapsed },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (collapsed) Icons.Rounded.KeyboardArrowDown
                        else Icons.Rounded.KeyboardArrowUp,
                        null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            }
            AnimatedVisibility(
                visible = !collapsed,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit  = shrinkVertically(tween(250)) + fadeOut(tween(250))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

// ===== 順位バッジ =====
@Composable
fun RankBadge(rank: Int) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (bg, textColor) = when (rank) {
        1 -> Pair(Color(0xFFFFD700), Color(0xFF7A5800))
        2 -> Pair(Color(0xFFC0C0C0), Color(0xFF444444))
        3 -> Pair(Color(0xFFCD7F32), Color(0xFF5C2D00))
        else -> Pair(
            if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f),
            MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$rank",
            fontSize = if (rank <= 3) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ===== アーティスト行 =====
@Composable
fun ArtistRankRow(rank: Int, item: ArtistRankItem, onClick: () -> Unit = {}) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        // アートワーク
        if (item.albumArtUri != null) {
            AsyncImage(
                model = item.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.artist, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.trackCount}曲、${formatListenedTime(item.playTimeMs)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }
    }
}

// ===== 曲行 =====
@Composable
fun TrackRankRow(rank: Int, item: TrackRankItem) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        if (item.albumArtUri != null) {
            AsyncImage(
                model = item.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.08f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title.ifEmpty { "タイトル不明" },
                fontWeight = FontWeight.Medium, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.playCount}回",
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ===== Drawable → Bitmap 拡張（AppSelectScreen.ktと同様）=====
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