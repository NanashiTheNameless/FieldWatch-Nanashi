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

val Icons.Outlined.SettingsInputAntenna: ImageVector
    get() {
        if (_settingsInputAntenna != null) {
            return _settingsInputAntenna!!
        }
        _settingsInputAntenna = materialIcon(name = "Outlined.SettingsInputAntenna") {
            materialPath {
                moveTo(12.0f, 5.0f)
                curveToRelative(-3.87f, 0.0f, -7.0f, 3.13f, -7.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -2.76f, 2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(13.0f, 14.29f)
                curveToRelative(0.88f, -0.39f, 1.5f, -1.26f, 1.5f, -2.29f)
                curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                reflectiveCurveTo(9.5f, 10.62f, 9.5f, 12.0f)
                curveToRelative(0.0f, 1.02f, 0.62f, 1.9f, 1.5f, 2.29f)
                verticalLineToRelative(3.3f)
                lineTo(7.59f, 21.0f)
                lineTo(9.0f, 22.41f)
                lineToRelative(3.0f, -3.0f)
                lineToRelative(3.0f, 3.0f)
                lineTo(16.41f, 21.0f)
                lineTo(13.0f, 17.59f)
                verticalLineToRelative(-3.3f)
                close()
                moveTo(12.0f, 1.0f)
                curveTo(5.93f, 1.0f, 1.0f, 5.93f, 1.0f, 12.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -4.97f, 4.03f, -9.0f, 9.0f, -9.0f)
                reflectiveCurveToRelative(9.0f, 4.03f, 9.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -6.07f, -4.93f, -11.0f, -11.0f, -11.0f)
                close()
            }
        }
        return _settingsInputAntenna!!
    }

private var _settingsInputAntenna: ImageVector? = null
