package com.sandolpin.santopimedia35.lyricsscreen

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.sandolpin.santopimedia35.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 間奏インジケーター（3つのドット）

// ===== 間奏インジケーター =====
// アニメーションシーケンス:
// 【出現】枠を開く(height 0→54dp) → 各ドットが stagger で小さく→大きくポップイン →
//         パルスループ: 全体100%→115%→100% を繰り返す
// 【消滅】全体を130%に拡大 → 0%への縮小・フェードアウトと同時に枠を約700msで閉じる
//
// ★ 実装メモ: 以前は「常に最大高さ(54dp)の枠を確保した上で中身をscaleYだけ変形する」
//   方式だった。scaleYはあくまで描画の見た目を縮小するだけでレイアウト上の占有スペースは
//   変わらないため、間奏が無いときも常に54dp分の余白がそのまま残ってしまう不具合があった。
//   heightScaleをレイアウト高さに反映し、間奏が無いときは高さ0dp（余白なし）、
//   出現時は0dp→54dpへなめらかにアニメーションしながら
//   実際にスペースが開くようにする。LazyColumn側の再計算は発生するが、
//   heightScaleの変化はtweenで滑らかに補間されるため、周囲の行も一緒になめらかに
//   詰まる/広がる動きになる。
@Composable
fun InterludeIndicator(
    accentColor: Color,
    isActive: Boolean,
    progress: Float,
    remainingMs: Long = Long.MAX_VALUE,
    isRight: Boolean = false,
    onClick: () -> Unit
) {
    val dotCount = 3
    val maxHeightDp = 54f

    val alpha        = remember { Animatable(0f) }
    val pulseScale    = remember { Animatable(1.0f) }
    val dotScales     = remember { List(dotCount) { Animatable(0f) } }
    // heightScale: 0f=高さ0dp（余白なし）、1f=高さmaxHeightDp（枠を全開にした状態）
    val heightScale   = remember { Animatable(0f) }
    // クリック領域を残さないための無効化フラグ
    var isCollapsed by remember { mutableStateOf(true) }
    var hasEntered by remember { mutableStateOf(false) }
    var exitStartedForActive by remember { mutableStateOf(false) }
    var pulseJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // remainingMsをSnapshotStateとして持つ（snapshotFlow で監視するため）
    val remainingMsState = rememberUpdatedState(remainingMs)

    val scope = rememberCoroutineScope()
    val naturalDeceleration = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    // isActive が false になって親の LaunchedEffect が再起動しても、終了演出は
    // CompositionScopeで最後まで実行する。縮小と穴閉じは同時に進める。
    val startExitSequence: () -> Unit = {
        if (hasEntered && !exitStartedForActive) {
            exitStartedForActive = true
            isCollapsed = true
            pulseJob?.cancel()
            scope.launch {
                pulseScale.stop()
                pulseScale.animateTo(1.30f, tween(450, easing = naturalDeceleration))
                delay(180)
                coroutineScope {
                    launch {
                        pulseScale.animateTo(0f, tween(700, easing = naturalDeceleration))
                    }
                    launch {
                        alpha.animateTo(0f, tween(700, easing = naturalDeceleration))
                    }
                    launch {
                        heightScale.animateTo(0f, tween(700, easing = naturalDeceleration))
                    }
                }
                hasEntered = false
            }
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            // ===== リセット =====
            hasEntered = true
            exitStartedForActive = false
            pulseJob?.cancel()
            pulseJob = null
            isCollapsed = false
            alpha.snapTo(0f)
            pulseScale.snapTo(1.0f)
            dotScales.forEach { it.snapTo(0f) }
            heightScale.snapTo(0f)

            // ===== 1. 枠を開き始め、400ms後にドットを出す =====
            coroutineScope {
                val openingJob = launch {
                    heightScale.animateTo(1f, tween(700, easing = naturalDeceleration))
                }
                delay(400)

                // ===== 2. ドットを stagger でポップイン =====
                alpha.animateTo(1f, tween(200))
                dotScales.forEachIndexed { i, anim ->
                    launch {
                        delay(i * 80L)
                        anim.animateTo(1.3f, tween(220, easing = FastOutSlowInEasing))
                        anim.animateTo(1.0f, tween(160, easing = FastOutSlowInEasing))
                    }
                }
                delay(dotCount * 80L + 380L)
                openingJob.join()
            }

            // ===== 3. パルスループ + 消滅監視 =====
            // snapshotFlow で remainingMs を監視しながらループ
            // 800ms 以下になったら消滅シーケンスへ
            pulseJob = scope.launch {
                while (true) {
                    pulseScale.animateTo(1.15f, tween(1400, easing = FastOutSlowInEasing))
                    delay(600)
                    pulseScale.animateTo(1.0f, tween(1400, easing = FastOutSlowInEasing))
                    delay(400)
                }
            }

            // remainingMs が 800ms 以下になるまで待つ
            snapshotFlow { remainingMsState.value }
                .collect { ms ->
                    if (ms in 0L..800L) startExitSequence()
                }
        } else {
            // 残り800msの通知を取りこぼしても、isActive=falseで同じ終了演出を開始。
            if (hasEntered) {
                startExitSequence()
            } else {
                isCollapsed = true
                pulseJob?.cancel()
            }
        }
    }

    // ★ isCollapsed による早期return は行わず、常にコンポーズし続ける。
    //   （if分岐でコンポーザブル自体をLazyColumnから抜き差しすると、間奏終了の瞬間に
    //   フェードアウト・縮小アニメーションが実行される前に強制終了してしまうため）
    //   高さ自体はheightScaleに応じて0dp〜maxHeightDpまで実際にアニメーションさせる。
    //   → 間奏が無い間は高さ0dpとなり、余白は一切表示されない。
    //   → 間奏が始まると0dpからなめらかに広がり、終わるとなめらかに0dpへ戻る。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // heightScaleは測定時に読む。毎フレームの高さ変化でコンポーズ全体を
                // 再実行せず、レイアウトだけを更新して間奏の開閉を軽くする。
                val heightPx = constraints.constrainHeight(
                    (maxHeightDp * heightScale.value.coerceIn(0f, 1f)).dp.roundToPx()
                )
                val placeable = measurable.measure(
                    constraints.copy(minHeight = heightPx, maxHeight = heightPx)
                )
                layout(constraints.maxWidth, heightPx) {
                    placeable.placeRelative(0, 0)
                }
            }
            .clickable(enabled = !isCollapsed) { onClick() },
        contentAlignment = if (isRight) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
                this.alpha = alpha.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(dotCount) { i ->
                val dotFraction = (progress * dotCount - i).coerceIn(0f, 1f)
                val dotAlpha    = 0.30f + dotFraction * 0.70f

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = dotScales[i].value
                            scaleY = dotScales[i].value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                        }
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = dotAlpha))
                )
                if (i < dotCount - 1) Spacer(Modifier.width(6.dp))
            }
        }
    }
}