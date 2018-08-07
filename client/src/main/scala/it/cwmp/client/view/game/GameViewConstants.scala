package it.cwmp.client.view.game

import javafx.scene.paint.Color
import javafx.scene.text.Font

/**
  * An object where to put constants about the game visual
  *
  * @author Enrico Siboni
  */
object GameViewConstants {

  /**
    * The constant value indicating the rgb range max value
    */
  val RGB_RANGE = 255.0

  /**
    * The game default font size
    */
  val GAME_DEFAULT_FONT_SIZE = 20

  /**
    * The game default font color
    */
  val GAME_DEFAULT_FONT_COLOR: Color = Color.BLACK


  // GAME TIME TEXT CONSTANTS

  /**
    * The game time default font
    */
  val GAME_TIME_TEXT_FONT: Font = Font.font("Verdana", GAME_DEFAULT_FONT_SIZE)

  /**
    * The constant value indicating the transparency of instant text in the GUI
    */
  val GAME_TIME_TEXT_TRANSPARENCY: Double = 0.5

  /**
    * The game time default font color
    */
  val GAME_TIME_TEXT_COLOR: Color =
    Color.color(GAME_DEFAULT_FONT_COLOR.getRed, GameViewConstants.GAME_DEFAULT_FONT_COLOR.getGreen,
      GameViewConstants.GAME_DEFAULT_FONT_COLOR.getBlue, GameViewConstants.GAME_TIME_TEXT_TRANSPARENCY)

  /**
    * The game time default format
    */
  val GAME_TIME_TEXT_FORMAT: String = "%02dm : %02ds"
}
