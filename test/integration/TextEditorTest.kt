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

// We need to access the private function StudioState.client.session.tryOpen, this allows us to.
// Do not use this outside of tests anywhere. It is extremely dangerous to do so.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.vaticle.typedb.studio.test.integration

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaticle.typedb.client.api.TypeDBSession
import com.vaticle.typedb.client.api.TypeDBTransaction
import com.vaticle.typedb.studio.state.StudioState
import com.vaticle.typedb.studio.test.integration.common.Data.CHEVRON_UP_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.DATA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.PLAY_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.PLUS_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.QUERY_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.ROLLBACK_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.SAMPLE_DATA_PATH
import com.vaticle.typedb.studio.test.integration.common.Data.SAVE_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.SCHEMA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.TQL_DATA_PATH
import com.vaticle.typedb.studio.test.integration.common.Delays.FILE_IO
import com.vaticle.typedb.studio.test.integration.common.Delays.NETWORK_IO
import com.vaticle.typedb.studio.test.integration.common.StudioActions.cloneAndOpenProject
import com.vaticle.typedb.studio.test.integration.common.StudioActions.connectToTypeDB
import com.vaticle.typedb.studio.test.integration.common.StudioActions.createDatabase
import com.vaticle.typedb.studio.test.integration.common.StudioActions.delayAndRecompose
import com.vaticle.typedb.studio.test.integration.common.StudioActions.verifyDataWrite
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeDataInteractively
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeSchemaInteractively
import com.vaticle.typedb.studio.test.integration.common.StudioTestHelpers.studioTest
import com.vaticle.typedb.studio.test.integration.common.StudioTestHelpers.studioTestWithRunner
import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class TextEditorTest: IntegrationTest() {

    @Test
    fun makeAFileAndSaveIt() {
        studioTest(composeRule) {
        // We have to open a project to enable the '+' to create a new file.
            val path = cloneAndOpenProject(composeRule, source = SAMPLE_DATA_PATH, destination = testID)

            composeRule.onNodeWithText(PLUS_ICON_STRING).performClick()
            delayAndRecompose(composeRule)

            // This sets saveFileDialog.file!! to the current file, so even though we can't see the window it is useful.
            composeRule.onNodeWithText(SAVE_ICON_STRING).performClick()
            val file = File("$path/Untitled1.tql")
            StudioState.project.saveFileDialog.file!!.trySave(file.toPath(), true)
            StudioState.project.current!!.reloadEntries()
            delayAndRecompose(composeRule, FILE_IO)

            assertTrue(file.exists())
        }
    }

    @Test
    fun schemaWriteAndCommit() {
        studioTestWithRunner(composeRule) { address ->
        // We have to open a project to enable the '+' to create a new file.
            cloneAndOpenProject(composeRule, source = TQL_DATA_PATH, destination = testID)
            connectToTypeDB(composeRule, address)
            createDatabase(composeRule, dbName = testID)
            writeSchemaInteractively(composeRule, dbName = testID, SCHEMA_FILE_NAME)

            StudioState.client.session.tryOpen(database = testID, TypeDBSession.Type.DATA)
            delayAndRecompose(composeRule, NETWORK_IO)

            composeRule.onNodeWithText(CHEVRON_UP_ICON_STRING).performClick()
            delayAndRecompose(composeRule)

            // We can assert that the schema has been written successfully here as the schema
            // is shown in the type browser.
            composeRule.onNodeWithText("commit-date").assertExists()
        }
    }

    @Test
    fun dataWriteAndCommit() {
        studioTestWithRunner(composeRule) { address ->
        cloneAndOpenProject(composeRule, source = TQL_DATA_PATH, destination = testID)
            connectToTypeDB(composeRule, address)
            createDatabase(composeRule, dbName = testID)
            writeSchemaInteractively(composeRule, dbName = testID, SCHEMA_FILE_NAME)
            writeDataInteractively(composeRule, dbName = testID, DATA_FILE_NAME)
            verifyDataWrite(composeRule, address, dbName = testID, "$testID/${QUERY_FILE_NAME}")
        }
    }

    @Test
    fun schemaWriteAndRollback() {
        studioTestWithRunner(composeRule) { address ->
            cloneAndOpenProject(composeRule, source = TQL_DATA_PATH, destination = testID)
            connectToTypeDB(composeRule, address)
            createDatabase(composeRule, dbName = testID)

            StudioState.client.session.tryOpen(testID, TypeDBSession.Type.SCHEMA)
            delayAndRecompose(composeRule, NETWORK_IO)

            composeRule.onNodeWithText(TypeDBSession.Type.SCHEMA.name.lowercase()).performClick()
            composeRule.onNodeWithText(TypeDBTransaction.Type.WRITE.name.lowercase()).performClick()

            StudioState.project.current!!.directory.entries.find { it.name == SCHEMA_FILE_NAME }!!.asFile().tryOpen()

            composeRule.onNodeWithText(PLAY_ICON_STRING).performClick()
            delayAndRecompose(composeRule, NETWORK_IO)
            composeRule.onNodeWithText(ROLLBACK_ICON_STRING).performClick()
            delayAndRecompose(composeRule, NETWORK_IO)

            composeRule.onNodeWithText("repo-id").assertDoesNotExist()
        }
    }
}