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
import androidx.compose.material.icons.filled.PlayArrow
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
    object StopGo: Screen("stop_go", "STOP/GO", Icons.Default.PlayArrow)
    object Directions: Screen("direction", "DIRECTION", Icons.Default.Directions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(mainViewModel: MainViewModel){
    val navController = rememberNavController()
    val screens = listOf(Screen.YesNo, Screen.OnOff, Screen.StopGo, Screen.Directions)

    //We manage the routing here
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val appState by mainViewModel.appState.collectAsState()

    //Acá agarramos para solicitud de permisos de grabación
    val context = LocalContext.current

    //Solicitud de permisos
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            mainViewModel.startVoiceRecognition()
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
                        onClick = {
                            //If the button is pressed, we navigate to that screen at that right exact moment
                            navController.navigate(screen.route){
                                popUpTo(navController.graph.startDestinationId){
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
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
                composable(Screen.YesNo.route) {
                    YesNoScreen()
                }
                composable(Screen.OnOff.route) {
                    //Cambiamos el modelo de visualización
                    val onOffViewModel: OnOffViewModel = viewModel()
                    OnOffScreen(
                        viewModel = onOffViewModel,
                        mainViewModel = mainViewModel
                    )
                }
                composable(Screen.StopGo.route) {
                    //Cambiamos el modelo de visualización (es opcional acá, ya que tiene el valor default
                    StopGoScreen()
                }
                composable(Screen.Directions.route) {
                    DirectionsScreen()
                }
            }

            //Grabador de Audio
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
                    if (appState.lastDetectedCommand.isNotEmpty()) {
                        Text(
                            text = "Last: ${appState.lastDetectedCommand.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Say a command",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Record button
                    AudioRecordButton(
                        isListening = appState.isListening,
                        onClick = {
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
                    )
                }
            }
        }
    }
}