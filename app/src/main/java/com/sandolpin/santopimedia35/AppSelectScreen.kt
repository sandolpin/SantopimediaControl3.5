package com.sandolpin.santopimedia35

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.MediaState
import com.sandolpin.santopimedia35.home.NormalSeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppSelectScreen（BottomNav 3番目）
 * - アクティブなMediaSessionを持つアプリを一覧表示
 * - 各アプリにミニプレーヤーカード
 * - タップでそのアプリのセッションにコントロールを切り替え
 */
@Composable
fun AppSelectScreen(
    viewModel: MediaPlayerViewModel,
    settings: AppSettings,
    onShowHistory: () -> Unit = {}
) {
    val activeSessions by viewModel.activeSessions.collectAsState()
    val currentPackage by viewModel.currentPackageName.collectAsState()

    // ===== この画面だけの「一時的に非表示」機能 =====
    // ★ HistoryScreenの「除外アプリ」（履歴記録そのものを止める設定・DataStore永続化）とは
    //   別の概念。こちらは「今セッション一覧がうるさいので一旦隠したい」という
    //   その場限りのUI操作のため、あえて永続化せずこの画面の表示中だけ保持する
    //   （remember。アプリ再起動はもちろん、この画面から離れて戻ってくると
    //   リセットされる。もし他の画面と同様に永続化したい場合は伝えてほしい）。
    var hiddenPackages by remember { mutableStateOf(setOf<String>()) }
    var showHiddenSection by remember { mutableStateOf(false) }

    val visibleSessions = activeSessions.filter { it.packageName !in hiddenPackages }
    val hiddenSessions = activeSessions.filter { it.packageName in hiddenPackages }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        // ★ MainActivity.ktのScaffoldがステータスバー分の余白を自動確保しなくなったため、
        //   ここで自分でステータスバー分の高さを読んで確保する。
        //   （backgroundはfillMaxSize()のままなのでステータスバーの裏まで塗られるが、
        //   タイトルやリスト項目はステータスバーに被らない位置から始まる）
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // タイトル
        item {
            Text(
                text = "アプリ切り替え",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // 「りれきを表示」ボタン
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(12.dp))
                    .clickable { onShowHistory() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    Spacer(Modifier.width(12.dp))
                    Text("りれきを表示", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }

        if (visibleSessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (activeSessions.isEmpty()) "再生中のアプリがありません"
                            else "表示できるアプリがありません（すべて非表示中）",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else {
            items(visibleSessions, key = { it.packageName }) { session ->
                AppSessionCard(
                    session = session,
                    isActive = session.packageName == currentPackage,
                    showFullDetail = settings.appSelectShowMiniPlayer,
                    showAppIcon = settings.appSelectShowIcon,
                    onSelect = { viewModel.switchToSession(session.packageName) },
                    onOpenApp = { viewModel.openApp(session.packageName) },
                    onHide = { hiddenPackages = hiddenPackages + session.packageName },
                    onPlayPause = { viewModel.togglePlayPauseForSession(session.packageName) },
                    onPrev = { viewModel.skipToPreviousForSession(session.packageName) },
                    onNext = { viewModel.skipToNextForSession(session.packageName) },
                    onSeek = { progress ->
                        viewModel.seekToForSession(session.packageName, progress, session.durationMs)
                    }
                )
            }
        }

        // ===== 非表示にしたアプリ（折りたたみセクション） =====
        if (hiddenSessions.isNotEmpty()) {
            item {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.2f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(12.dp))
                            .clickable { showHiddenSection = !showHiddenSection }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "非表示にしたアプリ（${hiddenSessions.size}）",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showHiddenSection) Icons.Rounded.KeyboardArrowUp
                                else Icons.Rounded.KeyboardArrowDown,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                            )
                        }
                    }
                    if (showHiddenSection) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            hiddenSessions.forEach { session ->
                                HiddenAppRow(
                                    session = session,
                                    onRestore = { hiddenPackages = hiddenPackages - session.packageName }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 非表示にしたアプリの1行（アプリ名 + 「×」で復元） =====
@Composable
private fun HiddenAppRow(session: MediaState, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(packageName = session.packageName)
        Spacer(Modifier.width(10.dp))
        Text(
            session.appLabel.ifEmpty { session.packageName },
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, "表示に戻す", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

// ===== アプリセッションカード =====
@Composable
fun AppSessionCard(
    session: MediaState,
    isActive: Boolean,
    showFullDetail: Boolean,
    showAppIcon: Boolean,
    onSelect: () -> Unit,
    onOpenApp: () -> Unit,
    onHide: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hasArt = session.albumArtUri != null || session.albumArtBitmap != null

    // ===== カード背景: アルバムアートから抽出した2色をミックス（HistoryCardの「複数色ミックス」と同じ方式） =====
    // アルバムアートが無い場合はこの処理自体を行わず、白/黒の単色にフォールバックする。
    // ★ 以前はremember/LaunchedEffectのキーに session.albumArtBitmap（Bitmapオブジェクトそのもの）を
    //   使っていた。曲自体は変わっていなくても、再生位置の更新のたびにMediaMetadataを
    //   丸ごと送り直すアプリ（YouTube Music等）では、同じ画像でも毎回新しいBitmapインスタンスの
    //   参照になる。そのためキーが変わったと判定されてcolorA/colorBが一瞬nullにリセットされ、
    //   下のグラデーションフォールバックが一瞬表示されてからPalette抽出完了で元の色に戻る、
    //   という切り替わりを繰り返すことで背景が高速点滅して見えていた（実際に発生した不具合）。
    //   曲を識別するtrackKey（title+artist+packageName。曲が変わらない限り値が変わらない）
    //   だけをキーにすることで、同じ曲の間はcolorA/colorBが保持され続け、点滅しなくなる。
    val trackKey = remember(session.title, session.artist, session.packageName) {
        "${session.title}|${session.artist}|${session.packageName}"
    }
    var colorA by remember(trackKey) { mutableStateOf<Color?>(null) }
    var colorB by remember(trackKey) { mutableStateOf<Color?>(null) }

    if (hasArt) {
        // ★ LaunchedEffect自体もtrackKeyだけをキーにする。albumArtUri/albumArtBitmapの
        //   「最初にtrackKeyが確定した時点での値」をここでキャプチャして使うことで、
        //   同じ曲の間に再生位置の更新等でBitmap参照だけが変わっても再抽出が走らないようにする。
        val artUriForEffect = session.albumArtUri
        val artBitmapForEffect = session.albumArtBitmap
        LaunchedEffect(trackKey) {
            // 既にこの曲の色を抽出済みなら何もしない（無駄なPalette解析を避ける）
            if (colorA != null && colorB != null) return@LaunchedEffect

            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (artBitmapForEffect != null) {
                        artBitmapForEffect
                    } else {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(artUriForEffect)
                            .allowHardware(false) // Paletteにはソフトウェアビットマップが必要
                            .build()
                        val result = loader.execute(request)
                        (result as? SuccessResult)?.drawable
                            ?.let { it as? BitmapDrawable }
                            ?.bitmap
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: return@LaunchedEffect

            Palette.from(bitmap).generate { palette ->
                val primary = palette?.vibrantSwatch
                    ?: palette?.darkVibrantSwatch
                    ?: palette?.mutedSwatch
                    ?: palette?.dominantSwatch
                if (primary != null) colorA = Color(primary.rgb)
                val secondary = palette?.mutedSwatch
                    ?: palette?.darkVibrantSwatch
                    ?: palette?.lightVibrantSwatch
                    ?: palette?.dominantSwatch
                if (secondary != null) colorB = Color(secondary.rgb)
            }
        }
    }

    // アートなし: 白(ライトテーマ) / 黒(ダークテーマ)の単色
    val noArtSolid = if (isDark) Color.Black else Color.White
    val extractedA = colorA
    val extractedB = colorB

    val backgroundBrush: Brush = when {
        !hasArt -> Brush.linearGradient(listOf(noArtSolid, noArtSolid))
        // Palette抽出が完了する前の一瞬（このLaunchedEffectは非同期のため）は、
        // 単色の抽出結果が出るまでカードが真っ白/真っ黒に見えてチラつくのを避けるため、
        // つなぎとして中立なグラデーションを暫定表示する。
        // ★ Palette自体はAndroidのバージョンに関わらず使えるライブラリのため
        //   （HomeScreenCの動的アニメーション背景(AGSL)のようなAPI33+限定の制約は無い）、
        //   「Android12未満は使えない」という制約は本来ここには存在しない。
        //   このグラデーション表示は"バージョン制限のフォールバック"ではなく、
        //   あくまで抽出完了までの一時的なつなぎとして実装している。
        extractedA == null || extractedB == null -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        )
        else -> Brush.linearGradient(listOf(extractedA, extractedB))
    }

    // ===== 背景の明るさに応じて文字色を自動で白/黒に切り替える =====
    val cardTextColor: Color = when {
        !hasArt -> if (noArtSolid == Color.White) Color.Black else Color.White
        extractedA != null && extractedB != null ->
            if (((extractedA.luminance() + extractedB.luminance()) / 2f) > 0.5f) Color.Black else Color.White
        else -> MaterialTheme.colorScheme.onSurface // 抽出完了前の暫定表示中
    }
    val subTextColor = cardTextColor.copy(alpha = 0.7f)

    // 「×」タップ時にいきなり非表示にせず、確認ダイアログを挟む
    var showHideConfirm by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = if (isActive)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            androidx.compose.foundation.BorderStroke(1.dp, cardTextColor.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ===== ヘッダー行: アイコン + アプリ名 + 再生中バッジ + アプリを開く + × =====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showAppIcon) {
                        AppIconImage(packageName = session.packageName)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = session.appLabel.ifEmpty { session.packageName },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = cardTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text("再生中", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // ===== アプリを開く =====
                    // ★ Surface(onClick=...)やOutlinedButton等、Material3の「クリック可能な
                    //   コンポーネント」は、アクセシビリティのため内部でminimumInteractiveComponentSize()
                    //   という「最低48dp四方のタッチ領域」を強制的に確保する仕組みが入っている。
                    //   このため見た目のpaddingをどれだけ小さくしても、実際に確保される高さは
                    //   変わらず、隣の「再生中」バッジより明らかに大きいままになっていた
                    //   （前回試したSurface(onClick=...)への置き換えでも解消しなかった不具合の原因）。
                    //   Material3のコンポーネントを使わず、素のModifier.clickable（こちらは
                    //   最低サイズを強制しない）を直接使うことで、「再生中」バッジと
                    //   全く同じ実寸のピルにする。
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, cardTextColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .clickable { onOpenApp() }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "アプリを開く",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = cardTextColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // × このアプリをこの画面から一時的に非表示にする（確認ダイアログ経由）
                    // ★ 同じ理由でIconButtonもやめ、素のBox+clickableにする。
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { showHideConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, "非表示にする", tint = cardTextColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp))
                    }
                }

                if (showFullDetail) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = cardTextColor.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))

                    // ===== アート + タイトル/アーティスト =====
                    Row(verticalAlignment = Alignment.Top) {
                        if (session.albumArtUri != null) {
                            AsyncImage(
                                model = session.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (session.albumArtBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = session.albumArtBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(cardTextColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, null, tint = cardTextColor.copy(alpha = 0.4f))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                            Text(
                                text = session.title.ifEmpty { "タイトル不明" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = cardTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = session.artist.ifEmpty { "アーティスト不明" },
                                fontSize = 13.sp,
                                color = subTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ===== シークバー（操作可能） =====
                    NormalSeekBar(
                        progress = session.progress,
                        onProgressChange = { onSeek(it) },
                        thickness = 4.dp,
                        color = if (cardTextColor == Color.Black) MaterialTheme.colorScheme.primary else Color.White,
                        durationMs = session.durationMs
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ===== 時間 + コントロール =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(session.currentPositionStr, fontSize = 12.sp, color = subTextColor)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SkipPrevious, null, tint = cardTextColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    null,
                                    tint = cardTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SkipNext, null, tint = cardTextColor, modifier = Modifier.size(20.dp))
                            }
                        }

                        Text("-${session.remainingTimeStr}", fontSize = 12.sp, color = subTextColor)
                    }
                }
            }
        }
    }

    // ===== 「×」確認ダイアログ =====
    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            icon = {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            },
            title = { Text("このアプリを非表示にしますか？", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "「${session.appLabel.ifEmpty { session.packageName }}」を一覧から一時的に非表示にします。" +
                            "「非表示にしたアプリ」からいつでも元に戻せます。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            },
            confirmButton = {
                Button(onClick = {
                    showHideConfirm = false
                    onHide()
                }) {
                    Text("非表示にする")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

// ===== アプリアイコン表示 =====
// ★ size を引数化。デフォルト18dp（既存呼び出し箇所と同じ見た目）を維持しつつ、
//   除外アプリ選択リスト等、もう少し大きく見せたい場所からも呼べるようにする。
@Composable
fun AppIconImage(packageName: String, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) { null }
    }
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp))
        )
    } else {
        Icon(Icons.Default.Android, null, modifier = Modifier.size(size))
    }
}

// Drawable → Bitmap 拡張
fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap {
    if (this is android.graphics.drawable.BitmapDrawable) return bitmap
    val bmp = android.graphics.Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}