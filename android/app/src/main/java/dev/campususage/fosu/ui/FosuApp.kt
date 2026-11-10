package dev.campususage.fosu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.campususage.fosu.ui.screens.ElectricityScreen
import dev.campususage.fosu.ui.screens.HomeScreen
import dev.campususage.fosu.ui.screens.ProfileScreen
import dev.campususage.fosu.ui.screens.TimetableScreen
import dev.campususage.fosu.ui.theme.*

@Composable
fun FosuApp(vm: FosuViewModel) {
    if (!vm.data.settings.guideSeen) {
        OnboardingDialog(onDone = vm::markGuideSeen)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }

    FosuBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        containerColor = SurfaceSoft,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            bottomBar = { BottomBar(vm.page, vm::go) }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                when (vm.page) {
                    "electricity" -> ElectricityScreen(vm)
                    "timetable" -> TimetableScreen(vm)
                    "profile" -> ProfileScreen(vm)
                    else -> HomeScreen(vm)
                }
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun BottomBar(page: String, onSelect: (String) -> Unit) {
    val items = remember {
        listOf(
            NavItem("home", "首页", Icons.Default.Home),
            NavItem("electricity", "电费", Icons.Default.Bolt),
            NavItem("timetable", "课表", Icons.Default.CalendarMonth),
            NavItem("profile", "我的", Icons.Default.Person)
        )
    }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface.copy(alpha = 0.98f))
            .border(0.7.dp, Outline.copy(alpha = 0.75f), shape)
            .navigationBarsPadding()
            .padding(top = 5.dp, bottom = 3.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        items.forEach { item ->
            val selected = page == item.route
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.route) })
                    .padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(item.icon, item.label, Modifier.size(23.dp), tint = if (selected) Pink else TextMuted)
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Pink else TextMuted
                )
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(if (selected) Pink else Color.Transparent, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
