package com.example

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.database.AppDatabase
import com.example.data.model.ChatConnection
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory
import com.example.ui.viewmodel.DecryptedMessage
import com.example.ui.viewmodel.Screen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------- IMMERSIVE UI STYLING HELPERS -------------------
fun Modifier.immersiveBackground(theme: AppTheme, baseColor: Color): Modifier = this.drawBehind {
    drawRect(color = baseColor)
    if (theme == AppTheme.LOVE || theme == AppTheme.BLACK) {
        val glowColor = if (theme == AppTheme.LOVE) Color(0xFFE11D48).copy(alpha = 0.12f) else Color(0xFF90A4AE).copy(alpha = 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                radius = size.width * 1.0f
            ),
            center = androidx.compose.ui.geometry.Offset(size.width, 0f),
            radius = size.width * 1.0f
        )
    }
}

@Composable
fun getCardBorder(theme: AppTheme): BorderStroke {
    val borderColor = when (theme) {
        AppTheme.WHITE -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        AppTheme.BLACK -> Color.White.copy(alpha = 0.08f)
        AppTheme.LOVE -> Color(0xFFE11D48).copy(alpha = 0.15f)
    }
    return BorderStroke(1.dp, borderColor)
}

@Composable
fun getMessageBubbleBrush(theme: AppTheme): Brush {
    val colors = when (theme) {
        AppTheme.WHITE -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
        AppTheme.BLACK -> listOf(Color(0xFF2C3E50), Color(0xFF1A252F))
        AppTheme.LOVE -> listOf(Color(0xFFE11D48), Color(0xFFBE123C))
    }
    return Brush.linearGradient(colors)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(database.chatDao())
        val factory = ChatViewModelFactory(application, repository)

        setContent {
            val viewModel: ChatViewModel = viewModel(factory = factory)
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

            MyApplicationTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(
                        targetState = currentScreen,
                        animationSpec = spring(),
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Register -> RegisterScreen(viewModel)
                            is Screen.Home -> HomeScreen(viewModel)
                            is Screen.Chat -> ChatScreen(viewModel, screen.connection)
                        }
                    }
                }
            }
        }
    }
}

// ------------------- UI SCREENS -------------------

@Composable
fun RegisterScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    val error by viewModel.registerError.collectAsStateWithLifecycle()

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .immersiveBackground(currentTheme, MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(getCardBorder(currentTheme), RoundedCornerShape(28.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_chat_logo),
                contentDescription = "AS-CHAT Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App Name
        Text(
            text = "AS-CHAT",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.SansSerif
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF4ADE80), CircleShape)
            )
            Text(
                text = "End-to-End Encrypted",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = getCardBorder(currentTheme),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Verify Anonymously",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "We use your phone number purely for anonymous validation. It is never disclosed, shared, or displayed to anyone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Nickname Input
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (Anonymous display name)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_nickname_input")
                )

                // Phone Number Input
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1 (555) 019-2834") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_phone_input")
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        viewModel.registerUser(phoneNumber, nickname)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Get My Secret Code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allUsers by viewModel.allLocalUsers.collectAsStateWithLifecycle()
    val connections by viewModel.chatConnections.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val connectionError by viewModel.connectionError.collectAsStateWithLifecycle()

    var peerCodeInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberLazyListState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editNickname by remember(currentUser) { mutableStateOf(currentUser?.nickname ?: "") }
    var editProfilePicUri by remember { mutableStateOf<Uri?>(null) }
    var editCapturedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val editGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            editProfilePicUri = uri
        }
    }

    val editCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            editProfilePicUri = editCapturedPhotoUri
        }
    }

    val editCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempUri = createTempImageUri(context)
            editCapturedPhotoUri = tempUri
            editCameraLauncher.launch(tempUri)
        } else {
            Toast.makeText(context, "Camera permission is required to click photos.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image Preview / Selector
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable {
                                editGalleryLauncher.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (editProfilePicUri != null) {
                            AsyncImage(
                                model = editProfilePicUri,
                                contentDescription = "Profile Picture Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(onClick = { editGalleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gallery")
                        }
                        TextButton(onClick = {
                            val hasCamPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCamPerm) {
                                val tempUri = createTempImageUri(context)
                                editCapturedPhotoUri = tempUri
                                editCameraLauncher.launch(tempUri)
                            } else {
                                editCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Camera")
                        }
                    }

                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNickname.isNotBlank()) {
                            viewModel.updateProfile(editNickname, editProfilePicUri)
                            showEditProfileDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .immersiveBackground(currentTheme, MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // App Header & Theme Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "AS-CHAT",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF4ADE80), CircleShape)
                    )
                    Text(
                        text = "End-to-End Encrypted",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Theme Switcher Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeButton(
                    theme = AppTheme.WHITE,
                    isSelected = currentTheme == AppTheme.WHITE,
                    icon = Icons.Default.LightMode,
                    onClick = { viewModel.setTheme(AppTheme.WHITE) }
                )
                ThemeButton(
                    theme = AppTheme.BLACK,
                    isSelected = currentTheme == AppTheme.BLACK,
                    icon = Icons.Default.DarkMode,
                    onClick = { viewModel.setTheme(AppTheme.BLACK) }
                )
                ThemeButton(
                    theme = AppTheme.LOVE,
                    isSelected = currentTheme == AppTheme.LOVE,
                    icon = Icons.Default.Favorite,
                    onClick = { viewModel.setTheme(AppTheme.LOVE) }
                )
            }
        }

        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card (Your Information)
            item {
                currentUser?.let { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = getCardBorder(currentTheme)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    UserAvatar(
                                        profilePictureUri = user.profilePictureUri,
                                        nickname = user.nickname,
                                        size = 44.dp
                                    )
                                    Column {
                                        Text(
                                            text = user.nickname,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Anonymously Verified",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = {
                                        editNickname = user.nickname
                                        editProfilePicUri = user.profilePictureUri?.let { Uri.parse(it) }
                                        showEditProfileDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(onClick = { viewModel.logout() }) {
                                        Icon(
                                            imageVector = Icons.Default.ExitToApp,
                                            contentDescription = "Logout",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Display Secret Code
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Secret Code", user.secretCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied code: ${user.secretCode}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "YOUR SECRET VERIFICATION CODE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = user.secretCode,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Connection Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = getCardBorder(currentTheme),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Connect with someone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = peerCodeInput,
                                onValueChange = { peerCodeInput = it },
                                label = { Text("Enter Friend's Secret Code") },
                                placeholder = { Text("AS-XXXXXX") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    viewModel.connectWithPeerCode(peerCodeInput)
                                    keyboardController?.hide()
                                }),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("connect_code_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.connectWithPeerCode(peerCodeInput)
                                    peerCodeInput = ""
                                    keyboardController?.hide()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("connect_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Connect")
                            }
                        }

                        if (connectionError != null) {
                            Text(
                                text = connectionError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }



            // Title: Chats
            item {
                Text(
                    text = "Your Chats",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Chats List
            if (connections.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Connected Chats Yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Enter a friend's secret verification code above to link end-to-end encrypted chats.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                        )
                    }
                }
            } else {
                items(connections, key = { it.id }) { connection ->
                    ChatItemCard(
                        connection = connection,
                        onClick = { viewModel.navigateToChat(connection) },
                        onDelete = { viewModel.deleteChat(connection) },
                        theme = currentTheme
                    )
                }
            }
        }
    }
}

@Composable
fun ChatItemCard(
    connection: ChatConnection,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    theme: AppTheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = getCardBorder(theme)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserAvatar(
                    profilePictureUri = connection.peerProfilePictureUri,
                    nickname = connection.peerNickname,
                    size = 40.dp,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )

                Column {
                    Text(
                        text = connection.peerNickname,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "E2E Encrypted",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Disconnect Chat",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ThemeButton(
    theme: AppTheme,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = theme.name,
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, connection: ChatConnection) {
    val context = LocalContext.current
    val decryptedMessages by viewModel.decryptedMessages.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for selecting an image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // Launcher for taking a photo with the camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri = capturedPhotoUri
        }
    }

    // Camera permission checker
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempUri = createTempImageUri(context)
            capturedPhotoUri = tempUri
            cameraLauncher.launch(tempUri)
        } else {
            Toast.makeText(context, "Camera permission is required to click photos.", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to the bottom of the list when new messages arrive
    LaunchedEffect(decryptedMessages.size) {
        if (decryptedMessages.isNotEmpty()) {
            scrollState.animateScrollToItem(decryptedMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .immersiveBackground(currentTheme, MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Chat Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .drawBehind {
                    val borderColor = when (currentTheme) {
                        AppTheme.WHITE -> Color.Black.copy(alpha = 0.05f)
                        AppTheme.BLACK -> Color.White.copy(alpha = 0.05f)
                        AppTheme.LOVE -> Color(0xFFE11D48).copy(alpha = 0.12f)
                    }
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { viewModel.navigateToHome() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                UserAvatar(
                    profilePictureUri = connection.peerProfilePictureUri,
                    nickname = connection.peerNickname,
                    size = 36.dp,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )

                Column {
                    Text(
                        text = connection.peerNickname,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color(0xFF4ADE80), CircleShape)
                        )
                        Text(
                            text = "End-to-End Encrypted",
                            fontSize = 10.sp,
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            IconButton(onClick = {
                viewModel.deleteChat(connection)
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Conversation",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }

        // Selected Image Preview Bar
        AnimatedVisibility(
            visible = selectedImageUri != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Photo",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "Photo attached",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { selectedImageUri = null }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel attachment",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Messages Box
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Transparent)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(decryptedMessages, key = { it.id }) { message ->
                MessageBubble(message, currentTheme)
            }
        }

        // Chat Input Control Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val inputBgColor = when (currentTheme) {
                    AppTheme.WHITE -> Color(0xFFF1F5F9)
                    AppTheme.BLACK -> Color(0xFF1A1A1A)
                    AppTheme.LOVE -> Color(0xFF1A1A1A)
                }
                val inputBorderColor = when (currentTheme) {
                    AppTheme.WHITE -> Color.Black.copy(alpha = 0.05f)
                    AppTheme.BLACK -> Color.White.copy(alpha = 0.05f)
                    AppTheme.LOVE -> Color.White.copy(alpha = 0.05f)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(inputBgColor, RoundedCornerShape(28.dp))
                        .border(1.dp, inputBorderColor, RoundedCornerShape(28.dp))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera Button
                    IconButton(onClick = {
                        val hasCamPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPerm) {
                            val tempUri = createTempImageUri(context)
                            capturedPhotoUri = tempUri
                            cameraLauncher.launch(tempUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Gallery Button
                    IconButton(onClick = {
                        galleryLauncher.launch("image/*")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Select Photo from Gallery",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Text Input
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write anonymously...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onDone = {
                            if (messageText.trim().isNotEmpty() || selectedImageUri != null) {
                                viewModel.sendMessage(messageText, selectedImageUri)
                                messageText = ""
                                selectedImageUri = null
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }

                // Send Button
                IconButton(
                    onClick = {
                        if (messageText.trim().isNotEmpty() || selectedImageUri != null) {
                            viewModel.sendMessage(messageText, selectedImageUri)
                            messageText = ""
                            selectedImageUri = null
                        }
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .size(48.dp)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = -20f)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: DecryptedMessage, theme: AppTheme) {
    val simpleDateFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { simpleDateFormat.format(Date(message.timestamp)) }

    val bubbleShape = if (message.isMe) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    val bubbleModifier = if (message.isMe) {
        Modifier
            .background(brush = getMessageBubbleBrush(theme), shape = bubbleShape)
    } else {
        val bgColor = when (theme) {
            AppTheme.WHITE -> Color(0xFFF1F5F9)
            AppTheme.BLACK -> Color(0xFF1A1A1A)
            AppTheme.LOVE -> Color(0xFF1A1A1A)
        }
        val borderColor = when (theme) {
            AppTheme.WHITE -> Color.Black.copy(alpha = 0.05f)
            AppTheme.BLACK -> Color.White.copy(alpha = 0.05f)
            AppTheme.LOVE -> Color.White.copy(alpha = 0.05f)
        }
        Modifier
            .background(color = bgColor, shape = bubbleShape)
            .border(width = 1.dp, color = borderColor, shape = bubbleShape)
    }

    val contentColor = if (message.isMe) {
        Color.White
    } else {
        when (theme) {
            AppTheme.WHITE -> Color(0xFF1F2937)
            AppTheme.BLACK -> Color(0xFFD1D5DB)
            AppTheme.LOVE -> Color(0xFFD1D5DB)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(bubbleModifier)
                .padding(12.dp)
        ) {
            Column {
                // If there's an image saved locally
                if (message.imageUri != null) {
                    val file = remember(message.imageUri) { File(message.imageUri) }
                    AsyncImage(
                        model = file,
                        contentDescription = "Shared image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .padding(bottom = 6.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                if (message.plainText.isNotEmpty()) {
                    Text(
                        text = message.plainText,
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formattedTime,
                    fontSize = 9.sp,
                    color = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// Helper to create a secure temporary URI for taking standard camera captures without Android version issues
private fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("temp_cam_capture", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

@Composable
fun UserAvatar(
    profilePictureUri: String?,
    nickname: String,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
) {
    if (profilePictureUri != null) {
        val file = remember(profilePictureUri) { File(profilePictureUri) }
        AsyncImage(
            model = file,
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = nickname.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = textStyle
            )
        }
    }
}
