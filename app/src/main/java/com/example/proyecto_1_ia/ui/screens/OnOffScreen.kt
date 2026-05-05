package com.example.proyecto_1_ia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.example.proyecto_1_ia.view_model.MainViewModel
import com.example.proyecto_1_ia.view_model.OnOffViewModel

@Composable
fun OnOffScreen(
    viewModel: OnOffViewModel,
    mainViewModel: MainViewModel
) {
    //Manejo de estados gracias al ViewModel
    val uiState by viewModel.uiState.collectAsState()

    //Sincronizamos primero con el DarkMode del dispositivo en base a MainViewModel
    LaunchedEffect(uiState.isOn) {
        mainViewModel.setDarkMode(!uiState.isOn) //ON es claro, OFF es oscuro
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            //Cambiamos el color del background para que sea más notable
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ){
        Text(
            text = "ON / OFF",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        //Carta indicadora visual
        Card(
            modifier = Modifier
                .size(250.dp)
                .shadow(
                    12.dp,
                    CircleShape,
                    ambientColor = if (uiState.isOn)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    spotColor = if (uiState.isOn)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isOn)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ){
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isOn) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (uiState.isOn) "Modo Claro" else "Modo Oscuro",
                        modifier = Modifier.size(80.dp),
                        tint = if (uiState.isOn)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = if (uiState.isOn) "ON" else "OFF",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isOn)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = uiState.statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isOn)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        //Manejo para detección de comandos
        if (uiState.commandDetected.isNotEmpty()){
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ){
                Text(
                    text = "Comando detectado: ${uiState.commandDetected}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            Text(
                text = "Waiting for voice command...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        //TODO: Quitar una vez esté integrado el ONNX (Botones de prueba)
        Text(
            text = "Testear Controles (Remover cuando ONNX)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.simulateCommand("on") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ON (Test)")
            }

            Button(
                onClick = { viewModel.simulateCommand("off") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DarkMode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OFF (Test)")
            }
        }
    }
}