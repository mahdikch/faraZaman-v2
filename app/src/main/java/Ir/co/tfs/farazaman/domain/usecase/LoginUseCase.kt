package Ir.co.tfs.farazaman.domain.usecase

import Ir.co.tfs.farazaman.domain.model.LoginResult
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import android.util.Log
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): LoginResult {
        Log.d("LoginUseCase", "Login use case invoked for username: $username")
        return try {
            val response = authRepository.login(username, password)
            Log.d("LoginUseCase", "Login use case completed successfully")
            LoginResult.Success(response)
        } catch (e: Exception) {
            Log.e("LoginUseCase", "Login use case failed with exception: ${e.message}")
            LoginResult.Error(e.message ?: "Login failed")
        }
    }
}
