package com.zybooks.boardbeats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Full American / English checkers implementation:
 * - 8x8 board, dark squares used
 * - Pieces: 0 = empty, 1 = red man, 2 = black man, 3 = red king, 4 = black king
 * - Black (2) moves first (dark pieces move first in standard rules)
 * - Men move diagonally forward one square; capture diagonally forward only
 * - Kings move and capture diagonally both directions (no flying kings)
 * - Captures are mandatory; multi-jumps are enforced; promotion ends turn
 */
class CheckersView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    interface OnWinListener {
        fun onPlayerWin(winningPlayer: Int);
    }
    var winListener: OnWinListener? = null;

    private val possibleMoves = mutableListOf<Pair<Int, Int>>();
    private val possibleMovePaint = Paint().apply {
        color = Color.GREEN;
        style = Paint.Style.STROKE;
        strokeWidth = 6f;
        isAntiAlias = true;
    };

    private val boardPaint = Paint().apply { isAntiAlias = true };
    private val highlightPaint = Paint().apply {
        color = Color.YELLOW;
        style = Paint.Style.STROKE;
        strokeWidth = 8f;
        isAntiAlias = true;
    };
    private val captureHighlightPaint = Paint().apply {
        color = Color.parseColor("#88FFEB3B"); // translucent highlight for capture destinations
        style = Paint.Style.FILL;
        isAntiAlias = true;
    };
    private val moveDotPaint = Paint().apply {
        style = Paint.Style.FILL;
        isAntiAlias = true;
    };

    private val piecePaintRed = Paint().apply { color = Color.RED; isAntiAlias = true };
    private val piecePaintBlack = Paint().apply { color = Color.BLACK; isAntiAlias = true };
    private val kingMarkPaint = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; textSize = 24f };

    private var cellSize = 0f;
    private val board = Array(8) { IntArray(8) }; // 0=empty,1=red,2=black,3=red king,4=black king

    // selection
    private var selectedRow = -1;
    private var selectedCol = -1;

    // Current player: 2 = black (dark) goes first per standard rules, 1 = red
    private var currentPlayer = 2;

    // When a capture sequence is in progress for a single piece, this is true.
    // The player must continue jumping with the same piece until no more captures available.
    private var mustContinueCapturing = false;

    // Cached valid moves for the currently selected piece (or empty if none).
    private var currentValidMoves: List<Move> = emptyList();

    init { setupInitialPieces() };

    private fun setupInitialPieces() {
        // Clear
        for (r in 0..7) for (c in 0..7)
            board[r][c] = 0;

        // Black pieces top (rows 0..2) on dark squares
        for (row in 0..2) {
            for (col in 0..7) if ((row + col) % 2 == 1) board[row][col] = 2;
        }
        // Red pieces bottom (rows 5..7) on dark squares
        for (row in 5..7) {
            for (col in 0..7)
                if ((row + col) % 2 == 1)
                    board[row][col] = 1;
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
        cellSize = size / 8f;
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBoard(canvas)
        drawPossibleMoves(canvas)
        drawPieces(canvas)
        drawHighlight(canvas);
    };

    private fun drawBoard(canvas: Canvas) {
        for (row in 0..7) {
            for (col in 0..7) {
                boardPaint.color = if ((row + col) % 2 == 0) Color.LTGRAY else Color.DKGRAY;
                canvas.drawRect(col * cellSize,
                    row * cellSize, (col + 1) * cellSize,
                    (row + 1) * cellSize, boardPaint);
            }
        }
    }

    private fun drawPieces(canvas: Canvas) {
        val padding = cellSize * 0.1f;
        val kingTextSize = cellSize * 0.35f;
        kingMarkPaint.textSize = kingTextSize;

        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board[row][col];
                if (piece != 0) {
                    val cx = col * cellSize + cellSize / 2;
                    val cy = row * cellSize + cellSize / 2;
                    val radius = cellSize / 2 - padding;
                    val paint = if (isRedPiece(piece)) piecePaintRed else piecePaintBlack;
                    canvas.drawCircle(cx, cy, radius, paint);

                    // mark kings with a small crown/letter
                    if (isKing(piece)) {
                        // Draw "K" centered
                        val textY = cy - (kingMarkPaint.descent() + kingMarkPaint.ascent()) / 2;
                        canvas.drawText("K", cx, textY, kingMarkPaint);
                    }
                }
            }
        }
    }

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
    }

    private fun drawPossibleMoves(canvas: Canvas) {
        if (currentValidMoves.isEmpty())
            return;

        for (m in currentValidMoves) {
            // If capture move, fill the destination square with translucent highlight
            if (m.isCapture) {
                canvas.drawRect(
                    m.toCol * cellSize,
                    m.toRow * cellSize,
                    (m.toCol + 1) * cellSize,
                    (m.toRow + 1) * cellSize,
                    captureHighlightPaint
                );
            } else {
                // Non-capture move: draw a small dot in the center
                val cx = m.toCol * cellSize + cellSize / 2;
                val cy = m.toRow * cellSize + cellSize / 2;
                val dotRadius = cellSize * 0.08f;
                // use opposite color for dot so it stands out
                moveDotPaint.color = if (currentPlayer == 2) Color.RED else Color.BLACK;
                canvas.drawCircle(cx, cy, dotRadius, moveDotPaint);
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN)
            return true;

        val col = (event.x / cellSize).toInt();
        val row = (event.y / cellSize).toInt();
        if (row !in 0..7 || col !in 0..7)
            return true;

        val clickedPiece = board[row][col];

        // If currently in the middle of a forced capture sequence, only allow selecting the continuing piece (same one)
        if (mustContinueCapturing) {
            // only accept taps on the selected piece or on a valid destination for that piece
            if (row == selectedRow && col == selectedCol) {
                // re-select the same piece (no-op)
                return true;
            }
            // if tapped on one of the valid moves, perform it
            val move = currentValidMoves.find { it.toRow == row && it.toCol == col };
            if (move != null) {
                performMove(move);
                invalidate();
                return true;
            } else {
                // ignore taps elsewhere while multi-jump required
                return true;
            }
        }

        // If there is a selected piece already and user tapped a valid destination -> move
        if (selectedRow != -1 && selectedCol != -1) {
            val move = currentValidMoves.find { it.toRow == row && it.toCol == col };
            if (move != null) {
                performMove(move);
                invalidate();
                return true;
            } else {
                // tapped somewhere else:
                // if tapped another piece belonging to current player, change selection
                if (clickedPiece != 0 && pieceBelongsToPlayer(clickedPiece, currentPlayer)) {
                    selectPiece(row, col);
                } else {
                    // clear selection
                    clearSelection();
                }
                invalidate();
                return true;
            }
        }

        // No selection currently. If clicking a piece of the current player, select it and show valid moves.
        if (clickedPiece != 0 && pieceBelongsToPlayer(clickedPiece, currentPlayer)) {
            selectPiece(row, col);
            invalidate();
            return true;
        }

        // Clicking an empty square with nothing selected does nothing
        return true;
    }

    // Selects a piece and computes valid moves (but if any capture exists anywhere, non-capture moves will be empty)
    private fun selectPiece(row: Int, col: Int) {
        selectedRow = row;
        selectedCol = col;
        currentValidMoves = computeValidMovesForSelectedPiece();
        // If captures are available globally, filter to captures only; computeValidMovesForSelectedPiece already does this,
        // but we ensure mandatory capture behavior across the whole board is respected.
        invalidate();
    }

    private fun clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        currentValidMoves = emptyList();
        mustContinueCapturing = false;
    }

    // Perform the chosen move (handles captures, multi-jumps, promotion, switching turns, win detection)
    private fun performMove(move: Move) {
        val fromR = selectedRow;
        val fromC = selectedCol;
        val toR = move.toRow;
        val toC = move.toCol;
        if (fromR !in 0..7 || fromC !in 0..7)
            return;

        val movingPiece = board[fromR][fromC];
        board[toR][toC] = movingPiece;
        board[fromR][fromC] = 0;

        // If capture, remove captured piece
        if (move.isCapture && move.captureRow != null && move.captureCol != null) {
            board[move.captureRow][move.captureCol] = 0;
        }

        // Promotion: a man reaching the farthest row becomes a king; promotion ends turn (no further jumps)
        var crownedThisMove = false;
        if (isMan(movingPiece)) {
            if (movingPiece == 1 && toR == 0) { // red reaches top
                board[toR][toC] = 3;
                crownedThisMove = true;
            } else if (movingPiece == 2 && toR == 7) { // black reaches bottom
                board[toR][toC] = 4;
                crownedThisMove = true;
            }
        }

        // After an (initial) capture, check for additional captures from the new position (multi-jump)
        if (move.isCapture && !crownedThisMove) {
            // compute captures only from this piece (only captures allowed)
            val furtherCaptures = computeCapturesForPiece(toR, toC, board[toR][toC]);
            if (furtherCaptures.isNotEmpty()) {
                // must continue capturing with the same piece
                selectedRow = toR;
                selectedCol = toC;
                currentValidMoves = furtherCaptures;
                mustContinueCapturing = true;
                // do not switch player yet
                return;
            }
        }

        // Either non-capture move, or capture sequence finished, or promotion ended turn
        mustContinueCapturing = false;
        clearSelection();

        // Switch player
        currentPlayer = if (currentPlayer == 1) 2 else 1;

        // Check win condition: if the player who now must move (currentPlayer) has no pieces or no legal moves, the opponent wins
        val opponent = if (currentPlayer == 1) 2 else 1;
        val opponentPieces = countPiecesForPlayer(currentPlayer);
        val opponentHasMoves = hasAnyLegalMove(currentPlayer);
        if (opponentPieces == 0 || !opponentHasMoves) {
            // The player that just moved is the winner (opponent cannot move / has no pieces).
            val winner = if (currentPlayer == 1) 2 else 1;
            winListener?.onPlayerWin(winner);
        }
    }

    // Move structure
    private data class Move(val toRow: Int, val toCol: Int,
                            val isCapture: Boolean, val captureRow: Int? = null,
                            val captureCol: Int? = null);

    // Compute valid moves for currently selected piece (honors global mandatory-capture rule)
    private fun computeValidMovesForSelectedPiece(): List<Move> {
        if (selectedRow !in 0..7 || selectedCol !in 0..7)
            return emptyList();
        val piece = board[selectedRow][selectedCol];
        if (piece == 0) return emptyList();

        // First, check whether any capture exists anywhere on the board for current player (mandatory capture)
        val anyCapture = anyCaptureExistsForPlayer(currentPlayer);

        // Disgusting return statement but it looks fancy
        return if (anyCapture) {
            // only return capture moves for this piece
            computeCapturesForPiece(selectedRow, selectedCol, piece);
        } else {
            // return simple moves (non-capture)
            computeNonCaptureMovesForPiece(selectedRow, selectedCol, piece);
        }
    }

    // Returns list of non-capture moves for a piece (single-step diagonal forward for men; both directions for king)
    private fun computeNonCaptureMovesForPiece(r: Int, c: Int, piece: Int): List<Move> {
        val moves = mutableListOf<Move>();
        val directions = movementDirectionsForPiece(piece, captureMode = false); // for move, captureMode false
        for ((dr, dc) in directions) {
            val nr = r + dr;
            val nc = c + dc;
            if (nr in 0..7 && nc in 0..7 && board[nr][nc] == 0) {
                moves.add(Move(nr, nc, isCapture = false));
            }
        }
        return moves;
    }

    // Returns list of capture moves for a piece (single jump captures). For multi-jump sequences we let the UI loop by computing further captures after performing a capture.
    private fun computeCapturesForPiece(r: Int, c: Int, piece: Int): List<Move> {
        val moves = mutableListOf<Move>();
        // For captures we consider directions where a man may capture: in American checkers, men capture forward only
        val directions = movementDirectionsForPiece(piece, captureMode = true);
        for ((dr, dc) in directions) {
            val midR = r + dr;
            val midC = c + dc;
            val landingR = r + 2 * dr;
            val landingC = c + 2 * dc;
            if (landingR in 0..7 && landingC in 0..7 && midR in 0..7 && midC in 0..7) {
                val midPiece = board[midR][midC];
                if (midPiece != 0 && !pieceBelongsToPlayer(midPiece, currentPlayer) && board[landingR][landingC] == 0) {
                    moves.add(Move(landingR, landingC,
                        isCapture = true, captureRow = midR, captureCol = midC));
                }
            }
        }
        return moves;
    }

    // Determine movement/capture direction vectors based on piece type.
    // For men:
    //  - For simple moves: men move forward only (red up, black down).
    //  - For captures: in American checkers men capture forward only (per standard US/English rules).
    // For kings: both directions.
    private fun movementDirectionsForPiece(piece: Int, captureMode: Boolean): List<Pair<Int, Int>> {
        return when {
            isKing(piece) -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1); // kings can move/cap both ways (single-step or capture-step)
            piece == 1 -> listOf(-1 to -1, -1 to 1); // red man moves/captures "up" (decreasing row)
            piece == 2 -> listOf(1 to -1, 1 to 1); // black man moves/captures "down" (increasing row)
            else -> emptyList();
        }
    }

    // Check whether any capture exists anywhere on the board for currentPlayer
    private fun anyCaptureExistsForPlayer(player: Int): Boolean {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c];
                if (p != 0 && pieceBelongsToPlayer(p, player)) {
                    if (computeCapturesForPiece(r, c, p).isNotEmpty())
                        return true;
                }
            }
        }
        return false;
    }

    // Check whether a given player has any legal move at all (taking mandatory-capture rule into account)
    private fun hasAnyLegalMove(player: Int): Boolean {
        // If any capture exists -> player has a move
        if (anyCaptureExistsForPlayer(player))
            return true;
        // else if any non-capture move exists -> player has a move
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c];
                if (p != 0 && pieceBelongsToPlayer(p, player)) {
                    if (computeNonCaptureMovesForPiece(r, c, p).isNotEmpty())
                        return true;
                }
            }
        }
        return false;
    }

    private fun countPiecesForPlayer(player: Int): Int {
        var cnt = 0;
        for (r in 0..7) for (c in 0..7)
            if (board[r][c] != 0 && pieceBelongsToPlayer(board[r][c], player))
                cnt++;
        return cnt;
    }

    // Utilities
    private fun isKing(piece: Int) = piece == 3 || piece == 4;
    private fun isMan(piece: Int) = piece == 1 || piece == 2;
    private fun isRedPiece(piece: Int) = piece == 1 || piece == 3;
    private fun isBlackPiece(piece: Int) = piece == 2 || piece == 4;
    private fun pieceBelongsToPlayer(piece: Int, player: Int) = (player == 1 && isRedPiece(piece))
            || (player == 2 && isBlackPiece(piece));

    // Public API to reset the game
    fun resetGame() {
        setupInitialPieces();
        selectedRow = -1;
        selectedCol = -1;
        currentValidMoves = emptyList();
        mustContinueCapturing = false;
        currentPlayer = 2; // black starts (Why must black always be second? Power to the people)
        invalidate();
    }
}
