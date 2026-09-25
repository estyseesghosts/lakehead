package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Minimal home placeholder shell.
 *
 * @param onRequestDefaultHome invoked when the user activates the control that asks to make
 * ProtoLaunch the default home launcher. Platform role mechanics stay with the caller.
 */
@Composable
fun HomePlaceholder(onRequestDefaultHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("ProtoLaunch")
        BasicText(
            text = "Set ProtoLaunch as default home",
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onRequestDefaultHome)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}
