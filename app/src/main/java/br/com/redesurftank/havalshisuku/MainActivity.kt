package br.com.redesurftank.havalshisuku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import br.com.redesurftank.havalshisuku.ui.navigation.MainScreen
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme

const val TAG = "HavalShisuku"

class MainActivity : ComponentActivity() {

    companion object {
        /** Título da aba em que o app deve abrir. Usado pelo chip de atualização do dashboard. */
        const val EXTRA_SCREEN = "br.com.redesurftank.havalshisuku.EXTRA_SCREEN"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HavalShisukuTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            initialScreen = intent?.getStringExtra(EXTRA_SCREEN)
                    )
                }
            }
        }
    }
}
