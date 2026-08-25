package com.sandolpin.santopimedia35.remember

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.favorite.FavoriteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// =============================================================================
// ReportPeriod: レポート画面の「見る単位」（週/月/年）とオフセットを封じ込める
// sealed class にすることで、各モードを型安全に扱える
// =============================================================================
sealed class ReportPeriod {
    data class Week(val weekOffset: Int = 0) : ReportPeriod()
    data class Month(val monthOffset: Int = 0) : ReportPeriod()
    data class Year(val yearOffset: Int = 0) : ReportPeriod()

    /** トップバーに表示するラベル */
    fun label(): String = when (this) {
        is Week  -> if (weekOffset == 0) "今週" else weekRangeLabel(weekOffset)
        is Month -> reportMonthLabel(monthOffset)
        is Year  -> reportYearLabel(yearOffset)
    }

    /** この期間の開始時刻(ms) */
    fun startMs(): Long {
        val cal = Calendar.getInstance()
        return when (this) {
            is Week -> {
                cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            is Month -> {
                cal.add(Calendar.MONTH, monthOffset)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            is Year -> {
                cal.add(Calendar.YEAR, yearOffset)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }

    /** この期間の終了時刻(ms) */
    fun endMs(): Long {
        val cal = Calendar.getInstance()
        return when (this) {
            is Week -> {
                cal.add(Calendar.WEEK_OF_YEAR, weekOffset + 1)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis - 1L
            }
            is Month -> {
                cal.add(Calendar.MONTH, monthOffset + 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis - 1L
            }
            is Year -> {
                cal.add(Calendar.YEAR, yearOffset + 1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis - 1L
            }
        }
    }

    fun prev(): ReportPeriod = when (this) {
        is Week  -> Week(weekOffset - 1)
        is Month -> Month(monthOffset - 1)
        is Year  -> Year(yearOffset - 1)
    }

    fun next(): ReportPeriod? = when (this) {
        is Week  -> if (weekOffset < 0) Week(weekOffset + 1) else null
        is Month -> if (monthOffset < 0) Month(monthOffset + 1) else null
        is Year  -> if (yearOffset < 0) Year(yearOffset + 1) else null
    }

    /** 棒グラフのバーエントリを構築する */
    fun buildBarEntries(allHistory: List<PlayHistoryEntity>): List<BarEntry> {
        return when (this) {
            is Week -> buildWeekBars(allHistory, weekOffset)
            is Month -> buildMonthBars(allHistory, monthOffset)
            is Year  -> buildYearBars(allHistory, yearOffset)
        }
    }
}

private fun reportMonthLabel(monthOffset: Int): String {
    if (monthOffset == 0) return "今月"
    if (monthOffset == -1) return "先月"
    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    return "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH)+1}月"
}

private fun reportYearLabel(yearOffset: Int): String {
    if (yearOffset == 0) return "今年"
    val cal = Calendar.getInstance().apply { add(Calendar.YEAR, yearOffset) }
    return "${cal.get(Calendar.YEAR)}年"
}

// ===== 棒グラフデータ構築ヘルパー =====

internal fun buildWeekBars(allHistory: List<PlayHistoryEntity>, weekOffset: Int): List<BarEntry> {
    val dayInfos = reportWeekDayInfoList(weekOffset)
    return (0..6).map { dayIdx ->
        val fromMs = reportDayStartMs(weekOffset, dayIdx)
        val toMs   = fromMs + 24 * 60 * 60 * 1000L - 1L
        val dayTime = allHistory.filter { it.playedAtMs in fromMs..toMs }.sumOf { it.playTimeMs }
        val info = dayInfos[dayIdx]
        BarEntry(label = info.dayLabel, dateLabel = info.dateLabel,
            valueMs = dayTime, isToday = info.isToday, dateMs = fromMs)
    }
}

// 月モード: 直近12ヶ月を1本ずつ並べる（現在の monthOffset を右端にした 12ヶ月分）
// 例: monthOffset=0（今月）なら、11ヶ月前〜今月の12本
internal fun buildMonthBars(allHistory: List<PlayHistoryEntity>, monthOffset: Int): List<BarEntry> {
    val today = Calendar.getInstance()
    return (-11..0).map { offset ->
        val targetOffset = monthOffset + offset
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, targetOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val fromMs = cal.timeInMillis
        val toCal = cal.clone() as Calendar
        toCal.add(Calendar.MONTH, 1)
        val toMs = toCal.timeInMillis - 1L
        val isCurrentMonth = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == today.get(Calendar.MONTH)
        val monthTime = allHistory.filter { it.playedAtMs in fromMs..toMs }.sumOf { it.playTimeMs }
        val label     = "${cal.get(Calendar.MONTH) + 1}月"
        val dateLabel = "${cal.get(Calendar.YEAR)}/${cal.get(Calendar.MONTH) + 1}"
        BarEntry(label = label, dateLabel = dateLabel, valueMs = monthTime,
            isToday = isCurrentMonth, dateMs = fromMs)
    }
}

// 年モード: 直近5年を1本ずつ並べる（現在の yearOffset を右端にした 5年分）
private fun buildYearBars(allHistory: List<PlayHistoryEntity>, yearOffset: Int): List<BarEntry> {
    val today = Calendar.getInstance()
    return (-4..0).map { offset ->
        val targetOffset = yearOffset + offset
        val cal = Calendar.getInstance().apply {
            add(Calendar.YEAR, targetOffset)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val fromMs = cal.timeInMillis
        val toCal = cal.clone() as Calendar
        toCal.add(Calendar.YEAR, 1)
        val toMs = toCal.timeInMillis - 1L
        val isCurrentYear = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val yearTime = allHistory.filter { it.playedAtMs in fromMs..toMs }.sumOf { it.playTimeMs }
        val label = "${cal.get(Calendar.YEAR)}"
        BarEntry(label = label, dateLabel = "${cal.get(Calendar.YEAR)}年", valueMs = yearTime,
            isToday = isCurrentYear, dateMs = fromMs)
    }
}

// GraphScreen.kt の dayStartMs と衝突しないようにリネーム
private fun reportDayStartMs(weekOffset: Int, dayIndex: Int): Long {
    val cal = Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, weekOffset)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayIndex)
    }
    return cal.timeInMillis
}

// GraphScreen.kt の weekDayInfoList は private で他ファイルから参照できないため、
// 同等のロジックをこちらに複製（DayInfo自体はGraphScreen.ktのpublicなdata classを再利用）
private fun reportWeekDayInfoList(weekOffset: Int = 0): List<DayInfo> {
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

// =============================================================================
// TrackFilter: 「聴いた曲」セクションのしぼりこみ単位
// =============================================================================
sealed class TrackFilter {
    object All : TrackFilter()
    data class ByDay(val dayStartMs: Long) : TrackFilter()
    data class ByWeek(val weekStart: Long) : TrackFilter()
    data class ByMonth(val monthStart: Long) : TrackFilter()

    fun label(): String = when (this) {
        is All -> "すべて"
        is ByDay -> {
            val c = Calendar.getInstance().apply { timeInMillis = dayStartMs }
            "${c.get(Calendar.MONTH)+1}/${c.get(Calendar.DAY_OF_MONTH)}"
        }
        is ByWeek -> {
            val c = Calendar.getInstance().apply { timeInMillis = weekStart }
            val s = "${c.get(Calendar.MONTH)+1}/${c.get(Calendar.DAY_OF_MONTH)}"
            c.add(Calendar.DAY_OF_YEAR, 6)
            val e = "${c.get(Calendar.MONTH)+1}/${c.get(Calendar.DAY_OF_MONTH)}"
            "$s〜$e"
        }
        is ByMonth -> {
            val c = Calendar.getInstance().apply { timeInMillis = monthStart }
            "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH)+1}月"
        }
    }

    fun rangeMs(): LongRange = when (this) {
        is All     -> 0L..Long.MAX_VALUE
        is ByDay   -> dayStartMs..(dayStartMs + 24 * 60 * 60 * 1000L - 1L)
        is ByWeek  -> weekStart..(weekStart + 7 * 24 * 60 * 60 * 1000L - 1L)
        is ByMonth -> {
            val end = Calendar.getInstance().apply {
                timeInMillis = monthStart; add(Calendar.MONTH, 1)
            }.timeInMillis - 1L
            monthStart..end
        }
    }
}

// =============================================================================
// ランキング自前集計（computeRankingはRankingPeriod依存のため使わない）
// =============================================================================
internal data class ReportRanking(
    val artists: List<ArtistRankItem>,
    val tracks:  List<TrackRankItem>
)

internal fun computeReportRanking(history: List<PlayHistoryEntity>): ReportRanking {
    data class ArtistAcc(var count: Int, var time: Long, var artUri: String?)
    val artistMap = mutableMapOf<String, ArtistAcc>()
    for (e in history) {
        val key = e.artist.ifEmpty { "不明" }
        val acc = artistMap.getOrPut(key) { ArtistAcc(0, 0L, null) }
        acc.count++; acc.time += e.playTimeMs
        if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
    }
    val artists = artistMap.entries.sortedByDescending { it.value.time }
        .map { ArtistRankItem(it.key, it.value.artUri, it.value.count, it.value.time) }

    data class TrackAcc(var count: Int, var time: Long, val artist: String, var artUri: String?)
    val trackMap = mutableMapOf<String, TrackAcc>()
    for (e in history) {
        val key = "${e.title}||${e.artist}"
        val acc = trackMap.getOrPut(key) { TrackAcc(0, 0L, e.artist.ifEmpty { "不明" }, null) }
        acc.count++; acc.time += e.playTimeMs
        if (acc.artUri == null && e.albumArtUri != null) acc.artUri = e.albumArtUri
    }
    val tracks = trackMap.entries.sortedByDescending { it.value.count }
        .map { TrackRankItem(it.key.substringBefore("||"),
            it.value.artist, it.value.artUri, it.value.count, it.value.time) }

    return ReportRanking(artists, tracks)
}

// =============================================================================
// ReportScreen 本体
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: HistoryViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit,
    onArtistClick: (ArtistRankItem) -> Unit = {},
    onShowAllTracks: (ReportPeriod) -> Unit = {}
) {
    val context = LocalContext.current
    val favoriteViewModel: FavoriteViewModel = composeViewModel(
        factory = FavoriteViewModel.Factory(context)
    )
    val settings by mediaPlayerViewModel.settings.collectAsState()

    var period by remember { mutableStateOf<ReportPeriod>(ReportPeriod.Week(0)) }
    var periodMenuExpanded by remember { mutableStateOf(false) }

    var barEntries      by remember { mutableStateOf<List<BarEntry>>(emptyList()) }
    var pieEntries      by remember { mutableStateOf<List<PieEntry>>(emptyList()) }
    var totalPlayMs     by remember { mutableStateOf(0L) }
    var totalCount      by remember { mutableStateOf(0) }
    var artistRanking   by remember { mutableStateOf<List<ArtistRankItem>>(emptyList()) }
    var trackRanking    by remember { mutableStateOf<List<TrackRankItem>>(emptyList()) }
    var periodHistory   by remember { mutableStateOf<List<PlayHistoryEntity>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(false) }

    var trackFilter     by remember { mutableStateOf<TrackFilter>(TrackFilter.All) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // ===== シェア機能 =====
    var showShareDialog by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val shareLauncher = rememberShareImageLauncher()

    val scope = rememberCoroutineScope()

    // ===== データ集計（period が変わるたびに再集計）=====
    LaunchedEffect(period) {
        trackFilter = TrackFilter.All
        isLoading = true
        scope.launch {
            val allHistory = withContext(Dispatchers.IO) { viewModel.getAllHistory() }
            val startMs = period.startMs()
            val endMs   = period.endMs()
            val filtered = allHistory.filter { it.playedAtMs in startMs..endMs }

            totalPlayMs   = filtered.sumOf { it.playTimeMs }
            totalCount    = filtered.size
            periodHistory = filtered.sortedByDescending { it.playedAtMs }

            barEntries = withContext(Dispatchers.Default) {
                period.buildBarEntries(allHistory)
            }

            val appMap = mutableMapOf<String, Pair<String, Long>>()
            for (e in filtered) {
                val prev = appMap[e.packageName]?.second ?: 0L
                appMap[e.packageName] = Pair(e.appLabel.ifEmpty { e.packageName }, prev + e.playTimeMs)
            }
            pieEntries = appMap.entries.sortedByDescending { it.value.second }
                .mapIndexed { i, entry ->
                    PieEntry(label = entry.value.first, packageName = entry.key,
                        valueMs = entry.value.second, color = PIE_COLORS[i % PIE_COLORS.size])
                }

            val ranking = withContext(Dispatchers.Default) { computeReportRanking(filtered) }
            artistRanking = ranking.artists
            trackRanking  = ranking.tracks
            isLoading = false
        }
    }

    // しぼりこみ後の曲リスト
    val filteredHistory = remember(periodHistory, trackFilter) {
        if (trackFilter is TrackFilter.All) periodHistory
        else {
            val range = trackFilter.rangeMs()
            periodHistory.filter { it.playedAtMs in range }
        }
    }

    val trackDisplayLimit = 5
    val displayHistory = filteredHistory.take(trackDisplayLimit)

    // しぼりこみ選択肢（period と履歴から生成）
    val filterOptions: List<TrackFilter> = remember(period, periodHistory) {
        buildReportFilterOptions(period, periodHistory)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("レポート", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = {
                    // シェアボタン
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(Icons.Rounded.Share, "シェア",
                            modifier = Modifier.size(20.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        // ← 前へ
                        IconButton(
                            onClick = { period = period.prev() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, "前へ",
                                modifier = Modifier.size(20.dp))
                        }

                        // ラベル ▼（ドロップダウン）
                        Box {
                            OutlinedButton(
                                onClick = { periodMenuExpanded = true },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(period.label(), fontSize = 13.sp)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Rounded.ArrowDropDown, null,
                                    modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = periodMenuExpanded,
                                onDismissRequest = { periodMenuExpanded = false }
                            ) {
                                // ── モード切り替え ──
                                DropdownMenuItem(
                                    text = {
                                        Text("── 表示単位 ──", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                    },
                                    onClick = {}, enabled = false
                                )
                                listOf(
                                    "週で見る" to (period is ReportPeriod.Week),
                                    "月で見る" to (period is ReportPeriod.Month),
                                    "年で見る" to (period is ReportPeriod.Year)
                                ).forEachIndexed { idx, (label, isCurrent) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = if (isCurrent) ({
                                            Icon(Icons.Rounded.Check, null,
                                                modifier = Modifier.size(16.dp))
                                        }) else null,
                                        onClick = {
                                            period = when (idx) {
                                                0 -> ReportPeriod.Week(0)
                                                1 -> ReportPeriod.Month(0)
                                                else -> ReportPeriod.Year(0)
                                            }
                                            periodMenuExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                // ── ショートカット ──
                                DropdownMenuItem(
                                    text = {
                                        Text("── 最近の期間 ──", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                    },
                                    onClick = {}, enabled = false
                                )
                                buildShortcuts(period).forEach { (label, target) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = if (period == target) ({
                                            Icon(Icons.Rounded.Check, null,
                                                modifier = Modifier.size(16.dp))
                                        }) else null,
                                        onClick = {
                                            period = target
                                            periodMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // → 次へ（現在より未来には進まない）
                        val nextPeriod = period.next()
                        IconButton(
                            onClick = { nextPeriod?.let { period = it } },
                            modifier = Modifier.size(36.dp),
                            enabled = nextPeriod != null
                        ) {
                            Icon(Icons.Rounded.ChevronRight, "次へ",
                                modifier = Modifier.size(20.dp),
                                tint = if (nextPeriod != null)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(0.25f))
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ─────────────────────────────────────────
            // 1. サマリー（聴いた時間・曲数）
            // ─────────────────────────────────────────
            item {
                ReportSummaryCard(
                    totalPlayMs = totalPlayMs,
                    totalCount  = totalCount,
                    periodLabel = period.label()
                )
            }

            // ─────────────────────────────────────────
            // 2. 聴いた時間 棒グラフ
            // ─────────────────────────────────────────
            item {
                ReportSectionCard(title = "聴いた時間", icon = Icons.Rounded.BarChart) {
                    if (isLoading || barEntries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) {
                            if (isLoading)
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else
                                Text("データなし",
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(period.label(), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                        }
                        var tappedEntry by remember { mutableStateOf<BarEntry?>(null) }
                        BarChart(
                            entries    = barEntries,
                            barColor   = MaterialTheme.colorScheme.primary,
                            todayColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                            onTap      = { entry ->
                                tappedEntry = if (tappedEntry == entry) null else entry
                            }
                        )
                        tappedEntry?.let { entry ->
                            val s   = entry.valueMs / 1_000L
                            val h   = s / 3_600L; val m = (s % 3_600L) / 60L; val sec = s % 60L
                            val str = when {
                                h > 0 && m > 0 -> "${h}時間${m}分"
                                h > 0          -> "${h}時間"
                                m > 0          -> "${m}分"
                                else           -> "${sec}秒"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text("${entry.dateLabel}  $str", fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────
            // 3. 聴いた曲（しぼりこみ + HistoryCard）
            // ─────────────────────────────────────────
            item {
                ReportSectionHeader(
                    title         = "聴いた曲",
                    icon          = Icons.Rounded.LibraryMusic,
                    filterLabel   = trackFilter.label(),
                    isFiltered    = trackFilter !is TrackFilter.All,
                    onFilterClick = { showFilterDialog = true }
                )
            }

            if (displayHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isLoading) "読み込み中…" else "この期間の履歴はありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // 日付ごとにグループ化して表示（HistoryScreen と同じUX）
                val grouped = displayHistory.groupBy { entity ->
                    val c = Calendar.getInstance().apply { timeInMillis = entity.playedAtMs }
                    "%d/%d/%d".format(c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                }
                grouped.forEach { (dateKey, entities) ->
                    item(key = "rpt_hdr_$dateKey") {
                        val c = Calendar.getInstance().apply {
                            timeInMillis = entities.first().playedAtMs }
                        val today = Calendar.getInstance()
                        val yesterday = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -1) }
                        val lbl = when {
                            c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                    c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                -> "きょう"
                            c.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                                    c.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
                                -> "きのう"
                            else -> "%d月%d日".format(
                                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            Spacer(Modifier.width(10.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(0.25f)
                            )
                        }
                    }
                    items(entities, key = { "rpt_card_${it.id}" }) { entity ->
                        HistoryCard(
                            entity            = entity,
                            favoriteViewModel = favoriteViewModel,
                            cardBackgroundStyle = settings.historyCardBackgroundStyle,
                            showDate          = settings.historyShowDate,
                            timeRangeMode     = settings.historyTimeRangeMode,
                            artBorderEnabled  = settings.historyArtBorderEnabled,
                            showPlayTime      = settings.historyShowPlayTime,
                            showProgressBar   = settings.historyShowProgressBar,
                            artSize           = settings.historyArtSize,
                            onLongClick       = {}
                        )
                    }
                }

                // ===== もっと見る（別画面へ遷移）=====
                // 1曲でも履歴があれば表示する。
                // 残りがある場合は「もっと見る（残り◯曲）」、
                // すでに全件表示できている場合は「カレンダーで見る」という案内にする。
                if (filteredHistory.isNotEmpty()) {
                    item(key = "rpt_show_more") {
                        val remaining = filteredHistory.size - trackDisplayLimit
                        val label = if (remaining > 0) "もっと見る（残り${remaining}曲）" else "カレンダーで見る"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onShowAllTracks(period) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // ─────────────────────────────────────────
            // 4. アプリの使用率（円グラフ）
            // ─────────────────────────────────────────
            item {
                ReportSectionCard(title = "アプリの使用率", icon = Icons.Rounded.PieChart) {
                    if (pieEntries.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
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

            // ─────────────────────────────────────────
            // 5. ランキング
            // ─────────────────────────────────────────
            item {
                RankingSection(title = "アーティストランキング", icon = Icons.Rounded.Person) {
                    if (artistRanking.isEmpty()) {
                        Text("データなし",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                    } else {
                        artistRanking.take(10).forEachIndexed { i, item ->
                            ArtistRankRow(rank = i + 1, item = item,
                                onClick = { onArtistClick(item) })
                            if (i < minOf(9, artistRanking.size - 1)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            }
                        }
                    }
                }
            }

            item {
                RankingSection(title = "最も聴いた曲", icon = Icons.Rounded.MusicNote) {
                    if (trackRanking.isEmpty()) {
                        Text("データなし",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                    } else {
                        trackRanking.take(10).forEachIndexed { i, item ->
                            TrackRankRow(rank = i + 1, item = item)
                            if (i < minOf(9, trackRanking.size - 1)) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ===== しぼりこみダイアログ =====
    if (showFilterDialog) {
        TrackFilterDialog(
            options       = filterOptions,
            currentFilter = trackFilter,
            onSelect      = { trackFilter = it; showFilterDialog = false },
            onDismiss     = { showFilterDialog = false }
        )
    }

    // ===== シェア方法選択ダイアログ（きょう・今週・今月）=====
    if (showShareDialog) {
        ShareKindDialog(
            isSharing = isSharing,
            onSelect = { kind ->
                showShareDialog = false
                isSharing = true
                scope.launch {
                    val shareData = buildShareReportData(viewModel, kind)
                    shareLauncher(shareData)
                    isSharing = false
                }
            },
            onDismiss = { showShareDialog = false }
        )
    }
}

// =============================================================================
// シェア方法選択ダイアログ
// =============================================================================
@Composable
private fun ShareKindDialog(
    isSharing: Boolean,
    onSelect: (ShareKind) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isSharing) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Rounded.Share, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("レポートをシェア", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    if (!isSharing) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                Spacer(Modifier.height(8.dp))

                if (isSharing) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("画像を作成しています…", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }
                } else {
                    ShareKindRow(label = "きょう", description = "本日の記録",
                        onClick = { onSelect(ShareKind.TODAY) })
                    ShareKindRow(label = "今週", description = "今週(7日間)の記録",
                        onClick = { onSelect(ShareKind.WEEK) })
                    ShareKindRow(label = "今月", description = "今月の記録",
                        onClick = { onSelect(ShareKind.MONTH) })
                }
            }
        }
    }
}

@Composable
private fun ShareKindRow(label: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.size(18.dp))
        }
    }
}

// =============================================================================
// サマリーカード
// =============================================================================
@Composable
private fun ReportSummaryCard(totalPlayMs: Long, totalCount: Int, periodLabel: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${periodLabel}聴いた時間", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.weight(1f))
                Text(formatListenedTime(totalPlayMs), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${periodLabel}聴いた曲数", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.weight(1f))
                Text("${totalCount}曲", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =============================================================================
// セクションカード（縁取りあり）
// =============================================================================
@Composable
private fun ReportSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
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
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(icon, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            content()
        }
    }
}

// =============================================================================
// 聴いた曲セクションのヘッダー（しぼりこみボタン付き）
// =============================================================================
@Composable
private fun ReportSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filterLabel: String,
    isFiltered: Boolean,
    onFilterClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onFilterClick,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isFiltered) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(0.65f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isFiltered) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(0.5f)
            )
        ) {
            Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(filterLabel, fontSize = 12.sp)
        }
    }
}

// =============================================================================
// しぼりこみダイアログ
// =============================================================================
@Composable
private fun TrackFilterDialog(
    options: List<TrackFilter>,
    currentFilter: TrackFilter,
    onSelect: (TrackFilter) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Rounded.FilterList, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("しぼりこみ", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.2f))
                Spacer(Modifier.height(8.dp))

                // 「すべて」
                FilterOptionItem(
                    label    = "すべて",
                    selected = currentFilter is TrackFilter.All,
                    onClick  = { onSelect(TrackFilter.All) }
                )

                if (options.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(options) { option ->
                            FilterOptionItem(
                                label    = option.label(),
                                selected = currentFilter == option,
                                onClick  = { onSelect(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterOptionItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else Color.Transparent
    Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Rounded.Check, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// =============================================================================
// ドロップダウン ショートカット生成
// =============================================================================
private fun buildShortcuts(period: ReportPeriod): List<Pair<String, ReportPeriod>> =
    when (period) {
        is ReportPeriod.Week -> listOf(
            "今週" to ReportPeriod.Week(0),
            "先週" to ReportPeriod.Week(-1),
            "2週前" to ReportPeriod.Week(-2),
            "3週前" to ReportPeriod.Week(-3)
        )
        is ReportPeriod.Month -> listOf(
            "今月" to ReportPeriod.Month(0),
            "先月" to ReportPeriod.Month(-1),
            "2ヶ月前" to ReportPeriod.Month(-2),
            "3ヶ月前" to ReportPeriod.Month(-3)
        )
        is ReportPeriod.Year -> listOf(
            "今年" to ReportPeriod.Year(0),
            "去年" to ReportPeriod.Year(-1),
            "2年前" to ReportPeriod.Year(-2)
        )
    }

// =============================================================================
// しぼりこみ選択肢を period + 実データから生成
// =============================================================================
private fun buildReportFilterOptions(
    period: ReportPeriod,
    history: List<PlayHistoryEntity>
): List<TrackFilter> {
    if (history.isEmpty()) return emptyList()
    return when (period) {
        is ReportPeriod.Week -> {
            // 週モード → 各日（その日にデータがある日のみ）
            (0..6).mapNotNull { dayIdx ->
                val fromMs = reportDayStartMs(period.weekOffset, dayIdx)
                val toMs   = fromMs + 24 * 60 * 60 * 1000L - 1L
                if (history.any { it.playedAtMs in fromMs..toMs }) TrackFilter.ByDay(fromMs)
                else null
            }
        }
        is ReportPeriod.Month -> {
            // 月モード → 各週（その週に月内データがある週のみ）
            val monthStart = period.startMs()
            val monthEnd   = period.endMs()
            val cal = Calendar.getInstance().apply {
                timeInMillis = monthStart
                // 月最初の日を含む週の先頭へ
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                if (timeInMillis > monthStart) add(Calendar.WEEK_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val result = mutableListOf<TrackFilter>()
            while (cal.timeInMillis <= monthEnd) {
                val wStart = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                val wEnd = cal.timeInMillis - 1L
                val rangeStart = maxOf(wStart, monthStart)
                val rangeEnd   = minOf(wEnd, monthEnd)
                if (history.any { it.playedAtMs in rangeStart..rangeEnd }) {
                    result.add(TrackFilter.ByWeek(wStart))
                }
            }
            result
        }
        is ReportPeriod.Year -> {
            // 年モード → 各月（データがある月のみ）
            (0..11).mapNotNull { monthIdx ->
                val cal = Calendar.getInstance().apply {
                    add(Calendar.YEAR, period.yearOffset)
                    set(Calendar.MONTH, monthIdx)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val fromMs = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val toMs = cal.timeInMillis - 1L
                if (history.any { it.playedAtMs in fromMs..toMs }) TrackFilter.ByMonth(fromMs)
                else null
            }
        }
    }
}