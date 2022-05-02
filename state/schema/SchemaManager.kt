/*
 * Copyright (C) 2021 Vaticle
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

package com.vaticle.typedb.studio.state.schema

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaticle.typedb.client.api.TypeDBOptions
import com.vaticle.typedb.client.api.TypeDBTransaction
import com.vaticle.typedb.studio.state.app.NotificationManager
import com.vaticle.typedb.studio.state.connection.SessionState
import com.vaticle.typedb.studio.state.connection.TransactionState.Companion.ONE_HOUR_IN_MILLS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTime::class)
class SchemaManager(private val session: SessionState, internal val notificationMgr: NotificationManager) {

    var root: TypeState? by mutableStateOf(null); private set
    var onRootChange: ((TypeState) -> Unit)? = null
    val hasWriteTx: Boolean get() = session.isSchema && session.transaction.isWrite
    private var readTx: AtomicReference<TypeDBTransaction> = AtomicReference()
    private val lastTransactionUse = AtomicLong(0)
    private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

    companion object {
        private val TX_IDLE_TIME = Duration.seconds(16)
    }

    init {
        session.onOpen { mayUpdateRoot() }
        session.transaction.onSchemaWrite {
            resetReadTx()
            mayUpdateRoot()
        }
        session.onClose {
            root?.close()
            root = null
            closeReadTx()
        }
    }

    private fun mayUpdateRoot() {
        session.transaction()?.let { tx ->
            root = TypeState(tx.concepts().rootThingType, null, this, true)
            onRootChange?.let { it(root!!) }
        }
    }

    fun exportTypeSchema(onSuccess: (String) -> Unit) = coroutineScope.launch {
        session.database?.typeSchema()?.let { onSuccess(it) }
    }

    internal fun openOrGetReadTx(): TypeDBTransaction {
        synchronized(this) {
            lastTransactionUse.set(System.currentTimeMillis())
            val options = TypeDBOptions.core().transactionTimeoutMillis(ONE_HOUR_IN_MILLS)
            if (readTx.compareAndSet(null, session.transaction(options = options))) {
                readTx.get().onClose { closeReadTx() }
                coroutineScope.launch {
                    var duration = TX_IDLE_TIME
                    while (true) {
                        delay(duration)
                        val sinceLastTx = System.currentTimeMillis() - lastTransactionUse.get()
                        if (sinceLastTx >= TX_IDLE_TIME.inWholeMilliseconds) {
                            closeReadTx()
                            break
                        } else duration = TX_IDLE_TIME - Duration.milliseconds(sinceLastTx)
                    }
                }
            }
            return readTx.get()
        }
    }

    fun resetReadTx() {
        synchronized(this) {
            if (readTx.get() != null) {
                closeReadTx()
                openOrGetReadTx()
            }
        }
    }

    private fun closeReadTx() {
        synchronized(this) { readTx.getAndSet(null)?.close() }
    }
}