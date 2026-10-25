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

val Icons.Outlined.CellTower: ImageVector
    get() {
        if (_cellTower != null) {
            return _cellTower!!
        }
        _cellTower = materialIcon(name = "Outlined.CellTower") {
            materialPath {
                moveTo(7.3f, 14.7f)
                lineToRelative(1.2f, -1.2f)
                curveToRelative(-1.0f, -1.0f, -1.5f, -2.3f, -1.5f, -3.5f)
                curveToRelative(0.0f, -1.3f, 0.5f, -2.6f, 1.5f, -3.5f)
                lineTo(7.3f, 5.3f)
                curveToRelative(-1.3f, 1.3f, -2.0f, 3.0f, -2.0f, 4.7f)
                reflectiveCurveTo(6.0f, 13.4f, 7.3f, 14.7f)
                close()
            }
            materialPath {
                moveTo(19.1f, 2.9f)
                lineToRelative(-1.2f, 1.2f)
                curveToRelative(1.6f, 1.6f, 2.4f, 3.8f, 2.4f, 5.9f)
                curveToRelative(0.0f, 2.1f, -0.8f, 4.3f, -2.4f, 5.9f)
                lineToRelative(1.2f, 1.2f)
                curveToRelative(2.0f, -2.0f, 2.9f, -4.5f, 2.9f, -7.1f)
                curveTo(22.0f, 7.4f, 21.0f, 4.9f, 19.1f, 2.9f)
                close()
            }
            materialPath {
                moveTo(6.1f, 4.1f)
                lineTo(4.9f, 2.9f)
                curveTo(3.0f, 4.9f, 2.0f, 7.4f, 2.0f, 10.0f)
                curveToRelative(0.0f, 2.6f, 1.0f, 5.1f, 2.9f, 7.1f)
                lineToRelative(1.2f, -1.2f)
                curveToRelative(-1.6f, -1.6f, -2.4f, -3.8f, -2.4f, -5.9f)
                curveTo(3.7f, 7.9f, 4.5f, 5.7f, 6.1f, 4.1f)
                close()
            }
            materialPath {
                moveTo(16.7f, 14.7f)
                curveToRelative(1.3f, -1.3f, 2.0f, -3.0f, 2.0f, -4.7f)
                curveToRelative(-0.1f, -1.7f, -0.7f, -3.4f, -2.0f, -4.7f)
                lineToRelative(-1.2f, 1.2f)
                curveToRelative(1.0f, 1.0f, 1.5f, 2.3f, 1.5f, 3.5f)
                curveToRelative(0.0f, 1.3f, -0.5f, 2.6f, -1.5f, 3.5f)
                lineTo(16.7f, 14.7f)
                close()
            }
            materialPath {
                moveTo(14.5f, 10.0f)
                curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                reflectiveCurveTo(9.5f, 8.62f, 9.5f, 10.0f)
                curveToRelative(0.0f, 0.76f, 0.34f, 1.42f, 0.87f, 1.88f)
                lineTo(7.0f, 22.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(0.67f, -2.0f)
                horizontalLineToRelative(4.67f)
                lineTo(15.0f, 22.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(-3.37f, -10.12f)
                curveTo(14.16f, 11.42f, 14.5f, 10.76f, 14.5f, 10.0f)
                close()
                moveTo(10.33f, 18.0f)
                lineTo(12.0f, 13.0f)
                lineToRelative(1.67f, 5.0f)
                horizontalLineTo(10.33f)
                close()
            }
        }
        return _cellTower!!
    }

private var _cellTower: ImageVector? = null
