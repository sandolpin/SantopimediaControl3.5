package com.sandolpin.santopimedia35

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MediaNotificationListener（Service）と MediaPlayerViewModel の橋渡し役。
 *
 * Service と ViewModel は直接参照できないため、
 * このシングルトン経由で接続状態とコントローラー情報を共有する。
 */
object MediaSessionRepository {

    // Service の接続状態
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    // Service 接続時に取得したコントローラー一覧
    // SharedFlow（replay=1）で最新の値を新規コレクターにも届ける
    private val _controllersFromService = MutableSharedFlow<List<MediaController>>(replay = 1)
    val controllersFromService: SharedFlow<List<MediaController>> =
        _controllersFromService.asSharedFlow()

    fun onListenerConnected() {
        _isListenerConnected.value = true
    }

    fun onListenerDisconnected() {
        _isListenerConnected.value = false
    }

    /**
     * Service 側で取得したコントローラー一覧を ViewModel に渡す。
     * tryEmit はコルーチン不要で呼べる。
     */
    fun onControllersUpdated(controllers: List<MediaController>) {
        _controllersFromService.tryEmit(controllers)
    }
}