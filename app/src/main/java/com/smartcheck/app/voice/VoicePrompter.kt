package com.smartcheck.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePrompter @Inject constructor(
    @ApplicationContext context: Context
) : TextToSpeech.OnInitListener {

    private val isReady = AtomicBoolean(false)
    private val pending = ArrayDeque<String>()

    private var tts: TextToSpeech? = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINA
            isReady.set(true)
            while (pending.isNotEmpty()) {
                speak(pending.removeFirst())
            }
        }
    }

    fun speak(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return

        val engine = tts
        if (engine == null || !isReady.get()) {
            pending.addLast(content)
            return
        }

        engine.speak(
            content,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "smartcheck_${System.currentTimeMillis()}"
        )
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady.set(false)
        pending.clear()
    }
}
