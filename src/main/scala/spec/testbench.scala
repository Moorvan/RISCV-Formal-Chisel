package spec

import chips.RISCVCPU
import chisel3._
import chiselsby._
import spec.insns.insn_add


class RVFI_IO() extends Bundle {
  val valid     = Output(Bool())
  val insn      = Output(UInt(32.W))
//  val pc_rdata  = Output(UInt(32.W))
//  val pc_wdata  = Output(UInt(32.W))
  val rs1_addr  = Output(UInt(5.W))
  val rs2_addr  = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(32.W))
  val rs2_rdata = Output(UInt(32.W))
  val rd_addr   = Output(UInt(5.W))
  val rd_wdata  = Output(UInt(32.W))
  val mem_addr  = Output(UInt(32.W))
  val mem_rdata = Output(UInt(32.W))
//  val mem_wdata = Output(UInt(32.W))
  val regs      = Vec(32, Output(UInt(32.W)))
}

//class spec_out() extends Bundle {
//  val valid    = Output(Bool())
//  val rs1_addr = Output(UInt(5.W))
//  val rs2_addr = Output(UInt(5.W))
//  val rd_addr  = Output(UInt(5.W))
//  val rd_wdata = Output(UInt(32.W))
//}


class testbench extends Module with Formal {
  val model = Module(new RISCVCPU()).io
  val spec  = Module(new insn_add()).io
  spec.in := model.rvfi
}


object testbench extends App {
//  Check.bmc(() => new testbench, 10)
  Check.kInduction(() => new testbench, 10)
}