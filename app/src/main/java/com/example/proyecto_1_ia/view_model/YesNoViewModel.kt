package com.example.proyecto_1_ia.view_model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyecto_1_ia.utils.AnsweredQuestion
import com.example.proyecto_1_ia.utils.Question
import com.example.proyecto_1_ia.utils.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskItem(
    val id: Int,
    val text: String,
    val isCompleted: Boolean = false
)

data class YesNoUiState(
    val tasks: List<TaskItem> = emptyList(),    //Donde guardamos la lista de preguntas
    val currentTaskIndex: Int = 0,              //Valor de index de la tarea
    val commandDetected: String = ""            //Cuando se detecta un comando se guarda (YES/NO)
)

class YesNoViewModel (application: Application) : AndroidViewModel(application) {
    //Para manejar el estado mediante el sistema de flow y modificar los estados de la pantalla o el trabajo
    private val repository = QuestionRepository(application)
    private val _uiState = MutableStateFlow(YesNoUiState())
    val uiState: StateFlow<YesNoUiState> = _uiState.asStateFlow()

    //Creamos un array de los ID's de posibles preguntas, y uno para almacenar las preguntas contestadas
    private val askedQuestionIds = mutableListOf<Int>()
    private val answeredQuestions = mutableListOf<AnsweredQuestion>()

    //Iniciamos el ViewModel con este solicitando la siguiente pregunta
    init {
        loadTasks()
    }

    //Esta función se encarga de cargar todas las tareas al sistema
    fun loadTasks(){
        //Ahora, en caso de no haber terminado, tenemos que seleccionar la siguiente pregunta, ojalá excluyendo las previas preguntas
        val questions = repository.getAllQuestions()
        val tasks = questions.map { q ->
            TaskItem(id = q.id, text = q.text, isCompleted = false)
        }
        _uiState.value = _uiState.value.copy(tasks = tasks)
    }

    //TODO //=============: Función para la interferencia de ONNX =============//
    //Esta función se llamará por el ONNX una vez esté implementado la conexión con el modelo
    fun processCommand(command: String){
        val currentState = _uiState.value
        if (currentState.tasks.isEmpty()) return //Si la lista no cargó las tareas, tiramos un error

        when(command.uppercase()){
            "YES" -> {
                //Marcamos la tarea como si estuviera completa
                val updatedTasks = currentState.tasks.toMutableList() //Sacamos las tareas para poder modificar
                val index = currentState.currentTaskIndex

                //Checamos que esté en el campo indicado de tareas
                if (index < updatedTasks.size){
                    //Completamos la tarea en su index exacto
                    updatedTasks[index] = updatedTasks[index].copy(isCompleted = true)

                    //Actualizamos la caja de estados con el valor modificado
                    _uiState.value = currentState.copy(
                        tasks = updatedTasks,
                        commandDetected = "YES"
                    )
                }
            }
            "NO" -> {
                //Marcamos la tarea como incompleta
                val updatedTasks = currentState.tasks.toMutableList() //Sacamos las tareas para poder modificar
                val index = currentState.currentTaskIndex

                //Checamos que esté en el campo indicado de tareas
                if (index < updatedTasks.size){
                    //Completamos la tarea en su index exacto
                    updatedTasks[index] = updatedTasks[index].copy(isCompleted = false)

                    //Actualizamos la caja de estados con el valor modificado
                    _uiState.value = currentState.copy(
                        tasks = updatedTasks,
                        commandDetected = "NO"
                    )
                }
            }
            //Comandos para los movimientos
            "UP" -> {
                //Nos movemos hacia arriba, o en un array, hacia la tarea previa
                val newIndex = if (currentState.currentTaskIndex == 0) {
                    currentState.tasks.size - 1
                } else {
                    currentState.currentTaskIndex - 1
                }
                _uiState.value = currentState.copy(
                    currentTaskIndex = newIndex,
                    commandDetected = "UP"
                )
            }
            "DOWN" -> {
                //Nos movemos hacia abajo, o en un array, hacia la tarea siguiente
                val newIndex = if (currentState.currentTaskIndex == currentState.tasks.size - 1) {
                    0
                } else {
                    currentState.currentTaskIndex + 1
                }
                _uiState.value = currentState.copy(
                    currentTaskIndex = newIndex,
                    commandDetected = "DOWN"
                )
            }
        }
    }
    //Función para procesar el comando por voz
    fun processVoiceCommand(command: String){
        processCommand(command)
    }


    //Función para simular el posible caso para checar si las pruebas funcionan
    fun simulateCommand(command: String){
        processCommand(command)
    }

}