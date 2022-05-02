package spec

import chips.RISCVCPU
import chisel3._
import chiselsby._
import spec.insns.{insn_add, insn_check}


class RVFI_IO() extends Bundle {
  val valid     = Output(Bool())
  val insn      = Output(UInt(32.W))
  val pc_rdata  = Output(UInt(32.W))
  val pc_wdata  = Output(UInt(32.W))
  val rs1_addr  = Output(UInt(5.W))
  val rs2_addr  = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(32.W))
  val rs2_rdata = Output(UInt(32.W))
  val rd_addr   = Output(UInt(5.W))
  val rd_wdata  = Output(UInt(32.W))
  val mem_addr  = Output(UInt(32.W))
  val mem_rdata = Output(UInt(32.W))
  val mem_wdata = Output(UInt(32.W))
  val regs      = Vec(32, Output(UInt(32.W)))
}

class spec_out() extends Bundle {
  val valid    = Output(Bool())
  val rs1_addr = Output(UInt(5.W))
  val rs2_addr = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(32.W))
  val rs2_rdata = Output(UInt(32.W))
  val rd_addr  = Output(UInt(5.W))
  val rd_wdata = Output(UInt(32.W))
  val pc_wdata = Output(UInt(32.W))
  val mem_addr = Output(UInt(32.W))
  val mem_wdata = Output(UInt(32.W))
}


class testbench extends Module with Formal {
  val model = Module(new RISCVCPU).io

  // instruction add check
  val insn_add_spec = Module(new insn_add).io
  insn_add_spec.in := model.rvfi
  val insn_add_check = Module(new insn_check).io
  insn_add_check.model_out := model.rvfi
  insn_add_check.spec_out := insn_add_spec.spec_out



}


object testbench extends App {
//  Check.bmc(() => new testbench, 10)
  Check.kInduction(() => new testbench, 10)
}