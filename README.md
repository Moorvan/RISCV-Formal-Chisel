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



## Formal Verification
