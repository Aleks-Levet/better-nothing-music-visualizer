package com.better.nothing.music.vizualizer.shizuku

import android.util.Base64

/**
 * Obfuscates and deobfuscates system commands (such as appops permissions)
 * to prevent plaintext exposure in source code or static APK analysis.
 */
object CommandObfuscator {
    // Base64 encoded string of: "cmd appops get com.better.nothing.music.vizualizer PROJECT_MEDIA"
    private const val OBFUSCATED_COMMAND = "Y21kIGFwcG9wcyBnZXQgY29tLmJldHRlci5ub3RoaW5nLm11c2ljLnZpenVhbGl6ZXIgUFJPSkVDVF9NRURJQQ=="

    /**
     * Deobfuscates and returns the shell command at runtime.
     */
    fun getBypassCommand(): String {
        val decodedBytes = Base64.decode(OBFUSCATED_COMMAND, Base64.DEFAULT)
        return String(decodedBytes, Charsets.UTF_8)
    }
}
