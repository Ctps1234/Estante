package com.example.estante

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.estante.data.SettingsRepository
import com.example.estante.data.ThemeMode
import com.example.estante.ui.browser.BrowserScreen
import com.example.estante.ui.library.LibraryScreen
import com.example.estante.ui.reader.ReaderScreen
import com.example.estante.ui.reader.ReaderViewModel
import com.example.estante.ui.theme.EstanteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as EstanteApp

        setContent {
            val themeMode by app.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            EstanteTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EstanteNavHost()
                }
            }
        }
    }
}

@Composable
fun EstanteNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
                onOpenBrowser = { navController.navigate("browser") }
            )
        }
        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: 0L
            val readerViewModel: ReaderViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ReaderViewModel(
                            bookId = bookId,
                            repository = EstanteApp.instance.repository,
                            settings = EstanteApp.instance.settingsRepository
                        )
                    }
                }
            )
            ReaderScreen(
                vm = readerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("browser") {
            BrowserScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { bookId ->
                    navController.navigate("reader/$bookId") { launchSingleTop = true }
                }
            )
        }
    }
}
