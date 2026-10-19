package dev.airpodsnative.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.airpodsnative.companion.R

/** Semantic colors mapped to Figma v3's AirPods Native / v3 iOS Colors.
 * No bundled SF Pro: Android system font is used for a private, portable APK.
 */
data class ApplePalette(
    val background: Color, val group: Color, val subgroup: Color,
    val primary: Color, val secondary: Color, val divider: Color,
    val selected: Color, val tint: Color, val green: Color, val modeTrack: Color
)
val LightApple = ApplePalette(
    Color(0xFFF2F2F7), Color.White, Color(0xFFF2F2F7),
    Color(0xFF161618), Color(0xFF8D8D93), Color(0xFFDADADF),
    Color.White, Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFE6E6EB)
)
val DarkApple = ApplePalette(
    Color.Black, Color(0xFF1C1C1E), Color(0xFF2C2C2E),
    Color(0xFFF5F5F7), Color(0xFF98989E), Color(0xFF3A3A3C),
    Color(0xFF626266), Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFF2C2C2E)
)
val LocalApplePalette = staticCompositionLocalOf { LightApple }

@Composable
fun AppleLook(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkApple else LightApple
    val colors = if (dark) darkColorScheme(
        background = p.background, surface = p.group, onSurface = p.primary,
        primary = p.tint, onBackground = p.primary
    ) else lightColorScheme(
        background = p.background, surface = p.group, onSurface = p.primary,
        primary = p.tint, onBackground = p.primary
    )
    CompositionLocalProvider(LocalApplePalette provides p) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
fun SectionCaption(title: String) {
    val p = LocalApplePalette.current
    Text(
        title.uppercase(),
        color = p.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 7.dp)
    )
}

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalApplePalette.current
    Column(
        Modifier.fillMaxWidth().background(p.group, RoundedCornerShape(15.dp)),
        content = content
    )
}

@Composable
fun Separator() {
    val p = LocalApplePalette.current
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = p.divider)
}

@Composable
fun NavRow(title: String, detail: String = "", enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalApplePalette.current
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) p.primary else p.secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) Text(detail, color = p.secondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Text("›", color = p.secondary, fontSize = 24.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
fun IosSwitchRow(
    title: String, detail: String,
    checked: Boolean?, enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val p = LocalApplePalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.primary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(
                if (checked == null) "Not reported by headphones" else detail,
                color = p.secondary, fontSize = 11.sp, lineHeight = 14.sp
            )
        }
        // Unknown is not Off: reconnects invalidate device observations until
        // fresh reports arrive. Never animate an enabled feature into a false
        // off state or expose an actionable toggle without an observed value.
        if (checked == null) {
            Box(Modifier.padding(start = 8.dp).size(width = 52.dp, height = 32.dp),
                contentAlignment = Alignment.Center) {
                Text("—", color = p.secondary, fontSize = 20.sp)
            }
        } else Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled && checked != null,
            modifier = Modifier.padding(start = 8.dp),
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.green, checkedThumbColor = Color.White,
                uncheckedTrackColor = p.subgroup, uncheckedThumbColor = Color.White,
                checkedBorderColor = p.green, uncheckedBorderColor = p.divider
            )
        )
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalApplePalette.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = p.primary, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = p.tint, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Display order follows the actual iPhone Settings strip, unlike the AAP wire enum:
 * Off=1, Transparency=3, Adaptive=2, Noise Cancellation=0.
 */
data class ListenChoice(val title: String, val modeId: Int, val icon: Int)
val ListeningModes = listOf(
    ListenChoice("Off", 1, R.drawable.mode_off),
    ListenChoice("Transparency", 3, R.drawable.mode_transparency),
    ListenChoice("Adaptive", 2, R.drawable.mode_adaptive),
    ListenChoice("Noise\nCancellation", 0, R.drawable.mode_cancellation)
)

@Composable
fun AppleListeningStrip(modeId: Int, enabled: Boolean, pendingMode: Int?, onClickMode: (Int) -> Unit) {
    val p = LocalApplePalette.current
    val selectedIndex = ListeningModes.indexOfFirst { it.modeId == modeId }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(91.dp)
            .background(p.modeTrack, RoundedCornerShape(18.dp))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / 4
        if (selectedIndex >= 0) {
            val offset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 240),
                label = "Listening selection"
            )
            Box(
                Modifier.offset(x = offset)
                    .width(segmentWidth).fillMaxHeight()
                    .background(p.selected, RoundedCornerShape(14.dp))
            )
        }
        Row(Modifier.fillMaxSize()) {
            ListeningModes.forEachIndexed { index, choice ->
                val selected = selectedIndex == index
                val selectedText = if (selected) p.primary else p.secondary
                Column(
                    Modifier.width(segmentWidth).fillMaxHeight()
                        .clickable(enabled = enabled && pendingMode == null) { onClickMode(choice.modeId) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(choice.icon), contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        choice.title, color = selectedText,
                        fontSize = 9.8.sp, lineHeight = 12.sp, textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 2
                    )
                }
            }
        }
    }
    if (pendingMode != null) {
        Text("Applying listening mode…", color = p.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    } else if (modeId < 0) {
        Text("Waiting for headphone state", color = p.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
