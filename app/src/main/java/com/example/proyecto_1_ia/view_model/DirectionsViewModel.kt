package com.example.proyecto_1_ia.view_model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DirectionsUiState(
    val currentDirection : String = "",         //Dirección activa actual
    val commandDetected : String = "",
    //Contadores
    val upCount : Int = 0,
    val downCount : Int = 0,
    val rightCount : Int = 0,
    val leftCount : Int = 0,
    //Booleanos para determinar el activado
    val isUpActive : Boolean = false,
    val isDownActive : Boolean = false,
    val isLeftActive : Boolean = false,
    val isRightActive : Boolean = false
)

class DirectionsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DirectionsUiState())
    val uiState : StateFlow<DirectionsUiState> = _uiState.asStateFlow()

    //Funciones del programa para manejo de comandos
    fun processCommand(command: String) {
        //Reseteamos los estados a falso
        val baseState = _uiState.value.copy(
            isUpActive = false,
            isDownActive = false,
            isLeftActive = false,
            isRightActive = false
        )

        when (command.uppercase()){
            "UP" -> {
                _uiState.value = baseState.copy(
                    currentDirection = "UP",
                    commandDetected = "UP",
                    upCount = baseState.upCount + 1,
                    isUpActive = true
                )
            }
            "DOWN" -> {
                _uiState.value = baseState.copy(
                    currentDirection = "DOWN",
                    commandDetected = "DOWN",
                    downCount = baseState.downCount + 1,
                    isDownActive = true
                )
            }
            "LEFT" -> {
                _uiState.value = baseState.copy(
                    currentDirection = "LEFT",
                    commandDetected = "LEFT",
                    leftCount = baseState.leftCount + 1,
                    isLeftActive = true
                )
            }
            "RIGHT" -> {
                _uiState.value = baseState.copy(
                    currentDirection = "RIGHT",
                    commandDetected = "RIGHT",
                    rightCount = baseState.rightCount + 1,
                    isRightActive = true
                )
            }

        }
    }
    //Función para procesar el comando por voz
    fun processVoiceCommand(command: String){
        processCommand(command)
    }

    //Función para el simulador como tal
    fun simulateCommand(command: String){
        processCommand(command)
    }

}



