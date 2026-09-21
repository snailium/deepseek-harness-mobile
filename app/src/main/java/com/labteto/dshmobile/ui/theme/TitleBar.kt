package com.labteto.dshmobile.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.labteto.dshmobile.ui.components.FeatherIcons

/**
 * The shared metrics of the app's title bars.
 *
 * Three surfaces open a screen and carry its title plus a small set of icon controls: the chat
 * page's top bar, the chat-list drawer, and the session details panel. Each was built by hand and
 * each picked its own numbers — the drawer's toolbar row was 48dp tall with 20dp icons, the details
 * header 48dp with a 24dp title, the chat top bar 44dp with 18dp icons. A reader moving between
 * them sees three different weights of chrome for what is the same object.
 *
 * The chat top bar is the template: it is the one the user sees most and the one the other two are
 * meant to match, not the reverse. Its numbers live here so a surface that wants to be a title bar
 * takes them as a set rather than re-measuring.
 */
object DsTitleBar {
    /** Minimum row height. The controls inside are smaller; this is the floor the row keeps. */
    val height: Dp = 44.dp

    /**
     * Icon glyph size for the row's controls. Smaller than the default [com.labteto.dshmobile.ui.components.DsIconButton]
     * (20dp) because a title bar is chrome, not a primary control surface — and smaller glyphs let
     * a 44dp row hold its text without the icons crowding it.
     */
    val iconSize: Dp = 18.dp

    /**
     * Touch target for the row's icon controls. The accessible 48dp is right for a primary action
     * and wrong for a bar of three or four small icons: at 44dp row height a 48dp target would be
     * taller than its own row and the gaps between targets would disappear. 24dp keeps the glyphs
     * distinct while staying comfortably tappable.
     */
    val iconTouchTarget: Dp = 24.dp

    /** Gap between the controls in the row, and between a control and the title text. */
    val iconGap: Dp = DsSpacing.xsmall

    /** The title's typeface: one step up from the row's other text, which carries the emphasis. */
    val titleStyle: TextStyle = DsType.base16Strong

    /** The drawer's menu glyph, shared by every surface that opens the chat list. */
    val menuIcon: ImageVector = FeatherIcons.Menu
}
