package com.example.proyecto_1_ia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
//import androidx.compose.ui.tooling.preview.Preview

//We import the libraries we are supposed to be using for the program
import com.example.proyecto_1_ia.ui.screens.*
import com.example.proyecto_1_ia.ui.theme.Proyecto_1_IATheme
import com.example.proyecto_1_ia.view_model.*

//import com.example.proyecto_1_ia.ui.screens.OnOffScreen
//import com.example.proyecto_1_ia.ui.screens.StopGoScreen
//import com.example.proyecto_1_ia.ui.screens.DirectionsScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Proyecto_1_IATheme {
                val mainViewModel: MainViewModel = viewModel()
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
        NavHost(
            navController = navController,
            startDestination = Screen.YesNo.route,
            modifier = Modifier.padding(innerPadding)
        ){
            composable(Screen.YesNo.route) {
                YesNoScreen()
            }
            //composable(Screen.OnOff.route) {
            //    OnOffScreen()
            //}
            //composable(Screen.StopGo.route) { StopGoScreen() }
            //composable(Screen.Directions.route) { DirectionsScreen() }
        }
    }
}