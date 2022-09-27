/*
 * Copyright (C) 2022 Vaticle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.vaticle.typedb.studio.module.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaticle.typedb.common.collection.Either
import com.vaticle.typedb.studio.framework.common.theme.Color
import com.vaticle.typedb.studio.framework.common.theme.Theme
import com.vaticle.typedb.studio.framework.material.Dialog
import com.vaticle.typedb.studio.framework.material.Form
import com.vaticle.typedb.studio.framework.material.Form.CaptionSpacer
import com.vaticle.typedb.studio.framework.material.Form.State
import com.vaticle.typedb.studio.framework.material.Form.RowSpacer
import com.vaticle.typedb.studio.framework.material.Form.ColumnSpacer
import com.vaticle.typedb.studio.framework.material.Form.Field
import com.vaticle.typedb.studio.framework.material.Form.Checkbox
import com.vaticle.typedb.studio.framework.material.Form.TextInput
import com.vaticle.typedb.studio.framework.material.Form.TextButton
import com.vaticle.typedb.studio.framework.material.Form.Text
import com.vaticle.typedb.studio.framework.material.Frame
import com.vaticle.typedb.studio.framework.material.Navigator
import com.vaticle.typedb.studio.framework.material.Navigator.rememberNavigatorState
import com.vaticle.typedb.studio.framework.material.Separator
import com.vaticle.typedb.studio.state.StudioState
import com.vaticle.typedb.studio.state.common.util.Label.APPLY
import com.vaticle.typedb.studio.state.common.util.Label.CANCEL
import com.vaticle.typedb.studio.state.common.util.Label.ENABLE_EDITOR_AUTOSAVE
import com.vaticle.typedb.studio.state.common.util.Label.ENABLE_GRAPH_OUTPUT
import com.vaticle.typedb.studio.state.common.util.Label.GRAPH_VISUALISER
import com.vaticle.typedb.studio.state.common.util.Label.MANAGE_PREFERENCES
import com.vaticle.typedb.studio.state.common.util.Label.OK
import com.vaticle.typedb.studio.state.common.util.Label.PROJECT_IGNORED_PATHS
import com.vaticle.typedb.studio.state.common.util.Label.PROJECT_MANAGER
import com.vaticle.typedb.studio.state.common.util.Label.QUERY
import com.vaticle.typedb.studio.state.common.util.Label.QUERY_RUNNER
import com.vaticle.typedb.studio.state.common.util.Label.SET_QUERY_LIMIT
import com.vaticle.typedb.studio.state.common.util.Label.TEXT_EDITOR
import com.vaticle.typedb.studio.state.common.util.Sentence.PREFERENCES_GRAPH_OUTPUT_CAPTION
import com.vaticle.typedb.studio.state.common.util.Sentence.PREFERENCES_MATCH_QUERY_LIMIT_CAPTION
import com.vaticle.typedb.studio.state.page.Navigable
import java.util.Optional

object PreferenceDialog {
    private val WIDTH = 800.dp
    private val HEIGHT = 600.dp
    private val NAVIGATOR_INIT_SIZE = 200.dp
    private val NAVIGATOR_MIN_SIZE = 150.dp
    private val PREFERENCE_GROUP_INIT_SIZE = 600.dp
    private val PREFERENCE_GROUP_MIN_SIZE = 500.dp

    private val preferenceMgr = StudioState.preference
    private var focusedPreferenceGroup by mutableStateOf(PreferenceGroup(""))
    private var state by mutableStateOf(PreferencesForm())

    sealed interface PreferenceField {
        val label: String
        val caption: Optional<String>
        fun isValid(): Boolean
        @Composable fun Display()

        @Composable
        fun Layout(fieldContent: @Composable () -> Unit) {
            Field(label) {
                fieldContent()
            }
            Caption()
        }

        @Composable
        fun Caption() {
            if (caption.isPresent) {
                CaptionText(caption.get())
            }
        }

        class TextInput(initialValue: String,
                        override val label: String, override val caption: Optional<String> = Optional.empty(),
                        private var placeholder: String,
                        var validator: (String) -> Boolean = { true }) : PreferenceField {

            var value by mutableStateOf(initialValue)

            override fun isValid(): Boolean {
                return validator(value)
            }

            @Composable
            override fun Display() {
                Layout {
                    var border = Form.Border(1.dp, RoundedCornerShape(Theme.ROUNDED_CORNER_RADIUS)) {
                        if (this.isValid()) Theme.studio.border else Theme.studio.errorStroke
                    }
                    TextInput(
                        value = value,
                        placeholder = placeholder,
                        border = border,
                        onValueChange = { value = it; state.modified = true }
                    )
                }
            }
        }

        class Checkbox(initialValue: Boolean, override var label: String,
                       override val caption: Optional<String> = Optional.empty()) : PreferenceField {

            var value by mutableStateOf(initialValue)

            @Composable
            override fun Display() {
                Layout {
                    Checkbox(
                        value = value,
                        onChange = { value = it; state.modified = true }
                    )
                }
            }

            override fun isValid(): Boolean {
                return true
            }
        }

        class Dropdown<T : Any>(val values: List<T>, override val label: String,
                                override val caption: Optional<String> = Optional.empty()) : PreferenceField {

            private var selected by mutableStateOf(values.first())

            @Composable
            override fun Display() {
                Layout {
                    Form.Dropdown(
                        values = values,
                        selected = selected,
                        onSelection = { selected = it; state.modified = true }
                    )
                }
            }

            override fun isValid(): Boolean {
                return true
            }
        }
    }

    class PreferencesForm : State {
        private val preferenceGroups: List<PreferenceGroup> = listOf(
            PreferenceGroup().GraphVisualiser(),
            PreferenceGroup().TextEditor(),
            PreferenceGroup().ProjectManager(),
            PreferenceGroup().QueryRunner()
        )
        var modified by mutableStateOf(false)
        val rootPreferenceGroup = PreferenceGroup(entries = preferenceGroups)

        override fun cancel() {
            StudioState.preference.preferencesDialog.close()
        }

        fun apply() {
            if (isValid()) {
                trySubmit()
                modified = false
            }
        }

        fun ok() {
            apply()
            cancel()
        }

        override fun isValid(): Boolean {
            return rootPreferenceGroup.isValid()
        }

        override fun trySubmit() {
            preferenceGroups.forEach { it.submit() }
        }
    }

    open class PreferenceGroup(
        override val name: String = "",
        override val entries: List<PreferenceGroup> = emptyList(),
        open val preferences: List<PreferenceField> = emptyList(),
    ) : Navigable<PreferenceGroup> {

        override var parent: Navigable<PreferenceGroup>? = null
        override val info: String? = null
        override val isExpandable = entries.isNotEmpty()
        override val isBulkExpandable = entries.isNotEmpty()

        open val submit = {}

        override fun reloadEntries() {}

        override fun compareTo(other: Navigable<PreferenceGroup>): Int {
            return this.name.compareTo(other.name);
        }

        fun isValid(): Boolean {
            return preferences.all { it.isValid() } &&
                entries.all { it.isValid() }
        }

        @Composable
        fun Display() {
            PreferencesHeader(name)
            preferences.forEach { it.Display() }
        }

        inner class GraphVisualiser : PreferenceGroup(GRAPH_VISUALISER) {
            var graphOutput = PreferenceField.Checkbox(
                initialValue = preferenceMgr.graphOutputEnabled, label = ENABLE_GRAPH_OUTPUT,
                caption = Optional.of(PREFERENCES_GRAPH_OUTPUT_CAPTION)
            )

            override val preferences: List<PreferenceField> = listOf(graphOutput)

            override val submit = { preferenceMgr.graphOutputEnabled = graphOutput.value }
        }

        inner class TextEditor : PreferenceGroup(TEXT_EDITOR) {
            var autoSave = PreferenceField.Checkbox(
                initialValue = preferenceMgr.autoSave, label = ENABLE_EDITOR_AUTOSAVE
            )
            override val preferences: List<PreferenceField> = listOf(autoSave)

            override val submit = { preferenceMgr.autoSave = autoSave.value }
        }

        inner class ProjectManager : PreferenceGroup(PROJECT_MANAGER) {
            private val IGNORED_PATHS_PLACEHOLDER = ".git"

            private val ignoredPathsString = preferenceMgr.ignoredPaths.joinToString(", ")
            var ignoredPaths = PreferenceField.TextInput(
                initialValue = ignoredPathsString,
                label = PROJECT_IGNORED_PATHS, placeholder = IGNORED_PATHS_PLACEHOLDER
            )

            override val preferences: List<PreferenceField> = listOf(ignoredPaths)

            override val submit = { preferenceMgr.ignoredPaths = ignoredPaths.value.split(',').map { it.trim() }}

        }

        inner class QueryRunner : PreferenceGroup(QUERY_RUNNER) {
            private val QUERY_LIMIT_PLACEHOLDER = "1000"

            var matchQueryLimit = PreferenceField.TextInput(
                initialValue = preferenceMgr.matchQueryLimit.toString(),
                label = SET_QUERY_LIMIT, placeholder = QUERY_LIMIT_PLACEHOLDER,
                caption = Optional.of(PREFERENCES_MATCH_QUERY_LIMIT_CAPTION)
            ) {/* validator = */ it.toLongOrNull() != null }

            override val preferences: List<PreferenceField> = listOf(matchQueryLimit)

            override val submit = { preferenceMgr.matchQueryLimit = matchQueryLimit.value.toLong()}
        }
    }

    @Composable
    private fun NavigatorLayout(state: PreferencesForm) {
        focusedPreferenceGroup = state.rootPreferenceGroup.entries.first()

        val navState = rememberNavigatorState(
            container = state.rootPreferenceGroup,
            title = MANAGE_PREFERENCES,
            behaviour = Navigator.Behaviour.Browser(clicksToOpenItem = 1),
            initExpandDepth = 0,
            openFn = { focusedPreferenceGroup = it.item }
        )

        Navigator.Layout(
            state = navState,
            modifier = Modifier.fillMaxSize(),
        )

        LaunchedEffect(navState) { navState.launch() }
    }

    @Composable
    fun MayShowDialogs() {
        if (StudioState.preference.preferencesDialog.isOpen) Preferences()
    }

    @Composable
    private fun Preferences() {
        state = remember { PreferencesForm() }

        Dialog.Layout(StudioState.preference.preferencesDialog, MANAGE_PREFERENCES, WIDTH, HEIGHT, padding = 0.dp) {
            Column {
                Frame.Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    separator = Frame.SeparatorArgs(Separator.WEIGHT),
                    Frame.Pane(
                        id = PreferenceDialog.javaClass.canonicalName + ".primary",
                        initSize = Either.first(NAVIGATOR_INIT_SIZE), minSize = NAVIGATOR_MIN_SIZE
                    ) {
                        Column(modifier = Modifier.fillMaxSize().background(Theme.studio.backgroundLight)) {
                            ColumnSpacer()
                            NavigatorLayout(state)
                        }
                    },
                    Frame.Pane(
                        id = PreferenceDialog.javaClass.canonicalName + ".secondary",
                        initSize = Either.first(PREFERENCE_GROUP_INIT_SIZE), minSize = PREFERENCE_GROUP_MIN_SIZE
                    ) {
                        Column(modifier = Modifier.fillMaxHeight().padding(10.dp)) {
                            focusedPreferenceGroup.Display()
                        }
                    }
                )
                Separator.Horizontal()
                ColumnSpacer()
                Row {
                    Column() {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ChangeFormButtons(state)
                        }
                    }
                }
                ColumnSpacer()
            }
        }
    }

    @Composable
    private fun PreferencesHeader(text: String) {
        Text(text, fontWeight = FontWeight.Bold)
        SpacedHorizontalSeparator()
    }

    @Composable
    private fun ChangeFormButtons(state: PreferencesForm) {
        TextButton(CANCEL) {
            state.cancel()
        }
        RowSpacer()
        TextButton(APPLY, enabled = state.modified) {
            state.apply()
        }
        RowSpacer()
        TextButton(OK) {
            state.ok()
        }
        RowSpacer()
        RowSpacer()
    }

    @Composable
    private fun SpacedHorizontalSeparator() {
        ColumnSpacer()
        Separator.Horizontal()
        ColumnSpacer()
    }

    @Composable
    private fun CaptionText(text: String) {
        CaptionSpacer()
        Row {
            RowSpacer()
            Text(text, alpha = Color.FADED_OPACITY)
        }
    }
}