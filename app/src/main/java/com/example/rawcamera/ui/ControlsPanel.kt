package com.example.rawcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rawcamera.LensInfo

@Composable
fun LensSelector(
    lenses: List<LensInfo>,
    currentId: String,
    onSelect: (LensInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lenses.filter { it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
            .sortedBy { it.focalLength }.forEach { lens ->
            val label = when {
                lens.focalLength < 15f -> "0.5x"
                lens.focalLength < 30f -> "1x"
                lens.focalLength < 60f -> "3x"
                else -> "5x"
            }
            val selected = lens.id == currentId
            FilterChip(
                selected = selected,
                onClick = { onSelect(lens) },
                label = { Text(label) },
                shape = CircleShape
            )
        }
    }
}
