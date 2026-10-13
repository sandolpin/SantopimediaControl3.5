package com.sandolpin.santopimedia35.settingscreen

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.lyricsscreen.*

// 設定: 背景スタイル・色の混ぜ方のプレビューとダイアログ

// ★ 色抽出パターン（colorExtractPattern）ダイアログのプレビュー用。
//   MediaPlayerViewModel.adjustColor() と同じ考え方の色補正を簡易再現したもの。
//   ViewModel側は private かつ ViewModel インスタンスに紐づく処理のため、
//   設定画面から直接は呼べない。完全に同じ結果を保証する必要は無く
//   （実際の抽出色は再生中のアルバムアート次第で常に変わるため）、
//   「パターンごとの色の傾向」が伝わることを目的とした近似実装。
internal fun previewAdjustColor(base: Color, pattern: Int): Color {
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
internal fun backgroundStyleLabel(value: String): String = when (value) {
    "default"   -> "デフォルト"
    "gradient"  -> "グラデーション"
    "blur"      -> "ぼかし"
    "color_mix" -> "色混ぜ"
    "animated"  -> "動的アニメーション"
    else        -> value
}

internal fun colorPatternLabel(value: String): String = when (value) {
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
internal fun BackgroundStyleThumbnail(
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
                    com.sandolpin.santopimedia35.home.homescreena.AnimatedMeshBackground(
                        color1 = animatedBgColor,
                        darkness = settings.animatedBgDarkness,
                        colorPattern = settings.animatedColorPattern,
                        isDarkTheme = isDark,
                        explicitColors = com.sandolpin.santopimedia35.home.explicitMeshColorsOrNull(settings, mediaState),
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
            com.sandolpin.santopimedia35.home.homescreena.AnimatedMeshBackground(
                color1 = animatedBgColor,
                darkness = settings.animatedBgDarkness,
                colorPattern = pattern,
                isDarkTheme = isDark,
                explicitColors = com.sandolpin.santopimedia35.home.explicitMeshColorsOrNull(settings, mediaState),
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
internal fun DialogTriggerRow(label: String, valueLabel: String, onClick: () -> Unit) {
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
internal fun BackgroundStyleDialog(
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
internal fun ColorPatternDialog(
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
internal fun BackgroundPreviewDialog(
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