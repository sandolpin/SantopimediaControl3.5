package com.sandolpin.santopimedia35.settingscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import com.sandolpin.santopimedia35.home.homescreena.CustomSeekBar
import com.sandolpin.santopimedia35.home.homescreena.SeekBarSpec
import com.sandolpin.santopimedia35.home.homescreena.toSeekBarSpec
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定: テキスト・再生画面ページ

// ===== テキスト =====
@Composable
internal fun TextPage(
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
internal fun PlayerPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("再生画面", onBack) {
        SettingSection("シークバー") {
            // ★ 波立ち・太さ・カプセル・未再生・先端装飾などは専用ダイアログにまとめた（全ホーム画面共通）
            var showSeekCustomize by remember { mutableStateOf(false) }
            DialogTriggerRow(
                label = "シークバーのカスタマイズ",
                valueLabel = if (settings.waveSeekBar) "波立ちON" else "",
                onClick = { showSeekCustomize = true }
            )
            if (showSeekCustomize) {
                SeekBarCustomizeDialog(viewModel, settings) { showSeekCustomize = false }
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

// =============================================================================
// シークバーカスタマイズ ダイアログ（全ホーム画面 A/B/C/D 共通の設定）
// -----------------------------------------------------------------------------
// 各選択肢のサムネイルは、本物の CustomSeekBar を小さく描いたもの
// （タップ操作は無効）。現在の設定を土台に、その項目だけ差し替えて見せる。
// =============================================================================
@Composable
private fun SeekBarCustomizeDialog(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    val spec = settings.toSeekBarSpec()
    val barColor = MaterialTheme.colorScheme.primary
    val tileColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("シークバーカスタマイズ", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "閉じる", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                // ===== プレビュー（実際に操作できる）=====
                var previewProgress by remember { mutableStateOf(0.55f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        Text("プレビュー", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        Spacer(Modifier.height(6.dp))
                        CustomSeekBar(
                            progress = previewProgress,
                            onProgressChange = { previewProgress = it },
                            color = barColor,
                            spec = spec,
                            isPlaying = true,
                            durationMs = 240_000L
                        )
                    }
                }

                // ===== ① カプセル（囲む / 囲まない だけ）=====
                SeekSectionTitle("①カプセル")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(true, false).forEach { on ->
                        SeekOptionTile(
                            selected = settings.seekCapsuleEnabled == on,
                            onClick = { viewModel.updateSetting(SettingsKeys.SEEK_CAPSULE_ENABLED, on) },
                            modifier = Modifier.weight(1f)
                        ) {
                            SeekThumb(spec.copy(capsule = on, unplayed = true, wave = false, tip = false), tileColor)
                        }
                    }
                }
                Text("左：カプセルで囲む　右：囲まない", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.padding(top = 4.dp))

                // ===== ② 未再生部分のスタイル（カプセルなしのときの残り部分）=====
                SeekSectionTitle("②未再生部分のスタイル")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("solid", "dashed", "dotted").forEach { st ->
                        SeekOptionTile(
                            selected = settings.seekUnplayedStyle == st,
                            enabled = !settings.seekCapsuleEnabled,
                            onClick = { viewModel.updateSetting(SettingsKeys.SEEK_UNPLAYED_STYLE, st) },
                            modifier = Modifier.weight(1f)
                        ) {
                            SeekThumb(spec.copy(capsule = false, unplayed = true, unplayedStyle = st, wave = false, tip = false), tileColor)
                        }
                    }
                }
                Text("※カプセルで囲まない設定のときだけ使えます（囲まない場合は常に表示）\n※カプセルで囲む設定では「波立たせる」は使えません", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.padding(top = 4.dp))

                // ===== ③ 現在位置のスタイル =====
                SeekSectionTitle("③現在位置のスタイル")
                SettingSwitchRow("波立たせる",
                    checked = settings.waveSeekBar && !settings.seekCapsuleEnabled,
                    enabled = !settings.seekCapsuleEnabled,
                    onCheckedChange = { viewModel.updateSetting(SettingsKeys.WAVE_SEEK_BAR, it) })
                if (settings.waveSeekBar && !settings.seekCapsuleEnabled) {
                    SettingRow(label = "スタイル") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "シンプル", 2 to "複数波").forEach { (v, label) ->
                                FilterChip(selected = settings.waveSeekBarStyle == v,
                                    onClick = { viewModel.updateSetting(SettingsKeys.WAVE_SEEK_BAR_STYLE, v) },
                                    label = { Text(label) })
                            }
                        }
                    }
                }
                SettingSwitchRow("先端の装飾",
                    checked = settings.seekTipEnabled,
                    onCheckedChange = { viewModel.updateSetting(SettingsKeys.SEEK_TIP_ENABLED, it) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("bar", "dot", "arrow").forEach { st ->
                        SeekOptionTile(
                            selected = settings.seekTipStyle == st,
                            enabled = settings.seekTipEnabled,
                            onClick = { viewModel.updateSetting(SettingsKeys.SEEK_TIP_STYLE, st) },
                            modifier = Modifier.weight(1f)
                        ) {
                            SeekThumb(spec.copy(tip = true, tipStyle = st, wave = false), tileColor)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val barOnly = settings.seekTipEnabled && settings.seekTipStyle == "bar"
                Column(modifier = Modifier.alpha(if (barOnly) 1f else 0.4f)) {
                    SettingSliderRow("縦幅の大きさ（縦棒のみ）",
                        settings.seekTipBarScale, 0.5f..2.0f, 5) {
                        if (barOnly) viewModel.updateSetting(SettingsKeys.SEEK_TIP_BAR_SCALE, it)
                    }
                }
                Column(modifier = Modifier.alpha(if (settings.seekTipEnabled) 1f else 0.4f)) {
                    SettingSliderRow("全体的な大きさ（装飾・${"%.1f".format(settings.seekTipSize)}倍）",
                        settings.seekTipSize, 0.5f..2.0f, 5) {
                        if (settings.seekTipEnabled) viewModel.updateSetting(SettingsKeys.SEEK_TIP_SIZE, it)
                    }
                }
                SettingSliderRow("シークバーの太さ（${settings.seekBarThickness}dp）",
                    settings.seekBarThickness.toFloat(), 3f..15f, 11) {
                    viewModel.updateSetting(SettingsKeys.SEEK_BAR_THICKNESS, it.toInt())
                }
                SettingSwitchRow("シークバー調節時に幅を太くする",
                    checked = settings.seekThickenOnDrag,
                    onCheckedChange = { viewModel.updateSetting(SettingsKeys.SEEK_THICKEN_ON_DRAG, it) })
            }
        }
    }
}

@Composable
private fun SeekSectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    Spacer(Modifier.height(6.dp))
}

// 選択肢の枠（選択中はテーマ色の太い枠、無効時は薄く表示）
@Composable
private fun SeekOptionTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(
                if (selected && enabled) 2.dp else 1.dp,
                if (selected && enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// 選択肢の中に描く小さなシークバー（本物の描画処理を流用。操作は無効）
@Composable
private fun SeekThumb(spec: SeekBarSpec, color: androidx.compose.ui.graphics.Color) {
    CustomSeekBar(
        progress = 0.6f,
        onProgressChange = {},
        color = color,
        spec = spec,
        isPlaying = true,
        maxThickness = 6.dp,
        interactive = false
    )
}