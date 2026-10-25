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

val Icons.Outlined.LocalPolice: ImageVector
    get() {
        if (_localPolice != null) {
            return _localPolice!!
        }
        _localPolice = materialIcon(name = "Outlined.LocalPolice") {
            materialPath {
                moveTo(14.5f, 12.59f)
                lineToRelative(0.9f, 3.88f)
                lineTo(12.0f, 14.42f)
                lineToRelative(-3.4f, 2.05f)
                lineToRelative(0.9f, -3.87f)
                lineToRelative(-3.0f, -2.59f)
                lineToRelative(3.96f, -0.34f)
                lineTo(12.0f, 6.02f)
                lineToRelative(1.54f, 3.64f)
                lineTo(17.5f, 10.0f)
                lineTo(14.5f, 12.59f)
                close()
                moveTo(12.0f, 3.19f)
                lineToRelative(7.0f, 3.11f)
                verticalLineTo(11.0f)
                curveToRelative(0.0f, 4.52f, -2.98f, 8.69f, -7.0f, 9.93f)
                curveTo(7.98f, 19.69f, 5.0f, 15.52f, 5.0f, 11.0f)
                verticalLineTo(6.3f)
                lineTo(12.0f, 3.19f)
                moveTo(12.0f, 1.0f)
                lineTo(3.0f, 5.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(5.0f)
                lineTo(12.0f, 1.0f)
                lineTo(12.0f, 1.0f)
                close()
            }
        }
        return _localPolice!!
    }

private var _localPolice: ImageVector? = null
