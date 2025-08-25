package com.myprojects.scanwisp.ui.screens.router

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.myprojects.scanwisp.OnboardingCheckState
import com.myprojects.scanwisp.ui.navigation.Screen

@Composable
fun RouterScreen(
    navController: NavController,
    onboardingCheckState: OnboardingCheckState
) {
    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Screen.Router.route) { inclusive = true }
        }
    }

    LaunchedEffect(onboardingCheckState) {
        when (onboardingCheckState) {
            OnboardingCheckState.Completed -> navigate(Screen.Home.createRoute())
            OnboardingCheckState.NotCompleted -> navigate(Screen.Onboarding.route)
            OnboardingCheckState.Loading -> {
                // Do nothing, wait for the state to change
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}