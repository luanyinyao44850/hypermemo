package com.example.hypermemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hypermemo.ui.MemoApp
import com.example.hypermemo.ui.MemoTheme
import com.example.hypermemo.ui.NoteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MemoTheme {
                val vm: NoteViewModel = viewModel(factory = NoteViewModel.Factory(
                    (application as MemoApplication).repository
                ))
                MemoApp(vm)
            }
        }
    }
}
