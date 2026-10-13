package com.sandolpin.santopimedia35.settingscreen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandolpin.santopimedia35.*
import com.sandolpin.santopimedia35.database.AppSettings
import com.sandolpin.santopimedia35.database.SettingsKeys
import com.sandolpin.santopimedia35.lyricsscreen.*
import com.sandolpin.santopimedia35.remember.HistoryCard
import com.sandolpin.santopimedia35.remember.PlayHistoryEntity

// 設定: アプリ切替・バックグラウンド・履歴画面・アクションメニューページ

// ===== アプリ切り替え =====
@Composable
internal fun AppSelectPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("アプリ切り替え", onBack) {
        SettingSection("表示") {
            SettingSwitchRow("ミニプレーヤーを表示",
                checked = settings.appSelectShowMiniPlayer,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.APP_SELECT_SHOW_MINI_PLAYER, it) })
            SettingSwitchRow("アプリアイコンを表示",
                checked = settings.appSelectShowIcon,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.APP_SELECT_SHOW_ICON, it) })
        }
    }
}

// ===== バックグラウンド処理 =====
@Composable
internal fun BackgroundPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    SubPageScaffold("バックグラウンド処理", onBack) {
        SettingSection("履歴記録") {
            SettingSwitchRow("バックグラウンドで履歴記録を許可",
                checked = settings.backgroundHistoryEnabled,
                onCheckedChange = { enabled ->
                    viewModel.updateSetting(SettingsKeys.BACKGROUND_HISTORY_ENABLED, enabled)
                    if (enabled) HistoryService.start(context) else HistoryService.stop(context)
                })
            Text(
                "ONにするとアプリを閉じていても再生履歴の時間を記録し続けます。" +
                        "OSに終了させられにくくするため、記録中は通知欄に常駐通知が表示されます" +
                        "（音・バイブは鳴りません／スワイプでは消せません）。バッテリーに多少影響する場合があります。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }

        SettingSection("タスクキル対策") {
            // 画面に入るたび・戻ってくるたびに最新の状態を再チェックする
            // （端末の設定画面から戻ってきた場合に反映するため）
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            var isIgnoringBatteryOpt by remember {
                mutableStateOf(isIgnoringBatteryOptimizations(context))
            }
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isIgnoringBatteryOpt = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("バッテリー最適化の除外", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (isIgnoringBatteryOpt)
                            "設定済み：OSの標準機能による終了は起きにくくなっています"
                        else
                            "未設定：OSの省電力機能により記録が途中で止まる場合があります",
                        fontSize = 11.sp,
                        color = if (isIgnoringBatteryOpt)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
                if (!isIgnoringBatteryOpt) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 端末によってはこのIntentに対応していない場合がある
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("設定する", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(12.dp))

            Text(
                "上記のAndroid標準の設定だけでは、端末メーカー（Xiaomi/Samsung/OPPO/Vivo/Huaweiなど）独自の" +
                        "バッテリー管理機能までは解除できないことがあります。それでも記録が止まってしまう場合は、" +
                        "端末の設定アプリ内で「バッテリー」「アプリの自動起動」「保護されたアプリ」などの項目から" +
                        "本アプリを許可リストに追加してください（名称は端末により異なります）。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "また、タスク一覧（最近使ったアプリ）からアプリ自体をスワイプして完全に終了させた場合は、" +
                        "Androidの仕様上どのアプリであっても自動では復帰できません。バックグラウンド記録を継続するには、" +
                        "アプリをタスク一覧から消さないようにしてください。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

// バッテリー最適化の対象外（除外）になっているかを確認する
private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE)
            as? android.os.PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

// ===== 履歴画面 =====
@Composable
internal fun HistoryDisplayPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    SubPageScaffold("履歴画面", onBack) {
        SettingSection("プレビュー") {
            // ★ 実際の履歴データではなく、表示確認用のダミーデータを使う。
            //   下の各設定（背景スタイル・日時表示・アート縁取り・聴いた時間表示・
            //   進捗バー・アートサイズ）を変更すると、このカードにその場で反映される。
            //   ただしアルバムアートの実画像は無い（albumArtUri=null）ため、
            //   Paletteでの色抽出が前提の背景スタイル（ぼかし・複数色ミックス・
            //   アート色で塗りつぶし）は、実際の曲再生時とは異なる簡易表示になる点に注意。
            val previewEntity = remember {
                PlayHistoryEntity(
                    title = "サンプルタイトル",
                    artist = "サンプルアーティスト",
                    album = "サンプルアルバム",
                    albumArtUri = null,
                    appLabel = "Apple Music",
                    packageName = "com.apple.android.music",
                    playedAtMs = System.currentTimeMillis() - 125_000L,
                    durationMs = 210_000L,
                    playTimeMs = 125_000L,
                    listenedMs = 125_000L,
                    listenedAll = false
                )
            }
            HistoryCard(
                entity = previewEntity,
                cardBackgroundStyle = settings.historyCardBackgroundStyle,
                showDate = settings.historyShowDate,
                timeRangeMode = settings.historyTimeRangeMode,
                artBorderEnabled = settings.historyArtBorderEnabled,
                showPlayTime = settings.historyShowPlayTime,
                showProgressBar = settings.historyShowProgressBar,
                artSize = settings.historyArtSize
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "※プレビュー用のサンプルです。実際のアルバムアートが無いため、" +
                        "アートの色を使う背景スタイルは簡易表示になります",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f)
            )
        }
        SettingSection("カードの背景") {
            Text(
                "「きいた曲」カードのアルバムアート背景の見せ方を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            // ★ Android 12未満は「アルバムアートのぼかし」が実質機能しないため除外する。
            val historyBgOptions = remember(settings.historyCardBackgroundStyle) {
                val all = listOf(
                    "blur" to "アルバムアートのぼかし",
                    "animated" to "複数色ミックス",
                    "color_fill" to "アルバムアート色で塗りつぶし"
                )
                if (isBlurRenderSupported()) all else all.filterNot { it.first == "blur" }
            }
            SettingDropdownRow("背景スタイル", settings.historyCardBackgroundStyle, historyBgOptions) {
                viewModel.updateSetting(SettingsKeys.HISTORY_CARD_BACKGROUND_STYLE, it)
            }
            if (!isBlurRenderSupported()) {
                Text(
                    "Android 12未満のため「アルバムアートのぼかし」は選択できません",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
        SettingSection("聴いた時間の表示") {
            Text(
                "「きょう聞いた時間」や再生時間の表示形式を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingDropdownRow("表示形式", settings.historyTimeDisplayMode,
                listOf("auto" to "自動調整（時間・分・秒）", "seconds" to "秒に固定", "minutes" to "分に固定", "hours" to "時間に固定")) {
                viewModel.updateSetting(SettingsKeys.HISTORY_TIME_DISPLAY_MODE, it)
            }
            // プレビュー
            Spacer(Modifier.height(8.dp))
            val previewMs = 7_384_000L // 2時間3分4秒 相当のサンプル
            Text(
                "プレビュー: ${com.sandolpin.santopimedia35.remember.formatListenedTimeMode(previewMs, settings.historyTimeDisplayMode)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SettingSection("カードの日時表示") {
            Text(
                "「きいた曲」カード1件ごとに表示する日時の形式を選べます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow(
                "日付を表示",
                checked = settings.historyShowDate,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_DATE, it) }
            )
            Spacer(Modifier.height(4.dp))
            SettingDropdownRow("表示する時間", settings.historyTimeRangeMode,
                listOf("start" to "開始時間のみ", "end" to "終了時間のみ", "both" to "開始〜終了（両方）")) {
                viewModel.updateSetting(SettingsKeys.HISTORY_TIME_RANGE_MODE, it)
            }
        }

        SettingSection("カードの表示項目") {
            SettingSwitchRow(
                "アルバムアートを白で縁取り",
                checked = settings.historyArtBorderEnabled,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_ART_BORDER_ENABLED, it) }
            )
            SettingSwitchRow(
                "カードに聴いた時間を表示",
                checked = settings.historyShowPlayTime,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_PLAY_TIME, it) }
            )
            Text(
                "開始〜停止までの実際に聴いていた時間を「〇:〇〇聴きました」の形式で表示します",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(6.dp))
            SettingSwitchRow(
                "カードに進捗状況を表示",
                checked = settings.historyShowProgressBar,
                onCheckedChange = { viewModel.updateSetting(SettingsKeys.HISTORY_SHOW_PROGRESS_BAR, it) }
            )
            Text(
                "現在のシークバー（どこまで聴いたか）をカードに表示します",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }

        SettingSection("アルバムアートの大きさ") {
            // ★ 実際のサイズ比率(HistoryScreen.ktのartSizeDp: 48/64/88/112dp)に
            //   準じた大きさの正方形を、中央寄せで表示してサイズ感をそのまま伝える。
            SettingDropdownRow(
                label = "サイズ",
                selectedValue = settings.historyArtSize,
                options = listOf("small" to "小", "medium" to "中（標準）", "large" to "大", "xlarge" to "特大"),
                preview = { value ->
                    val squareDp = when (value) {
                        "small" -> 16.dp
                        "large" -> 30.dp
                        "xlarge" -> 36.dp
                        else -> 22.dp // "medium"
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(squareDp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            ) {
                viewModel.updateSetting(SettingsKeys.HISTORY_ART_SIZE, it)
            }
        }
    }
}

// ===== アクションメニューID → 表示ラベル =====
private val actionMenuItemLabels = mapOf(
    "share" to "シェア",
    "search" to "今流れている曲をしらべる",
    "time_seek" to "指定の時間から再生",
    "lyrics" to "歌詞を表示",
    "favorites_list" to "お気に入り",
    "favorite_toggle" to "お気に入りに追加/解除",
    "property" to "プロパティ",
    "settings" to "設定"
)

// ===== アクションメニュー: 表示/非表示・並び替え =====
@Composable
internal fun ActionMenuSettingPage(viewModel: MediaPlayerViewModel, settings: AppSettings, onBack: () -> Unit) {
    // 文字列(カンマ区切り) ⇔ リストの変換ヘルパー
    fun parseOrder(s: String): List<String> =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun parseHidden(s: String): Set<String> =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val order = remember(settings.actionMenuOrder) { parseOrder(settings.actionMenuOrder).toMutableStateList() }
    val hidden = remember(settings.actionMenuHidden) {
        mutableStateOf(parseHidden(settings.actionMenuHidden))
    }

    fun saveOrder(newOrder: List<String>) {
        viewModel.updateSetting(SettingsKeys.ACTION_MENU_ORDER, newOrder.joinToString(","))
    }
    fun saveHidden(newHidden: Set<String>) {
        hidden.value = newHidden
        viewModel.updateSetting(SettingsKeys.ACTION_MENU_HIDDEN, newHidden.joinToString(","))
    }

    SubPageScaffold("アクションメニュー", onBack) {
        SettingSection("表示する項目・並び順") {
            Text(
                "長押しメニューに表示する項目のON/OFFと表示順を設定できます",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
            Spacer(Modifier.height(8.dp))
            order.forEachIndexed { index, id ->
                val isHidden = hidden.value.contains(id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ===== 並び替えボタン =====
                    IconButton(
                        onClick = {
                            if (index > 0) {
                                val newOrder = order.toMutableList()
                                val tmp = newOrder[index - 1]
                                newOrder[index - 1] = newOrder[index]
                                newOrder[index] = tmp
                                order.clear(); order.addAll(newOrder)
                                saveOrder(newOrder)
                            }
                        },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp, "上へ",
                            modifier = Modifier.size(18.dp),
                            tint = if (index > 0) MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.2f)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (index < order.size - 1) {
                                val newOrder = order.toMutableList()
                                val tmp = newOrder[index + 1]
                                newOrder[index + 1] = newOrder[index]
                                newOrder[index] = tmp
                                order.clear(); order.addAll(newOrder)
                                saveOrder(newOrder)
                            }
                        },
                        enabled = index < order.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown, "下へ",
                            modifier = Modifier.size(18.dp),
                            tint = if (index < order.size - 1) MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            else MaterialTheme.colorScheme.onSurface.copy(0.2f)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Text(
                        actionMenuItemLabels[id] ?: id,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(0.4f)
                        else MaterialTheme.colorScheme.onSurface
                    )

                    // ===== 表示/非表示トグル =====
                    Switch(
                        checked = !isHidden,
                        onCheckedChange = { checkedNowVisible ->
                            val newHidden = hidden.value.toMutableSet()
                            if (checkedNowVisible) newHidden.remove(id) else newHidden.add(id)
                            saveHidden(newHidden)
                        },
                        colors = SwitchDefaults.colors()
                    )
                }
                if (index < order.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                }
            }
        }
    }
}

// ===== 共通UIパーツ =====