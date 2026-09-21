package com.labteto.dshmobile.core.session

/** Resolve host paths without using the Android device's filesystem conventions. */
fun resolvePreviewReference(document: String, reference: String): String {
    val ref = reference.replace('\\', '/').substringBefore('#')
    if (ref.startsWith('/') || Regex("^[A-Za-z]:/").containsMatchIn(ref)) return ref
    val parent = document.replace('\\', '/').substringBeforeLast('/', "")
    val combined = if (parent.isEmpty()) ref else "$parent/$ref"
    val parts = mutableListOf<String>()
    combined.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty() && parts.last() != ".." && !parts.last().endsWith(':')) parts.removeAt(parts.lastIndex) else parts.add(part)
            else -> parts.add(part)
        }
    }
    return (if (combined.startsWith('/')) "/" else "") + parts.joinToString("/")
}

/**
 * The host file path a Markdown link or a JSON string value points at, or null when it is not one.
 *
 * A host path is never interpreted as an Android path or an arbitrary external scheme: `file://`
 * is unwrapped to its path, a Windows drive letter is kept verbatim, and anything else carrying a
 * scheme (`intent:`, `javascript:`, `content:`) is refused. That refusal is the point — a link in
 * a model's reply is untrusted input, and `previewPath` is the one place that decides whether it
 * is allowed to become a file the app will open.
 *
 * Lives in `:core` rather than beside its callers so both the Markdown renderer and the JSON
 * disclosure can use it without the presentational layer reaching up into a screen.
 */
fun previewPath(value: String): String? {
    val raw = value.trim().removeSurrounding("<", ">")
    if (raw.isEmpty() || raw.startsWith('#')) return null
    if (raw.startsWith("file://")) return runCatching { java.net.URI(raw).path }.getOrNull()
    if (Regex("^[A-Za-z]:[/\\\\]").containsMatchIn(raw)) return raw
    if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(raw)) return null
    return raw.substringBefore('#')
}

/** Convert a browser-normalized resource URL back to a readRelated relative path. */
fun relativePreviewResource(documentUrlPath: String, resourceUrlPath: String): String {
    val parent = documentUrlPath.substringBeforeLast('/').split('/').filter(String::isNotEmpty)
    val target = resourceUrlPath.split('/').filter(String::isNotEmpty)
    val shared = parent.zip(target).takeWhile { it.first == it.second }.size
    return (List(parent.size - shared) { ".." } + target.drop(shared)).joinToString("/")
}

/**
 * Aspect ratio of an attachment reference, guarded against a zero dimension on a malformed payload.
 *
 * Pure arithmetic, so it lives here rather than in a UI package: the presentational layer needs it
 * to reserve a box before the bitmap arrives, and a component is not allowed to depend on the
 * loader that used to own it.
 */
fun aspectRatioOf(width: Int, height: Int): Float =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
