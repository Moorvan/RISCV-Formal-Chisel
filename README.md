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
For example, in the Spec module corresponding to the ADD instruction, we assume that the content of the instruction and the value of the registers in the output RVFI signal is correct. Then, according to the behavior definition of the ADD instruction in the RISC-V ISA, we give the value of the other signals defined in the spec_out. For example, the source operand address is decoded for the instruction, and the source operand value is obtained from the register signal output by RVFI according to the source operand address. These values are used as spec_out.


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

From the above table, we can see that the fifth instruction in this sequence is the BEQ instruction. The instruction content corresponding to the decoded instruction is BEQ x17, x20, 32. Its meaning is to compare the values of the x17 and x20 registers. If they are equal, the next instruction will jump. And the target address of the jump is the current PC value plus 0x20.
The current state of the relevant register values is that the value in the x17 register is 0x80000001, and the value in the x20 register is 0x0, which are not equal, so the correct result is that there will be no jump. However, in the given instruction sequence, a NOP instruction is inserted as a null instruction insertion for the jump prediction failure the next time. Then the instruction jumps to 0x2C. This is not by the ISA specification.

Further analysis of the given counterexample data (a VCD file, giving the assignment of each variable at each time in the path) shows that when the BEQ instruction is executed, the two source operands to be compared are both 0x0. In the current five-stage pipeline design, the LD instruction before the execution of the BEQ instruction is not completed, and the last step of the write-back has not occurred. Therefore, when the BEQ instruction is executed, the values of the x17 and x20 registers have not been updated. Here we need to design forwarding to get the real values of x17 and x20 to avoid errors caused by data hazards.

So we can conclude that the forwarding design for the source operands of BEQ in the original design is wrong. After checking the design, the error was confirmed.

<!-- For the BEQ instruction, we need to compare the value of the two source operands, but in the original design, these two source operands were not considered data hazards.  -->
<!-- Instead, they were directly taken from the register. -->

Later, we modified the design on the Chisel version and passed the relevant verification. The correct design is the RISCVCPUv2 module, and the original design is the RISCVCPUv2Error module.