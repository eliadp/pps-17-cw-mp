package it.cwmp.client.model.game.impl

import java.time.{Duration, Instant}

import it.cwmp.model.User
import org.scalatest.FunSpec

/**
  * A test class for CellWorld
  *
  * @author Enrico Siboni
  */
class CellWorldTest extends FunSpec {

  private val worldInstant = Instant.now

  // scalastyle:off magic.number
  private val cells = Cell(User("Winner"), Point(20, 20), 20) ::
    Cell(User("Mantis"), Point(90, 400), 40) ::
    Cell(User("Enrico"), Point(70, 250), 40) ::
    Cell(User("Candle"), Point(200, 150), 200) :: Nil
  // scalastyle:on magic.number

  private val tentacles = Tentacle(cells.head, cells(1), worldInstant) ::
    Tentacle(cells(1), cells(2), worldInstant) ::
    Tentacle(cells(2), cells.head, worldInstant) ::
    Nil

  private val myCellWorld = CellWorld(worldInstant, cells, tentacles)

  private val timeAmount: Duration = Duration.ofSeconds(1)

  describe("A CellWorld") {
    describe("On creation") {
      it("should succeed if inputs correct") {
        assert(myCellWorld.instant == worldInstant)
        assert(myCellWorld.characters == cells)
        assert(myCellWorld.attacks == tentacles)
      }

      describe("should complain") {
        // scalastyle:off null
        it("on bad world instant")(intercept[NullPointerException](CellWorld(null, cells, tentacles)))
        it("on bad cells")(intercept[NullPointerException](CellWorld(worldInstant, null, tentacles)))
        it("on bad tentacles")(intercept[NullPointerException](CellWorld(worldInstant, cells, null)))
        // scalastyle:on null
      }
    }

    describe("Manipulation") {
      it("can add a tentacle to world") {
        val tentacle = Tentacle(cells(3), cells(2), worldInstant.plus(timeAmount))
        val newWorld = myCellWorld ++ tentacle

        assert(newWorld.attacks contains tentacle)
      }

      it("can remove tentacle from world") {
        val newWorld = myCellWorld -- tentacles.head

        assert(!(newWorld.attacks contains tentacles.head))
      }

      it("removing a tentacle from world refunds energy to character") {
        val advancedWorld = CellWorld(worldInstant.plus(timeAmount), cells, tentacles)
        val beforeCell = advancedWorld.characters.find(_ == tentacles.head.from).get

        val changedWorld = advancedWorld -- tentacles.head
        val afterCell = changedWorld.characters.find(Cell.ownerAndPositionMatch(_, beforeCell)).get

        assert(beforeCell.energy < afterCell.energy)
      }
    }
  }

}
