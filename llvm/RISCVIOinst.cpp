#include "RISCV.h"
#include "RISCVInstrInfo.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/CodeGen/MachineInstrBuilder.h"

using namespace llvm;

#define RISCV_MACHINEINSTR_PRINTER_PASS_NAME "Dummy RISCV machineinstr printer pass"

namespace{
    class RISCVIOinst : public MachineFunctionPass{
        public:
        static char ID;
        RISCVIOinst() : MachineFunctionPass(ID){
            initializeRISCVIOinstPass(*PassRegistry::getPassRegistry());
        }
        bool runOnMachineFunction(MachineFunction &MF) override;
        StringRef getPassName() const override { return RISCV_MACHINEINSTR_PRINTER_PASS_NAME; }
    };
    char RISCVIOinst::ID = 0;


    bool RISCVIOinst::runOnMachineFunction(MachineFunction &MF){
        static int flag = 0;
        static int taint_mark = 0;
        static int return_tag = 0;
        static int taint_flag = 0;
        const RISCVInstrInfo *XII = static_cast<const RISCVInstrInfo *>(MF.getSubtarget().getInstrInfo());
        for(auto &MBB : MF){
            for (auto MI = MBB.instr_begin();MI != MBB.instr_end();MI++)
            {
                if(flag == 1)
                {
                    flag = 0;
                    const auto &DL = MI->getDebugLoc();
                    BuildMI(MBB,MI,DL,XII->get(RISCV::open),RISCV::X0).addReg(RISCV::X0);
                    BuildMI(MBB,MI,DL,XII->get(RISCV::ADDI),RISCV::X0).addReg(RISCV::X0).addImm(0);
                    return_tag = 1;
                }
                if(taint_flag == 1)
                {
                    taint_flag = 0;
                    const auto &DL = MI->getDebugLoc();
                    BuildMI(MBB,MI,DL,XII->get(RISCV::taint),RISCV::X10).addReg(RISCV::X0);
                    return_tag = 1;
                }
                if(taint_mark == 1)
                {
                    taint_mark = 0;
                    const auto &DL = MI->getDebugLoc();
                    BuildMI(MBB,MI,DL,XII->get(RISCV::taint),RISCV::X0)
                        .addReg(RISCV::X10);
                    return_tag = 1;
                }
                if(MI->isCall())
                {
                    auto &func = MI->getOperand(0);
                    if(func.isGlobal())
                    {
                        if(func.getGlobal()->getName() == "fputs")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "itoa" || func.getGlobal()->getName() == "ltoa" || func.getGlobal()->getName() == "ultoa"
                        || func.getGlobal()->getName() == "gcvt" || func.getGlobal()->getName() == "ecvt" || func.getGlobal()->getName() == "fcvt"
                        )
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::arg),RISCV::X10).addReg(RISCV::X11);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "putchar")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                                .addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "puts")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                                .addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "fwrite")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                                .addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "printf")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            // BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                            //     .addReg(RISCV::X10);
                            // BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                            //     .addReg(RISCV::X11);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "sprintf")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X11);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X12);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "fprintf")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X15);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0).addReg(RISCV::X16);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "fputc")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                                .addReg(RISCV::X10);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "write")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::sink),RISCV::X0)
                                .addReg(RISCV::X11);
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
                            return_tag = 1;
                        }
                        else if(func.getGlobal()->getName() == "fopen" || func.getGlobal()->getName() == "fopen64")
                        {
                            taint_flag = 1;
                            return_tag = 1;
                        }
                    }
                }
            }
        }
        if(return_tag){
            return true;
        }
        return false;
    }
}
INITIALIZE_PASS(RISCVIOinst, "riscv machine pass", RISCV_MACHINEINSTR_PRINTER_PASS_NAME, true, true);

namespace llvm {
    FunctionPass *createRISCVIOinst() { return new RISCVIOinst(); }
}
