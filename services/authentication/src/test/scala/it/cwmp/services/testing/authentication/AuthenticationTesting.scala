package it.cwmp.services.testing.authentication

import it.cwmp.services.testing.authentication.AuthenticationTesting.RANDOM_STRING_LENGTH
import it.cwmp.testing.VertxTest
import it.cwmp.utils.Utils
import org.scalatest.Matchers

/**
  * A base class for testing Authentication service
  */
abstract class AuthenticationTesting extends VertxTest with Matchers {

  /**
    * @return a new random username
    */
  protected def nextUsername: String = Utils.randomString(RANDOM_STRING_LENGTH)

  /**
    * @return a new random password
    */
  protected def nextPassword: String = Utils.randomString(RANDOM_STRING_LENGTH)

  private val tokens = Iterator.continually(List(
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRpemlvIn0.f6eS98GeBmPau4O58NwQa_XRu3Opv6qWxYISWU78F68"
  )).flatten

  /**
    * Returns the next valid token. It iterates over a list of token using Round Robin algorithm.
    *
    * @return a new valid token
    */
  protected def nextToken: String = tokens.next()

  protected val invalidToken: String = "INVALID"


  protected def singUpTests()

  describe("Sign up") {
    singUpTests()
  }

  protected def signOutTests()

  describe("Sign out") {
    signOutTests()
  }

  protected def loginTests()

  describe("Login") {
    loginTests()
  }

  protected def validationTests()

  describe("Validation") {
    validationTests()
  }
}

/**
  * Companion object
  */
object AuthenticationTesting {
  private val RANDOM_STRING_LENGTH = 10
}
