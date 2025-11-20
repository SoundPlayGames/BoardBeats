package com.zybooks.boardbeats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class TicTacToeActivity : AppCompatActivity() {

    private lateinit var ticTacToeView: TicTacToeView
    private lateinit var playerXScoreText: TextView
    private lateinit var playerOScoreText: TextView
    private lateinit var drawScoreText: TextView
    private lateinit var statusText: TextView

    private var playerXWins = 0
    private var playerOWins = 0
    private var draws = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tic_tac_toe)

        ticTacToeView = findViewById(R.id.ticTacToeView)
        playerXScoreText = findViewById(R.id.player1Score)
        playerOScoreText = findViewById(R.id.player2Score)
        drawScoreText = findViewById(R.id.drawScore)
        statusText = findViewById(R.id.statusText)

        ticTacToeView.gameEvents = object : TicTacToeView.GameEvents {
            override fun onTurnChanged(isXTurn: Boolean) {
                setStatusForTurn(isXTurn)
            }

            override fun onGameWon(winner: Int) {
                if (winner == 1) {
                    playerXWins++;
                    statusText.text = "Player X wins! Tap Next round to continue."
                } else {
                    playerOWins++;
                    statusText.text = "Player O wins! Tap Next round to continue."
                }
                updateScoreboard();
            }

            override fun onDraw() {
                draws++;
                statusText.text = "It's a draw. Start another round!";
                updateScoreboard();
            }
        }
        updateScoreboard();

        findViewById<Button>(R.id.nextRoundButton).setOnClickListener {
            ticTacToeView.resetBoard();
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            playerXWins = 0;
            playerOWins = 0;
            draws = 0;
            updateScoreboard();
            ticTacToeView.resetBoard();
            statusText.text = "Scores cleared. Player X goes first.";
        }

        // Set initial prompt
        ticTacToeView.resetBoard();
    }

    private fun updateScoreboard() {
        playerXScoreText.text = playerXWins.toString();
        playerOScoreText.text = playerOWins.toString();
        drawScoreText.text = draws.toString();
    }

    private fun setStatusForTurn(isXTurn: Boolean) {
        statusText.text = if (isXTurn) "Player X's turn" else "Player O's turn";
    }
}
