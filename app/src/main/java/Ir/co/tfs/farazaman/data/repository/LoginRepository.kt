package Ir.co.tfs.farazaman.data.repository

import Ir.co.tfs.farazaman.data.api.LoginApi
import Ir.co.tfs.farazaman.data.db.dao.UserDao
import Ir.co.tfs.farazaman.data.db.model.UserEntity
import Ir.co.tfs.farazaman.data.model.req.LoginRequest
import Ir.co.tfs.farazaman.data.model.res.LoginResponse
//import javax.inject.Inject

class LoginRepository
//@Inject constructor(
//    private val loginApi: LoginApi,
//    private val userDao: UserDao
//)
{
//    suspend fun login(username: String, password: String): Result<LoginResponse> {
//        return try {
//            val response = loginApi.login(LoginRequest(username, password))
//            userDao.insertUser(UserEntity(response.userId, response.accessToken))
//            Result.success(response)
//        } catch (e: Exception) {
//            Result.failure(e)
//        }
//    }

//    suspend fun getUser(userId: Int): UserEntity? {
//        return userDao.getUser(userId)
//    }
}
