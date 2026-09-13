package dev.easonhuang.heartwood.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.easonhuang.heartwood.data.ExportFormat
import dev.easonhuang.heartwood.data.Metric

// 3650 days (~10 years) is the pragmatic "All time" ceiling because exportRows(metric, days)
// requires a finite window.
private val DAY_OPTIONS = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year", 3650 to "All time")

@Composable
fun ExportDialog(
    grantedMetrics: List<Metric>,
    onDismiss: () -> Unit,
    onExport: (metrics: List<Metric>, days: Int, format: ExportFormat) -> Unit,
) {
    var selected by remember { mutableStateOf(grantedMetrics.toSet()) }
    var days by remember { mutableStateOf(365) }
    var format by remember { mutableStateOf(ExportFormat.CSV) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export data") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Metrics", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (format == ExportFormat.MARKDOWN) {
                    Text(
                        "Markdown export always includes every metric you've granted access to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                } else {
                    grantedMetrics.forEach { metric ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = metric in selected,
                                    onClick = {
                                        selected = if (metric in selected) selected - metric else selected + metric
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = metric in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + metric else selected - metric
                                },
                            )
                            Text(metric.title, modifier = Modifier.height(48.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Range", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                DAY_OPTIONS.forEach { (value, label) ->
                    Row(
                        Modifier.selectable(selected = days == value, onClick = { days = value }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == value, onClick = { days = value })
                        Text(label, modifier = Modifier.height(48.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ExportFormat.entries.forEach { entry ->
                    Row(
                        Modifier.selectable(selected = format == entry, onClick = { format = entry }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == entry, onClick = { format = entry })
                        Text(entry.label, modifier = Modifier.height(48.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val metrics = if (format == ExportFormat.MARKDOWN) grantedMetrics else selected.toList()
                    onExport(metrics, days, format)
                },
                enabled = format == ExportFormat.MARKDOWN || selected.isNotEmpty(),
            ) { Text(if (format == ExportFormat.MARKDOWN) "Share" else "Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
