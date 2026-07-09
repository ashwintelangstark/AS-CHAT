package com.example.data.dao

import androidx.room.*
import com.example.data.model.ChatConnection
import com.example.data.model.Message
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // User Profiles (Local Simulated Multi-user support)
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users WHERE phoneNumber = :phoneNumber")
    suspend fun getUserByPhone(phoneNumber: String): User?

    @Query("SELECT * FROM users WHERE secretCode = :secretCode")
    suspend fun getUserBySecretCode(secretCode: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Chat Connections
    @Query("SELECT * FROM chat_connections WHERE mySecretCode = :mySecretCode")
    fun getChatConnectionsFlow(mySecretCode: String): Flow<List<ChatConnection>>

    @Query("SELECT * FROM chat_connections WHERE mySecretCode = :mySecretCode AND peerSecretCode = :peerSecretCode")
    suspend fun getChatConnection(mySecretCode: String, peerSecretCode: String): ChatConnection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatConnection(chatConnection: ChatConnection)

    // Messages
    @Query("SELECT * FROM messages WHERE chatConnectionId = :chatConnectionId ORDER BY timestamp ASC")
    fun getMessagesFlow(chatConnectionId: Int): Flow<List<Message>>

    @Query("""
        SELECT * FROM messages 
        WHERE (senderSecretCode = :myCode AND receiverSecretCode = :peerCode) 
           OR (senderSecretCode = :peerCode AND receiverSecretCode = :myCode)
        ORDER BY timestamp ASC
    """)
    fun getMessagesByCodesFlow(myCode: String, peerCode: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM messages WHERE senderSecretCode = :sender AND receiverSecretCode = :receiver AND timestamp = :timestamp AND encryptedText = :text LIMIT 1")
    suspend fun getMessageDuplicate(sender: String, receiver: String, timestamp: Long, text: String): Message?

    @Query("DELETE FROM messages WHERE chatConnectionId = :chatConnectionId")
    suspend fun deleteMessagesForConnection(chatConnectionId: Int)

    @Query("DELETE FROM chat_connections WHERE id = :connectionId")
    suspend fun deleteChatConnection(connectionId: Int)
}
