package dev.airpodsnative.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.airpodsnative.companion.ui.LocalApplePalette

/**
 * App-owned first-time setup half-sheet, styled like a nearby pairing card.
 * Not Google Fast Pair or a system-owned pairing dialog. Android createBond()
 * still handles final user-facing confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyPairingSheet(
    status: PairingDiscovery.Event,
    isDemo: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onConnect: (String) -> Unit,
) {
    val p = LocalApplePalette.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = (screenHeight * 0.22f).coerceIn(110.dp, 190.dp)
    val found = status as? PairingDiscovery.Event.Found
    val loading = status == PairingDiscovery.Event.Searching ||
        status == PairingDiscovery.Event.PairingRequested
    val transform by animateFloatAsState(
        targetValue = if (loading) .978f else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "Nearby device introduction"
    )
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onClose,
        containerColor = p.group,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().height(screenHeight * 0.55f)
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Only the descriptive portion scrolls; Connect and Not now
            // remain in view even on a short screen or with large font size.
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text("AirPods Max 2", color = p.primary, fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isDemo) "Design preview · no Bluetooth pairing"
                else "Ready to connect to your Android device",
                color = p.secondary, fontSize = 12.sp, textAlign = TextAlign.Center
            )
            // Real geometry-rendered alpha animation in the personal Debug APK;
            // static art fallback for Release or Android Reduced Motion.
            // Use a genuine-alpha 60fps native Animated WebP for the main
            // popup. Private MP4 hardware-decode version remains available as
            // an experimental comparison on this workstation.
            AirPodsPairingArtwork(
                modifier = Modifier.fillMaxWidth().height(heroHeight).scale(transform)
            )
            AnimatedVisibility(visible = loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp),
                        color = p.tint, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (status == PairingDiscovery.Event.PairingRequested)
                            "Waiting for Android pairing confirmation…"
                        else "Looking for nearby AirPods Max…",
                        fontSize = 13.sp, color = p.secondary
                    )
                }
            }
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(230)) + slideInVertically(
                        initialOffsetY = { it / 6 }, animationSpec = tween(230)
                    )) togetherWith fadeOut(tween(130))
                },
                label = "AirPods pairing state transition"
            ) { displayedStatus ->
                // AnimatedContent's root uses a Box-like layout. Multiple
                // ungrouped Text nodes overlap at the same origin. Always
                // wrap the state body in a vertical layout.
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (displayedStatus) {
                PairingDiscovery.Event.Searching -> {
                    Spacer(Modifier.height(14.dp))
                    Text("Hold the listening mode button for about 5 seconds until the light flashes white.",
                        color = p.secondary, fontSize = 12.sp, lineHeight = 16.sp,
                        textAlign = TextAlign.Center)
                }
                is PairingDiscovery.Event.Found -> {
                    Text("Nearby headset found", color = p.green, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(displayedStatus.name, color = p.primary, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Confirm that this is your AirPods Max 2. Bluetooth names alone don't prove the generation.",
                        color = p.secondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                PairingDiscovery.Event.NothingFound ->
                    Text("No AirPods Max detected. Enter pairing mode and try again.",
                        color = p.secondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                is PairingDiscovery.Event.Failed ->
                    Text(displayedStatus.reason, color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp, textAlign = TextAlign.Center)
                PairingDiscovery.Event.PairingRequested -> {}
                is PairingDiscovery.Event.Paired -> {
                    Text("Bluetooth pairing complete", color = p.green, fontWeight = FontWeight.SemiBold)
                }
            }
                } // state content Column
            } // AnimatedContent
            } // scrollable description
            Spacer(Modifier.height(10.dp))
            when {
                isDemo -> Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Close preview")
                }
                found != null -> Button(
                    onClick = { onConnect(found.address) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Connect") }
                status == PairingDiscovery.Event.NothingFound ||
                    status is PairingDiscovery.Event.Failed -> Button(
                    onClick = onRetry, modifier = Modifier.fillMaxWidth()
                ) { Text("Scan again") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text("Not now") }
        }
    }
}
