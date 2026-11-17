package io.bsoft.bconverter.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spec §37: on a tablet, forms and lists keep a comfortable reading width and sit in the middle instead of stretching edge to edge. */
const val READABLE_MAX_WIDTH_DP = 640

/** Limits the width to [max] and centres it; on a phone (narrower than [max]) it changes nothing. */
fun Modifier.readableWidth(max: Dp = READABLE_MAX_WIDTH_DP.dp): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()
