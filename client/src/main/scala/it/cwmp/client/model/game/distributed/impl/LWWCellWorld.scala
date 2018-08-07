package it.cwmp.client.model.game.distributed.impl

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator.{Changed, Update}
import akka.cluster.ddata._
import it.cwmp.client.model.game.distributed.AkkaDistributedState
import it.cwmp.client.model.game.distributed.AkkaDistributedState.UpdateState
import it.cwmp.client.model.game.distributed.impl.LWWCellWorld.DISTRIBUTED_KEY_NAME
import it.cwmp.client.model.game.impl.CellWorld

import scala.language.implicitConversions

/**
  * Distributed representation of the world where "Latest Write Wins"
  *
  * @param onWorldUpdate   the strategy to adopt on world changes
  * @param replicatorActor the actor that will distribute the data
  * @param cluster         the cluster where this distributed data are exchanged
  * @author Eugenio Pierfederici
  * @author contributor Enrico Siboni
  */
case class LWWCellWorld(onWorldUpdate: CellWorld => Unit)
                       (implicit replicatorActor: ActorRef, cluster: Cluster) extends AkkaDistributedState[CellWorld] {

  override type ReplicatedDataType = LWWRegister[CellWorld]

  override protected val distributedKey: LWWRegisterKey[CellWorld] =
    LWWRegisterKey[CellWorld](DISTRIBUTED_KEY_NAME)

  override def initialize(initialState: CellWorld): Unit = writeDistributed(initialState)

  override protected def passiveBehaviour: Receive = {
    // Called when notified of the distributed data change
    case msg@Changed(`distributedKey`) =>
      log.debug("Being notified that distributed state has changed")
      onWorldUpdate(msg.get[ReplicatedDataType](distributedKey))
  }

  override protected def activeBehaviour: Receive = {
    case UpdateState(state: CellWorld) =>
      log.debug("Updating distributed state")
      writeDistributed(state)
  }

  /**
    * Handle method to do a distributed write
    *
    * @param state the state to write
    */
  private def writeDistributed(state: CellWorld): Unit =
    replicatorActor ! Update(distributedKey, convertToDistributed(state), consistencyPolicy)(_.withValue(state))

  override protected implicit def convertToDistributed(state: CellWorld): LWWRegister[CellWorld] = LWWRegister(state)

  override protected implicit def convertFromDistributed(distributedData: LWWRegister[CellWorld]): CellWorld = distributedData.value
}

/**
  * Companion Object
  */
object LWWCellWorld {
  private val DISTRIBUTED_KEY_NAME = "distributedKey"
}
