package com.example.proyecto_1_ia.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyecto_1_ia.view_model.DirectionsViewModel


@Composable
fun DirectionsScreen(viewModel : DirectionsViewModel = viewModel()) {
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
            text = "DIRECTIONS",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        //D-Pad Controller
        DPadController(
            isUpActive = uiState.isUpActive,
            isDownActive = uiState.isDownActive,
            isLeftActive = uiState.isLeftActive,
            isRightActive = uiState.isRightActive,
            currentDirection = uiState.currentDirection
        )

        Spacer(modifier = Modifier.height(32.dp))

        //Counter de Directores
        Text(
            text = "Contador de Comandos",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        //Grid de Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ){
            DirectionCounterCard(
                label = "UP",
                count = uiState.upCount,
                isActive = uiState.isUpActive,
                modifier = Modifier.weight(1f)
            )
            DirectionCounterCard(
                label = "DOWN",
                count = uiState.downCount,
                isActive = uiState.isDownActive,
                modifier = Modifier.weight(1f)
            )
            DirectionCounterCard(
                label = "LEFT",
                count = uiState.leftCount,
                isActive = uiState.isLeftActive,
                modifier = Modifier.weight(1f)
            )
            DirectionCounterCard(
                label = "RIGHT",
                count = uiState.rightCount,
                isActive = uiState.isRightActive,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        //Feedback para la detección de comandos
        if (uiState.commandDetected.isNotEmpty()){
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Comando detectado: ${uiState.commandDetected}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            Text(
                text = "Waiting for voice command...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DPadController(
    isUpActive    : Boolean,
    isDownActive  : Boolean,
    isLeftActive  : Boolean,
    isRightActive : Boolean,
    currentDirection : String
){
    //Animamos para estados activos
    val upColor by animateColorAsState(
        targetValue = if (isUpActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween (300)
    )
    val downColor by animateColorAsState(
        targetValue = if (isDownActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween (300)
    )
    val leftColor by animateColorAsState(
        targetValue = if (isLeftActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween (300)
    )
    val rightColor by animateColorAsState(
        targetValue = if (isRightActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween (300)
    )
    val centerColor by animateColorAsState(
        targetValue = if (currentDirection.isNotEmpty())
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween (300)
    )

    //Ya terminamos el tipo DPAD, ahora nos toca ilustrarlo
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        //Colocamos el label de la dirección actual
        Text(
            text = if (currentDirection.isNotEmpty()) currentDirection else "NONE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        //Flecha hacia arriba
        ArrowButton(
            color = upColor,
            isActive = isUpActive,
            modifier = Modifier.size(70.dp)
        ){
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "UP",
                modifier = Modifier.size(40.dp),
                tint = if (isUpActive) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        //Centro (Flecha Izquierda, Centro, Flecha Derecha)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //Flecha izquierda
            ArrowButton(
                color = leftColor,
                isActive = isLeftActive,
                modifier = Modifier.size(70.dp)
            ){
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "LEFT",
                    modifier = Modifier.size(40.dp),
                    tint = if (isUpActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            //Circulo central
            Card(
                modifier = Modifier
                    .size(70.dp)
                    .shadow(4.dp, RoundedCornerShape(50)),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(containerColor = centerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {}


            //Flecha derecha
            ArrowButton(
                color = rightColor,
                isActive = isRightActive,
                modifier = Modifier.size(70.dp)
            ){
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "RIGHT",
                    modifier = Modifier.size(40.dp),
                    tint = if (isUpActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        //Flecha hacia abajo
        ArrowButton(
            color = downColor,
            isActive = isDownActive,
            modifier = Modifier.size(70.dp)
        ){
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "DOWN",
                modifier = Modifier.size(40.dp),
                tint = if (isUpActive) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ArrowButton(
  color: Color,
  isActive: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit  //Es para poder colocar dentro un objeto composable
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = if (isActive) 8.dp else 2.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            content
        }
    }
}

@Composable
fun DirectionCounterCard(
    label: String,
    count: Int,
    isActive: Boolean,
    modifier: Modifier
){
    //Agarramos el modo de decoración de borde y campo
    val borderColor by animateColorAsState(
        targetValue =   if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300)
    )
    val backgroundColor by animateColorAsState(
        targetValue =   if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300)
    )

    Card(
        modifier = modifier
            .border(2.dp, borderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}