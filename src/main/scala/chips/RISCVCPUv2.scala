package chips

import chisel3._
import chisel3.util._
import chiselFv._
import spec.RVFI_IO


class RISCVCPUv2 extends Module with Formal {
  val io    = IO(new Bundle {
    val rvfi = new RVFI_IO
  })
  val LD    = "b000_0011".U(7.W)
  val SD    = "b010_0011".U(7.W)
  val BEQ   = "b110_0011".U(7.W) // (6/E)3
  val NOP   = "h0000_0013".U(32.W)
  val ALUop = "b011_0011".U(7.W)

  val PC                                            = RegInit(0.U(64.W))
  val Regs                                          = RegInit(VecInit(Seq.fill(32)(0.U(64.W))))
  val IDEXA, IDEXB, EXMEMB, EXMEMALUOut, MEMWBValue = Reg(UInt(64.W))
  val IMemory, DMemory                              = Mem(1024, UInt(32.W))
  val IFIDIR, IDEXIR, EXMEMIR, MEMWBIR              = RegInit(NOP)
  val IFIDrs1, IFIDrs2, MEMWBrd                     = Wire(UInt(5.W))
  val IFIDop, IDEXop, EXMEMop, MEMWBop              = Wire(UInt(7.W))

  IFIDrs1 := IFIDIR(19, 15)
  IFIDrs2 := IFIDIR(24, 20)
  IDEXop := IDEXIR(6, 0)
  IFIDop := IFIDIR(6, 0)
  EXMEMop := EXMEMIR(6, 0)
  MEMWBop := MEMWBIR(6, 0)
  MEMWBrd := MEMWBIR(11, 7)

  val EXMEMrd: UInt = EXMEMIR(11, 7)
  val IDEXrd : UInt = IDEXIR(11, 7)
  val IFID_funct3   = IFIDIR(14, 12)

  val bypassAFromWB  = (IFIDrs1 === MEMWBrd) && (IFIDrs1 =/= 0.U) && (MEMWBop === ALUop || MEMWBop === LD)
  val bypassBFromWB  = (IFIDrs2 === MEMWBrd) && (IFIDrs2 =/= 0.U) && (MEMWBop === ALUop || MEMWBop === LD)
  val bypassAFromMEM = (IFIDrs1 === EXMEMrd) && (IFIDrs1 =/= 0.U) && (EXMEMop === ALUop)
  val bypassBFromMEM = (IFIDrs2 === EXMEMrd) && (IFIDrs2 =/= 0.U) && (EXMEMop === ALUop)
  val bypassAFromEX  = (IFIDrs1 === IDEXrd) && (IFIDrs1 =/= 0.U) && (IDEXop === ALUop)
  val bypassBFromEX  = (IFIDrs2 === IDEXrd) && (IFIDrs2 =/= 0.U) && (IDEXop === ALUop)

  val stall = (
    (EXMEMop === LD) && (((IFIDrs1 === EXMEMrd) && (IFIDrs1 =/= 0.U)) || ((IFIDrs2 === EXMEMrd) && (IFIDrs2 =/= 0.U)))
    ) || (
    (IDEXop === LD) && (((IFIDrs1 === IDEXrd) && (IFIDrs1 =/= 0.U)) || ((IFIDrs2 === IDEXrd) && (IFIDrs2 =/= 0.U)))
    )

  var A, B       = WireInit(0.U(64.W))
  val takeBranch = (IFIDop === BEQ) && (IFID_funct3 === 0.U) && (A === B)

  // Auxiliary
  val MEMWBrs1 = MEMWBIR(19, 15)
  val MEMWBrs2 = MEMWBIR(24, 20)
  val CurPC    = RegInit(0.U(64.W))
  val NextPC   = RegInit(0.U(64.W))


  //  assume(stall === false.B)
  when((~stall).asBool) {
    when((~takeBranch).asBool) {
      // first instruction in pipeline is being fetched
      // Fetch & increment PC
      IFIDIR := IMemory.read((PC >> 2.U).asUInt)
      PC := PC + 4.U
      NextPC := PC
    }.otherwise {
      IFIDIR := NOP
      val branchTarget = (CurPC.asSInt + Cat(IFIDIR(31), IFIDIR(7), IFIDIR(30, 25), IFIDIR(11, 8), "b0".U(1.W)).asSInt).asUInt
      PC := branchTarget
      NextPC := branchTarget
    }
    CurPC := PC

    // second instruction in pipeline is fetching registers
    when(bypassAFromEX) {
      IDEXA := IDEXA + IDEXB
      A := IDEXA + IDEXB
    }.elsewhen(bypassAFromMEM) {
      IDEXA := EXMEMALUOut
      A := EXMEMALUOut
    }.elsewhen(bypassAFromWB) {
      IDEXA := MEMWBValue
      A := MEMWBValue
    }.otherwise {
      IDEXA := Regs(IFIDrs1)
      A := Regs(IFIDrs1)
    }
    when(bypassBFromEX) {
      IDEXB := IDEXB + IDEXA
      B := IDEXB + IDEXA
    }.elsewhen(bypassBFromMEM) {
      IDEXB := EXMEMALUOut
      B := EXMEMALUOut
    }.elsewhen(bypassBFromWB) {
      IDEXB := MEMWBValue
      B := MEMWBValue
    }.otherwise {
      IDEXB := Regs(IFIDrs2)
      B := Regs(IFIDrs2)
    }
    IDEXIR := IFIDIR
  }.otherwise {
    IDEXIR := NOP
    IDEXA := 0.U
    IDEXB := 0.U
  }

  // third instruction is doing address calculation or ALU operation
  when(IDEXop === LD) {
    EXMEMALUOut := (IDEXA.asSInt + IDEXIR(31, 20).asSInt).asUInt
  }.elsewhen(IDEXop === SD) {
    EXMEMALUOut := (IDEXA.asSInt + Cat(IDEXIR(31, 25), IDEXIR(11, 7)).asSInt).asUInt
  }.elsewhen(IDEXop === ALUop) {
    //    switch(IDEXIR(31, 25)) {
    //      is(0.U) {
    EXMEMALUOut := IDEXA + IDEXB
    //      }
    //    }
  }
  EXMEMIR := IDEXIR
  EXMEMB := IDEXB

  // Mem stage of pipeline
  when(EXMEMop === ALUop) {
    MEMWBValue := EXMEMALUOut
  }.elsewhen(EXMEMop === LD) {
    MEMWBValue := DMemory.read((EXMEMALUOut >> 2.U).asUInt)
  }.elsewhen(EXMEMop === SD) {
    DMemory.write((EXMEMALUOut >> 2.U).asUInt, EXMEMB)
  }

  MEMWBIR := EXMEMIR

  // rvfi io rd_wdata
  io.rvfi.rd_wdata := 0.U

  // WB stage
  when(((MEMWBop === LD) || (MEMWBop === ALUop)) && (MEMWBrd =/= 0.U)) {
    Regs(MEMWBrd) := MEMWBValue
    io.rvfi.rd_wdata := MEMWBValue
  }

  // rvfi io
  io.rvfi.regs := Regs
  io.rvfi.insn := MEMWBIR
  io.rvfi.valid := false.B
  io.rvfi.rs1_addr := 0.U
  io.rvfi.rs2_addr := 0.U
  io.rvfi.rs1_rdata := 0.U
  io.rvfi.rs2_rdata := 0.U
  io.rvfi.mem_addr := 0.U
  io.rvfi.pc_rdata := 0.U
  io.rvfi.pc_wdata := 0.U

  past(CurPC, 3) { past_v =>
    io.rvfi.valid := true.B
    io.rvfi.pc_rdata := past_v
  }
  past(NextPC, 2) { past_pc =>
    io.rvfi.pc_wdata := past_pc
  }
  past(IFIDrs1, 3) { past_rs1 =>
    io.rvfi.rs1_addr := past_rs1
  }
  past(IFIDrs2, 3) { past_rs2 =>
    io.rvfi.rs2_addr := past_rs2
  }
  past(IDEXA, 2) { past_rs1_data =>
    io.rvfi.rs1_rdata := past_rs1_data
  }
  past(IDEXB, 2) { past_rs2_data =>
    io.rvfi.rs2_rdata := past_rs2_data
  }
  io.rvfi.rd_addr := MEMWBrd
  past(EXMEMALUOut, 1) { past_mem_addr =>
    io.rvfi.mem_addr := past_mem_addr
  }
  io.rvfi.mem_rdata := MEMWBValue
  io.rvfi.mem_wdata := 0.U
  past(EXMEMB, 1) { past_mem_wdata =>
    io.rvfi.mem_wdata := past_mem_wdata
  }
}

//object RISCVCPUv2 extends App {
//  Check.generateRTL(() => new RISCVCPUv2)
//}



