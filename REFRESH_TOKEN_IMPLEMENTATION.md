# Refresh Token Implementation Guide

## Overview

This project now has a comprehensive refresh token implementation that automatically handles token expiration and renewal for all API calls. The system is built with the following components:

## Key Components

### 1. TokenManager (`util/TokenManager.kt`)
- **Purpose**: Centralized token storage and management
- **Features**:
  - Secure token storage using SharedPreferences
  - Token expiration checking with 5-minute buffer
  - Thread-safe token refresh operations
  - Authorization header generation
  - Token validation and clearing

### 2. AuthInterceptor (`util/AuthInterceptor.kt`)
- **Purpose**: Automatic token refresh for all HTTP requests
- **Features**:
  - Automatically adds Authorization headers to requests
  - Detects 401 Unauthorized responses
  - Performs token refresh automatically
  - Retries failed requests with new tokens
  - Thread-safe to prevent multiple simultaneous refresh attempts

### 3. Enhanced AuthService (`service/remote/AuthService.kt`)
- **New Endpoint**: `refreshToken()` method added
- **Purpose**: Handles both login and token refresh API calls

### 4. Updated AuthRepository (`data/repository/AuthRepositoryImpl.kt`)
- **New Methods**:
  - `refreshToken()`: Performs token refresh
  - `logout()`: Clears all tokens
- **Features**: Uses TokenManager for all token operations

## How It Works

### Automatic Token Refresh Flow

1. **API Request Made**: Any API call goes through the AuthInterceptor
2. **Token Check**: Interceptor adds valid Authorization header
3. **Response Handling**: 
   - If response is successful → continues normally
   - If response is 401 Unauthorized → triggers refresh flow
4. **Token Refresh**: 
   - Uses refresh token to get new access token
   - Updates stored tokens via TokenManager
   - Retries original request with new token
5. **Fallback**: If refresh fails, clears tokens and user needs to login

### Login Flow

1. User enters credentials
2. AuthRepository calls login API
3. Successful response triggers TokenManager.saveTokens()
4. Both access and refresh tokens are stored with expiration time
5. User proceeds to main application

### Token Expiration Handling

- Tokens are checked for expiration before API calls
- 5-minute buffer prevents last-minute expirations
- Expired tokens trigger automatic refresh
- If refresh token is also expired, user is redirected to login

## Usage Examples

### Making Authenticated API Calls

```kotlin
// No changes needed! The AuthInterceptor handles everything automatically
class SomeRepository @Inject constructor(
    private val apiService: SomeApiService
) {
    suspend fun getData(): Response<SomeData> {
        // AuthInterceptor automatically adds headers and handles token refresh
        return apiService.getData()
    }
}
```

### Manual Token Operations

```kotlin
class SomeActivity @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) {
    
    fun checkAuthStatus() {
        if (tokenManager.isLoggedIn()) {
            if (tokenManager.isTokenExpired()) {
                // Token will be automatically refreshed on next API call
                // Or manually refresh:
                // authRepository.refreshToken()
            }
            // Proceed with authenticated operations
        } else {
            // Redirect to login
        }
    }
    
    fun logout() {
        authRepository.logout() // Clears all tokens
        // Redirect to login screen
    }
}
```

### Custom API Services

For new API services, simply inject them through Hilt and they'll automatically get the AuthInterceptor:

```kotlin
interface MyApiService {
    @GET("my-endpoint")
    suspend fun getMyData(): Response<MyData>
}

// In NetworkModule.kt
@Provides
fun provideMyApiService(retrofit: Retrofit): MyApiService {
    return retrofit.create(MyApiService::class.java)
}
```

## Integration Guide

### For Existing Activities/Services

1. **Remove manual token handling**: Delete code that manually gets ACCESS_TOKEN from SharedPreferences
2. **Inject TokenManager**: Use Hilt to inject TokenManager where needed
3. **Update auth checks**: Use `tokenManager.isLoggedIn()` instead of manual token checks

### Example Migration

**Before:**
```kotlin
val prefs = PreferenceManager.getDefaultSharedPreferences(this)
val token = prefs.getString("ACCESS_TOKEN", null)
if (token != null) {
    // Make API call with manual header
    apiService.getData("Bearer $token")
}
```

**After:**
```kotlin
@Inject lateinit var tokenManager: TokenManager

// Simply make the API call - everything is handled automatically
val response = apiService.getData()
```

## Error Handling

### Network Errors
- Connection timeouts are handled by OkHttp configuration
- 401 errors trigger automatic token refresh
- Other HTTP errors are passed through normally

### Token Refresh Failures
- Invalid refresh tokens clear all stored tokens
- Network failures during refresh are logged and tokens are cleared
- Users are redirected to login screen when tokens can't be refreshed

### Thread Safety
- All token operations are thread-safe using Mutex
- Multiple simultaneous refresh attempts are prevented
- Token state is consistent across the application

## Security Considerations

### Token Storage
- Tokens are stored in private SharedPreferences
- No tokens are logged in production builds
- Tokens are cleared on logout or refresh failure

### Network Security
- All API calls use HTTPS
- Client credentials are securely encoded
- Refresh tokens have limited lifetime

### Best Practices
- Always use TokenManager instead of direct SharedPreferences access
- Don't store tokens in memory longer than necessary
- Clear tokens on security-sensitive operations

## Testing

### Manual Testing Steps

1. **Login Flow**:
   - Login with valid credentials
   - Verify tokens are saved
   - Check API calls work correctly

2. **Token Refresh**:
   - Wait for token to expire (or manually set expired time)
   - Make API call
   - Verify automatic refresh occurs
   - Check new tokens are saved

3. **Error Scenarios**:
   - Login with invalid credentials
   - Make API call with manually corrupted token
   - Verify appropriate error handling

4. **Logout**:
   - Call logout functionality
   - Verify tokens are cleared
   - Check user is redirected to login

### Automated Testing

Consider adding unit tests for:
- TokenManager token validation logic
- AuthInterceptor refresh flow
- AuthRepository login/refresh methods

## Configuration

### Client Credentials
Update client ID and secret in AuthRepositoryImpl if needed:
```kotlin
val clientId = "your-client-id"
val clientSecret = "your-client-secret"
```

### Token Expiration Buffer
Adjust the expiration buffer in TokenManager:
```kotlin
val bufferTime = 5 * 60 * 1000L // 5 minutes in milliseconds
```

### Network Timeouts
Update timeouts in NetworkModule if needed:
```kotlin
.connectTimeout(180, TimeUnit.SECONDS)
.readTimeout(180, TimeUnit.SECONDS)
.writeTimeout(180, TimeUnit.SECONDS)
```

## Troubleshooting

### Common Issues

1. **"No refresh token available"**
   - User needs to login again
   - Check if logout was called

2. **"Token refresh failed"**
   - Check network connectivity
   - Verify server refresh endpoint is working
   - Check client credentials

3. **Infinite refresh loops**
   - Verify AuthInterceptor correctly identifies auth endpoints
   - Check that refresh endpoint doesn't trigger another refresh

### Debug Logging

Enable detailed logging by checking LogCat for these tags:
- `TokenManager`: Token operations and expiration checks
- `AuthInterceptor`: HTTP request/response and refresh operations
- `AuthRepository`: Login and refresh API calls
- `NETWORK_REQUEST`/`NETWORK_RESPONSE`: Detailed HTTP logs

## Future Enhancements

Potential improvements to consider:

1. **Biometric Authentication**: Add fingerprint/face unlock for token access
2. **Token Encryption**: Encrypt tokens in SharedPreferences
3. **Background Refresh**: Proactively refresh tokens before expiration
4. **Retry Logic**: Add exponential backoff for failed refresh attempts
5. **Analytics**: Track token refresh success/failure rates

## Migration Checklist

- [ ] Remove manual ACCESS_TOKEN references from activities
- [ ] Update authentication checks to use TokenManager
- [ ] Test login flow with new token management
- [ ] Test API calls with automatic token refresh
- [ ] Verify logout clears all tokens
- [ ] Update any custom API services to use dependency injection
- [ ] Test error scenarios (expired tokens, network failures)
- [ ] Update documentation for team members

## Support

For questions about this implementation:
1. Check this documentation first
2. Review the source code comments
3. Test with debug logging enabled
4. Check existing similar implementations in the codebase
