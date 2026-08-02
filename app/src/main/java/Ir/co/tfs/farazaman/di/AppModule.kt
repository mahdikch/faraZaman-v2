package Ir.co.tfs.farazaman.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import Ir.co.tfs.farazaman.data.db.dao.UserDao
import Ir.co.tfs.farazaman.data.db.room.AppDatabase
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import Ir.co.tfs.farazaman.data.repository.AuthRepositoryImpl
import Ir.co.tfs.farazaman.domain.repository.AuthRepository
import Ir.co.tfs.farazaman.domain.usecase.LoginUseCase
import Ir.co.tfs.farazaman.util.TokenManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
//    @Provides
//    @Singleton
//    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
//        return Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
//            .build()
//    }
//
//    @Provides
//    @Singleton
//    fun provideUserDao(database: AppDatabase): UserDao {
//        return database.userDao()
//    }

    @Provides
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        tokenManager: TokenManager,
        oidcAuthManager: OidcAuthManager,
    ): AuthRepository {
        return AuthRepositoryImpl(context, tokenManager, oidcAuthManager)
    }

    @Provides
    fun provideLoginUseCase(repository: AuthRepository): LoginUseCase {
        return LoginUseCase(repository)
    }
}
