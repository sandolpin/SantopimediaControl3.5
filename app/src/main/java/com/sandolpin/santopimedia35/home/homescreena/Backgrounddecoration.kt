package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.sandolpin.santopimedia35.home.*

// 背景装飾（ドット / 水玉 / ランダム図形）

// =============================================================================
// ===== 背景装飾（ドットグラデーション / 水玉 / ランダム図形）=====
// =============================================================================
// backgroundStyle（default/gradient/blur/color_mix/animated）とは独立した
// 追加の装飾レイヤー。どのスタイルの上にも重ねて表示できる。
// ・dot_gradient : 画面下ほど大きく・濃く、上ほど小さく・薄くなるドットのグラデーション
// ・polka_dot    : 均一な間隔・大きさで千鳥に並ぶ水玉模様
// ・random_shapes: 円・四角・三角をランダムな位置・大きさ・回転で散りばめた図形
// 色は呼び出し側（HomeScreenA.kt等）からaccentColorか白固定を渡してもらい、
// 不透明度・大きさ倍率は設定(bgDecorationOpacity/bgDecorationSize)で調整できる。
//
// ★ scrollOffsetPx: 歌詞画面でスクロールに連動して装飾を動かすための引数。
//   0f（デフォルト）を渡せば従来通り静止したまま。呼び出し側（LyricsScreen.kt）で
//   歌詞のスクロール量に「歌詞と区別がつくよう控えめな係数」をかけた値を渡すことで、
//   歌詞そのものより少ない量だけ装飾もスクロールする（パララックス効果）。
//   パターン自体をスクロール量ぶんずらし、間隔(spacing)や画面高さで折り返すことで、
//   継ぎ目なくループして見えるようにしている。
// =============================================================================
private data class RandomShapeSpec(
    val xFrac: Float,
    val yFrac: Float,
    val sizeFrac: Float,
    val rotationDeg: Float,
    val shapeType: Int // 0=円 1=四角 2=三角
)

@Composable
fun BackgroundDecorationLayer(
    type: String,
    color: Color,
    opacity: Float,
    sizeScale: Float,
    scrollOffsetPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    when (type) {
        "dot_gradient"  -> DotGradientDecoration(color, opacity, sizeScale, scrollOffsetPx, modifier)
        "polka_dot"     -> PolkaDotDecoration(color, opacity, sizeScale, scrollOffsetPx, modifier)
        "random_shapes" -> RandomShapesDecoration(color, opacity, sizeScale, scrollOffsetPx, modifier)
        else -> { /* "none" 等: 何も描画しない */ }
    }
}

@Composable
private fun DotGradientDecoration(
    color: Color,
    opacity: Float,
    sizeScale: Float,
    scrollOffsetPx: Float,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        // ★ 以前は34dpと間隔が広すぎて粗く見えていたため、
        //   参考画像のような密なハーフトーン風になるよう大幅に詰めた。
        val spacing = 15.dp.toPx() * sizeScale
        if (spacing <= 0f) return@Canvas
        val cols = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 3
        // スクロール量をspacingで折り返し、継ぎ目なくループするようにする
        val wrappedScroll = scrollOffsetPx % spacing
        for (row in -1 until rows) {
            for (col in 0 until cols) {
                val x = col * spacing
                val y = row * spacing + wrappedScroll
                // 下（画面下）ほど濃く・大きく、上ほど薄く・小さくすることで
                // グラデーションらしい奥行きを出す。大きい方は隣同士がくっつき、
                // 参考画像のような「下はほぼ塗りつぶし」の見た目に近づける。
                val verticalFrac = (y / size.height).coerceIn(0f, 1f)
                val dotAlpha = ((0.25f + verticalFrac * 0.75f) * opacity).coerceIn(0f, 1f)
                val dotRadius = spacing * (0.12f + verticalFrac * 0.46f)
                drawCircle(
                    color = color.copy(alpha = dotAlpha),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun PolkaDotDecoration(
    color: Color,
    opacity: Float,
    sizeScale: Float,
    scrollOffsetPx: Float,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {
        val spacing = 40.dp.toPx() * sizeScale
        val radius = 5.dp.toPx() * sizeScale
        if (spacing <= 0f) return@Canvas
        val cols = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 3
        val wrappedScroll = scrollOffsetPx % spacing
        val dotColor = color.copy(alpha = opacity.coerceIn(0f, 1f))
        for (row in -1 until rows) {
            // 1行ごとにずらして千鳥配置にすることで水玉らしい見た目にする
            // （rowが負の値になってもchequered判定が崩れないよう剰余の符号をそのまま利用）
            val rowOffsetX = if (row % 2 != 0) spacing / 2f else 0f
            for (col in 0 until cols) {
                val x = col * spacing + rowOffsetX
                val y = row * spacing + wrappedScroll
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun RandomShapesDecoration(
    color: Color,
    opacity: Float,
    sizeScale: Float,
    scrollOffsetPx: Float,
    modifier: Modifier
) {
    // ★ 以前は固定シード(42)で常に同じ配置になっていたが、
    //   「読み込みごとに形・配置を変えてほしい」という要望に合わせ、
    //   Composableが新しく生成される（画面に新しく入る）たびに毎回
    //   ランダムなシードで再生成する。sizeScaleが変わったときも再生成される。
    //   同じ画面にとどまっている間の再コンポーズでは配置を維持したいため、
    //   rememberでキャッシュする（毎フレーム変わるとチラついてしまうため）。
    val shapes = remember(sizeScale) {
        val random = kotlin.random.Random(System.nanoTime())
        List(18) {
            RandomShapeSpec(
                xFrac = random.nextFloat(),
                yFrac = random.nextFloat(),
                sizeFrac = (0.03f + random.nextFloat() * 0.05f) * sizeScale,
                rotationDeg = random.nextFloat() * 360f,
                shapeType = random.nextInt(3)
            )
        }
    }
    Canvas(modifier = modifier) {
        val minDim = kotlin.math.min(size.width, size.height)
        val shapeColor = color.copy(alpha = opacity.coerceIn(0f, 1f))
        val h = size.height
        shapes.forEach { spec ->
            val cx = spec.xFrac * size.width
            // スクロール量を画面の高さで折り返し、図形が継ぎ目なくループして動くようにする
            var cy = (spec.yFrac * h + scrollOffsetPx) % h
            if (cy < 0f) cy += h
            val shapeSize = minDim * spec.sizeFrac
            rotate(degrees = spec.rotationDeg, pivot = Offset(cx, cy)) {
                when (spec.shapeType) {
                    0 -> drawCircle(color = shapeColor, radius = shapeSize / 2f, center = Offset(cx, cy))
                    1 -> drawRect(
                        color = shapeColor,
                        topLeft = Offset(cx - shapeSize / 2f, cy - shapeSize / 2f),
                        size = Size(shapeSize, shapeSize)
                    )
                    else -> {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(cx, cy - shapeSize / 2f)
                            lineTo(cx + shapeSize / 2f, cy + shapeSize / 2f)
                            lineTo(cx - shapeSize / 2f, cy + shapeSize / 2f)
                            close()
                        }
                        drawPath(path = path, color = shapeColor)
                    }
                }
            }
        }
    }
}