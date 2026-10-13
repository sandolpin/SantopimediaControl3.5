package com.sandolpin.santopimedia35.mainactivity

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.remember.ArtistRankItem
import com.sandolpin.santopimedia35.remember.HistoryScreen
import com.sandolpin.santopimedia35.remember.HistoryViewModel
import com.sandolpin.santopimedia35.remember.RankingDetailScreen
import com.sandolpin.santopimedia35.remember.ReportPeriod
import com.sandolpin.santopimedia35.remember.ReportScreen
import com.sandolpin.santopimedia35.remember.TrackHistoryDetailScreen
import com.sandolpin.santopimedia35.settingscreen.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// アプリ本体: ナビゲーション・水平ピル・オーバーレイ画面の出し入れ

@Composable
fun SantopimediaApp() {
    val context = LocalContext.current
    val viewModel: MediaPlayerViewModel = viewModel(
        factory = MediaPlayerViewModel.Factory(context)
    )

    // ===== バックグラウンド履歴記録の常駐通知用: POST_NOTIFICATIONS権限リクエスト（Android 13+）=====
    // 拒否されてもHistoryService自体（バックグラウンド記録）は動作するが、
    // 常駐通知が表示されなくなるため、OSやメーカー製バッテリー最適化に
    // よってプロセスごと終了させられるリスクは残る。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 結果に関わらず特別な処理は不要（拒否時は通知が出ないだけ）*/ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ===== 音声出力先のLDACコーデック表示用: BLUETOOTH_CONNECT権限リクエスト（Android 12+）=====
    // 拒否されても音声出力先の表示自体（スピーカー/ヘッドホン/Bluetooth機器名/USB-DAC）は
    // 動作するが、Bluetooth接続時のコーデック名（LDAC等）の判定だけができなくなる
    // （AudioOutputHelper.kt側でtry-catchにより安全にフォールバックする設計のため、
    //   権限が無くてもクラッシュはしない）。
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 結果に関わらず特別な処理は不要（拒否時はLDAC表記が出ないだけ）*/ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    val settings by viewModel.settings.collectAsState()
    val mediaState by viewModel.mediaState.collectAsState()
    val accentColor = if (settings.extractColorFromArt) mediaState.dominantColor
    else MaterialTheme.colorScheme.primary
    val darkTheme = when (settings.themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> isSystemInDarkTheme()
    }
    SystemBarColorEffect(accentColor = accentColor, darkTheme = darkTheme)
    val navController = rememberNavController()
    // shareScreenshot用のスコープはここで宣言する
    // ActionMenuDialog内で宣言するとダイアログが閉じた瞬間にキャンセルされてしまう
    val appScope = rememberCoroutineScope()
    var showActionMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // 歌詞画面の三点メニュー「歌詞設定を開く」で設定タブに切り替えたとき、
    // 設定TOPではなく「歌詞」ページを直接開くためのフラグ
    var jumpToLyricsSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }          // グラフ+ランキング統合画面
    var showRankingDetail by remember { mutableStateOf(false) }
    var showTrackHistory by remember { mutableStateOf(false) }    // 聴いた曲「もっと見る」画面
    var trackHistoryPeriod by remember { mutableStateOf<ReportPeriod>(ReportPeriod.Week(0)) }
    var showProperty by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var selectedArtist by remember { mutableStateOf<ArtistRankItem?>(null) }

    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(context)
    )

    // アクティブセッション全体をHistoryViewModelに通知（全アプリの時間を記録）
    val mediaStateForHistory by viewModel.mediaState.collectAsState()

    // ===== 二重記録防止 =====
    // 「バックグラウンドで履歴記録を許可」がONのときは、フォアグラウンド中も含めて
    // 常にHistoryService側だけが記録の担当になる（HistoryServiceはアプリの表示状態に
    // 関わらず常時MediaControllerを監視できるため）。この画面（Activity）側では
    // 二重に記録しないよう、onMediaStateChanged自体を呼び出さない。
    // OFFのときは今まで通りこの画面側だけが記録する（HistoryServiceは起動されない）。
    // ★ 以前は「フォアグラウンドはActivity、バックグラウンドはService」と都度
    //   引き継がせる設計だったが、切り替えタイミングの同期が難しく、二重記録・
    //   記録漏れの両方が起こり得た。「設定ONなら常にServiceだけ」という単純な
    //   役割分担にすることで、同時に2つが動くこと自体を構造的に無くしている。
    val mediaHistoryKey = "${mediaStateForHistory.title}|${mediaStateForHistory.artist}|" +
            "${mediaStateForHistory.isPlaying}|${mediaStateForHistory.packageName}"
    LaunchedEffect(mediaHistoryKey, settings.backgroundHistoryEnabled) {
        if (settings.backgroundHistoryEnabled) {
            // ★ 起動直後、DataStoreから実際の設定値が読み込まれるまでの一瞬は
            //   settings.backgroundHistoryEnabled がデフォルト値(false)のままになる。
            //   その一瞬の間に下のonMediaStateChanged()が誤って呼ばれ、
            //   HistoryViewModel内部の5秒おき自動保存タイマーが起動してしまうことがある。
            //   その後すぐ本当の値(true)に切り替わってこのブロックに来るが、
            //   「以後呼ばない」だけでは、すでに動き出したタイマーは止まらず
            //   裏で動き続けてしまう（実際に発生した二重記録の直接の原因）。
            //   そのため設定ONの間は毎回、念のためpauseTracking()を呼んで
            //   万一動いているタイマーがあれば確実に止める。
            //   すでに停止済みなら中身はほぼ何もしない軽い呼び出しなので、
            //   頻繁に呼ばれても問題ない。
            historyViewModel.pauseTracking()
            return@LaunchedEffect
        }
        historyViewModel.onMediaStateChanged(mediaStateForHistory)
    }

    // 再生中は5秒ごとにpositionを最新化（記録主体がこの画面側のときのみ）
    LaunchedEffect(mediaStateForHistory.isPlaying, settings.backgroundHistoryEnabled) {
        if (settings.backgroundHistoryEnabled) return@LaunchedEffect
        if (!mediaStateForHistory.isPlaying) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5_000)
            if (!mediaStateForHistory.isPlaying) break
            historyViewModel.updatePosition(mediaStateForHistory.currentPositionMs)
        }
    }

    // BackHandler: 正しい階層で戻る
    // history → report（グラフ・ランキング統合） → rankingDetail / trackHistory
    // rankingDetail の戻り  → report へ（showRankingDetail=false のみ、showReportは維持）
    // trackHistory の戻り  → report へ（showTrackHistory=false のみ、showReportは維持）
    // report の戻り        → history へ（showReport=false）
    // history の戻り       → プレーヤーへ（showHistory=false）
    val topScreen = when {
        showRankingDetail -> "rankingDetail"
        showTrackHistory  -> "trackHistory"
        showReport        -> "report"
        showHistory       -> "history"
        showFavorites     -> "favorites"
        showLyrics        -> "lyrics"
        showProperty      -> "property"
        showActionMenu    -> "actionMenu"
        else              -> ""
    }
    BackHandler(enabled = topScreen == "rankingDetail") { showRankingDetail = false }
    BackHandler(enabled = topScreen == "trackHistory")  { showTrackHistory = false }
    BackHandler(enabled = topScreen == "report")        { showRankingDetail = false; showTrackHistory = false; showReport = false }
    BackHandler(enabled = topScreen == "history")       { showHistory = false }
    BackHandler(enabled = topScreen == "favorites")     { showFavorites = false }
    BackHandler(enabled = topScreen == "lyrics")        { showLyrics = false }
    BackHandler(enabled = topScreen == "property")      { showProperty = false }
    BackHandler(enabled = topScreen == "actionMenu")    { showActionMenu = false }

    val navItems = listOf(
        Triple("player", Icons.Default.MusicNote, "プレーヤー"),
        Triple("app_select", Icons.Default.Apps, "アプリ"),
        Triple("settings", Icons.Default.Settings, "設定"),
    )

    // ★ ステータスバー側で行った対応と同じ考え方をナビゲーションバー側にも適用する。
    //   Scaffold自体に色を塗る/透明にする対応(navBarColor・PlayerLikeBackground・
    //   windowBackground transparent化)はいずれも「複製した背景」や「Window自体の色」を
    //   使っており、実際のプレーヤー画面の背景と食い違いが生じたり、他画面で浮いて見える
    //   問題があった。
    //   正しい対応は、ステータスバーのときと同様「コンテンツ(プレーヤー画面)自体の背景を
    //   画面の本当の端まで伸ばす」こと。そのためにはbottomBar(ピル)の実際の高さを
    //   測定し、コンテンツ側の"操作要素"だけをその高さぶん押し上げる必要がある
    //   （背景は伸ばしたまま、ボタン等だけが安全な位置にくるように）。
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // ★ ナビゲーションバー下のショートカットパネル（背景スタイル・ホーム画面スタイル・
    //   ナビゲーションバー背景をワンタップで切り替えられるクイック設定）の開閉状態
    var navBarExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = navBarExpanded) { navBarExpanded = false }

    // ===== 横画面判定・縦向きナビゲーションバー =====
    // ★ 横画面のときは下部の水平ピル(bottomBar)ではなく、画面右端に縦向きで
    //   ピルを表示する（VerticalNavBar、このファイル下部で定義）。
    //   bottomBarHeightDpと同じ考え方で、縦向きピルの実測「幅」をendBarWidthDpに
    //   保持し、アプリ切り替え・設定・プレーヤー(UI-A)画面の操作要素が
    //   縦向きピルに隠れないよう、その幅ぶんの余白として使う。
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var endBarWidthDp by remember { mutableStateOf(0.dp) }
    // 履歴・歌詞・レポート等のオーバーレイ表示中はナビゲーションバー自体を隠す
    // （bottomBar側の非表示条件と同じ）。縦向きピルもこの条件を共有する。
    val hideNavBar = showHistory || showLyrics || showReport || showRankingDetail || showTrackHistory || showFavorites

    // ★ 横画面時に画面右端へ重ねて表示するVerticalNavBar（縦向きピル）は、
    //   Scaffold自体の外側（bottomBarスロットではない、通常のBoxオーバーレイ）に
    //   配置する必要があるため、Scaffold全体をBoxで包む。
    //   これはピル型ナビゲーションバーが「画面の端に重ねて表示され、
    //   コンテンツ側は操作要素にだけ余白を持たせる」という、既存の水平ピルと
    //   同じ設計方針を縦向きにもそのまま踏襲したもの。
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // 履歴・歌詞・レポート表示中はナビゲーションバーを非表示
                // ★ 横画面のときは水平ピル自体を描画しない（VerticalNavBarが右端に表示される）。
                //   Scaffold側が確保するbottomの余白も0になる。
                if (hideNavBar || isLandscape) return@Scaffold

                // ★ ナビゲーションバーの背景色: 設定(navBarBackgroundStyle)で2パターンから選べる。
                //   "accent": 従来通りaccentColorをHSVで彩度・明度調整した色を使う
                //   "solid" : 曲・アルバムアートに関わらず常に同じ単色（ダーク/ライトで固定色）を使う
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

                // ★ ナビゲーションバー全体の大きさ(1〜5段階)を倍率に変換。
                //   3(標準)を基準に、上下15%ずつの幅で小さく/大きくする。
                val navBarSizeScale = when (settings.navBarSizeLevel) {
                    1 -> 0.85f
                    2 -> 0.925f
                    3 -> 1.0f
                    4 -> 1.075f
                    else -> 1.15f
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ===== ショートカットパネル（展開時のみ）=====
                    // ピルの高さ測定(bottomBarHeightDp)には含めない
                    // （パネルの開閉でプレーヤー画面側の余白が変動しないようにするため、
                    //   下の「コンパクトピル」側のBoxだけを測定対象にしている）
                    AnimatedVisibility(
                        visible = navBarExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        NavShortcutPanel(
                            viewModel = viewModel,
                            settings = settings,
                            accentColor = accentColor
                        )
                    }

                    // ===== コンパクトピル（アイコンを中央寄せ）=====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // ★ このBox自体には背景色を敷かない（透明のまま）。
                            //   代わりに、このBoxの実際の高さ(ピル+余白+システムナビゲーションバー分)を
                            //   測定してbottomBarHeightDpに保持し、プレーヤー画面側の
                            //   「操作要素の下端の余白」として使う（背景側の制約には使わない）。
                            .onGloballyPositioned { coords ->
                                bottomBarHeightDp = with(density) { coords.size.height.toDp() }
                            }
                            .padding(horizontal = 16.dp)
                            .padding(
                                bottom = WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding() + 4.dp,
                                top = 4.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // ★ 影(shadow)と半透明背景(alpha付きbackground)を同じSurfaceで一緒に
                        //   描画すると、透明度を下げたときに角丸の外側まで巻き込んで
                        //   不透明な矩形として合成され、「白いバー」が透けて見える不具合の
                        //   原因になっていた（実際に発生した不具合）。
                        //   AlbumArtCard(HomescreenA.kt)と同じ考え方で、
                        //   「外側Box=影専用（clipしない）」「内側Box=clip+半透明背景」に
                        //   分離することで、影の描画が半透明部分を侵食しないようにする。
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
                                Row(
                                    modifier = Modifier
                                        .padding(
                                            horizontal = (10 * navBarSizeScale).dp,
                                            vertical = (6 * navBarSizeScale).dp
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(settings.navBarButtonSpacing.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ===== スライド式ナビアイテム（Liquid Glassのタブバーのように、
                                    //       選択中インジケーター(色付きピル)が項目間を滑らかに追従する）=====
                                    // タップだけでなく、アイコン列を横にドラッグしてもピルが指に追従し、
                                    // 指を離した位置に一番近い項目へスナップ・遷移する。
                                    val itemWidthDp = (52 * navBarSizeScale).dp
                                    val itemHeightDp = (38 * navBarSizeScale).dp
                                    val itemWidthPx = with(density) { itemWidthDp.toPx() }
                                    // ★ アイコン同士の間隔(navBarButtonSpacing)も、インジケーターが
                                    //   移動する1コマ分の距離（スロットの間隔=slotPitchPx）に含める。
                                    //   これが無いと、アイコン間に余白を追加してもインジケーターだけ
                                    //   間隔ゼロの位置（詰めた状態）を移動してしまい、アイコンとズレる。
                                    val spacingPx = with(density) { settings.navBarButtonSpacing.dp.toPx() }
                                    val slotPitchPx = itemWidthPx + spacingPx

                                    val currentIndex = remember(currentDestination, navItems) {
                                        navItems.indexOfFirst { (route, _, _) ->
                                            currentDestination?.hierarchy?.any { it.route == route } == true
                                        }.coerceAtLeast(0)
                                    }
                                    // ★ pointerInput(navItems.size)のブロックは navItems.size が変わらない限り
                                    //   一度起動したコルーチンがそのまま使い回される（再起動されない）。
                                    //   そのため、onDragEnd内で currentIndex を直接クロージャに捕まえてしまうと、
                                    //   「最初にこのコルーチンが起動した瞬間の古い値」に固定されてしまい、
                                    //   1回目の切り替え後は2回目以降のドラッグ判定がズレて
                                    //   常に「切り替わらず元の位置へ戻る」不具合の原因になっていた。
                                    //   rememberUpdatedStateでラップし、onDragEnd内では必ず
                                    //   currentIndexState.value（最新値）を読むようにする。
                                    val currentIndexState = rememberUpdatedState(currentIndex)

                                    // ドラッグ中に指に追従させるためのオフセット（px）。
                                    // ドラッグしていない間は常に0で、現在位置(currentIndex)通りに表示される。
                                    var dragOffsetPx by remember { mutableStateOf(0f) }
                                    var isDragging by remember { mutableStateOf(false) }

                                    val baseOffsetPx = slotPitchPx * currentIndex
                                    val maxOffsetPx = slotPitchPx * (navItems.size - 1)
                                    val targetOffsetPx = if (isDragging) {
                                        (baseOffsetPx + dragOffsetPx).coerceIn(0f, maxOffsetPx)
                                    } else {
                                        baseOffsetPx
                                    }
                                    // ドラッグ中は指にすぐ追従(高剛性)、指を離した後は
                                    // Liquid Glassらしい柔らかいバウンド感のあるスプリングで着地させる
                                    val animatedOffsetPx by animateFloatAsState(
                                        targetValue = targetOffsetPx,
                                        animationSpec = if (isDragging)
                                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                                        else
                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "navSlideIndicator"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .pointerInput(navItems.size) {
                                                detectHorizontalDragGestures(
                                                    onDragStart = {
                                                        isDragging = true
                                                        dragOffsetPx = 0f
                                                    },
                                                    onDragEnd = {
                                                        isDragging = false
                                                        // ★ 必ず最新のindexを使う（上のコメント参照）
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
                                                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                                                .size(width = itemWidthDp, height = itemHeightDp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(accentColor)
                                        )
                                        // 前面: アイコン本体（タップでも直接遷移できる）
                                        // ★ spacedByで実際に見た目のアイコン間隔を空け、
                                        //   その間隔ぶんも上のslotPitchPxに含めてインジケーターの
                                        //   移動量を計算しているため、アイコンとインジケーターの
                                        //   位置がズレずに一致する。
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(settings.navBarButtonSpacing.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            navItems.forEachIndexed { index, (route, icon, label) ->
                                                val selected = index == currentIndex
                                                val iconTint by animateColorAsState(
                                                    targetValue = if (selected) Color.White
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                                    label = "navIconTint$index"
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

                                    // ===== 開閉トグル（ショートカットパネルの表示/非表示）=====
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { navBarExpanded = !navBarExpanded }
                                            .padding(
                                                horizontal = (14 * navBarSizeScale).dp,
                                                vertical = (8 * navBarSizeScale).dp
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (navBarExpanded)
                                                Icons.Default.KeyboardArrowDown
                                            else
                                                Icons.Default.KeyboardArrowUp,
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
        ) { innerPadding ->
            // ★ 以前はここで一括してinnerPadding(bottom=ピルナビゲーションバーの高さを含む)を
            //   適用していたため、プレーヤー画面自身の背景もその手前で止まっており、
            //   ピルの裏には何も描画されない状態だった。
            //   ステータスバーのときと同じ考え方で、"player"ルートだけ背景を画面の本当の端まで
            //   伸ばすため、ここではbottomを適用しない(topのみ適用。現状contentWindowInsets=0の
            //   ため0だが、将来の変更に備えて明示的に読む)。
            //   代わりに、実際に操作するボタン等が隠れないよう、bottomBarHeightDpを
            //   PlayerScreenWithSwipe側に渡し、その内部で操作要素にだけ余白を持たせる。
            //   app_select・settingsルートはこれまで通りinnerPaddingを丸ごと適用し、
            //   ピルナビゲーションバー分の余白を自動確保したままにする(変更なし・影響なし)。
            Box(modifier = Modifier.fillMaxSize()) {

                // ルートのインデックス（スライド方向の判定用）
                val routeOrder = listOf("player", "app_select", "settings")

                NavHost(
                    navController = navController,
                    startDestination = "player"
                ) {
                    composable(
                        route = "player",
                        enterTransition = {
                            val from = initialState.destination.route ?: ""
                            val fromIdx = routeOrder.indexOf(from)
                            if (fromIdx > 0) slideInHorizontally { -it } + fadeIn()
                            else EnterTransition.None
                        },
                        exitTransition = {
                            val to = targetState.destination.route ?: ""
                            val toIdx = routeOrder.indexOf(to)
                            if (toIdx > 0) slideOutHorizontally { -it } + fadeOut()
                            else ExitTransition.None
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding())
                        ) {
                            PlayerScreenWithSwipe(
                                viewModel = viewModel,
                                historyViewModel = historyViewModel,
                                settings = settings,
                                // ★ 横画面では下部の水平ピルは表示されないため、bottomBarHeightDp
                                //   （その場合古い値が残っている可能性がある）は使わず0にする。
                                //   代わりに右端の縦向きピルの幅(endBarWidthDp)をendNavBarWidthとして渡す。
                                bottomNavBarHeight = if (isLandscape) 0.dp else bottomBarHeightDp,
                                endNavBarWidth = if (isLandscape) endBarWidthDp else 0.dp,
                                onLongPressPlay = { showActionMenu = true },
                                onShowLyrics = { showLyrics = true },
                                onShowFavorites = { showFavorites = true },
                                onShowHistory = { showHistory = true }
                            )
                        }
                    }

                    composable(
                        route = "app_select",
                        enterTransition = {
                            val from = initialState.destination.route ?: ""
                            val fromIdx = routeOrder.indexOf(from)
                            val toIdx = routeOrder.indexOf("app_select")
                            if (fromIdx < toIdx) slideInHorizontally { it } + fadeIn()
                            else slideInHorizontally { -it } + fadeIn()
                        },
                        exitTransition = {
                            val to = targetState.destination.route ?: ""
                            val toIdx = routeOrder.indexOf(to)
                            val fromIdx = routeOrder.indexOf("app_select")
                            if (toIdx > fromIdx) slideOutHorizontally { -it } + fadeOut()
                            else slideOutHorizontally { it } + fadeOut()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                // ★ 横画面では右端に縦向きピルが乗るため、Scaffoldのinner
                                //   paddingだけでは考慮されない（Scaffoldはbottom方向の
                                //   自動余白確保のみに対応しており、end方向の余白は自前で
                                //   追加する必要がある）。
                                .padding(end = if (isLandscape) endBarWidthDp else 0.dp)
                        ) {
                            AppSelectScreen(
                                viewModel = viewModel,
                                settings = settings,
                                onShowHistory = { showHistory = true }
                            )
                        }
                    }
                    composable(
                        route = "settings",
                        enterTransition = {
                            val from = initialState.destination.route ?: ""
                            val fromIdx = routeOrder.indexOf(from)
                            if (fromIdx < routeOrder.indexOf("settings")) slideInHorizontally { it } + fadeIn()
                            else slideInHorizontally { -it } + fadeIn()
                        },
                        exitTransition = {
                            val to = targetState.destination.route ?: ""
                            val toIdx = routeOrder.indexOf(to)
                            if (toIdx < routeOrder.indexOf("settings")) slideOutHorizontally { it } + fadeOut()
                            else slideOutHorizontally { -it } + fadeOut()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(end = if (isLandscape) endBarWidthDp else 0.dp)
                        ) {
                            SettingScreen(
                                viewModel = viewModel,
                                settings = settings,
                                openLyricsPage = jumpToLyricsSettings,
                                onConsumeOpenLyricsPage = { jumpToLyricsSettings = false }
                            )
                        }
                    }
                }

                if (showActionMenu) {
                    ActionMenuDialog(
                        viewModel = viewModel,
                        settings = settings,
                        onDismiss = { showActionMenu = false },
                        onNavigateToSettings = {
                            navController.navigate("settings")
                            showActionMenu = false
                        },
                        onShowLyrics = { showLyrics = true },
                        onShowProperty = { showProperty = true },
                        onShareScreenshot = {
                            // appScope はダイアログ外で宣言済みのため閉じてもキャンセルされない
                            appScope.launch {
                                kotlinx.coroutines.delay(300)
                                shareScreenshot(
                                    context = context,
                                    title   = mediaState.title,
                                    artist  = mediaState.artist
                                )
                            }
                        }
                    )
                }

                if (showProperty) {
                    PropertyDialog(
                        viewModel = viewModel,
                        onDismiss = { showProperty = false }
                    )
                }

                // ★ 歌詞画面はフェードイン/フェードアウトで出し入れする
                AnimatedVisibility(
                    visible = showLyrics,
                    enter = fadeIn(tween(400)),
                    exit = fadeOut(tween(300))
                ) {
                    LyricsScreen(
                        viewModel = viewModel,
                        onDismiss = { showLyrics = false },
                        onOpenLyricsSettings = {
                            // 歌詞画面を閉じて設定タブへ切り替え、TOPを経由せず「歌詞」ページを直接開く
                            showLyrics = false
                            jumpToLyricsSettings = true
                            navController.navigate("settings") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // ★ りれき画面はNavHostのルートではなく、このBox内の条件分岐で
                //   出し入れしているオーバーレイのため、AnimatedVisibilityで包んで
                //   「アプリ切り替え→りれき」の遷移に右からスライドイン＋フェードの
                //   アニメーションをつける（NavHost側のslideInHorizontallyと同じ考え方）。
                //   showHistory=falseに戻ったとき（戻るボタン・BackHandler経由）は
                //   右へスライドアウト＋フェードアウトして消える。
                AnimatedVisibility(
                    visible = showHistory,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    // 表示時に現在再生中の曲を即時反映
                    LaunchedEffect(Unit) {
                        historyViewModel.flushCurrentTrack()
                    }
                    HistoryScreen(
                        viewModel = historyViewModel,
                        mediaPlayerViewModel = viewModel,
                        onDismiss = { showHistory = false },
                        onShowReport = {
                            showReport = true
                        }
                    )
                }

                // ★ お気に入り画面も同様にAnimatedVisibilityで包み、
                //   「キュー→お気に入り」の遷移に右からスライドイン＋フェードをつける。
                AnimatedVisibility(
                    visible = showFavorites,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    val favoriteViewModel: com.sandolpin.santopimedia35.favorite.FavoriteViewModel =
                        viewModel(
                            factory = com.sandolpin.santopimedia35.favorite.FavoriteViewModel.Factory(context)
                        )
                    LaunchedEffect(Unit) {
                        favoriteViewModel.refresh()
                    }
                    com.sandolpin.santopimedia35.favorite.FavoriteScreen(
                        viewModel = favoriteViewModel,
                        mediaPlayerViewModel = viewModel,
                        onDismiss = { showFavorites = false }
                    )
                }

                // ★ レポート画面も同様にAnimatedVisibilityで包み、
                //   「りれき→レポート」の遷移に右からスライドイン＋フェードをつける。
                //   これで「アプリ切り替え→りれき→レポート」が連鎖的にスライドする見た目になる。
                AnimatedVisibility(
                    visible = showReport,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    ReportScreen(
                        viewModel = historyViewModel,
                        mediaPlayerViewModel = viewModel,
                        onDismiss = {
                            showRankingDetail = false
                            showTrackHistory = false
                            showReport = false
                        },
                        onArtistClick = { artist ->
                            selectedArtist = artist
                            showRankingDetail = true
                        },
                        onShowAllTracks = { period ->
                            trackHistoryPeriod = period
                            showTrackHistory = true
                        }
                    )
                }

                if (showRankingDetail) {
                    selectedArtist?.let { artist ->
                        RankingDetailScreen(
                            artist = artist,
                            viewModel = historyViewModel,
                            onDismiss = { showRankingDetail = false }
                        )
                    }
                }

                if (showTrackHistory) {
                    TrackHistoryDetailScreen(
                        viewModel = historyViewModel,
                        mediaPlayerViewModel = viewModel,
                        initialPeriod = trackHistoryPeriod,
                        onDismiss = { showTrackHistory = false }
                    )
                }
            }
        }

        // ===== 横画面: 右端に重ねる縦向きナビゲーションバー =====
        if (isLandscape && !hideNavBar) {
            VerticalNavBar(
                navController = navController,
                navItems = navItems,
                settings = settings,
                viewModel = viewModel,
                accentColor = accentColor,
                darkTheme = darkTheme,
                navBarExpanded = navBarExpanded,
                onToggleExpanded = { navBarExpanded = !navBarExpanded },
                onWidthMeasured = { endBarWidthDp = it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    } // Box（Scaffold全体のラッパー）ここまで
}