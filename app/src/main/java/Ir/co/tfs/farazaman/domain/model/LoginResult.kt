package Ir.co.tfs.farazaman.domain.model

import Ir.co.tfs.farazaman.data.model.res.LoginResponse

sealed class LoginResult {
    data class Success(val response: LoginResponse) : LoginResult()
    data class Error(val message: String) : LoginResult()
} 
