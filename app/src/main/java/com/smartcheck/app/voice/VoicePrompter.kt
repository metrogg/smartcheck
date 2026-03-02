package com.smartcheck.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.smartcheck.app.domain.repository.IVoiceService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePrompter @Inject constructor(
    @ApplicationContext context: Context
) : IVoiceService, TextToSpeech.OnInitListener {

    private val isReady = AtomicBoolean(false)
    private val pending = ArrayDeque<String>()
    private val enabledRef = AtomicReference(true)

    private var tts: TextToSpeech? = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINA
            isReady.set(true)
            while (pending.isNotEmpty()) {
                speakInternal(pending.removeFirst())
            }
        }
    }

    private fun speakInternal(text: String) {
        val engine = tts
        if (engine == null || !isReady.get()) {
            pending.addLast(text)
            return
        }

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "smartcheck_${System.currentTimeMillis()}"
        )
    }

    override fun speak(text: String) {
        if (!enabledRef.get()) return
        val content = text.trim()
        if (content.isEmpty()) return

        speakInternal(content)
    }

    override fun speakQueue(text: String) {
        if (!enabledRef.get()) return
        val content = text.trim()
        if (content.isEmpty()) return

        val engine = tts
        if (engine == null || !isReady.get()) {
            pending.addLast(content)
            return
        }

        engine.speak(
            content,
            TextToSpeech.QUEUE_ADD,
            null,
            "smartcheck_${System.currentTimeMillis()}"
        )
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady.set(false)
        pending.clear()
    }

    override fun setEnabled(enabled: Boolean) {
        enabledRef.set(enabled)
    }

    override fun isEnabled(): Boolean = enabledRef.get()
}
