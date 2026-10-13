package com.sandolpin.santopimedia35.mainactivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.settingscreen.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

// 横画面用: 画面右端の縦向きピルナビゲーションバー

// =============================================================================
// 横画面用: 画面右端に重ねて表示する縦向きピルナビゲーションバー
// -----------------------------------------------------------------------------
// SantopimediaApp内の水平ピル(bottomBar)と機能的には同じもの（アイコンタップ/
// ドラッグでのルート切り替え・スライド式インジケーター・展開式ショートカット
// パネル）だが、見た目と操作方向を縦向きに組み替えている。
// ・アイコンはColumnで縦に並べ、選択中インジケーターは横ドラッグではなく
//   縦ドラッグ(detectVerticalDragGestures)で追従・スナップする
// ・展開ボタンをタップすると、ショートカットパネル(NavShortcutPanel)が
//   ピルの「左側」に開く（横画面はピルが右端にあるため、パネルは左方向に
//   広げるのが自然なため expandHorizontally/shrinkHorizontally を使う）
// ・NavShortcutPanel自体はfillMaxWidth()で作られているため、Rowの中でそのまま
//   置くと残りの横幅全部を占めてしまう。固定幅のBoxで包んで幅を制限する
// =============================================================================
@Composable
internal fun VerticalNavBar(
    navController: androidx.navigation.NavHostController,
    navItems: List<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, String>>,
    settings: com.sandolpin.santopimedia35.database.AppSettings,
    viewModel: MediaPlayerViewModel,
    accentColor: Color,
    darkTheme: Boolean,
    navBarExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onWidthMeasured: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val density = LocalDensity.current

    // 水平ピルと同じロジックで背景色を決定
    val navBarColor = remember(accentColor, darkTheme, settings.navBarBackgroundStyle) {
        if (settings.navBarBackgroundStyle == "solid") {
            if (darkTheme) Color(0xFF1C1C1E) else Color(0xFFF5F5F5)
        } else {
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (accentColor.red   * 255).toInt(),
                (accentColor.green * 255).toInt(),
                (accentColor.blue  * 255).toInt(),
                hsv
            )
            if (darkTheme) {
                hsv[1] = (hsv[1] * 0.40f).coerceIn(0f, 1f)
                hsv[2] = (hsv[2] * 0.25f + 0.10f).coerceIn(0f, 1f)
            } else {
                hsv[1] = (hsv[1] * 0.20f).coerceIn(0f, 1f)
                hsv[2] = (hsv[2] * 0.10f + 0.92f).coerceIn(0f, 1f)
            }
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }

    val navBarSizeScale = when (settings.navBarSizeLevel) {
        1 -> 0.85f
        2 -> 0.925f
        3 -> 1.0f
        4 -> 1.075f
        else -> 1.15f
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ===== ショートカットパネル（展開時のみ・ピルの左側に表示）=====
        AnimatedVisibility(
            visible = navBarExpanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            // NavShortcutPanel自体はfillMaxWidth()前提のため、固定幅のBoxで包んで
            // 幅を制限する。縦画面の高さが小さい端末でも収まるよう、
            // 画面高の90%を上限にしてはみ出す分はNavShortcutPanel内部の
            // verticalScrollで対応する（NavShortcutPanel側にscroll対応を追加済み）。
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(0.92f)
                    .padding(end = 10.dp)
            ) {
                NavShortcutPanel(
                    viewModel = viewModel,
                    settings = settings,
                    accentColor = accentColor
                )
            }
        }

        // ===== 縦向きピル本体 =====
        Box(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .onGloballyPositioned { coords ->
                    onWidthMeasured(with(density) { coords.size.width.toDp() })
                }
                .padding(
                    end = WindowInsets.navigationBars.asPaddingValues()
                        .calculateEndPadding(LocalLayoutDirection.current) + 4.dp,
                    start = 4.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            // ★ 水平ピルと同じ理由（影と半透明背景を同じSurfaceで描くと透明度を下げたときに
            //   「白いバー」が透けて見える）で、影専用の外側Boxとclip+背景の内側Boxに分離する。
            Box(
                modifier = Modifier.shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(navBarColor.copy(alpha = settings.navBarOpacity))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(
                                vertical = (10 * navBarSizeScale).dp,
                                horizontal = (6 * navBarSizeScale).dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(settings.navBarButtonSpacing.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ===== スライド式ナビアイテム（縦版） =====
                        val itemWidthDp = (52 * navBarSizeScale).dp
                        val itemHeightDp = (38 * navBarSizeScale).dp
                        val itemHeightPx = with(density) { itemHeightDp.toPx() }
                        val spacingPx = with(density) { settings.navBarButtonSpacing.dp.toPx() }
                        val slotPitchPx = itemHeightPx + spacingPx

                        val currentIndex = remember(currentDestination, navItems) {
                            navItems.indexOfFirst { (route, _, _) ->
                                currentDestination?.hierarchy?.any { it.route == route } == true
                            }.coerceAtLeast(0)
                        }
                        val currentIndexState = rememberUpdatedState(currentIndex)

                        var dragOffsetPx by remember { mutableStateOf(0f) }
                        var isDragging by remember { mutableStateOf(false) }

                        val baseOffsetPx = slotPitchPx * currentIndex
                        val maxOffsetPx = slotPitchPx * (navItems.size - 1)
                        val targetOffsetPx = if (isDragging) {
                            (baseOffsetPx + dragOffsetPx).coerceIn(0f, maxOffsetPx)
                        } else {
                            baseOffsetPx
                        }
                        val animatedOffsetPx by animateFloatAsState(
                            targetValue = targetOffsetPx,
                            animationSpec = if (isDragging)
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                            else
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "navSlideIndicatorVertical"
                        )

                        Box(
                            modifier = Modifier
                                .pointerInput(navItems.size) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            isDragging = true
                                            dragOffsetPx = 0f
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            val startIndex = currentIndexState.value
                                            val landedIndex =
                                                ((slotPitchPx * startIndex + dragOffsetPx) / slotPitchPx)
                                                    .roundToInt()
                                                    .coerceIn(0, navItems.size - 1)
                                            dragOffsetPx = 0f
                                            if (landedIndex != startIndex) {
                                                val targetRoute = navItems[landedIndex].first
                                                navController.navigate(targetRoute) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            dragOffsetPx = 0f
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx += dragAmount
                                    }
                                }
                        ) {
                            // 背面: 滑らかに追従するインジケーター(色付きピル)
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(0, animatedOffsetPx.roundToInt()) }
                                    .size(width = itemWidthDp, height = itemHeightDp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(accentColor)
                            )
                            // 前面: アイコン本体
                            Column(
                                verticalArrangement = Arrangement.spacedBy(settings.navBarButtonSpacing.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                navItems.forEachIndexed { index, (route, icon, label) ->
                                    val selected = index == currentIndex
                                    val iconTint by animateColorAsState(
                                        targetValue = if (selected) Color.White
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        label = "navIconTintVertical$index"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(itemWidthDp)
                                            .height(itemHeightDp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, label, tint = iconTint, modifier = Modifier.size((22 * navBarSizeScale).dp))
                                    }
                                }
                            }
                        }

                        // ===== 開閉トグル（ショートカットパネルの表示/非表示） =====
                        // パネルは左側に開くため、矢印は「閉じている時=左向き（開く方向を示す）」
                        // 「開いている時=右向き（閉じる=ピル側へ戻る方向を示す）」にする
                        // （水平ピルの上下矢印と同じ考え方を90°回転させたもの）。
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onToggleExpanded() }
                                .padding(
                                    horizontal = (8 * navBarSizeScale).dp,
                                    vertical = (14 * navBarSizeScale).dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (navBarExpanded)
                                    Icons.Default.KeyboardArrowRight
                                else
                                    Icons.Default.KeyboardArrowLeft,
                                contentDescription = if (navBarExpanded) "ショートカットを閉じる" else "ショートカットを開く",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size((22 * navBarSizeScale).dp)
                            )
                        }
                    }
                } // 内側Box（clip+背景）を閉じる
            } // 外側Box（影）を閉じる
        }
    }
}