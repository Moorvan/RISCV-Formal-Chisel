package spec

import chips._
import chisel3._
import chiselFv._
import spec.insns._


class RVFI_IO extends Bundle {
  val valid     = Output(Bool())
  val insn      = Output(UInt(32.W))
  val pc_rdata  = Output(UInt(64.W))
  val pc_wdata  = Output(UInt(64.W))
  val rs1_addr  = Output(UInt(5.W))
  val rs2_addr  = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(64.W))
  val rs2_rdata = Output(UInt(64.W))
  val rd_addr   = Output(UInt(5.W))
  val rd_wdata  = Output(UInt(64.W))
  val mem_addr  = Output(UInt(32.W))
  val mem_rdata = Output(UInt(64.W))
  val mem_wdata = Output(UInt(64.W))
  val regs      = Vec(32, Output(UInt(64.W)))
}

class spec_out extends Bundle {
  val valid     = Output(Bool())
  val rs1_addr  = Output(UInt(5.W))
  val rs2_addr  = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(64.W))
  val rs2_rdata = Output(UInt(64.W))
  val rd_addr   = Output(UInt(5.W))
  val rd_wdata  = Output(UInt(64.W))
  val pc_wdata  = Output(UInt(64.W))
  val mem_addr  = Output(UInt(32.W))
  val mem_wdata = Output(UInt(64.W))
}


class testbench extends Module with Formal {
  val model = Module(new RISCVCPUv2).io

  // instruction add check
 val insn_add_spec  = Module(new insn_add).io
 val insn_add_check = Module(new insn_check).io
 insn_add_spec.in := model.rvfi
 insn_add_check.model_out := model.rvfi
 insn_add_check.spec_out := insn_add_spec.spec_out

  // instruction ld check
 val insn_ld_spec  = Module(new insn_ld).io
 val insn_ld_check = Module(new insn_check).io
 insn_ld_spec.in := model.rvfi
 insn_ld_check.model_out := model.rvfi
 insn_ld_check.spec_out := insn_ld_spec.spec_out

  // instruction beq check
  val insn_beq_spec = Module(new insn_beq).io
  val insn_beq_check = Module(new insn_check).io
  insn_beq_spec.in := model.rvfi
  insn_beq_check.model_out := model.rvfi
  insn_beq_check.spec_out := insn_beq_spec.spec_out

  // instruction sd check
  val insn_sd_spec = Module(new insn_sd).io
  val insn_sd_check = Module(new insn_check).io
  insn_sd_spec.in := model.rvfi
  insn_sd_check.model_out := model.rvfi
  insn_sd_check.spec_out := insn_sd_spec.spec_out
}


object testbench extends App {
  Check.bmc(() => new testbench, 20)
//  Check.kInduction(() => new testbench, 20)
//  Check.pdr(() => new testbench, 10)
}