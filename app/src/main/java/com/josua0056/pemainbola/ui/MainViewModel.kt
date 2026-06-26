package com.josua0056.pemainbola.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josua0056.pemainbola.data.ApiService
import com.josua0056.pemainbola.data.Player
import com.josua0056.pemainbola.data.User
import com.josua0056.pemainbola.data.UserPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Suppress("SpellCheckingInspection")
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)

    private val apiKey = "live_Wp9v68E7X1S8nNqR7p3mK9j6bXzV4L2tN8uW3qZ5vY7cE4rT1p"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiService = Retrofit.Builder()
        .baseUrl("https://api.thedogapi.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ApiService::class.java)

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.userFlow.collect {
                _user.value = it
                if (it != null) {
                    fetchPlayers()
                }
            }
        }
    }

    fun login(email: String) {
        viewModelScope.launch {
            val dummyUser = User(
                id = "josua_user_123",
                name = "Josua Natanael Panjaitan",
                email = email,
                photoUrl = "https://ui-avatars.com/api/?name=Josua+Natanael+Panjaitan&background=random"
            )
            userPreferences.saveUser(dummyUser)
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearUser()
            _players.value = emptyList()
        }
    }

    fun fetchPlayers() {
        val currentUser = _user.value ?: return
        if (!isInternetAvailable()) {
            _error.value = "Koneksi internet tidak tersedia"
            if (_players.value.isEmpty()) {
                _players.value = getSamplePlayers(currentUser.id)
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = apiService.getPlayers(apiKey, currentUser.id)
                val mappedPlayers = result.map { pet ->
                    Player(
                        id = pet.id,
                        name = pet.name ?: "Unknown Player",
                        club = pet.description ?: "No Club Info",
                        imageUrl = pet.url ?: pet.images?.firstOrNull()?.url ?: "",
                        userId = pet.subId ?: ""
                    )
                }

                if (mappedPlayers.isEmpty()) {
                    _players.value = getSamplePlayers(currentUser.id)
                } else {
                    _players.value = mappedPlayers
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Fetch failed, loading sample data", e)
                _players.value = getSamplePlayers(currentUser.id)
            } finally {
                _isLoading.value = false
            }
        }
    }
    private fun getSamplePlayers(userId: String): List<Player> {
        return listOf(
            Player(
                id = "sample1",
                name = "Bruno Fernandes",
                club = "Manchester United",
                imageUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&q=80",
                userId = userId
            ),
            Player(
                id = "sample2",
                name = "Lionel Messi",
                club = "Inter Miami",
                imageUrl = "https://images.unsplash.com/photo-1518063319789-7217e6706b04?w=500&q=80",
                userId = userId
            )
        )
    }

    fun addPlayer(name: String, club: String, imageUriString: String) {
        val currentUser = _user.value ?: return

        _isLoading.value = true

        viewModelScope.launch {
            try {
                if (imageUriString.isNotEmpty() && isInternetAvailable()) {
                    val uri = imageUriString.toUri()
                    val file = getFileFromUri(uri)

                    if (file != null && file.exists()) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val imagePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                        val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                        val clubBody = club.toRequestBody("text/plain".toMediaTypeOrNull())
                        val userIdBody = currentUser.id.toRequestBody("text/plain".toMediaTypeOrNull())

                        apiService.addPlayer(apiKey, nameBody, clubBody, userIdBody, imagePart)
                        fetchPlayers()
                        return@launch
                    }
                }

                val localDummyPlayer = Player(
                    id = "local_${System.currentTimeMillis()}",
                    name = name,
                    club = club,
                    imageUrl = "https://images.unsplash.com/photo-1544698310-74ea9d1c8258?w=500&q=80",
                    userId = currentUser.id
                )

                // SUDAH DIPERBAIKI: Menggunakan operator-assignment +=
                _players.value += localDummyPlayer

            } catch (e: Exception) {
                Log.e("MainViewModel", "API insert failed, saving to local instead", e)
                val localDummyPlayer = Player(
                    id = "local_${System.currentTimeMillis()}",
                    name = name,
                    club = club,
                    imageUrl = "https://images.unsplash.com/photo-1544698310-74ea9d1c8258?w=500&q=80",
                    userId = currentUser.id
                )
                // SUDAH DIPERBAIKI: Menggunakan operator-assignment +=
                _players.value += localDummyPlayer
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val app = getApplication<Application>()
            val inputStream: InputStream? = app.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("upload_player_", ".jpg", app.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("MainViewModel", "File creation failed", e)
            null
        }
    }

    fun deletePlayer(playerId: String) {
        if (playerId.startsWith("sample") || playerId.startsWith("local")) {
            _players.value = _players.value.filter { it.id != playerId }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                apiService.deletePlayer(apiKey, playerId)
                fetchPlayers()
            } catch (e: Exception) {
                _error.value = "Gagal menghapus data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun isInternetAvailable(): Boolean {
        val app = getApplication<Application>()
        val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}