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

package com.vaticle.typedb.studio.module.connection

interface ConnectionConfiguration {
    companion object {
        // Someone will probably put a pipe in their username and break this.
        // We need a separator that is an invalid character in an address, username, password or file path.
        private const val INTER_CONFIG_SEPARATOR: String = ":value:"
        private const val CONFIG_SEPARATOR: String = ":config:"
        private const val CLUSTER_ID: String = "cluster"
        private const val CORE_ID: String = "core"
    }

    class Cluster(val addresses: Set<String>, val username: String, val password: String,
                  val tls: Boolean, val certPath: String): ConnectionConfiguration

    class Core(val address: String) : ConnectionConfiguration

    fun configsFromString(configsString: String): List<ConnectionConfiguration> {

    }

    fun configFromString(configString: String): ConnectionConfiguration {
        val config: List<String> = configString.split(INTER_CONFIG_SEPARATOR)

        return if (config[0] == CLUSTER_ID) {
            Cluster(config[1].split(",").toSet(), config[2], config[3], config[4].toBooleanStrictOrNull()!!, config[5])
        } else {
            Core(config[1])
        }
    }

    fun configToString(config: ConnectionConfiguration): String {
        return when (config) {
            is Cluster -> {
                listOf(CLUSTER_ID, config.addresses.joinToString(","), config.username, config.password,
                    config.tls, config.certPath).joinToString(INTER_CONFIG_SEPARATOR)
            }
            is Core -> {
                listOf(CORE_ID, config.address).joinToString(INTER_CONFIG_SEPARATOR)
            }
            else -> {
                throw RuntimeException("No such connection configuration type.")
            }
        }
    }
}