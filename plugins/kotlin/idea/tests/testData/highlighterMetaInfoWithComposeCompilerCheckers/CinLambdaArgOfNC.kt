// COMPILER_ARGUMENTS: -Xplugin=$TEST_DIR$/compose_fake_plugin.jar
// FILE: main.kt
// ALLOW_ERRORS

import androidx.compose.runtime.*
@Composable fun C() { }
fun NC(lambda: () -> Unit) { lambda() }
@Composable fun C3() {
    NC {
        C()
    }
}
