#include <stdio.h>
#include "copro.h"
// #include <time.h>

// unsigned long read_cycles(void)
// {
//     unsigned long cycles;
//     asm volatile ("rdcycle %0" : "=r" (cycles));
//     return cycles;
// }

int main(){
    // unsigned long cycle_start = read_cycles();
    // unsigned long cycle_start_1 = read_cycles();

    // struct timespec time_start = { 0, 0 }, time_end = { 0, 0 };
    // clock_gettime(CLOCK_REALTIME, &time_start);

    coprocessor_disable();
    
    printf("[KOMODO]: komodo is disabled\n");

    // clock_gettime(CLOCK_REALTIME, &time_end);
    // printf("run time: %ld s, %ld ns\n", time_end.tv_sec-time_start.tv_sec, time_end.tv_nsec-time_start.tv_nsec);

    // unsigned long cycle_end = read_cycles();
    // printf("function read_cycles() cycles: %lu cycles\n", cycle_start_1-cycle_start);
    // // printf("run cycles: %lu cycles\n", cycle_end-cycle_start);
    // printf("%lu\n", cycle_end-cycle_start);
}