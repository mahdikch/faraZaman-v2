package Ir.co.tfs.farazaman.presentation.login

import Ir.co.tfs.farazaman.data.model.res.LoginResponse

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val response: LoginResponse) : LoginState()
    data class Error(val message: String) : LoginState()
}
