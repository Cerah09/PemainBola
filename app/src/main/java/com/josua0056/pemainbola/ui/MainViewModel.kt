package com.josua0056.pemainbola.ui

package com.josua0056.namapemainbola.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josua0056.namapemainbola.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)
    private val apiKey = "AIzaSyDARbKrxRG0Q_HPjzsTny2F4M4SpUxYMsU"

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
        val app = getApplication<Application>()
        val connectivityManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val activeNetwork = connectivityManager.getNetworkCapabilities(network)
        val isConnected = activeNetwork != null && (activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))

        if (!isConnected) {
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
                        name = pet.name ?: "Unknown",
                        club = pet.description ?: "No Club",
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
                _error.value = "Gagal mengambil data: ${e.message}"
                if (_players.value.isEmpty()) {
                    _players.value = getSamplePlayers(currentUser.id)
                }
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
                imageUrl = "https://images2.minutemediacdn.com/image/upload/c_fill,w_720,ar_16:9,f_auto,q_auto,g_auto/images/mmsport/90min_en_international_web/01hmv8v1v88k5v7v7v7v.jpg",
                userId = userId
            ),
            Player(
                id = "sample2",
                name = "Lionel Messi",
                club = "Barcelona",
                imageUrl = "https://upload.wikimedia.org/wikipedia/commons/b/b4/Lionel-Messi-Argentina-2022-FIFA-World-Cup_%28cropped%29.jpg",
                userId = userId
            )
        )
    }

    fun addPlayer(name: String, club: String, imageUriString: String) {
        val currentUser = _user.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val app = getApplication<Application>()
                val uri = Uri.parse(imageUriString)
                val inputStream: InputStream? = app.contentResolver.openInputStream(uri)
                val file = File.createTempFile("upload_player_", ".jpg", app.cacheDir)
                val outputStream = FileOutputStream(file)
                inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }

                if (file.exists()) {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val imagePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    val nameBody = name.toRequestBody("text/plain".toMediaTypeOrNull())
                    val clubBody = club.toRequestBody("text/plain".toMediaTypeOrNull())
                    val userIdBody = currentUser.id.toRequestBody("text/plain".toMediaTypeOrNull())

                    apiService.addPlayer(apiKey, nameBody, clubBody, userIdBody, imagePart)
                    fetchPlayers()
                } else {
                    _error.value = "File gambar tidak ditemukan atau gagal diproses"
                }
            } catch (e: Exception) {
                _error.value = "Gagal menambah data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlayer(playerId: String) {
        if (playerId.startsWith("sample")) {
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
}