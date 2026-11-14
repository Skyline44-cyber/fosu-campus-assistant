package dev.campususage.fosu.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.campususage.fosu.ChaoxingLoginActivity
import dev.campususage.fosu.data.ChaoxingContract

@Composable
fun rememberChaoxingImport(vm: FosuViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(ChaoxingContract.EXTRA_COURSES_JSON)
            val replace = result.data?.getBooleanExtra(ChaoxingContract.EXTRA_REPLACE, true) ?: true
            if (!json.isNullOrBlank()) {
                vm.notify(vm.importChaoxing(json, replace).message)
            }
        }
    }
    return remember(launcher, context) {
        { launcher.launch(Intent(context, ChaoxingLoginActivity::class.java)) }
    }
}
