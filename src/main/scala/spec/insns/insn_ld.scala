package spec.insns

import chisel3._
import chiselsby._
import spec.{RVFI_IO, spec_out}

class insn_ld extends Module with Formal {
  val io = IO(new Bundle() {
    val in       = Flipped(new RVFI_IO)
    val spec_out = new spec_out
  })

  val insn      = io.in.insn
  val pc_rdata  = io.in.pc_rdata
  val rs1_addr  = io.in.rs1_addr
  val rs2_addr  = io.in.rs2_addr
  val rs1_rdata = io.in.rs1_rdata
  val rs2_rdata = io.in.rs2_rdata
  val rd_addr   = io.in.rd_addr
  val rd_wdata  = io.in.rd_wdata
  val mem_rdata = io.in.mem_rdata

  // I-type instruction format
  val insn_padding = (insn >> 32.U).asUInt
  val insn_imm     = insn(31, 20).asSInt
  val insn_rs1     = insn(19, 15)
  val insn_funct3  = insn(14, 12)
  val insn_rd      = insn(11, 7)
  val insn_opcode  = insn(6, 0)

  val spec_valid     = (insn_funct3 === 0.U) && (insn_opcode === "b0000011".U)
  val spec_rs1_addr  = insn_rs1
  val spec_rd_addr   = insn_rd
  val spec_rs1_rdata = io.in.regs(spec_rs1_addr)
//  val spec_rs1_rdata = io.in.rs1_rdata
  val spec_mem_addr  = (spec_rs1_rdata.asSInt + insn_imm).asUInt
  val spec_rd_wdata  = Wire(UInt(32.W))
  when(spec_rd_addr =/= 0.U) {
    spec_rd_wdata := mem_rdata
  }.otherwise {
    spec_rd_wdata := 0.U
  }
  val spec_out_pc_wdata = pc_rdata + 4.U

  io.spec_out.valid := spec_valid
  io.spec_out.rs1_addr := spec_rs1_addr
  io.spec_out.rs1_rdata := spec_rs1_rdata
  io.spec_out.rd_addr := spec_rd_addr
  io.spec_out.mem_addr := spec_mem_addr
  io.spec_out.rd_wdata := spec_rd_wdata
  io.spec_out.pc_wdata := spec_out_pc_wdata

  // without check
  io.spec_out.rs2_addr := io.in.rs2_addr
  io.spec_out.rs2_rdata := io.in.rs2_rdata
  io.spec_out.mem_wdata := io.in.mem_wdata
}

object insn_ld extends App {
  Check.generateRTL(() => new insn_ld)
}

