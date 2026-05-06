package com.example.proyecto_1_ia.view_model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

//Nuestras librerías propias
import com.example.proyecto_1_ia.audio.AudioRecorder
import com.example.proyecto_1_ia.voice.VoiceCommandManager

enum class ScreenEnum {
    YES_NO,
    ON_OFF,
    STOP_GO,
    DIRECTIONS
}

data class AppState(
    val currentScreen: ScreenEnum = ScreenEnum.YES_NO,
    val lastDetectedCommand: String = "",
    val isListening: Boolean = false,
    val isDarkMode: Boolean = false,    //Falso significa LightMode (ON), Verdadero significa DarkMode (OFF)
    val isModelLoaded : Boolean = false,
    //val audioLevel: Float = 0f,         //Para mostrar el nivel de indicador de audio
    val confidence: Float = 0f          //Confianza o nivel de similitud del último comando
)

class MainViewModel(application: Application): AndroidViewModel(application) {
    //Creamos un companion object para poner el TAG y manejar los LOGS
    companion object {
        private const val TAG = "MainViewModel"
    }

    //Agarramos una forma más sencilla para tener acceso al estado de la app
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val audioRecorder = AudioRecorder(application)
    private var voiceCommandManager : VoiceCommandManager? = null

    //Al inicio de la aplicación
    init {
        //Inicializamos el detector de comandos por voz
        voiceCommandManager = VoiceCommandManager(application) { command, confidence ->
            Log.d(TAG, "Comando detectado: $command (confianza: $confidence)")
            _appState.value = _appState.value.copy(
                confidence = confidence
            )
            onCommandDetected(command, _appState.value.currentScreen)
        }

        //Iniciamos el capturador de sonido
        audioRecorder.setOnAudioCapturedListener { audioData ->
            // TODO: When ONNX model is ready, process audioData here
            //Una vez se captura audio, se procesa por el detector de comandos
            voiceCommandManager?.processAudio(audioData)
        }

        //Seteamos el sistema a que el modelo fue cargado
        _appState.value = _appState.value.copy(isModelLoaded = true)
    }

    //Función para apagar/encender grabación
    fun toggleVoiceRecognition(){
        if (_appState.value.isListening){
            stopVoiceRecognition()
        } else {
            startVoiceRecognition()
        }
    }

    //Iniciar reconocimiento de voz
    fun startVoiceRecognition(){
        if (!audioRecorder.hasPermission()){
            //El permiso será manejado por el UI
            return
        }

        _appState.value = _appState.value.copy(isListening = true)
        audioRecorder.startContinuousRecording(viewModelScope)
        Log.d(TAG, "Voice recognition started")
    }

    //Apagar reconocimiento de voz
    fun stopVoiceRecognition(){
        _appState.value = _appState.value.copy(isListening = false)
        audioRecorder.stopContinuousRecording()
        Log.d(TAG, "Voice recognition stopped")
    }

    //Esto será utilizado en todas las pantallas, algo para detectar el comando y enviar señal
    fun onCommandDetected(command: String, currentScreen: ScreenEnum){
        _appState.value = _appState.value.copy(
            lastDetectedCommand = command,
            currentScreen = currentScreen
        )

        //Mandamos el mensaje de enrutamiento a la página correcta
        Log.d(TAG, "Comando cambió a la pantalla: $currentScreen -> $command")
    }

    //Función para indicar el cambio de pestaña
    fun setCurrentScreen(screen: ScreenEnum) {
        _appState.value = _appState.value.copy(currentScreen = screen)
    }

    //Función para activar / desactivar el modo oscuro a claro
    fun setDarkMode(isDark: Boolean){
        _appState.value = _appState.value.copy(isDarkMode = isDark)
    }

    //Función para asignar al detector para ver si se está escuchando o no
    fun setListening(isListening: Boolean){
        _appState.value = _appState.value.copy(isListening = isListening)
    }


    //Reemplazamos la función de limpieza para liberar el grabador de audio
    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        voiceCommandManager?.close()
    }
}