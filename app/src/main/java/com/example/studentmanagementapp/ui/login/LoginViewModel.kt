package com.example.studentmanagementapp.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentmanagementapp.data.remote.AuthApi
import com.example.studentmanagementapp.data.remote.TokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    fun onUsernameChange(value: String) {
        username = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun login(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = authApi.login(
                    username = username.trim(),
                    password = password
                )
                tokenStorage.saveToken(result.access_token)
                password = ""
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorMessage = when {
                    e is HttpException && e.code() == 401 ->
                        "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง"
                    e is IOException ->
                        "เชื่อมต่อ Server ไม่ได้ กรุณาตรวจสอบว่า Server เปิดอยู่"
                    else ->
                        "เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจสอบข้อมูล"
                }
            } finally {
                isLoading = false
            }
        }
    }
}