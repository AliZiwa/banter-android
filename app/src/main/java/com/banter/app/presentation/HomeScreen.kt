package com.banter.app.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banter.app.domain.EngineState
import com.banter.app.domain.Presets
import com.banter.app.domain.Scenario

/**
 * The first screen: pick a group, then join in. One big colourful tile per scenario, the same
 * shape as the league tiles in the iOS app, plus one for a group of your own making.
 */
@Composable
fun HomeScreen(
    saved: Scenario,
    engine: EngineState,
    onStart: (Scenario) -> Unit,
    onCustom: () -> Unit,
    onOpenModel: () -> Unit,
) {
    val presets = Presets.all
    val custom = saved.takeIf { s -> presets.none { it.title == s.title } }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "Banter",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Pick a group. Then join in.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(presets + listOfNotNull(custom), key = { it.title }) { scenario ->
                ScenarioCard(scenario = scenario, onClick = { onStart(scenario) })
            }

            item(key = "custom") {
                MakeYourOwnCard(onClick = onCustom)
            }

            item(span = { GridItemSpan(2) }, key = "model") {
                ModelRow(engine = engine, onOpenModel = onOpenModel)
            }
        }
    }
}

/** A big tappable tile with the group's emoji and colour. */
@Composable
private fun ScenarioCard(scenario: Scenario, onClick: () -> Unit) {
    PressableTile(brush = scenario.gradient, shadowColor = scenario.tint, onClick = onClick) {
        Text(text = scenario.emoji, fontSize = 44.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = scenario.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun MakeYourOwnCard(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    PressableTile(
        brush = Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant),
        ),
        shadowColor = Color.Transparent,
        onClick = onClick,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = outline, modifier = Modifier.size(44.dp))
        Spacer(Modifier.weight(1f))
        Text(
            text = "Make your own",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = outline,
        )
    }
}

/** Shrinks a little while pressed. Small, but it makes the tiles feel alive. */
@Composable
private fun PressableTile(
    brush: Brush,
    shadowColor: Color,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "tile",
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .scale(scale)
            .shadow(elevation = 12.dp, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(brush, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 150.dp)
            .padding(18.dp),
        horizontalAlignment = Alignment.Start,
    ) { content() }
}

/** Where the model stands, in one line, with a way to the screen that manages it. */
@Composable
private fun ModelRow(engine: EngineState, onOpenModel: () -> Unit) {
    val text = when (engine) {
        EngineState.NoModel -> "No model on this phone yet. Add one and they can talk."
        EngineState.Loading -> "Waking the model up…"
        EngineState.Idle -> "Model installed, not loaded."
        is EngineState.Ready -> "Ready: ${engine.label}. Everything runs on your phone."
        is EngineState.Failed -> "Model failed: ${engine.message}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onOpenModel),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (engine is EngineState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Model",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
