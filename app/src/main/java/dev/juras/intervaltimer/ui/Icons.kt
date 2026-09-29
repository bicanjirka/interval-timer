package dev.juras.intervaltimer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material "content copy"; it only ships in the large extended icon set, so it is drawn here from its path. */
val Icons.Filled.ContentCopy: ImageVector
    get() = copyIcon ?: materialIcon(name = "Filled.ContentCopy") {
        materialPath {
            moveTo(16f, 1f); horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f); horizontalLineToRelative(2f)
            verticalLineTo(3f); horizontalLineToRelative(12f)
            verticalLineTo(1f); close()
            moveTo(19f, 5f); horizontalLineTo(8f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(11f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(19f, 21f); horizontalLineTo(8f)
            verticalLineTo(7f); horizontalLineToRelative(11f)
            verticalLineToRelative(14f); close()
        }
    }.also { copyIcon = it }

private var copyIcon: ImageVector? = null

/** Material "remove" (a minus), also from the extended set. */
val Icons.Filled.Remove: ImageVector
    get() = removeIcon ?: materialIcon(name = "Filled.Remove") {
        materialPath {
            moveTo(19f, 13f); horizontalLineTo(5f)
            verticalLineToRelative(-2f); horizontalLineToRelative(14f)
            verticalLineToRelative(2f); close()
        }
    }.also { removeIcon = it }

private var removeIcon: ImageVector? = null
