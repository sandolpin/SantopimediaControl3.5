package com.sandolpin.santopimedia35.settingscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定: 外観ページ

// ===== 外観 =====
@Composable
internal fun AppearancePage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
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
                    listOf("A", "B", "C", "D").forEach { style ->
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
                if (settings.animatedColorSource == "album_art") {
                    Text(
                        "ジャケットで一番広い色を下地にして、青・赤など目立つ色をアクセントとして混ぜます。背景が明るいときは文字・アイコンが自動で濃い色になります",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                    // ★ 色の抽出パターン（5種類）。切り替えは即時反映（5パターンとも曲の読み込み時に計算済み）
                    SettingRow(label = "色の抽出パターン") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                1 to "おまかせ（下地＋アクセント）",
                                2 to "鮮やか（目立つ色だけ）",
                                3 to "そのまま（面積の大きい3色）",
                                4 to "淡い（パステル調）",
                                5 to "深い（暗め・落ち着き）"
                            ).forEach { (v, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = settings.animatedExtractPattern == v,
                                        onClick = { viewModel.updateSetting(SettingsKeys.ANIMATED_EXTRACT_PATTERN, v) }
                                    )
                                    Text(label, fontSize = 13.sp)
                                }
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

                // ===== 背景の色の濃さ（ライトモード用）=====
                SettingSliderRow(
                    "背景の色の濃さ（${(settings.animatedBgDarkness * 100).toInt()}%）",
                    settings.animatedBgDarkness, 0.6f..1.0f, 7
                ) { viewModel.updateSetting(SettingsKeys.ANIMATED_BG_DARKNESS, it) }
                Text(
                    "低いほど暗く落ち着いた背景になります（ライトモード時）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )

                Spacer(Modifier.height(4.dp))

                // ===== 背景の色の濃さ（ダークモード専用）=====
                // ★ 以前はダークモード時に自動で×0.75倍暗くしていたが、
                //   ユーザーが独立して調整できるよう専用スライダーに変更した。
                SettingSliderRow(
                    "ダークモード時の背景の色の濃さ（${(settings.animatedBgDarknessDark * 100).toInt()}%）",
                    settings.animatedBgDarknessDark, 0.25f..1.0f, 14
                ) { viewModel.updateSetting(SettingsKeys.ANIMATED_BG_DARKNESS_DARK, it) }
                Text(
                    "ダークモードのときはこちらの値が使われます（上のスライダーとは独立）",
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
        SettingSection("背景装飾") {
            Text(
                "背景スタイルとは別に、どの背景の上にも重ねられる装飾を追加できます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow(
                label = "装飾の種類",
                selectedValue = settings.bgDecorationType,
                options = listOf(
                    "none"          to "なし",
                    "dot_gradient"  to "ドットグラデーション",
                    "polka_dot"     to "水玉",
                    "random_shapes" to "ランダム図形"
                )
            ) {
                viewModel.updateSetting(SettingsKeys.BG_DECORATION_TYPE, it)
            }
            if (settings.bgDecorationType != "none") {
                Spacer(Modifier.height(4.dp))
                SettingRow(label = "装飾の色") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("accent" to "アクセントカラー", "white" to "白固定").forEach { (v, label) ->
                            FilterChip(
                                selected = settings.bgDecorationColor == v,
                                onClick = { viewModel.updateSetting(SettingsKeys.BG_DECORATION_COLOR, v) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                SettingSliderRow(
                    "不透明度（${(settings.bgDecorationOpacity * 100).toInt()}%）",
                    settings.bgDecorationOpacity, 0.1f..1.0f, 8
                ) { viewModel.updateSetting(SettingsKeys.BG_DECORATION_OPACITY, it) }
                SettingSliderRow(
                    "大きさ（${"%.1f".format(settings.bgDecorationSize)}倍）",
                    settings.bgDecorationSize, 0.5f..2.0f, 5
                ) { viewModel.updateSetting(SettingsKeys.BG_DECORATION_SIZE, it) }
            }
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