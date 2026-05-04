package com.example.proyecto_1_ia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StopGoScreen() {
    //Management of state to determine if the car is Stopping or going
    var currentState : Boolean = false; //We start in the stop state
    //Time management parts
    var timePassedSinceStopped : Float;
    var timeGoingSinceTurningOn : Float;
    var timeOfEntireRunSinceGoing : Float;

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GO / STOP",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                //There is no ternary condition, we use this
                text = if (currentState) "Carro moviéndose" else "Carro detenido",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Placeholder for showing command recognition
            Text(
                text = "Waiting for command...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
