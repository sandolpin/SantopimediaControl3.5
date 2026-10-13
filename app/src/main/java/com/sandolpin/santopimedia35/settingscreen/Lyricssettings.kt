package com.sandolpin.santopimedia35.settingscreen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定: 歌詞・歌詞フォント・ローカル歌詞ファイルページ

// ===== 歌詞 =====
@Composable
internal fun LyricsPage(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onNavigate: (SettingPage) -> Unit = {}
) {
    val context = LocalContext.current

    // プレビュー用の仮データ
    val previewItems = remember {
        listOf(
            com.sandolpin.santopimedia35.LyricItem.Line(0L,    "さあ行こう どこまでも"),
            com.sandolpin.santopimedia35.LyricItem.Line(3000L, "風を切って走り出せ"),
            com.sandolpin.santopimedia35.LyricItem.Line(6000L, "夢の続きを探しに"),
            com.sandolpin.santopimedia35.LyricItem.Line(9000L, "光の向こうへ"),
        )
    }
    // プレビュー再生位置（3秒ごとに行が進む）
    var previewPositionMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            previewPositionMs = (previewPositionMs + 3000L) % 12000L
        }
    }

    SubPageScaffold("歌詞", onBack) {

        // ===== プレビュー（SyncedLyricsViewを50%縮小表示）=====
        SettingSection("プレビュー") {
            val accentColor = MaterialTheme.colorScheme.primary
            val isDark = isSystemInDarkTheme()
            val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(0.35f)

            // 縦高さ200dpの枠に、SyncedLyricsViewを50%縮小して収める
            // scale(0.5f)だけでは描画領域が元サイズのままなので
            // layout modifierで高さを半分に畳む
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(0.5f)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(
                                    maxWidth  = constraints.maxWidth * 2,
                                    minWidth  = constraints.maxWidth * 2,
                                    maxHeight = constraints.maxHeight * 2,
                                    minHeight = constraints.maxHeight * 2,
                                )
                            )
                            layout(placeable.width / 2, placeable.height / 2) {
                                placeable.placeRelative(
                                    x = -(placeable.width / 4),
                                    y = -(placeable.height / 4)
                                )
                            }
                        }
                ) {
                    SyncedLyricsView(
                        items              = previewItems,
                        currentPositionMs  = previewPositionMs,
                        accentColor        = accentColor,
                        inactiveColor      = inactiveColor,
                        futureColor        = inactiveColor,
                        fontSize           = settings.lyricsFontSize,
                        fontWeight         = settings.lyricsFontWeight,
                        lineHeightMultiplier = settings.lyricsLineHeight,
                        lineSpacingDp      = settings.lyricsLineSpacing,
                        inactiveLight      = settings.lyricsInactiveLight,
                        interludeThresholdMs = settings.lyricsInterludeThreshold * 1000L,
                        multiLineThresholdMs = (settings.lyricsMultiLineThreshold * 1000f).toLong(),
                        scrollSpeedMs      = settings.lyricsScrollSpeedMs,
                        scrollEasing       = settings.lyricsScrollEasing,
                        offsetSync         = settings.lyricsOffsetSync,
                        offsetDelayMs      = settings.lyricsOffsetDelayMs,
                        offsetStaggerMs    = settings.lyricsOffsetStaggerMs,
                        inactiveScale      = settings.lyricsInactiveScale,
                        inactiveBlur       = settings.lyricsInactiveBlur,
                        inactiveBlurRadius = settings.lyricsInactiveBlurRadius,
                        scrollAnimType     = settings.lyricsScrollAnimType,
                        overshootDistance  = settings.lyricsOvershootDistance,
                        fontPath           = settings.lyricsFontPath,
                        forceInactiveOnEnd = settings.lyricsForceInactiveOnEnd,
                        disableMultiShow   = settings.sdlrcDisableMultiShow,
                        disableRight       = settings.sdlrcDisableRight,
                        onLineClick        = { },
                        viewModel          = viewModel,
                        useOffsetAnim      = true
                    )
                }
                // タッチイベントを全消費してユーザーの手動スクロールを無効化
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }

        // ===== 背景・表示 =====
        SettingSection("表示") {
            // ★ Android 12未満は「ぼかし」「動的アニメーション」が実質機能しないため、
            //   ドロップダウンの選択肢自体から除外する（そもそも選べないようにする）。
            val lyricsBgOptions = remember(settings.lyricsBg) {
                val all = listOf(
                    "normal" to "ノーマル", "player" to "プレーヤーと同じ",
                    "gradient" to "グラデーション", "blur" to "ぼかし", "animated" to "動的アニメーション"
                )
                if (isBlurRenderSupported()) all
                else all.filterNot { it.first == "blur" || it.first == "animated" }
            }
            SettingDropdownRow("背景スタイル", settings.lyricsBg, lyricsBgOptions) {
                viewModel.updateSetting(SettingsKeys.LYRICS_BG, it)
            }
            if (!isBlurRenderSupported()) {
                Text(
                    "Android 12未満のため「ぼかし」「動的アニメーション」は選択できません",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            if (settings.lyricsBg == "animated" ||
                (settings.lyricsBg == "player" && settings.backgroundStyle == "animated")) {
                SettingRow(label = "背景の色ルール") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "accent"          to "アクセントカラーから",
                            "album_art"       to "アルバムアートから"
                        ).forEach { (v, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = settings.animatedColorSource == v,
                                    onClick = { viewModel.updateSetting(SettingsKeys.ANIMATED_COLOR_SOURCE, v) }
                                )
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Text(
                    "外観設定の「動的アニメーション」と共通の設定です",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }

        // ===== フォント =====
        SettingSection("フォント") {
            SettingSliderRow("フォントサイズ（${settings.lyricsFontSize}sp）",
                settings.lyricsFontSize.toFloat(), 20f..48f, 27) {
                viewModel.updateSetting(SettingsKeys.LYRICS_FONT_SIZE, it.toInt())
            }
            SettingSliderRow("テキストの太さ（${settings.lyricsFontWeight}）",
                settings.lyricsFontWeight.toFloat(), 100f..900f, 7) {
                viewModel.updateSetting(SettingsKeys.LYRICS_FONT_WEIGHT, (it / 100).toInt() * 100)
            }
            SettingSliderRow("行間倍率（${"%.1f".format(settings.lyricsLineHeight)}倍）",
                settings.lyricsLineHeight, 1.0f..2.0f, 9) {
                viewModel.updateSetting(SettingsKeys.LYRICS_LINE_HEIGHT, it)
            }
            SettingSliderRow("歌詞の間隔（${settings.lyricsLineSpacing.toInt()}dp）",
                settings.lyricsLineSpacing, 0f..32f, 15) {
                viewModel.updateSetting(SettingsKeys.LYRICS_LINE_SPACING, it)
            }
        }

        // ===== 非アクティブ表示 =====
        SettingSection("非アクティブ歌詞") {
            SettingSliderRow("大きさ（${(settings.lyricsInactiveScale * 100).toInt()}%）",
                settings.lyricsInactiveScale, 0.97f..1.00f, 2) {
                viewModel.updateSetting(SettingsKeys.LYRICS_INACTIVE_SCALE, it)
            }
            // ★ ぼかし(Modifier.blur)はAndroid 12未満では効果が無いため、
            //   スイッチ自体を無効化して選べないようにする。
            val blurSupported = isBlurRenderSupported()
            SettingSwitchRow("ぼかす",
                checked = settings.lyricsInactiveBlur && blurSupported,
                enabled = blurSupported,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.LYRICS_INACTIVE_BLUR, it) })
            if (!blurSupported) {
                Text("Android 12未満の端末では使用できません", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            } else if (settings.lyricsInactiveBlur) {
                val blurLabel = if (settings.lyricsInactiveBlurRadius == 0f)
                    "ぼかし強度（自動）" else "ぼかし強度（${"%.1f".format(settings.lyricsInactiveBlurRadius)}dp）"
                SettingSliderRow(blurLabel, settings.lyricsInactiveBlurRadius, 0f..4f, 7) {
                    viewModel.updateSetting(SettingsKeys.LYRICS_INACTIVE_BLUR_RADIUS, (it * 2).toInt() / 2f)
                }
                Text("0 = 行距離が遠いほど強くぼかします", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }

        // ===== スクロール =====
        SettingSection("スクロール") {
            SettingSliderRow("スクロールの速さ（${settings.lyricsScrollSpeedMs}ms）",
                settings.lyricsScrollSpeedMs.toFloat(), 100f..1000f, 17) {
                viewModel.updateSetting(SettingsKeys.LYRICS_SCROLL_SPEED_MS, (it / 50).toInt() * 50)
            }
            SettingDropdownRow("スクロールアニメーション", settings.lyricsScrollAnimType,
                listOf("decelerate_circ" to "減速（circ）", "decelerate_quad" to "減速（quad）", "decelerate_quint" to "減速（quint）", "decelerate_expo" to "減速（expo）", "overshoot" to "オーバーシュート")) {
                viewModel.updateSetting(SettingsKeys.LYRICS_SCROLL_ANIM_TYPE, it)
            }
            if (settings.lyricsScrollAnimType == "overshoot") {
                SettingRow(label = "オーバーシュートの距離") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 15).forEach { v ->
                            FilterChip(
                                selected = settings.lyricsOvershootDistance == v,
                                onClick = { viewModel.updateSetting(SettingsKeys.LYRICS_OVERSHOOT_DISTANCE, v) },
                                label = { Text("$v%", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // ===== 動作 =====
        SettingSection("動作") {
            SettingSwitchRow("歌詞終了時間で強制的に非アクティブにする",
                checked = settings.lyricsForceInactiveOnEnd,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.LYRICS_FORCE_INACTIVE_ON_END, it) })
            Text("sdlrcで終了時間になった場合、強制的に非アクティブにします",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            SettingSwitchRow("ネットワークを使用して歌詞を検索",
                checked = settings.lyricsUseNetwork,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.LYRICS_USE_NETWORK, it) })
            SettingSwitchRow("見つからない時タイトルのみで再検索",
                checked = settings.lyricsAutoRetry,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.LYRICS_AUTO_RETRY, it) })
        }

        // ===== sdlrc設定 =====
        SettingSection("sdlrc設定") {
            SettingSwitchRow("複数行の同時表示を許可しない",
                checked = settings.sdlrcDisableMultiShow,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.SDLRC_DISABLE_MULTI_SHOW, it) })
            SettingSwitchRow("右寄せ表示を許可しない",
                checked = settings.sdlrcDisableRight,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.SDLRC_DISABLE_RIGHT, it) })
        }

        // ===== キャッシュ =====
        SettingSection("キャッシュ") {
            var cacheCount by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                LyricsCache.init(context)
                cacheCount = LyricsCache.count()
            }
            var showClearDialog by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("歌詞キャッシュ", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${cacheCount}曲分 保存済み", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                OutlinedButton(onClick = { showClearDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("キャッシュを削除", fontSize = 12.sp)
                }
            }
            if (showClearDialog) {
                AlertDialog(onDismissRequest = { showClearDialog = false },
                    title = { Text("キャッシュを削除") },
                    text = { Text("${cacheCount}曲分の歌詞キャッシュを削除しますか？") },
                    confirmButton = {
                        TextButton(onClick = { LyricsCache.deleteAll(); cacheCount = 0; showClearDialog = false }) {
                            Text("削除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) { Text("キャンセル") }
                    })
            }
        }

        // フォント・ファイル設定へのリンク
        BorderedShadowBox(modifier = Modifier.fillMaxWidth()) {
            // LyricsFontPage へのナビは onBack ではなく SettingTopPage 経由なので
            // 親の onNavigate が届かないため、ここでは直接リンクRowを並べる
            // → SubPageScaffold の外にNavigation対応を追加するか、
            //   LyricsPage内にサブセクションとして内包する
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { onNavigate(SettingPage.LYRICS_FONT) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FontDownload, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("歌詞フォント", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
                Row(modifier = Modifier.fillMaxWidth().clickable { onNavigate(SettingPage.LYRICS_FILE) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("ローカル歌詞ファイル", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ===== 歌詞フォント =====
@Composable
internal fun LyricsFontPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var fontPath by remember { mutableStateOf(settings.lyricsFontPath) }
    val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val s = uri.toString(); fontPath = s; viewModel.updateSetting(SettingsKeys.LYRICS_FONT_PATH, s)
        }
    }
    SubPageScaffold("歌詞フォント", onBack) {
        SettingSection("フォントファイル") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "*/*")) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TextFields, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("フォントファイルを読み込み", fontSize = 12.sp)
                }
                if (fontPath.isNotEmpty()) {
                    TextButton(onClick = { fontPath = ""; viewModel.updateSetting(SettingsKeys.LYRICS_FONT_PATH, "") },
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("クリア", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (fontPath.isNotEmpty()) {
                val name = android.net.Uri.decode(fontPath.substringAfterLast("%2F").substringAfterLast("/")).ifEmpty { "選択済み" }
                Text("🔤 $name", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(0.8f),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            } else {
                Text("未設定の場合はシステムフォントを使用します", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Text("対応形式: .ttf / .otf", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

// ===== ローカル歌詞ファイル =====
@Composable
internal fun LyricsFilePage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var folderPath by remember { mutableStateOf(settings.lyricsFolder) }
    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val s = uri.toString(); folderPath = s; viewModel.updateSetting(SettingsKeys.LYRICS_FOLDER, s)
        }
    }
    SubPageScaffold("ローカル歌詞ファイル", onBack) {
        SettingSection("フォルダ") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { folderPicker.launch(null) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("フォルダを選択", fontSize = 12.sp)
                }
                if (folderPath.isNotEmpty()) {
                    TextButton(onClick = { folderPath = ""; viewModel.updateSetting(SettingsKeys.LYRICS_FOLDER, "") },
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("クリア", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (folderPath.isNotEmpty()) {
                val display = if (folderPath.startsWith("content://"))
                    android.net.Uri.decode(folderPath.substringAfterLast("%3A").substringAfterLast("/")).ifEmpty { "選択済み" }
                else folderPath
                Text("📁 $display", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(0.8f),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text("または絶対パスで直接入力:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            var manualPath by remember { mutableStateOf(if (!folderPath.startsWith("content://")) folderPath else "") }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = manualPath, onValueChange = { manualPath = it },
                    placeholder = { Text("/storage/emulated/0/Lyrics", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
                IconButton(onClick = {
                    if (manualPath.isNotEmpty()) { folderPath = manualPath; viewModel.updateSetting(SettingsKeys.LYRICS_FOLDER, manualPath) }
                }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Check, "適用", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("対応形式: .sdlrc / .lrc / .ttml", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}