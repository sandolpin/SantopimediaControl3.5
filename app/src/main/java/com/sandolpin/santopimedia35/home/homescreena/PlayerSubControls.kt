package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sandolpin.santopimedia35.QueueRepeatMode
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.*

// UI-A 部品: サブコントロール・ショートカット・音量バー・バイブレーション

// ===== サブコントロール（歌詞・音量・リピート）=====
@Composable
fun SubControlsA(
    mediaState: MediaState,
    repeatMode: QueueRepeatMode,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    shortcutLeft: String,
    shortcutRight: String,
    onShowLyrics: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowHistory: () -> Unit,
    onShare: () -> Unit,
    accentColor: Color,
    iconColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
    textColor: Color = Color.Unspecified,
) {
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground
    val activeColor = accentColor

    // ★ 音量バー行の「下」に音声出力先を表示するため、全体をColumnで包む。
    //   横画面(HomeScreenALandscapeContent)でも同じSubControlsAを呼んでいるため、
    //   ここを直せば縦画面・横画面どちらにも自動で反映される。
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ===== 左ショートカット（設定で選んだ機能）=====
            HomeShortcutButton(
                type = shortcutLeft,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                iconTint = iconTint,
                activeColor = activeColor,
                onShowLyrics = onShowLyrics,
                onRepeat = onRepeat,
                onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite,
                onShowHistory = onShowHistory,
                onShare = onShare
            )

            // ===== 音量スライダー（CustomVolumeBar）=====
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.VolumeDown, null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                var isDragging by remember { mutableStateOf(false) }
                CustomVolumeBar(
                    progress    = mediaState.volume,
                    isActive    = isDragging,
                    accentColor = accentColor,
                    dotColor    = accentColor.copy(alpha = 0.45f),
                    trackColor  = accentColor.copy(alpha = 0.20f),
                    modifier    = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart  = { isDragging = true },
                                onDragEnd    = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onVolumeChange((change.position.x / size.width).coerceIn(0f, 1f))
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                onVolumeChange((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.VolumeUp, null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint.copy(alpha = 0.6f))
            }

            // ===== 右ショートカット（設定で選んだ機能）=====
            HomeShortcutButton(
                type = shortcutRight,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                isFavorite = isFavorite,
                iconTint = iconTint,
                activeColor = activeColor,
                onShowLyrics = onShowLyrics,
                onRepeat = onRepeat,
                onShuffle = onShuffle,
                onToggleFavorite = onToggleFavorite,
                onShowHistory = onShowHistory,
                onShare = onShare
            )
        }

        // ===== 音の出力先（スピーカー / ヘッドホン / Bluetooth(接続名) / USB-DAC(接続名)）=====
        Spacer(Modifier.height(6.dp))
        AudioOutputIndicator(tint = iconTint.copy(alpha = 0.55f))
    }
}

// ===== ホーム画面ショートカットボタン =====
// 設定(homeShortcutLeft/Right)で選ばれた種類に応じてアイコン・挙動を出し分ける。
// 選択肢: "lyrics" / "favorite" / "history" / "share" / "repeat" / "shuffle"
// 未知の値やnullは "lyrics"（歌詞）にフォールバックする。
@Composable
private fun HomeShortcutButton(
    type: String,
    repeatMode: QueueRepeatMode,
    isShuffleEnabled: Boolean,
    isFavorite: Boolean,
    iconTint: Color,
    activeColor: Color,
    onShowLyrics: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowHistory: () -> Unit,
    onShare: () -> Unit,
) {
    when (type) {
        "favorite" -> IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "お気に入り",
                tint = if (isFavorite) activeColor else iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "history" -> IconButton(onClick = onShowHistory, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = "履歴",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "share" -> IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "シェア",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "repeat" -> IconButton(onClick = onRepeat, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = when (repeatMode) {
                    QueueRepeatMode.ONE  -> Icons.Rounded.RepeatOne
                    else                 -> Icons.Rounded.Repeat
                },
                contentDescription = "リピート",
                tint = if (repeatMode != QueueRepeatMode.NONE) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        "shuffle" -> IconButton(onClick = onShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "シャッフル",
                tint = if (isShuffleEnabled) activeColor else iconTint.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        else -> IconButton(onClick = onShowLyrics, modifier = Modifier.size(48.dp)) { // "lyrics" がデフォルト
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = "歌詞",
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ===== カスタム音量バー（資料.txtより）=====
@Composable
fun CustomVolumeBar(
    progress: Float,
    isActive: Boolean,
    accentColor: Color,
    dotColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedHeight by animateDpAsState(
        targetValue = if (isActive) 28.dp else 18.dp,
        animationSpec = tween(200),
        label = "volHeight"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(animatedHeight)) {
        val p = 4.dp.toPx()
        val rad = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, size = size, cornerRadius = rad)
        if (progress > 0) drawRoundRect(
            color = accentColor,
            topLeft = Offset(p, p),
            size = Size((size.width - p * 2) * progress, size.height - p * 2),
            cornerRadius = CornerRadius((size.height - p * 2) / 2)
        )
        val dotRadius = 2.dp.toPx()
        val spacing = 12.dp.toPx()
        var currentX = p + spacing
        while (currentX < size.width - p) {
            if (currentX > ((size.width - p * 2) * progress) + p + dotRadius) {
                drawCircle(color = dotColor, radius = dotRadius, center = Offset(currentX, size.height / 2))
            }
            currentX += spacing
        }
    }
}

// ===== 音量スライダー（CustomVolumeBar使用）=====
@Composable
fun VolumeSlider(volume: Float, onVolumeChange: (Float) -> Unit, color: Color) {
    var isDragging by remember { mutableStateOf(false) }
    val trackColor = color.copy(alpha = 0.20f)
    val dotColor   = color.copy(alpha = 0.45f)

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeDown, null, modifier = Modifier.size(18.dp),
            tint = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.width(8.dp))
        CustomVolumeBar(
            progress    = volume,
            isActive    = isDragging,
            accentColor = color,
            dotColor    = dotColor,
            trackColor  = trackColor,
            modifier    = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart  = { isDragging = true },
                        onDragEnd    = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            onVolumeChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onVolumeChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Rounded.VolumeUp, null, modifier = Modifier.size(18.dp),
            tint = color.copy(alpha = 0.7f))
    }
}

// ===== バイブレーション =====
@Suppress("DEPRECATION")
fun vibrateIfEnabled(context: android.content.Context, durationMs: Int) {
    if (durationMs <= 0) return
    val vibrator: android.os.Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    }
    vibrator ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(
            durationMs.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        vibrator.vibrate(durationMs.toLong())
    }
}