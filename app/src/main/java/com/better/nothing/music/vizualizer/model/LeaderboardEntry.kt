package com.better.nothing.music.vizualizer.model

import androidx.annotation.Keep

import com.google.firebase.database.PropertyName

@Keep
data class LeaderboardEntry(
    val userId: String = "",
    val name: String = "Anonymous",
    val profilePictureUrl: String? = null,
    val totalTimeMs: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    @get:PropertyName("supporter")
    @set:PropertyName("supporter")
    var isSupporter: Boolean = false
)

