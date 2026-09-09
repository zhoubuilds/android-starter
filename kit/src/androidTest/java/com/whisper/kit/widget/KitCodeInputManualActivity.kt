package com.whisper.kit.widget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.whisper.kit.test.R

/**
 * 提供可从桌面打开的短码输入手动测试页面, 不参与正式应用打包.
 *
 * @author whisper
 * @since 2026/09/09
 */
class KitCodeInputManualActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.kit_test_code_input_manual)
        val content: View = findViewById(R.id.kit_test_manual_content)
        val numeric: KitCodeInputEditText = findViewById(R.id.kit_test_manual_numeric)
        val ascii: KitCodeInputEditText = findViewById(R.id.kit_test_manual_ascii)
        val finishEditing: () -> Unit = {
            content.requestFocus()
            getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(content.windowToken, 0)
        }
        content.setOnClickListener { finishEditing() }
        findViewById<View>(R.id.kit_test_manual_clear).setOnClickListener {
            numeric.text?.clear()
            ascii.text?.clear()
        }
        findViewById<View>(R.id.kit_test_manual_done).setOnClickListener { finishEditing() }
        numeric.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                ascii.requestFocus()
            } else {
                false
            }
        }
        ascii.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishEditing()
                true
            } else {
                false
            }
        }
        if (savedInstanceState == null) {
            content.requestFocus()
        }
    }
}
