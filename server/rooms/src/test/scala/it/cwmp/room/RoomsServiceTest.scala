package it.cwmp.room

import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.lang.scala.json.{Json, JsonObject}
import io.vertx.scala.ext.web.client.{WebClient, WebClientOptions}
import it.cwmp.authentication.{AuthenticationService, AuthenticationServiceVerticle}
import it.cwmp.model.Room
import it.cwmp.testing.VerticleTesting
import org.scalatest.{BeforeAndAfterEach, Matchers}

import scala.concurrent.Future

/**
  * Test class for RoomsServiceVerticle
  *
  * @author Enrico Siboni
  */
class RoomsServiceTest extends VerticleTesting[RoomsServiceVerticle] with Matchers with BeforeAndAfterEach {

  private val roomsServiceHost = "127.0.0.1"
  private val roomsServicePort = 8667
  private var webClient: WebClient = _

  private val testUserUsername = "Enrico"
  private val testUserPassword = "password"

  private var testUserToken: Future[String] = _
  private var authenticationServiceDeploymentID: Future[String] = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    webClient = WebClient.create(vertx,
      WebClientOptions()
        .setDefaultHost(roomsServiceHost)
        .setDefaultPort(8667))

    authenticationServiceDeploymentID = vertx.deployVerticleFuture(new AuthenticationServiceVerticle)
    testUserToken =
      authenticationServiceDeploymentID
        .flatMap(_ => AuthenticationService(vertx).signUp(testUserUsername, testUserPassword))
  }

  override protected def afterEach(): Unit = {
    authenticationServiceDeploymentID.foreach(vertx.undeploy)
    super.afterEach()
  }

  private val roomName = "Stanza"
  private val playersNumber = 2

  describe("Room Creation") {
    val creationApi = "/api/rooms"

    it("should succeed when the user is authenticated") {
      testUserToken.flatMap(token =>
        webClient.post(creationApi)
          .putHeader(HttpHeaderNames.AUTHORIZATION.toString, token)
          .sendJsonObjectFuture(roomCreationJson(roomName, playersNumber)))
        .flatMap(res => assert(res.statusCode() == 201 && res.bodyAsString().isDefined))
    }

    describe("should fail") {
      it("if token isn't provided") {
        webClient.post(creationApi)
          .sendJsonObjectFuture(roomCreationJson(roomName, playersNumber))
          .flatMap(res => res statusCode() shouldEqual 400)
      }
      it("if token is wrong") {
        webClient.post(creationApi)
          .putHeader(HttpHeaderNames.AUTHORIZATION.toString, "token")
          .sendJsonObjectFuture(roomCreationJson(roomName, playersNumber))
          .flatMap(res => res statusCode() shouldEqual 400)
      }
      it("if token isn't valid") {
        webClient.post(creationApi)
          .putHeader(HttpHeaderNames.AUTHORIZATION.toString, "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRpemlvIn0.f6eS98GeBmPau4O58NwQa_XRu3Opv6qWxYISWU78F68")
          .sendJsonObjectFuture(roomCreationJson(roomName, playersNumber))
          .flatMap(res => res statusCode() shouldEqual 401)
      }
      it("if no room is sent with request") {
        testUserToken.flatMap(token =>
          webClient.post(creationApi)
            .putHeader(HttpHeaderNames.AUTHORIZATION.toString, token)
            .sendFuture())
          .flatMap(res => res statusCode() shouldEqual 400)
      }
      it("if body is malformed") {
        testUserToken.flatMap(token =>
          webClient.post(creationApi)
            .putHeader(HttpHeaderNames.AUTHORIZATION.toString, token)
            .sendJsonObjectFuture(Json.obj(("a", "a"))))
          .flatMap(res => res statusCode() shouldEqual 400)
      }
    }
  }

  // TODO: verify that it's present lisitng rooms
  describe("Room Listing") {
    it("should succeed if the user is authenticated") {
      testUserToken.flatMap(token => {
        listRooms(token)
          .map(res => res statusCode() shouldEqual 200)
      })
    }

    /* TODO
    describe("should fail") {
      it("if token isn't provided or wrong") {
        listRooms("TOKEN")
          .map(res => res.statusCode() should equal(400))
      }

      it("if token isn't valid") {
        listRooms("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRpemlvIn0.f6eS98GeBmPau4O58NwQa_XRu3Opv6qWxYISWU78F68")
          .map(res => res.statusCode() should equal(401))
      }
    }*/
  }

  describe("Room Entering") {
    it("should succeed if user is authenticated and room is present") {
      val roomName = "Stanza"
      testUserToken.flatMap(token => {
        createRoom(roomName, 4, token).flatMap(response => {
          val roomID = response.body().get.toString()
          enterRoom(roomID, token)
            .map(res => res.statusCode() shouldEqual 200)
        })
      })
    }

    // TODO: verify user inside after entering

    describe("should fail") {
      // TODO:  authentication not valid
    }
  }

  describe("Public Room Entering") {
    it("should succeed if user is authenticated") {
      val roomName = "public"
      testUserToken.flatMap(token => {
        enterRoom(roomName, token)
          .map(res => res.statusCode() shouldEqual 200)
      })
    }

    // TODO: verify user inside after entering
  }

  describe("Room Info Retrieval") {
    it("should succeed if user is authenticated and room is present") { // TODO: refactor test so they are one inside another
      val roomName = "Stanza"
      testUserToken.flatMap(token => {
        createRoom(roomName, 4, token).flatMap(response => {
          val roomID = response.body().get.toString()
          retrieveRoomInfo(roomID, token)
            .map(res => res.statusCode() shouldEqual 200)
        })
      })
    }

    // TODO: verify info modification after user entering
  }

  describe("Room") {
    // TODO: raggrupare qui tutti i fallimenti dovuti al token non valido sulle varie chiamate, cercando di non duplicare codice
  }


  // TODO: la parte qui sotto magari modificata sar da riportare nel RoomsServiceHelper
  private def createRoom(roomName: String, neededPlayers: Int, userToken: String) = {
    webClient.post("/api/rooms")
      .putHeader(HttpHeaderNames.AUTHORIZATION.toString, userToken)
      .sendJsonObjectFuture(
        Json.obj(
          (Room.FIELD_NAME, roomName),
          (Room.FIELD_NEEDED_PLAYERS, neededPlayers)
        ))
  }

  private def listRooms(userToken: String) = {
    webClient.get("/api/rooms")
      .putHeader(HttpHeaderNames.AUTHORIZATION.toString, userToken)
      .sendFuture()
  }

  private def enterRoom(roomID: String, userToken: String) = {
    webClient.get(s"/api/rooms/:${Room.FIELD_IDENTIFIER}")
      .setQueryParam(Room.FIELD_IDENTIFIER, roomID)
      .putHeader(HttpHeaderNames.AUTHORIZATION.toString, userToken)
      .sendFuture()
  }

  private def retrieveRoomInfo(roomID: String, userToken: String) = {
    webClient.get(s"/api/rooms/${Room.FIELD_IDENTIFIER}/info")
      .setQueryParam(Room.FIELD_IDENTIFIER, roomID)
      .putHeader(HttpHeaderNames.AUTHORIZATION.toString, userToken)
      .sendFuture()
  }

  /**
    * Handle method to create the JSON to use in creation API
    */
  private def roomCreationJson(roomName: String, playersNumber: Int): JsonObject =
    Json.obj((Room.FIELD_NAME, roomName), (Room.FIELD_NEEDED_PLAYERS, playersNumber))

}
