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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.vaticle.typedb.studio.test.integration

import com.vaticle.typedb.client.api.TypeDBSession
import com.vaticle.typedb.studio.framework.material.Icon
import com.vaticle.typedb.studio.service.Service
import com.vaticle.typedb.studio.service.common.util.Label
import com.vaticle.typedb.studio.test.integration.common.StudioActions.Delays
import com.vaticle.typedb.studio.test.integration.common.StudioActions.clickIcon
import com.vaticle.typedb.studio.test.integration.common.StudioActions.clickText
import com.vaticle.typedb.studio.test.integration.common.StudioActions.connectToTypeDB
import com.vaticle.typedb.studio.test.integration.common.StudioActions.copyFolder
import com.vaticle.typedb.studio.test.integration.common.StudioActions.createDatabase
import com.vaticle.typedb.studio.test.integration.common.StudioActions.delayAndRecompose
import com.vaticle.typedb.studio.test.integration.common.StudioActions.openProject
import com.vaticle.typedb.studio.test.integration.common.StudioActions.verifyDataWrite
import com.vaticle.typedb.studio.test.integration.common.StudioActions.waitUntilNodeWithIconIsClickable
import com.vaticle.typedb.studio.test.integration.common.StudioActions.waitUntilNodeWithTextExists
import com.vaticle.typedb.studio.test.integration.common.StudioActions.waitUntilNodeWithTextIsClickable
import com.vaticle.typedb.studio.test.integration.common.StudioActions.waitUntilNodeWithTextNotExists
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeDataInteractively
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeSchemaInteractively
import com.vaticle.typedb.studio.test.integration.common.TypeDBRunners.withTypeDB
import com.vaticle.typedb.studio.test.integration.data.Paths.SampleFileStructure
import com.vaticle.typedb.studio.test.integration.data.Paths.SampleGitHubData
import java.io.File
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TextEditorTest : IntegrationTest() {

    @Test
    fun makeAFileAndSaveIt() {
        runBlocking {
            val path = copyFolder(source = SampleFileStructure.path, destination = testID)
            openProject(composeRule, projectDirectory = testID)

            clickIcon(composeRule, Icon.ADD)

            // This sets saveFileDialog.file!! to the current file, so even though we can't see the window it is useful.
            clickIcon(composeRule, Icon.SAVE)
            val file = File("$path/Untitled1.tql")
            Service.project.saveFileDialog.file!!.trySave(file.toPath(), true)
            Service.project.current!!.reloadEntries()
            delayAndRecompose(composeRule, Delays.FILE_IO)

            assertTrue(file.exists())
        }
    }

    @Test
    fun schemaWriteAndCommit() {
        withTypeDB { typeDB ->
            runBlocking {
                copyFolder(source = SampleGitHubData.path, destination = testID)
                openProject(composeRule, projectDirectory = testID)
                connectToTypeDB(composeRule, typeDB.address())
                createDatabase(composeRule, dbName = testID)
                writeSchemaInteractively(composeRule, dbName = testID, SampleGitHubData.schemaFile)

                Service.client.session.tryOpen(
                    database = testID,
                    TypeDBSession.Type.DATA
                )
                delayAndRecompose(composeRule, Delays.NETWORK_IO)

                Service.schema.reloadEntries()

                // We can assert that the schema has been written successfully here as the schema
                // is shown in the type browser.
                waitUntilNodeWithTextExists(composeRule, text = "commit-date")
            }
        }
    }

    @Test
    fun dataWriteAndCommit() {
        withTypeDB { typeDB ->
            runBlocking {
                copyFolder(source = SampleGitHubData.path, destination = testID)
                openProject(composeRule, projectDirectory = testID)
                connectToTypeDB(composeRule, typeDB.address())
                createDatabase(composeRule, dbName = testID)
                writeSchemaInteractively(composeRule, dbName = testID, SampleGitHubData.schemaFile)
                writeDataInteractively(composeRule, dbName = testID, SampleGitHubData.dataFile)
                verifyDataWrite(
                    composeRule,
                    typeDB.address(),
                    dbName = testID,
                    "$testID/${SampleGitHubData.collaboratorsQueryFile}"
                )
            }
        }
    }

    @Test
    fun schemaWriteAndRollback() {
        withTypeDB { typeDB ->
            runBlocking {
                copyFolder(source = SampleGitHubData.path, destination = testID)
                openProject(composeRule, projectDirectory = testID)
                connectToTypeDB(composeRule, typeDB.address())
                createDatabase(composeRule, dbName = testID)

                Service.client.session.tryOpen(testID, TypeDBSession.Type.SCHEMA)
                delayAndRecompose(composeRule, Delays.NETWORK_IO)

                waitUntilNodeWithTextIsClickable(composeRule, Label.SCHEMA.lowercase())
                clickText(composeRule, Label.SCHEMA.lowercase())
                waitUntilNodeWithTextIsClickable(composeRule, Label.WRITE.lowercase())
                clickText(composeRule, Label.WRITE.lowercase())

                Service.project.current!!.directory.entries.find { it.name == SampleGitHubData.schemaFile }!!
                    .asFile().tryOpen()

                waitUntilNodeWithIconIsClickable(composeRule, Icon.RUN)
                clickIcon(composeRule, Icon.RUN)
                waitUntilNodeWithIconIsClickable(composeRule, Icon.ROLLBACK)
                clickIcon(composeRule, Icon.ROLLBACK)

                waitUntilNodeWithTextNotExists(composeRule, text = "repo-id")
            }
        }
    }
}