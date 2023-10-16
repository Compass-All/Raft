# include "copro.h"
# include "varanus.h"

void coprocessor_enable(){
  int mus_num = 11;
  mask_t mask_inst[mus_num];
  act_conf_table_t action_mu1, action_mu2, action_mu3, action_mu4, action_mu5, action_mu6, action_mu7, action_mu8;

  for(int i = 0; i < mus_num; i++)
  {
    mask_inst[i].care.pc_src = 0x0000000000000000; // match all PC_src
    mask_inst[i].dont_care.pc_src = 0xffffffffffffffff;
    mask_inst[i].care.pc_dst = 0x0000000000000000; // match all PC_dst
    mask_inst[i].dont_care.pc_dst = 0xffffffffffffffff;
    mask_inst[i].care.rd = 0x0000000000000000;
    mask_inst[i].dont_care.rd = 0xffffffffffffffff;
    mask_inst[i].care.data = 0x0000000000000000;
    mask_inst[i].dont_care.data = 0xffffffffffffffff;
  }

  /****Taint(R3) = Taint(R1) OR Taint(R2)****/
  // match opcode 0111011
  mask_inst[0].care.inst = 0x0000003b; 
  mask_inst[0].dont_care.inst = 0xffffff80;
  // match opcode 0110011
  mask_inst[1].care.inst  = 0x00000033; 
  mask_inst[1].dont_care.inst = 0xffffff80;
  komodo_reset_val(0);
  komodo_reset_val(1);
  komodo_pattern(0, &mask_inst[0]);
  komodo_pattern(1, &mask_inst[1]);

  /****Taint(R2) = Taint(R1)****/
  // match opcode 0010011
  mask_inst[2].care.inst = 0x00000013; 
  mask_inst[2].dont_care.inst = 0xffffff80;
  komodo_reset_val(2);
  komodo_pattern(2, &mask_inst[2]);
  // match opcode 0011011
  mask_inst[5].care.inst = 0x0000001b; 
  mask_inst[5].dont_care.inst = 0xffffff80;
  komodo_reset_val(5);
  komodo_pattern(5, &mask_inst[5]);

  /****Load****/
  // match opcode 0000011
  mask_inst[3].care.inst = 0x00000003; 
  mask_inst[3].dont_care.inst = 0xffffff80;
  komodo_reset_val(3);
  komodo_pattern(3, &mask_inst[3]);

  /****Store****/
  // match opcode 0100011
  mask_inst[4].care.inst = 0x00000023; 
  mask_inst[4].dont_care.inst = 0xffffff80;
  komodo_reset_val(4);
  komodo_pattern(4, &mask_inst[4]);

  /****Jump****/ /****Sink****/
  // // match jal insts
  // mask_inst[5].care.inst = 0x0000006f; 
  // mask_inst[5].dont_care.inst = 0xffffff80;
  // // match jalr insts
  // mask_inst[6].care.inst = 0x00000067; 
  // mask_inst[6].dont_care.inst = 0xffff8f80;
  // match ret inst
  mask_inst[6].care.inst = 0x00008067; 
  mask_inst[6].dont_care.inst = 0x00000000;
  // match 0011010 111 0101011 insts sink 
  mask_inst[7].care.inst = 0x3400702b; 
  mask_inst[7].dont_care.inst = 0x01ff8f80;
  // komodo_reset_val(5);
  komodo_reset_val(6);
  komodo_reset_val(7);
  // komodo_pattern(5, &mask_inst[5]);
  komodo_pattern(6, &mask_inst[6]);
  komodo_pattern(7, &mask_inst[7]);

  /****Specify address****/
  // match 0010100 111 0101011 insts src
  mask_inst[8].care.inst = 0x2800702b; 
  mask_inst[8].dont_care.inst = 0x01ff8f80;
  komodo_reset_val(8);
  komodo_pattern(8, &mask_inst[8]);

  /****Source****/
  // match 0001100 111 0101011 insts taint
  mask_inst[9].care.inst = 0x1800702b; 
  mask_inst[9].dont_care.inst = 0x01ff8f80;
  komodo_reset_val(9);
  komodo_pattern(9, &mask_inst[9]);
  // match 0011000 111 0101011 insts arg
  mask_inst[10].care.inst = 0x3000702b; 
  mask_inst[10].dont_care.inst = 0x01ff8f80;
  komodo_reset_val(10);
  komodo_pattern(10, &mask_inst[10]);

  // ************** ActionType **************** //
  /****Taint(R3) = Taint(R1) OR Taint(R2)****/
  action_mu1.op_type = e_OP_SMALLALU; //
  action_mu1.in1 = e_IN_CUSTOM; 
  action_mu1.in2 = e_IN_CUSTOM;
  action_mu1.fn = e_ALU_OR; //7
  action_mu1.out = e_IN_CUSTOM; 
  action_mu1.data = 0;
  komodo_action_config(0, &action_mu1);
  komodo_action_config(1, &action_mu1);

  /****Taint(R2) = Taint(R1)****/
  action_mu2.op_type = e_OP_SMALLALU; //
  action_mu1.in1 = e_IN_CUSTOM; 
  action_mu2.in2 = e_IN_CUSTOM; 
  action_mu2.fn = e_ALU_NOP; //9
  action_mu2.out = e_IN_CUSTOM; 
  action_mu2.data = 0; 
  komodo_action_config(2, &action_mu2);
  komodo_action_config(5, &action_mu2);

  /****Load****/
  action_mu3.op_type = e_OP_SMALLMEM_RD; //
  action_mu3.in1 = e_IN_CUSTOM; 
  action_mu3.in2 = e_IN_CUSTOM; 
  action_mu3.fn = e_ALU_ADD;
  action_mu3.out = e_IN_CUSTOM; 
  action_mu3.data = 0; 
  komodo_action_config(3, &action_mu3);

  /****Store****/
  action_mu4.op_type = e_OP_SMALLMEM_WR; //
  action_mu4.in1 = e_IN_CUSTOM; 
  action_mu4.in2 = e_IN_CUSTOM; 
  action_mu4.fn = e_ALU_ADD;
  action_mu4.out = e_IN_CUSTOM; 
  action_mu4.data = 0; 
  komodo_action_config(4, &action_mu4);

  /****Sink****/
  action_mu5.op_type = e_OP_SMALLSPEC; //
  action_mu5.in1 = e_IN_LOC1; //
  action_mu5.in2 = e_IN_CUSTOM; 
  action_mu5.out = e_IN_CUSTOM; 
  action_mu5.data = 0; 
  action_mu5.fn = e_ALU_OR; //7 sink
  // komodo_action_config(5, &action_mu5);
  komodo_action_config(6, &action_mu5);
  komodo_action_config(7, &action_mu5);

  /****Specify address****/
  action_mu6.op_type = e_OP_SMALLSPEC; //
  action_mu6.in1 = e_IN_CUSTOM; 
  action_mu6.in2 = e_IN_CUSTOM; 
  action_mu6.out = e_IN_CUSTOM; 
  action_mu6.fn = e_ALU_NOP; //9 src
  action_mu6.data = 0; 
  komodo_action_config(8, &action_mu6);

  /****Source****/
  action_mu7.op_type = e_OP_SMALLSPEC; //
  action_mu7.in1 = e_IN_CUSTOM; 
  action_mu7.in2 = e_IN_CUSTOM; 
  action_mu7.out = e_IN_CUSTOM; 
  action_mu7.fn = e_ALU_ADD; //0 taint
  action_mu7.data = 0; 
  komodo_action_config(9, &action_mu7);

  action_mu8.op_type = e_OP_SMALLSPEC; //
  action_mu8.in1 = e_IN_CUSTOM; 
  action_mu8.in2 = e_IN_CUSTOM; 
  action_mu8.out = e_IN_CUSTOM; 
  action_mu8.fn = e_ALU_SUB; //1 arg
  action_mu8.data = 0; 
  komodo_action_config(10, &action_mu8);

  // Set match conditions
  xlen_t match_count[mus_num];

  for(int i = 0; i < mus_num; i++)
  {
    match_count[i] = 0;
  }
  
  for(int i = 0; i < mus_num; i++)
  {
    komodo_match_count(i, 1, &match_count[i]);
  }

  komodo_set_mem_typ(3);

  for(int i = 0; i < mus_num; i++)
  {
    komodo_set_commit_index(i, 4);
  }

  // komodo_set_commit_index(5, 0);
  komodo_set_commit_index(6, 0);
  komodo_set_commit_index(7, 0);

  komodo_enable_all();
}

void coprocessor_disable(){
  komodo_disable_all();
}

void coprocessor_reset(){
  komodo_reset_all();
}