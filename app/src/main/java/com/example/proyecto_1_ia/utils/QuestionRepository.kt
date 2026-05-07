package com.example.proyecto_1_ia.utils

import android.content.Context
import com.example.proyecto_1_ia.R
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class QuestionRepository(private val context: Context){
    //Manejamos un arraylist de preguntas
    private var questions: List<Question> = emptyList()

    init {
        //Al ser ejecutado, llamar a esta función
        loadQuestions()
    }

    private fun loadQuestions() {
        try{
            val inputStream = context.resources.openRawResource(R.raw.tareas)
            val reader = BufferedReader(InputStreamReader(inputStream))

            //Ahora que tenemos un lector por medio de buffer, hacemos que lea el JSON
            val jsonString = reader.readText()
            val jsonArray = JSONArray(jsonString)

            //Una vez extraímos el jsonArray, vamos a generar la lista de preguntas, como objetos
            val questionList = mutableListOf<Question>()
            for (i in 0 until jsonArray.length()){
                //Extraemos la pregunta como un tipo objeto Question
                val jsonObject = jsonArray.getJSONObject(i)
                questionList.add(
                    Question(
                        id = jsonObject.getInt("id"),
                        text = jsonObject.getString("text")
                    )
                )
            }
            questions = questionList
        } catch(e: Exception) {
            e.printStackTrace()
            // Fallback questions if JSON fails to load
            questions = listOf(
                Question(1, "Is the sky blue?"),
                Question(2, "Can birds fly?"),
                Question(3, "Is water wet?")
            )
        }
    }

    //Función para seleccionar de forma aleatoria las preguntas
    fun getRandomQuestion(excludeIds : List<Int> = emptyList()): Question? {
        //Agarramos la pregunta de forma directa si no hay nada en la lista de excluidos
        val availableQuestion = if (excludeIds.isEmpty()){
            questions
        } else {
            questions.filter { it.id !in excludeIds }
        }
        return availableQuestion.randomOrNull()
    }

    //Función para obtener la lista total de preguntas
    fun getAllQuestions(): List<Question> = questions
}