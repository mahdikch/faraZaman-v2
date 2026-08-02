package Ir.co.tfs.farazaman.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import Ir.co.tfs.farazaman.util.TokenManager
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    // UI State for navigation
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Loading)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    init {
        checkAuthenticationAndNavigate()
    }

    /**
     * Check user authentication status and navigate accordingly
     */
    private fun checkAuthenticationAndNavigate() {
        viewModelScope.launch {
            // Simulate splash screen delay
            delay(2000)
            
            // Check if user is logged in
            if (tokenManager.isLoggedIn()) {
                // Check if token is expired
                if (tokenManager.isTokenExpired()) {
                    // Token is expired, but we have refresh token - let AuthInterceptor handle it
                    // User can proceed to main app, automatic refresh will happen on first API call
                    _navigationState.value = NavigationState.NavigateToMain
                } else {
                    // Valid token, proceed to main app
                    _navigationState.value = NavigationState.NavigateToMain
                }
            } else {
                // No tokens, go to login
                _navigationState.value = NavigationState.NavigateToLogin
            }
        }
    }

    /**
     * Force navigate to login (used when refresh token is invalid)
     */
    fun navigateToLogin() {
        _navigationState.value = NavigationState.NavigateToLogin
    }

    /**
     * Force navigate to main app
     */
    fun navigateToMain() {
        _navigationState.value = NavigationState.NavigateToMain
    }
}

// Navigation states
sealed class NavigationState {
    object Loading : NavigationState()
    object NavigateToLogin : NavigationState()
    object NavigateToMain : NavigationState()
}

