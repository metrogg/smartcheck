package com.smartcheck.sdk.face

import android.graphics.PointF
import android.graphics.RectF

data class FaceInfo(
    val id: Int,
    val box: RectF,
    val score: Float,
    val landmarks: List<PointF> = emptyList()
)
