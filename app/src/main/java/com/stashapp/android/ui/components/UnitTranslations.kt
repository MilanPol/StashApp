package com.stashapp.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stashapp.android.R
import com.stashapp.shared.domain.MeasurementUnit

@Composable
fun MeasurementUnit.translated(): String {
    return stringResource(
        when (this) {
            MeasurementUnit.LITERS -> R.string.unit_liters
            MeasurementUnit.MILLILITERS -> R.string.unit_milliliters
            MeasurementUnit.CENTILITERS -> R.string.unit_centiliters
            MeasurementUnit.KILOGRAMS -> R.string.unit_kilograms
            MeasurementUnit.GRAMS -> R.string.unit_grams
            MeasurementUnit.MILLIGRAMS -> R.string.unit_milligrams
            MeasurementUnit.OUNCES -> R.string.unit_ounces
            MeasurementUnit.PIECES -> R.string.unit_pieces
        }
    )
}
