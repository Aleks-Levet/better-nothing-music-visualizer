package com.better.nothing.music.vizualizer.model

import com.google.firebase.database.PropertyName
import androidx.annotation.Keep

@Keep
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val profilePictureUrl: String? = null,
    val totalVisualizedTime: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("supporter")
    @set:PropertyName("supporter")
    var isSupporter: Boolean = false
)

