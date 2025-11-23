package com.example.aiassistantcoder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val greyDark    = colorResource(id = R.color.colorOutline)
    val blue        = colorResource(id = R.color.colorPrimary)
    val bgSecondary = colorResource(R.color.colorSurface)
    val bgThird     = colorResource(R.color.colorSurfaceVariant)
    val white       = colorResource(R.color.colorOnBackground)
    val grey        = colorResource(R.color.colorOutline)
    val purple      = colorResource(R.color.colorSecondary)

    var dark by remember { mutableStateOf(useDarkTheme) }
    var auto by remember { mutableStateOf(autoApply) }
    var showDiff by remember { mutableStateOf(showDiffPreview) }
    var editorIdx by remember { mutableIntStateOf(editorFontIndex) }
    var consoleIdx by remember { mutableIntStateOf(consoleFontIndex) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // -------- UI --------
        SettingsSectionCard(
            title = "UI",
            labelColor = grey,
            bgColor = bgSecondary,
            dividerColor = bgThird
        ) {
            SettingSwitchRow(
                label = "Dark Mode",
                checked = dark,
                onCheckedChange = {
                    dark = it
                    onDarkThemeChange(it)
                },
                textColor = white,
                trackOn = blue,
                trackOff = greyDark,
                thumbOn = white,
                thumbOff = bgThird
            )
        }

        Spacer(Modifier.height(16.dp))

        // -------- Code Editor --------
        SettingsSectionCard(
            title = "Code Editor",
            labelColor = grey,
            bgColor = bgSecondary,
            dividerColor = bgThird
        ) {
            SettingSwitchRow(
                label = "Auto-apply AI code to editor",
                checked = auto,
                onCheckedChange = {
                    auto = it
                    onAutoApplyChange(it)
                },
                textColor = white,
                trackOn = blue,
                trackOff = greyDark,
                thumbOn = white,
                thumbOff = bgThird
            )

            DividerLine(color = bgThird)

            SettingSwitchRow(
                label = "Show diff preview",
                checked = showDiff,
                onCheckedChange = {
                    showDiff = it
                    onShowDiffChange(it)
                },
                textColor = white,
                trackOn = blue,
                trackOff = greyDark,
                thumbOn = white,
                thumbOff = bgThird
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

        ClearHistoryButton(
            textColor = white,
            bgColor = purple,
            onClick = onClearHistory
        )
    }
}

// ---- building blocks ----

@Composable
fun SettingsSectionCard(
    title: String,
    labelColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    dividerColor: androidx.compose.ui.graphics.Color,
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
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: androidx.compose.ui.graphics.Color,
    trackOn: androidx.compose.ui.graphics.Color,
    trackOff: androidx.compose.ui.graphics.Color,
    thumbOn: androidx.compose.ui.graphics.Color,
    thumbOff: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

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
fun DividerLine(color: androidx.compose.ui.graphics.Color) {
    Divider(
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
    labelColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    focusedBorderColor: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color
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
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
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
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, color = textColor) },
                        onClick = {
                            onSelectedIndexChange(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ClearHistoryButton(
    textColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
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
        Text(
            text = "Clear Project History",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
