package dev.airpodsnative.companion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import dev.airpodsnative.companion.ui.LocalApplePalette

/**
 * Native Android animated WebP decoder (not a heavyweight 3D scene renderer).
 *
 * Personal Debug builds can contain a 60 fps animation derived from Apple's
 * official AirPods Max Midnight USDZ. No USDZ or proprietary video included in
 * the public/source Release build. Motion is skipped when system animations
 * are disabled. A static matching product image remains the fallback.
 *
 * The motion is a product illustration only; device/bond state is never faked.
 */
object PairingMotion {
    private const val TAG = "AirPodsMotion"
    private const val OPTIONAL_ASSET_NAME = "airpods_pair_motion"
    fun resourceId(context: Context): Int =
        context.resources.getIdentifier(OPTIONAL_ASSET_NAME, "drawable", context.packageName)

    fun enabled(context: Context): Boolean = ValueAnimator.areAnimatorsEnabled()

    fun install(imageView: ImageView, fallback: Int = R.drawable.airpods_max_midnight): Boolean {
        stop(imageView)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        val id = resourceId(imageView.context)
        if (id == 0 || !enabled(imageView.context)) {
            imageView.contentDescription = "Midnight AirPods Max static image"
            imageView.setImageResource(fallback)
            return false
        }
        return try {
            val drawable = ImageDecoder.decodeDrawable(
                ImageDecoder.createSource(imageView.resources, id)
            )
            if (drawable is AnimatedImageDrawable) {
                // Entrance orbit plays once and holds front-facing final pose.
                // Continuous looping would visibly snap from final back to start.
                drawable.repeatCount = 0
                drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                    override fun onAnimationStart(drawable: Drawable?) {
                        Log.i(TAG, "ANIMATED_WEBP_PLAYBACK_BEGAN")
                    }
                    override fun onAnimationEnd(drawable: Drawable?) {
                        Log.i(TAG, "ANIMATED_WEBP_COMPLETED_ONCE")
                    }
                })
                imageView.setImageDrawable(drawable)
                imageView.contentDescription = "Midnight AirPods Max 3D animation"
                // AndroidView.factory and the overlay ImageView factory run before
                // the View is attached. AnimatedImageDrawable started before it
                // has a drawing callback may appear frozen even though it decodes.
                imageView.post {
                    if (imageView.isAttachedToWindow && imageView.drawable === drawable) {
                        drawable.start()
                        Log.i(TAG, "ANIMATED_WEBP_STARTED_ATTACHED")
                    }
                }
                true
            } else {
                imageView.contentDescription = "Midnight AirPods Max static image"
                imageView.setImageResource(fallback)
                false
            }
        } catch (error: Exception) {
            Log.w(TAG, "Animated artwork not supported, using static fallback", error)
            imageView.contentDescription = "Midnight AirPods Max static image"
            imageView.setImageResource(fallback)
            false
        }
    }

    fun stop(imageView: ImageView) {
        (imageView.drawable as? AnimatedImageDrawable)?.stop()
        imageView.setImageDrawable(null)
    }
}

@Composable
fun AirPodsPairingArtwork(
    modifier: Modifier,
    fallback: Int = R.drawable.airpods_max_midnight
) {
    val context = LocalContext.current
    // The file lives exclusively in the personal debug source set. Other
    // builds and Android Reduced Motion settings use the ordinary Image.
    val available = remember(context) {
        PairingMotion.resourceId(context) != 0 && PairingMotion.enabled(context)
    }
    LaunchedEffect(available) {
        Log.i("AirPodsMotion", "MOTION_DECISION available=$available res=${PairingMotion.resourceId(context)} enabled=${PairingMotion.enabled(context)}")
    }
    if (!available) {
        Image(
            painter = painterResource(fallback),
            contentDescription = "Midnight AirPods Max",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        return
    }
    AndroidView(
        factory = { c ->
            ImageView(c).apply {
                contentDescription = "Midnight AirPods Max 3D animation"
                PairingMotion.install(this, fallback)
            }
        },
        modifier = modifier,
        update = { image ->
            // Do not repeatedly install the image on recompositions:
            // AnimatedImageDrawable advances frames independently.
        },
        onRelease = { PairingMotion.stop(it) }
    )
}
