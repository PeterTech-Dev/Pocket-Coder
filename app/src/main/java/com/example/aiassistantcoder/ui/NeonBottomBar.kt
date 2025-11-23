package com.example.aiassistantcoder.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.example.aiassistantcoder.HomeFragment
import com.example.aiassistantcoder.ProjectsFragment
import com.example.aiassistantcoder.SettingsFragment
import com.example.aiassistantcoder.ProfileFragment
import com.example.aiassistantcoder.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.draw.dropShadow

@Composable
fun NeonBottomBar(
    onItemSelected: (Int) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    val items = listOf(
        "Home" to R.drawable.ic_nav_home,
        "Projects" to R.drawable.ic_nav_projects,
        "Settings" to R.drawable.ic_nav_settings,
        "Profile" to R.drawable.ic_nav_profile
    )

    NavigationBar(
        containerColor = colorResource(id = R.color.colorSurfaceVariant),
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, (label, iconRes) ->
            val selected = index == selectedIndex
            val glowColor = colorResource(id = R.color.colorPrimaryLight)
            val indicatorShape = RoundedCornerShape(percent = 50)

            NavigationBarItem(
                selected = selected,
                onClick = {
                    selectedIndex = index
                    onItemSelected(index)
                },
                icon = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = if (selected) {
                            Modifier
                                .dropShadow(
                                    shape = indicatorShape,
                                    shadow = Shadow(
                                        radius = 18.dp,
                                        spread = 1.dp,
                                        color = glowColor.copy(alpha = 0.5f),
                                        offset = DpOffset(0.dp, 4.dp)
                                    )
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = colorResource(id = R.color.colorPrimaryAccent),
                                    shape = indicatorShape
                                )
                                .background(
                                    color = glowColor.copy(alpha = 0.1f),
                                    shape = indicatorShape
                                )
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                        } else {
                            Modifier.padding(0.dp)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = label,
                        )
                    }
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorResource(id = R.color.colorPrimaryAccent),
                    selectedTextColor = colorResource(id = R.color.colorOnBackground),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = colorResource(id = R.color.colorOnBackground),
                    unselectedTextColor = colorResource(id = R.color.colorOnBackground)
                )
            )

        }
    }
}

fun setupNeonBottomBar(composeView: ComposeView, activity: FragmentActivity) {
    composeView.setContent {
        MaterialTheme {
            Surface(color = Color.Transparent) {
                NeonBottomBar { index ->
                    when (index) {
                        0 -> showFragment(activity, HomeFragment())
                        1 -> showFragment(activity, ProjectsFragment())
                        2 -> showFragment(activity, SettingsFragment())
                        3 -> showFragment(activity, ProfileFragment())
                    }
                }
            }
        }
    }
}

private fun showFragment(activity: FragmentActivity, fragment: Fragment) {
    activity.supportFragmentManager
        .beginTransaction()
        .replace(R.id.fragment_container, fragment)
        .commit()
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF050816,
    name = "Neon Bottom Bar"
)
@Composable
fun NeonBottomBarPreview() {
    MaterialTheme {
        Surface(color = colorResource(id = R.color.colorSurfaceVariant)) {
            NeonBottomBar {}
        }
    }
}
