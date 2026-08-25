package com.sandolpin.santopimedia35.remember

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
// TrackHistoryDetailScreen
// 「聴いた曲」の全件表示＋カレンダーによる日付しぼりこみ専用画面。
// ReportScreen から「もっと見る」をタップすると遷移してくる。
//
// initialPeriod / initialFilter は ReportScreen 側で選択していた期間・しぼりこみ条件を
// 引き継ぐためのもの。この画面に入った直後は ReportScreen と同じ範囲の曲が
// 表示された状態からスタートし、カレンダーで日付をタップするとさらに絞り込める。
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackHistoryDetailScreen(
    viewModel: HistoryViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    initialPeriod: ReportPeriod,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val favoriteViewModel: FavoriteViewModel = composeViewModel(
        factory = FavoriteViewModel.Factory(context)
    )
    val settings by mediaPlayerViewModel.settings.collectAsState()

    // ===== カレンダー表示中の「月」（ここを基準にカレンダーの1ページが決まる）=====
    // 初期表示は ReportScreen で見ていた期間に合わせる
    var calendarMonth by remember {
        mutableStateOf(calendarMonthFromPeriod(initialPeriod))
    }

    // ===== 選択中の日付（null = 選択なし = 全件表示）=====
    var selectedDayMs by remember { mutableStateOf<Long?>(null) }

    // ===== 全履歴データ =====
    var allHistory by remember { mutableStateOf<List<PlayHistoryEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            allHistory = withContext(Dispatchers.IO) { viewModel.getAllHistory() }
            isLoading = false
        }
    }

    // カレンダーに表示する月の中で「再生記録がある日」の集合（ドット表示用）
    val daysWithDataInMonth: Set<Int> = remember(allHistory, calendarMonth) {
        val cal = Calendar.getInstance().apply { timeInMillis = calendarMonth }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        allHistory.mapNotNullTo(mutableSetOf()) { entity ->
            val c = Calendar.getInstance().apply { timeInMillis = entity.playedAtMs }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month)
                c.get(Calendar.DAY_OF_MONTH)
            else null
        }
    }

    // 選択中の日付に応じた表示リスト
    val displayHistory: List<PlayHistoryEntity> = remember(allHistory, selectedDayMs) {
        val dayMs = selectedDayMs
        val filtered = if (dayMs == null) {
            allHistory
        } else {
            val toMs = dayMs + 24 * 60 * 60 * 1000L - 1L
            allHistory.filter { it.playedAtMs in dayMs..toMs }
        }
        filtered.sortedByDescending { it.playedAtMs }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聴いた曲（すべて）", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
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
            // ===== カレンダー =====
            item {
                TrackCalendarCard(
                    calendarMonth      = calendarMonth,
                    selectedDayMs      = selectedDayMs,
                    daysWithData       = daysWithDataInMonth,
                    onPrevMonth        = {
                        calendarMonth = Calendar.getInstance().apply {
                            timeInMillis = calendarMonth
                            add(Calendar.MONTH, -1)
                        }.timeInMillis
                    },
                    onNextMonth        = {
                        calendarMonth = Calendar.getInstance().apply {
                            timeInMillis = calendarMonth
                            add(Calendar.MONTH, 1)
                        }.timeInMillis
                    },
                    onSelectDay        = { dayMs ->
                        // 同じ日をもう一度タップしたら選択解除（全件表示に戻す）
                        selectedDayMs = if (selectedDayMs == dayMs) null else dayMs
                    },
                    onClearSelection   = { selectedDayMs = null }
                )
            }

            // ===== 選択状態の表示 =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedDayMs != null) {
                            val c = Calendar.getInstance().apply { timeInMillis = selectedDayMs!! }
                            "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日の記録"
                        } else {
                            "すべての記録"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${displayHistory.size}曲",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
            }

            // ===== 曲リスト =====
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (displayHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "この日の履歴はありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                val grouped = displayHistory.groupBy { entity ->
                    val c = Calendar.getInstance().apply { timeInMillis = entity.playedAtMs }
                    "%d/%d/%d".format(c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
                }
                grouped.forEach { (dateKey, entities) ->
                    // 日付選択中（selectedDayMs != null）はヘッダー不要（1日分しかないため）
                    if (selectedDayMs == null) {
                        item(key = "thd_hdr_$dateKey") {
                            val c = Calendar.getInstance().apply {
                                timeInMillis = entities.first().playedAtMs
                            }
                            val today = Calendar.getInstance()
                            val yesterday = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -1)
                            }
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
                    }
                    items(entities, key = { "thd_card_${it.id}" }) { entity ->
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
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// =============================================================================
// カレンダーカード本体
// =============================================================================
@Composable
private fun TrackCalendarCard(
    calendarMonth: Long,
    selectedDayMs: Long?,
    daysWithData: Set<Int>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onClearSelection: () -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = calendarMonth }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val monthLabel = "${year}年${month + 1}月"

    // 月初の曜日オフセット（0=日曜始まり）と、月の日数
    val firstDayCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val startOffset = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 → 0始まりに変換
    val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = Calendar.getInstance()
    val selectedCal = selectedDayMs?.let {
        Calendar.getInstance().apply { timeInMillis = it }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ===== ヘッダー：月送り =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, "前の月", modifier = Modifier.size(20.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(monthLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ChevronRight, "次の月", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 曜日ヘッダー =====
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("日","月","火","水","木","金","土").forEachIndexed { idx, dow ->
                    val color = when (idx) {
                        0 -> Color(0xFFE57373)
                        6 -> Color(0xFF64B5F6)
                        else -> MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(dow, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ===== 日付グリッド =====
            // startOffset個の空白 + daysInMonth個の日付を7列ずつ並べる
            val totalCells = startOffset + daysInMonth
            val rowCount = (totalCells + 6) / 7
            for (row in 0 until rowCount) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayOfMonth = cellIndex - startOffset + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayOfMonth in 1..daysInMonth) {
                                val dayCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year); set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                }
                                val dayMs = dayCal.timeInMillis
                                val isToday = dayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                                        dayCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                val isSelected = selectedCal != null &&
                                        dayCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                        dayCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                                val hasData = daysWithData.contains(dayOfMonth)

                                CalendarDayCell(
                                    day        = dayOfMonth,
                                    isToday    = isToday,
                                    isSelected = isSelected,
                                    hasData    = hasData,
                                    enabled    = hasData,
                                    onClick    = { onSelectDay(dayMs) }
                                )
                            }
                        }
                    }
                }
            }

            // ===== 選択解除ボタン（選択中のみ表示）=====
            if (selectedDayMs != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onClearSelection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("日付の選択を解除", fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== カレンダーの1日分のセル =====
@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasData: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else       -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        !hasData   -> MaterialTheme.colorScheme.onSurface.copy(0.25f)
        else       -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(bgColor, CircleShape)
            .then(
                if (enabled) Modifier.clickable { onClick() } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            // データがある日には小さいドットを表示
            if (hasData && !isSelected) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

// =============================================================================
// ReportPeriod からカレンダー初期表示月（その期間の代表的な月の1日のms）を求める
// =============================================================================
private fun calendarMonthFromPeriod(period: ReportPeriod): Long {
    // period.startMs() はその期間の開始時刻。月初の1日のmsに正規化して返す
    val cal = Calendar.getInstance().apply {
        timeInMillis = period.startMs()
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}