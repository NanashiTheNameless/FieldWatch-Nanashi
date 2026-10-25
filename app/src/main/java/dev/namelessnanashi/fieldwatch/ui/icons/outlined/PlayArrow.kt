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
 * Vendored from androidx.compose.material:material-icons-core 1.7.8 so the app
 * ships only the icons it uses.
 */

package dev.namelessnanashi.fieldwatch.ui.icons.outlined

import dev.namelessnanashi.fieldwatch.ui.icons.Icons
import dev.namelessnanashi.fieldwatch.ui.icons.materialIcon
import dev.namelessnanashi.fieldwatch.ui.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.Outlined.PlayArrow: ImageVector
    get() {
        if (_playArrow != null) {
            return _playArrow!!
        }
        _playArrow = materialIcon(name = "Outlined.PlayArrow") {
            materialPath {
                moveTo(10.0f, 8.64f)
                lineTo(15.27f, 12.0f)
                lineTo(10.0f, 15.36f)
                verticalLineTo(8.64f)
                moveTo(8.0f, 5.0f)
                verticalLineToRelative(14.0f)
                lineToRelative(11.0f, -7.0f)
                lineTo(8.0f, 5.0f)
                close()
            }
        }
        return _playArrow!!
    }

private var _playArrow: ImageVector? = null
