package com.sandolpin.santopimedia35.mainactivity

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.home.HomeScreenB
import com.sandolpin.santopimedia35.home.HomeScreenC
import com.sandolpin.santopimedia35.home.HomeScreenD
import com.sandolpin.santopimedia35.home.homescreena.HomeScreenA
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.remember.HistoryViewModel
import com.sandolpin.santopimedia35.settingscreen.*
import kotlin.math.abs

// プレーヤー画面 + キュー画面の縦スワイプ統合

// ===== プレーヤー画面 + QueueScreen の縦スワイプ統合 =====
// page=0: HomeScreen全面パススルー下スワイプ検知
// page=1: ミニプレーヤーエリアのみで上スワイプ・タップで戻る
@Composable
fun PlayerScreenWithSwipe(
    viewModel: MediaPlayerViewModel,
    historyViewModel: HistoryViewModel,
    settings: com.sandolpin.santopimedia35.database.AppSettings,
    // ★ ピル型ナビゲーションバーの実際の高さ(SantopimediaAppで測定)。
    //   このスクリーン自身の背景は画面の本当の端まで伸ばしたままにし、
    //   実際に操作するボタン類(HomeScreenA/Cのコントロール・BottomMiniPlayer)だけに
    //   この高さぶんの余白を持たせ、ピルナビゲーションバーに隠れないようにする。
    bottomNavBarHeight: Dp = 0.dp,
    // ★ 横画面のとき、画面右端に表示される縦向きピルナビゲーションバーの実測幅。
    //   HomeScreenA(UI-A)の横画面レイアウトへそのまま渡し、右側の操作要素が
    //   縦向きピルに隠れないよう余白として使う。縦画面では常に0dp。
    endNavBarWidth: Dp = 0.dp,
    onLongPressPlay: () -> Unit,
    onShowLyrics: () -> Unit = {},
    onShowFavorites: () -> Unit = {},
    onShowHistory: () -> Unit = {}
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    // ★ HomeScreenC（UI-C）の区切り線をドラッグしている間だけ true になるフラグ。
    //   区切り線のドラッグ操作を、下の homeSwipeModifier（画面全体の下スワイプ検知）が
    //   横取りしてミニプレーヤーを誤って表示してしまう不具合の対策。
    var isDividerDragActive by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx   = constraints.maxHeight.toFloat()
        val swipeThresholdPx = screenHeightPx * 0.20f
        val density = androidx.compose.ui.platform.LocalDensity.current

        val rawProgress = when {
            page == 0 ->  (dragOffset / screenHeightPx).coerceIn(0f, 1f)
            page == 1 -> (1f + dragOffset / screenHeightPx).coerceIn(0f, 1f)
            else      ->  page.toFloat()
        }
        val targetProgress = if (isDragging) rawProgress else page.toFloat()

        val animatedProgress by animateFloatAsState(
            targetValue   = targetProgress,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            label = "swipeProgress"
        )
        val p = if (isDragging) rawProgress else animatedProgress

        val homeTranslateY = screenHeightPx * p
        val homeAlpha      = (1f - p * 2f).coerceIn(0f, 1f)
        val queueAlpha     = p.coerceIn(0f, 1f)
        val miniAlpha      = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val miniSlideY     = with(density) { 24.dp.toPx() } * (1f - miniAlpha)

        // HomeScreen全面パススルー下スワイプ検知modifier
        val homeSwipeModifier = Modifier.pointerInput(Unit) {
            var totalDy = 0f; var totalDx = 0f
            var decided = false; var active = false
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val changes = event.changes
                    if (changes.all { it.changedToUp() }) {
                        if (active) {
                            isDragging = false
                            if (dragOffset > swipeThresholdPx) page = 1
                            dragOffset = 0f
                        }
                        totalDy = 0f; totalDx = 0f; decided = false; active = false
                        continue
                    }
                    val change = changes.firstOrNull() ?: continue
                    // ★ HomeScreenCの区切り線をドラッグ中は、この画面全体スワイプ判定を無効化する。
                    //   区切り線は自身で独自にドラッグを処理するため、ここで反応すると
                    //   ミニプレーヤーが誤って表示されてしまう（実際に発生した不具合）。
                    if (isDividerDragActive) {
                        totalDy = 0f; totalDx = 0f; decided = false; active = false
                        continue
                    }
                    val dy = change.position.y - change.previousPosition.y
                    val dx = change.position.x - change.previousPosition.x
                    if (!decided) {
                        totalDy += dy; totalDx += dx
                        if (abs(totalDy) + abs(totalDx) > 8f) {
                            decided = true
                            active = abs(totalDy) > abs(totalDx) && totalDy > 0
                            if (active) { isDragging = true; dragOffset = totalDy }
                        }
                    } else if (active) {
                        if (dy > 0) { dragOffset += dy; change.consume() }
                        else { isDragging = false; dragOffset = 0f; active = false; decided = false; totalDy = 0f; totalDx = 0f }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // QueueScreen（背面）
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = queueAlpha }) {
                QueueScreenBody(
                    viewModel = viewModel,
                    settings = settings,
                    onExpandHome = { page = 0 },
                    onShowFavorites = onShowFavorites
                )
            }

            // HomeScreen（前面・下スワイプで退場）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = homeTranslateY; alpha = homeAlpha }
                    // ★ UI-C（上下分割）は画面内に区切り線の縦ドラッグを持つため、
                    //   画面全体を対象にした下スワイプ検知（homeSwipeModifier）と操作が競合する。
                    //   タッチ開始位置が区切り線の当たり判定から少しでも外れると検知漏れが起き、
                    //   区切り線を操作したつもりでもミニプレーヤーが出てしまっていた（実際の不具合）。
                    //   タイミング依存の回避策では確実性に欠けるため、UI-Cのときはこのジェスチャー
                    //   自体を丸ごと無効化する。
                    .then(if (page == 0 && settings.playerUiStyle != "C") homeSwipeModifier else Modifier)
            ) {
                when (settings.playerUiStyle) {
                    "A" -> HomeScreenA(
                        viewModel = viewModel,
                        settings = settings,
                        bottomNavBarHeight = bottomNavBarHeight,
                        endNavBarWidth = endNavBarWidth,
                        onLongPressPlay = onLongPressPlay,
                        onShowLyrics = onShowLyrics,
                        onShowHistory = onShowHistory
                    )
                    "C" -> HomeScreenC(
                        viewModel = viewModel,
                        historyViewModel = historyViewModel,
                        settings = settings,
                        bottomNavBarHeight = bottomNavBarHeight,
                        onLongPressPlay = onLongPressPlay,
                        onDividerDragActiveChange = { isDividerDragActive = it }
                    )

                    "B" -> HomeScreenB(
                        viewModel = viewModel,
                        settings = settings,
                        bottomNavBarHeight = bottomNavBarHeight,
                        endNavBarWidth = endNavBarWidth,
                        onLongPressPlay = onLongPressPlay,
                        onShowLyrics = onShowLyrics,
                        onShowHistory = onShowHistory
                    )
                    "D" -> HomeScreenD(
                        viewModel = viewModel,
                        settings = settings,
                        bottomNavBarHeight = bottomNavBarHeight,
                        endNavBarWidth = endNavBarWidth,
                        onLongPressPlay = onLongPressPlay,
                        onShowLyrics = onShowLyrics,
                        onShowHistory = onShowHistory
                    )
                    else -> HomeScreenA(
                        viewModel = viewModel,
                        settings = settings,
                        bottomNavBarHeight = bottomNavBarHeight,
                        endNavBarWidth = endNavBarWidth,
                        onLongPressPlay = onLongPressPlay,
                        onShowLyrics = onShowLyrics,
                        onShowHistory = onShowHistory
                    )
                }
            }

            // ミニプレーヤー（page=1のみ・上スワイプ/タップで戻る）
            if (miniAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // ★ QueueScreen(page=1)側もプレーヤー同様に背景をピルナビゲーションバーの
                        //   裏まで伸ばしたままにしているため、ここに浮かぶBottomMiniPlayer自体は
                        //   ピルナビゲーションバーに隠れないよう、その高さぶんの余白を追加する。
                        .padding(bottom = bottomNavBarHeight)
                        .graphicsLayer { alpha = miniAlpha; translationY = miniSlideY }
                        .pointerInput(page) {
                            if (page != 1) return@pointerInput
                            detectVerticalDragGestures(
                                onDragStart = { isDragging = true; dragOffset = 0f },
                                onDragEnd = {
                                    isDragging = false
                                    if (dragOffset < -swipeThresholdPx * 0.5f) page = 0
                                    dragOffset = 0f
                                },
                                onDragCancel = { isDragging = false; dragOffset = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount < 0) { change.consume(); dragOffset += dragAmount }
                                }
                            )
                        }
                ) {
                    BottomMiniPlayer(viewModel = viewModel, settings = settings, onTap = { page = 0 })
                }
            }
        }
    }
}

// ===== QueueScreenBody =====
// BottomMiniPlayerを含まないQueueScreen本体
// （ミニプレーヤーはPlayerScreenWithSwipeで独立して制御）
@Composable
fun QueueScreenBody(
    viewModel: MediaPlayerViewModel,
    settings: com.sandolpin.santopimedia35.database.AppSettings,
    onExpandHome: () -> Unit,
    onShowFavorites: () -> Unit = {}
) {
    // QueueScreen内のBottomMiniPlayerのonTapをonExpandHomeに繋ぐ
    QueueScreen(
        viewModel    = viewModel,
        settings     = settings,
        onExpandHome = onExpandHome,
        onShowFavorites = onShowFavorites
    )
}