package varanus

import Chisel._

import freechips.rocketchip.util.property._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config.{Parameters,Field}
import scala.collection.mutable.ArrayBuffer

class MemoryRequest(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val addr = UInt(width = xLen)
  val data = UInt(width = xLen)
  val cmd = UInt(width = M_SZ)
  val tag = UInt(width = 8)
}

class NewMemoryRequest(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val addr = UInt(width = xLen)
  val data = UInt(width = xLen)
  val cmd = UInt(width = M_SZ)
  val tag = UInt(width = 8)
  val storebits = UInt(width=3)
}

class ALURequest(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val fn = Bits(width = 4)
  val in1 = UInt(width = xLen)
  val in2 = UInt(width = xLen)
  val out = UInt(width=xLen)
}

class ConfigLocal(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val index = UInt(width = 3)
  val data = UInt(width = xLen)
}

class ControlUnitInterface(implicit val p: Parameters)
    extends ParameterizedBundle()(p) with HasCoreParameters {
  val doorbell = Valid(new DoorbellResp).flip // doorbell coming from Action Unit
  val alu_req = Valid(new ActionALU).flip // ALU request coming from Config Table
  val mem_req = Valid(new ActionMem).flip // Memory request coming from Config Table
  val act_mem_resp = Valid(UInt(width=xLen)).flip // The memory response coming from Komodo through RoCC
  val act_alu_resp = Valid(UInt(width=xLen)).flip // The ALU response coming from Komodo (ALU)
  val act_intr = Bool(INPUT) // An interrupt is happening (config unit interrupt)
  val act_intr_done = Bool(INPUT) // The interrupt is done
  val act_sink = Bool(INPUT) // An sink interrupt is happening
  val act_sink_done = Bool(INPUT) // The sink interrupt is done
  val act_done = Bool(INPUT) // A signal coming from Config Table indicating that all the actions for the corresponding event are taken
  val conf_req = Valid(new ConfigLocal).flip // A request for accessing local storages
  val is_reset = Bool(INPUT)
  val interrupt_en = Bool(OUTPUT) // Intrupt request to Komodo
  val act_mem_req = Valid(new MemoryRequest) // Memory request to Komodo
  val act_alu_req = Valid(new ALURequest) // ALU request to Komodo (ALU)

  val smallalu_req = Valid(new SMALLActionALU).flip
  val smallact_alu_req = Valid(new NewActionALU)
  val smallact_alu_resp = Valid(UInt(width=xLen)).flip

  val smallmem_req = Valid(Bool(INPUT)).flip
  val smallmem_in1 = UInt(width=5)
  val smallmem_out = UInt(width=5)
  val smallact_mem_req = Valid(new NewMemoryRequest)
  val smallact_mem_resp = Valid(UInt(width=xLen)).flip

  val smallspec_req = Valid(new SMALLActionALU).flip
  val smallact_spec_req = Valid(new NewActionALU)
  val smallact_spec_resp = Valid(UInt(width=xLen)).flip

  val cu_wait = Bool(OUTPUT) // Control Unit is in the idel state, this signal goes to Config Table
  val ready = Bool(OUTPUT) // Control Unit is ready to take actions for a new event, this signal goes to Komodo
  val read_storage_resp = Reg(Vec(8, UInt(width=xLen))).asOutput // Contains the value of one of the local storages based the conf_req
  val skip_actions = Bool(OUTPUT)
  val firsttime = Bool(INPUT) 
  val idle = Bool()
  val reidle =Bool(OUTPUT)
}

class ControlUnit(implicit val p: Parameters)
    extends Module with ActionConfigEnums with HasCoreParameters with HasL1HellaCacheParameters {
  val io = new ControlUnitInterface
  val mu_resp = Reg(UInt(width=xLen))
  val action_mem_req = Reg(new MemoryRequest)
  val action_mem_req0 = Reg(new MemoryRequest)
  val action_mem_req1 = Reg(new MemoryRequest)
  val action_mem_req_valid = Reg(init=Bool(false))
  val action_mem_req_valid1 = Reg(init=Bool(false))
  val local1 = Reg(UInt(width=xLen))
  val local2 = Reg(UInt(width=xLen))
  val local3 = Reg(UInt(width=xLen))
  val local4 = Reg(UInt(width=xLen))
  val local5 = Reg(UInt(width=xLen))
  val local6 = Reg(UInt(width=xLen))
  val pass = Reg(init=Bool(false))

  // Control Unit's FSM
  val e_ready :: e_busy :: Nil = Enum(UInt(), 2)
  val dequeuestate = Reg(init = e_ready) 
  val readconfigstate = Reg(init = e_ready) 
  val actionstate = Reg(init = e_ready) 
  val dequeuevalid = Reg(init = Bool(false)) 
  val readconfigvalid = Reg(init = Bool(false)) 
  val actionvalid = Reg(init = Bool(false))
  val state = Reg(init = e_ready)

  val wait_for_done = Reg(init = Bool(false))
  val insn_type = Reg(init=UInt(0,1))

  val in1bits = Reg(init=UInt(0,xLen))
  val in1bits0 = Reg(init=UInt(0,xLen))
  val in1bits1 = Reg(init=UInt(0,xLen))

  val in2bits = Reg(init=UInt(0,xLen))
  val in2bits0 = Reg(init=UInt(0,xLen))
  val in2bits1 = Reg(init=UInt(0,xLen))
  
  val instructions = Reg(init=UInt(0,32))
  val instructions0 = Reg(init=UInt(0,32))
  val instructions1 = Reg(init=UInt(0,32))

  val specfun = Reg(init=UInt(0,6))

  val in1reg = Reg(init=UInt(0,32))
  val in2reg = Reg(init=UInt(0,32))
  val dataaddr = Reg(init=UInt(0,xLen))
  val fnbits = Reg(init=UInt(0,5))

  val pc_src = Reg(init=UInt(0,xLen))
  val pc_src0 = Reg(init=UInt(0,xLen))
  val pc_src1 = Reg(init=UInt(0,xLen))

  val mask1 = Bits("hf8000", 32)
  val mask4 = Bits("h1F", 32)
  val mask2 = Bits("h1f00000", 32)
  val mask3 = Bits("hf80", 32)
  val mask5 = Bits("hFFF00000", 32)
  val mask6 = Bits("h7F", 32)
  val mask7 = Bits("h80000000", 32)
  
  val smallact_alu_reqvalid=Reg(init = Bool(false))
  val smallact_intr_reqvalid=Reg(init = Bool(false))
  val smallact_mem_reqvalid=Reg(init = Bool(false))

  val counter_action = Reg(init=UInt(0,4))
  val interrupt_en = Mux(io.act_alu_resp.valid, (Mux(io.alu_req.bits.out === e_INTR, io.act_alu_resp.bits === UInt(0), Bool(false))), Bool(false))

  when(io.conf_req.valid) {
    when(io.conf_req.bits.index === UInt(0)) {
      local1 := io.conf_req.bits.data
    }
      .elsewhen(io.conf_req.bits.index === UInt(1)) {
      local2 := io.conf_req.bits.data
    }
    .elsewhen(io.conf_req.bits.index === UInt(2)) {
      local3 := io.conf_req.bits.data
    }
    .elsewhen(io.conf_req.bits.index === UInt(3)) {
      local4 := io.conf_req.bits.data
    }
    .elsewhen(io.conf_req.bits.index === UInt(4)) {
      local5 := io.conf_req.bits.data
    }
    .elsewhen(io.conf_req.bits.index === UInt(5)) {
      local6 := io.conf_req.bits.data
    }
  }

  io.ready :=  (dequeuestate === e_ready)  && (readconfigstate === e_ready) && (actionstate === e_ready)
  io.cu_wait := (readconfigvalid === true.B) && (actionstate === e_ready) 
  io.idle := readconfigstate === e_ready  &&(dequeuevalid === true.B) 
  io.reidle:=(io.act_mem_resp.valid )
  
  when (io.doorbell.fire() && (dequeuestate === e_ready)  && (readconfigstate === e_ready) ) {
    action_mem_req0.addr := io.doorbell.bits.addr
    action_mem_req0.data := io.doorbell.bits.data
    action_mem_req0.cmd := M_XRD
    instructions0 := io.doorbell.bits.instructions
    dequeuevalid :=true.B
    in1bits0 := io.doorbell.bits.in1bits
    in2bits0 := io.doorbell.bits.in2bits
    pc_src0 := io.doorbell.bits.data
    printf("[CU] time %x, doorbell instruction %x in2bits %x\n", GTimer(), io.doorbell.bits.instructions, io.doorbell.bits.in2bits)
    local1 := local1 + UInt(1)
    // printf("[EXTRA] state change from ready to idle, state:%d\n", state)
  }.otherwise{
    dequeuevalid :=false.B
  }

  when(io.doorbell.fire()){
    // printf("[FIRE] %x %x %x\n", dequeuestate, readconfigstate, actionstate)
  } 

  when ((readconfigstate === e_ready  && dequeuevalid === true.B ) ) {
    action_mem_req1.addr := action_mem_req0.addr
    action_mem_req1.data := action_mem_req0.data
    action_mem_req1.cmd  := action_mem_req0.cmd
    instructions1 := instructions0
    in1bits1 := in1bits0
    in2bits1 := in2bits0
    pc_src1 := pc_src0
    wait_for_done := Bool(false)
    counter_action := UInt(0)
    readconfigvalid:=true.B
    printf("[CU] time %x, readconfig valid, state: %d, instructions0 %x, action_mem_req.data %x, action_mem_req0.addr %x\n", GTimer(), readconfigstate, instructions0, action_mem_req0.data, action_mem_req0.addr)
  }.otherwise{
    readconfigvalid := false.B
  } 

  when(io.cu_wait){
    instructions := instructions1
    action_mem_req.addr := action_mem_req1.addr 
    action_mem_req.data := action_mem_req1.data
    in1bits := in1bits1
    in2bits := in2bits1
    pc_src := pc_src1
  }
  
  io.smallact_alu_req.valid := smallact_alu_reqvalid
  io.smallact_alu_req.bits.fn :=  io.smallalu_req.bits.fn
  io.smallact_alu_req.bits.opcode := (instructions & mask6) // opcode instructions[0:6]
  io.smallact_alu_req.bits.in1 := (instructions & mask1) >> 15 // rs1 instructions[15:19]
  io.smallact_alu_req.bits.in2 := (instructions & mask2) >> 20 // rs2 instructions[20:24]
  io.smallact_alu_req.bits.in1bits := in1bits // rs1 value
  io.smallact_alu_req.bits.in2bits := in2bits // rs2 value
  io.smallact_alu_req.bits.out := (instructions & mask3) >> 7 // rd instructions[7:11]
  io.smallact_alu_req.bits.data := (instructions & mask5) >> 20 // immediate instructions[20:31]
  io.smallact_alu_req.bits.sign := (instructions & mask7) >> 31 //sign instructions[31]

  when (io.smallalu_req.valid && (actionstate === e_ready)) {
    smallact_alu_reqvalid  := Bool(true)
    printf("[CU] alu time %x, alu instructions %x, in1 %x in2 %x, out %x\n", GTimer(), instructions, (instructions & mask1) >> 15, (instructions & mask2) >> 20, (instructions & mask3) >> 7)
    readconfigstate := e_ready
    dequeuestate := e_ready
    actionstate := e_ready
    wait_for_done := Bool(true)
  }.otherwise {
    smallact_alu_reqvalid := Bool(false)
  }

  io.smallact_mem_req.valid := action_mem_req_valid1
  io.smallmem_in1 := in1reg
  io.smallmem_out := in2reg

  when (io.smallmem_req.valid && (actionstate === e_ready)) {
    readconfigstate := e_ready
    dequeuestate := e_ready
    actionstate := e_ready
    printf("[CU] time %x, mem instructions1 %x, in1reg %x, in2reg %x, instructions %x, in1bits %x, in2bits %x, in1bits1 %x, in2bits1 %x\n", GTimer(), instructions1, in1reg, in2reg, instructions, in1bits, in2bits, in1bits1, in2bits1)
    in1reg := Mux(io.mem_req.bits.mem_op===M_XRD,(instructions1&mask1)>>15,(instructions1&mask1)>>15)
    in2reg := Mux(io.mem_req.bits.mem_op===M_XRD,(instructions1&mask3)>>7,(instructions1&mask2)>>20)
    action_mem_req.cmd := io.mem_req.bits.mem_op
    action_mem_req.tag := io.mem_req.bits.tag
    action_mem_req.addr := action_mem_req.addr
    action_mem_req.data := Mux(io.mem_req.bits.mem_op===M_XRD, 
    Mux((instructions1>>20)(11)===0.U, in2bits1+(instructions1>>20), in2bits1-(~(instructions1>>20)+1)), 
    Mux((instructions1)(31)===0.U, in2bits1+(((instructions1>>25)<<5)|((instructions1>>7)&mask4)), (in2bits1-(~(((instructions1>>25)<<5)|((instructions1>>7)&mask4))+1)(11,0))))
    io.smallact_mem_req.bits.storebits:=(instructions1&0x7000)>>12
    action_mem_req_valid1:= Bool(true)
  }
  .otherwise {
    action_mem_req_valid1 := Bool(false)
  }
  
  io.smallact_spec_req.bits.fn := specfun
  io.smallact_spec_req.bits.in1 := (instructions & mask1)>>15
  io.smallact_spec_req.bits.in2 := (instructions & mask2)>>20
  io.smallact_spec_req.bits.out := (instructions & mask3)>>7
  io.smallact_spec_req.bits.instructions := instructions
  io.smallact_spec_req.bits.in1bits := in1bits // rs1 value
  io.smallact_spec_req.bits.in2bits := in2bits // rs2 value
  io.smallact_spec_req.bits.pc_src := pc_src
  io.smallact_spec_req.valid := smallact_intr_reqvalid

  when (io.smallspec_req.valid && (actionstate=/= e_busy)) {
    specfun := io.smallspec_req.bits.fn
    smallact_intr_reqvalid := true.B
    readconfigstate := e_ready
    dequeuestate := e_ready
    actionstate := e_ready
    wait_for_done := Bool(true)
  } .otherwise {
    smallact_intr_reqvalid := Bool(false)
  }

  when (io.act_intr && actionstate === e_ready) {
    actionstate := e_busy
    readconfigvalid := false.B
    readconfigstate := e_busy
    dequeuestate := e_busy
    dequeuevalid := false.B
    printf("interrupt\n")
  }
  
  when (io.act_intr_done) {
    actionstate := e_ready
    dequeuestate := e_ready
    readconfigstate := e_ready     
    dequeuevalid := true.B
    readconfigvalid := true.B
    printf("interrupt done\n")
  }

  when (io.act_sink && actionstate === e_ready) {
    actionstate := e_busy
    readconfigvalid := false.B
    readconfigstate := e_busy
    dequeuestate := e_busy
    dequeuevalid := false.B
    printf("sink interrupt\n")
  }
  
  when (io.act_sink_done) {
    actionstate := e_ready
    dequeuestate := e_ready
    readconfigstate := e_ready     
    dequeuevalid := true.B
    readconfigvalid := true.B
    printf("sink interrupt done\n")
  }

  when (io.act_intr && actionstate === e_ready) {
    counter_action := counter_action + UInt(1)
    actionstate := e_busy
    readconfigvalid := false.B
    readconfigstate := e_busy
    dequeuestate := e_busy
    dequeuevalid := false.B
    printf("interrupt\n")
  }

  when (wait_for_done && (readconfigstate === e_busy)) {
    dequeuestate := e_ready
    readconfigstate := e_ready
    wait_for_done := Bool(false)
    counter_action := UInt(0)
  }

  when (io.skip_actions) {
    state := e_ready
    wait_for_done := Bool(false)
  }

  io.act_alu_req.valid := Bool(false)
  io.smallact_mem_req.bits.cmd := action_mem_req.cmd
  io.smallact_mem_req.bits.tag := action_mem_req.addr << untagBits
  io.smallact_mem_req.bits.data := action_mem_req.data
  io.smallact_mem_req.bits.addr := action_mem_req.addr

  io.read_storage_resp(0) := local1
  io.read_storage_resp(1) := local2
  io.read_storage_resp(2) := local3
  io.read_storage_resp(3) := local4
  io.read_storage_resp(4) := local5
  io.read_storage_resp(5) := local6
  io.read_storage_resp(6) := state
  io.read_storage_resp(7) := counter_action
  io.interrupt_en := interrupt_en
  io.skip_actions := Bool(false)

  when (io.mem_req.valid && (state =/= e_busy)) {
    action_mem_req.cmd := io.mem_req.bits.mem_op
    action_mem_req.tag := io.mem_req.bits.tag
    switch(io.mem_req.bits.data) {
      is (e_IN_ADDR_MU)    { action_mem_req.data := action_mem_req.addr }
      is (e_IN_LOC1)       { action_mem_req.data := local1 }
      is (e_IN_LOC2)       { action_mem_req.data := local2 }
      is (e_IN_LOC3)       { action_mem_req.data := local3 }
      is (e_IN_LOC4)       { action_mem_req.data := local4 }
      is (e_IN_LOC5)       { action_mem_req.data := local5 }
      is (e_IN_LOC6)       { action_mem_req.data := local6 }
      is (e_IN_DATA_RESP)  { action_mem_req.data := mu_resp }
      is (e_IN_COMPRESSED) { action_mem_req.data := insn_type }
    }

    switch(io.mem_req.bits.addr) {
      is (e_IN_DATA_MU)    { action_mem_req.addr := action_mem_req.data }
      is (e_IN_LOC1)       { action_mem_req.addr := local1 }
      is (e_IN_LOC2)       { action_mem_req.addr := local2 }
      is (e_IN_LOC3)       { action_mem_req.addr := local3 }
      is (e_IN_LOC4)       { action_mem_req.addr := local4 }
      is (e_IN_LOC5)       { action_mem_req.addr := local5 }
      is (e_IN_LOC6)       { action_mem_req.addr := local6 }
      is (e_IN_DATA_RESP)  { action_mem_req.addr := mu_resp }
      is (e_IN_COMPRESSED) { action_mem_req.addr := insn_type }
    }
    action_mem_req_valid := Bool(true)
    printf("[EXTRA] time %x, Control Unit received memory request from config Unit, data:%d, addr: %d\n", GTimer(), io.mem_req.bits.data, io.mem_req.bits.addr)
  }
  .otherwise {
  
    action_mem_req_valid := Bool(false)
  }

  when (io.alu_req.valid && (state =/= e_busy)) {
    switch(io.alu_req.bits.in1) {
      is (e_IN_DATA_MU)    { io.act_alu_req.bits.in1 := action_mem_req.data }
      is (e_IN_ADDR_MU)    { io.act_alu_req.bits.in1 := action_mem_req.addr }
      is (e_IN_CONST)      { io.act_alu_req.bits.in1 := io.alu_req.bits.data }
      is (e_IN_LOC1)       { io.act_alu_req.bits.in1 := local1 }
      is (e_IN_LOC2)       { io.act_alu_req.bits.in1 := local2 }
      is (e_IN_LOC3)       { io.act_alu_req.bits.in1 := local3 }
      is (e_IN_LOC4)       { io.act_alu_req.bits.in1 := local4 }
      is (e_IN_LOC5)       { io.act_alu_req.bits.in1 := local5 }
      is (e_IN_LOC6)       { io.act_alu_req.bits.in1 := local6 }
      is (e_IN_DATA_RESP)  { io.act_alu_req.bits.in1 := mu_resp }
      is (e_IN_COMPRESSED) { io.act_alu_req.bits.in1 := insn_type }
      is (e_IN_CUSTOM)     { io.act_alu_req.bits.in1 := in1bits      
      }
    }

    switch(io.alu_req.bits.in2) {
      is (e_IN_DATA_MU)    { io.act_alu_req.bits.in2 := action_mem_req.data }
      is (e_IN_ADDR_MU)    { io.act_alu_req.bits.in2 := action_mem_req.addr }
      is (e_IN_CONST)      { io.act_alu_req.bits.in2 := io.alu_req.bits.data }
      is (e_IN_LOC1)       { io.act_alu_req.bits.in2 := local1 }
      is (e_IN_LOC2)       { io.act_alu_req.bits.in2 := local2 }
      is (e_IN_LOC3)       { io.act_alu_req.bits.in2 := local3 }
      is (e_IN_LOC4)       { io.act_alu_req.bits.in2 := local4 }
      is (e_IN_LOC5)       { io.act_alu_req.bits.in2 := local5 }
      is (e_IN_LOC6)       { io.act_alu_req.bits.in2 := local6 }
      is (e_IN_DATA_RESP)  { io.act_alu_req.bits.in2 := mu_resp }
      is (e_IN_COMPRESSED) { io.act_alu_req.bits.in2 := insn_type }
      is (e_IN_CUSTOM)     { io.act_alu_req.bits.in2 := in2bits + (instructions >> 20)
      }
    }
    io.act_alu_req.bits.fn := io.alu_req.bits.fn
    io.act_alu_req.bits.out := (instructions & mask3) >> 7

    io.act_alu_req.valid := Bool(true)
  }

  when (io.act_alu_resp.valid) {
    switch(io.alu_req.bits.out) {
      is (e_OUT_LOC1)  { local1 := io.act_alu_resp.bits }
      is (e_OUT_LOC2)  { local2 := io.act_alu_resp.bits }
      is (e_OUT_LOC3)  { local3 := io.act_alu_resp.bits }
      is (e_OUT_LOC4)  { local4 := io.act_alu_resp.bits }
      is (e_OUT_LOC5)  { local5 := io.act_alu_resp.bits }
      is (e_OUT_LOC6)  { local6 := io.act_alu_resp.bits }
      is (e_OUT_DATA)  { action_mem_req.data := io.act_alu_resp.bits }
      is (e_OUT_ADDR)  { action_mem_req.addr := io.act_alu_resp.bits }
      is (e_DONE)      { io.skip_actions := (io.act_alu_resp.bits === UInt(1)) }
    }
  }

  when (io.act_alu_resp.valid) {
    switch(io.alu_req.bits.out) {
      is (e_OUT_LOC1)  { local1 := io.act_alu_resp.bits }
      is (e_OUT_LOC2)  { local2 := io.act_alu_resp.bits }
      is (e_OUT_LOC3)  { local3 := io.act_alu_resp.bits }
      is (e_OUT_LOC4)  { local4 := io.act_alu_resp.bits }
      is (e_OUT_LOC5)  { local5 := io.act_alu_resp.bits }
      is (e_OUT_LOC6)  { local6 := io.act_alu_resp.bits }
      is (e_OUT_DATA)  { action_mem_req.data := io.act_alu_resp.bits }
      is (e_OUT_ADDR)  { action_mem_req.addr := io.act_alu_resp.bits }
      is (e_DONE)      { io.skip_actions := (io.act_alu_resp.bits === UInt(1)) }
    }
    printf("[EXTRA] time %x, Control Unit alu resp received, state change to idle out = 0x%x @%d\n", GTimer(), io.act_alu_resp.bits, io.alu_req.bits.out)
  }

  when (io.act_mem_resp.valid ) {
    mu_resp := io.act_mem_resp.bits
    printf("[EXTRA] time %x, Contrlo Unit memory resp received: 0x%x, state change to idle\n", GTimer(), io.act_mem_resp.bits)
  }

  when (io.is_reset) {
    actionstate := e_ready
    dequeuestate := e_ready
    readconfigstate := e_ready
    wait_for_done := Bool(false)
    counter_action := UInt(0)
    local1 := UInt(0)
    local2 := UInt(0)
    local3 := UInt(0)
    local4 := UInt(0)
    local5 := UInt(0)
    local6 := UInt(0)
    mu_resp := UInt(0)
  }
}