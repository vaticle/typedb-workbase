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

// We need to access private function Studio.MainWindow, this allows us to.
// Do not use this outside of tests anywhere. It is extremely dangerous to do so.
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.vaticle.typedb.studio.test.integration

import com.vaticle.typedb.studio.test.integration.Utils.studioTestWithRunner
import com.vaticle.typedb.studio.test.integration.Utils.connectToTypeDB
import com.vaticle.typedb.studio.test.integration.Utils.createDatabase
import com.vaticle.typedb.studio.test.integration.Utils.cloneAndOpenProject
import com.vaticle.typedb.studio.test.integration.Utils.writeSchemaInteractively
import com.vaticle.typedb.studio.test.integration.Utils.writeDataInteractively
import com.vaticle.typedb.studio.test.integration.Utils.verifyDataWrite
import com.vaticle.typedb.studio.test.integration.Utils.SCHEMA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.Utils.DATA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.Utils.QUERY_FILE_NAME
import com.vaticle.typedb.studio.test.integration.Utils.TQL_DATA_PATH
import org.junit.Test

/**
 * Some of these tests use delay!
 *
 * The rationale for this is that substituting in stub classes/methods would create a lot of friction from release to
 * release as the tests would require updating to completely reflect all the internal state that changes with each
 * function. As a heavily state-driven application, duplicating all of this functionality and accurately verifying that
 * the duplicate is like-for-like is out of scope.
 *
 * The delays are:
 *  - used only when necessary (some data is travelling between the test and TypeDB)
 *  - generous with the amount of time for the required action.
 *
 * However, this is a source of non-determinism and a better and easier way may emerge.
 */
class QuickstartTest: IntegrationTest() {

    @Test
    fun Quickstart() {
        studioTestWithRunner(composeRule) { address ->
            connectToTypeDB(composeRule, address)
            createDatabase(composeRule, dbName = testID)
            cloneAndOpenProject(composeRule, source = TQL_DATA_PATH, destination = testID)
            writeSchemaInteractively(composeRule, dbName = testID, SCHEMA_FILE_NAME)
            writeDataInteractively(composeRule, dbName = testID, DATA_FILE_NAME)
            verifyDataWrite(composeRule, address, dbName = testID, "$testID/${QUERY_FILE_NAME}")
        }
    }
}