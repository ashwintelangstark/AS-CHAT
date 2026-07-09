package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crypto.EncryptionHelper
import com.example.data.database.AppDatabase
import com.example.data.model.ChatConnection
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.ui.theme.AppTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed interface Screen {
    object Register : Screen
    object Home : Screen
    data class Chat(val connection: ChatConnection) : Screen
}

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("as_chat_prefs", Context.MODE_PRIVATE)

    // Firebase Firestore Initialization and Listener Registrations
    private val firestore: FirebaseFirestore by lazy {
        val context = getApplication<Application>()
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("as-chat-demo")
                    .setApplicationId(context.packageName)
                    .setApiKey("AIzaSyFakeKeyForFallbackCompilation")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            Log.e("FirebaseInit", "Error in manual init", e)
        }
        FirebaseFirestore.getInstance()
    }

    private var incomingConnectionsListener: ListenerRegistration? = null
    private var incomingMessagesListener: ListenerRegistration? = null
    private var outgoingMessagesListener: ListenerRegistration? = null

    // Current screen state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Register)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Dynamic app theme
    private val _currentTheme = MutableStateFlow(AppTheme.WHITE)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    // Current logged-in user profile
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // All registered local profiles for the visual emulator account switcher
    val allLocalUsers: StateFlow<List<User>> = repository.allUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active chat connections for current user
    @OptIn(ExperimentalCoroutinesApi::class)
    val chatConnections: StateFlow<List<ChatConnection>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getChatConnectionsFlow(user.secretCode)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConnectionId = MutableStateFlow<Int?>(null)

    // Messages for the active chat connection (resolved dynamically by user codes)
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<Message>> = _currentScreen
        .flatMapLatest { screen ->
            val user = _currentUser.value
            if (screen is Screen.Chat && user != null) {
                repository.getMessagesByCodesFlow(user.secretCode, screen.connection.peerSecretCode)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Decrypted active messages in real-time
    val decryptedMessages: StateFlow<List<DecryptedMessage>> = combine(
        activeMessages,
        _currentUser,
        _currentScreen
    ) { messages, user, screen ->
        if (user == null || screen !is Screen.Chat) return@combine emptyList()
        val peerCode = screen.connection.peerSecretCode
        val secretKey = EncryptionHelper.deriveKey(user.secretCode, peerCode)
        
        messages.map { msg ->
            val isMe = msg.senderSecretCode == user.secretCode
            val plainText = EncryptionHelper.decrypt(msg.encryptedText, secretKey)
            DecryptedMessage(
                id = msg.id,
                plainText = plainText,
                imageUri = msg.imageUri,
                isMe = isMe,
                timestamp = msg.timestamp
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Registration input errors
    private val _registerError = MutableStateFlow<String?>(null)
    val registerError: StateFlow<String?> = _registerError.asStateFlow()

    // Connection input errors
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    init {
        // Load persisted theme
        val savedThemeName = prefs.getString("selected_theme", AppTheme.WHITE.name)
        _currentTheme.value = AppTheme.valueOf(savedThemeName ?: AppTheme.WHITE.name)

        // Auto-login last active user if saved
        val savedUserPhone = prefs.getString("last_user_phone", null)
        if (savedUserPhone != null) {
            viewModelScope.launch {
                val user = repository.getUserByPhone(savedUserPhone)
                if (user != null) {
                    _currentUser.value = user
                    startFirebaseSync(user)
                    _currentScreen.value = Screen.Home
                }
            }
        }
    }

    private val peerListeners = mutableListOf<ListenerRegistration>()

    private fun stopFirebaseSync() {
        incomingConnectionsListener?.remove()
        incomingConnectionsListener = null
        incomingMessagesListener?.remove()
        incomingMessagesListener = null
        outgoingMessagesListener?.remove()
        outgoingMessagesListener = null
        peerListeners.forEach { it.remove() }
        peerListeners.clear()
    }

    private fun startFirebaseSync(me: User) {
        stopFirebaseSync()
        val myCode = me.secretCode

        // 1. Listen for incoming connection requests
        incomingConnectionsListener = firestore.collection("users")
            .document(myCode)
            .collection("incoming_connections")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseSync", "Connections listener error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    for (doc in snapshot.documentChanges) {
                        if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = doc.document.data
                            val peerCode = data["mySecretCode"] as? String ?: continue
                            val peerNickname = data["peerNickname"] as? String ?: "Anonymous"
                            
                            viewModelScope.launch(Dispatchers.IO) {
                                val existing = repository.getChatConnection(myCode, peerCode)
                                if (existing == null) {
                                    // Fetch detailed peer profile to get latest nickname/picture
                                    firestore.collection("users").document(peerCode).get()
                                        .addOnSuccessListener { pDoc ->
                                            if (pDoc != null) {
                                                val pNickname = pDoc.getString("nickname") ?: peerNickname
                                                val pBase64 = pDoc.getString("profilePictureBase64")
                                                viewModelScope.launch(Dispatchers.IO) {
                                                    val localPicUri = pBase64?.let { saveBase64ImageToInternalStorage(it) }
                                                    val newConn = ChatConnection(
                                                        mySecretCode = myCode,
                                                        peerSecretCode = peerCode,
                                                        peerNickname = pNickname,
                                                        peerProfilePictureUri = localPicUri
                                                    )
                                                    repository.insertChatConnection(newConn)
                                                }
                                            }
                                        }
                                        .addOnFailureListener {
                                            viewModelScope.launch(Dispatchers.IO) {
                                                val newConn = ChatConnection(
                                                    mySecretCode = myCode,
                                                    peerSecretCode = peerCode,
                                                    peerNickname = peerNickname
                                                )
                                                repository.insertChatConnection(newConn)
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }

        // Helper to process raw Firestore message map
        fun processFirestoreMessage(data: Map<String, Any>) {
            val sender = data["senderSecretCode"] as? String ?: return
            val receiver = data["receiverSecretCode"] as? String ?: return
            val encryptedText = data["encryptedText"] as? String ?: ""
            val imageBase64 = data["imageBase64"] as? String
            val timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()

            val isMe = sender == myCode
            val peerCode = if (isMe) receiver else sender

            viewModelScope.launch(Dispatchers.IO) {
                // Ensure a local connection exists for this peer
                var localConn = repository.getChatConnection(myCode, peerCode)
                if (localConn == null) {
                    // Fetch peer nickname from Firestore users collection
                    var peerNickname = "Anonymous"
                    try {
                        firestore.collection("users").document(peerCode).get()
                            .addOnSuccessListener { pDoc ->
                                if (pDoc != null && pDoc.exists()) {
                                    peerNickname = pDoc.getString("nickname") ?: "Anonymous"
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val newConn = ChatConnection(
                                            mySecretCode = myCode,
                                            peerSecretCode = peerCode,
                                            peerNickname = peerNickname
                                        )
                                        repository.insertChatConnection(newConn)
                                        val secondCheck = repository.getChatConnection(myCode, peerCode)
                                        insertMessageToLocal(secondCheck?.id ?: 0, sender, receiver, encryptedText, imageBase64, timestamp)
                                    }
                                } else {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val newConn = ChatConnection(
                                            mySecretCode = myCode,
                                            peerSecretCode = peerCode,
                                            peerNickname = "Anonymous"
                                        )
                                        repository.insertChatConnection(newConn)
                                        val secondCheck = repository.getChatConnection(myCode, peerCode)
                                        insertMessageToLocal(secondCheck?.id ?: 0, sender, receiver, encryptedText, imageBase64, timestamp)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val newConn = ChatConnection(
                                        mySecretCode = myCode,
                                        peerSecretCode = peerCode,
                                        peerNickname = "Anonymous"
                                    )
                                    repository.insertChatConnection(newConn)
                                    val secondCheck = repository.getChatConnection(myCode, peerCode)
                                    insertMessageToLocal(secondCheck?.id ?: 0, sender, receiver, encryptedText, imageBase64, timestamp)
                                }
                            }
                    } catch (ex: Exception) {
                        Log.e("FirebaseSync", "Failed to fetch peer profile", ex)
                    }
                } else {
                    insertMessageToLocal(localConn.id, sender, receiver, encryptedText, imageBase64, timestamp)
                }
            }
        }

        // 2. Listen for incoming messages (where I am the receiver)
        incomingMessagesListener = firestore.collection("messages")
            .whereEqualTo("receiverSecretCode", myCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseSync", "Incoming messages error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    for (doc in snapshot.documentChanges) {
                        if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            processFirestoreMessage(doc.document.data)
                        }
                    }
                }
            }

        // 3. Listen for outgoing messages (where I am the sender, in case sent from another instance)
        outgoingMessagesListener = firestore.collection("messages")
            .whereEqualTo("senderSecretCode", myCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FirebaseSync", "Outgoing messages error", e)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    for (doc in snapshot.documentChanges) {
                        if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            processFirestoreMessage(doc.document.data)
                        }
                    }
                }
            }

        // 4. One-time update of peer profiles to keep nicknames/profile pictures fresh without infinite CPU loops
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val connections = repository.getChatConnectionsFlow(myCode).first()
                connections.forEach { conn ->
                    firestore.collection("users").document(conn.peerSecretCode).get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                val serverNickname = doc.getString("nickname") ?: conn.peerNickname
                                val base64Pic = doc.getString("profilePictureBase64")
                                
                                viewModelScope.launch(Dispatchers.IO) {
                                    var updatedPicUri = conn.peerProfilePictureUri
                                    // Only decode and save the base64 profile picture if we don't have it locally yet
                                    if (base64Pic != null && conn.peerProfilePictureUri == null) {
                                        val path = saveBase64ImageToInternalStorage(base64Pic)
                                        if (path != null) {
                                            updatedPicUri = path
                                        }
                                    }
                                    
                                    if (serverNickname != conn.peerNickname || updatedPicUri != conn.peerProfilePictureUri) {
                                        val updatedConn = conn.copy(
                                            peerNickname = serverNickname,
                                            peerProfilePictureUri = updatedPicUri
                                        )
                                        repository.insertChatConnection(updatedConn)
                                    }
                                }
                            }
                        }
                }
            } catch (ex: Exception) {
                Log.e("FirebaseSync", "Error during one-time peer update", ex)
            }
        }
    }

    private fun insertMessageToLocal(
        connectionId: Int?,
        sender: String,
        receiver: String,
        encryptedText: String,
        imageBase64: String?,
        timestamp: Long
    ) {
        if (connectionId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val duplicate = repository.getMessageDuplicate(sender, receiver, timestamp, encryptedText)
            if (duplicate == null) {
                var localImageUri: String? = null
                if (!imageBase64.isNullOrEmpty()) {
                    localImageUri = saveBase64ImageToInternalStorage(imageBase64)
                }
                val message = Message(
                    chatConnectionId = connectionId,
                    senderSecretCode = sender,
                    receiverSecretCode = receiver,
                    encryptedText = encryptedText,
                    imageUri = localImageUri,
                    timestamp = timestamp
                )
                repository.insertMessage(message)
            }
        }
    }

    private suspend fun saveBase64ImageToInternalStorage(base64: String): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val directory = File(context.filesDir, "chat_images")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = "img_received_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 4)}.jpg"
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            outputStream.write(bytes)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("selected_theme", theme.name).apply()
    }

    fun registerUser(phoneNumber: String, nickname: String) {
        val cleanPhone = phoneNumber.trim()
        val cleanNickname = nickname.trim()

        if (cleanPhone.isEmpty() || cleanNickname.isEmpty()) {
            _registerError.value = "Phone number and nickname cannot be empty"
            return
        }

        viewModelScope.launch {
            _registerError.value = null
            val existingUser = repository.getUserByPhone(cleanPhone)
            if (existingUser != null) {
                // Phone number is already registered, so we log them in
                _currentUser.value = existingUser
                prefs.edit().putString("last_user_phone", existingUser.phoneNumber).apply()
                startFirebaseSync(existingUser)
                _currentScreen.value = Screen.Home
            } else {
                // Generate a unique anonymous secret code prefixed with AS-
                val randomSuffix = UUID.randomUUID().toString().substring(0, 6).uppercase()
                val secretCode = "AS-$randomSuffix"

                val newUser = User(
                    phoneNumber = cleanPhone,
                    secretCode = secretCode,
                    nickname = cleanNickname
                )
                repository.insertUser(newUser)

                // Save to Firestore globally so other instances can find this user
                val firebaseUserMap = mapOf(
                    "phoneNumber" to cleanPhone,
                    "secretCode" to secretCode,
                    "nickname" to cleanNickname,
                    "registeredAt" to newUser.registeredAt
                )
                firestore.collection("users").document(secretCode).set(firebaseUserMap)

                _currentUser.value = newUser
                prefs.edit().putString("last_user_phone", newUser.phoneNumber).apply()
                startFirebaseSync(newUser)
                _currentScreen.value = Screen.Home
            }
        }
    }

    fun switchUser(user: User) {
        _currentUser.value = user
        prefs.edit().putString("last_user_phone", user.phoneNumber).apply()
        startFirebaseSync(user)
        _currentScreen.value = Screen.Home
        _activeConnectionId.value = null
    }

    fun connectWithPeerCode(peerCode: String) {
        val cleanCode = peerCode.trim().uppercase()
        val me = _currentUser.value ?: return

        if (cleanCode.isEmpty()) {
            _connectionError.value = "Secret code cannot be empty"
            return
        }

        if (cleanCode == me.secretCode) {
            _connectionError.value = "You cannot connect with your own secret code"
            return
        }

        viewModelScope.launch {
            _connectionError.value = null

            // 1. Try to find user locally first (supports offline / local testing instantly!)
            val localPeer = repository.getUserBySecretCode(cleanCode)
            if (localPeer != null) {
                val existing = repository.getChatConnection(me.secretCode, cleanCode)
                if (existing != null) {
                    _connectionError.value = "You are already connected to this user"
                    return@launch
                }

                // Create local ChatConnection for me
                val connectionForMe = ChatConnection(
                    mySecretCode = me.secretCode,
                    peerSecretCode = cleanCode,
                    peerNickname = localPeer.nickname,
                    peerProfilePictureUri = localPeer.profilePictureUri
                )
                repository.insertChatConnection(connectionForMe)

                // Create local ChatConnection for them too, to make dual-testing flawless
                val connectionForPeer = ChatConnection(
                    mySecretCode = cleanCode,
                    peerSecretCode = me.secretCode,
                    peerNickname = me.nickname,
                    peerProfilePictureUri = me.profilePictureUri
                )
                repository.insertChatConnection(connectionForPeer)

                // Background sync to Firestore so that if Firestore is online, it is registered
                val connectionRequest = mapOf(
                    "mySecretCode" to me.secretCode,
                    "peerSecretCode" to cleanCode,
                    "peerNickname" to me.nickname,
                    "connectedAt" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(cleanCode)
                    .collection("incoming_connections")
                    .document(me.secretCode)
                    .set(connectionRequest)

                return@launch
            }

            // 2. Fallback to Firestore global search
            firestore.collection("users").document(cleanCode).get()
                .addOnSuccessListener { doc ->
                    if (doc == null || !doc.exists()) {
                        _connectionError.value = "Incorrect secret code or user not found"
                        return@addOnSuccessListener
                    }

                    val peerNickname = doc.getString("nickname") ?: "Anonymous"
                    val pBase64 = doc.getString("profilePictureBase64")

                    viewModelScope.launch(Dispatchers.IO) {
                        val existing = repository.getChatConnection(me.secretCode, cleanCode)
                        if (existing != null) {
                            _connectionError.value = "You are already connected to this user"
                            return@launch
                        }

                        val localPicUri = pBase64?.let { saveBase64ImageToInternalStorage(it) }

                        // Create local ChatConnection
                        val connectionForMe = ChatConnection(
                            mySecretCode = me.secretCode,
                            peerSecretCode = cleanCode,
                            peerNickname = peerNickname,
                            peerProfilePictureUri = localPicUri
                        )
                        repository.insertChatConnection(connectionForMe)

                        // Notify peer via Firestore by writing to their incoming_connections
                        val connectionRequest = mapOf(
                            "mySecretCode" to me.secretCode,
                            "peerSecretCode" to cleanCode,
                            "peerNickname" to me.nickname,
                            "connectedAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users")
                            .document(cleanCode)
                            .collection("incoming_connections")
                            .document(me.secretCode)
                            .set(connectionRequest)
                    }
                }
                .addOnFailureListener { e ->
                    // Fallback: If we are offline or Firestore query fails, we STILL create the connection as requested!
                    viewModelScope.launch {
                        val existing = repository.getChatConnection(me.secretCode, cleanCode)
                        if (existing != null) {
                            _connectionError.value = "You are already connected to this user"
                            return@launch
                        }

                        // Create local ChatConnection with placeholder nickname
                        val connectionForMe = ChatConnection(
                            mySecretCode = me.secretCode,
                            peerSecretCode = cleanCode,
                            peerNickname = "Peer (${cleanCode.takeLast(6)})"
                        )
                        repository.insertChatConnection(connectionForMe)

                        // Attempt to notify peer in background (will succeed when online)
                        val connectionRequest = mapOf(
                            "mySecretCode" to me.secretCode,
                            "peerSecretCode" to cleanCode,
                            "peerNickname" to me.nickname,
                            "connectedAt" to System.currentTimeMillis()
                        )
                        try {
                            firestore.collection("users")
                                .document(cleanCode)
                                .collection("incoming_connections")
                                .document(me.secretCode)
                                .set(connectionRequest)
                        } catch (ex: Exception) {
                            Log.e("FirebaseSync", "Failed background notification", ex)
                        }
                    }
                }
        }
    }

    fun updateProfile(newNickname: String, imageUri: Uri?) {
        val me = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var localImgPath: String? = me.profilePictureUri
            if (imageUri != null) {
                val isAlreadyLocal = imageUri.scheme == null || imageUri.scheme == "file" || imageUri.path?.contains("/files/chat_images") == true
                if (isAlreadyLocal) {
                    localImgPath = imageUri.path
                } else {
                    localImgPath = saveImageToInternalStorage(imageUri)
                }
            }
            
            val updatedUser = me.copy(
                nickname = newNickname,
                profilePictureUri = localImgPath
            )
            repository.insertUser(updatedUser)
            _currentUser.value = updatedUser
            
            // Also update globally in Firestore
            val base64Image = if (localImgPath != null) {
                try {
                    val file = File(localImgPath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        Base64.encodeToString(bytes, Base64.DEFAULT)
                    } else null
                } catch (e: Exception) {
                    null
                }
            } else null
            
            val updates = mutableMapOf<String, Any>(
                "nickname" to newNickname
            )
            if (base64Image != null) {
                updates["profilePictureBase64"] = base64Image
            }
            
            firestore.collection("users").document(me.secretCode).update(updates)
                .addOnFailureListener {
                    firestore.collection("users").document(me.secretCode).set(updates, com.google.firebase.firestore.SetOptions.merge())
                }
        }
    }

    fun navigateToChat(connection: ChatConnection) {
        _currentScreen.value = Screen.Chat(connection)
        _activeConnectionId.value = connection.id
    }

    fun navigateToHome() {
        _currentScreen.value = Screen.Home
        _activeConnectionId.value = null
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().remove("last_user_phone").apply()
        stopFirebaseSync()
        _currentScreen.value = Screen.Register
        _activeConnectionId.value = null
    }

    fun sendMessage(text: String, imageUri: Uri? = null) {
        val me = _currentUser.value ?: return
        val screen = _currentScreen.value
        if (screen !is Screen.Chat) return

        val peerCode = screen.connection.peerSecretCode
        val connId = screen.connection.id

        if (text.trim().isEmpty() && imageUri == null) return

        viewModelScope.launch(Dispatchers.IO) {
            val secretKey = EncryptionHelper.deriveKey(me.secretCode, peerCode)
            val encryptedText = EncryptionHelper.encrypt(text, secretKey)

            var storedImageUriString: String? = null
            if (imageUri != null) {
                // Save image locally to prevent permission loss
                storedImageUriString = saveImageToInternalStorage(imageUri)
            }

            // Insert message locally
            val timestamp = System.currentTimeMillis()
            val message = Message(
                chatConnectionId = connId,
                senderSecretCode = me.secretCode,
                receiverSecretCode = peerCode,
                encryptedText = encryptedText,
                imageUri = storedImageUriString,
                timestamp = timestamp
            )
            repository.insertMessage(message)

            // To support bidirectional chat in the local simulator, we find the counterpart connection
            val counterpartConn = repository.getChatConnection(peerCode, me.secretCode)
            if (counterpartConn != null) {
                val peerMessage = Message(
                    chatConnectionId = counterpartConn.id,
                    senderSecretCode = me.secretCode,
                    receiverSecretCode = peerCode,
                    encryptedText = encryptedText,
                    imageUri = storedImageUriString,
                    timestamp = timestamp
                )
                repository.insertMessage(peerMessage)
            }

            // Upload the message to Firestore globally for interserver hosting
            val msgId = UUID.randomUUID().toString()
            val messageMap = mutableMapOf<String, Any>(
                "senderSecretCode" to me.secretCode,
                "receiverSecretCode" to peerCode,
                "encryptedText" to encryptedText,
                "timestamp" to timestamp
            )

            if (storedImageUriString != null) {
                try {
                    val file = File(storedImageUriString)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                        messageMap["imageBase64"] = base64
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseSync", "Failed to encode image to Base64", e)
                }
            }

            firestore.collection("messages").document(msgId).set(messageMap)
        }
    }

    private suspend fun saveImageToInternalStorage(uri: Uri): String? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val directory = File(context.filesDir, "chat_images")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 4)}.jpg"
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            
            // Compress image to manage storage size
            val bitmap = BitmapFactory.decodeStream(inputStream)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteChat(connection: ChatConnection) {
        viewModelScope.launch {
            repository.deleteMessagesForConnection(connection.id)
            repository.deleteChatConnection(connection.id)
            
            // Also delete counterparts for absolute cleanliness
            val me = _currentUser.value
            if (me != null) {
                val peerConn = repository.getChatConnection(connection.peerSecretCode, me.secretCode)
                if (peerConn != null) {
                    repository.deleteMessagesForConnection(peerConn.id)
                    repository.deleteChatConnection(peerConn.id)
                }
            }
            navigateToHome()
        }
    }
}

data class DecryptedMessage(
    val id: Int,
    val plainText: String,
    val imageUri: String?,
    val isMe: Boolean,
    val timestamp: Long
)

class ChatViewModelFactory(
    private val application: Application,
    private val repository: ChatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
