package com.example.proyecto_1_ia.view_model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.timer

data class StopGoUiState(
    val isMoving : Boolean = false,     //Determina el efecto de (STOP/GO)
    val commandDetected: String = "",   //Cuando se detecta un comando se guarda (YES/NO)
    val statusText: String = "",      //Variable visual para determinar si el vehículo está detenido o moviéndose
    var totalRunTime: Long = 0L,        //Variable que determina el tiempo total de ejecución que el carro ha corrido
    var currentGoDuration: Long = 0L,   //Variable que determina el tiempo actual de movimiento desde que se dio GO por última vez
    var currentStopDuration: Long = 0L, //Variable que determina el tiempo en el que se ha estado en estado STOP desde la última vez
    var showWarning: Boolean = false,   //Booleano para checar si ya está en ese estado (GO/STOP)
    val warningMessage: String = ""     //Mensaje mostrado en caso de presionarse el efecto una vez ya está en ese
)

class StopGoViewModel : ViewModel(){
    //Asignamos la caja de estados
    private val _uiState = MutableStateFlow(StopGoUiState())
    val uiState : StateFlow<StopGoUiState> = _uiState.asStateFlow()

    //Creamos los timers
    private var timerJob: Job? = null
    private var warningJob: Job? = null

    //Asignamos la función a ejecutarse una vez este viewModel es activado
    init{
        startTimer()
    }

    //Función ejecutada al inicio de ejecución de ésta pantalla
    private fun startTimer(){
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            //Ahora, le damos funcionamiento al timer
            while(true){
                delay(1000) //Esto se actualizará cada segundo
                //Asignamos una variable temporal para tener acceso a los tiempos
                val currentState = _uiState.value

                //Checamos si el movimiento se está dando, si es así incrementamos los timers de tiempo transcurrido de ejecución
                if (currentState.isMoving){
                    _uiState.value = _uiState.value.copy(
                        totalRunTime = currentState.totalRunTime + 1,
                        currentGoDuration = currentState.currentGoDuration + 1
                    )
                } else {
                    //Si el movimiento no es dado, entonces incrementamos el tiempo transcurrido desde que se detuvo
                    _uiState.value = _uiState.value.copy(
                        currentStopDuration = currentState.currentStopDuration + 1
                    )
                }
            }
        }
    }

    //TODO //=============: Función para la interferencia de ONNX =============//
    //Esta función se llamará por el ONNX una vez esté implementado la conexión con el modelo
    fun processCommand(command: String){
        when (command.uppercase()){
            "GO" -> {
                //Usamos un chequeo para determinar la alarma si ya está moviéndose y mostramos una advertencia
                if (_uiState.value.isMoving){
                    showTemporaryWarning("El vehículo ya está en marcha")
                } else {
                    //Si no se encontraba en marcha, lo actualizamos de una vez
                    _uiState.value = _uiState.value.copy(
                        isMoving = true,
                        commandDetected = "GO",
                        statusText = "El vehículo se puso en marcha",
                        currentGoDuration = 0L, //Reseteamos ambos tiempo de marcha actual y de detenerse
                        currentStopDuration = 0L,
                        showWarning = false,
                        warningMessage = ""
                    )
                    cancelWarning()
                }
            }
            "STOP" -> {
                //Usamos un chequeo para determinar si estamos detenidos (no isMoving)
                if (!_uiState.value.isMoving){
                    showTemporaryWarning("El vehículo ya está detenido")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isMoving = false,
                        commandDetected = "STOP",
                        statusText = "El vehículo se ha detenido",
                        currentGoDuration = 0L, //Reseteamos ambos tiempo de marcha actual y de detenerse
                        currentStopDuration = 0L,
                        showWarning = false,
                        warningMessage = ""
                    )
                    cancelWarning()
                }
            }
        }
    }


    //Funciones auxiliar para determinar un caso de un posible error
    fun showTemporaryWarning(warning: String){
        //Cancelamos cualquier advertencia previa
        cancelWarning()

        _uiState.value = _uiState.value.copy(
            showWarning = true,
            warningMessage = warning
        )

        //Activamos un modo que después de los 2 segundos desaparezca
        warningJob = viewModelScope.launch {
            delay(2000) //Se detenga luego de los 2 segundos
            _uiState.value = _uiState.value.copy(
                showWarning = false,
                warningMessage = ""
            )
        }
    }
    fun cancelWarning(){
        warningJob?.cancel()
    }


    //TODO: Función temporal para simular el comando
    fun simulateCommand(command: String){
        processCommand(command)
    }

    //Función de limpieza para la pantalla
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        warningJob?.cancel()
    }
}
