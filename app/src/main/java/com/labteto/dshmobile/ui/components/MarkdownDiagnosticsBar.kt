package com.labteto.dshmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labteto.dshmobile.ui.theme.DsTheme
import com.labteto.dshmobile.ui.theme.DsType

/**
 * The Markdown renderer's own read-out, shown above the composer while the "markdown shows as raw
 * text" report is open. Tapping the header folds it; tapping an entry expands its head.
 *
 * Temporary: remove with [MarkdownDiagnostics] once the report is closed.
 */
@Composable
fun MarkdownDiagnosticsBar(modifier: Modifier = Modifier) {
    val colors = DsTheme.colors
    val entries = MarkdownDiagnostics.entries
    if (entries.isEmpty()) return

    var open by remember { mutableStateOf(true) }
    var expandedAt by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.codeBlockBg)
            .border(1.dp, colors.borderL1, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val suspicious = entries.count { it.suspicious }
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MD ${entries.size}" + if (suspicious > 0) " · $suspicious suspect" else "",
                style = DsType.caption11Strong.copy(fontFamily = FontFamily.Monospace),
                color = if (suspicious > 0) colors.accent else colors.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "hide" else "show",
                style = DsType.caption11.copy(fontFamily = FontFamily.Monospace),
                color = colors.labelTertiary,
            )
        }
        if (open) {
            Column(
                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.forEach { entry ->
                    val expanded = expandedAt == entry.at
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            expandedAt = if (expanded) null else entry.at
                        },
                    ) {
                        Text(
                            "len=${entry.length}  " + entry.kinds.joinToString(","),
                            style = DsType.caption11.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = if (entry.suspicious) colors.accent else colors.labelSecondary,
                        )
                        Text(
                            entry.head,
                            style = DsType.caption11.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = colors.labelTertiary,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                        )
                    }
                }
            }
        }
    }
}
