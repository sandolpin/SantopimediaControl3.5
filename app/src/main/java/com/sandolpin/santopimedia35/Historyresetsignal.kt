package com.sandolpin.santopimedia35

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * HistoryResetSignal
 *
 * 履歴の手動リセット（設定画面・履歴画面の「履歴をリセット」）が行われたことを、
 * HistoryService（Activityとは別インスタンスで独立して動いているバックグラウンド記録係）
 * に伝えるための軽量なシグナル。
 *
 * ===== これが必要な理由 =====
 * 「履歴をリセット」はSQLiteのデータ行を削除するだけで、現在進行中の追跡セッション
 * （HistoryServiceが内部で保持している trackStartMs・accumulatedPlayMs 等）には
 * 一切影響しない。そのため、同じ曲がリセットをまたいで流れ続けていた場合、
 * リセット後の次の自動保存で「リセット前から積み上がっていた再生時間」が
 * そのまま書き戻されてしまい、「実際には数十秒しか聴いていないのに
 * 数分と記録される」といった不具合の原因になっていた。
 *
 * このシグナルを使い、リセットが行われたら、その時点で追跡中のインスタンス
 * （ActivityのHistoryViewModel／HistoryServiceのHistoryViewModelの両方）に
 * 「今追跡中の分は保存せずに0から数え直して」と伝える。
 */
object HistoryResetSignal {
    private val _resetEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resetEvents: SharedFlow<Unit> = _resetEvents.asSharedFlow()

    fun notifyReset() {
        _resetEvents.tryEmit(Unit)
    }
}