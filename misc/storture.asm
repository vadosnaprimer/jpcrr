SECTION .text
ORG 0x100

main:
	MOV AX, DS
	MOV FS, AX
	CALL install_handlers

	MOV DWORD [ES:entropy_low], 0
	MOV DWORD [ES:entropy_high], 0
.loop:
	CALL do_vga_wait

	CLI
	MOV AX, 0xB800
	MOV DS, AX
	MOV AX, FS
	MOV ES, AX

	MOV AH, 0x07
	MOV AL, [ES:entropy_low + 0]
	MOV [10], AX
	MOV AL, [ES:entropy_low + 1]
	MOV [12], AX
	MOV AL, [ES:entropy_low + 2]
	MOV [14], AX
	MOV AL, [ES:entropy_low + 3]
	MOV [16], AX
	MOV AL, [ES:entropy_high + 0]
	MOV [20], AX
	MOV AL, [ES:entropy_high + 1]
	MOV [22], AX
	MOV AL, [ES:entropy_high + 2]
	MOV [24], AX
	MOV AL, [ES:entropy_high + 3]
	MOV [26], AX
	STI

	JMP .loop


install_handlers:
	MOV AX, 0
	MOV DS, AX
	MOV AX, [32]
	MOV [ES:old_irq0_off], AX
	MOV AX, [34]
	MOV [ES:old_irq0_seg], AX
	MOV AX, [36]
	MOV [ES:old_irq1_off], AX
	MOV AX, [38]
	MOV [ES:old_irq1_seg], AX
	MOV AX, CS
	MOV [34], AX
	MOV [38], AX
	MOV AX, irq0_handler
	MOV [32], AX
	MOV AX, irq1_handler
	MOV [36], AX
	MOV AX, CS
	MOV DS, AX
	RET

irq0_handler:
	PUSH EAX
	PUSH EDX
	RDTSC
	ADD [ES:entropy_low], EAX
	ADC [ES:entropy_high], EDX
	ADD DWORD [ES:entropy_low], 0x57
	ADC DWORD [ES:entropy_high], 0
	POP EDX
	POP EAX
	MOV AL, 0x20
	OUT 0x20, AL
	STI
	IRET
	JMP FAR [ES:old_irq0]

irq1_handler:
	PUSH EAX
	PUSH EDX
	RDTSC
	ADD [ES:entropy_low], EAX
	ADC [ES:entropy_high], EDX
	ADD DWORD [ES:entropy_low], 0x163
	ADC DWORD [ES:entropy_high], 0
	POP EDX
	POP EAX
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	IN AL, 0x60
	MOV AL, 0x20
	OUT 0x20, AL
	STI
	IRET
	JMP FAR [ES:old_irq1]

do_vga_wait:
.l1:
	MOV DX, 0x3DA
	IN AL, DX
	TEST AL, 0x0F
	JNZ .l1
.l2:
	IN AL, DX
	TEST AL, 0x0F
	JZ .l2
	RDTSC
	ADD [ES:entropy_low], EAX
	ADC [ES:entropy_high], EDX
	ADD DWORD [ES:entropy_low], 0x246
	ADC DWORD [ES:entropy_high], 0
	RET


SECTION .data
old_irq0:
old_irq0_off:
	DW	0
old_irq0_seg:
	DW	0
old_irq1:
old_irq1_off:
	DW	0
old_irq1_seg:
	DW	0

entropy_low:
	DB	0, 0, 0, 0
entropy_high:
	DB	0, 0, 0, 0
