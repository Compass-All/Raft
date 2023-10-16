#include "RISCV.h"
#include "RISCVInstrInfo.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/CodeGen/MachineInstrBuilder.h"

using namespace llvm;

#define RISCV_MACHINEINSTR_PRINTER_PASS_NAME "Dummy RISCV machineinstr printer pass"

namespace{
    class RISCVStackBase : public MachineFunctionPass{
        public:
        static char ID;
        RISCVStackBase() : MachineFunctionPass(ID){
            initializeRISCVStackBasePass(*PassRegistry::getPassRegistry());
        }
        bool runOnMachineFunction(MachineFunction &MF) override;
        StringRef getPassName() const override { return RISCV_MACHINEINSTR_PRINTER_PASS_NAME; }
    };
    char RISCVStackBase::ID = 0;


    bool RISCVStackBase::runOnMachineFunction(MachineFunction &MF){
        static int flag = 1;
        if (MF.getName() == "main")
        {
            for(auto &MBB : MF){
                if (flag == 1)
                {
                const RISCVInstrInfo *XII = static_cast<const RISCVInstrInfo *>(MF.getSubtarget().getInstrInfo());
                auto MI = MBB.instr_begin();
                const auto &DL = MI->getDebugLoc();
                BuildMI(MBB,MI,DL,XII->get(RISCV::ADDI),RISCV::X5).addReg(RISCV::X2).addImm(0);
                BuildMI(MBB,MI,DL,XII->get(RISCV::base),RISCV::X2).addReg(RISCV::X5);
                BuildMI(MBB,MI,DL,XII->get(RISCV::ADDI),RISCV::X5).addReg(RISCV::X3).addImm(0);
                BuildMI(MBB,MI,DL,XII->get(RISCV::base),RISCV::X3).addReg(RISCV::X5);
                flag = 0;
                return true;
                }
            }
        }
    }
}
INITIALIZE_PASS(RISCVStackBase, "riscv machine pass", RISCV_MACHINEINSTR_PRINTER_PASS_NAME, true, true);

namespace llvm {
    FunctionPass *createRISCVStackBase() { return new RISCVStackBase(); }
}