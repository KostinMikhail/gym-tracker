package com.gym.tracker.ui.theme

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Все 7 тем для @Preview(@PreviewParameter). */
class GymThemePreviewProvider : PreviewParameterProvider<GymThemeId> {
    override val values: Sequence<GymThemeId> = GymThemeId.entries.asSequence()
}
