package com.sandolpin.santopimedia35

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.remember.HistoryCard
import com.sandolpin.santopimedia35.remember.PlayHistoryEntity
import androidx.compose.ui.platform.LocalContext
import com.sandolpin.santopimedia35.database.SettingsKeys

// ===== 設定ページ種別 =====
private enum class SettingPage {
    TOP,            // カテゴリ一覧
    APPEARANCE,     // 外観
    TEXT,           // テキスト
    PLAYER,         // 再生画面
    LYRICS,         // 歌詞
    LYRICS_FONT,    // 歌詞フォント
    LYRICS_FILE,    // ローカル歌詞ファイル
    APP_SELECT,     // アプリ切り替え
    BACKGROUND,     // バックグラウンド処理
    ACTION_MENU,    // アクションメニュー項目の表示/非表示・並び替え
    HISTORY_DISPLAY, // 履歴画面の表示設定
}

// ページの階層深さ（アニメーション方向判定用）
private fun SettingPage.depth() = when (this) {
    SettingPage.TOP          -> 0
    SettingPage.APPEARANCE   -> 1
    SettingPage.TEXT         -> 1
    SettingPage.PLAYER       -> 1
    SettingPage.LYRICS       -> 1
    SettingPage.APP_SELECT   -> 1
    SettingPage.BACKGROUND   -> 1
    SettingPage.ACTION_MENU  -> 1
    SettingPage.HISTORY_DISPLAY -> 1
    SettingPage.LYRICS_FONT  -> 2
    SettingPage.LYRICS_FILE  -> 2
}

@Composable
fun SettingScreen(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    // 歌詞画面の三点メニュー「歌詞設定を開く」から呼ばれたとき、
    // TOPを経由せず直接「歌詞」ページを開くためのフラグ。
    // 一度開いたら onConsumeOpenLyricsPage() で外側の状態をfalseに戻してもらう
    // （そうしないとタブを切り替えるたびに歌詞ページへ強制的に飛んでしまうため）。
    openLyricsPage: Boolean = false,
    onConsumeOpenLyricsPage: () -> Unit = {}
) {
    val mediaState by viewModel.mediaState.collectAsState()
    var currentPage by remember { mutableStateOf(SettingPage.TOP) }

    LaunchedEffect(openLyricsPage) {
        if (openLyricsPage) {
            currentPage = SettingPage.LYRICS
            onConsumeOpenLyricsPage()
        }
    }

    // システムの「戻る」ボタンで正しいページに戻るためのBackHandler
    // TOP以外のページにいるとき、深さに応じて適切な親ページに戻る
    BackHandler(enabled = currentPage != SettingPage.TOP) {
        currentPage = when (currentPage) {
            SettingPage.LYRICS_FONT,
            SettingPage.LYRICS_FILE -> SettingPage.LYRICS
            else                    -> SettingPage.TOP
        }
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            // 深くなる（進む）なら右からスライドイン、浅くなる（戻る）なら左からスライドイン
            if (targetState.depth() >= initialState.depth())
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            else
                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        },
        label = "settingNav"
    ) { page ->
        val navigate: (SettingPage) -> Unit = { dest -> currentPage = dest }
        val goBack: (SettingPage) -> Unit   = { target -> currentPage = target }
        when (page) {
            SettingPage.TOP -> SettingTopPage(viewModel = viewModel, onNavigate = { navigate(it) })
            SettingPage.APPEARANCE -> AppearancePage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.TEXT -> TextPage(
                viewModel = viewModel, settings = settings,
                mediaState = mediaState,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.PLAYER -> PlayerPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.LYRICS -> LyricsPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) },
                onNavigate = { navigate(it) }
            )
            SettingPage.LYRICS_FONT -> LyricsFontPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.LYRICS) }
            )
            SettingPage.LYRICS_FILE -> LyricsFilePage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.LYRICS) }
            )
            SettingPage.APP_SELECT -> AppSelectPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.BACKGROUND -> BackgroundPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.ACTION_MENU -> ActionMenuSettingPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
            SettingPage.HISTORY_DISPLAY -> HistoryDisplayPage(
                viewModel = viewModel, settings = settings,
                onBack = { goBack(SettingPage.TOP) }
            )
        }
    }
}

// TOPページのカテゴリ1項目分のデータ（アイコン・タイトルに加えて説明サブテキストを持つ）
private data class SettingCategoryItem(
    val page: SettingPage,
    val icon: ImageVector,
    val label: String,
    val description: String
)

// ===== TOP: カテゴリ一覧 =====
@Composable
private fun SettingTopPage(viewModel: MediaPlayerViewModel, onNavigate: (SettingPage) -> Unit) {
    val items = listOf(
        SettingCategoryItem(SettingPage.APPEARANCE, Icons.Rounded.Palette,
            "外観", "テーマ・背景・アニメーションなどの見た目"),
        SettingCategoryItem(SettingPage.TEXT, Icons.Rounded.TextFields,
            "テキスト", "曲名・アーティスト名などの文字サイズと太さ"),
        SettingCategoryItem(SettingPage.PLAYER, Icons.Rounded.PlayCircle,
            "再生画面", "シークバー・波形・ホーム画面ショートカット"),
        SettingCategoryItem(SettingPage.LYRICS, Icons.Rounded.Lyrics,
            "歌詞", "歌詞の表示スタイル・フォント・同期の挙動"),
        SettingCategoryItem(SettingPage.APP_SELECT, Icons.Rounded.Apps,
            "アプリ切り替え", "アプリ切り替え画面の表示項目"),
        SettingCategoryItem(SettingPage.ACTION_MENU, Icons.Rounded.FormatListNumbered,
            "アクションメニュー", "長押しメニューの表示項目と並び順"),
        SettingCategoryItem(SettingPage.BACKGROUND, Icons.Default.Settings,
            "バックグラウンド", "バックグラウンドでの再生履歴記録"),
        SettingCategoryItem(SettingPage.HISTORY_DISPLAY, Icons.Rounded.History,
            "履歴画面", "履歴カードの背景・聴いた時間の表示形式"),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            // ★ MainActivity.ktのScaffoldがステータスバー分の余白を自動確保しなくなったため、
            //   TOPページ（自分ではScaffold/TopAppBarを持たない唯一の設定ページ）だけ
            //   ここで自分でステータスバー分の高さを読んで確保する。
            //   サブページ(SubPageScaffold)は自前のTopAppBarが余白を処理するため対応不要。
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = 12.dp
            )
    ) {
        Text("設定", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))
        items.forEach { item ->
            BorderedShadowBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                clickable = true,
                onClick = { onNavigate(item.page) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier.size(22.dp))
                }
            }
        }

        // ===== バックアップ（設定のエクスポート/インポート） =====
        Spacer(Modifier.height(8.dp))
        Text(
            "バックアップ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingsBackupSection(viewModel = viewModel)

        Spacer(Modifier.height(32.dp))
    }
}

// =============================================================================
// 設定のバックアップ（エクスポート/インポート）
// -----------------------------------------------------------------------------
// AppSettingsの全項目を1つのJSONファイルにまとめて書き出し・読み込みできるようにする。
// 機種変更やアプリの再インストール後に、細かく作り込んだ設定をまるごと復元できる。
//
// ★ 設計メモ:
//   ・実際のJSON変換・DataStoreへの反映はDatabase.ktのSettingsBackup /
//     SettingsRepository側に集約している。ここはファイル選択UI（CreateDocument /
//     OpenDocument）と結果表示だけを担当する。
//   ・読み込みは今の設定をまるごと上書きする操作のため、誤操作防止に
//     確認ダイアログを1枚挟んでから適用する（履歴リセット等、他の破壊的操作と
//     同じ考え方）。
//   ・JSONに存在しない項目は今の設定値のまま維持されるため（Database.kt側の仕様）、
//     古いバージョンで書き出したファイルを新しいバージョンのアプリで読み込んでも、
//     新しく増えた設定項目だけはデフォルトのまま残り、壊れずに読み込める。
// =============================================================================
@Composable
private fun SettingsBackupSection(viewModel: MediaPlayerViewModel) {
    val context = LocalContext.current
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    // ファイル書き出し先を選ぶダイアログ（Android標準のファイル保存ピッカー）
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportSettingsJson { json ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                resultMessage = "設定をファイルに書き出しました"
            } catch (e: Exception) {
                resultMessage = "書き出しに失敗しました: ${e.message}"
            }
        }
    }

    // 読み込むファイルを選ぶダイアログ
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.readText()
            if (json.isNullOrBlank()) {
                resultMessage = "ファイルを読み込めませんでした"
            } else {
                // すぐには適用せず、確認ダイアログを経由してから反映する
                pendingImportJson = json
                showImportConfirm = true
            }
        } catch (e: Exception) {
            resultMessage = "読み込みに失敗しました: ${e.message}"
        }
    }

    BorderedShadowBox(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        exportLauncher.launch("santopimedia_settings.json")
                    }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.SaveAlt, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("設定をファイルに書き出す", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "今の設定をすべてJSONファイルとして保存します",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(22.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FileOpen, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("設定をファイルから読み込む", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "書き出したJSONファイルから設定を復元します",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.3f), modifier = Modifier.size(22.dp))
            }
        }
    }

    // ===== 読み込み前の確認ダイアログ（今の設定を上書きするため） =====
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false; pendingImportJson = null },
            icon = {
                Icon(Icons.Rounded.Warning, null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("設定を読み込みますか？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "ファイルの内容で今の設定をすべて上書きします。この操作は元に戻せません。",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingImportJson
                        showImportConfirm = false
                        pendingImportJson = null
                        if (json != null) {
                            viewModel.importSettingsJson(json) { success, error ->
                                resultMessage = if (success) "設定を読み込みました"
                                else "読み込みに失敗しました: ${error ?: "不明なエラー"}"
                            }
                        }
                    }
                ) { Text("読み込む") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false; pendingImportJson = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // ===== 結果メッセージ（成功・失敗どちらも簡易ダイアログで通知） =====
    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("OK") }
            },
            text = { Text(msg) }
        )
    }
}

// ===== 共通トップバー =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    // ★ トップバー右側に置く追加要素（今のプレーヤー背景のミニプレビュー等）。
    //   何も指定しなければ何も表示しない（従来通り）。
    headerActions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "戻る")
                    }
                },
                actions = { headerActions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ===== 外観 =====
@Composable
private fun AppearancePage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    val mediaState by viewModel.mediaState.collectAsState()
    var showBackgroundPreviewDialog by remember { mutableStateOf(false) }
    var showBackgroundStyleDialog by remember { mutableStateOf(false) }
    var showColorPatternDialog by remember { mutableStateOf(false) }

    SubPageScaffold(
        title = "外観",
        onBack = onBack,
        headerActions = {
            // ★ ヘッダー右側: 今のプレーヤー背景のミニプレビュー。タップで拡大表示する。
            BackgroundStyleThumbnail(
                style = settings.backgroundStyle,
                mediaState = mediaState,
                settings = settings,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(width = 44.dp, height = 60.dp)
                    .clickable { showBackgroundPreviewDialog = true }
            )
        }
    ) {
        SettingSection("テーマ") {
            SettingRow(label = "テーマ") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "システム", "light" to "ライト", "dark" to "ダーク").forEach { (v, label) ->
                        FilterChip(selected = settings.themeMode == v,
                            onClick = { viewModel.updateSetting(SettingsKeys.THEME_MODE, v) },
                            label = { Text(label) })
                    }
                }
            }
        }
        SettingSection("プレーヤー") {
            SettingRow(label = "プレーヤーUI") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "C").forEach { style ->
                        FilterChip(selected = settings.playerUiStyle == style,
                            onClick = { viewModel.updateSetting(SettingsKeys.PLAYER_UI_STYLE, style) },
                            label = { Text("UI-$style") })
                    }
                }
            }
            if (settings.playerUiStyle == "C") {
                Text(
                    "UI-C: 上下分割プレーヤー。ドラッグで比率を3段階に変更、ダブルタップで上下を入れ替えられます。" +
                            "下側に表示する内容は下の「ホーム画面C」欄で選べます",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            SettingSwitchRow("ボタン色をアルバムアートから抽出",
                checked = settings.extractColorFromArt,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.EXTRACT_COLOR, it) })
            if (settings.extractColorFromArt) {
                // ★ 実際に抽出された色(mediaState.dominantColor)へ、各パターンと
                //   同じ考え方の色補正を簡易再現して見た目の傾向をプレビューする
                //   （MediaPlayerViewModel.adjustColor()の簡略コピー。完全一致は
                //   保証しないが、「どのパターンがどんな傾向か」は十分に伝わる）。
                SettingDropdownRow(
                    label = "色抽出パターン",
                    selectedValue = settings.colorExtractPattern,
                    options = listOf(1 to "白黒補正", 2 to "鮮やか", 3 to "落ち着いた暗め", 4 to "パステル", 5 to "補正なし"),
                    preview = { pattern ->
                        Box(
                            Modifier.fillMaxSize()
                                .background(previewAdjustColor(mediaState.dominantColor, pattern))
                        )
                    }
                ) {
                    viewModel.updateSetting(SettingsKeys.COLOR_EXTRACT_PATTERN, it)
                }
                SettingSliderRow(
                    "彩度（${"%.1f".format(settings.colorSaturation)}）",
                    settings.colorSaturation, 0.5f..1.5f, 9
                ) { viewModel.updateSetting(SettingsKeys.COLOR_SATURATION, it) }
                SettingSliderRow(
                    "明るさ（${"%.1f".format(settings.colorBrightness)}）",
                    settings.colorBrightness, 0.5f..1.5f, 9
                ) { viewModel.updateSetting(SettingsKeys.COLOR_BRIGHTNESS, it) }
            }
            // ★ ドロップダウンではなく、プレビュー付きダイアログで選ぶ方式に変更。
            //   一覧の各項目に「実際にどう見えるか」の小さいプレビューが付くため、
            //   名前だけでは伝わりにくい背景スタイルの違いが一目で分かる。
            DialogTriggerRow(
                label = "背景スタイル",
                valueLabel = backgroundStyleLabel(settings.backgroundStyle),
                onClick = { showBackgroundStyleDialog = true }
            )

            if (settings.backgroundStyle == "animated") {
                SettingRow(label = "背景の色ルール") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "accent"    to "アクセントカラーから",
                            "album_art" to "アルバムアートから"
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
                SettingSwitchRow(
                    "アイコン・テキストを白に固定",
                    checked = settings.animatedWhiteIcon,
                    onCheckedChange = { viewModel.updateSetting(SettingsKeys.ANIMATED_WHITE_ICON, it) }
                )
                Text(
                    "ONにすると動的アニメーション背景上のアイコンとテキストを白で固定します",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )

                Spacer(Modifier.height(4.dp))

                // ===== 背景の色の濃さ =====
                SettingSliderRow(
                    "背景の色の濃さ（${(settings.animatedBgDarkness * 100).toInt()}%）",
                    settings.animatedBgDarkness, 0.6f..1.0f, 7
                ) { viewModel.updateSetting(SettingsKeys.ANIMATED_BG_DARKNESS, it) }
                Text(
                    "低いほど暗く落ち着いた背景になります。ダークモードのときはさらに自動で暗くなります",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )

                Spacer(Modifier.height(4.dp))

                // ===== 色の混ぜ方パターン =====
                // ★ こちらもプレビュー付きダイアログ化。実際のAnimatedMeshBackgroundを
                //   小さく描画するため、選ぶ前に本番と同じ見た目を確認できる。
                DialogTriggerRow(
                    label = "色の混ぜ方",
                    valueLabel = colorPatternLabel(settings.animatedColorPattern),
                    onClick = { showColorPatternDialog = true }
                )
                Text(
                    "「アルバムアートから」を選んでいて背景が単色っぽく見える場合は、" +
                            "「ビビッド」や「補色」に変更すると色の差がはっきりします",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            if (settings.backgroundStyle == "blur") {
                SettingRow(label = "ぼかしのスタイル") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "normal"    to "ふつう",
                            "dark_mode" to "すりガラス風"
                        ).forEach { (v, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = settings.blurStyle == v,
                                    onClick = { viewModel.updateSetting(SettingsKeys.BLUR_STYLE, v) }
                                )
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // ★ 参考画像の例と同じく、実際のアクセントカラーと単色(グレー)を
            //   そのまま小さな丸角パネルとしてプレビュー表示する。
            SettingDropdownRow(
                label = "ナビゲーションバーの背景",
                selectedValue = settings.navBarBackgroundStyle,
                options = listOf("accent" to "アクセントカラーから自動計算", "solid" to "単色"),
                preview = { value ->
                    val rawAccentColor = if (settings.extractColorFromArt)
                        mediaState.dominantColor.copy(alpha = 1f) else MaterialTheme.colorScheme.primary
                    val previewColor = if (value == "accent") rawAccentColor
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    Box(Modifier.fillMaxSize().background(previewColor))
                }
            ) {
                viewModel.updateSetting(SettingsKeys.NAV_BAR_BACKGROUND_STYLE, it)
            }
            Text(
                "下部のピル型ナビゲーションバーの背景色です",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            SettingSliderRow(
                "ナビゲーションバーの透明度（${(settings.navBarOpacity * 100).toInt()}%）",
                settings.navBarOpacity, 0.5f..1.0f, 9
            ) { viewModel.updateSetting(SettingsKeys.NAV_BAR_OPACITY, it) }
            SettingSliderRow(
                "ナビゲーションバーの大きさ（レベル${settings.navBarSizeLevel}）",
                settings.navBarSizeLevel.toFloat(), 1f..5f, 3
            ) { viewModel.updateSetting(SettingsKeys.NAV_BAR_SIZE_LEVEL, it.toInt()) }
            SettingSliderRow(
                "ナビゲーションバーのボタン間隔（${settings.navBarButtonSpacing}dp）",
                settings.navBarButtonSpacing.toFloat(), 2f..20f, 8
            ) { viewModel.updateSetting(SettingsKeys.NAV_BAR_BUTTON_SPACING, it.toInt()) }
            SettingSliderRow(
                "ホーム画面アイコンの透明度（${(settings.homeIconOpacity * 100).toInt()}%）",
                settings.homeIconOpacity, 0.5f..1.0f, 9
            ) { viewModel.updateSetting(SettingsKeys.HOME_ICON_OPACITY, it) }
            Text(
                "再生・スキップなど、ホーム画面（UI-A）のコントロールアイコンの透明度です",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
        SettingSection("バイブレーション") {
            SettingSwitchRow("ボタンを押したときに振動",
                checked = settings.vibrationEnabled,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.VIBRATION_ENABLED, it) })
            if (settings.vibrationEnabled) {
                SettingSliderRow("振動の強さ（${settings.vibrationStrength}ms）",
                    settings.vibrationStrength.toFloat(), 10f..100f, 8) {
                    viewModel.updateSetting(SettingsKeys.VIBRATION_STRENGTH, it.toInt())
                }
            }
        }
    }

    // ===== ヘッダープレビュー拡大ダイアログ =====
    if (showBackgroundPreviewDialog) {
        BackgroundPreviewDialog(
            style = settings.backgroundStyle,
            mediaState = mediaState,
            settings = settings,
            onDismiss = { showBackgroundPreviewDialog = false }
        )
    }

    // ===== 背景スタイル選択ダイアログ =====
    if (showBackgroundStyleDialog) {
        BackgroundStyleDialog(
            current = settings.backgroundStyle,
            mediaState = mediaState,
            settings = settings,
            onSelect = { viewModel.updateSetting(SettingsKeys.BACKGROUND_STYLE, it) },
            onDismiss = { showBackgroundStyleDialog = false }
        )
    }

    // ===== 色の混ぜ方選択ダイアログ =====
    if (showColorPatternDialog) {
        ColorPatternDialog(
            current = settings.animatedColorPattern,
            mediaState = mediaState,
            settings = settings,
            onSelect = { viewModel.updateSetting(SettingsKeys.ANIMATED_COLOR_PATTERN, it) },
            onDismiss = { showColorPatternDialog = false }
        )
    }
}

// =============================================================================
// 背景プレビュー系ヘルパー（外観設定ヘッダー・背景スタイル/色の混ぜ方ダイアログで使用）
// =============================================================================

// ★ Modifier.blur()（ぼかし効果）は内部的にRenderEffectを使っており、
//   これはAndroid 12 (API31, S)以降でしか動作しない。API31未満の端末では
//   blur()を呼んでも何も起きず（ぼかしがかからない元画像のまま）、
//   「ぼかし」設定を選んでも見た目上ほぼ意味をなさない状態になってしまう。
//   そのため、ぼかしに依存する設定項目はこの判定を使って選択肢自体を
//   無効化・除外し、使えない機能をそもそも選べないようにする。
//   （「動的アニメーション」もAPI33未満でのフォールバック描画がぼかしに依存しているため、
//   同様にAPI31未満では実質機能しない扱いとする）
private fun isBlurRenderSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

// ★ 色抽出パターン（colorExtractPattern）ダイアログのプレビュー用。
//   MediaPlayerViewModel.adjustColor() と同じ考え方の色補正を簡易再現したもの。
//   ViewModel側は private かつ ViewModel インスタンスに紐づく処理のため、
//   設定画面から直接は呼べない。完全に同じ結果を保証する必要は無く
//   （実際の抽出色は再生中のアルバムアート次第で常に変わるため）、
//   「パターンごとの色の傾向」が伝わることを目的とした近似実装。
private fun previewAdjustColor(base: Color, pattern: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (base.red * 255).toInt(), (base.green * 255).toInt(), (base.blue * 255).toInt(), hsv
    )
    when (pattern) {
        2 -> {
            hsv[1] = (hsv[1] + 0.3f).coerceIn(0.6f, 1.0f)
            hsv[2] = hsv[2].coerceIn(0.6f, 1.0f)
        }
        3 -> {
            hsv[1] = (hsv[1] + 0.1f).coerceIn(0.4f, 0.9f)
            hsv[2] = (hsv[2] - 0.15f).coerceIn(0.3f, 0.75f)
        }
        4 -> {
            hsv[1] = (hsv[1] - 0.2f).coerceIn(0.2f, 0.7f)
            hsv[2] = (hsv[2] + 0.2f).coerceIn(0.55f, 1.0f)
        }
        5 -> { /* 補正なし（Dominantそのまま） */ }
        else -> {
            // パターン1（白黒補正）
            val luminance = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
            when {
                luminance > 0.75f -> {
                    hsv[1] = (hsv[1] + 0.4f).coerceIn(0.5f, 1.0f)
                    hsv[2] = (hsv[2] - 0.35f).coerceIn(0.35f, 0.85f)
                }
                luminance < 0.15f -> {
                    hsv[2] = (hsv[2] + 0.35f).coerceIn(0.45f, 0.9f)
                }
            }
        }
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 現在の選択値をDialogTriggerRowの右側に短く表示するためのラベル変換
private fun backgroundStyleLabel(value: String): String = when (value) {
    "default"   -> "デフォルト"
    "gradient"  -> "グラデーション"
    "blur"      -> "ぼかし"
    "color_mix" -> "色混ぜ"
    "animated"  -> "動的アニメーション"
    else        -> value
}

private fun colorPatternLabel(value: String): String = when (value) {
    "soft"          -> "ソフト"
    "normal"        -> "ノーマル"
    "vivid"         -> "ビビッド"
    "complementary" -> "補色"
    else            -> value
}

// ===== 背景スタイルのミニプレビュー =====
// ★ HomeScreenA.kt等の本番の背景描画をそのまま流用すると、フル解像度でのぼかし処理や
//   AGSLシェーダーの初期化がこの小さな枠には過剰・重いため、ここでは
//   「見た目の傾向が伝わる」簡略版を描く。
//   ・blur          : 実際のアルバムアートを縮小してぼかす（一番違いが分かりやすいため実物を使う）
//   ・gradient/color_mix : 代表色（アクセントカラー・アルバムアート混色）のグラデーションで近似
//   ・animated      : 実際のAnimatedMeshBackground（動的アニメーション背景）をそのまま
//                     小さいCanvasで描画する。この設定だけは近似だと質感が伝わりにくいため、
//                     本物を縮小して見せる方針にした
//   ・default       : テーマの背景色のみ
@Composable
private fun BackgroundStyleThumbnail(
    style: String,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    val rawAccentColor = if (settings.extractColorFromArt) mediaState.dominantColor.copy(alpha = 1f)
    else MaterialTheme.colorScheme.primary
    val animatedBgColor = when (settings.animatedColorSource) {
        "album_art" -> mediaState.blendedArtColor.copy(alpha = 1f)
        else -> rawAccentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (style) {
            "blur" -> {
                if (mediaState.albumArtUri != null) {
                    AsyncImage(
                        model = mediaState.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blur(10.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            (if (isDark) Color.Black else Color.White).copy(alpha = 0.35f)
                        )
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(rawAccentColor.copy(alpha = 0.7f)))
                }
            }
            "gradient" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(rawAccentColor.copy(alpha = 0.7f), MaterialTheme.colorScheme.background)
                    )
                )
            )
            "color_mix" -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(rawAccentColor, mediaState.blendedArtColor.copy(alpha = 1f)))
                )
            )
            "animated" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    com.sandolpin.santopimedia35.home.AnimatedMeshBackground(
                        color1 = animatedBgColor,
                        darkness = settings.animatedBgDarkness,
                        colorPattern = settings.animatedColorPattern,
                        isDarkTheme = isDark,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // API33未満はAGSLが使えないため、既存のフォールバック方針(ぼかし相当)に合わせ
                    // 代表色のグラデーションで代用する
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(rawAccentColor, mediaState.blendedArtColor.copy(alpha = 1f)))
                        )
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
    }
}

// ===== 色の混ぜ方（animatedColorPattern）のミニプレビュー =====
// ★ この設定は「動的アニメーション」背景の色の混ぜ方そのものを変える設定のため、
//   本物のAnimatedMeshBackgroundを小さく描画するのが最も正確なプレビューになる
//   （簡易グラデーションでは4パターンの違いが伝わりにくいため）。
//   API33未満は元々この機能自体が使えないため、簡易グラデーションで代用する。
@Composable
private fun ColorPatternThumbnail(
    pattern: String,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    val rawAccentColor = if (settings.extractColorFromArt) mediaState.dominantColor.copy(alpha = 1f)
    else MaterialTheme.colorScheme.primary
    val animatedBgColor = when (settings.animatedColorSource) {
        "album_art" -> mediaState.blendedArtColor.copy(alpha = 1f)
        else -> rawAccentColor
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            com.sandolpin.santopimedia35.home.AnimatedMeshBackground(
                color1 = animatedBgColor,
                darkness = settings.animatedBgDarkness,
                colorPattern = pattern,
                isDarkTheme = isDark,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(animatedBgColor, mediaState.blendedArtColor.copy(alpha = 1f)))
                )
            )
        }
    }
}

// ===== 設定行: タップでダイアログを開く（右側に現在値のラベルを表示）=====
@Composable
private fun DialogTriggerRow(label: String, valueLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(
            valueLabel, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.35f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// ===== 選択肢1行: 左にプレビュー・右にラベル+選択チェック（参考画像のデザイン）=====
// 全体を角丸の枠で囲み、選択中はアクセントカラーの枠線、未選択は薄い枠線にする。
// enabled=false のときはタップを受け付けず、全体を薄くしてロックアイコン+理由を表示する
// （Android 12未満の端末でぼかし系オプションを選べなくするために使用）。
@Composable
private fun PreviewOptionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (selected && enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                preview()
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (!enabled && disabledReason != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        disabledReason,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (!enabled) {
                Icon(
                    Icons.Rounded.Lock, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(18.dp)
                )
            } else if (selected) {
                Icon(
                    Icons.Rounded.Check, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ===== 背景スタイル選択ダイアログ =====
@Composable
private fun BackgroundStyleDialog(
    current: String,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    settings: AppSettings,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val blurSupported = isBlurRenderSupported()
    val options = listOf(
        "default"   to "デフォルト",
        "gradient"  to "グラデーション",
        "blur"      to "ぼかし",
        "color_mix" to "色混ぜ",
        "animated"  to "動的アニメーション"
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Text("背景スタイル", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                // ★ Android 12未満の端末では「ぼかし」「動的アニメーション」が実質機能しないため、
                //   その旨を先に案内した上で、該当の選択肢自体をロック表示にする。
                if (!blurSupported) {
                    Text(
                        "お使いの端末（Android 12未満）では「ぼかし」「動的アニメーション」は使用できません",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                options.forEach { (value, label) ->
                    val needsBlur = value == "blur" || value == "animated"
                    val enabled = !needsBlur || blurSupported
                    PreviewOptionRow(
                        label = label,
                        selected = current == value,
                        enabled = enabled,
                        disabledReason = if (!enabled) "Android 12以上が必要" else null,
                        onClick = { onSelect(value); onDismiss() },
                        preview = {
                            BackgroundStyleThumbnail(
                                style = value, mediaState = mediaState, settings = settings,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ===== 色の混ぜ方選択ダイアログ =====
@Composable
private fun ColorPatternDialog(
    current: String,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    settings: AppSettings,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "soft"          to "ソフト",
        "normal"        to "ノーマル",
        "vivid"         to "ビビッド",
        "complementary" to "補色"
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Text("色の混ぜ方", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                options.forEach { (value, label) ->
                    PreviewOptionRow(
                        label = label,
                        selected = current == value,
                        onClick = { onSelect(value); onDismiss() },
                        preview = {
                            ColorPatternThumbnail(
                                pattern = value, mediaState = mediaState, settings = settings,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ===== ヘッダーサムネイルの拡大表示ダイアログ =====
@Composable
private fun BackgroundPreviewDialog(
    style: String,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        "背景プレビュー（${backgroundStyleLabel(style)}）",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                BackgroundStyleThumbnail(
                    style = style,
                    mediaState = mediaState,
                    settings = settings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
        }
    }
}

// ===== テキスト =====
@Composable
private fun TextPage(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    mediaState: com.sandolpin.santopimedia35.database.MediaState,
    onBack: () -> Unit
) {
    SubPageScaffold("テキスト", onBack) {
        // プレビュー
        SettingSection("プレビュー") {
            Column {
                Text(mediaState.title.ifEmpty { "タイトル名" },
                    fontSize = settings.titleFontSize.sp, fontWeight = FontWeight(settings.titleFontWeight),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(mediaState.artist.ifEmpty { "アーティスト名" },
                    fontSize = settings.artistFontSize.sp, fontWeight = FontWeight(settings.artistFontWeight),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f), maxLines = 1)
                Text(mediaState.album.ifEmpty { "アルバム名" },
                    fontSize = settings.albumFontSize.sp, fontWeight = FontWeight(settings.albumFontWeight),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f), maxLines = 1)
            }
        }
        SettingSection("タイトル・アーティスト・アルバム") {
            TextStyleSetting("タイトル", settings.titleFontSize, settings.titleFontWeight,
                { viewModel.updateSetting(SettingsKeys.TITLE_FONT_SIZE, it) },
                { viewModel.updateSetting(SettingsKeys.TITLE_FONT_WEIGHT, it) },
                { viewModel.updateSetting(SettingsKeys.TITLE_FONT_SIZE, 22); viewModel.updateSetting(SettingsKeys.TITLE_FONT_WEIGHT, 700) })
            TextStyleSetting("アーティスト", settings.artistFontSize, settings.artistFontWeight,
                { viewModel.updateSetting(SettingsKeys.ARTIST_FONT_SIZE, it) },
                { viewModel.updateSetting(SettingsKeys.ARTIST_FONT_WEIGHT, it) },
                { viewModel.updateSetting(SettingsKeys.ARTIST_FONT_SIZE, 14); viewModel.updateSetting(SettingsKeys.ARTIST_FONT_WEIGHT, 400) })
            TextStyleSetting("アルバム", settings.albumFontSize, settings.albumFontWeight,
                { viewModel.updateSetting(SettingsKeys.ALBUM_FONT_SIZE, it) },
                { viewModel.updateSetting(SettingsKeys.ALBUM_FONT_WEIGHT, it) },
                { viewModel.updateSetting(SettingsKeys.ALBUM_FONT_SIZE, 12); viewModel.updateSetting(SettingsKeys.ALBUM_FONT_WEIGHT, 400) })
            SettingSwitchRow("タイトルが長い場合スクロール",
                checked = settings.scrollLongTitle,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.SCROLL_LONG_TITLE, it) })
        }
    }
}

// ===== 再生画面 =====
@Composable
private fun PlayerPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("再生画面", onBack) {
        SettingSection("シークバー") {
            SettingSwitchRow("シークバーを波立たせる（UI-Aのみ）",
                checked = settings.waveSeekBar && settings.playerUiStyle == "A",
                enabled = settings.playerUiStyle == "A",
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.WAVE_SEEK_BAR, it) })
            if (settings.waveSeekBar && settings.playerUiStyle == "A") {
                SettingRow(label = "波のスタイル") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "シンプル", 2 to "複数波").forEach { (v, label) ->
                            FilterChip(selected = settings.waveSeekBarStyle == v,
                                onClick = { viewModel.updateSetting(SettingsKeys.WAVE_SEEK_BAR_STYLE, v) },
                                label = { Text(label) })
                        }
                    }
                }
            }
            SettingSliderRow("シークバーの太さ（${settings.seekBarThickness}dp）",
                settings.seekBarThickness.toFloat(), 3f..15f, 11) {
                viewModel.updateSetting(SettingsKeys.SEEK_BAR_THICKNESS, it.toInt())
            }
            SettingRow(label = "更新間隔") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.1f to "0.1", 0.3f to "0.3", 0.5f to "0.5", 1.0f to "1", 0f to "なし").forEach { (value, label) ->
                        FilterChip(selected = settings.seekBarUpdateInterval == value,
                            onClick = { viewModel.updateSetting(SettingsKeys.SEEK_BAR_UPDATE_INTERVAL, value) },
                            label = { Text(label, fontSize = 11.sp) })
                    }
                }
            }
        }
        SettingSection("表示") {
            SettingSwitchRow("波形を表示",
                checked = settings.showWaveform,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.SHOW_WAVEFORM, it) })
            SettingSwitchRow("音量バーを表示",
                checked = settings.showVolumeBar,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.SHOW_VOLUME_BAR, it) })
        }
        SettingSection("ホーム画面ショートカット") {
            Text(
                "音量バー両脇のボタンに割り当てる機能を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow(
                label = "左側",
                selectedValue = settings.homeShortcutLeft,
                options = homeShortcutOptions,
                preview = { type -> HomeShortcutIconPreview(type) }
            ) {
                viewModel.updateSetting(SettingsKeys.HOME_SHORTCUT_LEFT, it)
            }
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow(
                label = "右側",
                selectedValue = settings.homeShortcutRight,
                options = homeShortcutOptions,
                preview = { type -> HomeShortcutIconPreview(type) }
            ) {
                viewModel.updateSetting(SettingsKeys.HOME_SHORTCUT_RIGHT, it)
            }
        }
        SettingSection("ホーム画面C（上下分割）") {
            Text(
                "プレーヤーUIを「UI-C」にしたときの、下側ボックス（上下入れ替え時は上側）に" +
                        "表示する内容を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow(
                label = "表示する内容",
                selectedValue = settings.homeScreenCSecondaryContent,
                options = listOf("lyrics" to "歌詞", "queue" to "キュー", "history" to "りれき"),
                preview = { type -> SecondaryContentIconPreview(type) }
            ) {
                viewModel.updateSetting(SettingsKeys.HOME_SCREEN_C_SECONDARY_CONTENT, it)
            }
        }
    }
}

// ===== ホーム画面ショートカット/ホーム画面Cの表示内容ダイアログ用アイコンプレビュー =====
@Composable
private fun HomeShortcutIconPreview(type: String) {
    val icon = when (type) {
        "favorite" -> Icons.Rounded.Star
        "history"  -> Icons.Rounded.History
        "share"    -> Icons.Rounded.Share
        "repeat"   -> Icons.Rounded.Repeat
        "shuffle"  -> Icons.Rounded.Shuffle
        else       -> Icons.Rounded.Lyrics // "lyrics"（デフォルト）
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SecondaryContentIconPreview(type: String) {
    val icon = when (type) {
        "queue"   -> Icons.Rounded.QueueMusic
        "history" -> Icons.Rounded.History
        else      -> Icons.Rounded.Lyrics // "lyrics"（デフォルト）
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

// ホーム画面ショートカットの選択肢（左右共通）
private val homeShortcutOptions = listOf(

    "lyrics"   to "歌詞",
    "favorite" to "お気に入り",
    "history"  to "履歴",
    "share"    to "シェア",
    "repeat"   to "リピート",
    "shuffle"  to "シャッフル"
)

// ===== 歌詞 =====
@Composable
private fun LyricsPage(
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
                            "accent"    to "アクセントカラーから",
                            "album_art" to "アルバムアートから"
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
private fun LyricsFontPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
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
private fun LyricsFilePage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
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

// ===== アプリ切り替え =====
@Composable
private fun AppSelectPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("アプリ切り替え", onBack) {
        SettingSection("表示") {
            SettingSwitchRow("ミニプレーヤーを表示",
                checked = settings.appSelectShowMiniPlayer,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.APP_SELECT_SHOW_MINI_PLAYER, it) })
            SettingSwitchRow("アプリアイコンを表示",
                checked = settings.appSelectShowIcon,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.APP_SELECT_SHOW_ICON, it) })
        }
    }
}

// ===== バックグラウンド処理 =====
@Composable
private fun BackgroundPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    SubPageScaffold("バックグラウンド処理", onBack) {
        SettingSection("履歴記録") {
            SettingSwitchRow("バックグラウンドで履歴記録を許可",
                checked = settings.backgroundHistoryEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSetting(SettingsKeys.BACKGROUND_HISTORY_ENABLED, enabled)
                    if (enabled) HistoryService.start(context) else HistoryService.stop(context)
                })
            Text(
                "ONにするとアプリを閉じていても再生履歴の時間を記録し続けます。" +
                        "OSに終了させられにくくするため、記録中は通知欄に常駐通知が表示されます" +
                        "（音・バイブは鳴りません／スワイプでは消せません）。バッテリーに多少影響する場合があります。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }

        SettingSection("タスクキル対策") {
            // 画面に入るたび・戻ってくるたびに最新の状態を再チェックする
            // （端末の設定画面から戻ってきた場合に反映するため）
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            var isIgnoringBatteryOpt by remember {
                mutableStateOf(isIgnoringBatteryOptimizations(context))
            }
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isIgnoringBatteryOpt = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("バッテリー最適化の除外", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (isIgnoringBatteryOpt)
                            "設定済み：OSの標準機能による終了は起きにくくなっています"
                        else
                            "未設定：OSの省電力機能により記録が途中で止まる場合があります",
                        fontSize = 11.sp,
                        color = if (isIgnoringBatteryOpt)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
                if (!isIgnoringBatteryOpt) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 端末によってはこのIntentに対応していない場合がある
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("設定する", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(12.dp))

            Text(
                "上記のAndroid標準の設定だけでは、端末メーカー（Xiaomi/Samsung/OPPO/Vivo/Huaweiなど）独自の" +
                        "バッテリー管理機能までは解除できないことがあります。それでも記録が止まってしまう場合は、" +
                        "端末の設定アプリ内で「バッテリー」「アプリの自動起動」「保護されたアプリ」などの項目から" +
                        "本アプリを許可リストに追加してください（名称は端末により異なります）。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "また、タスク一覧（最近使ったアプリ）からアプリ自体をスワイプして完全に終了させた場合は、" +
                        "Androidの仕様上どのアプリであっても自動では復帰できません。バックグラウンド記録を継続するには、" +
                        "アプリをタスク一覧から消さないようにしてください。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

// バッテリー最適化の対象外（除外）になっているかを確認する
private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE)
            as? android.os.PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

// ===== 履歴画面 =====
@Composable
private fun HistoryDisplayPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("履歴画面", onBack) {
        SettingSection("プレビュー") {
            // ★ 実際の履歴データではなく、表示確認用のダミーデータを使う。
            //   下の各設定（背景スタイル・日時表示・アート縁取り・聴いた時間表示・
            //   進捗バー・アートサイズ）を変更すると、このカードにその場で反映される。
            //   ただしアルバムアートの実画像は無い（albumArtUri=null）ため、
            //   Paletteでの色抽出が前提の背景スタイル（ぼかし・複数色ミックス・
            //   アート色で塗りつぶし）は、実際の曲再生時とは異なる簡易表示になる点に注意。
            val previewEntity = remember {
                PlayHistoryEntity(
                    title = "サンプルタイトル",
                    artist = "サンプルアーティスト",
                    album = "サンプルアルバム",
                    albumArtUri = null,
                    appLabel = "Apple Music",
                    packageName = "com.apple.android.music",
                    playedAtMs = System.currentTimeMillis() - 125_000L,
                    durationMs = 210_000L,
                    playTimeMs = 125_000L,
                    listenedMs = 125_000L,
                    listenedAll = false
                )
            }
            HistoryCard(
                entity = previewEntity,
                cardBackgroundStyle = settings.historyCardBackgroundStyle,
                showDate = settings.historyShowDate,
                timeRangeMode = settings.historyTimeRangeMode,
                artBorderEnabled = settings.historyArtBorderEnabled,
                showPlayTime = settings.historyShowPlayTime,
                showProgressBar = settings.historyShowProgressBar,
                artSize = settings.historyArtSize
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "※プレビュー用のサンプルです。実際のアルバムアートが無いため、" +
                        "アートの色を使う背景スタイルは簡易表示になります",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
        }
        SettingSection("カードの背景") {
            Text(
                "「きいた曲」カードのアルバムアート背景の見せ方を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            // ★ Android 12未満は「アルバムアートのぼかし」が実質機能しないため除外する。
            val historyBgOptions = remember(settings.historyCardBackgroundStyle) {
                val all = listOf(
                    "blur" to "アルバムアートのぼかし",
                    "animated" to "複数色ミックス",
                    "color_fill" to "アルバムアート色で塗りつぶし"
                )
                if (isBlurRenderSupported()) all else all.filterNot { it.first == "blur" }
            }
            SettingDropdownRow("背景スタイル", settings.historyCardBackgroundStyle, historyBgOptions) {
                viewModel.updateSetting(SettingsKeys.HISTORY_CARD_BACKGROUND_STYLE, it)
            }
            if (!isBlurRenderSupported()) {
                Text(
                    "Android 12未満のため「アルバムアートのぼかし」は選択できません",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
        SettingSection("聴いた時間の表示") {
            Text(
                "「きょう聞いた時間」や再生時間の表示形式を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow("表示形式", settings.historyTimeDisplayMode,
                listOf("auto" to "自動調整（時間・分・秒）", "seconds" to "秒に固定", "minutes" to "分に固定", "hours" to "時間に固定")) {
                viewModel.updateSetting(SettingsKeys.HISTORY_TIME_DISPLAY_MODE, it)
            }
            // プレビュー
            Spacer(Modifier.height(8.dp))
            val previewMs = 7_384_000L // 2時間3分4秒 相当のサンプル
            Text(
                "プレビュー: ${com.sandolpin.santopimedia35.remember.formatListenedTimeMode(previewMs, settings.historyTimeDisplayMode)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SettingSection("カードの日時表示") {
            Text(
                "「きいた曲」カード1件ごとに表示する日時の形式を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                "日付を表示",
                checked = settings.historyShowDate,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_DATE, it) }
            )
            Spacer(Modifier.height(4.dp))
            SettingDropdownRow("表示する時間", settings.historyTimeRangeMode,
                listOf("start" to "開始時間のみ", "end" to "終了時間のみ", "both" to "開始〜終了（両方）")) {
                viewModel.updateSetting(SettingsKeys.HISTORY_TIME_RANGE_MODE, it)
            }
        }

        SettingSection("カードの表示項目") {
            SettingSwitchRow(
                "アルバムアートを白で縁取り",
                checked = settings.historyArtBorderEnabled,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_ART_BORDER_ENABLED, it) }
            )
            SettingSwitchRow(
                "カードに聴いた時間を表示",
                checked = settings.historyShowPlayTime,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_PLAY_TIME, it) }
            )
            Text(
                "開始〜停止までの実際に聴いていた時間を「〇:〇〇聴きました」の形式で表示します",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(6.dp))
            SettingSwitchRow(
                "カードに進捗状況を表示",
                checked = settings.historyShowProgressBar,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_PROGRESS_BAR, it) }
            )
            Text(
                "現在のシークバー（どこまで聴いたか）をカードに表示します",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }

        SettingSection("アルバムアートの大きさ") {
            // ★ 実際のサイズ比率(HistoryScreen.ktのartSizeDp: 48/64/88/112dp)に
            //   準じた大きさの正方形を、中央寄せで表示してサイズ感をそのまま伝える。
            SettingDropdownRow(
                label = "サイズ",
                selectedValue = settings.historyArtSize,
                options = listOf("small" to "小", "medium" to "中（標準）", "large" to "大", "xlarge" to "特大"),
                preview = { value ->
                    val squareDp = when (value) {
                        "small" -> 16.dp
                        "large" -> 30.dp
                        "xlarge" -> 36.dp
                        else -> 22.dp // "medium"
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(squareDp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            ) {
                viewModel.updateSetting(SettingsKeys.HISTORY_ART_SIZE, it)
            }
        }
    }
}

// ===== アクションメニューID → 表示ラベル =====
private val actionMenuItemLabels = mapOf(
    "share" to "シェア",
    "search" to "今流れている曲をしらべる",
    "time_seek" to "指定の時間から再生",
    "lyrics" to "歌詞を表示",
    "favorites_list" to "お気に入り",
    "favorite_toggle" to "お気に入りに追加/解除",
    "property" to "プロパティ",
    "settings" to "設定"
)

// ===== アクションメニュー: 表示/非表示・並び替え =====
@Composable
private fun ActionMenuSettingPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    // 文字列(カンマ区切り) ⇔ リストの変換ヘルパー
    fun parseOrder(s: String): List<String> =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun parseHidden(s: String): Set<String> =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val order = remember(settings.actionMenuOrder) { parseOrder(settings.actionMenuOrder).toMutableStateList() }
    val hidden = remember(settings.actionMenuHidden) {
        mutableStateOf(parseHidden(settings.actionMenuHidden))
    }

    fun saveOrder(newOrder: List<String>) {
        viewModel.updateSetting(SettingsKeys.ACTION_MENU_ORDER, newOrder.joinToString(","))
    }
    fun saveHidden(newHidden: Set<String>) {
        hidden.value = newHidden
        viewModel.updateSetting(SettingsKeys.ACTION_MENU_HIDDEN, newHidden.joinToString(","))
    }

    SubPageScaffold("アクションメニュー", onBack) {
        SettingSection("表示する項目・並び順") {
            Text(
                "長押しメニューに表示する項目のON/OFFと表示順を設定できます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            order.forEachIndexed { index, id ->
                val isHidden = hidden.value.contains(id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ===== 並び替えボタン =====
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                val newOrder = order.toMutableList()
                                val tmp = newOrder[index - 1]
                                newOrder[index - 1] = newOrder[index]
                                newOrder[index] = tmp
                                order.clear(); order.addAll(newOrder)
                                saveOrder(newOrder)
                            }
                        },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp, "上へ",
                            modifier = Modifier.size(18.dp),
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.2f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index < order.size - 1) {
                                val newOrder = order.toMutableList()
                                val tmp = newOrder[index + 1]
                                newOrder[index + 1] = newOrder[index]
                                newOrder[index] = tmp
                                order.clear(); order.addAll(newOrder)
                                saveOrder(newOrder)
                            }
                        },
                        enabled = index < order.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown, "下へ",
                            modifier = Modifier.size(18.dp),
                            tint = if (index < order.size - 1) MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.2f)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Text(
                        actionMenuItemLabels[id] ?: id,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )

                    // ===== 表示/非表示トグル =====
                    Switch(
                        checked = !isHidden,
                        onCheckedChange = { checkedNowVisible ->
                            val newHidden = hidden.value.toMutableSet()
                            if (checkedNowVisible) newHidden.remove(id) else newHidden.add(id)
                            saveHidden(newHidden)
                        },
                        colors = SwitchDefaults.colors()
                    )
                }
                if (index < order.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                }
            }
        }
    }
}

// ===== 共通UIパーツ =====

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        BorderedShadowBox(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), content = content)
        }
    }
}

// ===== 縁取り + 影付きボックス（設定画面共通）=====
// これまでは縁取り(border)だけのSurfaceだったが、影(shadow)を追加してより立体的に見せる。
//
// ★ shadowとclip/borderを同じModifierチェーンに乗せると、環境やアニメーションとの組み合わせ
//   によって影が切り取られてしまうことがある（AlbumArtCard@HomescreenA.kt で既に踏んだ問題と同じ）。
//   そのため「影だけを描く外側Box」と「clip+border+中身を描く内側Box」を分離する。
@Composable
fun BorderedShadowBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    clickable: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier.shadow(
            elevation = 3.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.18f),
            spotColor = Color.Black.copy(alpha = 0.22f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, borderColor, shape)
                .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
        ) {
            content()
        }
    }
}

@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

// =============================================================================
// 汎用: プレビュー付き選択肢ダイアログ（プルダウン設定の共通実装）
// -----------------------------------------------------------------------------
// SettingDropdownRow（旧: DropdownMenuによるプルダウン）が内部で使う共通ダイアログ。
// 参考画像のデザイン（各選択肢を角丸の枠で囲み、左にプレビュー、選択中は右に
// チェックマーク、下部中央に「閉じる」ボタン）に合わせている。
// タップで選択は即座に反映されるが、ダイアログ自体は自動では閉じない
// （画像の例と同じく、選んだ結果を確認しながら他の選択肢も見比べられるように、
// 明示的に「閉じる」を押すまで開いたままにしている）。
//
// ★ preview引数について:
//   nullの場合は「選択肢を区別しやすくするための色付き四角」を自動生成する
//   （GraphScreen.ktの円グラフ用パレットPIE_COLORSを流用。意味のある色ではなく、
//   あくまで一覧性のための識別カラー）。
//   設定の意味と直接対応した見た目（実際のアクセントカラー、アイコン、
//   サイズ感など）を見せたい場合は、呼び出し側でpreviewを個別に指定する
//   （例: ナビゲーションバーの背景、色抽出パターン、ショートカットのアイコン等）。
// =============================================================================
@Composable
private fun <T> GenericOptionSelectDialog(
    title: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    preview: (@Composable (T) -> Unit)?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                options.forEachIndexed { index, (value, optionLabel) ->
                    val selected = value == selectedValue
                    Surface(
                        onClick = { onSelect(value) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (preview != null) preview(value)
                                else DefaultOptionPreview(index)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                optionLabel, fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Rounded.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))

                // ===== 閉じるボタン（参考画像に合わせ、下部中央のピル型ボタン） =====
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "閉じる",
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// デフォルトプレビュー: 選択肢を見分けやすくするための色付き四角（意味のある色ではない）
@Composable
private fun DefaultOptionPreview(index: Int) {
    val palette = com.sandolpin.santopimedia35.remember.PIE_COLORS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette[index % palette.size])
    )
}

@Composable
fun <T> SettingDropdownRow(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    // ★ 各選択肢の左側に表示するプレビュー。指定が無ければ自動で色付き四角になる
    //   （DefaultOptionPreview参照）。呼び出し順序を崩さないよう、
    //   末尾のonSelection(トレーリングラムダ)より前のオプション引数として置く。
    preview: (@Composable (T) -> Unit)? = null,
    onSelection: (T) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: selectedValue.toString()

    SettingRow(label) {
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "選択肢を開く")
        }
    }

    if (showDialog) {
        GenericOptionSelectDialog(
            title = label,
            options = options,
            selectedValue = selectedValue,
            preview = preview,
            onSelect = { onSelection(it) },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(0.4f))
        // Android 12+ ではアプリのテーマ自体がシステムのダイナミックカラーに追従するため
        // （MainActivity.kt の SantopimediaThemeWrapper 参照）、
        // ここは MaterialTheme.colorScheme に素直に従う標準の Switch でよい。
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors()
        )
    }
}

@Composable
fun SettingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 14.sp)
        Slider(value = value, onValueChange = onValueChange,
            valueRange = valueRange, steps = steps, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TextStyleSetting(
    sectionLabel: String,
    fontSize: Int,
    fontWeight: Int,
    onFontSizeChange: (Int) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sectionLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "リセット", modifier = Modifier.size(18.dp))
            }
        }
        Text("大きさ（$fontSize）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Slider(value = fontSize.toFloat(), onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 8f..40f, modifier = Modifier.fillMaxWidth())
        Text("太さ（$fontWeight）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Slider(value = fontWeight.toFloat(), onValueChange = { onFontWeightChange((it / 100).toInt() * 100) },
            valueRange = 100f..900f, steps = 7, modifier = Modifier.fillMaxWidth())
    }
}