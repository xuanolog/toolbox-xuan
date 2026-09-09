package cn.edu.szcu.connect

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ProfileUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    /** Only creates/deletes this test's fake profile; never taps the connect button. */
    @Test fun createEditDeleteProfile() {
        ui.onNodeWithText("账号管理").performClick()
        ui.onNodeWithText("＋ 添加配置").performClick()
        ui.onAllNodes(hasSetTextAction())[0].performTextInput("UI-SMOKE-ONLY")
        ui.onNodeWithText("请选择运营商").performClick()
        ui.onNodeWithText("中国联通").performClick()
        // Read-only carrier field also has a setText semantic; select editable fields by labels.
        ui.onNodeWithText("学工号").performTextInput("student0001")
        ui.onNodeWithText("数字门户密码").performTextInput("test-only-a&b=+")
        ui.onNodeWithText("保存配置").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("UI-SMOKE-ONLY").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("UI-SMOKE-ONLY").assertExists()
        ui.onAllNodesWithText("编辑").onLast().performScrollTo().performClick()
        ui.onNodeWithText("配置名称").performTextReplacement("UI-SMOKE-EDITED")
        ui.onNodeWithText("保存配置").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("UI-SMOKE-EDITED").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText("删除").onLast().performScrollTo().performClick()
        ui.onAllNodesWithText("删除").onLast().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("UI-SMOKE-EDITED").fetchSemanticsNodes().isEmpty() }
    }
}
