package varanus

import Chisel._
import freechips.rocketchip.util.property._
import freechips.rocketchip.config.Parameters
import Chisel.ImplicitConversions._
import freechips.rocketchip.config._

class DefaultKomodoConfig extends Config ((site, here, up) => {
  case KomodoMatchUnits => 13 // The maximum number of MUs 
})

class CustommConfig extends Config ((site, here, up) => {
  case Xlen => 64
})