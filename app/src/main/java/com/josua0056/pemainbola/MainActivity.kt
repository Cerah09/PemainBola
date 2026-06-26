package com.josua0056.pemainbola

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.josua0056.pemainbola.data.Player
import com.josua0056.pemainbola.ui.MainViewModel
import com.josua0056.pemainbola.ui.LoginScreen
import com.josua0056.pemainbola.ui.PlayerListScreen
import com.josua0056.pemainbola.ui.ProfileScreen
import com.josua0056.pemainbola.ui.AddPlayerDialog
import com.josua0056.pemainbola.ui.DeleteConfirmationDialog
import com.josua0056.pemainbola.ui.theme.PemainBolaTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PemainBolaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NamaPemainbolaApp()
                }
            }
        }
    }
}

@Composable
fun NamaPemainbolaApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val user by viewModel.user.collectAsState()
    val players by viewModel.players.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var playerToDelete by remember { mutableStateOf<Player?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri = tempImageUri
            showAddDialog = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            try {
                val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempImageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyiapkan media penyimpanan.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin kamera ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            try {
                val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempImageUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyiapkan file foto.", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(user) {
        if (user == null) {
            navController.navigate("login") { popUpTo(0) { inclusive = true } }
        } else if (navController.currentDestination?.route == "login") {
            navController.navigate("list") { popUpTo("login") { inclusive = true } }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = if (user == null) "login" else "list") {
            composable("login") {
                LoginScreen(onLogin = { email -> viewModel.login(email) })
            }
            composable("list") {
                PlayerListScreen(
                    players = players,
                    onAddClick = { launchCamera() },
                    onProfileClick = { navController.navigate("profile") },
                    onDeleteClick = { playerToDelete = it },
                    onRefresh = { viewModel.fetchPlayers() }
                )
            }
            composable("profile") {
                ProfileScreen(
                    user = user,
                    onLogout = { viewModel.logout() },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (showAddDialog) {
            AddPlayerDialog(
                initialImageUri = capturedImageUri,
                onDismiss = { showAddDialog = false; capturedImageUri = null },
                onConfirm = { name, club, imageUrl -> viewModel.addPlayer(name, club, imageUrl) }
            )
        }

        playerToDelete?.let { player ->
            DeleteConfirmationDialog(
                onDismiss = { playerToDelete = null },
                onConfirm = { viewModel.deletePlayer(player.id); playerToDelete = null },
                playerName = player.name
            )
        }
    }
}