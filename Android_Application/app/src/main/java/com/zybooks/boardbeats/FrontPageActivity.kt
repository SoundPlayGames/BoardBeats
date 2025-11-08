package com.zybooks.boardbeats

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class FrontPageActivity : AppCompatActivity() {
    private lateinit var ivHero: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnTicTacToe: Button
    private lateinit var btnCheckers: Button
    private lateinit var btnMusic: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.front_page)

        ivHero = findViewById(R.id.ivHero)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnTicTacToe = findViewById(R.id.btnTicTacToe)
        btnCheckers = findViewById(R.id.btnCheckers)
        btnMusic = findViewById(R.id.btnMusic)

        btnTicTacToe.setOnClickListener {
            startActivity(Intent(this, TicTacToeActivity::class.java));
        }

        btnCheckers.setOnClickListener {
            startActivity(Intent(this, CheckersActivity::class.java));
        }

        btnMusic.setOnClickListener {
            Toast.makeText(this, "Spotify screen coming soon!", Toast.LENGTH_SHORT).show()
        }
    }
}