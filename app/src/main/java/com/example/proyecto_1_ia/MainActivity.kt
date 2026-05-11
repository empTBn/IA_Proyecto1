package com.example.proyecto_1_ia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PunchClock
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

//We import the libraries we are supposed to be using for the program
import com.example.proyecto_1_ia.ui.screens.*
import com.example.proyecto_1_ia.ui.theme.Proyecto_1_IATheme
import com.example.proyecto_1_ia.view_model.*
import com.example.proyecto_1_ia.ui.components.AudioRecordButton

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val appState by mainViewModel.appState.collectAsState()

            //Tenemos que modificar acá si o sí el DarkTheme
            Proyecto_1_IATheme(darkTheme = appState.isDarkMode) {
                MainApp(mainViewModel)
            }
        }
    }
}

//We define the screens as a sealed class
sealed class Screen(val route: String, val title: String, val icon: ImageVector){
    object YesNo: Screen("yes_no", "YES/NO", Icons.Default.Check)
    object OnOff: Screen("on_off", "ON/OFF", Icons.Default.PowerSettingsNew)
    object StopGo: Screen("stop_go", "STOP/GO", Icons.Default.PunchClock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(mainViewModel: MainViewModel){
    val navController = rememberNavController()
    val screens = listOf(Screen.YesNo, Screen.OnOff, Screen.StopGo)
    val routes = screens.map { it.route }

    //We manage the routing here
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val appState by mainViewModel.appState.collectAsState()

    //Acá agarramos para solicitud de permisos de grabación
    val context = LocalContext.current

    //Agarramos al último comando para evitar proceso duplicado
    val processedCommandId = remember { mutableLongStateOf(-1L) }

    //Solicitud de permisos para acceso a grabar audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) mainViewModel.startVoiceRecognition()
    }

    //Función ayudante para facilitar la navegación
    fun navigateToScreen(screen: Screen){
        mainViewModel.setCurrentScreen(
            when (screen) {
                Screen.YesNo -> ScreenEnum.YES_NO
                Screen.OnOff -> ScreenEnum.ON_OFF
                Screen.StopGo -> ScreenEnum.STOP_GO
            }
        )

        navController.navigate(screen.route){
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // ===== SINGLE CENTRALIZED COMMAND HANDLER =====
    // Acá manejamos el movimiento de la Barra inferior
    // Limpia también el comando para evitar volver a lanzarlo
    LaunchedEffect(appState.lastDetectedCommand) {
        val command = appState.lastDetectedCommand
        if (command.isEmpty()) return@LaunchedEffect

        val currentIndex = routes.indexOf(currentRoute)
        if (currentIndex == -1) return@LaunchedEffect

        when (command.uppercase()) {
            "LEFT" -> {
                val newIndex = if (currentIndex == 0) routes.lastIndex else currentIndex - 1
                navigateToScreen(screens[newIndex])
                // Clear command so screens don't process it
            }
            "RIGHT" -> {
                val newIndex = if (currentIndex == routes.lastIndex) 0 else currentIndex + 1
                navigateToScreen(screens[newIndex])
                // Clear command so screens don't process it
            }
        }
    }

    //We manage the navigation between screens here
    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = { navigateToScreen(screen) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.YesNo.route,
                modifier = Modifier.weight(1f)
            ){
                //Pantalla YES/NO
                composable(Screen.YesNo.route) {
                    val yesNoViewModel: YesNoViewModel = viewModel()
                    // Only handle non-navigation commands
                    LaunchedEffect(appState.lastDetectedCommand) {
                        val command = appState.lastDetectedCommand
                        if (command.isNotEmpty()
                            && command !in listOf("LEFT", "RIGHT")
                            && appState.currentScreen == ScreenEnum.YES_NO) {
                            yesNoViewModel.processVoiceCommand(command)
                        }
                    }
                    YesNoScreen(viewModel = yesNoViewModel)
                }

                //Pantalla ON/OFF
                composable(Screen.OnOff.route) {
                    val onOffViewModel: OnOffViewModel = viewModel()

                    LaunchedEffect(appState.lastDetectedCommand) {
                        val command = appState.lastDetectedCommand
                        if (command.isNotEmpty()
                            && command !in listOf("LEFT", "RIGHT")
                            && appState.currentScreen == ScreenEnum.ON_OFF) {
                                onOffViewModel.processVoiceCommand(command)
                            }
                        }
                    OnOffScreen(viewModel = onOffViewModel, mainViewModel = mainViewModel)
                }

                //Pantalla STOP/GO
                composable(Screen.StopGo.route) {
                    val stopGoViewModel: StopGoViewModel = viewModel()

                    LaunchedEffect(appState.lastDetectedCommand) {
                        val command = appState.lastDetectedCommand
                        if (command.isNotEmpty()
                            && command !in listOf("LEFT", "RIGHT")
                            && appState.currentScreen == ScreenEnum.STOP_GO) {
                            stopGoViewModel.processVoiceCommand(command)
                        }
                    }
                    StopGoScreen(stopGoViewModel)
                }
            }

            //Barra de grabación de audio
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Command feedback text
                    if (appState.isListening) {
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (appState.lastDetectedCommand.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Detected: ${appState.lastDetectedCommand.uppercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Confidence: ${(appState.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Tap mic to speak or say LEFT/RIGHT",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Record button
                    AudioRecordButton(
                        isListening = appState.isListening,
                        onClick = {
                            //Prevenimos que se de click mientras se esté escuchando / procesando
                            if (!appState.isListening){
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    mainViewModel.toggleVoiceRecognition()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}