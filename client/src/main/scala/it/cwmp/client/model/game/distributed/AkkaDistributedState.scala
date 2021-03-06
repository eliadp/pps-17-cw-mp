package it.cwmp.client.model.game.distributed

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.cluster.ddata.Replicator.{Changed, Subscribe, Unsubscribe, WriteMajority}
import akka.cluster.ddata.{Key, ReplicatedData, Replicator}
import it.cwmp.utils.Logging

import scala.concurrent.duration._

/**
  * A base class to represent a distributed state in Akka
  *
  * @param replicatorActor the actor that will distribute the data
  * @author Eugenio Pierfederici
  * @author contributor Enrico Siboni
  */
abstract class AkkaDistributedState[State](onDistributedStateUpdate: State => Unit)
                                          (implicit replicatorActor: ActorRef)
  extends DistributedState[State, ActorRef] with Logging {

  /**
    * The type of replicated data for this distributed state
    */
  type ReplicatedDataType <: ReplicatedData

  override def initialize(initialState: State): Unit = updateDistributedStateTo(initialState)

  override def subscribe(subscriber: ActorRef): Unit =
    replicatorActor ! Subscribe(distributedKey, subscriber)

  override def unsubscribe(subscriber: ActorRef): Unit =
    replicatorActor ! Unsubscribe(distributedKey, subscriber)

  /**
    * This behaviour provides an easy way to make the interested actor,
    * able to receive updates and make changes in this distributed state
    */
  def distributedStateBehaviour: Receive = passiveBehaviour orElse activeBehaviour

  /**
    * @return the consistency policy to adopt when writing updates in distributed state
    */
  protected def consistencyPolicy: Replicator.WriteConsistency = WriteMajority(1.seconds)

  /**
    * @return the behaviour enabling to listen for modification in the distributed state
    */
  protected def passiveBehaviour: Receive = {
    val changedDistributedKey = distributedKey;
    {
      // Called when notified of the distributed data change
      case msg@Changed(`changedDistributedKey`) =>
        log.debug("Being notified that distributed state has changed")
        onDistributedStateUpdate(msg.get[ReplicatedDataType](changedDistributedKey))
    }
  }

  /**
    * @return the behaviour enabling to modify distributed state
    */
  protected def activeBehaviour: Receive

  /**
    * @return the key to access distributed state
    */
  protected def distributedKey: Key[ReplicatedDataType]

  /**
    * Updates the distributed state with provided new state
    *
    * @param state the new state to be published
    */
  protected def updateDistributedStateTo(state: State)

  /**
    * A function to modify distributed state to the new one
    *
    * @param oldDistributedState the old distributed state to modify
    * @param newState            the new state to inject
    * @return the new distributed state
    */
  protected def distributedModify(oldDistributedState: ReplicatedDataType, newState: State): ReplicatedDataType

  /**
    * Implicit conversion from distributed state to application State
    *
    * @param distributedData the distributed data to convert
    * @return the application version of state
    */
  protected implicit def parseFromDistributed(distributedData: ReplicatedDataType): State
}

/**
  * Companion object, with actor messages
  */
object AkkaDistributedState {

  /**
    * The message to send to update distributed state
    *
    * @param state the new state
    */
  case class UpdateState[T](state: T)

}
