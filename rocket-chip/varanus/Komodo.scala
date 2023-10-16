package varanus

import Chisel._
import chisel3.RegInit
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.tile.XLen
import freechips.rocketchip.util.property._
import freechips.rocketchip.config.Parameters
import Chisel.ImplicitConversions._
import freechips.rocketchip.rocket._
import freechips.rocketchip.config.{Parameters,Field}
import scala.collection.mutable.ArrayBuffer

case object KomodoMatchUnits extends Field[Int]
case object DebugKomodo extends Field[Boolean]
case object Xlen extends Field[Int]

trait KomodoParameters {
  implicit val p: Parameters
  val numUnits = p(KomodoMatchUnits)
  val debugKomodo = p(DebugKomodo)
  val xlen =p(Xlen)
}

trait KomodoEnums {
  val (e_SM_PC_SRC_CARE :: e_SM_PC_DST_CARE :: e_SM_INST_CARE :: e_SM_RD_CARE :: e_SM_DATA_CARE ::
    e_SM_PC_SRC_DCARE :: e_SM_PC_DST_DCARE :: e_SM_INST_DCARE :: e_SM_RD_DCARE :: e_SM_DATA_DCARE :: Nil) =
    Enum(UInt(), 10)
  val (e_C_VALID :: e_C_INVALID :: e_C_RESET :: e_C_M_COUNT :: e_C_LOCAL :: e_C_COMMIT_IDX :: e_C_INFO_SP_OFFSET :: e_C_WRITE_COUNT :: e_C_MEM_TYPE :: e_C_DONE :: Nil) =
    Enum(UInt(), 10)
  val commit_PC_SRC :: commit_PC_DST :: commit_INST :: commit_DATA :: commit_ADDR :: Nil = Enum(UInt(), 5)
  val (e_ACT_INTR :: e_ACT_MEM_RD :: e_ACT_MEM_WR :: Nil) = Enum(UInt(), 3)
}

class PHMonRoCC(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC (opcodes) {
  override lazy val module = new Komodo(this)
}

class Komodo(outer: PHMonRoCC)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) with HasCoreParameters with KomodoParameters with KomodoEnums    {
  val matchUnits = Vec.tabulate(numUnits){
    (i: Int) => Module(new KomodoMatchUnit(i)(p)).io }
  val alu = Module(new MyTinyALU).io

  val smallalu = Module(new MyBitALU).io
  // shadow register file
  val shadowrf = RegInit(Vec(Seq.fill(32)(0.U(1.W))))
  val shadowheaprf = RegInit(Vec(Seq.fill(32)(0.U(1.W))))
  val heaprf = RegInit(Vec(Seq.fill(32)(0.U(64.W))))

  val size = UInt(1024)
  val sizegp = UInt(1024)
  val sizei = 1024
  val sizeigp = 2048

  // tag storage file
  val taintrf = RegInit(Vec(Seq.fill(1024)(0.U(1.W))))
  val taintheaprf = RegInit(Vec(Seq.fill(1024)(0.U(1.W))))
  val taintgprf = RegInit(Vec(Seq.fill(2048)(0.U(1.W))))

  // base address
  val s0 = RegInit(Bits("h3ffffff9e0",64))
  val gp = RegInit(Bits("h851a8",64))
  val resporegaddr = RegInit(UInt(0,width=64))
  val resporegdata = RegInit(UInt(0,width=64))
  val resporegcmd  = RegInit(UInt(0,width=M_SZ))
  val respregvalid = Reg(init=Bool(false))

  val controlUnit = Module(new ControlUnit).io
  val configUnits = Vec.tabulate(numUnits){
   (i: Int) => Module(new ActionConfigUnit(i)(p)).io }
  val activeUnit = Reg(UInt(width=log2Up(numUnits))) // The MU that its action is getting executed
  val activeUnit1 = Reg(UInt(width=log2Up(numUnits)))
  val activeUnit2 = Reg(UInt(width=log2Up(numUnits)))
  val activeUnit3 = Reg(UInt(width=log2Up(numUnits)))
  
  val activation_queue = Module(new Queue(new DoorbellResp, 1024)).io // match queue size
  
  val ctrl = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(1))
  val enable = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(3))
  val disable = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(4))
  val is_reset = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(2))
  val enabled = Reg(init=Bool(false))
  val resume = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(15))
  val id = io.cmd.bits.rs1(31,0)
  val action = io.cmd.bits.rs1(63,32)
  val data = io.cmd.bits.rs2

  val interrupt_en = Reg(init=Bool(false))
  val interrupt_valid = Reg(init=Bool(false))
  val sink_en = Reg(init=Bool(false))
  val busy_en = Reg(init=Bool(false))
  val pass = Reg(init=Bool(false))

  val intr_en = Reg(init=Bool(false))
  val loadorstore = Reg(init=Bool(false))
  val addrthreebit = RegInit(UInt(0,width=3))
  val mem_wait = Reg(init=Bool(false)) // A register to keep the memory request while RoCC is not ready to receive it
  val mem_req_typ = RegInit(UInt(3,width=3)) // Default: MT_D
  val wait_for_resp = RegInit(init=Bool(false)) // A debugging register to verify whether Varanus is waiting to receive the memory response from RoCC
  val wait_for_resp_after_assert = RegInit(init=Bool(false))
  val read_mask = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(12))
  val read_conf = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(13))
  val read_commit_index = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(14))
  val taint_bitmap = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(11))
  val re_shadowstack = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(20))
  val taint_shadowstack = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(21))
  val walk_mu = RegInit(init=Bool(true))
  val get_base = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(23))
  val enable_MU = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(22))
  val disable_MU = io.cmd.valid && (io.cmd.bits.inst.funct === UInt(21))  
  val threshold = RegInit(UInt(1000,width=xLen)) // match queue size
  val specpcsrc = RegInit(UInt(0,width=64))

  controlUnit.act_sink := Bool(false)
  controlUnit.act_intr_done := Bool(false)
  controlUnit.act_sink_done := Bool(false)

  io.cmd.ready := Bool(true)
  io.resp.bits.rd := io.cmd.bits.inst.rd
  io.resp.valid := Bool(false)
  io.resp.bits.data := UInt(0)
  io.commitLog.ready := Bool(true)

  controlUnit.conf_req.valid := ctrl && (action === e_C_LOCAL)
  controlUnit.act_alu_resp.valid := Bool(false)

  controlUnit.smallact_alu_resp.valid := Bool(false)
  controlUnit.smallact_mem_resp.valid := Bool(false)

  (0 until numUnits).map(i => {
  matchUnits(i).walk_mu:=walk_mu
    matchUnits(i).cmd.bits := io.cmd.bits
    matchUnits(i).commitLog <> io.commitLog
    configUnits(i).req := Bool(false)
    configUnits(i).cu_wait := Bool(false)
    configUnits(i).cmd.bits := io.cmd.bits
    configUnits(i).cmd.valid := io.cmd.valid & id === UInt(i)
    when (enable | disable | is_reset) {
      matchUnits(i).cmd.valid := io.cmd.valid }
    .otherwise {
      matchUnits(i).cmd.valid := io.cmd.valid & id === UInt(i) }})

  when (enable) {
    enabled := Bool(true)
  }
  .elsewhen (disable) {
    enabled := Bool(false)
  }

  when (is_reset) {
    mem_wait := Bool(false)
    intr_en := Bool(false)
    busy_en := Bool(false)
    io.mem.req.valid := Bool(false)
    wait_for_resp := Bool(false)
    // reset SRF
    var i = 0
    for (i <- 0 until 32)
    {
      shadowrf(i) := 0.U
      shadowheaprf(i) := 0.U
    }
    for (i <- 0 until 1024)
    {
      taintrf(i) := 0.U
      taintheaprf(i) := 0.U
      taintgprf(i) := 0.U
    }
  }

  when (ctrl) {
    switch (action) {
      is (e_C_RESET)           { mem_wait := Bool(false)
        wait_for_resp := Bool(false)
        wait_for_resp_after_assert := Bool(false)}
      is (e_C_LOCAL)           { controlUnit.conf_req.bits.index := id
        controlUnit.conf_req.bits.data := data}
      is (e_C_INFO_SP_OFFSET)  { io.resp.valid := Bool(true)
        io.resp.bits.data := controlUnit.read_storage_resp(id) }
      is (e_C_MEM_TYPE)        { mem_req_typ := data(2,0) }
      is (e_C_DONE)            { io.resp.valid := Bool(true)
        io.resp.bits.data := activation_queue.count }
    }
  }

  for (i <- 0 until numUnits) {
    when (matchUnits(i).resp.doorbell.valid && activation_queue.enq.ready) {
      activation_queue.enq <> matchUnits(i).resp.doorbell
      printf("[EXTRA] time:%x, MU[%d] increases the counter:%d\n", GTimer(), UInt(i), activation_queue.count)
    }
  }

  when (read_mask | read_commit_index) {
    io.resp.bits.data := matchUnits(id).read_cmd.bits
    io.resp.valid := Bool(true)
  }
  
  when (re_shadowstack) {
    io.resp.bits.data := taintrf(io.cmd.bits.rs1-s0)|(taintrf(io.cmd.bits.rs1-s0-1)<1)|(taintrf(io.cmd.bits.rs1-s0-2)<2)|(taintrf(io.cmd.bits.rs1-s0-3)<3)|(taintrf(io.cmd.bits.rs1-s0-4)<4)
    io.resp.valid := Bool(true)
  }

  when (taint_shadowstack) {
    io.resp.bits.data := 0
    io.resp.valid := Bool(true)
  }

  when(get_base){
    // reset s0, gp
    val in1 = io.cmd.bits.inst.rs1
    when (in1 === 2.U)
    {
      s0 := io.cmd.bits.rs2
    }.elsewhen (in1 === 3.U)
    {
      gp := io.cmd.bits.rs2
    }
    io.resp.bits.data := 0.U
    io.resp.valid := Bool(true)
  }

  when(enable_MU){
  walk_mu := true.B
    io.resp.bits.data:=0.U
    io.resp.valid:=Bool(true)
    printf("[EXTRA] walk = %d\n", walk_mu)
  }

  when(disable_MU){
    walk_mu := false.B
    io.resp.bits.data:=0.U
    io.resp.valid:=Bool(true)
    printf("[EXTRA] no walk = %d\n", walk_mu)
  }

  when (read_conf) {
    io.resp.bits.data := configUnits(id).conf_read.bits
    io.resp.valid := Bool(true)
  }
  
  alu.fn := UInt(9)
  alu.in1 := UInt(0)
  alu.in2 := UInt(0)

  activation_queue.deq.ready := controlUnit.ready
  controlUnit.is_reset := is_reset
  controlUnit.doorbell.bits.addr := activation_queue.deq.bits.addr
  controlUnit.doorbell.bits.data := activation_queue.deq.bits.data
  controlUnit.doorbell.bits.tag := activation_queue.deq.bits.tag
  controlUnit.doorbell.bits.insn_type := activation_queue.deq.bits.insn_type

  controlUnit.doorbell.bits.instructions := activation_queue.deq.bits.instructions
  controlUnit.doorbell.bits.in1bits := activation_queue.deq.bits.in1bits
  controlUnit.doorbell.bits.in2bits := activation_queue.deq.bits.in2bits

  controlUnit.doorbell.valid := activation_queue.deq.valid
  controlUnit.alu_req <> configUnits(activeUnit).resp.alu_req
  controlUnit.mem_req <> configUnits(activeUnit).resp.mem_req

  controlUnit.smallalu_req <> configUnits(activeUnit).resp.smallalu_req
  controlUnit.smallmem_req <> configUnits(activeUnit).resp.smallmem_req
  controlUnit.smallspec_req <> configUnits(activeUnit).resp.smallspec_req

  controlUnit.act_done := configUnits(activeUnit).act_done
  configUnits(activeUnit).skip_actions := controlUnit.skip_actions

  configUnits(activeUnit).cu_wait := controlUnit.cu_wait

  when (activation_queue.deq.fire()) {
    activeUnit1 := activation_queue.deq.bits.tag
    configUnits(activation_queue.deq.bits.tag).req := Bool(true)
  }

  when(controlUnit.idle){
    activeUnit := activeUnit1
    activeUnit3 := activeUnit1
  }

  when(controlUnit.cu_wait&&controlUnit.firsttime){
    activeUnit2 := activeUnit
    configUnits(activeUnit).req := Bool(true)   
  }

  when(controlUnit.reidle){
    activeUnit := activeUnit2  
  }

  io.mem.req.bits.valid_req := (wait_for_resp_after_assert && (io.commitLog.bits.priv === UInt(0) || io.commitLog.bits.priv === UInt(1) || io.commitLog.bits.priv === UInt(3)) && (enabled)) 
  io.mem.req.valid := (mem_wait && (io.commitLog.bits.priv === UInt(0) || io.commitLog.bits.priv === UInt(1) || io.commitLog.bits.priv === UInt(3)) && (enabled) )
  // (controlUnit.smallact_mem_req.valid | mem_wait)

  // interrupt_en := controlUnit.interrupt_en || configUnits(activeUnit).resp.intr || io.commitLog.bits.interrupt_replay || (activation_queue.count === threshold) 
  interrupt_en := (activation_queue.count === threshold) 

  controlUnit.act_mem_resp.valid := (io.mem.resp.valid && wait_for_resp )
  pass := Bool(false);

  controlUnit.act_mem_resp.bits := io.mem.resp.bits.data

  when (controlUnit.act_alu_req.valid) {
    alu.fn := controlUnit.act_alu_req.bits.fn
    alu.in1 := controlUnit.act_alu_req.bits.in1
    alu.in2 := controlUnit.act_alu_req.bits.in2
    controlUnit.act_alu_resp.valid := Bool(true)
    heaprf(controlUnit.act_alu_req.bits.out) := alu.out
  }

  when (controlUnit.smallact_alu_req.valid) {
    shadowrf(controlUnit.smallact_alu_req.bits.out) := Mux(controlUnit.smallact_alu_req.bits.fn === 9.U, shadowrf(controlUnit.smallact_alu_req.bits.in1), shadowrf(controlUnit.smallact_alu_req.bits.in1)|shadowrf(controlUnit.smallact_alu_req.bits.in2))
    shadowheaprf(controlUnit.smallact_alu_req.bits.out) := shadowheaprf(controlUnit.smallact_alu_req.bits.in1)
    heaprf(controlUnit.smallact_alu_req.bits.out) := heaprf(controlUnit.smallact_alu_req.bits.in1)
    for(i <- 0 until 32){
      printf("shadowheaprf(%d) %d  shadowrf(%d) %d\n", i, shadowheaprf(i), i, shadowrf(i))
    }
  }  

  val fd = RegInit(UInt(0,width=5))
  when (controlUnit.smallact_spec_req.valid) // custom instructions
  { 
    val rs1 = controlUnit.smallact_spec_req.bits.in1
    val rs2 = controlUnit.smallact_spec_req.bits.in2
    val opcode = controlUnit.smallact_spec_req.bits.fn
    when (opcode===9.U){
      printf("Source(src):\n")
      when (rs1 === 0.U)
      {
        shadowheaprf(rs2) := 1.U
      }.otherwise{
        taintheaprf(controlUnit.smallact_spec_req.bits.in1bits) := 1.U // !rs2 value in fact
      }
    }
    .elsewhen(opcode === 1.U)
    {
      printf("Source(arg):\n")
      when (rs2 =/= 0.U && shadowrf(rs1) === 1.U)
      {
        shadowrf(rs2) := 1.U
        shadowheaprf(rs2) := 1.U
        fd := 0.U 
      }.elsewhen(rs2 === 0.U && shadowrf(rs1) === 1.U) 
      {
        fd := shadowrf(rs1)
      }
    }
    .elsewhen(opcode === 0.U)
    {
      printf("Source(taint):\n")
      when(rs1 =/= 0.U && rs2 === 0.U)
      {
        shadowrf(rs1) := 1.U 
      }.elsewhen(rs1 === 0.U && rs2 =/= 0.U && fd === 1.U){ 
        shadowrf(rs2) := 1.U
      }.elsewhen(rs1 === 2.U && rs2 =/= 0.U){
        taintrf(controlUnit.smallact_spec_req.bits.in1bits) := 1.U
      }.elsewhen(rs1 === 3.U && rs2 =/= 0.U)
      {
        taintgprf(controlUnit.smallact_spec_req.bits.in1bits) := 1.U
      }
    }
    .elsewhen(opcode === 7.U){
      printf("Sink:\n")
      when ((rs1 === 0.U && shadowrf(rs2) === 1.U) || (rs1 === 2.U && taintrf(controlUnit.smallact_spec_req.bits.in1bits) === 1.U) || (rs1 === 3.U && taintgprf(controlUnit.smallact_spec_req.bits.in1bits) === 1.U))
      {
        printf("tainted!\n")
        controlUnit.act_sink := Bool(true)
        val base = Bits("h8700_0000", 64)
        printf("pc_src = %x\n", controlUnit.smallact_spec_req.bits.pc_src)
        io.mem.req.bits.phys := Bool(true)
        specpcsrc := controlUnit.smallact_spec_req.bits.pc_src
        io.mem.req.bits.addr := base
        io.mem.req.bits.cmd := M_XWR
        io.mem.req.bits.size := log2Ceil(8).U
        mem_wait := Bool(true)
      }
    }
  }

  io.mem.req.bits.data := specpcsrc

  def loadgp(out: UInt) = {
    shadowrf(out) := taintgprf(gp-controlUnit.smallact_mem_req.bits.data)
    shadowheaprf(out) := 0.U
    heaprf(out) := 0.U
  }

  def loads0(in1: UInt, out: UInt) = {
    heaprf(out) := Mux(in1 === 8.U, controlUnit.smallact_mem_req.bits.data, 0.U) // s0, maybe load a address
    shadowrf(out) := Mux(taintheaprf(s0-heaprf(in1)) === 1.U, shadowrf(in1), taintrf(s0-controlUnit.smallact_mem_req.bits.data))
    shadowheaprf(out) := taintheaprf(s0-controlUnit.smallact_mem_req.bits.data) // data or address
  }

  def storegp(out: UInt) = {
    val eee = gp > controlUnit.smallact_mem_req.bits.data
    val fff = gp < controlUnit.smallact_mem_req.bits.data + sizegp
    when (eee === 1.U & fff === 1.U){
      when(controlUnit.smallact_mem_req.bits.storebits===0.U){ // store byte
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
      }
      when(controlUnit.smallact_mem_req.bits.storebits===1.U){ // store half word
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
      }
      when(controlUnit.smallact_mem_req.bits.storebits===2.U){ // store word
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-2) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-3) := shadowrf(out)
      }
      when(controlUnit.smallact_mem_req.bits.storebits===3.U){ // store double word
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-2) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-3) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-4) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-5) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-6) := shadowrf(out)
      taintgprf(gp-controlUnit.smallact_mem_req.bits.data-7) := shadowrf(out)
      }
    }
    .otherwise{
      printf("[Warning]: exceed size(gp)!\n")
    }
  }
  
  def stores0(in1: UInt, out: UInt) = {
    when(shadowheaprf(in1)===1.U){
      val aaa = s0 > heaprf(in1)
      val ccc = s0 < heaprf(in1) + size
      when (aaa === 1.U & ccc === 1.U){
        taintrf(s0-heaprf(in1)) := shadowrf(out)
      }.otherwise {
        printf("[Warning]: exceed size(stack)!\n")
      }
    }.otherwise{
      val bbb = s0 > controlUnit.smallact_mem_req.bits.data
      val ddd = s0 < controlUnit.smallact_mem_req.bits.data + size
      when (bbb === 1.U & ddd === 1.U){
        taintheaprf(s0-controlUnit.smallact_mem_req.bits.data) := shadowheaprf(out)
        when(controlUnit.smallact_mem_req.bits.storebits===0.U){
        taintrf(s0-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)}
        when(controlUnit.smallact_mem_req.bits.storebits===1.U){
        taintrf(s0-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
        }
        when(controlUnit.smallact_mem_req.bits.storebits===2.U){
        taintrf(s0-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-2) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-3) := shadowrf(out)
        }
        when(controlUnit.smallact_mem_req.bits.storebits===3.U){
        taintrf(s0-controlUnit.smallact_mem_req.bits.data) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-1) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-2) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-3) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-4) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-5) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-6) := shadowrf(out)
        taintrf(s0-controlUnit.smallact_mem_req.bits.data-7) := shadowrf(out)
        }
      }.otherwise {
        printf("[Warning]: exceed size(stack)!\n")
      }
    }
  }

  val in1 = controlUnit.smallmem_in1
  val out = controlUnit.smallmem_out
  when (controlUnit.smallact_mem_req.valid) { //memory access operation
    when (controlUnit.smallact_mem_req.bits.cmd === M_XRD){
      when (in1 === 3.U){
        loadgp(out)
      }.elsewhen (in1 === 8.U || in1 === 2.U){
        loads0(in1, out)
      }.otherwise{
        when (controlUnit.smallact_mem_req.bits.data < gp){
          loadgp(out)
        }.otherwise{
          loads0(in1, out)
        }
      }
    printf("Matched load:\n")
    for(i <- 0 until 32){
      printf("shadowheaprf(%d) %d  shadowrf(%d) %d\n",i,shadowheaprf(i),i,shadowrf(i))
    }
    printf("taintgprf:")
    for(i <- 0 until sizeigp){
      printf("%d",taintgprf(i))
    }
    printf("\n")
    printf("taintrf:")
    for(i <- 0 until sizei){
      printf("%d",taintrf(i))
    }
    printf("\n")
    }
  .otherwise{
  when (in1 === 3.U){
      storegp(out)
  }.elsewhen (in1 === 8.U ||in1 === 2.U){
      stores0(in1, out)
  }.otherwise{
      when (controlUnit.smallact_mem_req.bits.data < gp){
        storegp(out)
      }.otherwise {
      stores0(in1, out)
      }
    }
    printf("Matched store:\n")
    for(i <- 0 until 32){
      printf("shadowheaprf(%d) %d  shadowrf(%d) %d\n",i,shadowheaprf(i),i,shadowrf(i))
    }
    printf("taintgprf:")
    for(i <- 0 until sizeigp){
      printf("%d",taintgprf(i))
    }
    printf("\n")
    printf("taintrf:")
    for(i <- 0 until sizei){
      printf("%d",taintrf(i))
    }
    printf("\n")
  }
}

  controlUnit.act_intr := interrupt_en
  when (resume) {
    printf("resume\n")
    interrupt_valid := Bool(false)
    controlUnit.act_intr_done := Bool(true)
    controlUnit.act_sink_done := Bool(true)
  }
 
  io.busy := Bool(false)
  io.interrupt := interrupt_valid | interrupt_en

  when (interrupt_en) {
    printf("[EXTRA] Komodo: RoCC Interrupt(Queue full)\n")
  }

  when (io.interrupt) {
    printf("[EXTRA] Komodo: RoCC Interrupt\n")
  }

  when (interrupt_valid) {
    printf("[EXTRA] Komodo: RoCC Interrupt(Sink)\n")
  }

  when (io.mem.req.fire()) {
    busy_en := Bool(true)
    mem_wait := Bool(false)
    wait_for_resp :=Bool(true)
    printf("[MEM] Komodo memory request arrived, data: 0x%x, addr: 0x%x\n", io.mem.req.bits.data, io.mem.req.bits.addr)
  }
  
  when (io.mem.resp.valid && wait_for_resp) {
    busy_en := Bool(false)
    wait_for_resp := Bool(false)
    wait_for_resp_after_assert := Bool(false)
    printf("[MEM] Komodo memory response arrived, data: 0x%x\n", io.mem.resp.bits.data)
    interrupt_valid := Bool(true)
  }
  
  when (io.mem.assertion && wait_for_resp) {
    wait_for_resp_after_assert := Bool(true)
  }

  when (controlUnit.act_mem_req.valid && (!io.mem.req.ready || (io.commitLog.bits.priv =/= UInt(0) && io.commitLog.bits.priv =/= UInt(1)) || !enabled)) {
    mem_wait := Bool(true)
    printf("[EXTRA] Komodo has to wait for memory ready; data: 0x%x, addr: 0x%x\n", io.mem.req.bits.data, io.mem.req.bits.addr)
  }

  when (mem_wait && io.mem.req.ready) {
    printf("[EXTRA] Komodo memory ready has arrived!; data: 0x%x, addr: 0x%x  phys :%d   dprv  :%d  signed  :%d\n", io.mem.req.bits.data, io.mem.req.bits.addr,io.mem.req.bits.phys,io.mem.req.bits.dprv,io.mem.req.bits.signed )
  }
}