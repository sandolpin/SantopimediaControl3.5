package com.sandolpin.santopimedia35.home.homescreena

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.sandolpin.santopimedia35.home.*
import kotlin.math.sin

// 動的アニメーション背景（AGSL メッシュグラデーション）

// =============================================================================
// ===== 動的アニメーション背景（Apple Music風 AGSL メッシュグラデーション）=====
// =============================================================================
// 【概要】
//   accentColor（アルバムアートから抽出した支配色）を軸に、
//   HSV色空間で色相を±40°ずらした3色を生成し、
//   AGSLの RuntimeShader で時間とともにゆっくり歪みながら混ぜ合わせる。
//
// 【AGSLシェーダーのアルゴリズム】
//   1. UV座標を sin/cos でゆっくり変形（ワープ）
//   2. 変形後のUVを使い、3つの色スポットとの距離で重み付け混合
//   3. 暗めのオーバーレイを重ねて可読性を確保
//
// 【API要件】
//   - RuntimeShader: API 33 (Android 13 / TIRAMISU) 以上
//   - この関数を呼ぶ前に Build.VERSION.SDK_INT >= TIRAMISU をチェックすること
//   - API 32以下では呼び出し元でブロックし、ぼかし背景にフォールバックする
// =============================================================================
// ===== 色の混ぜ方パターン =====
// hueShift    : 中心色から2・3色目を作るときの色相のずらし幅(度)。広いほど色の差がはっきりする
// baseSatBoost: 中心色自体の彩度を底上げする倍率。
//               ★ animatedColorSource="album_art"のとき、元になるblendedArtColorは
//               複数Swatchの加重平均のため彩度が失われがち（＝結果的に3色とも似た
//               薄い色になり、見た目が単色っぽくなる不具合の原因）。この倍率で
//               混色前にあらかじめ彩度を持ち上げておくことで、パターンによらず
//               「色が判別できる」状態を作る。
// sat2/val2, sat3/val3: 2・3色目それぞれの彩度・明度の追加倍率
private data class MeshColorPatternParams(
    val hueShift: Float,
    val baseSatBoost: Float,
    val sat2: Float, val val2: Float,
    val sat3: Float, val val3: Float
)

private fun resolveMeshColorPattern(pattern: String): MeshColorPatternParams = when (pattern) {
    // soft: 色相差を狭くし、彩度も抑えめ→落ち着いた印象
    "soft" -> MeshColorPatternParams(
        hueShift = 20f, baseSatBoost = 1.0f,
        sat2 = 0.95f, val2 = 0.90f, sat3 = 0.90f, val3 = 0.95f
    )
    // vivid: 色相差を広く・彩度を大きく底上げ→はっきり複数色に見える
    "vivid" -> MeshColorPatternParams(
        hueShift = 60f, baseSatBoost = 1.35f,
        sat2 = 1.30f, val2 = 0.95f, sat3 = 1.20f, val3 = 1.0f
    )
    // complementary: 補色に近い角度まで振る→コントラストが最も強い
    "complementary" -> MeshColorPatternParams(
        hueShift = 90f, baseSatBoost = 1.2f,
        sat2 = 1.2f, val2 = 0.9f, sat3 = 1.15f, val3 = 0.95f
    )
    // normal: 従来の見た目を踏襲しつつ、単色化対策として彩度だけ少し底上げ
    else -> MeshColorPatternParams(
        hueShift = 40f, baseSatBoost = 1.15f,
        sat2 = 1.1f, val2 = 0.85f, sat3 = 1.05f, val3 = 0.90f
    )
}

@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
@Composable
fun AnimatedMeshBackground(
    color1: Color,
    // ★ 背景の色の濃さ(明るさ倍率)。設定画面のスライダーで0.6〜1.0の範囲で調整する。
    //   低いほど暗く落ち着いた背景になる。ライトモード用。
    darkness: Float = 0.88f,
    // ★ ダークモード専用の濃さ。以前は isDarkTheme のとき darkness*0.75 という
    //   固定倍率で自動計算していたが、ユーザーが独立して調整できるよう
    //   専用スライダーの値をそのまま使う方式に変更した。
    darkModeDarkness: Float = 0.65f,
    // ★ 色の混ぜ方パターン。"soft"/"normal"/"vivid"/"complementary"
    colorPattern: String = "normal",
    // ★ ダークモードのときは明示的にさらに暗くする（ライトモードと同じ濃さのままだと
    //   ダークテーマ全体の中で背景だけが浮いて明るく見えてしまうため）。
    isDarkTheme: Boolean = false,
    // ★ 背景の不透明度。「アルバムアートのぼかし＋色」のように、下にぼかし画像を
    //   敷いてこの動的アニメーションを半透明で重ねたいケースのために追加した。
    //   通常のanimatedスタイル単体では1f（不透明）のまま使う。
    // ★ 「アルバムアートから」用: [下地, アクセント1, アクセント2] を直接指定する。
    //   nullなら従来どおり color1 から色相を回して3色を作る。
    explicitColors: List<Color>? = null,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    val params = resolveMeshColorPattern(colorPattern)

    // ===== 3色生成: accentColor を中心に HSV ±hueShift° =====
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color1.red   * 255).toInt(),
        (color1.green * 255).toInt(),
        (color1.blue  * 255).toInt(),
        hsv
    )
    // 単色化対策: 混色前に中心色自体の彩度を底上げしておく
    // （album_artモードで元色がくすんだ平均色になっていても、ここで彩度を持ち上げる
    //   ことで3色それぞれがちゃんと色として判別できるようにする）
    hsv[1] = (hsv[1] * params.baseSatBoost).coerceIn(0f, 1f)

    // 色相を回して2・3色目を作る。彩度・明度はパターンに応じて強調する
    val hsv2 = hsv.copyOf().also {
        it[0] = (it[0] + params.hueShift) % 360f
        it[1] = (it[1] * params.sat2).coerceIn(0f, 1f)
        it[2] = (it[2] * params.val2).coerceIn(0f, 1f)
    }
    val hsv3 = hsv.copyOf().also {
        it[0] = (it[0] - params.hueShift + 360f) % 360f
        it[1] = (it[1] * params.sat3).coerceIn(0f, 1f)
        it[2] = (it[2] * params.val3).coerceIn(0f, 1f)
    }

    val useExplicit = explicitColors != null && explicitColors.size >= 3
    val c1 = if (useExplicit) explicitColors!![0] else Color(android.graphics.Color.HSVToColor(hsv))
    val c2 = if (useExplicit) explicitColors!![1] else Color(android.graphics.Color.HSVToColor(hsv2))
    val c3 = if (useExplicit) explicitColors!![2] else Color(android.graphics.Color.HSVToColor(hsv3))
    // 下地スポットの重み。explicit時は下地を広く、既定(従来)は 1.0 で見た目は変わらない
    val baseBoost = if (useExplicit) MESH_BASE_BOOST else 1f

    // ===== 最終的な明るさ倍率 =====
    // ダークモード時はdarkModeDarkness（専用スライダー）、それ以外はdarkness（通常スライダー）を使う。
    val effectiveDarkness = (if (isDarkTheme) darkModeDarkness else darkness).coerceIn(0.25f, 1f)

    // ===== 時間アニメーション（nanoTimeベース: リセットなし・完全連続）=====
    // infiniteTransition + RepeatMode.Restart は 0→2π で値が瞬間リセットされ
    // シェーダーの sin/cos がガクッと跳ぶ。
    // withFrameNanos で実経過時間（秒）を単調増加させることで完全に連続になる。
    var time by remember { mutableFloatStateOf(0f) }
    val startNanos = remember { System.nanoTime() }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                // 経過秒（単調増加・リセットなし）
                time = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    // ===== AGSL シェーダーコード =====
    // uniform: r/g/b の各チャンネルを3色×3個（合計9個）渡す
    val shaderSrc = remember {
        """
        uniform float2 iResolution;
        uniform float  iTime;
        // 色1〜3（linear RGB）
        uniform float3 color1;
        uniform float3 color2;
        uniform float3 color3;
        // 背景全体の明るさ倍率（設定のスライダー・ダークモード補正を反映した最終値）
          uniform float  darkness;
        // 下地スポット(color1)の重み倍率。大きいほど color1 が画面の広い範囲を占める
        uniform float  baseBoost;
        // ゆっくり動くスポット中心座標（-0.5〜0.5 の正規化空間）
        float2 spotCenter(float2 seed, float time, float speed) {
            return float2(
                0.5 + 0.35 * sin(time * speed + seed.x),
                0.5 + 0.35 * cos(time * speed * 0.7 + seed.y)
            );
        }

        // UV をゆっくり歪ませるワープ関数
        float2 warp(float2 uv, float time) {
            float wx = sin(uv.y * 2.8 + time * 0.4) * 0.06
                     + cos(uv.x * 3.1 + time * 0.3) * 0.04;
            float wy = cos(uv.x * 2.5 + time * 0.35) * 0.06
                     + sin(uv.y * 3.3 + time * 0.25) * 0.04;
            return uv + float2(wx, wy);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / iResolution;

            // ワープ適用
            float2 wuv = warp(uv, iTime);

            // 3つのスポット中心（それぞれ異なる速度・位相で動く）
            float2 p1 = spotCenter(float2(0.0, 1.2), iTime, 0.28);
            float2 p2 = spotCenter(float2(2.1, 0.5), iTime, 0.21);
            float2 p3 = spotCenter(float2(4.3, 3.1), iTime, 0.17);

            // 距離を逆数二乗で重み付け（近いほど強く）
            float d1 = baseBoost / (dot(wuv - p1, wuv - p1) * 6.0 + 0.3);
            float d2 = 1.0 / (dot(wuv - p2, wuv - p2) * 6.0 + 0.3);
            float d3 = 1.0 / (dot(wuv - p3, wuv - p3) * 6.0 + 0.3);

            float total = d1 + d2 + d3;
            float3 col  = (color1 * d1 + color2 * d2 + color3 * d3) / total;

            // 明るさ調整（テキスト可読性のため・設定のスライダー値+ダークモード補正）
            col *= darkness;

            return half4(col, 1.0);
        }
        """.trimIndent()
    }

    // c1/c2/c3（アルバムアート色）が変わったとき shader を再作成する
    val shader = remember(c1, c2, c3) { android.graphics.RuntimeShader(shaderSrc) }

    Canvas(modifier = modifier) {
        // uniform を毎フレーム更新
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("color1", c1.red, c1.green, c1.blue)
        shader.setFloatUniform("color2", c2.red, c2.green, c2.blue)
        shader.setFloatUniform("color3", c3.red, c3.green, c3.blue)
        shader.setFloatUniform("darkness", effectiveDarkness)
        shader.setFloatUniform("baseBoost", baseBoost)

        // RuntimeShader を android.graphics.Paint に直接セットして nativeCanvas で描画
        val paint = android.graphics.Paint()
        paint.shader = shader
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()

        drawContext.canvas.nativeCanvas.drawRect(
            0f, 0f, size.width, size.height, paint
        )
    }
}