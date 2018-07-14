package it.cwmp.client.view.authentication

import akka.actor.{Actor, ActorRef}
import it.cwmp.client.controller.ClientControllerMessages
import it.cwmp.client.view.{AlertActor, FXAlerts}
import javafx.application.Platform
import javafx.embed.swing.JFXPanel

/**
  * Object that contains all the messages that this actor can receive.
  */
object AuthenticationViewMessages {

  case object InitController

  case object ShowGUI
}

object AuthenticationViewActor {
  def apply(): AuthenticationViewActor = new AuthenticationViewActor()
}

class AuthenticationViewActor extends Actor with AlertActor {

  var signInFXController: SignInFXController = _
  var controllerActor: ActorRef = _

  var fxController: SignUpFXController = _

  override def preStart(): Unit = {
    super.preStart()

    //inizializzo il toolkit
    new JFXPanel
    Platform setImplicitExit false
    Platform runLater(() => {
      // TODO: ha senso definire tutto qui dentro?
      signInFXController = SignInFXController(new SignInFXStrategy {
        override def onSignIn(username: String, password: String): Unit =
          controllerActor ! ClientControllerMessages.AuthenticationPerformSignIn(username, password)

        override def onRequestSignUp(): Unit = {
          fxController = SignUpFXController((username: String, password: String) =>
          controllerActor ! ClientControllerMessages.AuthenticationPerformSignUp(username, password))
          fxController showGUI()
        }
      })
    })
  }

  override def receive: Receive = {
    case AuthenticationViewMessages.InitController => controllerActor = sender()
    case AuthenticationViewMessages.ShowGUI => Platform runLater(() => signInFXController showGUI())
  }
}