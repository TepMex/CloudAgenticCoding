package com.tepmex.inbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tepmex.inbox.R
import com.tepmex.inbox.domain.CenterAction
import com.tepmex.inbox.domain.Tab

@Composable
fun InboxBottomBar(
    tab: Tab,
    center: CenterAction,
    onReminders: () -> Unit,
    onInbox: () -> Unit,
    onCenter: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SideButton(
                selected = tab == Tab.Reminders,
                icon = if (tab == Tab.Reminders) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                label = stringResource(R.string.tab_reminders),
                onClick = onReminders,
                modifier = Modifier.weight(1f),
            )
            SideButton(
                selected = tab == Tab.Inbox,
                icon = if (tab == Tab.Inbox) Icons.Filled.Inbox else Icons.Outlined.Inbox,
                label = stringResource(R.string.tab_inbox),
                onClick = onInbox,
                modifier = Modifier.weight(1f),
            )
            CenterButton(
                center = center,
                onClick = onCenter,
                modifier = Modifier.weight(1.2f),
            )
            SideButton(
                selected = tab == Tab.Archive,
                icon = if (tab == Tab.Archive) Icons.Filled.Archive else Icons.Outlined.Archive,
                label = stringResource(R.string.tab_archive),
                onClick = onArchive,
                modifier = Modifier.weight(1f),
            )
            SideButton(
                selected = tab == Tab.Settings,
                icon = if (tab == Tab.Settings) Icons.Filled.Settings else Icons.Outlined.Settings,
                label = stringResource(R.string.tab_settings),
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SideButton(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundButton(
        icon = icon,
        label = label,
        onClick = onClick,
        enabled = true,
        diameter = 56.dp,
        iconSize = 26.dp,
        container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        content = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.semantics {
            role = Role.Tab
            this.selected = selected
        },
    )
}

@Composable
private fun CenterButton(
    center: CenterAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = center != CenterAction.SendDisabled
    val icon: ImageVector
    val label: String
    val container: Color
    val content: Color
    when (center) {
        CenterAction.Send -> {
            icon = Icons.AutoMirrored.Filled.Send
            label = stringResource(R.string.send)
            container = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
        }
        CenterAction.SendDisabled -> {
            icon = Icons.AutoMirrored.Filled.Send
            label = stringResource(R.string.send_disabled)
            container = MaterialTheme.colorScheme.surfaceVariant
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        CenterAction.OpenCapture -> {
            icon = Icons.AutoMirrored.Filled.Article
            label = stringResource(R.string.open_capture)
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    RoundButton(
        icon = icon,
        label = label,
        onClick = onClick,
        enabled = enabled,
        diameter = 72.dp,
        iconSize = 34.dp,
        container = container,
        content = content,
        modifier = modifier.semantics { role = Role.Button },
    )
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    diameter: Dp,
    iconSize: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(diameter),
            shape = CircleShape,
            color = container,
            contentColor = content,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
