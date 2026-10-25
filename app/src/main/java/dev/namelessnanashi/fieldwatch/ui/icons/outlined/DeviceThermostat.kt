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

val Icons.Outlined.DeviceThermostat: ImageVector
    get() {
        if (_deviceThermostat != null) {
            return _deviceThermostat!!
        }
        _deviceThermostat = materialIcon(name = "Outlined.DeviceThermostat") {
            materialPath {
                moveTo(15.0f, 13.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                reflectiveCurveTo(9.0f, 3.34f, 9.0f, 5.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(-1.21f, 0.91f, -2.0f, 2.37f, -2.0f, 4.0f)
                curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                curveTo(17.0f, 15.37f, 16.21f, 13.91f, 15.0f, 13.0f)
                close()
                moveTo(11.0f, 11.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(1.0f)
                horizontalLineTo(11.0f)
                close()
            }
        }
        return _deviceThermostat!!
    }

private var _deviceThermostat: ImageVector? = null
