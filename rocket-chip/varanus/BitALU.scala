package varanus

import Chisel._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.property._
import freechips.rocketchip.config.Parameters
import Chisel.ImplicitConversions._
object MyBitALU {
  val SZ_ALU_FN = 4
  val FN_X   = BitPat("b????")
  val FN_AND = UInt(0)
  val FN_OR  = UInt(7)
  val FN_XOR = UInt(2)
  val FN_IN1 = UInt(9)
}
import MyBitALU._

class MyBitALU(implicit val p: Parameters)
    extends Module with HasCoreParameters {

  val io = new Bundle {
    val fn  = Bits(INPUT, SZ_ALU_FN) // The maximum number of ALU operations is 16
    val in2 = UInt(INPUT, 1)
    val in1 = UInt(INPUT, 1)
    val out = UInt(OUTPUT, 1)
  }

  // XOR, AND, OR
  val in1_xor_in2 = io.in1 ^ io.in2
  val in1_and_in2 = io.in1 & io.in2
  val in1_or_in2 = io.in1 | io.in2 
  val in1_or_in1 =io.in1
  
  val logic = Mux(io.fn===FN_IN1,in1_or_in1,Mux(io.fn === FN_OR, in1_or_in2, Mux(io.fn === FN_AND, in1_and_in2, Mux(io.fn === FN_XOR, in1_xor_in2, UInt(0)))))

  io.out := logic
}