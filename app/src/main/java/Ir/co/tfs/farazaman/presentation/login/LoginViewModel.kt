package Ir.co.tfs.farazaman.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import Ir.co.tfs.farazaman.domain.model.LoginResult
import Ir.co.tfs.farazaman.domain.usecase.LoginUseCase
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(username: String, password: String) {
        Log.d("LoginViewModel", "Login method called for username: $username")
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                Log.d("LoginViewModel", "Executing login use case...")
                val result = loginUseCase(username, password)
                Log.d("LoginViewModel", "Login use case result: ${result.javaClass.simpleName}")
                
                _loginState.value = when (result) {
                    is LoginResult.Success -> {
                        Log.d("LoginViewModel", "Login successful - Setting success state")
                        LoginState.Success(result.response)
                    }
                    is LoginResult.Error -> {
                        Log.e("LoginViewModel", "Login failed with error: ${result.message}")
                        LoginState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Login exception occurred: ${e.message}")
                _loginState.value = LoginState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
