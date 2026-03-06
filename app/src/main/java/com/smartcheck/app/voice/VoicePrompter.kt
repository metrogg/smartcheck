package com.smartcheck.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.smartcheck.app.domain.repository.IVoiceService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import java.util.UUID
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

    private var tts: TextToSpeech? = null
    private val context: Context = context.applicationContext

    init {
        Timber.d("[Voice] 初始化 TTS...")
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 尝试多种中文语言设置
            var result = tts?.setLanguage(Locale.CHINA)  // 简体中文
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)  // 繁体中文
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)  // 简体中文备用
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 尝试使用默认语言
                result = tts?.setLanguage(Locale.getDefault())
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.w("[Voice] 中文不可用，使用系统默认语言")
                tts?.setLanguage(Locale.getDefault())
            }
            
            // 设置语速和音调
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            
            isReady.set(true)
            Timber.d("[Voice] TTS 初始化成功，语言: ${Locale.getDefault().displayLanguage}")
            
            // 播放队列中的内容
            while (pending.isNotEmpty()) {
                speakInternal(pending.removeFirst())
            }
        } else {
            Timber.e("[Voice] TTS 初始化失败: status=$status，尝试使用系统默认引擎")
            // 尝试重新初始化
            try {
                tts?.shutdown()
                tts = TextToSpeech(context) { status2 ->
                    if (status2 == TextToSpeech.SUCCESS) {
                        isReady.set(true)
                        Timber.d("[Voice] TTS 重试初始化成功")
                        while (pending.isNotEmpty()) {
                            speakInternal(pending.removeFirst())
                        }
                    } else {
                        Timber.e("[Voice] TTS 重试也失败")
                    }
                }
            } catch (e: Exception) {
                Timber.e("[Voice] TTS 重试异常: ${e.message}")
            }
        }
    }

    private fun speakInternal(text: String) {
        val engine = tts
        if (engine == null || !isReady.get()) {
            Timber.w("[Voice] TTS 未就绪，加入队列: $text")
            pending.addLast(text)
            return
        }

        Timber.d("[Voice] 播报: $text")
        
        try {
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "smartcheck_${UUID.randomUUID()}"
            )
        } catch (e: Exception) {
            Timber.e("[Voice] 播报异常: ${e.message}")
        }
    }

    override fun speak(text: String) {
        if (!enabledRef.get()) {
            Timber.d("[Voice] 语音已禁用，跳过: $text")
            return
        }
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
