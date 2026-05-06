package com.example.proyecto_1_ia.view_model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnOffUiState(
    val isOn: Boolean = true,                       //ON = light-mode, OFF = dark-mode
    val commandDetected: String = "",               //Cuando se detecta un comando se guarda (ON/OFF)
    val statusText: String = "Modo Claro Activado"  //Texto que aparece para mostrar el estado
)

class OnOffViewModel : ViewModel(){
    private val _uiState = MutableStateFlow(OnOffUiState())
    val uiState : StateFlow<OnOffUiState> = _uiState.asStateFlow()

    //TODO //=============: Función para la interferencia de ONNX =============//
    //Esta función se llamará por el ONNX una vez esté implementado la conexión con el modelo
    fun processCommand(command: String){
        when (command.uppercase()){
            "ON" -> {
                //Activamos el modo claro
                _uiState.value = _uiState.value.copy(
                    isOn = true,
                    commandDetected = "ON",
                    statusText = "Modo Claro Activado"
                )
            }
            "OFF" -> {
                //Activamos el modo oscuro
                _uiState.value = _uiState.value.copy(
                    isOn = false,
                    commandDetected = "OFF",
                    statusText = "Modo Oscuro Activado"
                )
            }
        }
    }
    //Función para procesar el comando por voz
    fun processVoiceCommand(command: String){
        processCommand(command)
    }

    //Función utilizada para activar el modo oscuro en el MainModel
    fun syncWithMainViewModel(isDarkMode: Boolean){
        _uiState.value = _uiState.value.copy(
            isOn = !isDarkMode,
            statusText = if (!isDarkMode) "Modo Claro Activado" else "Modo Oscuro Activado"
        )
    }

    //Función temporal para simular el comando
    fun simulateCommand(command: String){
        processCommand(command)
    }
}
