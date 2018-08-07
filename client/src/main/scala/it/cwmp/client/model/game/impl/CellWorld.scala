package it.cwmp.client.model.game.impl

import java.time.{Duration, Instant}
import java.util.Objects.requireNonNull

import it.cwmp.client.controller.game.GameConstants.{ATTACK_DURATION_TO_ENERGY_REDUCTION_RATE, LENGTH_TO_ENERGY_REDUCTION_RATE}
import it.cwmp.client.model.game.{SizingStrategy, World}

/**
  * Default implementation of the snapshot of Game World
  *
  * @param instant    the time this world represents
  * @param characters the cells in the world
  * @param attacks    the tentacles in the world
  * @author Enrico Siboni
  */
case class CellWorld(instant: Instant,
                     characters: Seq[Cell],
                     attacks: Seq[Tentacle]) extends World[Instant, Cell, Tentacle] {

  requireNonNull(instant, "World instant must not be null")
  requireNonNull(characters, "Cells must not be null")
  requireNonNull(attacks, "Tentacles must not be null")
}

/**
  * Companion object
  */
object CellWorld {

  /**
    * A wrapper class to manipulate a CellWorld
    *
    * @param cellWorld the cellWorld to manipulate
    */
  implicit class CellWorldManipulator(cellWorld: CellWorld) {
    /**
      * Add a tentacle to the world
      *
      * @param tentacle the tentacle to add
      * @return the modified world
      */
    // scalastyle:off method.name
    def ++(tentacle: Tentacle): CellWorld =
    // scalastyle:on method.name
      CellWorld(cellWorld.instant, cellWorld.characters, tentacle +: cellWorld.attacks)

    /**
      * Removes the tentacle from the world refunding for energy the attacker
      *
      * @param tentacle the tentacle to remove
      * @return the modified world
      */
    // scalastyle:off method.name
    def --(tentacle: Tentacle): CellWorld = {
      // scalastyle:on method.name
      val tentacleActualLength = tentacle.length(cellWorld.instant)
      val energyRefund = lengthToEnergyReductionStrategy(tentacleActualLength)
      val attackerAndOthersPair = cellWorld.characters.partition(Cell.ownerAndPositionMatch(_, tentacle.from))
      val attackerCell = attackerAndOthersPair._1.head
      val attackerRefunded = Cell(attackerCell.owner, attackerCell.position, attackerCell.energy + energyRefund)
      CellWorld(cellWorld.instant, attackerAndOthersPair._2 :+ attackerRefunded, cellWorld.attacks filterNot (_ == tentacle))
    }
  }

  /**
    * Default strategy for sizing the energy reduction of attacking character
    *
    * Every [[LENGTH_TO_ENERGY_REDUCTION_RATE]] the attacking character spends 1 energy
    */
  val lengthToEnergyReductionStrategy: SizingStrategy[Long, Double] =
    (length: Long) => length / LENGTH_TO_ENERGY_REDUCTION_RATE

  /**
    * Default strategy to calculate reduction/increment of energy of attacked character
    *
    * Every [[ATTACK_DURATION_TO_ENERGY_REDUCTION_RATE]] the attacked character reduces its energy by 1
    */
  val durationToEnergyConversionStrategy: SizingStrategy[Duration, Double] =
    (attackDuration: Duration) => attackDuration.toMillis / ATTACK_DURATION_TO_ENERGY_REDUCTION_RATE
}
