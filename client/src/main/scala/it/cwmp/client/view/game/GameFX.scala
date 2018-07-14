package it.cwmp.client.view.game

import it.cwmp.client.view.game.model._
import javafx.application.Application
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.stage.Stage

/**
  * Questa classe permette di visualizzare una GUI statica che rappresenta uno stato del gioco
  *
  * @author Davide Borficchia
  */
class GameFX extends Application with ObjectDrawer {

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Prima schermata del gioco!")
    val root = new Group
    val theScene: Scene = new Scene(root)

    //setto la dimensione della finestra
    val canvas = new Canvas(512, 512)
    root.getChildren.add(canvas)
    implicit val graphicsContex = canvas.getGraphicsContext2D

    val cells = Cell(Point(20,20)) :: Cell(Point(90,400)) :: Cell(Point(200,150)) :: Nil

    cells.foreach(drawCell)

    drawArch(cells(0), cells(1))
    drawArch(cells(1), cells(2))
    drawArch(cells(2), cells(0))

    primaryStage setScene theScene
    primaryStage.show()
  }
}