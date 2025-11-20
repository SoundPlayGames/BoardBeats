package com.zybooks.boardbeats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TicTacToeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    interface GameEvents {
        fun onTurnChanged(isXTurn: Boolean)
        fun onGameWon(winner: Int)
        fun onDraw()
    }

    private val boardPaint = Paint().apply {
        color = Color.parseColor("#0C6174");
        strokeWidth = 10f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private val xPaint = Paint().apply {
        color = Color.parseColor("#0F1A2B");
        strokeWidth = 15f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private val oPaint = Paint().apply {
        color = Color.parseColor("#FF8A5C");
        strokeWidth = 15f;
        style = Paint.Style.STROKE;
        isAntiAlias = true;
    };

    private val winFillPaint = Paint().apply {
        color = Color.parseColor("#DFF4FB"); // soft highlight for winning line
        style = Paint.Style.FILL;
        isAntiAlias = true;
    };

    private var cellSize = 0f;
    private val board = Array(3) { IntArray(3) } ;// 0 = empty, 1 = X, 2 = O
    private var playerXTurn = true;
    private var gameOver = false;
    private var winningLine: List<Pair<Int, Int>>? = null;
    var gameEvents: GameEvents? = null

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
        val winCells = winningLine?.toSet();
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val left = col * cellSize;
                val top = row * cellSize;
                if (winCells != null && winCells.contains(Pair(row, col))) {
                    canvas.drawRoundRect(
                        RectF(left + 8f, top + 8f, left + cellSize - 8f, top + cellSize - 8f),
                        24f,
                        24f,
                        winFillPaint
                    );
                }
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
            val winner = checkForWin();
            if (winner != 0) {
                gameOver = true;
                gameEvents?.onGameWon(winner);
            } else if (isBoardFull()) {
                gameOver = true;
                gameEvents?.onDraw();
            } else {
                playerXTurn = !playerXTurn;
                gameEvents?.onTurnChanged(playerXTurn);
            }
            invalidate(); // redraw
        }
        return true;
    };

    private fun checkForWin(): Int {
        winningLine = null;
        // check rows, columns, and diagonals
        for (i in 0 until 3) {
            if (board[i][0] != 0 &&
                board[i][0] == board[i][1] &&
                board[i][0] == board[i][2]
            ) {
                winningLine = listOf(Pair(i, 0), Pair(i, 1), Pair(i, 2));
                return board[i][0];
            }

            if (board[0][i] != 0 &&
                board[0][i] == board[1][i] &&
                board[0][i] == board[2][i]
            ) {
                winningLine = listOf(Pair(0, i), Pair(1, i), Pair(2, i));
                return board[0][i];
            }
        }

        if (board[0][0] != 0 &&
            board[0][0] == board[1][1] &&
            board[0][0] == board[2][2]
        ) {
            winningLine = listOf(Pair(0, 0), Pair(1, 1), Pair(2, 2));
            return board[0][0];
        }

        if (board[0][2] != 0 &&
            board[0][2] == board[1][1] &&
            board[0][2] == board[2][0]
        ) {
            winningLine = listOf(Pair(0, 2), Pair(1, 1), Pair(2, 0));
            return board[0][2];
        }

        return 0;
    };

    private fun isBoardFull(): Boolean {
        for (r in 0..2) {
            for (c in 0..2) {
                if (board[r][c] == 0) return false;
            }
        }
        return true;
    }

    fun resetBoard() {
        for (r in 0..2) {
            for (c in 0..2) {
                board[r][c] = 0;
            }
        }
        playerXTurn = true;
        gameOver = false;
        winningLine = null;
        gameEvents?.onTurnChanged(playerXTurn);
        invalidate();
    }
}
