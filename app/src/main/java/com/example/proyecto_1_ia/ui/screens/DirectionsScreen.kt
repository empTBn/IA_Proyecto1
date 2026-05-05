package com.example.proyecto_1_ia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

@Composable
fun DirectionsScreen() {
    //Management of state to determine if the car is Stopping or going
    //var currentState : Direction;
    var directionsList : Array<Int?> = arrayOf(0, 0, 0, 0)
;

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "UP / DOWN / LEFT / RIGHT",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                //There is no ternary condition, we use this
                text = "Flecha en la dirección: DEFAULT",
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