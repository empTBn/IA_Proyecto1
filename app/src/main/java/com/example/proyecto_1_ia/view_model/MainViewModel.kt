package com.example.proyecto_1_ia.view_model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyecto_1_ia.audio.AudioRecorder
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
    val isListening: Boolean = false,
    val isDarkMode: Boolean = false,    //Falso significa LightMode (ON), Verdadero significa DarkMode (OFF)
    val isModelLoaded : Boolean = false,
    val audioLevel: Float = 0f          //Para mostrar el nivel de indicador de audio
)

class MainViewModel(application: Application): AndroidViewModel(application) {
    //Agarramos una forma más sencilla para tener acceso al estado de la app
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val audioRecorder = AudioRecorder(application)

    //Al inicio de la aplicación
    init {
        //Iniciamos el capturador de sonido
        audioRecorder.setOnAudioCapturedListener { audioData ->
            // TODO: When ONNX model is ready, process audioData here
            // val features = featureExtractor.audioToMelSpectrogram(audioData)
            // val result = onnxEngine.predict(features)
            // if (result != null) onCommandDetected(result.first, currentScreen)
        }
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
    }

    //Apagar reconocimiento de voz
    fun stopVoiceRecognition(){
        _appState.value = _appState.value.copy(isListening = false)
        audioRecorder.stopContinuousRecording()
    }

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

    //Función para indicar el cambio de pestaña
    fun setCurrentScreen(screen: Screen) {
        _appState.value = _appState.value.copy(currentScreen = screen)
    }

    //Función para activar / desactivar el modo oscuro a claro
    fun setDarkMode(isDark: Boolean){
        _appState.value = _appState.value.copy(isDarkMode = isDark)
    }

    //Reemplazamos la función de limpieza para liberar el grabador de audio
    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
    }
}