package com.sandolpin.santopimedia35.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.MediaPlayerViewModel
import com.sandolpin.santopimedia35.database.AppSettings

/**
 * UI-B: ミニマルなPlayerScreen
 * - 画面幅いっぱいのアルバムアート（大きな角丸）
 * - フラットなコントロールボタン（背景なし）
 * - よりコンパクトなレイアウト
 */
@Composable
fun HomeScreenB(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onLongPressPlay: () -> Unit
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // ぼかし背景
        if (settings.backgroundStyle == "blur" && mediaState.albumArtUri != null) {
            AsyncImage(
                model = mediaState.albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(30.dp),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }

        val backgroundModifier = when (settings.backgroundStyle) {
            "gradient" -> Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        mediaState.dominantColor.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            else -> Modifier.background(MaterialTheme.colorScheme.background)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (settings.backgroundStyle != "blur") backgroundModifier else Modifier)
                .padding(horizontal = 16.dp)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // アルバムアート（UI-Bは角丸大きめ・画面幅いっぱい）
            if (mediaState.albumArtUri != null) {
                AsyncImage(
                    model = mediaState.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 波形（表示設定ONのとき）
            if (settings.showWaveform) {
                WaveformAnimation(
                    isPlaying = mediaState.isPlaying,
                    color = if (settings.extractColorFromArt) mediaState.dominantColor
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(20.dp).fillMaxWidth(0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // シークバー（UI-Bは波状非対応 → 通常のみ）
            NormalSeekBar(
                progress = mediaState.progress,
                onProgressChange = { viewModel.seekTo(it) },
                thickness = settings.seekBarThickness.dp,
                color = if (settings.extractColorFromArt) mediaState.dominantColor
                else MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mediaState.currentPositionStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "-${mediaState.remainingTimeStr}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 曲名・アーティスト
            TrackInfo(
                title = mediaState.title,
                artist = mediaState.artist,
                album = mediaState.album,
                titleFontSize = settings.titleFontSize.sp,
                titleFontWeight = FontWeight(settings.titleFontWeight),
                artistFontSize = settings.artistFontSize.sp,
                artistFontWeight = FontWeight(settings.artistFontWeight),
                albumFontSize = settings.albumFontSize.sp,
                albumFontWeight = FontWeight(settings.albumFontWeight)
            )

            Spacer(modifier = Modifier.weight(1f))

            // コントロールボタン UI-B（フラット・背景なし）
            PlayerControlsB(
                isPlaying = mediaState.isPlaying,
                onPrev = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() },
                onPlayPause = { viewModel.togglePlayPause() },
                onRewind = { viewModel.seekRelative(-10000L) },
                onFastForward = { viewModel.seekRelative(10000L) },
                onLongPressPlay = onLongPressPlay,
                buttonColor = if (settings.extractColorFromArt) mediaState.dominantColor
                else MaterialTheme.colorScheme.primary,
                vibrationMs = if (settings.vibrationEnabled) settings.vibrationStrength else 0
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 音量スライダー
            if (settings.showVolumeBar) {
                VolumeSlider(
                    volume = mediaState.volume,
                    onVolumeChange = { viewModel.setVolume(it) },
                    color = if (settings.extractColorFromArt) mediaState.dominantColor
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ===== コントロールボタン UI-B（フラット + Rounded）=====
@Composable
fun PlayerControlsB(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onLongPressPlay: () -> Unit,
    buttonColor: Color,
    vibrationMs: Int
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vibrateIfEnabled(context, vibrationMs); onPrev() }) {
            Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onBackground)
        }
        // 10秒戻し（薄い丸背景）
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { vibrateIfEnabled(context, vibrationMs); onRewind() })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Replay10, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onBackground)
        }

        // 再生・停止ボタン（大きめ・フラット）
        Box(
            modifier = Modifier
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { vibrateIfEnabled(context, vibrationMs); onPlayPause() },
                        onLongPress = { vibrateIfEnabled(context, vibrationMs); onLongPressPlay() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = buttonColor,
                modifier = Modifier.size(72.dp)
            )
        }

        // 10秒送り（薄い丸背景）
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { vibrateIfEnabled(context, vibrationMs); onFastForward() })
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Forward10, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onBackground)
        }

        IconButton(onClick = { vibrateIfEnabled(context, vibrationMs); onNext() }) {
            Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}