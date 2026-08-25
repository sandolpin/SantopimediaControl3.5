package com.sandolpin.santopimedia35.remember

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sandolpin.santopimedia35.AppIconImage
import com.sandolpin.santopimedia35.HistoryLongTapDialog
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.favorite.FavoriteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    mediaPlayerViewModel: MediaPlayerViewModel,
    onDismiss: () -> Unit,
    onShowReport: () -> Unit = {}
) {
    val context = LocalContext.current
    val favoriteViewModel: FavoriteViewModel = composeViewModel(
        factory = FavoriteViewModel.Factory(context)
    )
    val historyList    by viewModel.historyList.collectAsState()
    val todayTotalMs   by viewModel.todayTotalMs.collectAsState()
    val todayCount     by viewModel.todayTrackCount.collectAsState()
    val filter         by viewModel.filter.collectAsState()
    val excludedApps   by viewModel.excludedApps.collectAsState()
    val recordingStatus by viewModel.recordingStatus.collectAsState()
    val settings        by mediaPlayerViewModel.settings.collectAsState()

    var showExcludeMenu    by remember { mutableStateOf(false) }
    var showExcludeDialog  by remember { mutableStateOf(false) }
    var showResetMenu      by remember { mutableStateOf(false) }
    var resetPeriodLabel   by remember { mutableStateOf("") }
    var resetFromMs        by remember { mutableStateOf<Long?>(null) }
    var showResetConfirm   by remember { mutableStateOf(false) }
    var showDebugCard      by remember { mutableStateOf(false) }
    // 長押しされたカードの詳細ダイアログ表示用
    var longTapEntity      by remember { mutableStateOf<PlayHistoryEntity?>(null) }

    Scaffold(
        topBar = {
            HistoryTopBar(
                filter          = filter,
                onFilterChange  = { viewModel.setFilter(it) },
                onDismiss       = onDismiss,
                onMenuClick     = { showExcludeMenu = true },
                onResetClick    = { showResetMenu = true },
                isDebugVisible  = showDebugCard,
                onToggleDebug   = { showDebugCard = !showDebugCard }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 今日のサマリー
            item {
                TodaySummaryCard(
                    todayTotalMs = todayTotalMs,
                    todayCount   = todayCount,
                    timeDisplayMode = settings.historyTimeDisplayMode
                )
            }

            // デバッグ: 記録状態診断カード（メニューから表示/非表示切り替え可能）
            if (showDebugCard) {
                item {
                    RecordingStatusCard(status = recordingStatus)
                }
            }

            // レポート
            item {
                OutlinedButton(
                    onClick = { onShowReport() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.BarChart, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("レポート", fontSize = 13.sp)
                }
            }

            item {
                Text(
                    "きいた曲",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (historyList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "まだ履歴がありません",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                }
            } else if (filter == HistoryViewModel.Filter.TODAY) {
                // きょうはそのままリスト表示
                items(historyList, key = { it.id }) { entity ->
                    HistoryCard(
                        entity = entity,
                        favoriteViewModel = favoriteViewModel,
                        cardBackgroundStyle = settings.historyCardBackgroundStyle,
                        showDate = settings.historyShowDate,
                        timeRangeMode = settings.historyTimeRangeMode,
                        artBorderEnabled = settings.historyArtBorderEnabled,
                        showPlayTime = settings.historyShowPlayTime,
                        showProgressBar = settings.historyShowProgressBar,
                        artSize = settings.historyArtSize,
                        onLongClick = { longTapEntity = entity }
                    )
                }
            } else {
                // 今週・すべて: 日付ごとにグループ化してセクションヘッダーを挿入
                val grouped = historyList.groupBy { entity ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = entity.playedAtMs }
                    "%d/%d/%d".format(
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH) + 1,
                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                    )
                }
                grouped.forEach { (dateKey, entities) ->
                    item(key = "header_$dateKey") {
                        // 日付ヘッダー
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 日付ラベル（今日・昨日・それ以外）
                            val cal = java.util.Calendar.getInstance().apply {
                                timeInMillis = entities.first().playedAtMs
                            }
                            val today = java.util.Calendar.getInstance()
                            val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                            val label = when {
                                cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                                        cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "きょう"
                                cal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                                        cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> "きのう"
                                else -> "%d月%d日".format(
                                    cal.get(java.util.Calendar.MONTH) + 1,
                                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                                )
                            }
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                            Spacer(Modifier.width(10.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outline.copy(0.25f)
                            )
                        }
                    }
                    items(entities, key = { it.id }) { entity ->
                        HistoryCard(
                            entity = entity,
                            favoriteViewModel = favoriteViewModel,
                            cardBackgroundStyle = settings.historyCardBackgroundStyle,
                            showDate = settings.historyShowDate,
                            timeRangeMode = settings.historyTimeRangeMode,
                            artBorderEnabled = settings.historyArtBorderEnabled,
                            showPlayTime = settings.historyShowPlayTime,
                            showProgressBar = settings.historyShowProgressBar,
                            artSize = settings.historyArtSize,
                            onLongClick = { longTapEntity = entity }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // 除外アプリメニュー
    if (showExcludeMenu) {
        ExcludeMenuDialog(
            excludedApps   = excludedApps,
            onAddRequest   = { showExcludeDialog = true; showExcludeMenu = false },
            onRemove       = { viewModel.removeExcludedApp(it) },
            onDismiss      = { showExcludeMenu = false }
        )
    }

    // 除外アプリ追加ダイアログ
    if (showExcludeDialog) {
        AddExcludeDialog(
            onConfirm = { pkg, label ->
                viewModel.addExcludedApp(pkg, label)
                showExcludeDialog = false
            },
            onDismiss = { showExcludeDialog = false }
        )
    }

    // 履歴リセット: 期間選択シート
    if (showResetMenu) {
        ResetPeriodSheet(
            onSelect = { label, fromMs ->
                resetPeriodLabel = label
                resetFromMs      = fromMs
                showResetMenu    = false
                showResetConfirm = true
            },
            onDismiss = { showResetMenu = false }
        )
    }

    // 履歴リセット: 確認ダイアログ
    if (showResetConfirm) {
        ResetConfirmDialog(
            periodLabel = resetPeriodLabel,
            onConfirm = {
                viewModel.resetHistory(resetFromMs)
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false }
        )
    }

    // きいた曲カード長押し: 詳細ダイアログ（再生したアプリ・メタデータ・再生時間）
    longTapEntity?.let { entity ->
        HistoryLongTapDialog(
            entity = entity,
            viewModel = mediaPlayerViewModel,
            onDismiss = { longTapEntity = null }
        )
    }
}

// ===== トップバー =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(
    filter: HistoryViewModel.Filter,
    onFilterChange: (HistoryViewModel.Filter) -> Unit,
    onDismiss: () -> Unit,
    onMenuClick: () -> Unit,
    onResetClick: () -> Unit = {},
    isDebugVisible: Boolean = false,
    onToggleDebug: () -> Unit = {}
) {
    val labels = mapOf(
        HistoryViewModel.Filter.TODAY to "きょう",
        HistoryViewModel.Filter.WEEK  to "今週",
        HistoryViewModel.Filter.ALL   to "すべて"
    )
    TopAppBar(
        title = { Text("りれき", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.ArrowBack, "閉じる")
            }
        },
        actions = {
            // フィルター
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(labels[filter] ?: "きょう", fontSize = 13.sp)
                    Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    HistoryViewModel.Filter.values().forEach { f ->
                        DropdownMenuItem(
                            text = { Text(labels[f] ?: "") },
                            onClick = { onFilterChange(f); expanded = false }
                        )
                    }
                }
            }
            // ... メニュー（除外設定 + 履歴リセット）
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, "メニュー")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isDebugVisible) "デバッグ情報を非表示" else "デバッグ情報を表示") },
                        leadingIcon = {
                            Icon(
                                if (isDebugVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                null, modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = { menuExpanded = false; onToggleDebug() }
                    )
                    DropdownMenuItem(
                        text = { Text("除外アプリ設定") },
                        leadingIcon = { Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onMenuClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("履歴をリセット", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DeleteSweep, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuExpanded = false; onResetClick() }
                    )
                }
            }
        }
    )
}

// ===== デバッグ: 記録状態診断カード =====
@Composable
fun RecordingStatusCard(status: HistoryViewModel.RecordingStatus) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 記録中判定: 追跡中 & 再生中 & タイマー動作中
    val isRecording = status.isTracking && status.isPlaying && status.timerRunning
    val statusColor = when {
        isRecording                          -> Color(0xFF4CAF50)  // 緑: 記録中
        status.isTracking && !status.isPlaying -> Color(0xFFFF9800) // 橙: 停止中（曲は追跡中）
        else                                 -> Color(0xFFF44336)  // 赤: 未追跡
    }
    val statusText = when {
        isRecording                            -> "● 記録中"
        status.isTracking && !status.isPlaying -> "■ 一時停止中"
        else                                   -> "✕ 未追跡"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, statusColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "デバッグ: 記録状態",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(8.dp))

            DebugRow("追跡中の曲",
                if (status.currentTitle.isEmpty()) "なし" else status.currentTitle)
            DebugRow("アプリ",
                if (status.currentApp.isEmpty()) "なし" else status.currentApp)
            DebugRow("再生状態",    if (status.isPlaying) "再生中" else "停止中")
            DebugRow("タイマー",    if (status.timerRunning) "動作中" else "停止")
            DebugRow("積算時間",    "${status.accumulatedSec}秒")
            DebugRow("最終通知",
                if (status.lastUpdatedMs == 0L) "なし"
                else {
                    val sec = (System.currentTimeMillis() - status.lastUpdatedMs) / 1000L
                    "${sec}秒前"
                }
            )
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ===== 今日のサマリーカード =====
@Composable
private fun TodaySummaryCard(todayTotalMs: Long, todayCount: Int, timeDisplayMode: String = "auto") {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "きょう聞いた時間",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatListenedTimeMode(todayTotalMs, timeDisplayMode),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "きょう聞いた曲数",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${todayCount}曲",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ===== 履歴カード =====
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    entity: PlayHistoryEntity,
    favoriteViewModel: com.sandolpin.santopimedia35.favorite.FavoriteViewModel? = null,
    cardBackgroundStyle: String = "blur", // "blur"=アルバムアートのぼかし / "animated"=複数色ミックス / "color_fill"=アルバムアート色で塗りつぶし
    showDate: Boolean = true,             // 日時表示に日付を含めるか
    timeRangeMode: String = "start",      // "start" / "end" / "both"
    artBorderEnabled: Boolean = false,    // アルバムアートを白で縁取りするか
    showPlayTime: Boolean = false,        // 「〇:〇〇聴きました」を表示するか
    showProgressBar: Boolean = true,      // 進捗バーを表示するか
    artSize: String = "medium",           // "small" / "medium" / "large" / "xlarge"
    onLongClick: () -> Unit = {}
) {
    // isSystemInDarkTheme() ではなく MaterialTheme で判定する
    // → アプリ設定で手動ライト/ダークにしたときも正しく追従する
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // カード背景色: ダーク=白8%、ライト=黒4%（明示的に設定してblurアートが黒くならないようにする）
    val cardBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f)
    // オーバーレイ色（ぼかしアートの上に重ねる）
    val overlayColor = if (isDark) Color.Black else Color.White

    // ===== アルバムアートの表示サイズ =====
    val artSizeDp = when (artSize) {
        "small"  -> 48.dp
        "large"  -> 88.dp
        "xlarge" -> 112.dp
        else     -> 64.dp // "medium"（従来のサイズ）
    }

    // ===== 複数色ミックス・色塗りつぶし の対応可否 =====
    // どちらもAGSL(RuntimeShader)は使わない静的な塗りつぶしのみのため、API制限は無い。
    val useColorMix = cardBackgroundStyle == "animated" && entity.albumArtUri != null
    val useColorFill = cardBackgroundStyle == "color_fill" && entity.albumArtUri != null

    // ===== 複数色ミックス・色塗りつぶし共通: アクセントカラー抽出 =====
    // カードごとに曲のアルバムアートが異なるため、曲ごとに個別へPaletteで色を取り出す。
    // ぼかし表示（デフォルト）の場合はこの処理自体を行わない（余分な画像デコードを避けるため）。
    // 色ミックスは2色（メイン・サブ）を、色塗りつぶしは1色（メインのみ）を使う。
    val context = LocalContext.current
    var cardAccentColor by remember(entity.albumArtUri) {
        mutableStateOf<Color?>(null)
    }
    var cardAccentColor2 by remember(entity.albumArtUri) {
        mutableStateOf<Color?>(null)
    }
    val needsColorExtraction = useColorMix || useColorFill
    if (needsColorExtraction) {
        LaunchedEffect(entity.albumArtUri) {
            val uri = entity.albumArtUri ?: return@LaunchedEffect
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .allowHardware(false) // Paletteにはソフトウェアビットマップが必要
                        .build()
                    val result = loader.execute(request)
                    (result as? SuccessResult)?.drawable
                        ?.let { it as? android.graphics.drawable.BitmapDrawable }
                        ?.bitmap
                } catch (e: Exception) {
                    null
                }
            } ?: return@LaunchedEffect

            Palette.from(bitmap).generate { palette ->
                val primary = palette?.vibrantSwatch
                    ?: palette?.darkVibrantSwatch
                    ?: palette?.mutedSwatch
                    ?: palette?.dominantSwatch
                if (primary != null) cardAccentColor = Color(primary.rgb)
                // 色ミックス用のサブカラー（メインとは違うSwatchを優先して選ぶ）
                val secondary = palette?.mutedSwatch
                    ?: palette?.darkVibrantSwatch
                    ?: palette?.lightVibrantSwatch
                    ?: palette?.dominantSwatch
                if (secondary != null) cardAccentColor2 = Color(secondary.rgb)
            }
        }
    }

    // ===== 背景描画・文字色判定の両方で使う色をここで一度だけ解決する =====
    // （色が取得できるまでの一瞬はテーマのprimaryカラーで代用）
    val mixColorA = cardAccentColor ?: MaterialTheme.colorScheme.primary
    val mixColorB = cardAccentColor2 ?: mixColorA
    val fillColor = cardAccentColor ?: MaterialTheme.colorScheme.primary

    // ===== 背景の明度に応じた自動文字色切り替え =====
    // 「複数色ミックス」「アルバムアート色で塗りつぶし」は背景色が曲ごとに大きく変わるため、
    // テーマ固定のonSurfaceのままだと明るい背景に白文字が乗るなど可読性事故が起こり得る。
    // 実際に描画される背景色の明度を計算し、しきい値0.5を境に白/黒へ自動で切り替える。
    // 「アルバムアートのぼかし」「デフォルト」はオーバーレイ自体がテーマの明暗(isDark)に
    // 追従するよう設計済みのため、従来通りテーマのonSurfaceに任せて問題ない。
    val cardTextColor: Color = when {
        useColorMix  -> if (((mixColorA.luminance() + mixColorB.luminance()) / 2f) > 0.5f)
            Color.Black else Color.White
        useColorFill -> if (fillColor.luminance() > 0.5f) Color.Black else Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
    ) {
        // 1. カード背景（明示的に設定 → ぼかしアートが透けて黒くなるのを防ぐ）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cardBg)
        )

        // 2. 背景（設定に応じて「アルバムアートのぼかし」「複数色ミックス」「色で塗りつぶし」）
        if (useColorMix) {
            // アルバムアートから抽出した2色を静的にグラデーションで混ぜるだけ（アニメーションなし）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(mixColorA, mixColorB)))
            )
            // 2色の平均明度に応じて黒/白の薄いオーバーレイを重ね、文字の可読性を確保する
            val mixLuminance = (mixColorA.luminance() + mixColorB.luminance()) / 2f
            val overlayForMix = if (mixLuminance > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayForMix.copy(alpha = 0.15f))
            )
        } else if (useColorFill) {
            // アルバムアートから抽出した色でグラデーション塗りつぶし
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(fillColor.copy(alpha = 0.85f), fillColor.copy(alpha = 0.55f))
                        )
                    )
            )
            // 塗りつぶし色の明度に応じて黒/白の薄いオーバーレイを重ね、文字の可読性を確保する
            val overlayForFill = if (fillColor.luminance() > 0.5f) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayForFill.copy(alpha = 0.15f))
            )
        } else if (entity.albumArtUri != null) {
            AsyncImage(
                model = entity.albumArtUri, contentDescription = null,
                modifier = Modifier.matchParentSize().blur(24.dp),
                contentScale = ContentScale.Crop
            )
            // アートの上に半透明オーバーレイ（アートが透けて見える程度）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                overlayColor.copy(alpha = 0.78f),
                                overlayColor.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
        }

        // 2.5 お気に入り☆ボタン（右上）
        if (favoriteViewModel != null) {
            val isFavorite = favoriteViewModel.isFavorite(
                entity.title, entity.artist, entity.packageName
            )
            IconButton(
                onClick = {
                    favoriteViewModel.toggleFavorite(
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        albumArtUri = entity.albumArtUri,
                        appLabel = entity.appLabel,
                        packageName = entity.packageName,
                        mediaId = entity.mediaId,
                        durationMs = entity.durationMs
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (isFavorite) "お気に入り解除" else "お気に入りに追加",
                    tint = if (isFavorite) Color(0xFFFFC107)
                    else cardTextColor.copy(0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 3. コンテンツ
        Column(modifier = Modifier.padding(12.dp)) {
            // 日時 + アプリ名
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatCardDateTime(entity.playedAtMs, entity.playTimeMs, showDate, timeRangeMode),
                    fontSize = 11.sp,
                    color = cardTextColor.copy(0.55f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = cardTextColor.copy(0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.MusicNote, null,
                            modifier = Modifier.size(10.dp),
                            tint = cardTextColor)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            entity.appLabel.ifEmpty { entity.packageName },
                            fontSize = 10.sp,
                            color = cardTextColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entity.albumArtUri != null) {
                    AsyncImage(
                        model = entity.albumArtUri, contentDescription = null,
                        modifier = Modifier
                            .size(artSizeDp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (artBorderEnabled)
                                    Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(artSizeDp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (artBorderEnabled)
                                    Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, null,
                            tint = cardTextColor.copy(0.4f))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entity.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = cardTextColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entity.artist, fontSize = 12.sp,
                        color = cardTextColor.copy(0.65f), maxLines = 1)
                    if (entity.album.isNotEmpty()) {
                        Text(entity.album, fontSize = 11.sp,
                            color = cardTextColor.copy(0.45f), maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // プログレスバー（設定でON/OFF可能）
            if (showProgressBar) {
                val progress = if (entity.durationMs > 0)
                    (entity.listenedMs.toFloat() / entity.durationMs).coerceIn(0f, 1f)
                else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = cardTextColor,
                    trackColor = cardTextColor.copy(0.15f)
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (entity.listenedAll) "ぜんぶ聴きました"
                        else "${formatDuration(entity.listenedMs)} まで聴きました",
                        fontSize = 11.sp,
                        color = cardTextColor.copy(0.55f)
                    )
                    Text(
                        formatDuration(entity.durationMs),
                        fontSize = 11.sp,
                        color = cardTextColor.copy(0.45f)
                    )
                }
            }

            // 実際に聴いていた時間（開始〜停止までの playTimeMs）を「〇:〇〇聴きました」で表示
            if (showPlayTime) {
                if (showProgressBar) Spacer(Modifier.height(4.dp))
                Text(
                    "${formatDuration(entity.playTimeMs)}聴きました",
                    fontSize = 11.sp,
                    color = cardTextColor.copy(0.55f)
                )
            }
        }
    }
}

// ===== 除外アプリ管理ダイアログ =====
@Composable
private fun ExcludeMenuDialog(
    excludedApps: List<ExcludedApp>,
    onAddRequest: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("アプリを除外", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                Text("除外したアプリの再生は履歴に記録されません",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Spacer(Modifier.height(12.dp))

                if (excludedApps.isEmpty()) {
                    Text("除外中のアプリはありません",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    excludedApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(packageName = app.packageName, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.appLabel.ifEmpty { app.packageName },
                                modifier = Modifier.weight(1f), fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(
                                onClick = { onRemove(app.packageName) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, "削除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAddRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("アプリを追加")
                }
            }
        }
    }
}

// ===== 除外アプリ追加ダイアログ =====
@Composable
private fun AddExcludeDialog(
    onConfirm: (packageName: String, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // インストール済みアプリ一覧を取得
    // ★ 「一部のアプリしか出ない」という不具合の主因は2つある。
    //   1) コード側フィルタ: 以前はFLAG_SYSTEM等でアプリ種別を絞り込んでいたが、
    //      「除外候補として全部出てほしい」という要望に合わせ、このダイアログ自体を
    //      呼び出したアプリ（自分自身）以外は種別を問わず全て候補にする。
    //   2) Android 11(API30)以降のパッケージ可視性の制限: AndroidManifest.xmlに
    //      <queries>宣言が無いと、getInstalledApplications()は自分自身や一部の
    //      システムアプリ以外の一般アプリ（Spotify・LINE等）をほとんど返さなくなる。
    //      これはコード側の絞り込みをどれだけ緩めても解決できない、Manifest側の
    //      対応が必須の制限。<queries>にLAUNCHERインテントのフィルタを追加する
    //      必要がある（詳細は回答メッセージ側で案内）。
    val apps = remember {
        context.packageManager
            .getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                // 一部のシステムコンポーネントはラベル取得自体が例外になることがあるため、
                // その場合はリストから静かに除外する（一覧全体が壊れないようにするため）
                try {
                    val label = context.packageManager.getApplicationLabel(info).toString()
                    Pair(info.packageName, label)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.second }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.second.contains(searchQuery, ignoreCase = true) ||
                    it.first.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("除外するアプリを選択", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.weight(1f))
                    Text("${apps.size}件", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("アプリ名で検索") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, "クリア", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "該当するアプリがありません",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onConfirm(pkg, label) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(packageName = pkg, size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        pkg,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// ===== 履歴リセット: 期間選択シート =====
@Composable
private fun ResetPeriodSheet(
    onSelect: (label: String, fromMs: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options = listOf(
        Triple("きょう",   "きょう",   periodStartMsLocal(0)),
        Triple("今週",     "今週",     periodStartMsLocal(1)),
        Triple("今月",     "今月",     periodStartMsLocal(2)),
        Triple("今年",     "今年",     periodStartMsLocal(3)),
        Triple("ぜんぶけす", "ぜんぶ", null as Long?)
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "削除する期間を選んでください",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                options.forEach { (display, label, fromMs) ->
                    val isAll = fromMs == null
                    TextButton(
                        onClick = { onSelect(label, fromMs) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isAll) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isAll) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(display, fontSize = 15.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("キャンセル", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
        }
    }
}

// ===== 履歴リセット: 確認ダイアログ =====
@Composable
private fun ResetConfirmDialog(
    periodLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text("「${periodLabel}」の履歴を削除", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "完全に削除します。復元はできませんがよろしいですか？",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("削除する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

// 期間選択用のstartMs計算（ResetPeriodSheet内で使用）
private fun periodStartMsLocal(type: Int): Long {
    val cal = java.util.Calendar.getInstance()
    return when (type) {
        0 -> { // きょう
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        1 -> { // 今週
            cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        2 -> { // 今月
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        3 -> { // 今年
            cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        else -> 0L
    }
}