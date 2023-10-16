#include "RISCV.h"
#include "RISCVInstrInfo.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/CodeGen/MachineInstrBuilder.h"

using namespace llvm;

#define RISCV_MACHINEINSTR_PRINTER_PASS_NAME "Dummy RISCV machineinstr printer pass"

namespace{
    class RISCVInputTaint : public MachineFunctionPass{
        public:
        static char ID;
        RISCVInputTaint() : MachineFunctionPass(ID){
            initializeRISCVInputTaintPass(*PassRegistry::getPassRegistry());
        }
        bool runOnMachineFunction(MachineFunction &MF) override;
        StringRef getPassName() const override { return RISCV_MACHINEINSTR_PRINTER_PASS_NAME; }
    };
    char RISCVInputTaint::ID = 0;


    bool RISCVInputTaint::runOnMachineFunction(MachineFunction &MF){
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
                    BuildMI(MBB,MI,DL,XII->get(RISCV::open),RISCV::X0).addReg(RISCV::X0);
                    BuildMI(MBB,MI,DL,XII->get(RISCV::ADDI),RISCV::X0).addReg(RISCV::X0).addImm(0);
                    BuildMI(MBB,MI,DL,XII->get(RISCV::taint),RISCV::X10).addReg(RISCV::X0);
                    return_tag = 1;
                }
                if(MI->isCall())
                {
                    auto &func = MI->getOperand(0);
                    if(func.isGlobal())
                    {
                        if(func.getGlobal()->getName() == "getchar" || func.getGlobal()->getName() == "gets" || func.getGlobal()->getName() == "ihex_get_byte" 
                        || func.getGlobal()->getName() == "fread" || func.getGlobal()->getName() == "pread" || func.getGlobal()->getName() == "fgetc" 
                        || func.getGlobal()->getName() == "fgets" || func.getGlobal()->getName() == "fscanf" || func.getGlobal()->getName() == "sscanf" 
                        || func.getGlobal()->getName() == "ungetc" || func.getGlobal()->getName() == "scanf" || func.getGlobal()->getName() == "getc" 
                        || func.getGlobal()->getName() == "get_data" || func.getGlobal()->getName() == "byte_get" || func.getGlobal()->getName() == "malloc" 
                        || func.getGlobal()->getName() == "read")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0)
                                .addReg(RISCV::X0);
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
INITIALIZE_PASS(RISCVInputTaint, "riscv machine pass", RISCV_MACHINEINSTR_PRINTER_PASS_NAME, true, true);

namespace llvm {
    FunctionPass *createRISCVInputTaint() { return new RISCVInputTaint(); }
}