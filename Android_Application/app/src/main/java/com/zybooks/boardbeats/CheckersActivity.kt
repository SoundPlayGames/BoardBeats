package com.zybooks.boardbeats

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button

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

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        checkersView.winListener = object : CheckersView.OnWinListener {
            override fun onPlayerWin(winningPlayer: Int) {
                showWinScreen(winningPlayer)
            }
        }


    };
    private fun showWinScreen(player: Int) {
        // Update score
        if (player == 1) {
            val losses = player2Losses.text.toString().substringAfter(": ").toInt() + 1;
            player2Losses.text = "Player 2: $losses";
        } else {
            val losses = player1Losses.text.toString().substringAfter(": ").toInt() + 1;
            player1Losses.text = "Player 1: $losses";
        }

        val builder = AlertDialog.Builder(this);
        builder.setTitle("Player $player Wins!");
        builder.setMessage("Game Over – Player $player has won.");

        builder.setPositiveButton("Play Again") { _, _ ->
            checkersView.resetGame();
        };

        builder.setNegativeButton("Back") { _, _ ->
            finish();  // closes activity and returns to previous screen
        };

        builder.setCancelable(false);
        builder.show();
    };

}