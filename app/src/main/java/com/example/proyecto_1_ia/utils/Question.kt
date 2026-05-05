package com.example.proyecto_1_ia.utils

data class Question(
    val id: Int,
    val text: String
)

data class AnsweredQuestion(
    val question: String,       //The question
    val answer: String          //Either YES/NO
)
