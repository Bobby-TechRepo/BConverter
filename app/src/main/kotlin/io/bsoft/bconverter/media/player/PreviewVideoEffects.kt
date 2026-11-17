package io.bsoft.bconverter.media.player

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import io.bsoft.bconverter.domain.filter.PreviewLook
import io.bsoft.bconverter.domain.transform.ClipTransform

/** Media3 effects that show a clip's crop / turn / mirror and approximate its colour look; shared by both preview engines. */
@androidx.annotation.OptIn(UnstableApi::class)
object PreviewVideoEffects {
    fun build(look: PreviewLook?, t: ClipTransform): List<Effect> {
        val effects = mutableListOf<Effect>()
        if (!t.crop.isFull) {
            // Crop takes the kept area in normalised device coordinates: -1..1, y up.
            effects += Crop(t.crop.left * 2f - 1f, t.crop.right * 2f - 1f, 1f - t.crop.bottom * 2f, 1f - t.crop.top * 2f)
        }
        // Media3 turns counter-clockwise; the model turns clockwise.
        if (t.rotationDegrees != 0) effects += ScaleAndRotateTransformation.Builder().setRotationDegrees(-t.rotationDegrees.toFloat()).build()
        if (t.flipHorizontal || t.flipVertical) {
            effects += ScaleAndRotateTransformation.Builder().setScale(if (t.flipHorizontal) -1f else 1f, if (t.flipVertical) -1f else 1f).build()
        }
        if (look == null) return effects
        if (look.saturation != 0f || look.lightness != 0f || look.hueDegrees != 0f) {
            effects += HslAdjustment.Builder()
                .adjustHue(look.hueDegrees).adjustSaturation(look.saturation).adjustLightness(look.lightness).build()
        }
        if (look.contrast != 0f) effects += Contrast(look.contrast)
        if (look.red != 1f || look.green != 1f || look.blue != 1f) {
            effects += RgbAdjustment.Builder().setRedScale(look.red).setGreenScale(look.green).setBlueScale(look.blue).build()
        }
        return effects
    }
}
