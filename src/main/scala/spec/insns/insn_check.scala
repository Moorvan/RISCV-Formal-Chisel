package spec.insns

import chisel3._
import chiselsby._
import spec.{RVFI_IO, spec_out}

class insn_check extends Module with Formal {
  val io    = IO(new Bundle() {
    val model_out = Flipped(new RVFI_IO)
    val spec_out  = Flipped(new spec_out)
  })
  val model = io.model_out
  val spec  = io.spec_out
  when(model.valid && spec.valid) {
    when(spec.rs1_addr === 0.U) {
      assert(model.rs1_rdata === 0.U)
    }
    when(spec.rs2_addr === 0.U) {
      assert(model.rs2_rdata === 0.U)
    }
    assert(model.rs1_addr === spec.rs1_addr)
    assert(model.rs2_addr === spec.rs2_addr)
    assert(model.rd_addr  === spec.rd_addr)
    assert(model.rs1_rdata === spec.rs1_rdata)
    assert(model.rs2_rdata === spec.rs2_rdata)
    assert(model.rd_wdata === spec.rd_wdata)
    assert(model.pc_wdata === spec.pc_wdata)
    assert(model.mem_addr === spec.mem_addr)
    assert(model.mem_wdata === spec.mem_wdata)
  }
}


object insn_check extends App {
  Check.generateRTL(() => new insn_check)
}
