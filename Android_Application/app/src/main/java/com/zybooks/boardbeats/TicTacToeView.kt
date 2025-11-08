package com.zybooks.boardbeats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TicTacToeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val boardPaint = Paint().apply {
        color = Color.BLACK;
        strokeWidth = 10f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private val xPaint = Paint().apply {
        color = Color.BLUE;
        strokeWidth = 15f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private val oPaint = Paint().apply {
        color = Color.RED;
        strokeWidth = 15f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private var cellSize = 0f;
    private val board = Array(3) { IntArray(3) } ;// 0 = empty, 1 = X, 2 = O
    private var playerXTurn = true;
    private var gameOver = false;

    // Ensure the board stays square
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
        cellSize = size / 3f;
    };

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);
        drawGrid(canvas);
        drawMarkers(canvas);
    };

    private fun drawGrid(canvas: Canvas) {
        // Draw the 2 internal grid lines (3x3 board)
        for (i in 1 until 3) {
            // vertical lines
            canvas.drawLine(cellSize * i, 0f, cellSize * i, height.toFloat(), boardPaint);
            // horizontal lines
            canvas.drawLine(0f, cellSize * i, width.toFloat(), cellSize * i, boardPaint);
        }
    };

    private fun drawMarkers(canvas: Canvas) {
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val left = col * cellSize;
                val top = row * cellSize;
                when (board[row][col]) {
                    1 -> drawX(canvas, left, top);
                    2 -> drawO(canvas, left, top);
                }
            }
        }
    };

    private fun drawX(canvas: Canvas, left: Float, top: Float) {
        val padding = cellSize * 0.2f
        canvas.drawLine(left + padding, top + padding, left + cellSize - padding, top + cellSize - padding, xPaint);
        canvas.drawLine(left + padding, top + cellSize - padding, left + cellSize - padding, top + padding, xPaint);
    };

    private fun drawO(canvas: Canvas, left: Float, top: Float) {
        val centerX = left + cellSize / 2;
        val centerY = top + cellSize / 2;
        val radius = cellSize * 0.3f;
        canvas.drawCircle(centerX, centerY, radius, oPaint);
    };

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN || gameOver)
            return false;

        val col = (event.x / cellSize).toInt();
        val row = (event.y / cellSize).toInt();

        if (row in 0..2 && col in 0..2 && board[row][col] == 0) {
            board[row][col] = if (playerXTurn) 1 else 2;
            playerXTurn = !playerXTurn;
            invalidate(); // redraw

            if (checkForWin()) {
                gameOver = true;
                //Todo: In the future, trigger a callback to show the winner
            }
        }
        return true;
    };

    private fun checkForWin(): Boolean {
        // check rows, columns, and diagonals
        for (i in 0 until 3) {
            if (board[i][0] != 0 &&
                board[i][0] == board[i][1] &&
                board[i][0] == board[i][2]
            ) return true;

            if (board[0][i] != 0 &&
                board[0][i] == board[1][i] &&
                board[0][i] == board[2][i]
            ) return true;
        }

        if (board[0][0] != 0 &&
            board[0][0] == board[1][1] &&
            board[0][0] == board[2][2]
        ) return true;

        if (board[0][2] != 0 &&
            board[0][2] == board[1][1] &&
            board[0][2] == board[2][0]
        ) return true;

        return false;
    };

    fun resetGame() {
        for (r in 0..2) {
            for (c in 0..2) {
                board[r][c] = 0;
            }
        }
        playerXTurn = true;
        gameOver = false;
        invalidate();
    }
}
