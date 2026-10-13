package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.home.*
import kotlin.math.sin

// UI-A 部品: 波形アニメーション・波状シークバー・通常シークバー

// ===== 波形アニメーション =====
@Composable
fun WaveformAnimation(isPlaying: Boolean, color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val barCount = 5
    val animatedValues: List<State<Float>> = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + index * 80, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar$index"
        )
    }
    Canvas(modifier = modifier) {
        val totalBarsWidth = barCount * (size.width / (barCount * 2.5f))
        val barWidth = size.width / (barCount * 2.5f) * 0.45f
        val gap = size.width / (barCount * 2.5f) * 1.55f
        val startX = (size.width - (barWidth * barCount + gap * (barCount - 1))) / 2f
        for (i in 0 until barCount) {
            val h = if (isPlaying) size.height * animatedValues[i].value else size.height * 0.25f
            val x = startX + i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

// ===== 波状シークバー =====
@Composable
fun WaveSeekBar(progress: Float, onProgressChange: (Float) -> Unit, thickness: Dp, color: Color, style: Int = 1, isPlaying: Boolean = true, durationMs: Long = 0L) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveSeek")
    // 波アニメーション（遅め: 2800ms）
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(2800, easing = LinearEasing)),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3800, easing = LinearEasing)),
        label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = (Math.PI).toFloat(), targetValue = (3 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "phase3"
    )

    // 停止時に波振幅を滑らかに0へ（1000msかけて）
    val amplitudeScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ampScale"
    )

    // ドラッグ中の位置（-1=非ドラッグ）
    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    // ★ 時間インジケーターの消滅アニメーション中もdragProgressが-1fにリセットされた後の
    //   一瞬、表示位置・時刻の計算に使う値を保持しておくための変数。
    //   dragProgressをそのまま使うと、フェードアウトの途中で表示中の吹き出しが
    //   0:00の位置にワープしてから消えるような不自然な動きになってしまう。
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    // ドラッグ中は即時追従、それ以外（シーク・スキップ等）はなめらかに補間
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "waveSeekProgress"
    )

    // ★ 以前はこの領域全体がBox（子要素は重ねて描画される）だったため、
    //   ドラッグ中の時間インジケーターとシークバー本体(Canvas)が同じ位置に
    //   重なって表示されてしまっていた（Boxは指定が無い限り両方左上に配置される）。
    //   Columnに変更し、インジケーター→シークバーの順に縦積みすることで、
    //   インジケーターが必ずシークバーの「上」に表示され、重ならないようにする。
    Column(modifier = Modifier.fillMaxWidth()) {
        // TimeIndicator（ドラッグ中のみ表示）
        // ★ 以前は if(...) による単純な出し分けだったため、表示・非表示が
        //   アニメーション無しで瞬時に切り替わっていた（実際に指摘された不具合）。
        //   AnimatedVisibilityに変更し、フェード＋ポップイン/ポップアウトの
        //   アニメーションを付ける。位置・時刻の計算には lastKnownDragProgress を使い、
        //   消える瞬間にdragProgressが-1fへリセットされても表示内容が
        //   0:00にワープしないようにする（見た目は最後の位置のままフェードアウトする）。
        AnimatedVisibility(
            visible = isDraggingSeek && durationMs > 0,
            enter = fadeIn(tween(150)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(150),
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            exit = fadeOut(tween(120)) + scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(120),
                transformOrigin = TransformOrigin(0.5f, 1f)
            )
        ) {
            val posMs = (durationMs * lastKnownDragProgress).toLong()
            val posStr = "%d:%02d".format(posMs / 60000, (posMs % 60000) / 1000)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    // ★ 以前は color(=シークバーと同じアクセントカラー) を背景に使っていたため、
                    //   アクセントカラーが白に近い場合、白文字(Color.White)が背景に埋もれて
                    //   見えなくなっていた（実際に発生した不具合）。
                    //   テーマ・アクセントカラーに関わらず常に読めるよう、
                    //   背景は黒の半透明・文字は白で固定する（吹き出し風の見た目にする）。
                    color = Color.Black.copy(alpha = 0.78f),
                    // ★ 以前は fillMaxWidth(indicatorFraction) で吹き出しの「幅」自体を
                    //   ドラッグ位置の割合で制限していたため、シークバー左端付近
                    //   （dragProgressが0に近い、例: 0:01など）では幅がほぼ0まで潰れ、
                    //   中の文字が入りきらず2〜3行に折り返されてしまっていた
                    //   （実際に発生した不具合）。
                    //   NormalSeekBarと同じく、幅は制限せず絶対オフセット(padding start)で
                    //   位置だけをドラッグ位置に追従させる方式に統一する。
                    //   これにより吹き出しは常に文字が収まる自然な幅になり、折り返さない。
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = (lastKnownDragProgress * 300).dp.coerceIn(0.dp, 280.dp))
                ) {
                    Text(
                        posStr,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness * 3.5f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val p = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(p)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDraggingSeek = true },
                        onDragEnd = {
                            if (dragProgress >= 0f) onProgressChange(dragProgress)
                            isDraggingSeek = false
                            dragProgress = -1f
                        },
                        onDragCancel = { isDraggingSeek = false; dragProgress = -1f },
                        onDrag = { change: PointerInputChange, _: Offset ->
                            change.consume()
                            dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onProgressChange(dragProgress)
                        }
                    )
                }
        ) {
            val cy = size.height / 2f
            val r = thickness.toPx() / 2f
            val progressX = size.width * animatedProgress


            if (style == 1) {
                // ===== Style 1: おだやかな波線 + 右端縦棒 + 未再生破線 =====

                // 再生済み: おだやかな波線
                val waveAmplitude = r * 0.9f * amplitudeScale  // おだやかに
                val waveFreq = 0.035f                           // 周波数を少し下げる
                val wavePath = androidx.compose.ui.graphics.Path()
                wavePath.moveTo(0f, cy)
                var xw = 0f
                while (xw <= progressX) {
                    val yw = cy + waveAmplitude * sin((xw * waveFreq + phase1).toDouble()).toFloat()
                    wavePath.lineTo(xw, yw)
                    xw += 2f                                    // 間隔を短く
                }
                drawPath(
                    path = wavePath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = r * 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )

                // 右端縦棒（再生位置マーカー）大きく・太く
                val markerW = r * 1.4f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(progressX - markerW / 2f, cy - r * 3.5f),
                    size = Size(markerW, r * 7.0f),
                    cornerRadius = CornerRadius(markerW / 2f)
                )

                // 未再生: 破線（固定・整列）
                val dotW = r * 3.0f
                val dotH = r * 0.6f
                val dotGap = r * 2.5f
                val startOffset = progressX % (dotW + dotGap)
                var dx = progressX + (dotW + dotGap - startOffset)
                while (dx + dotW < size.width) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(dx, cy - dotH),
                        size = Size(dotW, dotH * 2),
                        cornerRadius = CornerRadius(dotH)
                    )
                    dx += dotW + dotGap
                }
            } else {
                // ===== Style 2: 塗りつぶし波形（音楽プレーヤー風）=====
                // 未再生トラック（薄い直線）
                drawRoundRect(
                    color = color.copy(alpha = 0.2f),
                    topLeft = Offset(progressX, cy - r * 0.5f),
                    size = Size((size.width - progressX).coerceAtLeast(0f), r),
                    cornerRadius = CornerRadius(r * 0.5f)
                )

                // 波形パスを構築（上縁 → 右端 → 下縁 → 左端 の閉じたパス）
                val amp = r * 2.8f * amplitudeScale  // 振幅（停止時0に）
                val freq = 0.03f            // 周波数
                val step = 4f               // x方向のサンプリング間隔（px）

                val path = androidx.compose.ui.graphics.Path()
                // 左端からスタート（中心線から上方向へ）
                path.moveTo(0f, cy)
                // 上縁（左→右）
                var xp = 0f
                while (xp <= progressX) {
                    val y = cy - amp * kotlin.math.abs(sin((xp * freq + phase1).toDouble()).toFloat())
                    path.lineTo(xp, y)
                    xp += step
                }
                path.lineTo(progressX, cy)
                path.close()

                // グラデーション塗りつぶし
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.3f)),
                        startY = cy - amp,
                        endY = cy
                    )
                )

                // 波の上縁ライン（くっきり表示）
                val linePath = androidx.compose.ui.graphics.Path()
                linePath.moveTo(0f, cy)
                xp = 0f
                while (xp <= progressX) {
                    val y = cy - amp * kotlin.math.abs(sin((xp * freq + phase1).toDouble()).toFloat())
                    linePath.lineTo(xp, y)
                    xp += step
                }
                drawPath(
                    path = linePath,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.7f)
                )

                // 再生位置マーカー（縦線 + 丸）
                drawLine(
                    color = color,
                    start = Offset(progressX, cy - amp - r),
                    end = Offset(progressX, cy + r),
                    strokeWidth = r * 0.8f
                )
                drawCircle(
                    color = color,
                    radius = r * 1.4f,
                    center = Offset(progressX, cy)
                )
            }
            // Style2のみ：未再生は薄いトラック（破線なし）
            // Style1のみ：上で処理済み
        }
    } // Column
}

// ===== 通常シークバー（カプセル＋固定整列ドット）=====
@Composable
fun NormalSeekBar(progress: Float, onProgressChange: (Float) -> Unit, thickness: Dp, color: Color, durationMs: Long = 0L) {
    var dragProgress by remember { mutableStateOf(-1f) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    // ★ 時間インジケーターの消滅アニメーション中もdragProgressが-1fにリセットされた後の
    //   一瞬、表示位置・時刻の計算に使う値を保持しておくための変数。
    //   dragProgressをそのまま使うと、フェードアウトの途中で表示中の吹き出しが
    //   0:00の位置にワープしてから消えるような不自然な動きになってしまう。
    var lastKnownDragProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(dragProgress) {
        if (dragProgress >= 0f) lastKnownDragProgress = dragProgress
    }

    // ドラッグ中は即時追従、それ以外（シーク・スキップ等）はなめらかに補間
    val animatedProgress by animateFloatAsState(
        targetValue = if (isDraggingSeek && dragProgress >= 0f) dragProgress else progress,
        animationSpec = if (isDraggingSeek) snap() else tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "seekProgress"
    )

    // ★ 以前はこの領域全体がBox（子要素は重ねて描画される）だったため、
    //   ドラッグ中の時間インジケーターとシークバー本体(Canvas)が同じ位置に
    //   重なって表示されてしまっていた（Boxは指定が無い限り両方左上に配置される）。
    //   Columnに変更し、インジケーター→シークバーの順に縦積みすることで、
    //   インジケーターが必ずシークバーの「上」に表示され、重ならないようにする。
    Column(modifier = Modifier.fillMaxWidth()) {
        // ★ 以前は if(...) による単純な出し分けだったため、表示・非表示が
        //   アニメーション無しで瞬時に切り替わっていた（実際に指摘された不具合）。
        //   AnimatedVisibilityに変更し、フェード＋ポップイン/ポップアウトの
        //   アニメーションを付ける。
        AnimatedVisibility(
            visible = isDraggingSeek && durationMs > 0,
            enter = fadeIn(tween(150)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(150),
                transformOrigin = TransformOrigin(0.5f, 1f)
            ),
            exit = fadeOut(tween(120)) + scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(120),
                transformOrigin = TransformOrigin(0.5f, 1f)
            )
        ) {
            val posMs = (durationMs * lastKnownDragProgress).toLong()
            val posStr = "%d:%02d".format(posMs / 60000, (posMs % 60000) / 1000)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    // ★ 以前は color(=シークバーと同じアクセントカラー) を背景に使っていたため、
                    //   アクセントカラーが白に近い場合、白文字(Color.White)が背景に埋もれて
                    //   見えなくなっていた（実際に発生した不具合）。
                    //   テーマ・アクセントカラーに関わらず常に読めるよう、
                    //   背景は黒の半透明・文字は白で固定する（吹き出し風の見た目にする）。
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = (lastKnownDragProgress * 300).dp.coerceIn(0.dp, 280.dp))
                ) {
                    Text(
                        posStr,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness * 3)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onProgressChange((offset.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDraggingSeek = true },
                        onDragEnd = {
                            if (dragProgress >= 0f) onProgressChange(dragProgress)
                            isDraggingSeek = false; dragProgress = -1f
                        },
                        onDragCancel = { isDraggingSeek = false; dragProgress = -1f },
                        onDrag = { change: PointerInputChange, _: Offset ->
                            change.consume()
                            dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onProgressChange(dragProgress)
                        }
                    )
                }
        ) {
            val cy = size.height / 2f
            val progressX = size.width * animatedProgress
            val r = thickness.toPx() / 2f

            // 再生済み：カプセル
            if (progressX > r * 2) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, cy - r),
                    size = Size(progressX, r * 2),
                    cornerRadius = CornerRadius(r)
                )
            }

            // 未再生：破線
            // ★ 波立ち(WaveSeekBar)ON時のStyle1と全く同じ形状・間隔にすることで、
            //   波立ちのON/OFFを切り替えても未再生部分の見た目が変わらないようにする。
            val dashW = r * 3.0f
            val dashH = r * 0.6f
            val dashGap = r * 2.5f
            val dashStartOffset = progressX % (dashW + dashGap)
            var dashX = progressX + (dashW + dashGap - dashStartOffset)
            while (dashX + dashW < size.width) {
                drawRoundRect(
                    color = color.copy(alpha = 0.35f),
                    topLeft = Offset(dashX, cy - dashH),
                    size = Size(dashW, dashH * 2),
                    cornerRadius = CornerRadius(dashH)
                )
                dashX += dashW + dashGap
            }
        }
    } // Column
}