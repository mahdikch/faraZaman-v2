package Ir.co.tfs.farazaman.data.model.res

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String
)
