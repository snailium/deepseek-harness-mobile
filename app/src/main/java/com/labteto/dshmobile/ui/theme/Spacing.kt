package com.labteto.dshmobile.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for consistent layout rhythm throughout the app.
 * Use these tokens instead of hardcoded dp values to maintain visual consistency.
 */
object DsSpacing {
    /** 4dp - Minimal spacing between tightly related elements (e.g., icon and label) */
    val tiny = 4.dp
    
    /** 6dp - Extra small spacing */
    val xsmall = 6.dp
    
    /** 8dp - Small spacing for related items within a component */
    val small = 8.dp
    
    /** 8dp - Compact spacing for related items within a component (alias for small) */
    val compact = 8.dp
    
    /** 12dp - Medium spacing between component elements */
    val medium = 12.dp
    
    /** 12dp - Standard spacing between component elements (alias for medium) */
    val standard = 12.dp
    
    /** 16dp - Comfortable spacing for screen padding and section content */
    val comfortable = 16.dp

    /**
     * The horizontal inset every top-level container on the chat surface shares.
     *
     * Named for its *role*, not its size, because that is what went wrong: the names above say how
     * much an inset is, so each container picked the amount that looked right on its own and the
     * left edges stopped lining up — the transcript used 12, the to-do strip 6, the question panel
     * none at all. A reader sees a ragged column and cannot say which one is wrong.
     *
     * Any container that spans the screen width and holds transcript-level content uses this:
     * the composer card, the transcript's own content padding, the docks above the composer, the
     * to-do strip, the question and approval panels, the connection banner. Nested or
     * self-contained surfaces keep choosing by amount — a chip's inner padding has no obligation
     * to match the page margin.
     */
    val pageHorizontal = 12.dp

    /**
     * The inset between a text surface and its content, shared by the composer's input field and
     * the user message bubble so the two read as the same kind of object.
     */
    val textFieldInset = PaddingValues(10.dp)

    /**
     * The leading inset for content *inside* an expanded disclosure row.
     *
     * Derived from the header it sits under rather than chosen: the row's own 4dp padding, the
     * 14dp chevron, a 4dp gap, and the 6dp that centres a 16dp leading slot's content. A body that
     * starts at 0 sits under the chevron and reads as a sibling of the header rather than as its
     * contents.
     *
     * It had been hardcoded as `28.dp` in five places across four files, which is exactly how it
     * drifted — each site looked locally reasonable and nothing said they had to agree. Named for
     * its role so the next disclosure body does not re-derive it.
     */
    val disclosureBodyIndent = 28.dp

    /** 20dp - Large spacing between major UI sections */
    val large = 20.dp
    
    /** 24dp - Extra large spacing for distinct content blocks */
    val xlarge = 24.dp
    
    /** 32dp - Major section spacing for clear visual separation */
    val xxlarge = 32.dp
    
    /** 48dp - Minimum touch target size per Material Design guidelines */
    val touchTarget = 48.dp
}
