package com.sandolpin.santopimedia35.mainactivity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.settingscreen.*

// ナビゲーションバーのショートカットパネル（背景・UIスタイルのクイック切替）

// =============================================================================
// ナビゲーションバー下のショートカットパネル
// =============================================================================
// ピル型ナビゲーションバーの上矢印(∧)をタップすると展開する簡易設定パネル。
// 「設定」画面まで潜らなくても、よく変える3項目（背景スタイル・ホーム画面の
// スタイル・ナビゲーションバー自体の背景）をワンタップで切り替えられるようにする。
// 実体は SettingScreen.kt の同名設定と同じ DataStore キーを更新するだけなので、
// ここでの変更は設定画面側にも即座に反映される（逆も同様）。
@Composable
internal fun NavShortcutPanel(
    viewModel: MediaPlayerViewModel,
    settings: com.sandolpin.santopimedia35.database.AppSettings,
    accentColor: Color
) {
    // ★ パネルの背景は白/黒、テキストは背景と反対の黒/白に固定する
    //   （ライト=白背景+黒文字、ダーク=黒背景+白文字）。
    //   選択中のチップだけはアクセントカラーで塗りつぶしたままにする。
    val isDark = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val panelBg = if (isDark) Color.Black else Color.White
    val panelFg = if (isDark) Color.White else Color.Black
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = panelBg,
        contentColor = panelFg,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                // ★ 横画面のVerticalNavBarから開いたときは表示できる高さが限られるため、
                //   収まりきらない場合にクリップされず操作できるよう保険でスクロール対応する
                //   （縦画面の水平ピルから開く場合は通常収まるため、実質スクロールは発生しない）。
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "ショートカット",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Text(
                "背景スタイル",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = panelFg.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            NavShortcutChipRow(
                options = listOf(
                    "default"  to "デフォルト",
                    "gradient" to "グラデーション",
                    "blur"     to "ぼかし",
                    "color_mix" to "色混ぜ",
                    "animated" to "動的アニメーション"
                ),
                selected = settings.backgroundStyle,
                accentColor = accentColor,
                unselectedColor = panelFg,
                onSelect = { viewModel.updateSetting(SettingsKeys.BACKGROUND_STYLE, it) }
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "ホーム画面のスタイル",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = panelFg.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            NavShortcutChipRow(
                options = listOf("A" to "UI-A", "B" to "UI-B", "C" to "UI-C", "D" to "UI-D"),
                selected = settings.playerUiStyle,
                accentColor = accentColor,
                unselectedColor = panelFg,
                onSelect = { viewModel.updateSetting(SettingsKeys.PLAYER_UI_STYLE, it) }
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "ナビゲーションバーの背景",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = panelFg.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            NavShortcutChipRow(
                options = listOf("accent" to "アクセントカラー", "solid" to "単色"),
                selected = settings.navBarBackgroundStyle,
                accentColor = accentColor,
                unselectedColor = panelFg,
                onSelect = { viewModel.updateSetting(SettingsKeys.NAV_BAR_BACKGROUND_STYLE, it) }
            )
        }
    }
}

// チップの折り返し行（画面幅に応じて自動的に次の行へ折り返す）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavShortcutChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    accentColor: Color,
    unselectedColor: Color,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            NavShortcutChip(
                label = label,
                selected = selected == value,
                accentColor = accentColor,
                unselectedColor = unselectedColor,
                onClick = { onSelect(value) }
            )
        }
    }
}

// 1個のチップ（未選択=枠線のみ、選択=accentColorで塗りつぶし）
@Composable
private fun NavShortcutChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accentColor else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (selected) Color.Transparent else unselectedColor.copy(alpha = 0.45f)
        )
    ) {
        Text(
            label,
            color = if (selected) Color.White else unselectedColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}