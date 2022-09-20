RISC-V Formal in Chisel
=======================

## Build

You can use Dockerfile to build the verification environment.

```shell
docker build -t chiselfv:v0 .
```

## Design and Implementation

In the classic architecture textbooks, *Computer Organization and Design RISC-V Edition*, a simple five-stage pipeline is given. 

<img src="https://github.com/Moorvan/PictureHost/blob/main/chiselfv/5pipeline.png?raw=true" height="540" />

## Formal Verification