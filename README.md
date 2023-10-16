# Raft-prototype

## Overview

Our prototype is built from Programmable Hardware Monitor (PHMon) [^1]. We run our experiments on the Xilinx Kintex-7 FPGA KC705 evaluation board. Considering code size, we only show the necessary modifications in this repository. 

## Contents

- `llvm`: patches for LLVM Compiler Infrastructure.
- `rocket-chip`: patches for Rocket Chip Generator.
- `rocket-chip/varanus`: code of the coprocessor.
- `security policy`: code to enable/disable the coprocessor.
- `security policy/copro.c`: code of the policy configuration.
- `wrapper`: patches for Wrapper for Rocket-Chip on FPGAs
- `kernel.patch`: patches for Linux Kernel.
- `opensbi.patch`: patches for RISC-V Open Source Supervisor Binary Interface.
- `uboot.patch`: patches for Das U-Boot Source Tree.
- `bitstreams`: generated bitstreams.
- `debian-riscv64-boot`:  linux kernel and bootloader.

## Usage

Make sure to install llvm, elf-gcc, and linux-gcc and set RISCV variable in advance. A Xilinx Kintex-7 FPGA KC705 evaluation board, an SD card, and a Vivado installation are required. We provide the generated bitstream, linux kernel, and bootloader in the repository. In addition, we recommend vivado-risc-v[^2] as the wrapper on KC705.

1. Compile the protected program

   ```
   $ cd security-policy
   $ make
   ```

   then you should get enable.riscv, disable.riscv, and test.riscv. Change the copro.c if you want to specify a new security policy and remake.

2. Program the FPGA

   - Drag compiled programs and debian-riscv64-boot/ into the SD card.
   - Connect your KC705 with a USB cable and power it on.
   - Open Hardware Manager in Vivado and program FPGA with the bitstream.

3. Boot linux

   You can login over UART console: 

   ```
   $ sudo microcom −p /dev/ttyUSB0 −s 115200
   ```
   
    after Linux boot,  you can run the protected program.
   
   ```
   $ ./enable.riscv 
   $ mv test.riscv t1.riscv 
   $ ./t1.riscv
   ```
   
   Note that we need to manually rename the protected program to t1.riscv in the current implementation. You can also disable the coprocessor by running disable.riscv. 

## Additional Information

### Citation

If you use this repository for research, please cite our paper:

```
@inproceedings{wang2023raft,
  title={Raft: Hardware-assisted Dynamic Information Flow Tracking for Runtime Protection on RISC-V},
  author={Wang, Yu and Wu, Jinting and Zheng, Haodong and Ning, Zhenyu and He, Boyuan and Zhang, Fengwei},
  booktitle={Proceedings of the 26th International Symposium on Research in Attacks, Intrusions and Defenses (RAID'23)},
  year={2023}
}
```

### Publication

Wang, Yu, et al. "Raft: Hardware-assisted Dynamic Information Flow Tracking for Runtime Protection on RISC-V." *Proceedings of the 26th International Symposium on Research in Attacks, Intrusions and Defenses (RAID'23)*. 2023.

  * [Paper](https://cse.sustech.edu.cn/faculty/~zhangfw/paper/raft-raid23.pdf)

### Others

- https://github.com/eugene-tarassov/vivado-risc-v: commit 1e99e190f6ef36e0142670a6446f978ffd992663
- https://github.com/llvm/llvm-project: commit fed41342a82f5a3a9201819a82bf7a48313e296b
- https://github.com/ucb-bar/rocket-chip.git: commit 1bd43fe1f154c0d180e1dd8be4b62602ce160045
- https://git.kernel.org/pub/scm/linux/kernel/git/stable/linux.git: commit 07e0b709cab7dc987b5071443789865e20481119
- https://github.com/riscv/opensbi.git: commit 48f91ee9c960f048c4a7d1da4447d31e04931e38
- https://github.com/u-boot/u-boot.git: commit d637294e264adfeb29f390dfc393106fd4d41b17

## Reference

[^1]: PHMon: A Programmable Hardware Monitor and Its Security Use Cases. https://github.com/bu-icsg/PHMon
[^2]: Xilinx Vivado block designs for FPGA RISC-V SoC running Debian Linux distro. https://github.com/eugene-tarassov/vivado-risc-v
