package it.cwmp.client.view

import it.cwmp.client.controller.AuthenticationController
import it.cwmp.client.utils.{LayoutRes, StringRes}
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage

class SignUpView extends View {

  var controller: AuthenticationController = _

  /**
    * Main method that starts this view.
    **/
  override def start(): Unit = {
    val mainStage = new Stage
    mainStage setTitle StringRes.appName
    mainStage setResizable false

    val loader: FXMLLoader = new FXMLLoader(getClass.getResource(LayoutRes.signUpLayout))
    val root: Pane = loader.load[Pane]
    controller = loader.getController[AuthenticationController]
    controller.stage = mainStage
    val scene: Scene = new Scene(root)

    mainStage setScene scene
    mainStage.show()
  }
}
