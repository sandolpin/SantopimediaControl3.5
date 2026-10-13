package com.sandolpin.santopimedia35.settingscreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定画面 本体: ページ遷移・TOP一覧・バックアップ

// ===== 設定ページ種別 =====
internal enum class SettingPage {
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
internal fun SettingTopPage(viewModel: MediaPlayerViewModel, onNavigate: (SettingPage) -> Unit) {
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