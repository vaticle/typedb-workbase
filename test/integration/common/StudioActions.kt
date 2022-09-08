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

// We need to access private function StudioState.client.session.tryOpen, this allows us to.
// Do not use this outside of tests anywhere. It is extremely dangerous to do so.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.vaticle.typedb.studio.test.integration.common

import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaticle.typedb.client.TypeDB
import com.vaticle.typedb.client.api.TypeDBOptions
import com.vaticle.typedb.client.api.TypeDBSession
import com.vaticle.typedb.client.api.TypeDBTransaction
import com.vaticle.typedb.studio.state.StudioState
import com.vaticle.typedb.studio.state.common.util.Label
import com.vaticle.typedb.studio.test.integration.common.Data.CHECK_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.FAIL_CONNECT_TYPEDB
import com.vaticle.typedb.studio.test.integration.common.Data.FAIL_CREATE_DATABASE
import com.vaticle.typedb.studio.test.integration.common.Data.FAIL_DATA_WRITE
import com.vaticle.typedb.studio.test.integration.common.Data.FAIL_SCHEMA_WRITE
import com.vaticle.typedb.studio.test.integration.common.Data.PLAY_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Data.PLUS_ICON_STRING
import com.vaticle.typedb.studio.test.integration.common.Delays.CONNECT_SERVER
import com.vaticle.typedb.studio.test.integration.common.Delays.NETWORK_IO
import com.vaticle.typedb.studio.test.integration.common.Delays.RECOMPOSE
import com.vaticle.typedb.studio.state.common.util.Message
import com.vaticle.typeql.lang.TypeQL
import com.vaticle.typeql.lang.query.TypeQLMatch
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.delay

object StudioActions {
    /// Wait `timeMillis` milliseconds, then wait for all recompositions to finish.
    suspend fun delayAndRecompose(composeRule: ComposeContentTestRule, timeMillis: Int = RECOMPOSE) {
        delay(timeMillis.toLong())
        composeRule.waitForIdle()
    }

    suspend fun waitForConditionAndRecompose(
        context: ComposeContentTestRule,
        failMessage: String,
        beforeRetry: (() -> Unit) = {},
        successCondition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!successCondition() && System.currentTimeMillis() < deadline) {
            beforeRetry()
            delayAndRecompose(context, 500)
        }
        if (!successCondition()) {
            fail(failMessage)
        }
    }

    fun cloneAndOpenProject(composeRule: ComposeContentTestRule, source: String, destination: String): Path {
        val absolute = File(File(destination).absolutePath)

        absolute.deleteRecursively()
        File(source).copyRecursively(overwrite = true, target = absolute)

        StudioState.project.tryOpenProject(absolute.toPath())

        composeRule.waitForIdle()
        return absolute.toPath()
    }

    suspend fun connectToTypeDB(composeRule: ComposeContentTestRule, address: String) {
        // This opens a dialog box (which we can't see through) so we assert that buttons with that text can be
        // clicked.
        composeRule.onAllNodesWithText(Label.CONNECT_TO_TYPEDB).assertAll(hasClickAction())

        StudioState.client.tryConnectToTypeDB(address) {}
        delayAndRecompose(composeRule, CONNECT_SERVER)

        waitForConditionAndRecompose(composeRule, FAIL_CONNECT_TYPEDB) { StudioState.client.isConnected }

        composeRule.onNodeWithText(address).assertExists()
    }

    suspend fun createDatabase(composeRule: ComposeContentTestRule, dbName: String) {
        composeRule.onAllNodesWithText(Label.SELECT_DATABASE).assertAll(hasClickAction())

        StudioState.client.tryDeleteDatabase(dbName)
        delayAndRecompose(composeRule, NETWORK_IO)

        StudioState.client.tryCreateDatabase(dbName) {}
        delayAndRecompose(composeRule, NETWORK_IO)

        StudioState.client.refreshDatabaseList()

        waitForConditionAndRecompose(
            context = composeRule,
            failMessage = FAIL_CREATE_DATABASE,
            beforeRetry = { StudioState.client.refreshDatabaseList() }
        ) { StudioState.client.databaseList.contains(dbName) }
    }

    suspend fun writeSchemaInteractively(composeRule: ComposeContentTestRule, dbName: String, schemaFileName: String) {
        StudioState.notification.dismissAll()

        composeRule.onNodeWithText(PLUS_ICON_STRING).performClick()
        delayAndRecompose(composeRule)

        StudioState.client.session.tryOpen(dbName, TypeDBSession.Type.SCHEMA)
        delayAndRecompose(composeRule, NETWORK_IO)

        StudioState.client.tryUpdateTransactionType(TypeDBTransaction.Type.WRITE)
        delayAndRecompose(composeRule, NETWORK_IO)

        composeRule.onNodeWithText(TypeDBSession.Type.SCHEMA.name.lowercase()).performClick()
        composeRule.onNodeWithText(TypeDBTransaction.Type.WRITE.name.lowercase()).performClick()

        StudioState.project.current!!.directory.entries.find { it.name == schemaFileName }!!.asFile().tryOpen()

        composeRule.onNodeWithText(PLAY_ICON_STRING).performClick()
        delayAndRecompose(composeRule, NETWORK_IO)

        composeRule.onNodeWithText(CHECK_ICON_STRING).performClick()
        delayAndRecompose(composeRule, NETWORK_IO)

        waitForConditionAndRecompose(composeRule, FAIL_SCHEMA_WRITE) {
            StudioState.notification.queue.last().code == Message.Connection.TRANSACTION_COMMIT_SUCCESSFULLY.code()
        }
    }

    suspend fun writeDataInteractively(composeRule: ComposeContentTestRule, dbName: String, dataFileName: String) {
        StudioState.notification.dismissAll()
        delayAndRecompose(composeRule)

        StudioState.client.session.tryOpen(dbName, TypeDBSession.Type.DATA)
        delayAndRecompose(composeRule, NETWORK_IO)

        composeRule.onNodeWithText(TypeDBSession.Type.DATA.name.lowercase()).performClick()
        composeRule.onNodeWithText(TypeDBTransaction.Type.WRITE.name.lowercase()).performClick()

        StudioState.project.current!!.directory.entries.find { it.name == dataFileName }!!.asFile().tryOpen()

        composeRule.onNodeWithText(PLAY_ICON_STRING).performClick()
        delayAndRecompose(composeRule, NETWORK_IO)

        composeRule.onNodeWithText(CHECK_ICON_STRING).performClick()
        delayAndRecompose(composeRule, NETWORK_IO)

        waitForConditionAndRecompose(composeRule, FAIL_DATA_WRITE) {
            StudioState.notification.queue.last().code == Message.Connection.TRANSACTION_COMMIT_SUCCESSFULLY.code()
        }
    }

    suspend fun verifyDataWrite(composeRule: ComposeContentTestRule, address: String, dbName: String, queryFileName: String) {
        val queryString = Files.readAllLines(Paths.get(queryFileName), StandardCharsets.UTF_8)
            .filter { line -> !line.startsWith('#') }
            .joinToString("")

        composeRule.onNodeWithText(Label.INFER.lowercase()).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TypeDBTransaction.Type.READ.name.lowercase()).performClick()
        delayAndRecompose(composeRule)

        TypeDB.coreClient(address).use { client ->
            client.session(dbName, TypeDBSession.Type.DATA, TypeDBOptions.core().infer(true)).use { session ->
                session.transaction(TypeDBTransaction.Type.READ).use { transaction ->
                    val results = ArrayList<String>()
                    val query = TypeQL.parseQuery<TypeQLMatch>(queryString)
                    transaction.query().match(query).forEach { result ->
                        results.add(
                            result.get("user-name").asAttribute().value.toString()
                        )
                    }
                    assertEquals(2, results.size)
                    assertTrue(results.contains("jmsfltchr"))
                    assertTrue(results.contains("krishnangovindraj"))
                }
            }
        }
    }
}