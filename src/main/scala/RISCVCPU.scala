import chisel3._
import chisel3.util._
import chiselsby._


class RISCVCPU extends Module with Formal {
  val LD = "b000_0011".U(7.W)
  val SD = "b010_0011".U(7.W)
  val BEQ = "b110_0011".U(7.W)
  val NOP = "h0000_0013".U(32.W)
  val ALUop = "b011_0011".U(7.W)

  val PC = RegInit(0.U(64.W))
  val Regs = VecInit(Seq.fill(32)(RegInit(0.U(64.W))))
  val IDEXA, IDEXB, EXMEMB, EXMEMALUOut, MEMWBValue = Reg(UInt(64.W))
  val IMemory, DMemory = Mem(1024, UInt(32.W))
  val IFIDIR, IDEXIR, EXMEMIR, MEMWBIR = RegInit(NOP)
  val IFIDrs1, IFIDrs2, MEMWBrd = Wire(UInt(5.W))
  val IDEXop, EXMEMop, MEMWBop = Wire(UInt(7.W))
  val Ain, Bin = Wire(UInt(64.W))

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
  when (IDEXop === LD) {
    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 20))
  } .elsewhen (IDEXop === SD) {
    EXMEMALUOut := IDEXA + Cat(0.U(53.W), IDEXIR(31), IDEXIR(30, 25), IDEXIR(11, 7))
  } .elsewhen (IDEXop === ALUop) {
    switch (IDEXIR(31, 25)) {
      is (0.U) {
        EXMEMALUOut := Ain + Bin
      }
    }
  }
  EXMEMIR := IDEXIR
  EXMEMB := IDEXB

  // Mem stage of pipeline
  when (EXMEMop === ALUop) {
    MEMWBValue := EXMEMALUOut
  } .elsewhen (EXMEMop === LD) {
    MEMWBValue := DMemory.read((EXMEMALUOut >> 2.U).asUInt)
  } .elsewhen (EXMEMop === SD) {
    DMemory.write((EXMEMALUOut >> 2.U).asUInt, EXMEMB)
  }

  MEMWBIR := EXMEMIR

  // WB stage
  when (((MEMWBop === LD) || (MEMWBop === ALUop)) && (MEMWBrd =/= 0.U)) {
    Regs(MEMWBrd) := MEMWBValue
  }

  assert(Regs(0) === 0.U)

}


object RISCVCPU extends App {
//  Check.generateRTL(() => new RISCVCPU)
  Check.kInduction(() => new RISCVCPU, depth = 3)

}