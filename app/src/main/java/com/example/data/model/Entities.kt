package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val phoneNumber: String,
    val secretCode: String,
    val nickname: String,
    val registeredAt: Long = System.currentTimeMillis(),
    val profilePictureUri: String? = null
)

@Entity(tableName = "chat_connections")
data class ChatConnection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mySecretCode: String,
    val peerSecretCode: String,
    val peerNickname: String,
    val connectedAt: Long = System.currentTimeMillis(),
    val peerProfilePictureUri: String? = null
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatConnectionId: Int,
    val senderSecretCode: String,
    val receiverSecretCode: String,
    val encryptedText: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
