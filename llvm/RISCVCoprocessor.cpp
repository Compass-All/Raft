#include "RISCV.h"
#include "RISCVInstrInfo.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/CodeGen/MachineInstrBuilder.h"

using namespace llvm;

#define RISCV_MACHINEINSTR_PRINTER_PASS_NAME "Dummy RISCV machineinstr printer pass"

namespace{
    class RISCVCoprocessor : public MachineFunctionPass{
        public:
        static char ID;
        RISCVCoprocessor() : MachineFunctionPass(ID){
            initializeRISCVCoprocessorPass(*PassRegistry::getPassRegistry());
        }
        bool runOnMachineFunction(MachineFunction &MF) override;
        StringRef getPassName() const override { return RISCV_MACHINEINSTR_PRINTER_PASS_NAME; }
    };
    char RISCVCoprocessor::ID = 0;


    bool RISCVCoprocessor::runOnMachineFunction(MachineFunction &MF){
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
                    return_tag = 1;
                }
                if(MI->isCall())
                {
                    auto &func = MI->getOperand(0);
                    if(func.isGlobal())
                        if(func.getGlobal()->getName() == "assert" || func.getGlobal()->getName() == "isalnum"
                        || func.getGlobal()->getName() == "isalpha" || func.getGlobal()->getName() == "iscntrl" || func.getGlobal()->getName() == "isdigit" 
                        || func.getGlobal()->getName() == "isgraph" || func.getGlobal()->getName() == "islower" || func.getGlobal()->getName() == "isprint" 
                        || func.getGlobal()->getName() == "ispunct" || func.getGlobal()->getName() == "isspace" || func.getGlobal()->getName() == "isupper" 
                        || func.getGlobal()->getName() == "isxdigit" || func.getGlobal()->getName() == "tolower" || func.getGlobal()->getName() == "toupper" 
                        || func.getGlobal()->getName() == "acos" || func.getGlobal()->getName() == "asin" || func.getGlobal()->getName() == "atan" 
                        || func.getGlobal()->getName() == "atan2" || func.getGlobal()->getName() == "cos" || func.getGlobal()->getName() == "cosh" 
                        || func.getGlobal()->getName() == "sin" || func.getGlobal()->getName() == "sinh" || func.getGlobal()->getName() == "tanh" 
                        || func.getGlobal()->getName() == "exp" || func.getGlobal()->getName() == "frexp" || func.getGlobal()->getName() == "ldexp" 
                        || func.getGlobal()->getName() == "log" || func.getGlobal()->getName() == "log10" || func.getGlobal()->getName() == "modf" 
                        || func.getGlobal()->getName() == "pow" || func.getGlobal()->getName() == "sqrt" || func.getGlobal()->getName() == "fmod" 
                        || func.getGlobal()->getName() == "signal" || func.getGlobal()->getName() == "raise" || func.getGlobal()->getName() == "feof" 
                        || func.getGlobal()->getName() == "ferror" || func.getGlobal()->getName() == "remove" || func.getGlobal()->getName() == "vprintf" 
                        || func.getGlobal()->getName() == "perror" || func.getGlobal()->getName() == "atof" || func.getGlobal()->getName() == "atoi" 
                        || func.getGlobal()->getName() == "atol" || func.getGlobal()->getName() == "strtod" || func.getGlobal()->getName() == "strtol" 
                        || func.getGlobal()->getName() == "strtoul" || func.getGlobal()->getName() == "abort" || func.getGlobal()->getName() == "div" 
                        || func.getGlobal()->getName() == "rand" || func.getGlobal()->getName() == "ldiv" || func.getGlobal()->getName() == "srand" 
                        || func.getGlobal()->getName() == "mblen" || func.getGlobal()->getName() == "mbtowc" || func.getGlobal()->getName() == "wctomb" 
                        || func.getGlobal()->getName() == "strcoll" || func.getGlobal()->getName() == "strcspn" || func.getGlobal()->getName() == "strerror" 
                        || func.getGlobal()->getName() == "strlen" || func.getGlobal()->getName() == "time" || func.getGlobal()->getName() == "clock" 
                        || func.getGlobal()->getName() == "ctime" || func.getGlobal()->getName() == "difftime" || func.getGlobal()->getName() == "gmtime" 
                        || func.getGlobal()->getName() == "localtime" || func.getGlobal()->getName() == "mktime" || func.getGlobal()->getName() == "strftime" 
                        || func.getGlobal()->getName() == "va_start" || func.getGlobal()->getName() == "va_end" || func.getGlobal()->getName() == "sleep" 
                        || func.getGlobal()->getName() == "__ctype_b_loc" || func.getGlobal()->getName() == "__assert_fail"
                        || func.getGlobal()->getName() == "fopen" || func.getGlobal()->getName() == "fopen64" || func.getGlobal()->getName() == "fgetc")
                        {
                            flag = 1;
                            const auto &DL = MI->getDebugLoc();
                            BuildMI(MBB,MI,DL,XII->get(RISCV::close),RISCV::X0).addReg(RISCV::X0);
                            return_tag = 1;
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
INITIALIZE_PASS(RISCVCoprocessor, "riscv machine pass", RISCV_MACHINEINSTR_PRINTER_PASS_NAME, true, true);

namespace llvm {
    FunctionPass *createRISCVCoprocessor() { return new RISCVCoprocessor(); }
}
