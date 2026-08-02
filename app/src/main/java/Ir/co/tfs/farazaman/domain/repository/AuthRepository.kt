package Ir.co.tfs.farazaman.domain.repository

import Ir.co.tfs.farazaman.data.model.res.LoginResponse

interface AuthRepository {
    suspend fun login(username: String, password: String): LoginResponse
    suspend fun refreshToken(): LoginResponse
    fun logout()
} 
