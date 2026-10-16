package com.example.studentmanagementapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.studentmanagementapp.data.remote.TokenStorage
import com.example.studentmanagementapp.ui.StudentScreen
import com.example.studentmanagementapp.ui.login.LoginScreen
import com.example.studentmanagementapp.ui.theme.StudentManagementAppTheme
import com.example.studentmanagementapp.worker.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val tokenStorage by lazy { TokenStorage(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudentManagementAppTheme {
                // เปิดแอป: มี Token -> StudentScreen / ไม่มี -> LoginScreen
                var isLoggedIn by remember { mutableStateOf(tokenStorage.getToken() != null) }
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        SyncScheduler.schedulePeriodicSync(applicationContext)
                    } else {
                        SyncScheduler.cancelPeriodicSync(applicationContext)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isLoggedIn) {
                        StudentScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLogout = {
                                tokenStorage.clearToken()
                                isLoggedIn = false
                            }
                        )
                    } else {
                        LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StudentScreenPreview() {
    StudentManagementAppTheme {
        StudentScreen()
    }
}