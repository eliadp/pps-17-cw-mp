package it.cwmp.client.model.game

/**
  * A trait describing a game world snapshot
  *
  * @author Enrico Siboni
  */
trait World[Instant, Character, Attack] {

  def instant: Instant

  def characters: Seq[Character]

  def attacks: Seq[Attack]

}