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

package com.vaticle.typedb.studio.test.integration

import com.vaticle.typedb.studio.test.integration.common.Data.DATA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.QUERY_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.SCHEMA_FILE_NAME
import com.vaticle.typedb.studio.test.integration.common.Data.TQL_DATA_PATH
import com.vaticle.typedb.studio.test.integration.common.StudioActions.cloneAndOpenProject
import com.vaticle.typedb.studio.test.integration.common.StudioActions.connectToTypeDB
import com.vaticle.typedb.studio.test.integration.common.StudioActions.createDatabase
import com.vaticle.typedb.studio.test.integration.common.StudioActions.verifyDataWrite
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeDataInteractively
import com.vaticle.typedb.studio.test.integration.common.StudioActions.writeSchemaInteractively
import com.vaticle.typedb.studio.test.integration.common.StudioTestHelpers.withTypeDB
import kotlinx.coroutines.runBlocking
import org.junit.Test

class QuickstartTest: IntegrationTest() {

    @Test
    fun Quickstart() {
        withTypeDB {address ->
            runBlocking {
                connectToTypeDB(composeRule, address)
                createDatabase(composeRule, dbName = testID)
                cloneAndOpenProject(composeRule, source = TQL_DATA_PATH, destination = testID)
                writeSchemaInteractively(composeRule, dbName = testID, SCHEMA_FILE_NAME)
                writeDataInteractively(composeRule, dbName = testID, DATA_FILE_NAME)
                verifyDataWrite(composeRule, address, dbName = testID, "$testID/${QUERY_FILE_NAME}")
            }
        }
    }
}