package com.example.rawcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rawcamera.CameraSettings

@Composable
fun ControlsPanel(
    settings: CameraSettings,
    zoomRange: ClosedFloatingPointRange<Float>,
    onSettingsChange: (CameraSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LabeledSlider("ISO", settings.iso.toFloat(), 50f..6400f, settings.iso.toString()) { onSettingsChange(settings.copy(iso = it.toInt())) }
        
        val exposureMs = settings.exposureTimeNs / 1_000_000f
        LabeledSlider("Czas ekspozycji", exposureMs, 1f..1000f, "${String.format("%.1f", exposureMs)} ms") { onSettingsChange(settings.copy(exposureTimeNs = (it * 1_000_000).toLong())) }
        
        LabeledSlider("Ostrość", settings.focusDistance, 0f..10f, String.format("%.2f D", settings.focusDistance)) { onSettingsChange(settings.copy(focusDistance = it)) }
        
        LabeledSlider("Balans bieli (K)", settings.whiteBalance.toFloat(), 2000f..8000f, "${settings.whiteBalance} K") { onSettingsChange(settings.copy(whiteBalance = it.toInt())) }
        
        LabeledSlider("Zoom", settings.zoomRatio, zoomRange.start..zoomRange.endInclusive, String.format("%.1fx", settings.zoomRatio)) { onSettingsChange(settings.copy(zoomRatio = it)) }
        
        LabeledSlider("Jakość JPEG", settings.jpegQuality.toFloat(), 10f..100f, "${settings.jpegQuality}%") { onSettingsChange(settings.copy(jpegQuality = it.toInt())) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Zapisz RAW (DNG)", color = Color.White, fontWeight = FontWeight.Medium)
            Switch(checked = settings.captureRaw, onCheckedChange = { onSettingsChange(settings.copy(captureRaw = it)) })
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, displayValue: String, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontWeight = FontWeight.Medium)
            Text(displayValue, color = Color.White.copy(alpha = 0.7f))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = Modifier.fillMaxWidth())
    }
}
