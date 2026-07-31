package com.better.nothing.music.vizualizer.logic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.better.nothing.music.vizualizer.model.UserProfile
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class UserRepository {
    private val database = FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app")
    private val usersRef = database.getReference("users")
    private val codesRef = database.getReference("premium_codes")

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            snapshot.getValue(UserProfile::class.java)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching profile", e)
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        try {
            usersRef.child(profile.userId).setValue(profile).await()
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving profile", e)
            throw e
        }
    }

    suspend fun generatePremiumCode(): String = withContext(Dispatchers.IO) {
        val code = java.util.UUID.randomUUID().toString().take(8).uppercase()
        codesRef.child(code).setValue(true).await()
        code
    }

    suspend fun redeemPremiumCode(userId: String, code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val codeSnapshot = codesRef.child(code).get().await()
            if (codeSnapshot.exists()) {
                // Code is valid. Remove it and grant premium.
                codesRef.child(code).removeValue().await()
                usersRef.child(userId).child("supporter").setValue(true).await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error redeeming code", e)
            false
        }
    }

    suspend fun uploadAvatarFromResource(userId: String, resourceId: Int, context: android.content.Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Encoding resource $resourceId to Base64")
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                ?: throw Exception("Could not decode resource")
            encodeBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e("UserRepository", "Resource encoding failed", e)
            throw e
        }
    }

    suspend fun uploadProfilePicture(userId: String, imageUri: Uri, context: android.content.Context): String = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Encoding Uri $imageUri to Base64")
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw Exception("Could not open input stream")
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw Exception("Could not decode stream")
            encodeBitmapToBase64(bitmap)
        } catch (e: Exception) {
            Log.e("UserRepository", "Uri encoding failed", e)
            throw e
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        // 1. Scale down to 200x200 to keep RTDB lightweight
        val scaled = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
        
        // 2. Compress to high-quality JPEG
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        
        // 3. Convert to Base64 Data URL
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
