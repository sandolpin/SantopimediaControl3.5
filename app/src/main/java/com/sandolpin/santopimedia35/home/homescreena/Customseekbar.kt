package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.database.AppSettings
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// =============================================================================
// カスタムシークバー（全ホーム画面 A/B/C/D 共通）
// -----------------------------------------------------------------------------
// 設定「再生画面 → シークバーのカスタマイズ」の内容をすべて反映して描画する。
//
//  ① カプセル        : バーをカプセルで囲むか囲まないかだけ（バーの太さは変わらない）
//  ② 未再生部分      : カプセルなしのときの残り部分の 表示ON/OFF と 見た目(実線/破線/点線)
//  ③ 現在位置        : 波立たせる(シンプル/複数波) ・ 先端の装飾(縦棒/丸/矢印)
//  その他            : シークバーの太さ・装飾の全体的な大きさ・調整中に太くする
// =============================================================================

/** シークバーの見た目設定（AppSettingsから作る。プレビュー用に copy で一部だけ変えて使う） */
data class SeekBarSpec(
    val capsule: Boolean = true,
    val unplayed: Boolean = true,
    val unplayedStyle: String = "dashed", // "solid" / "dashed" / "dotted"
    val wave: Boolean = false,
    val waveStyle: Int = 1,              // 1=シンプル / 2=複数波
    val tip: Boolean = false,
    val tipStyle: String = "bar",        // "bar" / "dot" / "arrow"
    val tipBarScale: Float = 1.0f,       // 縦棒の縦幅倍率
    val tipSize: Float = 1.0f,           // 装飾の全体的な大きさ(倍率)
    val thickness: Int = 4,              // シークバーの太さ(dp)
    val thickenOnDrag: Boolean = true,
)

fun AppSettings.toSeekBarSpec(): SeekBarSpec = SeekBarSpec(
    capsule = seekCapsuleEnabled,
    unplayed = true,   // 未再生部分は常に表示（カプセルなし時）
    unplayedStyle = seekUnplayedStyle,
    // カプセルで囲む設定のときは波立たせない
    wave = waveSeekBar && !seekCapsuleEnabled,
    waveStyle = waveSeekBarStyle,
    tip = seekTipEnabled,
    tipStyle = seekTipStyle,
    tipBarScale = seekTipBarScale,
    tipSize = seekTipSize,
    thickness = seekBarThickness,
    thickenOnDrag = seekThickenOnDrag,
)

@Composable
fun CustomSeekBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    color: Color,
    spec: SeekBarSpec,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    durationMs: Long = 0L,
    // 小さいボックス(UI-Cのペイン等)で太さの上限を設けたいときに指定
    maxThickness: Dp = Dp.Unspecified,
    // false のときはタップ/ドラッグ・時間吹き出しを無効にする（設定画面のサムネイル用）
    interactive: Boolean = true,
) {
    val baseThickness: Dp =
        if (maxThickness != Dp.Unspecified) min(spec.thickness.toFloat(), maxThickness.value).dp
        else spec.thickness.dp

    // ===== 波のアニメーション位相（描画ブロック内で読む＝毎フレーム再コンポーズしない）=====
    val infinite = rememberInfiniteTransition(label = "customSeek")
    val phase1 = infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "phase1"
    )
    val phase2 = infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "phase2"
    )
    // 停止中は波の振幅を滑らかに0へ
    val amplitudeScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "ampScale"
    )

    // 縦棒の装飾が切れない高さ（太さ・縦幅・大きさから計算）
    val tipHeightDp: Dp =
        if (spec.tip && spec.tipStyle == "bar")
            maxOf(baseThickness * 7f * spec.tipBarScale, baseThickness * 2.4f) * spec.tipSize * 0.5f + 2.dp
        else 0.dp

    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(300, easing = FastOutSlowInEasing),
        label = "customSeekProgress"
    )
    // 調整中に太くする
    val thickenScale by animateFloatAsState(
        targetValue = if (isDraggingSeek && spec.thickenOnDrag) 1.5f else 1f,
        animationSpec = tween(150),
        label = "thickenScale"
    )

    val currentOnChange by rememberUpdatedState(onProgressChange)

    Column(modifier = modifier.fillMaxWidth()) {
        // ===== ドラッグ中の時間吹き出し =====
        if (interactive) {
            AnimatedVisibility(
                visible = isDraggingSeek && durationMs > 0,
                enter = fadeIn(tween(150)) + scaleIn(
                    initialScale = 0.85f, animationSpec = tween(150),
                    transformOrigin = TransformOrigin(0.5f, 1f)
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    targetScale = 0.85f, animationSpec = tween(120),
                    transformOrigin = TransformOrigin(0.5f, 1f)
                )
            ) {
                val posMs = (durationMs * lastKnownDragProgress).toLong()
                val posStr = "%d:%02d".format(posMs / 60000, (posMs % 60000) / 1000)
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = (lastKnownDragProgress * 300).dp.coerceIn(0.dp, 280.dp))
                    ) {
                        Text(
                            posStr,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, softWrap = false
                        )
                    }
                }
            }
        }

        val gestureModifier = if (!interactive) Modifier else Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDraggingSeek = true },
                    onDragEnd = {
                        if (dragProgress >= 0f) currentOnChange(dragProgress)
                        isDraggingSeek = false; dragProgress = -1f
                    },
                    onDragCancel = { isDraggingSeek = false; dragProgress = -1f },
                    onDrag = { change: PointerInputChange, _: Offset ->
                        change.consume()
                        dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        currentOnChange(dragProgress)
                    }
                )
            }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                // 複数波の山・先端の装飾が収まる高さを確保（太さに比例）
                .height(maxOf(baseThickness * 4.5f, tipHeightDp))
                .then(gestureModifier)
        ) {
            val w = size.width
            val cy = size.height / 2f
            val t = baseThickness.toPx() * thickenScale   // バーの太さ(px)
            val r = t / 2f
            val sz = spec.tipSize
            val capsuleOn = spec.capsule
            // カプセルON: バーの外側にカプセルの枠を描き、バーはその内側に収める（バーの太さは変えない）
            val outerH = t * 2.0f
            val inset = if (capsuleOn) (outerH - t) / 2f else 0f
            val x0 = inset                       // バーの左端
            val x1 = w - inset                   // バーの右端
            val px = (x0 + (x1 - x0) * animatedProgress).coerceIn(x0, x1)
            val ph1 = phase1.value
            val ph2 = phase2.value

            // ---------- ① カプセル（囲むだけ）----------
            if (capsuleOn) {
                val orr = outerH / 2f
                drawRoundRect(
                    color = color.copy(alpha = 0.22f),
                    topLeft = Offset(0f, cy - orr), size = Size(w, outerH),
                    cornerRadius = CornerRadius(orr)
                )
            }

            // ---------- ② 未再生部分（カプセルなしのときだけ）----------
            if (!capsuleOn && spec.unplayed && px < x1) {
                when (spec.unplayedStyle) {
                    "solid" -> {
                        drawRoundRect(
                            color = color.copy(alpha = 0.30f),
                            topLeft = Offset(px, cy - r), size = Size(x1 - px, t),
                            cornerRadius = CornerRadius(r)
                        )
                    }
                    "dotted" -> {
                        val rad = r * 0.8f
                        val step = r * 3.2f
                        var x = px + (step - px % step)
                        while (x < x1 - rad) {
                            drawCircle(color.copy(alpha = 0.45f), radius = rad, center = Offset(x, cy))
                            x += step
                        }
                    }
                    else -> { // dashed
                        val dashW = r * 4.0f
                        val gap = r * 3.0f
                        var x = px + (dashW + gap - px % (dashW + gap))
                        while (x + dashW < x1) {
                            drawRoundRect(
                                color = color.copy(alpha = 0.35f),
                                topLeft = Offset(x, cy - r), size = Size(dashW, t),
                                cornerRadius = CornerRadius(r)
                            )
                            x += dashW + gap
                        }
                    }
                }
            }

            // ---------- ③ 再生済み部分（カプセルの有無で太さは変えない）----------
            val playedW = max(px - x0, t)
            if (px > x0) {
                val ampScale = amplitudeScale
                if (spec.wave && spec.waveStyle == 2) {
                    // ===== 複数波: バーの上に位相の違う2つの半透明の波。右端(●の位置)に向けて0へ収束 =====
                    val topY = cy - r
                    val amp = t * 0.9f * ampScale
                    val len = px - x0
                    val ramp = min(len * 0.5f, t * 5f)
                    fun hump(freq: Float, ph: Float, k: Float): Path {
                        val p = Path()
                        p.moveTo(x0, topY)
                        var x = x0
                        while (x < px) {
                            val env = min(1f, (x - x0) / ramp) * min(1f, (px - x) / ramp)
                            val y = topY - amp * k * (0.5f + 0.5f * sin((x - x0) * freq + ph)) * env
                            p.lineTo(x, y)
                            x += 3f
                        }
                        p.lineTo(px, topY)
                        p.close()
                        return p
                    }
                    drawPath(hump(0.022f, ph1, 1.0f), color.copy(alpha = 0.55f))
                    drawPath(hump(0.016f, 1.7f - ph2, 0.75f), color.copy(alpha = 0.35f))
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x0, cy - r), size = Size(playedW, t),
                        cornerRadius = CornerRadius(r)
                    )
                } else if (spec.wave) {
                    // ===== シンプル: 再生済み部分そのものが波打つ線 =====
                    val amp = r * 0.9f * ampScale
                    val path = Path()
                    path.moveTo(x0, cy)
                    var x = x0
                    while (x <= px) {
                        path.lineTo(x, cy + amp * sin((x - x0) * 0.035f + ph1))
                        x += 2f
                    }
                    drawPath(
                        path = path, color = color,
                        style = Stroke(width = t, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                } else {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x0, cy - r), size = Size(playedW, t),
                        cornerRadius = CornerRadius(r)
                    )
                }
            }

            // ---------- 先端の装飾（全体的な大きさ = sz）----------
            if (spec.tip) {
                when (spec.tipStyle) {
                    "dot" -> {
                        drawCircle(color, radius = max(r * 1.7f, 4.dp.toPx()) * sz, center = Offset(px, cy))
                    }
                    "arrow" -> {
                        val u = r * sz
                        val sw = max(r * 0.9f, 2.dp.toPx()) * sz.coerceAtLeast(0.7f)
                        val tipX = px + u * 0.5f
                        drawLine(color, Offset(px - u * 3f, cy), Offset(tipX, cy), sw, StrokeCap.Round)
                        drawLine(color, Offset(tipX, cy), Offset(tipX - u * 2f, cy - u * 1.9f), sw, StrokeCap.Round)
                        drawLine(color, Offset(tipX, cy), Offset(tipX - u * 2f, cy + u * 1.9f), sw, StrokeCap.Round)
                    }
                    else -> { // bar
                        val bw = max(r * 0.9f, 2.dp.toPx()) * sz.coerceAtLeast(0.7f)
                        val bh = min(max(r * 7f * spec.tipBarScale, t * 1.2f) * sz, size.height)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(px - bw / 2f, cy - bh / 2f),
                            size = Size(bw, bh),
                            cornerRadius = CornerRadius(bw / 2f)
                        )
                    }
                }
            }
        }
    }
}