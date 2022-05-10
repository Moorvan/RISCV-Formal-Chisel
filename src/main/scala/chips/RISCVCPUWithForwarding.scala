package chips

import chisel3._
import chisel3.util._
import chiselsby._
import spec.RVFI_IO


class RISCVCPUWithForwarding extends Module with Formal {
  val io    = IO(new Bundle {
    val rvfi = new RVFI_IO
  })
  val LD    = "b000_0011".U(7.W)
  val SD    = "b010_0011".U(7.W)
  val BEQ   = "b110_0011".U(7.W)
  val NOP   = "h0000_0013".U(32.W)
  val ALUop = "b011_0011".U(7.W)

  val PC                                                   = RegInit(0.U(64.W))
  val Regs                                                 = RegInit(VecInit(Seq.fill(32)(0.U(64.W))))
  val IDEXA, IDEXB, EXMEMB, EXMEMALUOut, MEMWBValue        = Reg(UInt(64.W))
  val IMemory, DMemory                                     = Mem(1024, UInt(32.W))
  val IFIDIR, IDEXIR, EXMEMIR, MEMWBIR                     = RegInit(NOP)
  val IFIDrs1, IFIDrs2, IDEXrs1, IDEXrs2, EXMEMrd, MEMWBrd = Wire(UInt(5.W))
  val IDEXop, EXMEMop, MEMWBop                             = Wire(UInt(7.W))
  val Ain, Bin                                             = Wire(UInt(64.W))

  val bypassAfromMEM, bypassAfromALUinWB, bypassBfromMEM, bypassBfromALUinWB, bypassAfromLDinWB, bypassBfromLDinWB = Wire(Bool())

  IFIDrs1 := IFIDIR(19, 15)
  IFIDrs2 := IFIDIR(24, 20)
  IDEXop := IDEXIR(6, 0)
  IDEXrs1 := IDEXIR(19, 15)
  IDEXrs2 := IDEXIR(24, 20)
  EXMEMop := EXMEMIR(6, 0)
  EXMEMrd := EXMEMIR(11, 7)
  MEMWBop := MEMWBIR(6, 0)
  MEMWBrd := MEMWBIR(11, 7)

  // The bypass to input from the MEM/WB stage for an ALU/LD operation
  bypassAfromMEM := (IDEXrs1 === EXMEMrd) && (IDEXrs1 =/= 0.U) && (EXMEMop === ALUop)
  bypassBfromMEM := (IDEXrs2 === EXMEMrd) && (IDEXrs2 =/= 0.U) && (EXMEMop === ALUop)
  bypassAfromALUinWB := (IDEXrs1 === MEMWBrd) && (IDEXrs1 =/= 0.U) && (MEMWBop === ALUop)
  bypassBfromALUinWB := (IDEXrs2 === MEMWBrd) && (IDEXrs2 =/= 0.U) && (MEMWBop === ALUop)
  bypassAfromLDinWB := (IDEXrs1 === MEMWBrd) && (IDEXrs1 =/= 0.U) && (MEMWBop === LD)
  bypassBfromLDinWB := (IDEXrs2 === MEMWBrd) && (IDEXrs2 =/= 0.U) && (MEMWBop === LD)

  // Auxiliary
  val MEMWBrs1: UInt = MEMWBIR(19, 15)
  val MEMWBrs2: UInt = MEMWBIR(24, 20)

  // Use the bypass signals to determine the input to the ALU
  when(bypassAfromMEM) {
    Ain := EXMEMALUOut
  }.elsewhen(bypassAfromALUinWB || bypassAfromLDinWB) {
    Ain := MEMWBValue
  }.otherwise {
    Ain := IDEXA
  }

  when(bypassBfromMEM) {
    Bin := EXMEMALUOut
  }.elsewhen(bypassBfromALUinWB || bypassBfromLDinWB) {
    Bin := MEMWBValue
  }.otherwise {
    Bin := IDEXB
  }

  // first instruction in pipeline is being fetched
  // Fetch & increment PC
  IFIDIR := IMemory.read((PC >> 2.U).asUInt)
  PC := PC + 4.U

  // second instruction in pipeline is fetching registers
  IDEXA := Regs(IFIDrs1)
  IDEXB := Regs(IFIDrs2)
  IDEXIR := IFIDIR

  // third instruction is doing address calculation or ALU operation
  when(IDEXop === LD) {
    //    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 20))
    EXMEMALUOut := (Ain.asSInt + IDEXIR(31, 20).asSInt).asUInt
  }.elsewhen(IDEXop === SD) {
    //    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 25), IDEXIR(11, 7))
    EXMEMALUOut := (Ain.asSInt + Cat(IDEXIR(31, 25), IDEXIR(11, 7)).asSInt).asUInt
  }.elsewhen(IDEXop === ALUop) {
    switch(IDEXIR(31, 25)) {
      is(0.U) {
        EXMEMALUOut := Ain + Bin
      }
    }
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
  past(PC, 4) { past_pc =>
    io.rvfi.valid := true.B
    io.rvfi.pc_rdata := past_pc
  }
  io.rvfi.pc_wdata := 0.U
  past(PC, 3) { past_pc =>
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
  //  past(Ain, 2) { past_rs1_data =>
  //    io.rvfi.rs1_rdata := past_rs1_data
  //  }
  //  past(Bin, 2) { past_rs2_data =>
  //    io.rvfi.rs2_rdata := past_rs2_data
  //  }
  io.rvfi.rd_addr := MEMWBrd
  past(EXMEMALUOut, 1) { past_mem_addr =>
    io.rvfi.mem_addr := past_mem_addr
  }
  io.rvfi.mem_rdata := MEMWBValue
  io.rvfi.mem_wdata := 0.U
  past(EXMEMB, 1) { past_mem_wdata =>
    io.rvfi.mem_wdata := past_mem_wdata
  }
  val IDEXrd: UInt = IDEXIR(11, 7)
  val pass  : Bool = IDEXrs1 === EXMEMrd || IDEXrs1 === MEMWBrd
  val pass1 : Bool = IFIDrs1 === IDEXrd || IFIDrs1 === EXMEMrd || IFIDrs1 === MEMWBrd
  val pass2 : Bool = IFIDrs2 === IDEXrd || IFIDrs2 === EXMEMrd || IFIDrs2 === MEMWBrd
  past(pass1, 3) { v =>
    when(v) {
      io.rvfi.rs1_rdata := Regs(MEMWBrs1)
    }
  }
  past(pass2, 3) { v =>
    when(v) {
      io.rvfi.rs2_rdata := Regs(MEMWBrs2)
    }
  }
  //  past(pass, 2) { v =>
  //    when(v) {
  //      io.rvfi.rs1_rdata := Regs(MEMWBrs1)
  //    }
  //  }
}


object RISCVCPUWithForwarding extends App {
  Check.generateRTL(() => new RISCVCPUWithForwarding)
}

