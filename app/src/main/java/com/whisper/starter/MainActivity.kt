package com.whisper.starter

import android.R.attr.x
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.whisper.architecture.bean.transmit.ApiResponse
import com.whisper.architecture.extension.viewBinding
import com.whisper.architecture.net.ApiFactory
import com.whisper.starter.data.bean.GettingResp
import com.whisper.starter.data.ds.Api
import com.whisper.starter.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG: String = "whisper"
    }

    private val _viewBinding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(_viewBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        _viewBinding.tv.text = BuildConfig.DEBUG.toString()

        _viewBinding.btGet.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val resp: ApiResponse<GettingResp?>? = try {
                    ApiFactory.create(Api::class).getting(1)
                } catch (e: Exception) {
                    Log.i(TAG, "resp: ${e.message}")
                    null
                }
                Log.i(TAG, "resp: $resp")
            }
        }

    }
}