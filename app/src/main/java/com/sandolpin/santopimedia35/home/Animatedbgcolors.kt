package com.sandolpin.santopimedia35.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState

// =============================================================================
// 動的アニメーション背景「アルバムアートから」用の共通ヘルパー
// -----------------------------------------------------------------------------
// ホームA/B/C/D・歌詞画面・設定プレビューで同じ判定を使うため、1か所に集約している。
//
// 【色の持ち方】
//   MediaState.artMeshColors = [下地色, アクセント1, アクセント2]
//     [0] 下地色     … 画像の中で一番面積が大きい色（白背景のジャケットなら白）
//     [1][2] アクセント … 彩度が高く、下地と明らかに違う色（青・赤など）
//   これを AnimatedMeshBackground(explicitColors = …) に渡すと、
//   「下地を大きく広げ、その上にアクセントがふんわり混ざる」背景になる。
// =============================================================================

/** 下地スポットの重み倍率（大きいほど下地色が画面の広い範囲を占める）。シェーダー側と共有 */
const val MESH_BASE_BOOST = 2.4f

/** 明るい背景のときに使う文字・アイコン色（真っ黒ではなく少しやわらかい黒） */
val AnimatedFgDark = Color(0xFF1A1A1A)

/** 背景が明るいなら濃い色、暗いなら白 */
fun animatedForegroundColor(isLightBg: Boolean): Color =
    if (isLightBg) AnimatedFgDark else Color.White

/**
 * 「アルバムアートから」が選ばれていて、抽出済みの3色が使えるときだけ3色を返す。
 * それ以外（アクセントカラーから／抽出前）は null → 従来どおりの色生成になる。
 */
fun explicitMeshColorsOrNull(settings: AppSettings, mediaState: MediaState): List<Color>? =
    if (settings.animatedColorSource == "album_art") {
        // 5パターン分が計算済みならそこから選ぶ。無ければ従来の1パターン(おまかせ)
        val picked = mediaState.artMeshPalettes
            .getOrNull(settings.animatedExtractPattern.coerceIn(1, 5) - 1)
            ?: mediaState.artMeshColors
        if (picked.size >= 3) picked else null
    } else null

/**
 * 描画される背景が「明るい」かどうかの判定。
 * シェーダーと同じく下地を重く見た平均色に、濃さ(darkness)を掛けた色の明るさで判断する。
 * 明るい背景なら白文字は読めなくなるため、呼び出し側で文字・アイコンを濃い色に切り替える。
 */
fun isMeshBackgroundLight(
    colors: List<Color>,
    darkness: Float,
    darkModeDarkness: Float,
    isDarkTheme: Boolean
): Boolean {
    if (colors.size < 3) return false
    val d = (if (isDarkTheme) darkModeDarkness else darkness).coerceIn(0.25f, 1f)
    val wBase = MESH_BASE_BOOST
    val total = wBase + 2f
    val r = (colors[0].red * wBase + colors[1].red + colors[2].red) / total
    val g = (colors[0].green * wBase + colors[1].green + colors[2].green) / total
    val b = (colors[0].blue * wBase + colors[1].blue + colors[2].blue) / total
    return Color(r * d, g * d, b * d).luminance() > 0.30f
}