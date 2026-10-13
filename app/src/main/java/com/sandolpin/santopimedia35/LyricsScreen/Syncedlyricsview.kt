package com.sandolpin.santopimedia35.lyricsscreen

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sandolpin.santopimedia35.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 同期歌詞ビュー（スクロール制御・offsetアニメ・間奏連携）

// ===== スクロール量の事前計算 =====
// offsetアニメの初期値設定に使用（スクロール実行前に呼ぶ）
private fun calcScrollDiff(
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    offsetPx: Int
): Float {
    if (state.layoutInfo.totalItemsCount == 0) return 0f
    val safeIndex = index.coerceIn(0, state.layoutInfo.totalItemsCount - 1)
    val info = state.layoutInfo
    val targetItem = info.visibleItemsInfo.firstOrNull { it.index == safeIndex }
    return if (targetItem != null) {
        (targetItem.offset - offsetPx).toFloat()
    } else {
        val avgH = if (info.visibleItemsInfo.isNotEmpty())
            info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size.toFloat()
        else 120f
        val firstIdx = state.firstVisibleItemIndex
        val firstOff = state.firstVisibleItemScrollOffset
        (safeIndex - firstIdx) * avgH - firstOff - offsetPx
    }
}

// ===== なめらかスクロールヘルパー =====
private suspend fun scrollWithAnim(
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Int,
    offsetPx: Int,
    durationMs: Long = 500L,
    easing: String = "decelerate",
    overshootDistancePercent: Int = 10, // オーバーシュートの行き過ぎ量(%): 3/5/10/15
    onConsumed: (Float) -> Unit = {}
): Float {
    if (state.layoutInfo.totalItemsCount == 0) return 0f
    val safeIndex = index.coerceIn(0, state.layoutInfo.totalItemsCount - 1)

    fun applyEasing(t: Float): Float = when (easing) {
        "overshoot" -> {
            // easeOutBack: 目標を一度行き過ぎてから戻ってくるバウンド感のある動き
            // c1=1.70158 のとき標準の「10%行き過ぎる」動きになるため、
            // 設定された行き過ぎ量(%)からその比率でc1を逆算する。
            val c1 = 1.70158f * (overshootDistancePercent / 10f)
            val c3 = c1 + 1f
            val u = t - 1f
            1f + c3 * u * u * u + c1 * u * u
        }
        "decelerate_circ" -> {
            // easeOutCirc: 円弧を描くような自然な減速カーブ
            val u = t - 1f
            kotlin.math.sqrt((1f - u * u).coerceAtLeast(0f))
        }
        "decelerate_quad" -> {
            // easeOutQuad: 最もマイルドな減速
            val u = 1f - t; 1f - u * u
        }
        "decelerate_quint" -> {
            // easeOutQuint: 強めの減速
            val u = 1f - t; 1f - u * u * u * u * u
        }
        "decelerate_expo" -> {
            // easeOutExpo: 序盤に急停止し、その後ゆっくり収束する独特の減速感
            if (t >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * t).toFloat()
        }
        else -> {
            // デフォルト = decelerate_quint と同じ
            val u = 1f - t; 1f - u * u * u * u * u
        }
    }

    val totalDiff = calcScrollDiff(state, safeIndex, offsetPx)
    if (kotlin.math.abs(totalDiff) < 2f) return 0f

    var consumedTotal = 0f
    state.scroll(scrollPriority = androidx.compose.foundation.MutatePriority.Default) {
        val startMs = System.currentTimeMillis()
        var prevScrolled = 0f
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val progress = applyEasing(t)
            val scrolled = totalDiff * progress
            val consumed = scrollBy(scrolled - prevScrolled)
            consumedTotal += consumed
            onConsumed(consumedTotal)
            prevScrolled = scrolled
            if (t >= 1f) break
            kotlinx.coroutines.delay(16)
        }
    }
    return totalDiff
}

// ===== 同期歌詞ビュー =====
@Composable
fun SyncedLyricsView(
    items: List<LyricItem>,
    currentPositionMs: Long,
    accentColor: Color,
    inactiveColor: Color = Color.Unspecified,
    futureColor: Color = Color.Unspecified,
    fontSize: Int = 34,
    fontWeight: Int = 700,
    lineHeightMultiplier: Float = 1.4f,
    lineSpacingDp: Float = 8f,
    inactiveLight: Boolean = true,
    interludeThresholdMs: Long = 7000L,
    multiLineThresholdMs: Long = 2500L,
    scrollSpeedMs: Int = 500,           // スクロール速さ
    scrollEasing: String = "decelerate", // スクロールeasing: "decelerate"/"accelerate_decelerate"/"bounce"
    offsetSync: String = "together",    // "together"=全行同時 / "stagger"=行ごとに遅延
    offsetDelayMs: Int = 50,            // offsetアニメ開始遅延(ms)
    offsetStaggerMs: Int = 50,          // staggerモード時の行ごと遅延間隔(ms)
    inactiveScale: Float = 0.98f,       // 非アクティブ歌詞の大きさ(0.97～1.00)
    inactiveBlur: Boolean = false,      // 非アクティブ歌詞をぼかす
    inactiveBlurRadius: Float = 1.5f,   // ぼかし強度(dp): 0=行距離に応じて自動調整
    scrollAnimType: String = "decelerate_quint", // スクロールアニメ種類
    overshootDistance: Int = 10,        // オーバーシュートの行き過ぎ量(%): 3/5/10/15
    fontPath: String = "",              // カスタムフォントのURI文字列
    forceInactiveOnEnd: Boolean = true, // endMs到達で強制非アクティブ(sdlrc)
    disableMultiShow: Boolean = false,  // sdlrc複数同時表示を無効化
    disableRight: Boolean = false,      // sdlrc右寄せを無効化
    onLineClick: (Long) -> Unit,
    viewModel: MediaPlayerViewModel,
    useOffsetAnim: Boolean = true,
    // ★ 歌詞画面(LyricsScreen.kt)で、歌詞のスクロール量から背景装飾のパララックス量を
    //   計算するために、LazyListStateを外側から受け取れるようにする。
    //   nullの場合（HomeScreenCの簡易表示・設定画面のプレビュー等）は従来通り
    //   内部で新しく生成する。
    externalListState: androidx.compose.foundation.lazy.LazyListState? = null,
    // 白文字+ぼかし縁取りにするか（歌詞画面の動的/ぼかし/グラデ/色混ぜ背景用）
    textOutline: Boolean = false
) {
    // currentIndex: 現在再生位置に対応するアクティブ行のインデックス
    // 「timeMs <= currentPositionMs」を満たす行のうち最後のもの
    // (同一timeMsが複数ある場合は先頭インデックスを使う)
    // ★ 最後の行の推定終了時刻を過ぎたら -1 に戻して全行非アクティブにする
    val currentIndex = remember(currentPositionMs, items) {
        val lines = items.filterIsInstance<LyricItem.Line>()
        val firstLineTime = lines.firstOrNull()?.timeMs ?: 0L
        if (currentPositionMs < firstLineTime) return@remember -1

        var lastMatchTime = -1L
        var lastMatchIdx  = -1
        for (i in items.indices) {
            val item = items[i]
            if (item is LyricItem.Line && item.timeMs <= currentPositionMs) {
                if (item.timeMs > lastMatchTime) {
                    lastMatchTime = item.timeMs
                    lastMatchIdx  = i
                }
            }
        }
        if (lastMatchIdx < 0) return@remember -1

        // 最後の行かどうか判定
        // ★ 最後の行だけは forceInactiveOnEnd の設定に関わらず、
        //   endMs（無ければ推定終了時刻）を過ぎたら強制的に非アクティブにする。
        //   これが無いと最後の行が再生終了までずっとアクティブ表示のままになるため。
        val lastLine = lines.lastOrNull()
        val matchedLine = items[lastMatchIdx]
        if (matchedLine is LyricItem.Line && matchedLine == lastLine) {
            val estimatedEndMs = if (matchedLine.endMs != -1L) {
                matchedLine.endMs
            } else {
                matchedLine.timeMs + 5_000L
            }
            if (currentPositionMs >= estimatedEndMs) return@remember -1
        }

        lastMatchIdx
    }

    // sdlrc以外(lrc)ではmultiShow・間奏ドットを使わない
    // lrcはbuildLyricItemsでInterludeを生成しないため、
    // Interludeが存在するか endMs != -1L の行があればsdlrc形式と判定
    val isSdlrc = remember(items) {
        items.any { it is LyricItem.Line && it.endMs != -1L } ||
                items.any { it is LyricItem.Interlude }
    }

    // ===== sdlrc設定の適用 =====
    // disableMultiShow=true の場合 multiShowIndices を強制空にする（isSdlrcより優先）
    val effectiveDisableMultiShow = disableMultiShow

    // ===== isRight判定 =====
    // disableRight=true の場合は isRight を全て false 扱い
    // isRight行が1つでもあれば(かつdisableRightでなければ) 85%幅、なければ 95%幅
    val hasRightLines = remember(items, disableRight) {
        !disableRight && items.any { it is LyricItem.Line && it.isRight }
    }
    val lyricLineWidth = if (hasRightLines) 0.85f else 0.95f

    // ===== multiShow判定（sdlrcのみ有効）=====
    // ★ 判定方法を「currentPositionMsに対して毎回再計算」ではなく、
    //   「currentIndexが変わった瞬間のcurrentPositionMsだけを使って、
    //   その時点で重なっている行を確定し、それをcurrentIndexが変わるまで維持する」
    //   方式に変更する。
    //
    //   （変更前の問題点）
    //   remember のキーに currentPositionMs を含めて「currentLineの時間範囲(固定値)と
    //   他の行の時間範囲(固定値)が重なっているか」を判定していたため、
    //   currentPositionMs の値に関係なく、currentLineが同じ行である間はずっと
    //   同じ判定結果になっていた。これにより、たとえばcurrentLineが行1
    //   （00:55.97〜01:00.94）である間（＝00:55.97の時点から）、本来は
    //   01:00.27にならないと重ならないはずの行3・行4的な離れた行までもが、
    //   時間に関係なく最初からmultiShow扱いされてしまう不具合の原因になっていた。
    //
    //   （修正後）
    //   currentIndexが切り替わった瞬間の currentPositionMs だけを使って
    //   「今まさに重なっている行」を1回だけ確定し、以後はcurrentIndexが
    //   変わるまでそのSetをそのまま保持する。currentPositionMsの経過では
    //   再計算しないため、後から時間経過で新しい行が巻き込まれることもなく、
    //   かつ「先に終わった行がendMs到達で消えない」という目的も両立できる。
    val multiShowIndices = remember(currentIndex, items) {
        if (!isSdlrc || effectiveDisableMultiShow ||
            currentIndex < 0 || currentIndex >= items.size) {
            emptySet()
        } else {
            val currentLine = items[currentIndex] as? LyricItem.Line
            if (currentLine == null || currentLine.endMs == -1L) emptySet()
            else {
                // インデックスが切り替わった時点で重なっている行を固定する。
                items.indices.filterTo(mutableSetOf()) { i ->
                    if (i == currentIndex) return@filterTo false
                    val item = items[i] as? LyricItem.Line ?: return@filterTo false
                    item.endMs != -1L && currentPositionMs >= item.timeMs &&
                            currentPositionMs < item.endMs && item.timeMs < currentLine.endMs
                }
            }
        }
    }

    val listState = externalListState ?: rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var scrollDirection by remember { mutableStateOf(1) }
    var prevIndex by remember { mutableStateOf(0) }
    var prevMultiShowIndices by remember { mutableStateOf(setOf<Int>()) }

    // ===== offsetアニメ用 =====
    // scrollTargetDiffPx は予定量、scrollDiffPx はLazyColumnが実際に消費した量。
    // リスト先頭では予定した上方向スクロールが境界で消費されず、予定量だけを
    // offsetに使うと歌詞だけが動いて見えるため、描画には実消費量を使う。
    var scrollTargetDiffPx by remember(items) { mutableStateOf(0f) }
    var scrollDiffPx by remember(items) { mutableStateOf(0f) }
    var offsetCycle by remember(items) { mutableIntStateOf(0) }
    var hasInitialAlignment by remember(items) { mutableStateOf(false) }

    // ===== 手動スクロール検知・ぼかし抑制 =====
    // isUserScrolling は「手を離した瞬間にfalse」になるため、
    // スクロール後すぐぼかしが戻ってしまう問題がある。
    // 代わりに isBlurSuppressed フラグを使い、以下のタイミングで制御する:
    //   ON : 手動スクロール開始時
    //   OFF: 歌詞行タップ時 / currentIndex が変化した時（次の歌詞に進んだ時）
    //        ただし間奏中は OFF にしない
    var isAutoScrolling by remember { mutableStateOf(false) }
    var isBlurSuppressed by remember { mutableStateOf(false) }

    // ===== 間奏がアクティブかどうか（isBlurSuppressed判定で使うため、この位置で先に計算しておく）=====
    val activeInterludeStartMs = remember(currentPositionMs, items, interludeThresholdMs) {
        items.filterIsInstance<LyricItem.Interlude>()
            .firstOrNull { item ->
                val gapMs = item.endMs - item.startMs
                gapMs >= interludeThresholdMs &&
                        currentPositionMs >= item.startMs &&
                        currentPositionMs < item.endMs
            }?.startMs ?: -1L
    }

    // 手動スクロール開始を検知してぼかし抑制をON
    // ★ 間奏中（activeInterludeStartMs >= 0L）は、間奏スクロールに伴う
    //   isScrollInProgressの一時的な変化を手動スクロールと誤認しないよう、
    //   ぼかし抑制自体をかけない保険を入れる。
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAutoScrolling && activeInterludeStartMs < 0L) {
            isBlurSuppressed = true
        }
    }

    // ★ 自動スクロールが完了したタイミング（true→falseに変わった瞬間）でぼかし抑制をOFFに戻す。
    //   LaunchedEffect(isAutoScrolling) だと「初回コンポーズ時にfalseである」ことでも
    //   発火してしまい、手動スクロール中のisBlurSuppressed=trueまで巻き込んで
    //   即座にリセットしてしまう。そのため true→false の遷移だけを検知する。
    LaunchedEffect(Unit) {
        var wasAutoScrolling = isAutoScrolling
        snapshotFlow { isAutoScrolling }.collect { scrolling ->
            if (wasAutoScrolling && !scrolling) {
                isBlurSuppressed = false
            }
            wasAutoScrolling = scrolling
        }
    }

    // ===== 間奏スクロール =====
    LaunchedEffect(activeInterludeStartMs) {
        if (activeInterludeStartMs < 0L) return@LaunchedEffect
        val interludeIdx = items.indexOfFirst {
            it is LyricItem.Interlude && it.startMs == activeInterludeStartMs
        }
        if (interludeIdx < 0) return@LaunchedEffect
        val viewportH2 = listState.layoutInfo.viewportSize.height
        // ★ isAutoScrolling を立てずに scrollWithAnim を呼ぶと、
        //   内部の実スクロールが listState.isScrollInProgress を true にし、
        //   「手動スクロールが起きた」と誤判定されて isBlurSuppressed が true になり
        //   間奏中ずっとぼかしが解除されたままになっていた。currentIndex用のスクロールと
        //   同様に isAutoScrolling を正しく管理する。
        isAutoScrolling = true
        scrollWithAnim(listState, interludeIdx, (viewportH2 * 0.15f).toInt(),
            durationMs = scrollSpeedMs.toLong(), easing = scrollEasing)
        isAutoScrolling = false
    }

    LaunchedEffect(currentIndex, items) {
        if (currentIndex < 0) {
            // 歌い終わりに currentIndex が -1 へ移る際、前回のスクロール量を
            // 残すと全行が future 扱いになり、歌詞が上へ流れてしまう。
            scrollDiffPx = 0f
            scrollTargetDiffPx = 0f
            offsetCycle++
            return@LaunchedEffect
        }

        // 初回は LazyColumn の測定前に LaunchedEffect が走ることがある。
        // 実際の表示領域が決まってから初期位置を合わせる。
        snapshotFlow {
            listState.layoutInfo.totalItemsCount > 0 &&
                    listState.layoutInfo.viewportSize.height > 0 &&
                    listState.layoutInfo.visibleItemsInfo.isNotEmpty()
        }.first { it }

        val wasMulti = prevMultiShowIndices.isNotEmpty()
        val isMultiNow = multiShowIndices.isNotEmpty()
        prevMultiShowIndices = multiShowIndices

        scrollDirection = if (currentIndex >= prevIndex) 1 else -1
        prevIndex = currentIndex

        val viewportH = listState.layoutInfo.viewportSize.height

        // アクティブ行が画面上端から15%の位置に来るように設定
        // （1つ前の行がちらっと見えるくらいの余白）
        val topOffset = (viewportH * 0.15f).toInt()

        val allActiveIndices = (multiShowIndices + currentIndex)
            .filter { it >= 0 }.toSortedSet()
        val topScrollIndex = allActiveIndices.firstOrNull()?.coerceAtLeast(0) ?: currentIndex

        // スクロール先を決定:
        // multiShow時・通常時どちらも topScrollIndex（最上部アクティブ行）を使用
        val target = when {
            isMultiNow  -> topScrollIndex   // multiShow: 複数行の中で最上の行へ
            wasMulti    -> currentIndex     // multiShow終了直後: currentIndexへ
            else        -> currentIndex     // 通常: currentIndexへ
        }

        if (!hasInitialAlignment) {
            listState.scrollToItem(target, -topOffset)
            hasInitialAlignment = true
            prevMultiShowIndices = multiShowIndices
            prevIndex = currentIndex
            scrollDiffPx = 0f
            scrollTargetDiffPx = 0f
            offsetCycle++
            return@LaunchedEffect
        }

        val diff = if (currentIndex > 0) calcScrollDiff(listState, target, topOffset) else 0f
        scrollTargetDiffPx = if (kotlin.math.abs(diff) >= 2f) diff else 0f
        scrollDiffPx = 0f
        offsetCycle++

        val resolvedEasing = scrollAnimType

        // multiShow時も含めて常にスクロールを実行する
        when {
            currentIndex <= 0 -> { /* 先頭はスクロール不要 */ }
            else -> {
                isAutoScrolling = true
                try {
                    scrollWithAnim(listState, target, topOffset,
                        durationMs = scrollSpeedMs.toLong(), easing = resolvedEasing,
                        overshootDistancePercent = overshootDistance,
                        onConsumed = { scrollDiffPx = it })
                } finally {
                    isAutoScrolling = false
                }
            }
        }
    }

    // カスタムフォントの読み込み（URIのストリームをキャッシュファイルにコピーしてTypeface生成）
    val context = androidx.compose.ui.platform.LocalContext.current
    val customFontFamily = remember(fontPath) {
        if (fontPath.isEmpty()) return@remember null
        try {
            val uri = android.net.Uri.parse(fontPath)
            // コンテンツURIは直接Typefaceに渡せないため、
            // キャッシュディレクトリに一時コピーしてからファイルパスで読み込む
            val cacheFile = java.io.File(context.cacheDir, "lyrics_custom_font.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            val typeface = android.graphics.Typeface.createFromFile(cacheFile)
            FontFamily(typeface)
        } catch (e: Exception) {
            android.util.Log.e("SantopiMedia", "フォント読み込み失敗", e)
            null
        }
    }

    // フォントサイズに応じた水平余白
    val hPadding = (32 - (fontSize - 20).coerceIn(0, 20)).coerceAtLeast(8).dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = hPadding, end = hPadding,
                top = 16.dp, bottom = 600.dp
            ),
            verticalArrangement = Arrangement.spacedBy(lineSpacingDp.dp)
        ) {
            itemsIndexed(items) { index, item ->
                when (item) {
                    is LyricItem.Line -> {
                        // 間奏中かどうか: 次のアイテムがInterludeで現在位置がその範囲内
                        val nextItem = items.getOrNull(index + 1)
                        val isDuringInterlude = nextItem is LyricItem.Interlude &&
                                currentPositionMs >= nextItem.startMs &&
                                currentPositionMs < nextItem.endMs

                        // 間奏中は currentIndex 行も非アクティブ(isPast)扱いにする
                        // forceInactiveOnEnd: sdlrc行でendMs設定済みかつ現在位置がendMsを超えたら非アクティブ
                        // ★ カラオケ行(isKaraoke)はワイプ表現の性質上、endMsを過ぎたら
                        //   「歌い終わった」状態を見た目に反映する必要があるため、
                        //   forceInactiveOnEnd の設定に関わらず常にendMs判定を適用する。
                        // ★ ただしこの判定は「単独アクティブ行(isActive)」にのみ適用する。
                        //   multiShow中の行(isMulti)にも適用すると、2行同時表示中に
                        //   先に発声が終わった方の行だけが自分のendMs到達で強制非表示になり、
                        //   「2行あるのに1行しかワイプ表示されない」原因になっていたため。
                        //   multiShow行はcurrentIndex行の表示が終わるまで表示を維持する。
                        val pastEndMs = (forceInactiveOnEnd || item.isKaraoke) &&
                                item.endMs != -1L &&
                                currentPositionMs >= item.endMs
                        val isActive = currentIndex >= 0 && index == currentIndex && !isDuringInterlude && !pastEndMs
                        val isMulti  = currentIndex >= 0 && multiShowIndices.contains(index) && !isDuringInterlude
                        val isPast   = currentIndex >= 0 && (index < currentIndex || (index == currentIndex && isDuringInterlude)) && !isMulti

                        val isFuture = index > currentIndex && !isMulti
                        val isAnimTarget = currentIndex >= 0 && (isActive || isMulti || isPast || isFuture)

                        // distFromActive: ぼかし強度の自動計算用（Composableスコープで算出）
                        val distFromActive = when {
                            index > currentIndex -> (index - currentIndex).coerceAtLeast(0)
                            else                 -> (currentIndex - index).coerceAtLeast(0)
                        }

                        // 1f = スクロール相殺なし、0f = 相殺を保持中。
                        val releaseAnim = remember { Animatable(1f) }

                        // 行高さの推定値（可視アイテムの平均、なければfontSize*lineHeight*densityで推定）
                        val estItemH = with(density) {
                            listState.layoutInfo.visibleItemsInfo
                                .map { it.size }.average()
                                .takeIf { !it.isNaN() && it > 0.0 }?.toFloat()
                                ?: (fontSize * lineHeightMultiplier * 1.5f).dp.toPx()
                        }
                        // 余白 = 行高さの45%
                        val maxOffsetPx = estItemH * 0.45f

                        // ===== currentIndex と scrollDirection を State として渡す =====
                        // LaunchedEffect は起動時の値をキャプチャするが、
                        // scrollDirection は var mutableStateOf のため再コンポーズ前の値を
                        // キャプチャしてしまう。
                        // snapshotFlow で State の最新値をコルーチン内で確実に読む。
                        LaunchedEffect(offsetCycle, currentIndex, multiShowIndices) {
                            if (!useOffsetAnim || !isAnimTarget) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            val rawOffset = kotlin.math.abs(scrollTargetDiffPx).coerceIn(0f, maxOffsetPx)
                            if (rawOffset == 0f) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            // LaunchedEffect 内で scrollDirection・行位置を再評価
                            val fwd = scrollDirection >= 0
                            val activeGroup = multiShowIndices + currentIndex
                            val firstActiveIndex = activeGroup.minOrNull() ?: currentIndex
                            val lastActiveIndex = activeGroup.maxOrNull() ?: currentIndex
                            val thisIsActive = activeGroup.contains(index)
                            val thisIsFuture = index > lastActiveIndex
                            val thisIsPast   = index < firstActiveIndex

                            // 前進時の過去行は、画面外から遅れて compose されることがある。
                            // その時点で offset を始めると、アクティブ行の上で後追いの
                            // ガクつきが起きるため、過去行は今回の遅延対象にしない。
                            if (fwd && thisIsPast) {
                                releaseAnim.snapTo(1f)
                                return@LaunchedEffect
                            }

                            // ===== 遅延設計 =====
                            // 順スクロール（前進）:
                            //   past/active = 0ms（即時）
                            //   future      = 100ms + stagger
                            // 逆スクロール（後退）:
                            //   past        = 0ms（即時）
                            //   active      = 50ms
                            //   future      = 100ms + stagger
                            val thisDist = when {
                                thisIsFuture -> (index - lastActiveIndex).coerceAtLeast(0)
                                thisIsPast   -> (firstActiveIndex - index).coerceAtLeast(0)
                                else         -> 0
                            }
                            val baseDelayMs = offsetDelayMs.toLong()
                            val widenedStaggerMs = (offsetStaggerMs * 1.8f).toLong()
                            val thisDelayMs = when {
                                fwd && thisIsFuture ->
                                    if (offsetSync == "stagger")
                                        baseDelayMs + (thisDist - 1).coerceAtLeast(0) * widenedStaggerMs
                                    else baseDelayMs
                                !fwd && thisIsActive -> baseDelayMs
                                !fwd && thisIsFuture ->
                                    if (offsetSync == "stagger")
                                        baseDelayMs + (thisDist - 1).coerceAtLeast(0) * widenedStaggerMs
                                    else baseDelayMs
                                else -> 0L  // 順スクロールのpast/active、逆スクロールのpastは即時
                            }

                            // スクロール量を保持して表示位置を一時停止させ、
                            // 行ごとの遅延後に解除する。下方向への移動は発生しない。
                            releaseAnim.snapTo(0f)
                            kotlinx.coroutines.delay(thisDelayMs)
                            releaseAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (scrollSpeedMs * 0.70f).toInt().coerceAtLeast(1),
                                    easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)
                                )
                            )
                        }
                        // 毎フレーム変わる実スクロール量とreleaseAnimはgraphicsLayer内で読む。
                        // LazyColumnの行コンポーズを毎フレーム再実行せず、描画位置だけ更新する。
                        val rowTranslationYProvider: () -> Float = {
                            if (useOffsetAnim && isAnimTarget) {
                                val scrolledPx = scrollDiffPx
                                val cappedScrolledPx = if (scrolledPx > 0f)
                                    scrolledPx.coerceAtMost(maxOffsetPx)
                                else scrolledPx.coerceAtLeast(-maxOffsetPx)
                                cappedScrolledPx * (1f - releaseAnim.value)
                            } else 0f
                        }

                        if (item.isKaraoke) {
                            // ===== カラオケ行: ワイプアニメーション =====
                            KaraokeLineItem(
                                karaoke = item.karaoke,
                                currentPositionMs = currentPositionMs,
                                isActive = isActive || isMulti,
                                isPast = isPast,
                                accentColor = accentColor,
                                inactiveColor = if (inactiveColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeightMultiplier = lineHeightMultiplier,
                                inactiveScale = inactiveScale,
                                fontFamily = customFontFamily,
                                translationYProvider = rowTranslationYProvider,
                                isRight = if (disableRight) false else item.isRight,
                                lineWidthFraction = lyricLineWidth,
                                textOutline = textOutline,
                                onClick = {
                                    isBlurSuppressed = false
                                    coroutineScope.launch { onLineClick(item.timeMs) }
                                }
                            )
                        } else {
                            LyricLineItem(
                                text = item.text,
                                isActive = isActive || isMulti,
                                isPast = isPast,
                                accentColor = accentColor,
                                inactiveColor = if (inactiveColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else inactiveColor,
                                futureColor = if (futureColor == Color.Unspecified)
                                    MaterialTheme.colorScheme.onSurface.copy(0.35f) else futureColor,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                lineHeightMultiplier = lineHeightMultiplier,
                                inactiveLight = inactiveLight,
                                inactiveScale = inactiveScale,
                                // ★ 間奏中でもぼかしを解除しないよう activeInterludeStartMs による条件を撤去
                                inactiveBlur = inactiveBlur && !isBlurSuppressed,
                                inactiveBlurRadius = inactiveBlurRadius,
                                distFromActive = distFromActive,
                                fontFamily = customFontFamily,
                                translationYProvider = rowTranslationYProvider,
                                isRight = if (disableRight) false else item.isRight,
                                lineWidthFraction = lyricLineWidth,
                                textOutline = textOutline,
                                onClick = {
                                    isBlurSuppressed = false
                                    coroutineScope.launch { onLineClick(item.timeMs) }
                                }
                            )
                        }
                    }
                    is LyricItem.Interlude -> {
                        val gapMs = item.endMs - item.startMs
                        // ★ sdlrc以外・閾値未満の間奏は「表示しない」のではなく
                        //   isActive を常にfalse扱いにすることで対応する。
                        //   if分岐でコンポーザブル自体の呼び出しをスキップすると、
                        //   間奏終了の瞬間に InterludeIndicator がコンポジションから
                        //   完全に消滅し、内部で用意している縮小・フェードアウト
                        //   アニメーションが実行される前に強制終了してしまう
                        //   （LazyColumn側のレイアウトも同時に変化してガクつきの原因になる）。
                        //   常にコンポーズし続け、表示すべきでない場合は isActive=false を
                        //   渡すことで、InterludeIndicator内部の「常時コンポーズ・高さ固定・
                        //   scaleYのみアニメーション」という設計を活かす。
                        val qualifiesAsInterlude = isSdlrc && gapMs >= interludeThresholdMs
                        val isActive = qualifiesAsInterlude &&
                                currentPositionMs >= item.startMs &&
                                currentPositionMs < item.endMs
                        val progress = if (isActive)
                            ((currentPositionMs - item.startMs).toFloat() /
                                    (item.endMs - item.startMs).toFloat()).coerceIn(0f, 1f)
                        else 0f
                        val remainingMs = if (isActive) item.endMs - currentPositionMs else 0L
                        // 次の行が右寄せかどうか取得
                        val nextIsRight = items.getOrNull(index + 1)
                            .let { it is LyricItem.Line && it.isRight }

                        InterludeIndicator(
                            accentColor = accentColor,
                            isActive = isActive,
                            progress = progress,
                            remainingMs = remainingMs,
                            isRight = nextIsRight,
                            onClick = { coroutineScope.launch { onLineClick(item.startMs) } }
                        )
                    }
                }
            }
        }
    } // Box
}