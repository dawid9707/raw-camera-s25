package com.example.rawcamera.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel // POPRAWKA: Dodano brakujący import
import com.example.rawcamera.CameraController
import com.example.rawcamera.CameraSettings
import com.example.rawcamera.LensInfo

@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    
    // POPRAWKA: viewModel.settings jest teraz typu State<CameraSettings>, 
    // więc delegacja 'by' działa poprawnie i zapewnia recompozycję.
    val settings by viewModel.settings
    val activeLensId by viewModel.activeLensId
    val showControls by viewModel.showControls
    val statusMessage by viewModel.statusMessage

    val controller = remember { CameraController(context) }
    val lenses = remember { controller.availableLenses }
    
    val currentLens = remember {
        lenses.firstOrNull { it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
            ?: lenses.firstOrNull()
    }
    
    val effectiveLensId = activeLensId ?: currentLens?.id
    val effectiveLens = lenses.firstOrNull { it.id == effectiveLensId } ?: currentLens
    
    val zoomRange = remember(effectiveLensId) {
        effectiveLensId?.let { controller.getZoomRange(it) } ?: 1f..1f
    }

    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    LaunchedEffect(effectiveLensId, surfaceView) {
        val sv = surfaceView ?: return@LaunchedEffect
        val lid = effectiveLensId ?: return@LaunchedEffect
        
        controller.close()
        // POPRAWKA: Jawne nazwy parametrów lambda (msg, path) zamiast 'it', 
        // aby uniknąć błędów inferencji typów kompilatora Kotlin.
        controller.onJpegCaptured = { path -> viewModel.setStatus("Zapisano JPEG: ${path.substringAfterLast("/")}") }
        controller.onRawCaptured = { path -> viewModel.setStatus("Zapisano RAW: ${path.substringAfterLast("/")}") }
        controller.onError = { msg -> viewModel.setStatus("Błąd: $msg") }
        
        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { 
                controller.open(lid, holder.surface, settings) 
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h2: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    LaunchedEffect(settings, surfaceView) {
        surfaceView?.holder?.surface?.let { 
            controller.applyPreviewSettings(settings, it) 
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> 
                SurfaceView(ctx).apply { 
                    holder.setFormat(android.graphics.PixelFormat.JPEG) 
                } 
            },
            modifier = Modifier.fillMaxSize(),
            update = { surfaceView = it }
        )

        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), 
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RAW Camera", 
                    color = Color.White, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.toggleControls() }) {
                    Icon(Icons.Default.SwitchCamera, contentDescription = "Kontrolki", tint = Color.White)
                }
            }
            effectiveLens?.let {
                LensSelector(lenses = lenses, currentId = it.id, onSelect = { lens -> viewModel.setLens(lens.id) })
            }
        }

        if (showControls) {
            ControlsPanel(
                settings = settings,
                zoomRange = zoomRange,
                // POPRAWKA: Jawny parametr 'newSettings' zamiast 'it'
                onSettingsChange = { newSettings -> viewModel.updateSettings(newSettings) }, 
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp)
            )
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(24.dp), 
            horizontalArrangement = Arrangement.SpaceEvenly, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(64.dp))
            Button(
                onClick = { controller.capture(settings) },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Zrób zdjęcie", tint = Color.Black, modifier = Modifier.size(40.dp))
            }
            statusMessage?.let { msg ->
                Text(
                    msg, 
                    color = Color.White, 
                    style = MaterialTheme.typography.labelSmall, 
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } ?: Spacer(Modifier.size(64.dp))
        }
    }

    DisposableEffect(Unit) { 
        onDispose { controller.close() } 
    }
}

// POPRAWKA: Wzorzec "backing property" z State<T>, aby delegacja 'by' w Compose działała poprawnie
class CameraViewModel : ViewModel() {
    private val _settings = mutableStateOf(CameraSettings())
    val settings: State<CameraSettings> = _settings
    
    private val _activeLensId = mutableStateOf<String?>(null)
    val activeLensId: State<String?> = _activeLensId
    
    private val _showControls = mutableStateOf(false)
    val showControls: State<Boolean> = _showControls
    
    private val _statusMessage = mutableStateOf<String?>(null)
    val statusMessage: State<String?> = _statusMessage

    fun updateSettings(s: CameraSettings) { _settings.value = s }
    fun setLens(id: String) { _activeLensId.value = id }
    fun toggleControls() { _showControls.value = !_showControls.value }
    fun setStatus(msg: String) { _statusMessage.value = msg }
}
