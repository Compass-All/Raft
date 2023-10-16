/*
Control-flow Hijacking Detection Sample
*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "copro.h"

struct SIMPLE{
    char buffer[20];
    int private_data;
};

int test (void)
{
    struct SIMPLE s;
    s.private_data = 10; 
    // open file
    FILE *fp = fopen("test.txt", "r");
    if (NULL == fp)
    {
        printf("File open fail!\n");
    }
    // user input
    signed char input_char;
    int i = 0;
    for(i = 0; i < 24; i++)
    {
        input_char = fgetc(fp);
        s.buffer[i] = input_char; //source
    }

    fclose(fp);
    fp = NULL;
    return 0;
}

int main(void) {
    coprocessor_enable();
    test();
    return 0; //sink
}
