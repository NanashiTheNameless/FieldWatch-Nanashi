/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Vendored from androidx.compose.material:material-icons-extended 1.7.8 so the
 * app does not ship the whole extended icon set.
 */

package dev.namelessnanashi.fieldwatch.ui.icons.outlined

import dev.namelessnanashi.fieldwatch.ui.icons.Icons
import dev.namelessnanashi.fieldwatch.ui.icons.materialIcon
import dev.namelessnanashi.fieldwatch.ui.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.Outlined.Router: ImageVector
    get() {
        if (_router != null) {
            return _router!!
        }
        _router = materialIcon(name = "Outlined.Router") {
            materialPath {
                moveTo(16.0f, 4.2f)
                curveToRelative(1.5f, 0.0f, 3.0f, 0.6f, 4.2f, 1.7f)
                lineToRelative(0.8f, -0.8f)
                curveTo(19.6f, 3.7f, 17.8f, 3.0f, 16.0f, 3.0f)
                reflectiveCurveToRelative(-3.6f, 0.7f, -5.0f, 2.1f)
                lineToRelative(0.8f, 0.8f)
                curveTo(13.0f, 4.8f, 14.5f, 4.2f, 16.0f, 4.2f)
                close()
                moveTo(12.7f, 6.7f)
                lineToRelative(0.8f, 0.8f)
                curveToRelative(0.7f, -0.7f, 1.6f, -1.0f, 2.5f, -1.0f)
                reflectiveCurveToRelative(1.8f, 0.3f, 2.5f, 1.0f)
                lineToRelative(0.8f, -0.8f)
                curveToRelative(-0.9f, -0.9f, -2.1f, -1.4f, -3.3f, -1.4f)
                reflectiveCurveToRelative(-2.4f, 0.5f, -3.3f, 1.4f)
                close()
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(-2.0f)
                lineTo(17.0f, 9.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                lineTo(5.0f, 13.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 19.0f)
                lineTo(5.0f, 19.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(6.0f, 16.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                lineTo(6.0f, 18.0f)
                close()
                moveTo(9.5f, 16.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 16.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
        return _router!!
    }

private var _router: ImageVector? = null
