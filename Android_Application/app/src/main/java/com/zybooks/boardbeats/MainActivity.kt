package com.zybooks.boardbeats

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val ticTacToeButton = findViewById<Button>(R.id.btnTicTacToe);
        ticTacToeButton.setOnClickListener {
            startActivity(Intent(this, TicTacToeActivity::class.java));
        };

        val frontPageButton = findViewById<Button>(R.id.frontPageButton);
        frontPageButton.setOnClickListener {
            startActivity(Intent(this, FrontPageActivity::class.java));
        };
    }
}