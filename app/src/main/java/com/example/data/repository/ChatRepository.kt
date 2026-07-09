package com.example.data.repository

import com.example.data.dao.ChatDao
import com.example.data.model.ChatConnection
import com.example.data.model.Message
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allUsersFlow: Flow<List<User>> = chatDao.getAllUsersFlow()

    suspend fun getAllUsers(): List<User> = chatDao.getAllUsers()

    suspend fun getUserByPhone(phone: String): User? = chatDao.getUserByPhone(phone)

    suspend fun getUserBySecretCode(code: String): User? = chatDao.getUserBySecretCode(code)

    suspend fun insertUser(user: User) = chatDao.insertUser(user)

    fun getChatConnectionsFlow(mySecretCode: String): Flow<List<ChatConnection>> =
        chatDao.getChatConnectionsFlow(mySecretCode)

    suspend fun getChatConnection(mySecretCode: String, peerSecretCode: String): ChatConnection? =
        chatDao.getChatConnection(mySecretCode, peerSecretCode)

    suspend fun insertChatConnection(chatConnection: ChatConnection) =
        chatDao.insertChatConnection(chatConnection)

    fun getMessagesFlow(chatConnectionId: Int): Flow<List<Message>> =
        chatDao.getMessagesFlow(chatConnectionId)

    fun getMessagesByCodesFlow(myCode: String, peerCode: String): Flow<List<Message>> =
        chatDao.getMessagesByCodesFlow(myCode, peerCode)

    suspend fun insertMessage(message: Message) = chatDao.insertMessage(message)

    suspend fun getMessageDuplicate(sender: String, receiver: String, timestamp: Long, text: String): Message? =
        chatDao.getMessageDuplicate(sender, receiver, timestamp, text)

    suspend fun deleteMessagesForConnection(chatConnectionId: Int) =
        chatDao.deleteMessagesForConnection(chatConnectionId)

    suspend fun deleteChatConnection(connectionId: Int) =
        chatDao.deleteChatConnection(connectionId)
}
