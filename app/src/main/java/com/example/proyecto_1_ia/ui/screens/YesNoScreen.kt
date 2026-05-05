package com.example.proyecto_1_ia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.proyecto_1_ia.view_model.YesNoViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proyecto_1_ia.utils.AnsweredQuestion

@Composable
fun YesNoScreen(viewModel: YesNoViewModel = viewModel()) {
    //Acá agarramos el manejo del estado de la pantalla
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isComplete){
        //Mostrar la pantalla de resultados una vez la lista de resultados esté completa
        ResultsScreen(
            answeredQuestion = uiState.answeredQuestions,
            onReset = { viewModel.reset() }
        )
    } else {
        //Mostramos la pantalla de preguntas
        QuestionScreen(
            currentQuestion = uiState.currentQuestion?.text ?: "Loading...",
            questionNumber = uiState.questionNumber,
            totalQuestions = uiState.totalQuestions,
            commandDetected = uiState.commandDetected,
            //Testeamos los botones
            onYesClick = { viewModel.simulateCommand("yes") },
            onNoClick = { viewModel.simulateCommand("no") }
        )
    }
}

@Composable
fun QuestionScreen(
    currentQuestion: String,
    questionNumber: Int,
    totalQuestions: Int,
    commandDetected: String,
    onYesClick: () -> Unit,
    onNoClick: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        //Header with progress
        Text(
            text = "Question $questionNumber of $totalQuestions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        //Progress indicator
        LinearProgressIndicator(
            progress = { questionNumber.toFloat() / totalQuestions.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        //Question Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentQuestion,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Command detection feedback
        if (commandDetected.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (commandDetected == "YES")
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Detected: $commandDetected",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (commandDetected == "YES")
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            Text(
                text = "Waiting for voice command...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Test buttons (remove these when ONNX is integrated)
        Text(
            text = "Test Controls (Remove when ONNX is ready)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onYesClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("YES (Test)", modifier = Modifier.padding(8.dp))
            }

            Button(
                onClick = onNoClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("NO (Test)", modifier = Modifier.padding(8.dp))
            }
        }
    }
}
@Composable
fun ResultsScreen(
    answeredQuestion: List<AnsweredQuestion>,
    onReset: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            //Cambiamos el color del background para que sea más notable
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Preguntas y Respuestas:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    itemsIndexed(answeredQuestion) { index, qa ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp))
                            {
                                Text(
                                    text = "Q${index + 1}: ${qa.question}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Respuesta: ${qa.answer}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (qa.answer == "YES")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onReset,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Over")
        }
    }
}