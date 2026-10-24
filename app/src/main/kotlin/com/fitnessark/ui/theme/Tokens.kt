package com.fitnessark.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The 4/8dp rhythm: use these for padding and gaps instead of loose numbers. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Icon sizes: inline with text, in a button or row, and as a feature/empty-state mark. */
object IconSize {
    val sm = 18.dp
    val md = 24.dp
    val xl = 48.dp
}

/** Corner radii: chips and thumbnails (small), inputs and inner panels (medium), cards and photos (large). */
val FitnessArkShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)
