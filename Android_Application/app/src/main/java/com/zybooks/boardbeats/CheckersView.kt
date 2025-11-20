package com.zybooks.boardbeats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min


class CheckersView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // Piece constants
    companion object {
        const val EMPTY = 0
        const val RED = 1           // player 1 (moves up = row--)
        const val BLACK = 2         // player 2 (moves down = row++)
        const val RED_KING = 3
        const val BLACK_KING = 4
    }

    // Paints
    private val boardPaint = Paint().apply { isAntiAlias = true }
    private val highlightPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val movePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        alpha = 120
        isAntiAlias = true
    }
    private val piecePaintRed = Paint().apply { color = Color.RED; isAntiAlias = true }
    private val piecePaintBlack = Paint().apply { color = Color.BLACK; isAntiAlias = true }
    private val kingInner = Paint().apply { color = Color.WHITE; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 30f }

    // Board state
    private val board = Array(8) { IntArray(8) { EMPTY } }
    private var cellSize = 0f

    // Selection & turn
    private var selectedRow = -1
    private var selectedCol = -1
    private var possibleMoves: MutableList<Pair<Int, Int>> = mutableListOf()
    private var mustCapturePieces: MutableSet<Pair<Int, Int>> = mutableSetOf()

    // Turn: true = RED's turn, false = BLACK's turn
    private var redTurn = true

    // Listener so Activity can update score / show winner
    interface GameListener {
        // player: 1 = RED, 2 = BLACK
        fun onTurnChanged(currentPlayer: Int)
        fun onGameOver(winnerPlayer: Int) // winnerPlayer: 1 = RED, 2 = BLACK, 0 = draw (rare)
    }
    private var listener: GameListener? = null
    fun setGameListener(l: GameListener) { listener = l }

    init {
        resetBoard()
    }

    // initialize starting positions
    private fun resetBoard() {
        for (r in 0..7) for (c in 0..7) board[r][c] = EMPTY
        for (r in 0..2) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = BLACK
        for (r in 5..7) for (c in 0..7) if ((r + c) % 2 == 1) board[r][c] = RED
        selectedRow = -1; selectedCol = -1; possibleMoves.clear()
        redTurn = true
        computeMustCapturePieces()
        listener?.onTurnChanged(currentPlayer())
        invalidate()
    }

    fun resetGame() { resetBoard() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
        cellSize = size / 8f
        kingInner.textSize = cellSize * 0.28f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoard(canvas)
        drawHighlights(canvas)
        drawPieces(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        for (r in 0..7) {
            for (c in 0..7) {
                boardPaint.color = if ((r + c) % 2 == 0) Color.LTGRAY else Color.DKGRAY
                canvas.drawRect(c * cellSize, r * cellSize, (c + 1) * cellSize, (r + 1) * cellSize, boardPaint)
            }
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        // highlight selected
        if (selectedRow != -1 && selectedCol != -1) {
            canvas.drawRect(
                selectedCol * cellSize, selectedRow * cellSize,
                (selectedCol + 1) * cellSize, (selectedRow + 1) * cellSize, highlightPaint
            )
        }
        // draw possible move overlays
        for ((r, c) in possibleMoves) {
            val cx = c * cellSize + cellSize / 2
            val cy = r * cellSize + cellSize / 2
            canvas.drawCircle(cx, cy, cellSize * 0.15f, movePaint)
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val padding = cellSize * 0.1f
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != EMPTY) {
                    val cx = c * cellSize + cellSize / 2
                    val cy = r * cellSize + cellSize / 2
                    val radius = cellSize / 2 - padding
                    val paint = if (p == RED || p == RED_KING) piecePaintRed else piecePaintBlack
                    canvas.drawCircle(cx, cy, radius, paint)
                    // draw king marker if king
                    if (p == RED_KING || p == BLACK_KING) {
                        val txt = "K"
                        kingInner.color = if (p == RED_KING) Color.WHITE else Color.YELLOW
                        kingInner.textSize = cellSize * 0.28f
                        canvas.drawText(txt, cx, cy + kingInner.textSize / 3f, kingInner)
                    }
                }
            }
        }
    }

    // ====================================================
    // TOUCH HANDLING & MOVE LOGIC
    // ====================================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true

        val col = (event.x / cellSize).toInt()
        val row = (event.y / cellSize).toInt()
        if (row !in 0..7 || col !in 0..7) return true

        val clickedPiece = board[row][col]

        // If no piece selected yet:
        if (selectedRow == -1 && selectedCol == -1) {
            // Only allow selecting a piece that belongs to current player
            if (clickedPiece != EMPTY && isCurrentPlayersPiece(clickedPiece)) {
                selectedRow = row; selectedCol = col
                updatePossibleMovesForSelected()
            }
        } else {
            // If click on another of current player's pieces, change selection
            if (clickedPiece != EMPTY && isCurrentPlayersPiece(clickedPiece)) {
                selectedRow = row; selectedCol = col
                updatePossibleMovesForSelected()
            } else {
                // Try to move to clicked square if it's in possibleMoves
                val target = Pair(row, col)
                if (possibleMoves.contains(target)) {
                    val wasCapture = performMove(selectedRow, selectedCol, row, col)
                    // If it was a capture and the same piece can capture again -> continue selection
                    if (wasCapture) {
                        // find the new location of that piece
                        val newR = row
                        val newC = col
                        // Check for further captures
                        val moreCaptures = availableCapturesForPiece(newR, newC)
                        if (moreCaptures.isNotEmpty()) {
                            // continue with same piece (multi-jump)
                            selectedRow = newR; selectedCol = newC
                            updatePossibleMovesForSelected() // will only contain capturing moves in this case
                            invalidate()
                            return true // do not switch turn yet
                        }
                    }
                    // otherwise, finish turn: switch player
                    selectedRow = -1; selectedCol = -1
                    possibleMoves.clear()
                    toggleTurn()
                } else {
                    // clicked an empty square that's not legal -> clear selection
                    selectedRow = -1; selectedCol = -1; possibleMoves.clear()
                }
            }
        }

        invalidate()
        return true
    }

    // perform move, return true if a capture happened
    private fun performMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        val piece = board[fromRow][fromCol]
        var captured = false

        // Determine if this is a capturing move (jump of distance 2)
        if (abs(toRow - fromRow) == 2 && abs(toCol - fromCol) == 2) {
            val midRow = (fromRow + toRow) / 2
            val midCol = (fromCol + toCol) / 2
            // remove captured piece
            board[midRow][midCol] = EMPTY
            captured = true
        }

        // Move piece
        board[toRow][toCol] = board[fromRow][fromCol]
        board[fromRow][fromCol] = EMPTY

        // Promotion to king
        if (board[toRow][toCol] == RED && toRow == 0) board[toRow][toCol] = RED_KING
        if (board[toRow][toCol] == BLACK && toRow == 7) board[toRow][toCol] = BLACK_KING

        // If a capture occurred, recompute must-captures for the same player (because captures might remain)
        computeMustCapturePieces()

        // After move, check game over conditions
        checkGameOver()

        return captured
    }

    // Switch turn unless multi-jump continues
    private fun toggleTurn() {
        redTurn = !redTurn
        computeMustCapturePieces()
        listener?.onTurnChanged(currentPlayer())
        // If the next player has no legal moves -> game over
        checkGameOver()
    }

    private fun currentPlayer(): Int = if (redTurn) 1 else 2

    private fun isCurrentPlayersPiece(piece: Int): Boolean {
        return if (redTurn) (piece == RED || piece == RED_KING) else (piece == BLACK || piece == BLACK_KING)
    }

    // Recomputes which pieces MUST capture (i.e., any captures available for current player)
    private fun computeMustCapturePieces() {
        mustCapturePieces.clear()
        val targetSet = mutableSetOf<Pair<Int, Int>>()
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p == EMPTY) continue
            if (!isCurrentPlayersPiece(p)) continue
            val caps = availableCapturesForPiece(r, c)
            if (caps.isNotEmpty()) targetSet.add(Pair(r, c))
        }
        mustCapturePieces.addAll(targetSet)
    }

    // Fill possibleMoves for currently selected square (respects forced captures)
    private fun updatePossibleMovesForSelected() {
        possibleMoves.clear()
        if (selectedRow == -1 || selectedCol == -1) return

        val capturesForSelected = availableCapturesForPiece(selectedRow, selectedCol)
        if (mustCapturePieces.isNotEmpty()) {
            // If any piece must capture, selected piece must be one of them
            if (!mustCapturePieces.contains(Pair(selectedRow, selectedCol))) {
                // not allowed to move this piece
                possibleMoves.clear()
                return
            }
            possibleMoves.addAll(capturesForSelected)
        } else {
            // no forced captures; allow simple moves & captures (captures list may be empty)
            possibleMoves.addAll(capturesForSelected)
            possibleMoves.addAll(availableSimpleMovesForPiece(selectedRow, selectedCol))
        }
    }

    // Return list of simple (non-capturing) moves for this piece
    private fun availableSimpleMovesForPiece(r: Int, c: Int): List<Pair<Int, Int>> {
        val res = mutableListOf<Pair<Int, Int>>()
        val p = board[r][c]
        if (p == EMPTY) return res

        val directions = when (p) {
            RED -> listOf(-1 to -1, -1 to +1)          // up-left, up-right
            BLACK -> listOf(+1 to -1, +1 to +1)       // down-left, down-right
            RED_KING, BLACK_KING -> listOf(-1 to -1, -1 to +1, +1 to -1, +1 to +1)
            else -> emptyList()
        }

        for ((dr, dc) in directions) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 && board[nr][nc] == EMPTY) {
                res.add(Pair(nr, nc))
            }
        }
        return res
    }

    // Return list of captures (jump targets) for this piece
    private fun availableCapturesForPiece(r: Int, c: Int): List<Pair<Int, Int>> {
        val captures = mutableListOf<Pair<Int, Int>>()
        val p = board[r][c]
        if (p == EMPTY) return captures

        // For men (non-kings), capture only forward (American rules)
        val directions = when (p) {
            RED -> listOf(-1 to -1, -1 to +1)
            BLACK -> listOf(+1 to -1, +1 to +1)
            RED_KING, BLACK_KING -> listOf(-1 to -1, -1 to +1, +1 to -1, +1 to +1)
            else -> emptyList()
        }

        for ((dr, dc) in directions) {
            val midR = r + dr
            val midC = c + dc
            val toR = r + 2 * dr
            val toC = c + 2 * dc
            if (midR in 0..7 && midC in 0..7 && toR in 0..7 && toC in 0..7) {
                val midPiece = board[midR][midC]
                val destPiece = board[toR][toC]
                if (destPiece == EMPTY && midPiece != EMPTY && !isSamePlayer(midPiece, p)) {
                    captures.add(Pair(toR, toC))
                }
            }
        }
        return captures
    }

    private fun isSamePlayer(a: Int, b: Int): Boolean {
        if (a == EMPTY || b == EMPTY) return false
        val aIsRed = (a == RED || a == RED_KING)
        val bIsRed = (b == RED || b == RED_KING)
        return aIsRed == bIsRed
    }

    // Check for game over: if opponent has no pieces or no legal moves, current player wins
    private fun checkGameOver() {
        // Count pieces
        var redCount = 0; var blackCount = 0
        for (r in 0..7) for (c in 0..7) {
            when (board[r][c]) {
                RED, RED_KING -> redCount++
                BLACK, BLACK_KING -> blackCount++
            }
        }

        if (redCount == 0 || blackCount == 0) {
            val winner = if (redCount == 0) 2 else 1
            listener?.onGameOver(winner)
            return
        }

        // Check if the player whose turn it is has any legal moves; if not they lose.
        val playerHasMoves = playerHasAnyLegalMove(if (redTurn) RED else BLACK)
        if (!playerHasMoves) {
            // winner is the other player
            val winner = if (redTurn) 2 else 1
            listener?.onGameOver(winner)
        }
    }

    private fun playerHasAnyLegalMove(playerPiece: Int): Boolean {
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p == EMPTY) continue
            if (!isSamePlayer(p, playerPiece)) continue
            // if any capture exists -> move exists
            if (availableCapturesForPiece(r, c).isNotEmpty()) return true
            if (availableSimpleMovesForPiece(r, c).isNotEmpty()) return true
        }
        return false
    }

    // Utility: print board in log (for debugging)
    fun debugPrintBoard() {
        for (r in 0..7) {
            val rowStr = board[r].joinToString(" ") { when (it) {
                EMPTY -> "."
                RED -> "r"; BLACK -> "b"; RED_KING -> "R"; BLACK_KING -> "B"
                else -> "?"
            } }
            android.util.Log.d("CheckersView", rowStr)
        }
    }
}