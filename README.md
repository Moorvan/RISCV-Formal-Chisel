RISC-V Formal in Chisel
=======================

## Build

You can use Dockerfile to build the verification environment.

```shell
docker build -t chiselfv:v0 .
```

## Design and Implementation

In the classic architecture textbooks, [*Computer Organization and Design RISC-V Edition*](http://bank.engzenon.com/tmp/5e7f7183-219c-4d93-911a-4aaec0feb99b/5dc835ea-b66c-4988-be3f-4d51c0feb99b/Computer_Organization_RiscV_Edition.pdf), a simple five-stage pipeline is given. 

<img src="https://github.com/Moorvan/PictureHost/blob/main/chiselfv/5pipeline.png?raw=true" height="480" />

This processor design has five pipeline stages: instruction fetch and decode, execution, memory access, and write back. 
It implements four typical instructions in the RISC-V instruction set, including LD, SD, ADD, and BEQ. It avoids data hazards by forwarding and stalling. On branch prediction, it adopts the way of assuming that the branch will not be taken to handle control hazards. If the prediction is wrong, a NOP instruction will be inserted in the middle to keep the pipeline correct. The book uses the Verilog language to implement the processor, and the specific code can be seen on pages 345.e9 to 345.e11. 
We reimplemented this five-stage pipeline processor using the Chisel language, and the Chisel module is in this repository.


## Formal Verification

Our verification solution is mainly inspired by the [RISC-V Formal Verification Framework](https://github.com/SymbioticEDA/riscv-formal). They provide a framework for verifying RISC-V processors at the SystemVerilog level, using SVA to define properties and then using verification tools to verify them.

We try to migrate the RISC-V Formal verification framework to the Chisel level by ChiselFV to verify the RISC-V processor at the Chisel level.

<img src="https://github.com/Moorvan/PictureHost/blob/main/chiselfv/riscvFvChisel.png?raw=true" height="270" />

The diagram above shows the verification architecture we implemented. The Chip module here is the RISC-V CPU module we implemented before. In the Chip, we need to provide the RISC-V Formal Interface (RVFI). RVFI defines important information about the processor during the execution of the instruction. The RVFI definition is as follows:

```scala
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
```

Here, the meaning of each signal is:

- valid: Indicates whether the current output RVFI signal is valid.
- insn: The instruction is currently being executed.
- pc_rdata: The value of the program counter before the instruction is loaded.
- pc_wdata: The value of the program counter after the instruction is loaded.
- rs1_addr: The register number of the first source operand.
- rs2_addr: The register number of the second source operand.
- rs1_rdata: The value of the first source operand.
- rs2_rdata: The value of the second source operand.
- rd_addr: The register number of the destination operand.
- rd_wdata: The value of the destination operand.
- mem_addr: The address of the memory access.
- mem_rdata: The data read from memory.
- mem_wdata: The data to be written back to memory.
- regs: The value of all registers.

Next, we define the Spec module. There can be many Spec modules, each Spec module describes the correct execution of a small property or a specific instruction of the processor. The input of each Spec module is also the RVFI signal. In the Spec module, we define how the key signals of the processor should change under the condition that the verification property is satisfied, and then output the spec_out signal.

```scala
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
```

The content of spec_out is similar to RVFI.

Next, we need to define the Check module, which inputs the RVFI and spec_out signals and asserts the consistency of the two.

```scala
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
   assert(model.regs(0) === 0.U)
   assert(model.rs1_addr === spec.rs1_addr)
   assert(model.rs2_addr === spec.rs2_addr)
   assert(model.rd_addr === spec.rd_addr)
   assert(model.rs1_rdata === spec.rs1_rdata)
   assert(model.rs2_rdata === spec.rs2_rdata)
   assert(model.rd_wdata === spec.rd_wdata)
   assert(model.pc_wdata === spec.pc_wdata)
   assert(model.mem_addr === spec.mem_addr)
   assert(model.mem_wdata === spec.mem_wdata)
  }
}
```
Finally, we connect these modules at the Top layer. In this project, the testbench module is used as the top module.
The code for calling the verification process is as follows:

```scala
object testbench extends App {
  Check.bmc(() => new testbench, 20)
}
```

Because the verification model here is more complex, only 20 steps of the BMC check are performed here.

## Results

In the check of the correctness of the execution of the BEQ instruction, we found an error. The verification engine gave an incorrect execution path, and part of the signals are shown in the following figure:

<img src="https://github.com/Moorvan/PictureHost/blob/main/chiselfv/trace.png?raw=true" style="zoom:67%;" />

We translated the instructions and summarized the relevant key information as shown in the following table:

<img src="https://github.com/Moorvan/PictureHost/blob/main/chiselfv/cex.png?raw=true" height="300"/>

For the BEQ instruction, we need to compare the value of the two source operands, but in the original design, these two source operands were not considered data hazards. 
Instead, they were directly taken from the register.

Later, we modified the design on the Chisel version and passed the relevant verification. The correct design is the RISCVCPUv2 module, and the original design is the RISCVCPUv2Error module.