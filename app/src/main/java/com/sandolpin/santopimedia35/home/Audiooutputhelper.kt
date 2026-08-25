package com.sandolpin.santopimedia35.home

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// =============================================================================
// 音声出力先の判定・表示
// -----------------------------------------------------------------------------
// ★ Androidには「今まさにアクティブな出力ルート」を直接返す公式APIが存在しない
//   （isBluetoothScoOn/isSpeakerphoneOnは通話用のルーティングフラグでしかなく、
//   音楽再生の出力先判定には使えない）。
//   そのため AudioManager.getDevices(GET_DEVICES_OUTPUTS) で「今接続されている
//   出力デバイス一覧」を取得し、実際のAndroidのオーディオルーティングの優先度に
//   近い順（Bluetooth > 有線ヘッドホン > USB > 内蔵スピーカー）で
//   「一番優先されていそうなもの」を選んで表示する、という一般的なアプリ実装の
//   やり方に倣っている。複数デバイスが同時接続されている場合、実際にどれが
//   鳴っているかはOS内部のルーティングに依存するため、この判定はあくまで推定。
//
// ★ LDAC等のコーデック名は AudioDeviceInfo からは一切取得できない
//   （AudioManager側は「何が繋がっているか」だけを教えてくれるAPIで、
//   「どのコーデックで転送されているか」は関知しない）。
//   コーデック情報を得るには本来 BluetoothA2dp.getCodecStatus(device) を使うが、
//   これはAndroid公式SDKには公開されていない内部API（@SystemApi）のため、
//   通常のアプリからは直接呼び出せない（コンパイルエラーになる）。
//   このファイルではリフレクション経由で「呼べたら使う」形にしている。
//   Android 9以降の非SDKインターフェース制限により、端末・OSバージョンによっては
//   呼び出しに失敗することがあるが、その場合は例外を握りつぶしてLDAC表記なしに
//   静かにフォールバックする（＝動作保証はできないが、失敗してもクラッシュはしない）。
// =============================================================================

enum class AudioOutputType { SPEAKER, WIRED, BLUETOOTH, USB }

data class AudioOutputInfo(
    val type: AudioOutputType,
    val label: String,
    // Bluetooth接続時、コーデックがLDACだと判別できた場合のみ"LDAC"が入る。
    // 判別できない（対応端末でない・権限が無い・LDAC以外）場合はnull。
    val codec: String? = null
)

// AudioDeviceInfo.type(int定数) → このアプリで扱うAudioOutputTypeへの変換
// 該当しない種類（内蔵マイク等の入力専用や、その他の未対応タイプ）はnull
private fun classifyDevice(device: AudioDeviceInfo): AudioOutputType? = when (device.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioOutputType.BLUETOOTH
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioOutputType.WIRED
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioOutputType.USB
    else -> {
        // TYPE_USB_HEADSET は API26以降の定数のため、それ未満のビルドでは
        // 定数自体は存在するが実機でこの値が来ることはない（安全に無視される）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        ) {
            AudioOutputType.USB
        } else if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            AudioOutputType.SPEAKER
        } else null
    }
}

// デバイス名（Bluetooth機器名・USB-DAC名）を可能な範囲で取り出す。
// ★ Bluetoothは「Bluetooth」という種別文言は表示せず、接続機器名のみを表示する
//   仕様のため、名前が取れない場合の最低限のフォールバックのみ用意する。
// ★ productNameは端末・機器によっては空や"?"のような無意味な値になることがあるため、
//   その場合はフォールバック文言にする。
private fun resolveLabel(type: AudioOutputType, device: AudioDeviceInfo?): String {
    val productName = device?.productName?.toString()?.trim().orEmpty()
    val hasUsefulName = productName.isNotEmpty() && productName != "?"
    return when (type) {
        AudioOutputType.BLUETOOTH -> if (hasUsefulName) productName else "Bluetooth機器"
        AudioOutputType.USB       -> if (hasUsefulName) productName else "USB-DAC"
        AudioOutputType.WIRED     -> "ヘッドホン"
        AudioOutputType.SPEAKER   -> "スピーカー"
    }
}

/** 現在の音声出力先を1回だけ判定する（同期・非Composable。コーデック判定は含まない） */
fun getCurrentAudioOutput(context: Context): AudioOutputInfo {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        // AudioManager.getDevices() はAPI23以降のため、それ未満は判定不能→スピーカー扱い
        return AudioOutputInfo(AudioOutputType.SPEAKER, "スピーカー")
    }
    return try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // 優先順位: Bluetooth → 有線ヘッドホン → USB → スピーカー
        val priority = listOf(AudioOutputType.BLUETOOTH, AudioOutputType.WIRED, AudioOutputType.USB)
        for (targetType in priority) {
            val found = devices.firstOrNull { classifyDevice(it) == targetType }
            if (found != null) {
                return AudioOutputInfo(targetType, resolveLabel(targetType, found))
            }
        }
        AudioOutputInfo(AudioOutputType.SPEAKER, "スピーカー")
    } catch (e: Exception) {
        AudioOutputInfo(AudioOutputType.SPEAKER, "スピーカー")
    }
}

// codecType(int定数) → 表示名。今のところLDACのみ対応（他コーデックは今回の要望に
// 含まれないため未対応。将来aptX HD等も表示したくなったらここに追加するだけでよい）
private fun codecDisplayName(codecType: Int): String? = when (codecType) {
    BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
    else -> null
}

/**
 * 現在Bluetooth A2DPで接続されている機器のコーデック名を取得するComposable。
 * ★ BluetoothA2dpプロファイルへの接続は非同期(ServiceListener)のため、
 *   DisposableEffectでプロキシの取得・解放を管理する。
 * ★ Android 12(API31)以降はBLUETOOTH_CONNECT権限が無いとSecurityExceptionになるため、
 *   事前にチェックし、権限が無ければ何もせず null のままにする
 *   （＝LDAC表記が出ないだけで、機器名の表示自体は変わらず正常に動作する）。
 */
@Composable
private fun rememberConnectedA2dpCodecName(): String? {
    val context = LocalContext.current
    var codecName by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        var proxyRef: BluetoothA2dp? = null

        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // API30以下はBLUETOOTHが通常権限のためManifest宣言だけで実行時チェック不要
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.A2DP) return
                val a2dp = proxy as? BluetoothA2dp ?: return
                proxyRef = a2dp
                try {
                    val device = a2dp.connectedDevices.firstOrNull()
                    if (device == null) {
                        codecName = null
                        return
                    }
                    // ★ BluetoothA2dp.getCodecStatus(device) はAndroid公式SDKには
                    //   公開されていない内部API（@SystemApi）で、通常のcompileSdk環境
                    //   では直接呼び出せない（呼ぶと"Unresolved reference"になる）。
                    //   そのためリフレクション経由で「呼べたら使う・呼べなければ諦める」
                    //   形にする。BluetoothCodecStatus/BluetoothCodecConfigクラス自体は
                    //   公開APIのため、戻り値さえ取得できればその先は通常通り扱える。
                    //   ★ Android 9以降の非SDKインターフェース制限により、この呼び出しは
                    //   端末・OSバージョンによって失敗することがある（NoSuchMethodException等）。
                    //   失敗しても例外を握りつぶしてLDAC表記なしにフォールバックするだけなので、
                    //   アプリがクラッシュすることはない。
                    val status: BluetoothCodecStatus? = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val method = BluetoothA2dp::class.java.getMethod(
                                "getCodecStatus", BluetoothDevice::class.java
                            )
                            method.invoke(a2dp, device) as? BluetoothCodecStatus
                        } else {
                            val method = BluetoothA2dp::class.java.getMethod("getCodecStatus")
                            method.invoke(a2dp) as? BluetoothCodecStatus
                        }
                    } catch (e: ReflectiveOperationException) {
                        null
                    } catch (e: Exception) {
                        null
                    }
                    codecName = status?.codecConfig?.codecType?.let { codecDisplayName(it) }
                } catch (e: SecurityException) {
                    codecName = null
                } catch (e: Exception) {
                    codecName = null
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) codecName = null
            }
        }

        if (adapter != null && hasBluetoothPermission) {
            try {
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
            } catch (e: Exception) {
                // 端末がA2DPプロファイルに対応していない等、失敗時はLDAC表記なしで継続
            }
        }

        onDispose {
            try {
                proxyRef?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            } catch (e: Exception) {
                // 既に切断済み等で例外になることがあるが無視してよい
            }
        }
    }

    return codecName
}

/**
 * 現在の音声出力先をリアルタイムに監視するComposable。
 * AudioDeviceCallbackでイヤホン抜き差し・Bluetooth接続変化を検知し、
 * 変化のたびに再判定する。Bluetooth接続中はLDACコーデックの判定も合わせて行う。
 */
@Composable
fun rememberCurrentAudioOutput(): AudioOutputInfo {
    val context = LocalContext.current
    var output by remember { mutableStateOf(getCurrentAudioOutput(context)) }
    val ldacCodecName = rememberConnectedA2dpCodecName()

    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    output = getCurrentAudioOutput(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    output = getCurrentAudioOutput(context)
                }
            }
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            onDispose {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        } else {
            onDispose { }
        }
    }

    // Bluetooth接続中のときだけコーデック名を合成する（有線・USB・スピーカーには無関係のため）
    return if (output.type == AudioOutputType.BLUETOOTH) {
        output.copy(codec = ldacCodecName)
    } else {
        output
    }
}

// ===== 表示用の小さなインジケーター（アイコン + ラベル）=====
@Composable
fun AudioOutputIndicator(
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val output = rememberCurrentAudioOutput()
    val resolvedTint = if (tint != Color.Unspecified) tint
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)

    // "機器名" のみ、LDAC判定できたときだけ "機器名 ・ LDAC" のように付け足す
    val displayText = if (output.codec != null) "${output.label} ・ ${output.codec}" else output.label

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (output.type) {
                AudioOutputType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                AudioOutputType.WIRED     -> Icons.Rounded.Headphones
                AudioOutputType.USB       -> Icons.Rounded.Usb
                AudioOutputType.SPEAKER   -> Icons.Rounded.Speaker
            },
            contentDescription = "出力先",
            tint = resolvedTint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            displayText,
            fontSize = 11.sp,
            color = resolvedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}