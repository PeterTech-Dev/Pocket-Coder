package com.example.aiassistantcoder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ----------------- UI MODEL -----------------

data class ProjectUi(
    val id: String,
    val name: String,
    val date: String
)

// ----------------- LIST -----------------

@Composable
fun ProjectsList(
    projects: List<ProjectUi>,
    onDelete: (ProjectUi) -> Unit,
    onEdit: (ProjectUi) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = projects,
            key = { it.id }
        ) { project ->
            SwipeableProjectItem(
                project = project,
                onDelete = onDelete,
                onEdit = onEdit
            )
        }
    }
}

// ----------------- SWIPE ITEM WITH OVERLAY -----------------

@Composable
fun SwipeableProjectItem(
    project: ProjectUi,
    onDelete: (ProjectUi) -> Unit,
    onEdit: (ProjectUi) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // swipe RIGHT -> delete
                    onDelete(project)
                    // return false so it snaps back instead of disappearing
                    false
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    // swipe LEFT -> edit
                    onEdit(project)
                    false
                }

                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val progress = swipeState.progress          // 0f..1f
            val direction = swipeState.dismissDirection

            Box(modifier = Modifier.fillMaxSize()) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        // RIGHT swipe -> red overlay growing from the left
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .align(Alignment.CenterStart)
                                .background(Color(0xCCFF2D55), shape) // CC ~ 80% alpha
                                .padding(start = 24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        // LEFT swipe -> blue overlay growing from the right
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .align(Alignment.CenterEnd)
                                .background(Color(0xCC007AFF), shape)
                                .padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("Edit", color = Color.White)
                        }
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        // no overlay when idle
                    }
                }
            }
        }
    ) {
        // card content underneath the overlay
        ProjectRowCard(project, shape)
    }
}

// ----------------- ROW CONTENT -----------------

@Composable
private fun ProjectRowCard(
    project: ProjectUi,
    shape: RoundedCornerShape
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color(0xFF141625), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(project.name, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(project.date, color = Color.LightGray)
    }
}
