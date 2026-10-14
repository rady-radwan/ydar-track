package com.rady.worktracker.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.rady.worktracker.R
import com.rady.worktracker.data.TimerEvent

/** The app's own sounds (stored in res/raw). */
object Sounds {
    fun forEvent(e: TimerEvent): Int = when (e) {
        TimerEvent.LAP -> R.raw.lap
        TimerEvent.HALF -> R.raw.halfway
        TimerEvent.FIVE_LEFT -> R.raw.five_min
        TimerEvent.END -> R.raw.finish
    }

    fun forRank(level: Int): Int = when (level.coerceIn(0, 9)) {
        0 -> R.raw.rank_0
        1 -> R.raw.rank_1
        2 -> R.raw.rank_2
        3 -> R.raw.rank_3
        4 -> R.raw.rank_4
        5 -> R.raw.rank_5
        6 -> R.raw.rank_6
        7 -> R.raw.rank_7
        8 -> R.raw.rank_8
        else -> R.raw.rank_9
    }

    fun uri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    /** Plays a sound now (used inside the app: rank pop-up and previews). */
    fun play(context: Context, resId: Int) {
        runCatching {
            val mp = MediaPlayer.create(
                context.applicationContext, resId,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
            ) ?: return
            mp.setOnCompletionListener { it.release() }
            mp.start()
        }
    }
}
