default: all

src_dir = .

bmarks = \
	disable\
	enable\
	test

RISCV_PREFIX ?= YOUR_PATH/llvm-project/install/bin/
RISCV_GCC ?= $(RISCV_PREFIX)clang --gcc-toolchain=YOUR_PATH/riscv-linux-gcc-rv64g/ --sysroot=YOUR_PATH/riscv-linux-gcc-rv64g/sysroot
RISCV_GCC_OPTS ?= -march=rv64g -z execstack -fno-stack-protector -O0 -static
RISCV_OBJDUMP ?= $(RISCV_PREFIX)llvm-objdump -d

define compile_template
$(1).riscv: $(wildcard $(src_dir)/$(1)/*)
	$$(RISCV_GCC) $$(RISCV_GCC_OPTS) komodo.c copro.c $(1).c -o $(1).riscv
endef

$(foreach bmark,$(bmarks),$(eval $(call compile_template,$(bmark))))

bmarks_riscv_bin  = $(addsuffix .riscv, $(bmarks))
bmarks_riscv_dump = $(addsuffix .riscv.dump, $(bmarks))

$(bmarks_riscv_dump): %.riscv.dump: %.riscv
	$(RISCV_OBJDUMP) $< > $@

riscv: $(bmarks_riscv_dump)

all: riscv

clean:
	rm *.dump *.riscv