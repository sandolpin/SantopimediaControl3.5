package com.sandolpin.santopimedia35.lyricsscreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*

// 歌詞1行表示: 通常行・カラオケ行（ワイプ）

// 白文字の周りに付けるぼかし縁取り（オフセット0の黒シャドウ=全方向に滲む）
val lyricOutlineShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.18f),
    offset = androidx.compose.ui.geometry.Offset.Zero,
    blurRadius = 8f
)

// ===== 歌詞1行アイテム =====
@Composable
fun LyricLineItem(
    text: String,
    isActive: Boolean,
    isPast: Boolean,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    futureColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    inactiveLight: Boolean = true,
    inactiveScale: Float = 0.98f,       // 非アクティブ歌詞の大きさ(0.97～1.00)
    inactiveBlur: Boolean = false,      // 非アクティブ歌詞をぼかす
    inactiveBlurRadius: Float = 1.5f,   // ぼかし強度(dp): 0=自動（行距離に応じて調整）
    distFromActive: Int = 0,            // アクティブ行からの距離（自動ぼかし用）
    fontFamily: FontFamily? = null,     // カスタムフォント（nullでシステムフォント）
    translationY: Float = 0f,           // graphicsLayer translationY (px)
    isRight: Boolean = false,
    lineWidthFraction: Float = 0.85f,   // テキスト幅の割合 (isRight行なし→0.95f)
    translationYProvider: (() -> Float)? = null,
    textOutline: Boolean = false,       // 白文字+ぼかし縁取り（動的/ぼかし/グラデ/色混ぜ背景用）
    onClick: () -> Unit
) {
    val resolvedInactive = if (inactiveColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor
    val resolvedFuture = if (futureColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else futureColor

    val colorSpec: AnimationSpec<Color> =
        tween(durationMillis = 400, easing = FastOutSlowInEasing)

    val textColor by animateColorAsState(
        targetValue = when {
            isActive -> accentColor
            isPast   -> resolvedInactive
            else     -> resolvedFuture
        },
        animationSpec = colorSpec,
        label = "lyricColor"
    )

    val activeFontWeight = FontWeight(fontWeight)

    // ===== ガクつき防止 =====
    // fontWeight を Light/Normal/Bold と切り替えると文字幅が変わり折り返し位置が変化、
    // LazyColumn がアイテム高さを再計算して下の行を瞬時に押し下げる（ガクっ）。
    // → fontWeight は常に activeFontWeight 固定にしてレイアウト高さを変えない。
    // → 太さの視覚差は color の alpha 差（accentColor vs 薄いグレー）だけで表現する。

    // 非アクティブ時のscale（animateFloatAsStateで滑らかに変化）
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else inactiveScale,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lyricScale"
    )
    // 非アクティブblur量（animateFloatAsStateで滑らかに変化）
    // inactiveBlurRadius == 0f のとき自動モード: 行距離に応じて線形にぼかし強度を上げる
    //   距離0(active)=0dp, 距離1=0.5dp, 距離2=1.0dp, 距離3以上=1.5dp (上限)
    // inactiveBlurRadius > 0f のとき固定モード: 指定値を使用
    val resolvedBlurDp = when {
        isActive -> 0f
        !inactiveBlur -> 0f
        inactiveBlurRadius == 0f -> (distFromActive * 0.5f).coerceIn(0f, 1.5f)
        else -> inactiveBlurRadius
    }
    val targetBlurDp by animateFloatAsState(
        targetValue = resolvedBlurDp,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "lyricBlur"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationYProvider?.invoke() ?: translationY
                // 非アクティブ時のスケール（左端/右端基準）
                scaleX = targetScale
                scaleY = targetScale
                transformOrigin = if (isRight)
                    androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                else
                    androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = if (textOutline && isActive) LocalTextStyle.current.copy(shadow = lyricOutlineShadow)
            else LocalTextStyle.current,
            color = textColor,
            // 常に activeFontWeight 固定 → 高さが変わらずガクつかない
            fontWeight = activeFontWeight,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            softWrap = true,
            textAlign = if (isRight) androidx.compose.ui.text.style.TextAlign.End
            else androidx.compose.ui.text.style.TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth(lineWidthFraction)
                .padding(vertical = 8.dp)
                .then(
                    if (targetBlurDp > 0f)
                        Modifier.blur(targetBlurDp.dp)
                    else
                        Modifier
                )
        )
    }
}


// ===== カラオケ行アイテム（ワイプアニメーション）=====
// 各文字に startMs/endMs が設定されており、現在位置に応じて
// 左→右へ accentColor でワイプ（塗りつぶし）する。
// Canvas で「ベーステキスト（薄色）」と「ワイプ済み（アクセント色）」を重ね描画。
@Composable
fun KaraokeLineItem(
    karaoke: List<KaraokeChar>,
    currentPositionMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    inactiveScale: Float = 0.98f,
    fontFamily: FontFamily? = null,
    translationY: Float = 0f,
    isRight: Boolean = false,
    lineWidthFraction: Float = 0.85f,   // テキスト幅の割合 (isRight行なし→0.95f)
    translationYProvider: (() -> Float)? = null,
    textOutline: Boolean = false,       // 白文字+ぼかし縁取り
    onClick: () -> Unit
) {
    // ===== 滑らかなワイプのための時間補間 =====
    // currentPositionMs は再生位置のスナップショットであり、
    // 設定(seekBarUpdateInterval)によっては0.3〜1秒に1回など粗い間隔でしか更新されない。
    // そのままワイプ進捗の計算に使うと「パッ、パッ」とカクカク進んで見えてしまうため、
    // 「最後に受け取った値」と「それを受け取った実時刻」を記録し、
    // withFrameNanosで毎フレーム経過時間を足し合わせて滑らかな位置を推定する。
    // シーク等でcurrentPositionMsが大きくジャンプした場合は即座にスナップする。
    var lastKnownPositionMs by remember { mutableStateOf(currentPositionMs) }
    var lastKnownRealtimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var interpolatedPositionMs by remember { mutableStateOf(currentPositionMs) }

    LaunchedEffect(currentPositionMs) {
        // 前回の推定値との差が大きい（シーク・曲変更など）場合は即座にスナップ
        val jumpMs = kotlin.math.abs(currentPositionMs - interpolatedPositionMs)
        lastKnownPositionMs = currentPositionMs
        lastKnownRealtimeMs = System.currentTimeMillis()
        if (jumpMs > 400L) {
            interpolatedPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val elapsed = System.currentTimeMillis() - lastKnownRealtimeMs
                interpolatedPositionMs = lastKnownPositionMs + elapsed.coerceAtLeast(0L)
            }
        }
    }

    val resolvedInactive = if (inactiveColor == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor
    val activeFontWeight = FontWeight(fontWeight)

    // スケールアニメーション（LyricLineItemと同じ）
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.0f else inactiveScale,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "karaokeScale"
    )

    // 行全体のカラーアニメーション（非アクティブ↔アクティブ）
    val baseColor by animateColorAsState(
        targetValue = when {
            isPast -> resolvedInactive
            isActive -> resolvedInactive  // ベースは薄色、ワイプでaccentを重ねる
            else -> resolvedInactive
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "karaokeBaseColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.translationY = translationYProvider?.invoke() ?: translationY
                scaleX = targetScale
                scaleY = targetScale
                transformOrigin = if (isRight)
                    androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                else
                    androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // ===== ワイプ描画 =====
        // 1. ベーステキスト（薄色）を描画
        // 2. Canvas で clipRect + drawText によりワイプ済み部分をアクセントカラーで重ね描画
        //
        // ★ LyricLineItem と全く同じレイアウト構造（外側Boxはcontentalignmentのみ、
        //   内側要素にfillMaxWidth(lineWidthFraction)+padding(vertical=8.dp)を直接適用）
        //   に揃えることで、sdlrc通常行とカラオケ行の行間の差異を解消する。
        //   以前は内側にも独立したBox+contentAlignmentがあり、二重のアラインメント計算で
        //   高さの算出結果がわずかにズレていた。
        val textMeasurer = rememberTextMeasurer()
        val fullText = karaoke.joinToString("") { it.char }
        val textStyle = androidx.compose.ui.text.TextStyle(
            fontWeight = activeFontWeight,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(lineWidthFraction)
                .padding(vertical = 8.dp)
        ) {
            // ベース（薄色）テキスト（非アクティブは縁取りなし。縁取りの黒が透けて灰色になるため）
            Text(
                text = fullText,
                style = textStyle,
                color = baseColor,
                softWrap = true,
                textAlign = if (isRight) androidx.compose.ui.text.style.TextAlign.End
                else androidx.compose.ui.text.style.TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            // ワイプ済み部分を Canvas で重ねる
            Canvas(modifier = Modifier.matchParentSize()) {
                // 右寄せの場合は全体幅から開始X座標を算出
                val totalLayout = textMeasurer.measure(
                    text = fullText,
                    style = textStyle,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = size.width.toInt())
                )
                var charX = if (isRight) {
                    size.width - totalLayout.size.width.toFloat()
                } else 0f

                karaoke.forEachIndexed { i, k ->
                    val charLayout = textMeasurer.measure(
                        text = k.char,
                        style = textStyle
                    )
                    val charWidth = charLayout.size.width.toFloat()
                    // 現在位置はフレームごとに更新されるため、進捗計算もCompositionではなく
                    // Canvas描画時に行い、歌詞行全体の再コンポーズを避ける。
                    val wipeProgress = when {
                        interpolatedPositionMs < k.startMs -> 0f
                        k.endMs > k.startMs && interpolatedPositionMs < k.endMs ->
                            (interpolatedPositionMs - k.startMs).toFloat() /
                                    (k.endMs - k.startMs).toFloat()
                        else -> if (isPast || interpolatedPositionMs >= k.endMs) 1f else 0f
                    }
                    val wipeWidth = charWidth * wipeProgress

                    if (wipeWidth > 0f) {
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(
                            androidx.compose.ui.geometry.Rect(
                                left   = charX,
                                top    = 0f,
                                right  = charX + wipeWidth,
                                bottom = size.height
                            )
                        )
                        // drawText(TextMeasurer, ...) 形式で描画
                        drawText(
                            textMeasurer = textMeasurer,
                            text         = k.char,
                            topLeft      = androidx.compose.ui.geometry.Offset(charX, 0f),
                            style        = if (textOutline) textStyle.copy(color = accentColor, shadow = lyricOutlineShadow)
                            else textStyle.copy(color = accentColor)
                        )
                        drawContext.canvas.restore()
                    }
                    charX += charWidth
                }
            }
        }
    }
}