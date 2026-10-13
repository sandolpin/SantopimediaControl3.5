package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sandolpin.santopimedia35.home.*

// UI-A 部品: アルバムアート・曲情報・空間オーディオバッジ・再生コントロール

// ===== アルバムアート =====
@Composable
fun AlbumArtCard(
    uri: String?,
    bitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier,
    shadowColor: Color = Color.Black
) {
    val shape = RoundedCornerShape(20.dp)
    if (uri != null || bitmap != null) {
        // 外Box: shadowのみ — clipと分離することで影が切り取られない
        Box(
            modifier = modifier.shadow(
                elevation    = 28.dp,
                shape        = shape,
                ambientColor = shadowColor.copy(alpha = 0.45f),
                spotColor    = shadowColor.copy(alpha = 0.75f)
            )
        ) {
            // 内Box: clip + 白縁取り6dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(width = 6.dp, color = Color.White.copy(alpha = 0.85f), shape = shape)
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier.background(Color.Gray.copy(alpha = 0.3f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        }
    }
}

// ===== 曲情報 =====
@Composable
fun TrackInfo(
    title: String, artist: String, album: String,
    titleFontSize: TextUnit, titleFontWeight: FontWeight,
    artistFontSize: TextUnit, artistFontWeight: FontWeight,
    albumFontSize: TextUnit, albumFontWeight: FontWeight,
    scrollTitle: Boolean = false,
    textColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
    // ★ HomeScreenC⇔LyricsScreen間のsharedElementTransition用。
    //   デフォルトはModifier（何もしない）のため、他の呼び出し元は無変更で動作する。
    titleModifier: Modifier = Modifier,
    artistModifier: Modifier = Modifier,
) {
    // ぼかし背景時はテキストに影を付ける
    // ただし dark_mode + ライトモードは白オーバーレイ上なのでシャドウ不要
    val isDarkBlur = isBlurBg && !(textColor == Color.Black)
    val shadowStyle: androidx.compose.ui.text.TextStyle = if (isDarkBlur) {
        androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.6f),
                offset = Offset(0f, 2f),
                blurRadius = 6f
            )
        )
    } else {
        androidx.compose.ui.text.TextStyle.Default
    }

    val resolvedTitleColor = if (textColor == Color.Unspecified)
        MaterialTheme.colorScheme.onBackground else textColor
    // textColor から派生させることで dark_mode+ライト(黒)の場合も黒になる
    val resolvedSubColor = if (textColor != Color.Unspecified)
        textColor.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    val resolvedAlbumColor = if (textColor != Color.Unspecified)
        textColor.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (scrollTitle) {
            // ===== ループスクロール（末尾で一時停止→再スタート）=====
            val offsetX = remember(title) { Animatable(0f) }
            var containerWidthPx by remember { mutableStateOf(0) }
            var textWidthPx by remember(title) { mutableStateOf(0) }
            val gapDp = 60.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val gapPx = with(density) { gapDp.toPx() }

            LaunchedEffect(title, textWidthPx, containerWidthPx) {
                offsetX.snapTo(0f)
                if (textWidthPx <= containerWidthPx || textWidthPx <= 0) return@LaunchedEffect
                val unitPx = textWidthPx + gapPx
                val durationMs = (unitPx * 14f).toInt().coerceIn(3000, 12000)
                kotlinx.coroutines.delay(1000)
                while (true) {
                    // 末尾までスクロール
                    offsetX.animateTo(
                        targetValue = -unitPx,
                        animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                    )
                    // 末尾で1.5秒停止
                    kotlinx.coroutines.delay(1500)
                    // 先頭に瞬時リセット
                    offsetX.snapTo(0f)
                    // 先頭でも1秒停止してから再スタート
                    kotlinx.coroutines.delay(1000)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .onGloballyPositioned { containerWidthPx = it.size.width }
                    .then(titleModifier)
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = offsetX.value },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.ifEmpty { "タイトル不明" },
                        fontSize = titleFontSize,
                        fontWeight = titleFontWeight,
                        color = resolvedTitleColor,
                        maxLines = 1,
                        softWrap = false,
                        style = shadowStyle,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            if (coords.size.width != textWidthPx) textWidthPx = coords.size.width
                        }
                    )
                    if (textWidthPx > containerWidthPx && containerWidthPx > 0) {
                        Spacer(modifier = Modifier.width(gapDp))
                        Text(
                            text = title.ifEmpty { "タイトル不明" },
                            fontSize = titleFontSize,
                            fontWeight = titleFontWeight,
                            color = resolvedTitleColor,
                            maxLines = 1,
                            softWrap = false,
                            style = shadowStyle,
                        )
                    }
                }
            }
        } else {
            Text(
                title.ifEmpty { "タイトル不明" },
                fontSize = titleFontSize,
                fontWeight = titleFontWeight,
                color = resolvedTitleColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = shadowStyle,
                modifier = titleModifier,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            artist.ifEmpty { "アーティスト不明" },
            fontSize = artistFontSize,
            fontWeight = artistFontWeight,
            color = resolvedSubColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = shadowStyle,
            modifier = artistModifier,
        )
        if (album.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                album,
                fontSize = albumFontSize,
                fontWeight = albumFontWeight,
                color = resolvedAlbumColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = shadowStyle,
            )
        }
    }
}

// ===== 空間オーディオバッジ =====
@Composable
fun SpatialAudioBadge(isBlurBg: Boolean = false) {
    val bgColor = if (isBlurBg)
        Color.White.copy(alpha = 0.18f)
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isBlurBg)
        Color.White
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = "空間オーディオ",
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "空間オーディオ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

// ===== 長押しスケールアニメーション付きボタン =====
@Composable
private fun PressScaleButton(
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onTap?.invoke() },
                    onLongPress = {
                        isPressed = false
                        onLongPress?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ===== コントロールボタン UI-A =====
@Composable
fun PlayerControlsA(
    isPlaying: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit,
    onPlayPause: () -> Unit, onRewind: () -> Unit, onFastForward: () -> Unit,
    onLongPressPlay: () -> Unit,
    buttonColor: Color, vibrationMs: Int,
    iconColor: Color = Color.Unspecified,
    // 再生ボタンのアイコン色（背景と同色になる場合に個別指定するため分離）
    // デフォルトは Color.Unspecified → 内部で Color.White にフォールバック（従来動作）
    playIconColor: Color = Color.Unspecified,
    isBlurBg: Boolean = false,
) {
    val context = LocalContext.current
    val iconTint = if (iconColor != Color.Unspecified) iconColor
    else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ⏮ 前の曲
        PressScaleButton(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPrev() }
        ) {
            Icon(Icons.Rounded.SkipPrevious, "前の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }

        // ↺ 10秒戻し（薄い丸背景）
        PressScaleButton(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = RoundedCornerShape(50)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onRewind() }
        ) {
            Icon(Icons.Rounded.Replay10, "10秒戻し", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        // ⏸/▶ 再生・一時停止（大きめ・角丸正方形）長押しでアクションメニュー
        // playIconColor が指定されていればそれを使う（白ボタンに白アイコンを避けるため）
        // 未指定（Unspecified）の場合は従来通り白
        val resolvedPlayIconColor = if (playIconColor != Color.Unspecified) playIconColor else Color.White
        PressScaleButton(
            modifier = Modifier
                .size(80.dp)
                .background(buttonColor, shape = RoundedCornerShape(24.dp)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onPlayPause() },
            onLongPress = { vibrateIfEnabled(context, vibrationMs); onLongPressPlay() }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                tint = resolvedPlayIconColor,
                modifier = Modifier.size(44.dp)
            )
        }

        // ↻ 10秒送り（薄い丸背景）
        PressScaleButton(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor.copy(alpha = 0.15f), shape = RoundedCornerShape(50)),
            onTap = { vibrateIfEnabled(context, vibrationMs); onFastForward() }
        ) {
            Icon(Icons.Rounded.Forward10, "10秒送り", modifier = Modifier.size(28.dp), tint = iconTint)
        }

        // ⏭ 次の曲
        PressScaleButton(
            modifier = Modifier.size(48.dp),
            onTap = { vibrateIfEnabled(context, vibrationMs); onNext() }
        ) {
            Icon(Icons.Rounded.SkipNext, "次の曲", modifier = Modifier.size(34.dp), tint = iconTint)
        }
    }
}