package com.labteto.dshmobile.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.R
import com.labteto.dshmobile.core.session.previewPath
import com.labteto.dshmobile.ui.components.DisclosureRow
import com.labteto.dshmobile.ui.media.rememberAttachmentImageState
import kotlinx.serialization.json.*

/** JSON strings can contain embedded JSON; expand those without losing the original copy value. */
@Composable
internal fun JsonDisclosure(title: String, value: JsonElement, depth: Int = 0) {
    var expanded by remember(value) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val open = com.labteto.dshmobile.ui.components.LocalFileOpener.current
    DisclosureRow(title = title, expanded = expanded, onToggle = { expanded = !expanded }) {
        Column(Modifier.padding(start = 12.dp)) {
            if (value is JsonObject) {
                val imageId = (value["attachmentId"] as? JsonPrimitive)?.contentOrNull
                if (imageId != null && (value["type"]?.jsonPrimitive?.contentOrNull == "image" || value["mediaType"]?.jsonPrimitive?.contentOrNull?.startsWith("image/") == true)) {
                    run {
                        val w = (value["width"] as? JsonPrimitive)?.intOrNull ?: 512
                        val h = (value["height"] as? JsonPrimitive)?.intOrNull ?: 512
                        val imageState by rememberAttachmentImageState(attachmentId = imageId, intrinsicWidth = w, intrinsicHeight = h)
                        com.labteto.dshmobile.ui.components.AttachmentImage(state = imageState, intrinsicWidth = w, intrinsicHeight = h)
                    }
                }
            }
            if (value is JsonPrimitive && value.isString && title in setOf("path", "absolutePath", "file_path", "filePath")) {
                previewPath(value.content)?.let { path -> TextButton(onClick = { open(path) }) { Text(stringResource(R.string.panel_preview)) } }
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(value.toString())) }) { Text(stringResource(R.string.common_copy)) }
            when {
                depth >= 8 -> SelectionContainer { Text(value.toString(), fontFamily = FontFamily.Monospace) }
                value is JsonObject -> value.forEach { (name, child) -> JsonDisclosure(name, child, depth + 1) }
                value is JsonArray -> value.forEachIndexed { i, child -> JsonDisclosure(i.toString(), child, depth + 1) }
                value is JsonPrimitive && value.isString -> {
                    val parsed = remember(value) { runCatching { Json.parseToJsonElement(value.content) }.getOrNull() }
                    if (parsed is JsonObject || parsed is JsonArray) JsonDisclosure("JSON", parsed, depth + 1)
                    else SelectionContainer { Text(value.content, fontFamily = FontFamily.Monospace) }
                }
                else -> SelectionContainer { Text(value.toString(), fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

internal fun fuzzyContains(name: String, query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    var index = 0
    for (character in name.lowercase()) {
        if (character == needle[index]) index++
        if (index == needle.length) return true
    }
    return false
}

