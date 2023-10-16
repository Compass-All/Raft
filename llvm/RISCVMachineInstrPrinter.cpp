#include "RISCV.h"
#include "RISCVInstrInfo.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/CodeGen/MachineInstrBuilder.h"

using namespace llvm;

#define RISCV_MACHINEINSTR_PRINTER_PASS_NAME "Dummy RISCV machineinstr printer pass"

namespace{
    class RISCVMachineInstrPrinter : public MachineFunctionPass{
        public:
        static char ID;
        RISCVMachineInstrPrinter() : MachineFunctionPass(ID){
            initializeRISCVMachineInstrPrinterPass(*PassRegistry::getPassRegistry());
        }
        bool runOnMachineFunction(MachineFunction &MF) override;
        StringRef getPassName() const override { return RISCV_MACHINEINSTR_PRINTER_PASS_NAME; }
    };
    char RISCVMachineInstrPrinter::ID = 0;


    bool RISCVMachineInstrPrinter::runOnMachineFunction(MachineFunction &MF){
        static int flag = 0;
        static int return_tag = 0;
        const RISCVInstrInfo *XII = static_cast<const RISCVInstrInfo *>(MF.getSubtarget().getInstrInfo());
        for(auto &MBB : MF){
            for (auto MI = MBB.instr_begin();MI != MBB.instr_end();MI++)
            {
                if(flag == 1)
                {
                    flag = 0;
                    const auto &DL = MI->getDebugLoc();
                    BuildMI(MBB,MI,DL,XII->get(RISCV::src),RISCV::X0).addReg(RISCV::X10);
                    BuildMI(MBB,MI,DL,XII->get(RISCV::open),RISCV::X0).addReg(RISCV::X0);
                    BuildMI(MBB,MI,DL,XII->get(RISCV::ADDI),RISCV::X0).addReg(RISCV::X0).addImm(0);
                    return_tag = 1;
                }
                if(MI->isCall())
                {
                    auto &func = MI->getOperand(0);
                    if(func.isGlobal())
                        if(func.getGlobal()->getName() == "malloc" || func.getGlobal()->getName() == "realloc" || func.getGlobal()->getName() == "aligned_alloc" 
                        || func.getGlobal()->getName() == "alloca" || func.getGlobal()->getName() == "calloc" || func.getGlobal()->getName() == "mmap" 
                        || func.getGlobal()->getName() == "get_data" || func.getGlobal()->getName() == "_TIFFmalloc")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
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
INITIALIZE_PASS(RISCVMachineInstrPrinter, "riscv machine pass", RISCV_MACHINEINSTR_PRINTER_PASS_NAME, true, true);

namespace llvm {
    FunctionPass *createRISCVMachineInstrPrinter() { return new RISCVMachineInstrPrinter(); }
}