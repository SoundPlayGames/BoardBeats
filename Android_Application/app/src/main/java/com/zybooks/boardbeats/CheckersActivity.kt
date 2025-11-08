package com.zybooks.boardbeats

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CheckersActivity : AppCompatActivity() {
    private lateinit var player1Losses: TextView;
    private lateinit var player2Losses: TextView;
    private lateinit var checkersView: CheckersView;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkers);

        player1Losses = findViewById(R.id.player1Losses);
        player2Losses = findViewById(R.id.player2Losses);
        checkersView = findViewById(R.id.checkersView);



    };
}