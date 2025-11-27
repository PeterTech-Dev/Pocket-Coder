package com.example.aiassistantcoder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiassistantcoder.R


// ---------- helper callable from Java ----------
fun bindSettingsContent(
    composeView: ComposeView,
    useDarkTheme: Boolean,
    autoApply: Boolean,
    showDiffPreview: Boolean,
    fontLabels: List<String>,
    editorFontIndex: Int,
    consoleFontIndex: Int,
    onDarkThemeChange: (Boolean) -> Unit,
    onAutoApplyChange: (Boolean) -> Unit,
    onShowDiffChange: (Boolean) -> Unit,
    onEditorFontChange: (Int) -> Unit,
    onConsoleFontChange: (Int) -> Unit,
    onClearHistory: () -> Unit
) {
    composeView.setContent {
        MaterialTheme(colorScheme = darkColorScheme()) {
            SettingsContent(
                useDarkTheme = useDarkTheme,
                autoApply = autoApply,
                showDiffPreview = showDiffPreview,
                fontLabels = fontLabels,
                editorFontIndex = editorFontIndex,
                consoleFontIndex = consoleFontIndex,
                onDarkThemeChange = onDarkThemeChange,
                onAutoApplyChange = onAutoApplyChange,
                onShowDiffChange = onShowDiffChange,
                onEditorFontChange = onEditorFontChange,
                onConsoleFontChange = onConsoleFontChange,
                onClearHistory = onClearHistory
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    useDarkTheme: Boolean,
    autoApply: Boolean,
    showDiffPreview: Boolean,
    fontLabels: List<String>,
    editorFontIndex: Int,
    consoleFontIndex: Int,
    onDarkThemeChange: (Boolean) -> Unit,
    onAutoApplyChange: (Boolean) -> Unit,
    onShowDiffChange: (Boolean) -> Unit,
    onEditorFontChange: (Int) -> Unit,
    onConsoleFontChange: (Int) -> Unit,
    onClearHistory: () -> Unit
) {
    // Colors
    val buttonTrackOn = colorResource(id = R.color.colorPrimary)
    val buttonTrackOff = Color(0xFF444444)
    val buttonThumbOn = colorResource(id = R.color.colorOnPrimary)
    val buttonThumbOff = Color.Black

    val blue = colorResource(id = R.color.colorPrimary)
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird = colorResource(R.color.colorSurfaceVariant)
    val white = colorResource(R.color.colorOnBackground)
    val grey = colorResource(R.color.colorOutline)
    val purple = colorResource(R.color.colorSecondary)

    // Local state mirrors
    var dark by remember { mutableStateOf(useDarkTheme) }
    var auto by remember { mutableStateOf(autoApply) }
    var showDiff by remember { mutableStateOf(showDiffPreview) }
    var editorIdx by remember { mutableIntStateOf(editorFontIndex) }
    var consoleIdx by remember { mutableIntStateOf(consoleFontIndex) }

    // Confirmation dialog state
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Screen header
        Text(
            text = "Settings",
            color = white,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Customize your workspace, fonts, and AI behavior.",
            color = grey.copy(alpha = 0.8f),
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        // -------- UI --------
        SettingsSectionCard(
            title = "UI",
            labelColor = grey,
            bgColor = bgSecondary,
            dividerColor = bgThird
        ) {
            SettingSwitchRow(
                label = "Dark mode",
                subtitle = "Use a dark theme throughout the app.",
                checked = dark,
                onCheckedChange = {
                    dark = it
                    onDarkThemeChange(it)
                },
                textColor = white,
                trackOn = buttonTrackOn,
                trackOff = buttonTrackOff,
                thumbOn = buttonThumbOn,
                thumbOff = buttonThumbOff
            )
        }

        Spacer(Modifier.height(16.dp))

        // -------- Code Editor --------
        SettingsSectionCard(
            title = "Code editor",
            labelColor = grey,
            bgColor = bgSecondary,
            dividerColor = bgThird
        ) {
            SettingSwitchRow(
                label = "Auto-apply AI code",
                subtitle = "Automatically insert AI suggestions into the editor.",
                checked = auto,
                onCheckedChange = {
                    auto = it
                    onAutoApplyChange(it)
                },
                textColor = white,
                trackOn = buttonTrackOn,
                trackOff = buttonTrackOff,
                thumbOn = buttonThumbOn,
                thumbOff = buttonThumbOff
            )

            DividerLine(color = bgThird)

            SettingSwitchRow(
                label = "Show diff preview",
                subtitle = "Preview changes before applying them to your code.",
                checked = showDiff,
                onCheckedChange = {
                    showDiff = it
                    onShowDiffChange(it)
                },
                textColor = white,
                trackOn = buttonTrackOn,
                trackOff = buttonTrackOff,
                thumbOn = buttonThumbOn,
                thumbOff = buttonThumbOff
            )
        }

        Spacer(Modifier.height(16.dp))

        // -------- Fonts --------
        SettingsSectionCard(
            title = "Fonts",
            labelColor = grey,
            bgColor = bgSecondary,
            dividerColor = bgThird
        ) {
            SettingDropdownRow(
                label = "Editor font",
                options = fontLabels,
                selectedIndex = editorIdx,
                onSelectedIndexChange = { i ->
                    editorIdx = i
                    onEditorFontChange(i)
                },
                labelColor = grey,
                textColor = white,
                borderColor = bgThird,
                focusedBorderColor = blue,
                containerColor = bgThird
            )

            DividerLine(color = bgThird)

            SettingDropdownRow(
                label = "Console font",
                options = fontLabels,
                selectedIndex = consoleIdx,
                onSelectedIndexChange = { i ->
                    consoleIdx = i
                    onConsoleFontChange(i)
                },
                labelColor = grey,
                textColor = white,
                borderColor = bgThird,
                focusedBorderColor = blue,
                containerColor = bgThird
            )
        }

        Spacer(Modifier.height(24.dp))

        // Clear history (opens confirmation dialog)
        ClearHistoryButton(
            textColor = colorResource(id = R.color.colorOnPrimary),
            bgColor = purple,
            onClick = { showClearHistoryDialog = true }
        )

        Spacer(Modifier.height(12.dp))
    }

    // ---- Clear history confirmation dialog ----
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    text = "Clear project history?",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove your recent project history. " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearHistory()
                    }
                ) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = bgSecondary
        )
    }
}

// ---- building blocks ----

@Composable
fun SettingsSectionCard(
    title: String,
    labelColor: Color,
    bgColor: Color,
    dividerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: Color,
    trackOn: Color,
    trackOff: Color,
    thumbOn: Color,
    thumbOff: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = trackOn,
                uncheckedTrackColor = trackOff,
                checkedThumbColor = thumbOn,
                uncheckedThumbColor = thumbOff
            )
        )
    }
}

@Composable
fun DividerLine(color: Color) {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = color
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    labelColor: Color,
    textColor: Color,
    borderColor: Color,
    focusedBorderColor: Color,
    containerColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    val safeIndex = selectedIndex.coerceIn(
        0,
        if (options.isEmpty()) 0 else options.size - 1
    )
    val selectedText = options.getOrNull(safeIndex).orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 13.sp
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    color = textColor,
                    fontSize = 15.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = focusedBorderColor,
                    unfocusedBorderColor = borderColor,
                    cursorColor = focusedBorderColor,
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor
                ),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = containerColor
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, color = textColor) },
                        onClick = {
                            onSelectedIndexChange(index)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = textColor
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ClearHistoryButton(
    textColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Clear project history",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
