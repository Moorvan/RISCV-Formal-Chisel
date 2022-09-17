package spec.insns

import chisel3._
import chiselFv._
import spec.{RVFI_IO, spec_out}


class insn_add extends Module with Formal {
  val io = IO(new Bundle() {
    val in       = Flipped(new RVFI_IO)
    val spec_out = new spec_out
  })

  val insn      = io.in.insn
  val pc_rdata  = io.in.pc_rdata
  val pc_wdata  = io.in.pc_wdata
  val rs1_addr  = io.in.rs1_addr
  val rs2_addr  = io.in.rs2_addr
  val rs1_rdata = io.in.rs1_rdata
  val rs2_rdata = io.in.rs2_rdata
  val rd_addr   = io.in.rd_addr
  val rd_wdata  = io.in.rd_wdata
  val mem_rdata = io.in.mem_rdata

  // R-type instruction format
  val insn_padding = insn >> 32.U
  val insn_funct7  = insn(31, 25)
  val insn_rs2     = insn(24, 20)
  val insn_rs1     = insn(19, 15)
  val insn_funct3  = insn(14, 12)
  val insn_rd      = insn(11, 7)
  val insn_opcode  = insn(6, 0)

  // ADD instruction
  val sepc_valid     = insn_padding.asUInt === 0.U && insn_funct7 === 0.U && insn_funct3 === 0.U && insn_opcode === "b011_0011".U
  val spec_rs1_addr  = insn_rs1
  val spec_rs2_addr  = insn_rs2
  val spec_rs1_rdata = io.in.regs(spec_rs1_addr)
  val spec_rs2_rdata = io.in.regs(spec_rs2_addr)
  val spec_rd_addr   = insn_rd
  val result         = spec_rs1_rdata + spec_rs2_rdata
  val spec_rd_wdata  = Wire(UInt(64.W))
  when(spec_rd_addr === 0.U) {
    spec_rd_wdata := 0.U
  }.otherwise {
    spec_rd_wdata := result
  }

  io.spec_out.valid := sepc_valid
  io.spec_out.rs1_addr := spec_rs1_addr
  io.spec_out.rs2_addr := spec_rs2_addr
  io.spec_out.rs1_rdata := spec_rs1_rdata
  io.spec_out.rs2_rdata := spec_rs2_rdata
  io.spec_out.rd_addr := spec_rd_addr
  io.spec_out.rd_wdata := spec_rd_wdata
  io.spec_out.pc_wdata := pc_rdata + 4.U

  // without check
  io.spec_out.mem_addr := io.in.mem_addr
  io.spec_out.mem_wdata := io.in.mem_wdata

}


object insn_add extends App {
  Check.generateRTL(() => new insn_add())
}