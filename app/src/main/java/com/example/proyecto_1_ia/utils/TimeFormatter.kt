package com.example.proyecto_1_ia.utils

//Librería que hacemos para directamente solo manejar el formato del tiempo visual
fun formatTime(totalSeconds: Long) : String{
    //Primero calcularemos el tiempo en horas / minutos / segundos
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    //Ahora, retornamos en tiempo, si tenemos 1 hora o más colocamos más, si no, nuestro formato será de
    //minutos y segundos
    return if (hours > 0){
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}