package com.serwylo.retrowars.games.snake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.serwylo.beatgame.ui.UI_SPACE
import com.serwylo.retrowars.RetrowarsGame
import com.serwylo.retrowars.games.GameScreen
import com.serwylo.retrowars.games.Games
import com.serwylo.retrowars.ui.IconButton

class SnakeGameScreen(game: RetrowarsGame) : GameScreen(game, Games.snake, 400f, 400f) {

    companion object {
        @Suppress("unused")
        const val TAG = "SnakeGameScreen"
    }

    private val controllerLeft: Button
    private val controllerRight: Button
    private val controllerUp: Button
    private val controllerDown: Button

    private val state = SnakeGameState()

    /**
     * Used to provide an on-screen controller for driving the ship. Left, Right, Thrust, and Fire.
     */
    private val softController = Table()

    init {

        val skin = game.uiAssets.getSkin()
        val sprites = game.uiAssets.getSprites()

        controllerLeft = IconButton(skin, sprites.buttonIcons.left)
        controllerRight = IconButton(skin, sprites.buttonIcons.right)
        controllerUp = IconButton(skin, sprites.buttonIcons.up)
        controllerDown = IconButton(skin, sprites.buttonIcons.down)

        controllerLeft.addAction(Actions.alpha(0.4f))
        controllerRight.addAction(Actions.alpha(0.4f))
        controllerUp.addAction(Actions.alpha(0.4f))
        controllerDown.addAction(Actions.alpha(0.4f))

        val buttonSize = UI_SPACE * 15
        softController.apply {
            bottom().pad(UI_SPACE * 4)
            add(controllerLeft).space(UI_SPACE * 2).size(buttonSize)
            add(controllerRight).space(UI_SPACE * 2).size(buttonSize)
            add().expandX()
            add(controllerUp).space(UI_SPACE * 2).size(buttonSize)
            add(controllerDown).space(UI_SPACE * 2).size(buttonSize)
        }

        addGameOverlayToHUD(softController)
        showMessage("Eat the fruit", "Avoid your tail")

    }

    override fun show() {
        Gdx.input.inputProcessor = getInputProcessor()
    }

    override fun getScore() = state.score

    override fun updateGame(delta: Float) {

        state.timer += delta

        recordInput()
        decideNextDirection()
        moveSnake()

    }

    private fun recordInput() {
        state.left = controllerLeft.isPressed || Gdx.input.isKeyPressed(Input.Keys.LEFT)
        state.right = controllerRight.isPressed || Gdx.input.isKeyPressed(Input.Keys.RIGHT)
        state.up = controllerUp.isPressed || Gdx.input.isKeyPressed(Input.Keys.UP)
        state.down = controllerDown.isPressed || Gdx.input.isKeyPressed(Input.Keys.DOWN)
    }

    /**
     * Based on the current keys pressed and the current direction, queue up the next direction to
     * travel. This will be applied the next time the snake needs to inch forward. If you press one
     * direction then another very fast, you can queue up the next direction many times before the
     * snake actually moves.
      */
    private fun decideNextDirection() {
        if (state.left && state.currentDirection != Direction.RIGHT && !state.right && !state.up && !state.down) {
            state.nextDirection = Direction.LEFT
        } else if (state.right && state.currentDirection != Direction.LEFT && !state.left && !state.up && !state.down) {
            state.nextDirection = Direction.RIGHT
        } else if (state.up && state.currentDirection != Direction.DOWN && !state.left && !state.right && !state.down) {
            state.nextDirection = Direction.UP
        } else if (state.down && state.currentDirection != Direction.UP && !state.left && !state.right && !state.up) {
            state.nextDirection = Direction.DOWN
        }
    }

    private fun moveSnake() {
        if (state.timer < state.nextTimeStep) {
            return
        }

        state.nextTimeStep = state.nextTimeStep + state.timeStep
        state.currentDirection = state.nextDirection

        val currentHead = state.snake.first
        val newHead = moveTo(state.currentDirection, currentHead)

        if (newHead == null || state.snake.contains(newHead)) {
            endGame()
            return
        }

        Gdx.app.log(TAG, "Moving ${state.currentDirection} from $currentHead -> $newHead")
        state.snake.addFirst(newHead)

        if (newHead == state.food) {

            increaseSpeed()
            spawnFood()

            state.score += 10000

        } else if (state.queuedGrowth > 0) {

            // In response to receiving a handicap from the network.
            // Move the head forward, but leave the tail where it was. Do this as many time
            // steps as necessary.
            state.queuedGrowth --

        } else {

            state.snake.removeLast()

        }

    }

    private fun moveTo(direction: Direction, current: SnakeGameState.Cell) =
        when(direction) {
            Direction.UP -> if (current.y < SnakeGameState.CELLS_HIGH - 1) state.cells[current.y + 1][current.x] else null
            Direction.DOWN -> if (current.y > 0) state.cells[current.y - 1][current.x] else null
            Direction.LEFT -> if (current.x > 0) state.cells[current.y][current.x - 1] else null
            Direction.RIGHT -> if (current.x < SnakeGameState.CELLS_WIDE - 1) state.cells[current.y][current.x + 1] else null
        }

    private fun increaseSpeed() {
        // state.timeStep = (state.timeStep - 0.02f).coerceAtLeast(state.minTimeStep)
    }

    private fun spawnFood() {
        // Keep respawning food until we find a place that doesn't clash with the snakes tail.
        do {
            val x = (0 until SnakeGameState.CELLS_WIDE).random()
            val y = (0 until SnakeGameState.CELLS_HIGH).random()
            state.food = state.cells[y][x]
        } while (state.snake.contains(state.food))
    }

    override fun onReceiveDamage(strength: Int) {
        state.queuedGrowth += strength
    }

    override fun renderGame(camera: OrthographicCamera) {

        val numCellsHigh = state.cells.size
        val numCellsWide = state.cells[0].size

        val cellWidth = viewport.worldWidth / numCellsWide
        val cellHeight = viewport.worldHeight / numCellsHigh

        val r = game.uiAssets.shapeRenderer
        r.projectionMatrix = camera.combined

        r.begin(ShapeRenderer.ShapeType.Line)
        r.color = Color.DARK_GRAY

        // For debugging, it can help to draw every cell:
        /*state.cells.forEachIndexed { y, row ->
            row.forEachIndexed { x, cell ->
                r.rect(x * cellWidth, y * cellHeight, cellWidth, cellHeight)
            }
        }*/

        r.end()
        r.begin(ShapeRenderer.ShapeType.Filled)

        r.color = Color.WHITE
        state.snake.forEach { cell ->
            r.rect(cell.x * cellWidth + 1, cell.y * cellHeight + 1, cellWidth - 2, cellHeight - 2)
        }

        val food = state.food
        r.color = Color.GREEN
        if (food != null) {
            r.rect(food.x * cellWidth + 1, food.y * cellHeight + 1, cellWidth - 2, cellHeight - 2)
        }

        r.end()
    }

}