package com.labteto.dshmobile.ui.components.media

import androidx.compose.ui.graphics.ImageBitmap

/**
 * What a raster looks like while it is being fetched, and after.
 *
 * A pure value type, deliberately in `components` rather than beside the loader in `ui/media`: the
 * presentational layer has to name this type to take it as a parameter, and `ui/media` reaches the
 * session store — which `ui/components` may not do (see `lint/`). Splitting the *state* from the
 * *fetching* is what lets the drawing half stay previewable with a hand-made value.
 */
sealed interface AttachmentImageState {
    data object Loading : AttachmentImageState
    data class Ready(val image: ImageBitmap) : AttachmentImageState
    data object Failed : AttachmentImageState
}
