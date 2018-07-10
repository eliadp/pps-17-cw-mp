package it.cwmp.controller.rooms

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.lang.scala.json.Json
import io.vertx.scala.ext.web.{Router, RoutingContext}
import it.cwmp.authentication.Validation
import it.cwmp.controller.client.ClientCommunication
import it.cwmp.controller.rooms.RoomsServiceVerticle._
import it.cwmp.model.{Room, User}
import it.cwmp.utils.HttpUtils
import javax.xml.ws.http.HTTPException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Class that implements the Rooms micro-service
  *
  * @author Enrico Siboni
  */
case class RoomsServiceVerticle(validationStrategy: Validation[String, User]) extends ScalaVerticle {

  private var daoFuture: Future[RoomDAO] = _

  override def startFuture(): Future[_] = {
    val storageHelper = RoomsLocalDAO(vertx)
    daoFuture = storageHelper.initialize().map(_ => storageHelper)

    import it.cwmp.controller.rooms.RoomsApiWrapper._
    val router = Router.router(vertx)
    router post API_CREATE_PRIVATE_ROOM_URL handler createPrivateRoomHandler
    router put API_ENTER_PRIVATE_ROOM_URL handler enterPrivateRoomHandler
    router get API_PRIVATE_ROOM_INFO_URL handler privateRoomInfoHandler
    router delete API_EXIT_PRIVATE_ROOM_URL handler exitPrivateRoomHandler

    router get API_LIST_PUBLIC_ROOMS_URL handler listPublicRoomsHandler
    router put API_ENTER_PUBLIC_ROOM_URL handler enterPublicRoomHandler
    router get API_PUBLIC_ROOM_INFO_URL handler publicRoomInfoHandler
    router delete API_EXIT_PUBLIC_ROOM_URL handler exitPublicRoomHandler

    vertx
      .createHttpServer()
      .requestHandler(router.accept _)
      .listenFuture(DEFAULT_PORT)
  }

  /**
    * Handles the creation of a private room
    */
  private def createPrivateRoomHandler: Handler[RoutingContext] = implicit routingContext => {
    routingContext.request().bodyHandler(body =>
      validateUserOrSendError.map(_ =>
        extractIncomingRoomFromBody(body) match {
          case Some((roomName, neededPlayers)) =>
            daoFuture.map(_.createRoom(roomName, neededPlayers)
              .onComplete {
                case Success(generatedID) => sendResponse(201, Some(generatedID))
                case Failure(ex) => sendResponse(400, Some(ex.getMessage))
              })
          case None =>
            sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR no Room JSON in body"))
        }))

    def extractIncomingRoomFromBody(body: Buffer): Option[(String, Int)] = {
      try {
        val jsonObject = body.toJsonObject
        if ((jsonObject containsKey Room.FIELD_NAME) && (jsonObject containsKey Room.FIELD_NEEDED_PLAYERS))
          Some((jsonObject getString Room.FIELD_NAME, jsonObject getInteger Room.FIELD_NEEDED_PLAYERS))
        else None
      } catch {
        case _: Throwable => None
      }
    }
  }

  /**
    * Handles entering in a private room
    */
  private def enterPrivateRoomHandler: Handler[RoutingContext] = implicit routingContext => {
    routingContext.request().bodyHandler(body =>
      validateUserOrSendError.map(user => {
        (extractRequestParam(Room.FIELD_IDENTIFIER), extractAddressFromBody(body)) match {
          case (Some(roomID), Some(userAddress)) =>
            daoFuture.map(implicit dao => dao.enterRoom(roomID)(User(user.username, userAddress)).onComplete {
              case Success(_) => handlePrivateRoomFilling(roomID).onComplete { case Failure(_) => sendResponse(200, None) }
              case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
              case Failure(ex) => sendResponse(400, Some(ex.getMessage))
            })

          case _ => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_IDENTIFIER} or ${User.FIELD_ADDRESS}"))
        }
      }))
  }

  /**
    * Handles retrieving info of a private room
    */
  private def privateRoomInfoHandler: Handler[RoutingContext] = implicit routingContext => {
    validateUserOrSendError.map(_ => {
      extractRequestParam(Room.FIELD_IDENTIFIER) match {
        case Some(roomId) =>
          daoFuture.map(_.roomInfo(roomId).onComplete {
            case Success(room) =>
              import Room.Converters._
              sendResponse(200, Some(room.toJson.encode()))
            case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
            case Failure(ex) => sendResponse(400, Some(ex.getMessage))
          })
        case None => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_IDENTIFIER}"))
      }
    })
  }

  /**
    * Handles exititng a private room
    */
  private def exitPrivateRoomHandler: Handler[RoutingContext] = implicit routingContext => {
    validateUserOrSendError.map(implicit user => {
      extractRequestParam(Room.FIELD_IDENTIFIER) match {
        case Some(roomId) =>
          daoFuture.map(_.exitRoom(roomId).onComplete {
            case Success(_) => sendResponse(200, None)
            case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
            case Failure(ex) => sendResponse(400, Some(ex.getMessage))
          })
        case None => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_IDENTIFIER}"))
      }
    })
  }

  /**
    * Handles listing of public rooms
    */
  private def listPublicRoomsHandler: Handler[RoutingContext] = implicit routingContext => {
    validateUserOrSendError.map(_ => {
      daoFuture.map(_.listPublicRooms().onComplete {
        case Success(rooms) =>
          import Room.Converters._
          val jsonArray = rooms.foldLeft(Json emptyArr())(_ add _.toJson)
          sendResponse(200, Some(jsonArray encode()))
        case Failure(ex) => sendResponse(400, Some(ex.getMessage))
      })
    })
  }

  /**
    * Handles entering in a public room
    */
  private def enterPublicRoomHandler: Handler[RoutingContext] = implicit routingContext => {
    routingContext.request().bodyHandler(body =>
      validateUserOrSendError.map(user => {
        val playersNumberOption = try extractRequestParam(Room.FIELD_NEEDED_PLAYERS).map(_.toInt)
        catch {
          case _: Throwable => None
        }
        (playersNumberOption, extractAddressFromBody(body)) match {
          case (Some(playersNumber), Some(userAddress)) =>
            daoFuture.map(implicit dao => dao.enterPublicRoom(playersNumber)(User(user.username, userAddress)).onComplete {
              case Success(_) => handlePublicRoomFilling(playersNumber).onComplete { case Failure(_) => sendResponse(200, None) }
              case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
              case Failure(ex) => sendResponse(400, Some(ex.getMessage))
            })

          case _ => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_NEEDED_PLAYERS} or ${User.FIELD_ADDRESS}"))
        }
      }))
  }

  /**
    * Handles retrieving info of a public room
    */
  private def publicRoomInfoHandler: Handler[RoutingContext] = implicit routingContext => {
    validateUserOrSendError.map(_ => {
      (try extractRequestParam(Room.FIELD_NEEDED_PLAYERS).map(_.toInt)
      catch {
        case _: Throwable => None
      }) match {
        case Some(playersNumber) =>
          daoFuture.map(_.publicRoomInfo(playersNumber).onComplete {
            case Success(room) =>
              import Room.Converters._
              sendResponse(200, Some(room.toJson.encode()))
            case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
            case Failure(ex) => sendResponse(400, Some(ex.getMessage))
          })
        case None => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_NEEDED_PLAYERS}"))
      }
    })
  }

  /**
    * Handles exiting a public room
    */
  private def exitPublicRoomHandler: Handler[RoutingContext] = implicit routingContext => {
    validateUserOrSendError.map(implicit user => {
      (try extractRequestParam(Room.FIELD_NEEDED_PLAYERS).map(_.toInt)
      catch {
        case _: Throwable => None
      }) match {
        case Some(roomId) =>
          daoFuture.map(_.exitPublicRoom(roomId).onComplete {
            case Success(_) => sendResponse(200, None)
            case Failure(ex: NoSuchElementException) => sendResponse(404, Some(ex.getMessage))
            case Failure(ex) => sendResponse(400, Some(ex.getMessage))
          })
        case None => sendResponse(400, Some(s"$INVALID_PARAMETER_ERROR ${Room.FIELD_NEEDED_PLAYERS}"))
      }
    })
  }


  /**
    * Checks whether the user is authenticated;
    * if token not provided sends back 400
    * if token invalid sends back 401
    *
    * @param routingContext the context in which to check
    * @return the future that will contain the user if successfully validated
    */
  private def validateUserOrSendError(implicit routingContext: RoutingContext): Future[User] = {
    HttpUtils.getRequestAuthorizationHeader(routingContext.request()) match {
      case None =>
        sendResponse(400, Some(TOKEN_NOT_PROVIDED_OR_INVALID))
        Future.failed(new IllegalAccessException(TOKEN_NOT_PROVIDED_OR_INVALID))

      case Some(authorizationToken) =>
        validationStrategy.validate(authorizationToken)
          .recoverWith {
            case ex: HTTPException if ex.getStatusCode == 401 =>
              sendResponse(ex.getStatusCode, Some(USER_NOT_AUTHENTICATED))
              Future.failed(new IllegalAccessException(USER_NOT_AUTHENTICATED))
            case ex: HTTPException =>
              sendResponse(ex.getStatusCode, Some(TOKEN_NOT_PROVIDED_OR_INVALID))
              Future.failed(new IllegalAccessException(TOKEN_NOT_PROVIDED_OR_INVALID))
          }
    }
  }
}

/**
  * Companion object
  *
  * @author Enrico Siboni
  */
object RoomsServiceVerticle {

  private val USER_NOT_AUTHENTICATED = "User is not authenticated"
  private val TOKEN_NOT_PROVIDED_OR_INVALID = "Token not provided or invalid"
  private val INVALID_PARAMETER_ERROR = "Invalid parameters: "

  /**
    * @param routingContext the routing context on which to extract
    * @return the extracted room name
    */
  private def extractRequestParam(paramName: String)(implicit routingContext: RoutingContext): Option[String] = {
    routingContext.request().getParam(paramName)
  }

  /**
    * @param body the body where to extract
    * @return the extracted Address
    */
  private def extractAddressFromBody(body: Buffer): Option[String] = {
    try {
      val jsonObject = body.toJsonObject
      if (jsonObject containsKey User.FIELD_ADDRESS) Some(jsonObject getString User.FIELD_ADDRESS)
      else None
    } catch {
      case _: Throwable => None
    }
  }

  /**
    * Method that manages the filling of private rooms, fails if the room is not full
    */
  private def handlePrivateRoomFilling(roomID: String)(implicit roomDAO: RoomDAO, routingContext: RoutingContext): Future[Unit] = {
    handleRoomFilling(roomDAO.roomInfo(roomID),
      roomDAO deleteRoom roomID map (_ => sendResponse(200, None)))
  }

  /**
    * Method that manages the filling of public rooms, fails if the room is not full
    */
  private def handlePublicRoomFilling(playersNumber: Int)(implicit roomDAO: RoomDAO, routingContext: RoutingContext): Future[Unit] = {
    handleRoomFilling(roomDAO.publicRoomInfo(playersNumber),
      roomDAO deleteAndRecreatePublicRoom playersNumber map (_ => sendResponse(200, None)))
  }

  /**
    * Describes how to behave when a room is filled out
    *
    * @param roomFuture            the future that will contain the room
    * @param onRetrievedRoomAction the action to do if the room is full
    * @return a future that completes when all players received addresses
    */
  private[this] def handleRoomFilling(roomFuture: Future[Room], onRetrievedRoomAction: => Future[Unit]): Future[Unit] = {
    roomFuture.filter(roomIsFull)
      .flatMap(fullRoom => {
        onRetrievedRoomAction
        sendParticipantAddresses(fullRoom)
      })
  }

  /**
    * Describes when a room is full
    */
  private def roomIsFull(room: Room): Boolean = room.participants.size == room.neededPlayersNumber

  /**
    * Method to cummunicate to clients that the game can start because of reaching the specified number of players
    *
    * @param room the room where players are waiting in
    */
  private def sendParticipantAddresses(room: Room): Future[Unit] = {
    val participantAddresses = for (participant <- room.participants) yield participant.address
    Future.sequence {
      participantAddresses map (address => ClientCommunication(address).sendParticipantAddresses(participantAddresses filter (_ != address)))
    } map (_ => Unit)
  }

  /**
    * Utility method to send back responses
    *
    * @param routingContext the routing context in wich to send the error
    * @param httpCode       the http code
    * @param message        the message to send back
    */
  private def sendResponse(httpCode: Int,
                           message: Option[String])
                          (implicit routingContext: RoutingContext): Unit = {
    val response = routingContext.response().setStatusCode(httpCode)

    message match {
      case Some(messageString) => response.end(messageString)
      case None => response.end()
    }
  }
}