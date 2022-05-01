package chips


import chisel3._
import chisel3.util._
import chiselsby._
import spec.RVFI_IO


class RISCVCPU extends Module with Formal {
  val io = IO(new Bundle {
    val rvfi = new RVFI_IO
  })
  val LD    = "b000_0011".U(7.W)
  val SD    = "b010_0011".U(7.W)
  val BEQ   = "b110_0011".U(7.W)
  val NOP   = "h0000_0013".U(32.W)
  val ALUop = "b011_0011".U(7.W)

  val PC                                            = RegInit(0.U(64.W))
  val Regs                                          = VecInit(Seq.fill(32)(RegInit(0.U(64.W))))
  val IDEXA, IDEXB, EXMEMB, EXMEMALUOut, MEMWBValue = Reg(UInt(64.W))
  val IMemory, DMemory                              = Mem(1024, UInt(32.W))
  val IFIDIR, IDEXIR, EXMEMIR, MEMWBIR              = RegInit(NOP)
  val IFIDrs1, IFIDrs2, MEMWBrd                     = Wire(UInt(5.W))
  val IDEXop, EXMEMop, MEMWBop                      = Wire(UInt(7.W))
  val Ain, Bin                                      = Wire(UInt(64.W))

  IFIDrs1 := IFIDIR(19, 15)
  IFIDrs2 := IFIDIR(24, 20)
  IDEXop := IDEXIR(6, 0)
  EXMEMop := EXMEMIR(6, 0)
  MEMWBop := MEMWBIR(6, 0)
  MEMWBrd := MEMWBIR(11, 7)
  Ain := IDEXA
  Bin := IDEXB

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
    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 20))
  }.elsewhen(IDEXop === SD) {
    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 25), IDEXIR(11, 7))
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

  // WB stage
  when(((MEMWBop === LD) || (MEMWBop === ALUop)) && (MEMWBrd =/= 0.U)) {
    Regs(MEMWBrd) := MEMWBValue
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
  past(IFIDrs1, 3) { past_rs1 =>
    io.rvfi.rs1_addr := past_rs1
    io.rvfi.valid := true.B
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
  io.rvfi.rd_wdata := MEMWBValue
  past(EXMEMALUOut, 1) { past_mem_addr =>
    io.rvfi.mem_addr := past_mem_addr
  }
  io.rvfi.rd_wdata := MEMWBValue
  io.rvfi.mem_rdata := MEMWBValue

}


object RISCVCPU extends App {
  //  Check.generateRTL(() => new chips.RISCVCPU)
  Check.kInduction(() => new RISCVCPU, depth = 3)

}