package br.com.redesurftank.havalshisuku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Force landscape orientation
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        setContent {
            HavalShisukuTheme {
                SplashScreen {
                    // Navigate to MainActivity
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var showDots by remember { mutableStateOf(true) }
    
    // Animate progress
    LaunchedEffect(Unit) {
        // Simulate loading
        for (i in 1..100) {
            progress = i / 100f
            delay(30) // 3 seconds total
        }
        onComplete()
    }
    
    // Dots animation
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showDots = !showDots
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A3F5F),
                        Color(0xFF151920)
                    ),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo Container
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated Circle
                Canvas(
                    modifier = Modifier.size(180.dp)
                ) {
                    drawAnimatedCircle(progress)
                }
                
                // Lightning Logo
                Canvas(
                    modifier = Modifier.size(80.dp)
                ) {
                    drawLightningLogo()
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // HAVAL Text
            Text(
                stringResource(R.string.app_splash_title),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp
            )
            
            // IMPULSE Text
            Text(
                stringResource(R.string.app_splash_subtitle),
                color = Color(0xFF4A9EFF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 12.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Support Text
            Text(
                stringResource(R.string.app_splash_support),
                color = Color(0xFFB0B8C4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(60.dp))
            
            // Loading dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.height(20.dp)
            ) {
                for (i in 0..2) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (showDots || i == 0) Color(0xFF4A9EFF) 
                                else Color(0xFF4A9EFF).copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    if (i < 2) Spacer(modifier = Modifier.width(16.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Loading Text
            Text(
                stringResource(R.string.app_splash_init),
                color = Color(0xFFB0B8C4),
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress Bar
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF4A9EFF),
                    trackColor = Color(0xFF2A3F47).copy(alpha = 0.5f)
                )
            }
        }
    }
}

fun DrawScope.drawAnimatedCircle(progress: Float) {
    val strokeWidth = 3.dp.toPx()
    val radius = size.minDimension / 2 - strokeWidth / 2
    val center = Offset(size.width / 2, size.height / 2)
    
    // Background circle
    drawCircle(
        color = Color(0xFF4A9EFF).copy(alpha = 0.2f),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    
    // Animated arc
    val sweepAngle = 360f * progress
    rotate(degrees = -90f, pivot = center) {
        drawArc(
            color = Color(0xFF4A9EFF),
            startAngle = 0f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
    }
}

fun DrawScope.drawLightningLogo() {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val scale = size.minDimension * 0.01f
    
    // Lightning bolt path matching the app icon
    val lightningPath = Path().apply {
        moveTo(centerX + scale * 16f, centerY - scale * 26f)  // Top point
        lineTo(centerX - scale * 16f, centerY + scale * 0f)   // Left middle
        lineTo(centerX - scale * 4f, centerY + scale * 0f)    // Small indent right
        lineTo(centerX - scale * 18f, centerY + scale * 26f)  // Bottom point
        lineTo(centerX + scale * 10f, centerY + scale * 1f)   // Right middle
        lineTo(centerX - scale * 2f, centerY + scale * 1f)    // Small indent left
        close()
    }
    
    // Drop shadow
    val shadowPath = Path().apply {
        moveTo(centerX + scale * 16.5f, centerY - scale * 25.5f)
        lineTo(centerX - scale * 15.5f, centerY + scale * 0.5f)
        lineTo(centerX - scale * 3.5f, centerY + scale * 0.5f)
        lineTo(centerX - scale * 17.5f, centerY + scale * 26.5f)
        lineTo(centerX + scale * 10.5f, centerY + scale * 1.5f)
        lineTo(centerX - scale * 1.5f, centerY + scale * 1.5f)
        close()
    }
    
    // Draw shadow
    drawPath(
        path = shadowPath,
        color = Color.Black.copy(alpha = 0.15f)
    )
    
    // Draw outer glow
    drawPath(
        path = lightningPath,
        color = Color.White.copy(alpha = 0.2f),
        style = Stroke(width = scale * 3f)
    )
    
    // Draw main lightning bolt in white
    drawPath(
        path = lightningPath,
        color = Color.White
    )
    
    // Add subtle blue tint on top
    drawPath(
        path = lightningPath,
        color = Color(0xFF6BB6FF).copy(alpha = 0.1f)
    )
}
