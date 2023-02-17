package spec.insns

import chisel3.util._
import chisel3._
import chiselFv._
import spec.{RVFI_IO, spec_out}

class insn_beq extends Module with Formal {
  val io = IO(new Bundle() {
    val in       = Flipped(new RVFI_IO)
    val spec_out = new spec_out
  })

  val insn = io.in.insn
  // SB-type instruction format
  val insn_padding = insn >> 32.U

  val insn_imm = Cat(insn(31), insn(7), insn(30, 25), insn(11, 8), "b0".U(1.W)).asSInt

  val spec_rs2_addr = insn(24, 20)
  val spec_rs1_addr = insn(19, 15)
  val spec_pc_rdata = io.in.pc_rdata
  val insn_funct3   = insn(14, 12)
  val insn_opcode   = insn(6, 0)

  val spec_rs1_rdata = io.in.regs(spec_rs1_addr)
  val spec_rs2_rdata = io.in.regs(spec_rs2_addr)

  val cond = spec_rs1_rdata === spec_rs2_rdata
  val spec_pc_wdata = Mux(cond, (spec_pc_rdata.asSInt + insn_imm).asUInt, spec_pc_rdata + 4.U)

  io.spec_out.valid := (insn_padding.asUInt === 0.U) && (insn_funct3 === 0.U) && (insn_opcode === "b110_0011".U(7.W))
  io.spec_out.rs1_addr := spec_rs1_addr
  io.spec_out.rs2_addr := spec_rs2_addr
  io.spec_out.rs1_rdata := spec_rs1_rdata
  io.spec_out.rs2_rdata := spec_rs2_rdata
  io.spec_out.pc_wdata := spec_pc_wdata

  // without check
  io.spec_out.rd_addr := io.in.rd_addr
  io.spec_out.rd_wdata := io.in.rd_wdata
  io.spec_out.mem_addr := io.in.mem_addr
  io.spec_out.mem_wdata := io.in.mem_wdata
}

//object insn_beq extends App {
//  Check.generateRTL(() => new insn_beq)
//}