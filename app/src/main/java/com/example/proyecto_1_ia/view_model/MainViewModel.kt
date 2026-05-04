package com.example.proyecto_1_ia.view_model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Screen {
    YES_NO,
    ON_OFF,
    STOP_GO,
    DIRECTIONS
}

data class AppState(
    val currentScreen: Screen = Screen.YES_NO,
    val lastDetectedCommand: String = "",
    val isListening: Boolean = false
)

class MainViewModel : ViewModel() {
    //Agarramos una forma más sencilla para tener acceso al estado de la app
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    //Esto será utilizado en todas las pantallas, algo para detectar el comando y enviar señal
    fun onCommandDetected(command: String, currentScreen: Screen){
        _appState.value = _appState.value.copy(
            lastDetectedCommand = command,
            currentScreen = currentScreen
        )
    }

    //Función para asignar al detector para ver si se está escuchando o no
    fun setListening(isListening: Boolean){
        _appState.value = _appState.value.copy(isListening = isListening)
    }
}