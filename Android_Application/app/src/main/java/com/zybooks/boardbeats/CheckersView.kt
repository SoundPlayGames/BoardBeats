package com.zybooks.boardbeats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CheckersView (context: Context, attrs: AttributeSet?) : View(context, attrs){
    private val boardPaint = Paint().apply { isAntiAlias = true };
    private val highlightPaint = Paint().apply {
        color = Color.YELLOW;
        style = Paint.Style.STROKE;
        strokeWidth = 8f;
        isAntiAlias = true;
    };
    private val piecePaintRed = Paint().apply { color = Color.RED; isAntiAlias = true };
    private val piecePaintBlack = Paint().apply { color = Color.BLACK; isAntiAlias = true };

    private var cellSize = 0f;
    private val board = Array(8) { IntArray(8) }; // 0=empty, 1=red, 2=black

    private var selectedRow = -1;
    private var selectedCol = -1;

    init { setupInitialPieces() };

    private fun setupInitialPieces() {
        for (row in 0..2) {
            for (col in 0..7) if ((row + col) % 2 == 1)
                board[row][col] = 2;
        }
        for (row in 5..7) {
            for (col in 0..7) if ((row + col) % 2 == 1)
                board[row][col] = 1;
        }
    };

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
        cellSize = size / 8f;
    };

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);
        drawBoard(canvas);
        drawPieces(canvas);
        drawHighlight(canvas);
    };

    private fun drawBoard(canvas: Canvas) {
        for (row in 0..7) {
            for (col in 0..7) {
                boardPaint.color = if ((row + col) % 2 == 0) Color.LTGRAY else Color.DKGRAY;
                canvas.drawRect(
                    col * cellSize,
                    row * cellSize,
                    (col + 1) * cellSize,
                    (row + 1) * cellSize,
                    boardPaint
                );
            }
        }
    };

    private fun drawPieces(canvas: Canvas) {
        val padding = cellSize * 0.1f;
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board[row][col];
                if (piece != 0) {
                    val cx = col * cellSize + cellSize / 2;
                    val cy = row * cellSize + cellSize / 2;
                    val radius = cellSize / 2 - padding;
                    canvas.drawCircle(cx, cy, radius, if (piece == 1) piecePaintRed else piecePaintBlack);
                }
            }
        }
    };

    private fun drawHighlight(canvas: Canvas) {
        if (selectedRow != -1 && selectedCol != -1) {
            canvas.drawRect(
                selectedCol * cellSize,
                selectedRow * cellSize,
                (selectedCol + 1) * cellSize,
                (selectedRow + 1) * cellSize,
                highlightPaint
            );
        }
    };

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN)
            return true;

        val col = (event.x / cellSize).toInt();
        val row = (event.y / cellSize).toInt();

        if (row !in 0..7 || col !in 0..7)
            return true;

        val piece = board[row][col];

        if (selectedRow == -1 && selectedCol == -1) {
            // Select a piece if it exists
            if (piece != 0) {
                selectedRow = row;
                selectedCol = col;
            }
        } else {
            // Try to move selected piece
            if (board[row][col] == 0 && isValidMove(selectedRow, selectedCol, row, col)) {
                board[row][col] = board[selectedRow][selectedCol];
                board[selectedRow][selectedCol] = 0;
            }
            // Clear selection
            selectedRow = -1;
            selectedCol = -1;
        }

        invalidate(); // Redraw board
        return true;
    };

    private fun isValidMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        // Simple forward move validation (no captures)
        val piece = board[fromRow][fromCol]
        if (piece == 1)
            return toRow == fromRow - 1 && Math.abs(toCol - fromCol) == 1;
        if (piece == 2)
            return toRow == fromRow + 1 && Math.abs(toCol - fromCol) == 1;
        return false;
    };

    fun resetGame() {
        for (row in board.indices) {
            for (col in board[row].indices)
                board[row][col] = 0;
        }
        setupInitialPieces();
        selectedRow = -1;
        selectedCol = -1;
        invalidate();
    };


}