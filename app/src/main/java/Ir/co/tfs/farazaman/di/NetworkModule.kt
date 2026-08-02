package Ir.co.tfs.farazaman.di

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import Ir.co.tfs.farazaman.AppConstants
import Ir.co.tfs.farazaman.data.repository.FormDataRepository
import Ir.co.tfs.farazaman.service.remote.AuthService
import Ir.co.tfs.farazaman.data.api.FormDataApiService
import Ir.co.tfs.farazaman.service.remote.RoadService
import Ir.co.tfs.farazaman.service.remote.ViolationApiService
import Ir.co.tfs.farazaman.service.remote.UserService
import Ir.co.tfs.farazaman.service.remote.RolesService
import Ir.co.tfs.farazaman.service.remote.VehicleZoneWorkService
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import Ir.co.tfs.farazaman.service.remote.ApiService
import Ir.co.tfs.farazaman.auth.OidcAuthManager
import Ir.co.tfs.farazaman.util.AuthInterceptor
import Ir.co.tfs.farazaman.util.AuthStateManager
import Ir.co.tfs.farazaman.util.TokenManager


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideCustomLoggingInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            
            Log.d("NETWORK_REQUEST", "=== DETAILED REQUEST LOG ===")
            Log.d("NETWORK_REQUEST", "URL: ${request.url}")
            Log.d("NETWORK_REQUEST", "Method: ${request.method}")
            Log.d("NETWORK_REQUEST", "Headers:")
            request.headers.forEach { (name, value) ->
                if (name.equals("Authorization", ignoreCase = true)) {
                    Log.d("NETWORK_REQUEST", "  $name: ${value.take(20)}...")
                } else {
                    Log.d("NETWORK_REQUEST", "  $name: $value")
                }
            }
            
            val requestBody = request.body
            if (requestBody != null) {
                Log.d("NETWORK_REQUEST", "Request Body Type: ${requestBody.contentType()}")
                Log.d("NETWORK_REQUEST", "Request Body Length: ${requestBody.contentLength()}")
            }
            Log.d("NETWORK_REQUEST", "=== END REQUEST LOG ===")
            
            val response = chain.proceed(request)
            
            Log.d("NETWORK_RESPONSE", "=== DETAILED RESPONSE LOG ===")
            Log.d("NETWORK_RESPONSE", "Response Code: ${response.code}")
            Log.d("NETWORK_RESPONSE", "Response Message: ${response.message}")
            Log.d("NETWORK_RESPONSE", "Response Headers:")
            response.headers.forEach { (name, value) ->
                Log.d("NETWORK_RESPONSE", "  $name: $value")
            }
            Log.d("NETWORK_RESPONSE", "=== END RESPONSE LOG ===")
            
            response
        }
    }
    
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideTokenManager(sharedPreferences: android.content.SharedPreferences): TokenManager {
        return TokenManager(sharedPreferences)
    }
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
    
    @Provides
    @Singleton
    fun provideUserManager(sharedPreferences: android.content.SharedPreferences): Ir.co.tfs.farazaman.util.UserManager {
        return Ir.co.tfs.farazaman.util.UserManager(sharedPreferences)
    }
    
    @Provides
    @Singleton
    fun provideRolesManager(sharedPreferences: android.content.SharedPreferences, gson: Gson): Ir.co.tfs.farazaman.util.RolesManager {
        return Ir.co.tfs.farazaman.util.RolesManager(sharedPreferences, gson)
    }
    
    @Provides
    @Singleton
    fun provideAuthStateManager(@ApplicationContext context: Context): AuthStateManager {
        return AuthStateManager(context)
    }
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager,
        @ApplicationContext context: Context,
        authStateManager: AuthStateManager,
        authRepository: Ir.co.tfs.farazaman.domain.repository.AuthRepository,
    ): AuthInterceptor {
        return AuthInterceptor(tokenManager, context, authStateManager, authRepository)
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        customLoggingInterceptor: Interceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor) // Add auth interceptor first
            .addInterceptor(customLoggingInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
    
    @Provides
    fun provideRetrofit(okHttpClient: OkHttpClient,@ApplicationContext context: Context): Retrofit {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!
        Log.d("NetworkModule", "=== provideRetrofit called ===")
        Log.d("NetworkModule", "Reading base URL from preferences: $baseUrl")
        Log.d("NetworkModule", "Current timestamp: ${System.currentTimeMillis()}")
        Log.d("NetworkModule", "Thread: ${Thread.currentThread().name}")
        
        // Force recreation by always creating a new instance
        Log.d("NetworkModule", "Forcing new Retrofit creation for base URL: $baseUrl")
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        Log.d("NetworkModule", "Retrofit created with base URL: ${retrofit.baseUrl()}")
        Log.d("NetworkModule", "Retrofit instance hash: ${retrofit.hashCode()}")
        Log.d("NetworkModule", "=== End provideRetrofit ===")
        return retrofit

    }
    
    @Provides
    fun provideFormDataApiService(retrofit: Retrofit): FormDataApiService {
        return retrofit.create(FormDataApiService::class.java)
    }
    
    @Provides
    fun provideFormDataRepository(
        apiService: FormDataApiService,
        @ApplicationContext context: Context,
        sharedPreferences: android.content.SharedPreferences,
        tokenManager: TokenManager
    ): FormDataRepository {
        return FormDataRepository(apiService, context, sharedPreferences, tokenManager)
    }

    @Provides
    fun provideAuthService(retrofit: Retrofit, @ApplicationContext context: Context): AuthService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentBaseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!
        
        Log.d("NetworkModule", "=== provideAuthService called ===")
        Log.d("NetworkModule", "Current base URL from preferences: $currentBaseUrl")
        Log.d("NetworkModule", "Retrofit base URL: ${retrofit.baseUrl()}")
        Log.d("NetworkModule", "AuthService creation timestamp: ${System.currentTimeMillis()}")
        
        // Create a new Retrofit instance with the current base URL
        val newRetrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(retrofit.callFactory() as okhttp3.OkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val authService = newRetrofit.create(AuthService::class.java)
        Log.d("NetworkModule", "AuthService created successfully")
        Log.d("NetworkModule", "AuthService instance hash: ${authService.hashCode()}")
        Log.d("NetworkModule", "=== End provideAuthService ===")
        return authService
    }

    @Provides
    fun provideRoadService(retrofit: Retrofit): RoadService {
        return retrofit.create(RoadService::class.java)
    }

    @Provides
    fun provideViolationApiService(retrofit: Retrofit): ViolationApiService {
        return retrofit.create(ViolationApiService::class.java)
    }

    @Provides
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
    
    @Provides
    fun provideUserService(retrofit: Retrofit, @ApplicationContext context: Context): UserService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentBaseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!
        
        Log.d("NetworkModule", "=== provideUserService called ===")
        Log.d("NetworkModule", "Current base URL from preferences: $currentBaseUrl")
        Log.d("NetworkModule", "Retrofit base URL: ${retrofit.baseUrl()}")
        Log.d("NetworkModule", "UserService creation timestamp: ${System.currentTimeMillis()}")
        
        // Create a new Retrofit instance with the current base URL
        val newRetrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(retrofit.callFactory() as okhttp3.OkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val userService = newRetrofit.create(UserService::class.java)
        Log.d("NetworkModule", "UserService created successfully")
        Log.d("NetworkModule", "UserService instance hash: ${userService.hashCode()}")
        Log.d("NetworkModule", "=== End provideUserService ===")
        return userService
    }
    
    @Provides
    fun provideRolesService(retrofit: Retrofit, @ApplicationContext context: Context): RolesService {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentBaseUrl = prefs.getString("BASE_URL", "https://app.tfs.co.ir/")!!
        
        Log.d("NetworkModule", "=== provideRolesService called ===")
        Log.d("NetworkModule", "Current base URL from preferences: $currentBaseUrl")
        
        // Create a new Retrofit instance with the current base URL
        val newRetrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(retrofit.callFactory() as okhttp3.OkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val rolesService = newRetrofit.create(RolesService::class.java)
        Log.d("NetworkModule", "RolesService created successfully")
        Log.d("NetworkModule", "=== End provideRolesService ===")
        return rolesService
    }
    
    @Provides
    fun provideVehicleZoneWorkService(retrofit: Retrofit): VehicleZoneWorkService {
        return retrofit.create(VehicleZoneWorkService::class.java)
    }

    object RetrofitProvider {
        @Volatile
        private var retrofit: Retrofit? = null

        fun getRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit {
            Log.d("RetrofitProvider", "=== getRetrofit called ===")
            Log.d("RetrofitProvider", "Requested base URL: $baseUrl")
            Log.d("RetrofitProvider", "Current retrofit is null: ${retrofit == null}")
            if (retrofit != null) {
                Log.d("RetrofitProvider", "Current retrofit base URL: ${retrofit?.baseUrl()}")
                Log.d("RetrofitProvider", "Base URLs match: ${retrofit?.baseUrl().toString() == baseUrl}")
            }
            
            if (retrofit == null || retrofit?.baseUrl().toString() != baseUrl) {
                Log.d("RetrofitProvider", "Creating new Retrofit instance with base URL: $baseUrl")
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                Log.d("RetrofitProvider", "New Retrofit instance created")
            } else {
                Log.d("RetrofitProvider", "Reusing existing Retrofit instance")
            }
            Log.d("RetrofitProvider", "Returning Retrofit with base URL: ${retrofit?.baseUrl()}")
            Log.d("RetrofitProvider", "=== End getRetrofit ===")
            return retrofit!!
        }

        fun reset() {
            Log.d("RetrofitProvider", "Resetting Retrofit instance")
            retrofit = null
        }
    }
}
