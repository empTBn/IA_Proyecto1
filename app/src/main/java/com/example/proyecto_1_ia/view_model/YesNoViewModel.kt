package com.example.proyecto_1_ia.view_model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyecto_1_ia.data.AnsweredQuestion
import com.example.proyecto_1_ia.data.Question
import com.example.proyecto_1_ia.data.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YesNoUiState(
    val currentQuestion: Question? = null,
    val questionNumber: Int = 0,        //Número actual de preguntas hechas
    val totalQuestions: Int = 10,       //Valor a cambiar, pueden ser solo 5 preguntas para ser más rápido
    val isComplete: Boolean = false,    //Determinamos si se contestaron todas las necesarias
    val commandDetected: String = "",   //Cuando se detecta un comando se guarda (YES/NO)
    val answeredQuestions: List<AnsweredQuestion> = emptyList(), //Lista de preguntas contestadas
    val isListening : Boolean = false   //Para determinar si el objeto para escuchar o no está funcionando
)

class YesNoViewModel (application: Application) : AndroidViewModel(application) {
    private val repository = QuestionRepository(application)

    //Para manejar el estado mediante el sistema de flow y modificar los estados de la pantalla o el trabajo
    private val _uiState = MutableStateFlow(YesNoUiState())
    val uiState: StateFlow<YesNoUiState> = _uiState.asStateFlow()

    //Creamos un array de los ID's de posibles preguntas, y uno para almacenar las preguntas contestadas
    private val askedQuestionIds = mutableListOf<Int>()
    private val answeredQuestions = mutableListOf<AnsweredQuestion>()

    //Iniciamos el ViewModel con este solicitando la siguiente pregunta
    init {
        nextQuestion()
    }

    fun nextQuestion(){
        if (_uiState.value.questionNumber >= _uiState.value.totalQuestions){
            //Si el número de preguntas es mayor o igual al número total de preguntas esperadas, significa que ya terminó
            //y puede mostrar el resumen
            _uiState.value = uiState.value.copy(isComplete = true)
            return
        }

        //Ahora, en caso de no haber terminado, tenemos que seleccionar la siguiente pregunta, ojalá excluyendo las previas preguntas
        val question = repository.getRandomQuestion(excludeIds = askedQuestionIds)
        question?.let{
            //Al agarrar una pregunta al azar, tenemos que agregarla a la lista de preguntas ya hechas y aumentar el contador
            askedQuestionIds.add(it.id)
            _uiState.value = _uiState.value.copy(
                currentQuestion = it,
                questionNumber = _uiState.value.questionNumber + 1,
                commandDetected = ""
            )
        }
    }

    //TODO //=============: Función para la interferencia de ONNX =============//
    //Esta función se llamará por el ONNX una vez esté implementado la conexión con el modelo
    fun onCommandDetected(command: String){
        val currentQuestion = _uiState.value.currentQuestion ?: return

        if (command.equals("yes", ignoreCase = true) || command.equals("no", ignoreCase = true)){
            answeredQuestions.add(
                AnsweredQuestion(  //Guardamos el texto de la pregunta y su respuesta
                    question = currentQuestion.text,
                    answer = command.uppercase()
                )
            )

            _uiState.value = _uiState.value.copy(
                commandDetected = command.uppercase(),
                answeredQuestions = answeredQuestions.toList()
            )

            //Avanzamos a la siguiente pregunta de forma automática después de 1.5 segundos
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                nextQuestion()
            }
        }
    }

    //Función para simular el posible caso para checar si las pruebas funcionan
    fun simulateCommand(command: String){
        onCommandDetected(command)
    }

    //Función para resetear la lista de preguntas (una vez se hicieron las 10 preguntas en total y se mostró las respuestas)
    fun reset(){
        askedQuestionIds.clear()
        answeredQuestions.clear()
        _uiState.value = YesNoUiState()
        nextQuestion()
    }
}