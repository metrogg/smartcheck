package com.smartcheck.app.domain.repository

interface IVoiceService {

    fun speak(text: String)

    fun speakQueue(text: String)

    fun setEnabled(enabled: Boolean)

    fun isEnabled(): Boolean

    fun shutdown()
}
