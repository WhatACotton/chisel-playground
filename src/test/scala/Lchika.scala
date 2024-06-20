package cpu

import chiseltest._
import scala.util.control.Breaks
import org.scalatest.flatspec.AnyFlatSpec
import chisel3._
import chisel3.util._
import scala.util.Random
import Lchika.Hello
class TopTest extends AnyFlatSpec with ChiselScalatestTester {
  it must "runs Top" in {
    test(new Hello).withAnnotations(
      Seq(VerilatorBackendAnnotation, WriteFstAnnotation)
    ) { c =>
      c.reset.poke(true.B)
      c.clock.step(4)
      c.reset.poke(false.B)
      c.clock.setTimeout(100010)
      c.clock.step(100000)
    }
  }

}
