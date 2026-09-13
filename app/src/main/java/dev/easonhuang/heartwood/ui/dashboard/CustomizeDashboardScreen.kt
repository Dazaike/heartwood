package dev.easonhuang.heartwood.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.easonhuang.heartwood.data.DashboardLayoutRepository
import dev.easonhuang.heartwood.data.Metric
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardScreen(
    layoutRepo: DashboardLayoutRepository,
    bottomInset: Dp,
    onBack: () -> Unit,
) {
    val entries by layoutRepo.editableOrder.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val visible = entries.filter { !it.second }.map { it.first }
    val hidden = entries.filter { it.second }.map { it.first }

    fun move(metric: Metric, delta: Int) {
        val index = visible.indexOf(metric)
        val target = index + delta
        if (index < 0 || target < 0 || target >= visible.size) return
        val reordered = visible.toMutableList().apply { add(target, removeAt(index)) }
        scope.launch { layoutRepo.setOrder(reordered) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Customize dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = inner.calculateTopPadding(), bottom = bottomInset + 24.dp),
        ) {
            item { SectionHeader("Shown on dashboard") }
            items(visible, key = { "visible_${it.key}" }) { metric ->
                MetricLayoutRow(
                    metric = metric,
                    dimmed = false,
                    checked = true,
                    onCheckedChange = { scope.launch { layoutRepo.setHidden(metric, true) } },
                    upEnabled = visible.indexOf(metric) > 0,
                    downEnabled = visible.indexOf(metric) < visible.size - 1,
                    onMoveUp = { move(metric, -1) },
                    onMoveDown = { move(metric, 1) },
                )
            }
            item { SectionHeader("Hidden") }
            items(hidden, key = { "hidden_${it.key}" }) { metric ->
                MetricLayoutRow(
                    metric = metric,
                    dimmed = true,
                    checked = false,
                    onCheckedChange = { scope.launch { layoutRepo.setHidden(metric, false) } },
                    upEnabled = false,
                    downEnabled = false,
                    onMoveUp = {},
                    onMoveDown = {},
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun MetricLayoutRow(
    metric: Metric,
    dimmed: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    upEnabled: Boolean,
    downEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val alpha = if (dimmed) 0.5f else 1f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            metric.icon, null,
            tint = metric.accent.copy(alpha = alpha),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(metric.accent.copy(alpha = 0.12f * alpha))
                .padding(8.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            metric.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        if (!dimmed) {
            IconButton(onClick = onMoveUp, enabled = upEnabled) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = downEnabled) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
