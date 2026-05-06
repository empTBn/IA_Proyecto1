package com.example.proyecto_1_ia.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyecto_1_ia.utils.formatTime
import com.example.proyecto_1_ia.view_model.StopGoViewModel


@Composable
fun StopGoScreen(viewModel: StopGoViewModel = viewModel()) {
    //Agarramos la máquina de estados del view model
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        //Título
        Text(
            text = "GO / STOP",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        //Animación del indicador GO/STOP
        AnimatedCarIndicator(isMoving = uiState.isMoving)

        Spacer(modifier = Modifier.height(24.dp))

        //Texto indicador del estado
        Text(
            text = uiState.statusText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isMoving)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        //Cartas para manejo de los timers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ){
            //Timer para el tiempo actual (detenido o moviéndose)
            TimerCard(
                title = if(uiState.isMoving) "Tiempo desde que empezó a moverse" else "Tiempo desde que se detuvo",
                time = formatTime(
                    if (uiState.isMoving) uiState.currentGoDuration
                    else uiState.currentStopDuration
                ),
                icon = if (uiState.isMoving) Icons.Default.Speed else Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )

            //Timer del recorrido completo
            TimerCard(
                title = "Tiempo total del recorrido del vehículo",
                time = formatTime(uiState.totalRunTime),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        //Mensaje de advertencia con animación
        AnimatedVisibility(
            visible = uiState.showWarning,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ){
                Text(
                    text = uiState.warningMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        //Feedback de detección de mensajes
        if (uiState.commandDetected.isNotEmpty()){
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isMoving)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Comando detectado: ${uiState.commandDetected}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (uiState.isMoving)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

//Composable indicador para manejar la animación del vehículo en movimiento
@Composable
fun AnimatedCarIndicator(isMoving: Boolean){
    //Animamos el tamaño del vehículo para un efecto de pulsación cuando se mueve
    val scale by animateFloatAsState(
        targetValue = if (isMoving) 1.1f else 1.0f,  //Aumenta la escala de la imagen por 1 decimal
        animationSpec = if (isMoving) {
            //Si se mueve, aplicamos una animación en loop cada 600 milisegundos
            infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            //Si no se mueve aplicamos un tween por 300 milisegundos
            tween(300)
        }
    )

    //Animamos también por color por una duración de 500 milisegundos
    val containerColor by animateColorAsState(
        targetValue = if (isMoving)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(500)
    )
    val iconColor by animateColorAsState(
        targetValue = if (isMoving)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(500)
    )

    //Manejamos el área física donde se ve la animación
    Card(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Icon(
                imageVector = if (isMoving)
                    Icons.Default.DirectionsCar
                else
                    Icons.Default.StopCircle,
                contentDescription = if(isMoving) "Vehículo en movimiento" else "Vehículo detenido",
                modifier = Modifier.size(120.dp),
                tint = iconColor
            )
        }
    }
}

//Composable de una carta, la cual es modificada según nuestro gusto
@Composable
fun TimerCard(
    title: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
){
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = time,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


