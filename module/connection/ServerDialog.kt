/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.typedb.studio.module.connection

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition.Aligned
import androidx.compose.ui.window.rememberDialogState
import com.typedb.studio.framework.common.theme.Theme
import com.typedb.studio.framework.common.theme.Theme.TOOLBAR_BUTTON_SIZE
import com.typedb.studio.framework.material.ActionableList
import com.typedb.studio.framework.material.Dialog
import com.typedb.studio.framework.material.Form
import com.typedb.studio.framework.material.Form.Checkbox
import com.typedb.studio.framework.material.Form.FIELD_HEIGHT
import com.typedb.studio.framework.material.Form.Field
import com.typedb.studio.framework.material.Form.IconButton
import com.typedb.studio.framework.material.Form.MultilineTextInput
import com.typedb.studio.framework.material.Form.RowSpacer
import com.typedb.studio.framework.material.Form.Submission
import com.typedb.studio.framework.material.Form.Text
import com.typedb.studio.framework.material.Form.TextButton
import com.typedb.studio.framework.material.Form.TextButtonArg
import com.typedb.studio.framework.material.Form.TextButtonRow
import com.typedb.studio.framework.material.Form.TextInput
import com.typedb.studio.framework.material.Form.TextInputValidated
import com.typedb.studio.framework.material.Form.toggleButtonColor
import com.typedb.studio.framework.material.Icon
import com.typedb.studio.framework.material.SelectFileDialog
import com.typedb.studio.framework.material.SelectFileDialog.SelectorOptions
import com.typedb.studio.framework.material.Tooltip
import com.typedb.studio.service.Service
import com.typedb.studio.service.common.util.ConnectionUri
import com.typedb.studio.service.common.util.ConnectionUri.ParsedCloudConnectionUri
import com.typedb.studio.service.common.util.ConnectionUri.ParsedCloudTranslatedConnectionUri
import com.typedb.studio.service.common.util.ConnectionUri.ParsedCloudUntranslatedConnectionUri
import com.typedb.studio.service.common.util.ConnectionUri.ParsedCoreConnectionUri
import com.typedb.studio.service.common.util.Label
import com.typedb.studio.service.common.util.Property
import com.typedb.studio.service.common.util.Property.Server.TYPEDB_CLOUD
import com.typedb.studio.service.common.util.Property.Server.TYPEDB_CORE
import com.typedb.studio.service.common.util.Sentence
import com.typedb.studio.service.connection.DriverState.Status.CONNECTED
import com.typedb.studio.service.connection.DriverState.Status.CONNECTING
import com.typedb.studio.service.connection.DriverState.Status.DISCONNECTED
import com.vaticle.typedb.driver.api.TypeDBCredential
import java.nio.file.Path
import androidx.compose.ui.window.DialogState as ComposeDialogState

object ServerDialog {

    private val WIDTH = 500.dp
    private val SIMPLE_HEIGHT = 240.dp
    private val ADVANCED_HEIGHT = 400.dp
    private val ADDRESS_MANAGER_WIDTH = 400.dp
    private val ADDRESS_MANAGER_HEIGHT = 500.dp
    private val appData = Service.data.connection

    private val state by mutableStateOf(ConnectServerForm())
    private var dialogState: ComposeDialogState? by mutableStateOf(null)

    private class ConnectServerForm : Form.State() {
        var server: Property.Server by mutableStateOf(appData.server ?: TYPEDB_CLOUD)
        var coreAddress: String by mutableStateOf(appData.coreAddress ?: "")
        var cloudAddresses: MutableList<String> = mutableStateListOf<String>().also {
            appData.cloudAddresses?.let { saved -> it.addAll(saved) }
        }
        var cloudTranslatedAddresses = mutableStateListOf<Pair<String, String>>().also {
            appData.cloudAddressTranslation?.let { saved -> it.addAll(saved) }
        }
        var useCloudTranslatedAddress: Boolean by mutableStateOf(appData.useCloudAddressTranslation ?: false)
        var username: String by mutableStateOf(appData.username ?: "")
        var password: String by mutableStateOf("")
        var tlsEnabled: Boolean by mutableStateOf(appData.tlsEnabled ?: true)
        var caCertificate: String by mutableStateOf(appData.caCertificate ?: "")
        var connectionUri: TextFieldValue by mutableStateOf(TextFieldValue(appData.connectionUri ?: ""))
        var advancedConfigSelected: Boolean by mutableStateOf(appData.advancedConfigSelected ?: false)

        override fun cancel() = Service.driver.connectServerDialog.close()
        override fun isValid(): Boolean = when (server) {
            TYPEDB_CORE -> addressFormatIsValid(coreAddress)
            TYPEDB_CLOUD -> username.isNotBlank() && password.isNotBlank() && if (useCloudTranslatedAddress) {
                cloudTranslatedAddresses.isNotEmpty()
                } else {
                    cloudAddresses.isNotEmpty()
                }
        }

        override fun submit() {
            when (server) {
                TYPEDB_CORE -> Service.driver.tryConnectToTypeDBCoreAsync(coreAddress) {
                    Service.driver.connectServerDialog.close()
                }
                TYPEDB_CLOUD -> {
                    val credentials = if (caCertificate.isBlank()) TypeDBCredential(username, password, tlsEnabled)
                        else TypeDBCredential(username, password, Path.of(caCertificate))
                    val onSuccess = { Service.driver.connectServerDialog.close() }
                    if (useCloudTranslatedAddress) {
                        val firstAddress = cloudTranslatedAddresses.first().first
                        val connectionName = "$username@$firstAddress"
                        Service.driver.tryConnectToTypeDBCloudAsync(
                            connectionName, cloudTranslatedAddresses.toMap(), credentials, onSuccess
                        )
                    } else {
                        val firstAddress = cloudAddresses.first()
                        val connectionName = "$username@$firstAddress"
                        Service.driver.tryConnectToTypeDBCloudAsync(
                            connectionName, cloudAddresses.toSet(), credentials, onSuccess
                        )
                    }
                }
            }
            password = ""
            appData.server = server
            appData.coreAddress = coreAddress
            appData.cloudAddresses = cloudAddresses
            appData.cloudAddressTranslation = cloudTranslatedAddresses
            appData.useCloudAddressTranslation = useCloudTranslatedAddress
            appData.username = username
            appData.tlsEnabled = tlsEnabled
            appData.caCertificate = caCertificate
            appData.connectionUri = connectionUri.text
            appData.advancedConfigSelected = advancedConfigSelected
        }
    }

    private object AddAddressForm : Form.State() {
        var server: String by mutableStateOf("")
        override fun cancel() = Service.driver.manageAddressesDialog.close()
        override fun isValid() = addressFormatIsValid(server) && !state.cloudAddresses.contains(server)

        override fun submit() {
            assert(isValid())
            state.cloudAddresses.add(server)
            syncConnectionUri()
            server = ""
        }
    }

    private object AddTranslatedAddressForm : Form.State() {
        var internalAddress: String by mutableStateOf("")
        var externalAddress: String by mutableStateOf("")

        override fun cancel() = Service.driver.manageAddressesDialog.close()

        override fun isValid() = isValidAddress(internalAddress, true) && isValidAddress(externalAddress, true)

        fun isValidAddress(
            address: String, isInternal: Boolean
        ) = addressFormatIsValid(address) && !state.cloudTranslatedAddresses.any {
            (isInternal && it.first == address) || (!isInternal && it.second == address)
        }

        override fun submit() {
            assert(isValid())
            state.cloudTranslatedAddresses.add(Pair(internalAddress, externalAddress))
            internalAddress = ""
            externalAddress = ""
        }
    }

    private fun addressFormatIsValid(address: String): Boolean {
        if (address.isBlank()) return true
        val tokens = address.split(":")
        val hostIsValid = tokens.isNotEmpty() && !tokens[0].contains(Regex("\\s"))
        val portIsValid = tokens.size > 1 && tokens[1].toIntOrNull()?.let { it in 0..65535 } == true
        return tokens.size == 2 && hostIsValid && portIsValid
    }

    @Composable
    fun MayShowDialogs() {
        if (Service.driver.connectServerDialog.isOpen) ConnectServer()
        if (Service.driver.manageAddressesDialog.isOpen) ManageCloudAddresses()
    }

    @Composable
    private fun ColumnScope.AdvancedConfigToggleButtons(state: ConnectServerForm) {
        TextButtonRow(
            height = TOOLBAR_BUTTON_SIZE,
            buttons = listOf(
                TextButtonArg(
                    text = Label.CONNECTION_URI,
                    onClick = { state.advancedConfigSelected = false; updateHeight() },
                    color = { toggleButtonColor(!state.advancedConfigSelected) },
                ),
                TextButtonArg(
                    text = Label.ADVANCED_CONFIG,
                    onClick = { state.advancedConfigSelected = true; updateHeight() },
                    color = { toggleButtonColor(state.advancedConfigSelected) },
                )
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    private fun updateHeight() {
        val currentHeight = dialogState!!.size.height
        dialogState!!.size = DpSize(
            width = dialogState!!.size.width,
            height = if (state.advancedConfigSelected && currentHeight < ADVANCED_HEIGHT) ADVANCED_HEIGHT
            else if (!state.advancedConfigSelected && currentHeight == ADVANCED_HEIGHT) SIMPLE_HEIGHT
            else currentHeight
        )
    }

    @Composable
    private fun ConnectServer() {
        if (this.dialogState == null) {
            val dialogState = rememberDialogState(
                position = Aligned(Alignment.Center),
                size = DpSize(WIDTH, if (state.advancedConfigSelected) ADVANCED_HEIGHT else SIMPLE_HEIGHT)
            )
            this.dialogState = dialogState
        }
        Dialog.Layout(
            state = Service.driver.connectServerDialog,
            title = Label.CONNECT_TO_TYPEDB,
            composeDialogState = this.dialogState!!
        ) {
            Submission(state = state, modifier = Modifier.fillMaxSize(), showButtons = false) {
                AdvancedConfigToggleButtons(state)
                Divider()
                if (state.advancedConfigSelected) {
                    ServerFormField(state)
                    if (state.server == TYPEDB_CLOUD) {
                        ManageCloudAddressesButton(state = state, shouldFocus = Service.driver.isDisconnected)
                        UsernameFormField(state)
                        PasswordFormField(state)
                        TLSEnabledFormField(state)
                        if (state.tlsEnabled) CACertificateFormField(state = state, dialogWindow = window)
                    } else if (state.server == TYPEDB_CORE) {
                        CoreAddressFormField(state, shouldFocus = Service.driver.isDisconnected)
                    }
                } else {
                    ConnectionURIFormField(state)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    ServerConnectionStatus()
                    Spacer(modifier = Modifier.weight(1f))
                    when (Service.driver.status) {
                        DISCONNECTED -> DisconnectedFormButtons(state)
                        CONNECTING -> ConnectingFormButtons()
                        CONNECTED -> ConnectedFormButtons(state)
                    }
                }
            }
        }
    }

    @Composable
    private fun ServerFormField(state: ConnectServerForm) = Field(label = Label.SERVER) {
        Form.Dropdown(
            values = Property.Server.values().toList(),
            selected = state.server,
            onSelection = { state.server = it!! },
            modifier = Modifier.fillMaxSize(),
            enabled = Service.driver.isDisconnected
        )
    }

    @Composable
    private fun CoreAddressFormField(state: ConnectServerForm, shouldFocus: Boolean) {
        var modifier = Modifier.fillMaxSize()
        val focusReq = if (shouldFocus) remember { FocusRequester() } else null
        focusReq?.let { modifier = modifier.focusRequester(focusReq) }
        Field(label = Label.ADDRESS) {
            TextInputValidated(
                value = state.coreAddress,
                placeholder = Label.DEFAULT_SERVER_ADDRESS,
                onValueChange = { state.coreAddress = it },
                enabled = Service.driver.isDisconnected,
                modifier = modifier,
                invalidWarning = Label.ADDRESS_PORT_WARNING,
                validator = { state.coreAddress.isBlank() || addressFormatIsValid(state.coreAddress) }
            )
        }
        LaunchedEffect(focusReq) { focusReq?.requestFocus() }
    }

    @Composable
    private fun ManageCloudAddressesButton(state: ConnectServerForm, shouldFocus: Boolean) {
        val focusReq = if (shouldFocus) remember { FocusRequester() } else null
        Field(label = Label.ADDRESSES) {
            val numAddresses =
                if (state.useCloudTranslatedAddress) state.cloudTranslatedAddresses.size else state.cloudAddresses.size
            TextButton(
                text = Label.MANAGE_CLOUD_ADDRESSES + " ($numAddresses)",
                focusReq = focusReq, leadingIcon = Form.IconArg(Icon.CONNECT_TO_TYPEDB),
                enabled = Service.driver.isDisconnected
            ) {
                Service.driver.manageAddressesDialog.open()
            }
        }
        LaunchedEffect(focusReq) { focusReq?.requestFocus() }
    }

    @Composable
    private fun ManageCloudAddresses() {
        @Composable
        fun ColumnSpacer() = Spacer(Modifier.height(Dialog.DIALOG_SPACING))
        val dialogState = Service.driver.manageAddressesDialog
        Dialog.Layout(dialogState, Label.MANAGE_CLOUD_ADDRESSES, ADDRESS_MANAGER_WIDTH, ADDRESS_MANAGER_HEIGHT) {
            Column(Modifier.fillMaxSize()) {
                Text(value = Sentence.MANAGE_ADDRESSES_MESSAGE, softWrap = true)
                ColumnSpacer()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(value = state.useCloudTranslatedAddress) { state.useCloudTranslatedAddress = it }
                    RowSpacer()
                    Text(value = Label.USE_ADDRESS_TRANSLATION)
                    Spacer(modifier = Modifier.weight(1f))
                }
                ColumnSpacer()
                if (state.useCloudTranslatedAddress) {
                    CloudTranslatedAddressList(Modifier.fillMaxWidth().weight(1f))
                    ColumnSpacer()
                    AddCloudTranslatedAddressForm()
                } else {
                    CloudAddressList(Modifier.fillMaxWidth().weight(1f))
                    ColumnSpacer()
                    AddCloudAddressForm()
                }
                Spacer(Modifier.height(Dialog.DIALOG_SPACING * 2))
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(modifier = Modifier.weight(1f))
                    RowSpacer()
                    TextButton(text = Label.CLOSE) { dialogState.close() }
                }
            }
        }
    }

    @Composable
    private fun AddCloudAddressForm() {
        val focusReq = remember { FocusRequester() }
        Submission(AddAddressForm, modifier = Modifier.height(Form.FIELD_HEIGHT), showButtons = false) {
            Row {
                TextInputValidated(
                    value = AddAddressForm.server,
                    placeholder = Label.DEFAULT_SERVER_ADDRESS,
                    onValueChange = { AddAddressForm.server = it.trim() },
                    modifier = Modifier.weight(1f).focusRequester(focusReq),
                    invalidWarning = Label.ADDRESS_PORT_WARNING,
                    validator = { AddAddressForm.server.isBlank() || addressFormatIsValid(AddAddressForm.server) }
                )
                RowSpacer()
                TextButton(text = Label.ADD, enabled = AddAddressForm.isValid()) { AddAddressForm.submit() }
            }
        }
        LaunchedEffect(focusReq) { focusReq.requestFocus() }
    }

    @Composable
    private fun AddCloudTranslatedAddressForm() {
        val focusReq = remember { FocusRequester() }
        val form = AddTranslatedAddressForm
        Submission(form, modifier = Modifier.height(Form.FIELD_HEIGHT), showButtons = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInputValidated(
                    value = form.externalAddress,
                    placeholder = Label.EXTERNAL_ADDRESS.lowercase(),
                    onValueChange = { form.externalAddress = it.trim() },
                    modifier = Modifier.weight(1f).focusRequester(focusReq),
                    invalidWarning = Label.ADDRESS_PORT_WARNING,
                    validator = { form.externalAddress.isBlank() || addressFormatIsValid(form.externalAddress) }
                )
                RowSpacer()
                Text(value = Label.TO.lowercase())
                RowSpacer()
                TextInputValidated(
                    value = form.internalAddress,
                    placeholder = Label.INTERNAL_ADDRESS.lowercase(),
                    onValueChange = { form.internalAddress = it.trim() },
                    modifier = Modifier.weight(1f).focusRequester(focusReq),
                    invalidWarning = Label.ADDRESS_PORT_WARNING,
                    validator = { form.internalAddress.isBlank() || addressFormatIsValid(form.internalAddress) }
                )
                RowSpacer()
                TextButton(text = Label.ADD, enabled = form.isValid()) { form.submit() }
            }
        }
        LaunchedEffect(focusReq) { focusReq.requestFocus() }
    }

    @Composable
    private fun CloudAddressList(modifier: Modifier) = ActionableList.SingleButtonLayout(
        items = state.cloudAddresses,
        itemDisplayFn = { it },
        modifier = modifier.border(1.dp, Theme.studio.border),
        buttonSide = ActionableList.Side.RIGHT,
        buttonFn = { address ->
            Form.IconButtonArg(
                icon = Icon.REMOVE,
                color = { Theme.studio.errorStroke },
                onClick = { state.cloudAddresses.remove(address) }
            )
        }
    )

    @Composable
    private fun CloudTranslatedAddressList(modifier: Modifier) = ActionableList.SingleButtonLayout(
        items = state.cloudTranslatedAddresses,
        itemDisplayFn = { "${it.first} ⇒ ${it.second}" },
        modifier = modifier.border(1.dp, Theme.studio.border),
        buttonSide = ActionableList.Side.RIGHT,
        buttonFn = { translatedAddress ->
            Form.IconButtonArg(
                icon = Icon.REMOVE,
                color = { Theme.studio.errorStroke },
                onClick = { state.cloudTranslatedAddresses.remove(translatedAddress) }
            )
        }
    )

    @Composable
    private fun UsernameFormField(state: ConnectServerForm) = Field(label = Label.USERNAME) {
        TextInput(
            value = state.username,
            placeholder = Label.USERNAME.lowercase(),
            onValueChange = { state.username = it; syncConnectionUri() },
            enabled = Service.driver.isDisconnected,
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun PasswordFormField(state: ConnectServerForm) = Field(label = Label.PASSWORD) {
        TextInput(
            value = state.password,
            placeholder = Label.PASSWORD.lowercase(),
            onValueChange = { state.password = it; syncConnectionUri() },
            enabled = Service.driver.isDisconnected,
            isPassword = true,
            modifier = Modifier.fillMaxSize(),
        )
    }

    @Composable
    private fun TLSEnabledFormField(state: ConnectServerForm) = Field(label = Label.ENABLE_TLS) {
        Checkbox(value = state.tlsEnabled, enabled = Service.driver.isDisconnected) { state.tlsEnabled = it; syncConnectionUri() }
    }

    @Composable
    private fun CACertificateFormField(
        state: ConnectServerForm, dialogWindow: ComposeDialog
    ) = Field(label = Label.CA_CERTIFICATE) {
        TextInput(
            value = state.caCertificate,
            placeholder = "${Label.PATH_TO_CA_CERTIFICATE} (${Label.OPTIONAL.lowercase()})",
            onValueChange = { state.caCertificate = it },
            enabled = Service.driver.isDisconnected,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            icon = Icon.FOLDER_OPEN,
            tooltip = Tooltip.Arg(Label.SELECT_CERTIFICATE_FILE)
        ) {
            val (selectedFilePath) = SelectFileDialog.open(
                dialogWindow, Label.SELECT_CERTIFICATE_FILE, SelectorOptions.FILES_ONLY
            )
            if (selectedFilePath != null) state.caCertificate = selectedFilePath
        }
    }

    @Composable
    private fun ConnectionURIFormField(
        state: ConnectServerForm
    ) = MultilineTextInput(
        value = state.connectionUri,
        onValueChange = {
            state.connectionUri = it
            loadConnectionUri(state.connectionUri.text)
        },
        onTextLayout = { },
        horizontalScroll = false,
        enabled = !state.advancedConfigSelected,
        modifier = Modifier.border(1.dp, Theme.studio.border, RoundedCornerShape(Theme.ROUNDED_CORNER_RADIUS))
            .height(FIELD_HEIGHT * 3),
        placeholder = ConnectionUri.PLACEHOLDER_URI,
    )

    private fun syncConnectionUri() {
        state.connectionUri = TextFieldValue(
            when (state.server) {
                TYPEDB_CORE -> ConnectionUri.buildCore(state.coreAddress)
                TYPEDB_CLOUD -> if (state.useCloudTranslatedAddress) {
                    ConnectionUri.buildCloudTranslated(state.username, state.password, state.cloudTranslatedAddresses, state.tlsEnabled)
                } else {
                    ConnectionUri.buildCloud(state.username, state.password, state.cloudAddresses, state.tlsEnabled)
                }
            }
        )
    }

    private fun loadConnectionUri(connectionUri: String) {
        ConnectionUri.parse(connectionUri)?.let { parsedConnectionUri ->
            when (parsedConnectionUri) {
                is ParsedCoreConnectionUri -> {
                    state.server = TYPEDB_CORE
                    state.coreAddress = parsedConnectionUri.address
                }
                is ParsedCloudConnectionUri -> {
                    state.server = TYPEDB_CLOUD
                    parsedConnectionUri.username?.let { state.username = it }
                    parsedConnectionUri.password?.let { state.password = it }
                    state.tlsEnabled = parsedConnectionUri.tlsEnabled ?: false
                    when (parsedConnectionUri) {
                        is ParsedCloudUntranslatedConnectionUri -> {
                            state.useCloudTranslatedAddress = false
                            state.cloudAddresses.clear()
                            state.cloudAddresses.addAll(parsedConnectionUri.addresses.filter { addressFormatIsValid(it) })
                        }
                        is ParsedCloudTranslatedConnectionUri -> {
                            state.useCloudTranslatedAddress = true
                            state.cloudTranslatedAddresses.clear()
                            state.cloudTranslatedAddresses.addAll(parsedConnectionUri.addresses.filter {
                                addressFormatIsValid(it.first) && addressFormatIsValid(it.second)
                            })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ServerConnectionStatus() {
        val statusText = "${Label.STATUS}: ${Service.driver.status.name.lowercase()}"
        Text(
            value = statusText, color = when (Service.driver.status) {
                DISCONNECTED -> Theme.studio.errorStroke
                CONNECTING -> Theme.studio.warningStroke
                CONNECTED -> Theme.studio.secondary
            }
        )
    }

    @Composable
    private fun DisconnectedFormButtons(state: ConnectServerForm) {
        TextButton(text = Label.CANCEL) { state.cancel() }
        RowSpacer()
        TextButton(text = Label.CONNECT, enabled = state.isValid()) { state.submit() }
    }

    @Composable
    private fun ConnectedFormButtons(state: ConnectServerForm) {
        val focusReq = remember { FocusRequester() }
        TextButton(
            text = Label.DISCONNECT,
            textColor = Theme.studio.errorStroke
        ) { Service.driver.closeAsync() }
        RowSpacer()
        TextButton(text = Label.CLOSE, focusReq = focusReq) { state.cancel() }
        LaunchedEffect(focusReq) { focusReq.requestFocus() }
    }

    @Composable
    private fun ConnectingFormButtons() {
        val focusReq = remember { FocusRequester() }
        TextButton(text = Label.CANCEL, focusReq = focusReq) { Service.driver.closeAsync() }
        RowSpacer()
        TextButton(text = Label.CONNECTING, enabled = false) {}
        LaunchedEffect(focusReq) { focusReq.requestFocus() }
    }
}
