package com.zybooks.boardbeats

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button

class TicTacToeActivity : AppCompatActivity() {

    private lateinit var ticTacToeView: TicTacToeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tic_tac_toe)

        ticTacToeView = findViewById(R.id.ticTacToeView)

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            ticTacToeView.resetGame()
        }
    }
}