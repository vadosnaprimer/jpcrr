/*
 *  Copyright (C) 2002-2009  The DOSBox Team
 *  Copyright (C) 2011       H. Ilari Liusvaara
 *  OPL2/OPL3 emulation library
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */


/*
 * Originally based on ADLIBEMU.C, an AdLib/OPL2 emulation library by Ken Silverman
 * Copyright (C) 1998-2001 Ken Silverman
 * Ken Silverman's official web site: "http://www.advsys.net/ken"
 */

/*
 * Modified 20th April 2011 by Ilari:
 * - Replace static state with context.
 * - Remove OPL2 support (OPL3 is superset anyway)
 */

#ifndef OPL_INLINE
#define OPL_INLINE
#endif

#include <math.h>
#include <stdlib.h> // rand()
#include <string.h>
#include "opl.h"

// key scale level lookup table
static const fltype kslmul[4] = {
	0.0, 0.5, 0.25, 1.0		// -> 0, 3, 1.5, 6 dB/oct
};

// frequency multiplicator lookup table
static const fltype frqmul_tab[16] = {
	0.5,1,2,3,4,5,6,7,8,9,10,10,12,12,15,15
};

// map a channel number to the register offset of the modulator (=register base)
static const Bit8u modulatorbase[9]	= {
	0,1,2,
	8,9,10,
	16,17,18
};

// map a register base to a modulator operator number or operator number
static const Bit8u regbase2modop[44] = {
	0,1,2,0,1,2,0,0,3,4,5,3,4,5,0,0,6,7,8,6,7,8,					// first set
	18,19,20,18,19,20,0,0,21,22,23,21,22,23,0,0,24,25,26,24,25,26	// second set
};
static const Bit8u regbase2op[44] = {
	0,1,2,9,10,11,0,0,3,4,5,12,13,14,0,0,6,7,8,15,16,17,			// first set
	18,19,20,27,28,29,0,0,21,22,23,30,31,32,0,0,24,25,26,33,34,35	// second set
};

// start of the waveform
static Bit32u waveform[8] = {
	WAVEPREC,
	WAVEPREC>>1,
	WAVEPREC,
	(WAVEPREC*3)>>2,
	0,
	0,
	(WAVEPREC*5)>>2,
	WAVEPREC<<1
};

// length of the waveform as mask
static Bit32u wavemask[8] = {
	WAVEPREC-1,
	WAVEPREC-1,
	(WAVEPREC>>1)-1,
	(WAVEPREC>>1)-1,
	WAVEPREC-1,
	((WAVEPREC*3)>>2)-1,
	WAVEPREC>>1,
	WAVEPREC-1
};

// where the first entry resides
static Bit32u wavestart[8] = {
	0,
	WAVEPREC>>1,
	0,
	WAVEPREC>>2,
	0,
	0,
	0,
	WAVEPREC>>3
};

// envelope generator function constants
static fltype attackconst[4] = {
	(fltype)(1/2.82624),
	(fltype)(1/2.25280),
	(fltype)(1/1.88416),
	(fltype)(1/1.59744)
};
static fltype decrelconst[4] = {
	(fltype)(1/39.28064),
	(fltype)(1/31.41608),
	(fltype)(1/26.17344),
	(fltype)(1/22.44608)
};


void operator_advance(opl_context* ctx, op_type* op_pt, Bit32s vib) {
	op_pt->wfpos = op_pt->tcount;						// waveform position

	// advance waveform time
	op_pt->tcount += op_pt->tinc;
	op_pt->tcount += (Bit32s)(op_pt->tinc)*vib/FIXEDPT;

	op_pt->generator_pos += (ctx->generator_add);
}

void operator_advance_drums(opl_context* ctx, op_type* op_pt1, Bit32s vib1, op_type* op_pt2, Bit32s vib2, op_type* op_pt3, Bit32s vib3) {
	Bit32u c1 = op_pt1->tcount/FIXEDPT;
	Bit32u c3 = op_pt3->tcount/FIXEDPT;
	Bit32u phasebit = (((c1 & 0x88) ^ ((c1<<5) & 0x80)) | ((c3 ^ (c3<<2)) & 0x20)) ? 0x02 : 0x00;

	Bit32u noisebit = rand()&1;

	Bit32u snare_phase_bit = (((Bitu)((op_pt1->tcount/FIXEDPT) / 0x100))&1);

	//Hihat
	Bit32u inttm = (phasebit<<8) | (0x34<<(phasebit ^ (noisebit<<1)));
	op_pt1->wfpos = inttm*FIXEDPT;				// waveform position
	// advance waveform time
	op_pt1->tcount += op_pt1->tinc;
	op_pt1->tcount += (Bit32s)(op_pt1->tinc)*vib1/FIXEDPT;
	op_pt1->generator_pos += (ctx->generator_add);

	//Snare
	inttm = ((1+snare_phase_bit) ^ noisebit)<<8;
	op_pt2->wfpos = inttm*FIXEDPT;				// waveform position
	// advance waveform time
	op_pt2->tcount += op_pt2->tinc;
	op_pt2->tcount += (Bit32s)(op_pt2->tinc)*vib2/FIXEDPT;
	op_pt2->generator_pos += (ctx->generator_add);

	//Cymbal
	inttm = (1+phasebit)<<8;
	op_pt3->wfpos = inttm*FIXEDPT;				// waveform position
	// advance waveform time
	op_pt3->tcount += op_pt3->tinc;
	op_pt3->tcount += (Bit32s)(op_pt3->tinc)*vib3/FIXEDPT;
	op_pt3->generator_pos += (ctx->generator_add);
}


// output level is sustained, mode changes only when operator is turned off (->release)
// or when the keep-sustained bit is turned off (->sustain_nokeep)
void operator_output(opl_context* ctx, op_type* op_pt, Bit32s modulator, Bit32s trem) {
	if (op_pt->op_state != OF_TYPE_OFF) {
		op_pt->lastcval = op_pt->cval;
		Bit32u i = (Bit32u)((op_pt->wfpos+modulator)/FIXEDPT);

		// wform: -16384 to 16383 (0x4000)
		// trem :  32768 to 65535 (0x10000)
		// step_amp: 0.0 to 1.0
		// vol  : 1/2^14 to 1/2^29 (/0x4000; /1../0x8000)

		op_pt->cval = (Bit32s)(op_pt->step_amp*op_pt->vol*op_pt->cur_wform[i&op_pt->cur_wmask]*trem/16.0);
	}
}


// no action, operator is off
void operator_off(opl_context* ctx, op_type* /*op_pt*/) {
}

// output level is sustained, mode changes only when operator is turned off (->release)
// or when the keep-sustained bit is turned off (->sustain_nokeep)
void operator_sustain(opl_context* ctx, op_type* op_pt) {
	Bit32u num_steps_add = op_pt->generator_pos/FIXEDPT;	// number of (standardized) samples
	for (Bit32u ct=0; ct<num_steps_add; ct++) {
		op_pt->cur_env_step++;
	}
	op_pt->generator_pos -= num_steps_add*FIXEDPT;
}

// operator in release mode, if output level reaches zero the operator is turned off
void operator_release(opl_context* ctx, op_type* op_pt) {
	// ??? boundary?
	if (op_pt->amp > 0.00000001) {
		// release phase
		op_pt->amp *= op_pt->releasemul;
	}

	Bit32u num_steps_add = op_pt->generator_pos/FIXEDPT;	// number of (standardized) samples
	for (Bit32u ct=0; ct<num_steps_add; ct++) {
		op_pt->cur_env_step++;						// sample counter
		if ((op_pt->cur_env_step & op_pt->env_step_r)==0) {
			if (op_pt->amp <= 0.00000001) {
				// release phase finished, turn off this operator
				op_pt->amp = 0.0;
				if (op_pt->op_state == OF_TYPE_REL) {
					op_pt->op_state = OF_TYPE_OFF;
				}
			}
			op_pt->step_amp = op_pt->amp;
		}
	}
	op_pt->generator_pos -= num_steps_add*FIXEDPT;
}

// operator in decay mode, if sustain level is reached the output level is either
// kept (sustain level keep enabled) or the operator is switched into release mode
void operator_decay(opl_context* ctx, op_type* op_pt) {
	if (op_pt->amp > op_pt->sustain_level) {
		// decay phase
		op_pt->amp *= op_pt->decaymul;
	}

	Bit32u num_steps_add = op_pt->generator_pos/FIXEDPT;	// number of (standardized) samples
	for (Bit32u ct=0; ct<num_steps_add; ct++) {
		op_pt->cur_env_step++;
		if ((op_pt->cur_env_step & op_pt->env_step_d)==0) {
			if (op_pt->amp <= op_pt->sustain_level) {
				// decay phase finished, sustain level reached
				if (op_pt->sus_keep) {
					// keep sustain level (until turned off)
					op_pt->op_state = OF_TYPE_SUS;
					op_pt->amp = op_pt->sustain_level;
				} else {
					// next: release phase
					op_pt->op_state = OF_TYPE_SUS_NOKEEP;
				}
			}
			op_pt->step_amp = op_pt->amp;
		}
	}
	op_pt->generator_pos -= num_steps_add*FIXEDPT;
}

// operator in attack mode, if full output level is reached,
// the operator is switched into decay mode
void operator_attack(opl_context* ctx, op_type* op_pt) {
	op_pt->amp = ((op_pt->a3*op_pt->amp + op_pt->a2)*op_pt->amp + op_pt->a1)*op_pt->amp + op_pt->a0;

	Bit32u num_steps_add = op_pt->generator_pos/FIXEDPT;		// number of (standardized) samples
	for (Bit32u ct=0; ct<num_steps_add; ct++) {
		op_pt->cur_env_step++;	// next sample
		if ((op_pt->cur_env_step & op_pt->env_step_a)==0) {		// check if next step already reached
			if (op_pt->amp > 1.0) {
				// attack phase finished, next: decay
				op_pt->op_state = OF_TYPE_DEC;
				op_pt->amp = 1.0;
				op_pt->step_amp = 1.0;
			}
			op_pt->step_skip_pos_a <<= 1;
			if (op_pt->step_skip_pos_a==0) op_pt->step_skip_pos_a = 1;
			if (op_pt->step_skip_pos_a & op_pt->env_step_skip_a) {	// check if required to skip next step
				op_pt->step_amp = op_pt->amp;
			}
		}
	}
	op_pt->generator_pos -= num_steps_add*FIXEDPT;
}


typedef void (*optype_fptr)(opl_context* ctx, op_type*);

optype_fptr opfuncs[6] = {
	operator_attack,
	operator_decay,
	operator_release,
	operator_sustain,	// sustain phase (keeping level)
	operator_release,	// sustain_nokeep phase (release-style)
	operator_off
};

void change_attackrate(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	Bits attackrate = (ctx->adlibreg)[ARC_ATTR_DECR+regbase]>>4;
	if (attackrate) {
		fltype f = (fltype)(pow(FL2,(fltype)attackrate+(op_pt->toff>>2)-1)*attackconst[op_pt->toff&3]*(ctx->recipsamp));
		// attack rate coefficients
		op_pt->a0 = (fltype)(0.0377*f);
		op_pt->a1 = (fltype)(10.73*f+1);
		op_pt->a2 = (fltype)(-17.57*f);
		op_pt->a3 = (fltype)(7.42*f);

		Bits step_skip = attackrate*4 + op_pt->toff;
		Bits steps = step_skip >> 2;
		op_pt->env_step_a = (1<<(steps<=12?12-steps:0))-1;

		Bits step_num = (step_skip<=48)?(4-(step_skip&3)):0;
		static Bit8u step_skip_mask[5] = {0xff, 0xfe, 0xee, 0xba, 0xaa};
		op_pt->env_step_skip_a = step_skip_mask[step_num];

		if (step_skip>=60) {
			op_pt->a0 = (fltype)(2.0);	// something that triggers an immediate transition to amp:=1.0
			op_pt->a1 = (fltype)(0.0);
			op_pt->a2 = (fltype)(0.0);
			op_pt->a3 = (fltype)(0.0);
		}
	} else {
		// attack disabled
		op_pt->a0 = 0.0;
		op_pt->a1 = 1.0;
		op_pt->a2 = 0.0;
		op_pt->a3 = 0.0;
		op_pt->env_step_a = 0;
		op_pt->env_step_skip_a = 0;
	}
}

void change_decayrate(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	Bits decayrate = (ctx->adlibreg)[ARC_ATTR_DECR+regbase]&15;
	// decaymul should be 1.0 when decayrate==0
	if (decayrate) {
		fltype f = (fltype)(-7.4493*decrelconst[op_pt->toff&3]*(ctx->recipsamp));
		op_pt->decaymul = (fltype)(pow(FL2,f*pow(FL2,(fltype)(decayrate+(op_pt->toff>>2)))));
		Bits steps = (decayrate*4 + op_pt->toff) >> 2;
		op_pt->env_step_d = (1<<(steps<=12?12-steps:0))-1;
	} else {
		op_pt->decaymul = 1.0;
		op_pt->env_step_d = 0;
	}
}

void change_releaserate(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	Bits releaserate = (ctx->adlibreg)[ARC_SUSL_RELR+regbase]&15;
	// releasemul should be 1.0 when releaserate==0
	if (releaserate) {
		fltype f = (fltype)(-7.4493*decrelconst[op_pt->toff&3]*(ctx->recipsamp));
		op_pt->releasemul = (fltype)(pow(FL2,f*pow(FL2,(fltype)(releaserate+(op_pt->toff>>2)))));
		Bits steps = (releaserate*4 + op_pt->toff) >> 2;
		op_pt->env_step_r = (1<<(steps<=12?12-steps:0))-1;
	} else {
		op_pt->releasemul = 1.0;
		op_pt->env_step_r = 0;
	}
}

void change_sustainlevel(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	Bits sustainlevel = (ctx->adlibreg)[ARC_SUSL_RELR+regbase]>>4;
	// sustainlevel should be 0.0 when sustainlevel==15 (max)
	if (sustainlevel<15) {
		op_pt->sustain_level = (fltype)(pow(FL2,(fltype)sustainlevel * (-FL05)));
	} else {
		op_pt->sustain_level = 0.0;
	}
}

void change_waveform(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	if (regbase>=ARC_SECONDSET) regbase -= (ARC_SECONDSET-22);	// second set starts at 22
	// waveform selection
	op_pt->cur_wmask = wavemask[(ctx->wave_sel)[regbase]];
	op_pt->cur_wform = &(ctx->wavtable)[waveform[(ctx->wave_sel)[regbase]]];
	// (might need to be adapted to waveform type here...)
}

void change_keepsustain(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	op_pt->sus_keep = ((ctx->adlibreg)[ARC_TVS_KSR_MUL+regbase]&0x20)>0;
	if (op_pt->op_state==OF_TYPE_SUS) {
		if (!op_pt->sus_keep) op_pt->op_state = OF_TYPE_SUS_NOKEEP;
	} else if (op_pt->op_state==OF_TYPE_SUS_NOKEEP) {
		if (op_pt->sus_keep) op_pt->op_state = OF_TYPE_SUS;
	}
}

// enable/disable vibrato/tremolo LFO effects
void change_vibrato(opl_context* ctx, Bitu regbase, op_type* op_pt) {
	op_pt->vibrato = ((ctx->adlibreg)[ARC_TVS_KSR_MUL+regbase]&0x40)!=0;
	op_pt->tremolo = ((ctx->adlibreg)[ARC_TVS_KSR_MUL+regbase]&0x80)!=0;
}

// change amount of self-feedback
void change_feedback(opl_context* ctx, Bitu chanbase, op_type* op_pt) {
	Bits feedback = (ctx->adlibreg)[ARC_FEEDBACK+chanbase]&14;
	if (feedback) op_pt->mfbi = (Bit32s)(pow(FL2,(fltype)((feedback>>1)+8)));
	else op_pt->mfbi = 0;
}

void change_frequency(opl_context* ctx, Bitu chanbase, Bitu regbase, op_type* op_pt) {
	// frequency
	Bit32u frn = ((((Bit32u)(ctx->adlibreg)[ARC_KON_BNUM+chanbase])&3)<<8) + (Bit32u)(ctx->adlibreg)[ARC_FREQ_NUM+chanbase];
	// block number/octave
	Bit32u oct = ((((Bit32u)(ctx->adlibreg)[ARC_KON_BNUM+chanbase])>>2)&7);
	op_pt->freq_high = (Bit32s)((frn>>7)&7);

	// keysplit
	Bit32u note_sel = ((ctx->adlibreg)[8]>>6)&1;
	op_pt->toff = ((frn>>9)&(note_sel^1)) | ((frn>>8)&note_sel);
	op_pt->toff += (oct<<1);

	// envelope scaling (KSR)
	if (!((ctx->adlibreg)[ARC_TVS_KSR_MUL+regbase]&0x10)) op_pt->toff >>= 2;

	// 20+a0+b0:
	op_pt->tinc = (Bit32u)((((fltype)(frn<<oct))*(ctx->frqmul)[(ctx->adlibreg)[ARC_TVS_KSR_MUL+regbase]&15]));
	// 40+a0+b0:
	fltype vol_in = (fltype)((fltype)((ctx->adlibreg)[ARC_KSL_OUTLEV+regbase]&63) +
							kslmul[(ctx->adlibreg)[ARC_KSL_OUTLEV+regbase]>>6]*(ctx->kslev)[oct][frn>>6]);
	op_pt->vol = (fltype)(pow(FL2,(fltype)(vol_in * -0.125 - 14)));

	// operator frequency changed, care about features that depend on it
	change_attackrate(ctx, regbase,op_pt);
	change_decayrate(ctx, regbase,op_pt);
	change_releaserate(ctx, regbase,op_pt);
}

void enable_operator(opl_context* ctx, Bitu regbase, op_type* op_pt, Bit32u act_type) {
	// check if this is really an off-on transition
	if (op_pt->act_state == OP_ACT_OFF) {
		Bits wselbase = regbase;
		if (wselbase>=ARC_SECONDSET) wselbase -= (ARC_SECONDSET-22);	// second set starts at 22

		op_pt->tcount = wavestart[(ctx->wave_sel)[wselbase]]*FIXEDPT;

		// start with attack mode
		op_pt->op_state = OF_TYPE_ATT;
		op_pt->act_state |= act_type;
	}
}

void disable_operator(opl_context* ctx, op_type* op_pt, Bit32u act_type) {
	// check if this is really an on-off transition
	if (op_pt->act_state != OP_ACT_OFF) {
		op_pt->act_state &= (~act_type);
		if (op_pt->act_state == OP_ACT_OFF) {
			if (op_pt->op_state != OF_TYPE_OFF) op_pt->op_state = OF_TYPE_REL;
		}
	}
}

void adlib_init(opl_context* ctx, Bit32u samplerate) {
	Bits i, j, oct;

	//Clear the entiere state.
	memset((void *)ctx,0,sizeof(*ctx));

	(ctx->int_samplerate) = samplerate;

	(ctx->generator_add) = (Bit32u)(INTFREQU*FIXEDPT/(ctx->int_samplerate));

	for (i=0;i<MAXOPERATORS;i++) {
		(ctx->op)[i].op_state = OF_TYPE_OFF;
		(ctx->op)[i].act_state = OP_ACT_OFF;
		(ctx->op)[i].amp = 0.0;
		(ctx->op)[i].step_amp = 0.0;
		(ctx->op)[i].vol = 0.0;
		(ctx->op)[i].tcount = 0;
		(ctx->op)[i].tinc = 0;
		(ctx->op)[i].toff = 0;
		(ctx->op)[i].cur_wmask = wavemask[0];
		(ctx->op)[i].cur_wform = &(ctx->wavtable)[waveform[0]];
		(ctx->op)[i].freq_high = 0;

		(ctx->op)[i].generator_pos = 0;
		(ctx->op)[i].cur_env_step = 0;
		(ctx->op)[i].env_step_a = 0;
		(ctx->op)[i].env_step_d = 0;
		(ctx->op)[i].env_step_r = 0;
		(ctx->op)[i].step_skip_pos_a = 0;
		(ctx->op)[i].env_step_skip_a = 0;

		(ctx->op)[i].is_4op = false;
		(ctx->op)[i].is_4op_attached = false;
		(ctx->op)[i].left_pan = 1;
		(ctx->op)[i].right_pan = 1;
	}

	(ctx->recipsamp) = 1.0 / (fltype)(ctx->int_samplerate);
	for (i=15;i>=0;i--) {
		(ctx->frqmul)[i] = (fltype)(frqmul_tab[i]*INTFREQU/(fltype)WAVEPREC*(fltype)FIXEDPT*(ctx->recipsamp));
	}

	(ctx->status) = 0;
	(ctx->opl_index) = 0;


	// create vibrato table
	(ctx->vib_table)[0] = 8;
	(ctx->vib_table)[1] = 4;
	(ctx->vib_table)[2] = 0;
	(ctx->vib_table)[3] = -4;
	for (i=4; i<VIBTAB_SIZE; i++) (ctx->vib_table)[i] = (ctx->vib_table)[i-4]*-1;

	// vibrato at ~6.1 ?? (opl3 docs say 6.1, opl4 docs say 6.0, y8950 docs say 6.4)
	(ctx->vibtab_add) = static_cast<Bit32u>(VIBTAB_SIZE*FIXEDPT_LFO/8192*INTFREQU/(ctx->int_samplerate));
	(ctx->vibtab_pos) = 0;

	for (i=0; i<BLOCKBUF_SIZE; i++) (ctx->vibval_const)[i] = 0;


	// create tremolo table
	Bit32s trem_table_int[TREMTAB_SIZE];
	for (i=0; i<14; i++)	trem_table_int[i] = i-13;		// upwards (13 to 26 -> -0.5/6 to 0)
	for (i=14; i<41; i++)	trem_table_int[i] = -i+14;		// downwards (26 to 0 -> 0 to -1/6)
	for (i=41; i<53; i++)	trem_table_int[i] = i-40-26;	// upwards (1 to 12 -> -1/6 to -0.5/6)

	for (i=0; i<TREMTAB_SIZE; i++) {
		// 0.0 .. -26/26*4.8/6 == [0.0 .. -0.8], 4/53 steps == [1 .. 0.57]
		fltype trem_val1=(fltype)(((fltype)trem_table_int[i])*4.8/26.0/6.0);				// 4.8db
		fltype trem_val2=(fltype)((fltype)((Bit32s)(trem_table_int[i]/4))*1.2/6.0/6.0);		// 1.2db (larger stepping)

		(ctx->trem_table)[i] = (Bit32s)(pow(FL2,trem_val1)*FIXEDPT);
		(ctx->trem_table)[TREMTAB_SIZE+i] = (Bit32s)(pow(FL2,trem_val2)*FIXEDPT);
	}

	// tremolo at 3.7hz
	(ctx->tremtab_add) = (Bit32u)((fltype)TREMTAB_SIZE * TREM_FREQ * FIXEDPT_LFO / (fltype)(ctx->int_samplerate));
	(ctx->tremtab_pos) = 0;

	for (i=0; i<BLOCKBUF_SIZE; i++) (ctx->tremval_const)[i] = FIXEDPT;


	static Bitu initfirstime = 0;
	if (!initfirstime) {
		initfirstime = 1;

		// create waveform tables
		for (i=0;i<(WAVEPREC>>1);i++) {
			(ctx->wavtable)[(i<<1)  +WAVEPREC]	= (Bit16s)(16384*sin((fltype)((i<<1)  )*PI*2/WAVEPREC));
			(ctx->wavtable)[(i<<1)+1+WAVEPREC]	= (Bit16s)(16384*sin((fltype)((i<<1)+1)*PI*2/WAVEPREC));
			(ctx->wavtable)[i]					= (ctx->wavtable)[(i<<1)  +WAVEPREC];
			// alternative: (zero-less)
/*			wavtable[(i<<1)  +WAVEPREC]	= (Bit16s)(16384*sin((fltype)((i<<2)+1)*PI/WAVEPREC));
			wavtable[(i<<1)+1+WAVEPREC]	= (Bit16s)(16384*sin((fltype)((i<<2)+3)*PI/WAVEPREC));
			wavtable[i]					= wavtable[(i<<1)-1+WAVEPREC]; */
		}
		for (i=0;i<(WAVEPREC>>3);i++) {
			(ctx->wavtable)[i+(WAVEPREC<<1)]		= (ctx->wavtable)[i+(WAVEPREC>>3)]-16384;
			(ctx->wavtable)[i+((WAVEPREC*17)>>3)]	= (ctx->wavtable)[i+(WAVEPREC>>2)]+16384;
		}

		// key scale level table verified ([table in book]*8/3)
		(ctx->kslev)[7][0] = 0;	(ctx->kslev)[7][1] = 24;	(ctx->kslev)[7][2] = 32;	(ctx->kslev)[7][3] = 37;
		(ctx->kslev)[7][4] = 40;	(ctx->kslev)[7][5] = 43;	(ctx->kslev)[7][6] = 45;	(ctx->kslev)[7][7] = 47;
		(ctx->kslev)[7][8] = 48;
		for (i=9;i<16;i++) (ctx->kslev)[7][i] = (Bit8u)(i+41);
		for (j=6;j>=0;j--) {
			for (i=0;i<16;i++) {
				oct = (Bits)(ctx->kslev)[j+1][i]-8;
				if (oct < 0) oct = 0;
				(ctx->kslev)[j][i] = (Bit8u)oct;
			}
		}
	}

}



void adlib_write(opl_context* ctx, Bitu idx, Bit8u val) {
	Bit32u second_set = idx&0x100;
	(ctx->adlibreg)[idx] = val;

	switch (idx&0xf0) {
	case ARC_CONTROL:
		// here we check for the second set registers, too:
		switch (idx) {
		case 0x02:	// timer1 counter
		case 0x03:	// timer2 counter
			break;
		case 0x04:
			// IRQ reset, timer mask/start
			if (val&0x80) {
				// clear IRQ bits in status register
				(ctx->status) &= ~0x60;
			} else {
				(ctx->status) = 0;
			}
			break;
		case 0x04|ARC_SECONDSET:
			// 4op enable/disable switches for each possible channel
			(ctx->op)[0].is_4op = (val&1)>0;
			(ctx->op)[3].is_4op_attached = (ctx->op)[0].is_4op;
			(ctx->op)[1].is_4op = (val&2)>0;
			(ctx->op)[4].is_4op_attached = (ctx->op)[1].is_4op;
			(ctx->op)[2].is_4op = (val&4)>0;
			(ctx->op)[5].is_4op_attached = (ctx->op)[2].is_4op;
			(ctx->op)[18].is_4op = (val&8)>0;
			(ctx->op)[21].is_4op_attached = (ctx->op)[18].is_4op;
			(ctx->op)[19].is_4op = (val&16)>0;
			(ctx->op)[22].is_4op_attached = (ctx->op)[19].is_4op;
			(ctx->op)[20].is_4op = (val&32)>0;
			(ctx->op)[23].is_4op_attached = (ctx->op)[20].is_4op;
			break;
		case 0x05|ARC_SECONDSET:
			break;
		case 0x08:
			// CSW, note select
			break;
		default:
			break;
		}
		break;
	case ARC_TVS_KSR_MUL:
	case ARC_TVS_KSR_MUL+0x10: {
		// tremolo/vibrato/sustain keeping enabled; key scale rate; frequency multiplication
		int num = idx&7;
		Bitu base = (idx-ARC_TVS_KSR_MUL)&0xff;
		if ((num<6) && (base<22)) {
			Bitu modop = regbase2modop[second_set?(base+22):base];
			Bitu regbase = base+second_set;
			Bitu chanbase = second_set?(modop-18+ARC_SECONDSET):modop;

			// change tremolo/vibrato and sustain keeping of this operator
			op_type* op_ptr = &(ctx->op)[modop+((num<3) ? 0 : 9)];
			change_keepsustain(ctx, regbase,op_ptr);
			change_vibrato(ctx, regbase,op_ptr);

			// change frequency calculations of this operator as
			// key scale rate and frequency multiplicator can be changed
			if (((ctx->adlibreg)[0x105]&1) && ((ctx->op)[modop].is_4op_attached)) {
				// operator uses frequency of channel
				change_frequency(ctx, chanbase-3,regbase,op_ptr);
			} else {
				change_frequency(ctx, chanbase,regbase,op_ptr);
			}
		}
		}
		break;
	case ARC_KSL_OUTLEV:
	case ARC_KSL_OUTLEV+0x10: {
		// key scale level; output rate
		int num = idx&7;
		Bitu base = (idx-ARC_KSL_OUTLEV)&0xff;
		if ((num<6) && (base<22)) {
			Bitu modop = regbase2modop[second_set?(base+22):base];
			Bitu chanbase = second_set?(modop-18+ARC_SECONDSET):modop;

			// change frequency calculations of this operator as
			// key scale level and output rate can be changed
			op_type* op_ptr = &(ctx->op)[modop+((num<3) ? 0 : 9)];
			Bitu regbase = base+second_set;
			if (((ctx->adlibreg)[0x105]&1) && ((ctx->op)[modop].is_4op_attached)) {
				// operator uses frequency of channel
				change_frequency(ctx, chanbase-3,regbase,op_ptr);
			} else {
				change_frequency(ctx, chanbase,regbase,op_ptr);
			}
		}
		}
		break;
	case ARC_ATTR_DECR:
	case ARC_ATTR_DECR+0x10: {
		// attack/decay rates
		int num = idx&7;
		Bitu base = (idx-ARC_ATTR_DECR)&0xff;
		if ((num<6) && (base<22)) {
			Bitu regbase = base+second_set;

			// change attack rate and decay rate of this operator
			op_type* op_ptr = &(ctx->op)[regbase2op[second_set?(base+22):base]];
			change_attackrate(ctx, regbase,op_ptr);
			change_decayrate(ctx, regbase,op_ptr);
		}
		}
		break;
	case ARC_SUSL_RELR:
	case ARC_SUSL_RELR+0x10: {
		// sustain level; release rate
		int num = idx&7;
		Bitu base = (idx-ARC_SUSL_RELR)&0xff;
		if ((num<6) && (base<22)) {
			Bitu regbase = base+second_set;

			// change sustain level and release rate of this operator
			op_type* op_ptr = &(ctx->op)[regbase2op[second_set?(base+22):base]];
			change_releaserate(ctx, regbase,op_ptr);
			change_sustainlevel(ctx, regbase,op_ptr);
		}
		}
		break;
	case ARC_FREQ_NUM: {
		// 0xa0-0xa8 low8 frequency
		Bitu base = (idx-ARC_FREQ_NUM)&0xff;
		if (base<9) {
			Bits opbase = second_set?(base+18):base;
			if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[opbase].is_4op_attached) break;
			// regbase of modulator:
			Bits modbase = modulatorbase[base]+second_set;

			Bitu chanbase = base+second_set;

			change_frequency(ctx, chanbase,modbase,&(ctx->op)[opbase]);
			change_frequency(ctx, chanbase,modbase+3,&(ctx->op)[opbase+9]);
			// for 4op channels all four operators are modified to the frequency of the channel
			if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[second_set?(base+18):base].is_4op) {
				change_frequency(ctx, chanbase,modbase+8,&(ctx->op)[opbase+3]);
				change_frequency(ctx, chanbase,modbase+3+8,&(ctx->op)[opbase+3+9]);
			}
		}
		}
		break;
	case ARC_KON_BNUM: {
		if (idx == ARC_PERC_MODE) {
			if (second_set) return;

			if ((val&0x30) == 0x30) {		// BassDrum active
				enable_operator(ctx, 16,&(ctx->op)[6],OP_ACT_PERC);
				change_frequency(ctx, 6,16,&(ctx->op)[6]);
				enable_operator(ctx, 16+3,&(ctx->op)[6+9],OP_ACT_PERC);
				change_frequency(ctx, 6,16+3,&(ctx->op)[6+9]);
			} else {
				disable_operator(ctx, &(ctx->op)[6],OP_ACT_PERC);
				disable_operator(ctx, &(ctx->op)[6+9],OP_ACT_PERC);
			}
			if ((val&0x28) == 0x28) {		// Snare active
				enable_operator(ctx, 17+3,&(ctx->op)[16],OP_ACT_PERC);
				change_frequency(ctx, 7,17+3,&(ctx->op)[16]);
			} else {
				disable_operator(ctx, &(ctx->op)[16],OP_ACT_PERC);
			}
			if ((val&0x24) == 0x24) {		// TomTom active
				enable_operator(ctx, 18,&(ctx->op)[8],OP_ACT_PERC);
				change_frequency(ctx, 8,18,&(ctx->op)[8]);
			} else {
				disable_operator(ctx, &(ctx->op)[8],OP_ACT_PERC);
			}
			if ((val&0x22) == 0x22) {		// Cymbal active
				enable_operator(ctx, 18+3,&(ctx->op)[8+9],OP_ACT_PERC);
				change_frequency(ctx, 8,18+3,&(ctx->op)[8+9]);
			} else {
				disable_operator(ctx, &(ctx->op)[8+9],OP_ACT_PERC);
			}
			if ((val&0x21) == 0x21) {		// Hihat active
				enable_operator(ctx, 17,&(ctx->op)[7],OP_ACT_PERC);
				change_frequency(ctx, 7,17,&(ctx->op)[7]);
			} else {
				disable_operator(ctx, &(ctx->op)[7],OP_ACT_PERC);
			}

			break;
		}
		// regular 0xb0-0xb8
		Bitu base = (idx-ARC_KON_BNUM)&0xff;
		if (base<9) {
			Bits opbase = second_set?(base+18):base;
			if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[opbase].is_4op_attached) break;
			// regbase of modulator:
			Bits modbase = modulatorbase[base]+second_set;

			if (val&32) {
				// operator switched on
				enable_operator(ctx, modbase,&(ctx->op)[opbase],OP_ACT_NORMAL);		// modulator (if 2op)
				enable_operator(ctx, modbase+3,&(ctx->op)[opbase+9],OP_ACT_NORMAL);	// carrier (if 2op)
				// for 4op channels all four operators are switched on
				if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[opbase].is_4op) {
					// turn on chan+3 operators as well
					enable_operator(ctx, modbase+8,&(ctx->op)[opbase+3],OP_ACT_NORMAL);
					enable_operator(ctx, modbase+3+8,&(ctx->op)[opbase+3+9],OP_ACT_NORMAL);
				}
			} else {
				// operator switched off
				disable_operator(ctx, &(ctx->op)[opbase],OP_ACT_NORMAL);
				disable_operator(ctx, &(ctx->op)[opbase+9],OP_ACT_NORMAL);
				// for 4op channels all four operators are switched off
				if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[opbase].is_4op) {
					// turn off chan+3 operators as well
					disable_operator(ctx, &(ctx->op)[opbase+3],OP_ACT_NORMAL);
					disable_operator(ctx, &(ctx->op)[opbase+3+9],OP_ACT_NORMAL);
				}
			}

			Bitu chanbase = base+second_set;

			// change frequency calculations of modulator and carrier (2op) as
			// the frequency of the channel has changed
			change_frequency(ctx, chanbase,modbase,&(ctx->op)[opbase]);
			change_frequency(ctx, chanbase,modbase+3,&(ctx->op)[opbase+9]);
			// for 4op channels all four operators are modified to the frequency of the channel
			if (((ctx->adlibreg)[0x105]&1) && (ctx->op)[second_set?(base+18):base].is_4op) {
				// change frequency calculations of chan+3 operators as well
				change_frequency(ctx, chanbase,modbase+8,&(ctx->op)[opbase+3]);
				change_frequency(ctx, chanbase,modbase+3+8,&(ctx->op)[opbase+3+9]);
			}
		}
		}
		break;
	case ARC_FEEDBACK: {
		// 0xc0-0xc8 feedback/modulation type (AM/FM)
		Bitu base = (idx-ARC_FEEDBACK)&0xff;
		if (base<9) {
			Bits opbase = second_set?(base+18):base;
			Bitu chanbase = base+second_set;
			change_feedback(ctx, chanbase,&(ctx->op)[opbase]);
			// OPL3 panning
			(ctx->op)[opbase].left_pan = ((val&0x10)>>4);
			(ctx->op)[opbase].right_pan = ((val&0x20)>>5);
		}
		}
		break;
	case ARC_WAVE_SEL:
	case ARC_WAVE_SEL+0x10: {
		int num = idx&7;
		Bitu base = (idx-ARC_WAVE_SEL)&0xff;
		if ((num<6) && (base<22)) {
			Bits wselbase = second_set?(base+22):base;	// for easier mapping onto (ctx->wave_sel)[]
			// change waveform
			if ((ctx->adlibreg)[0x105]&1) (ctx->wave_sel)[wselbase] = val&7;	// opl3 mode enabled, all waveforms accessible
			else (ctx->wave_sel)[wselbase] = val&3;
			op_type* op_ptr = &(ctx->op)[regbase2modop[wselbase]+((num<3) ? 0 : 9)];
			change_waveform(ctx, wselbase,op_ptr);
		}
		}
		break;
	default:
		break;
	}
}


Bitu adlib_reg_read(opl_context* ctx, Bitu port) {
	// opl3-detection routines require ret&6 to be zero
	if ((port&1)==0) {
		return (ctx->status);
	}
	return 0x00;
}

void adlib_write_index(opl_context* ctx, Bitu port, Bit8u val) {
	(ctx->opl_index) = val;
	if ((port&3)!=0) {
		// possibly second set
		if ((((ctx->adlibreg)[0x105]&1)!=0) || ((ctx->opl_index)==5)) (ctx->opl_index) |= ARC_SECONDSET;
	}
}

static void OPL_INLINE clipit16(Bit32s ival, Bit16s* outval) {
	if (ival<32768) {
		if (ival>-32769) {
			*outval=(Bit16s)ival;
		} else {
			*outval = -32768;
		}
	} else {
		*outval = 32767;
	}
}



// be careful with this
// uses cptr and chanval, outputs into outbufl(/outbufr)
// for opl3 check if opl3-mode is enabled (which uses stereo panning)
#undef CHANVAL_OUT
#define CHANVAL_OUT									\
	if ((ctx->adlibreg)[0x105]&1) {						\
		outbufl[i] += chanval*cptr[0].left_pan;		\
		outbufr[i] += chanval*cptr[0].right_pan;	\
	} else {										\
		outbufl[i] += chanval;						\
	}

void adlib_getsample(opl_context* ctx, Bit16s* sndptr, Bits numsamples) {
	Bits i, endsamples;
	op_type* cptr;

	Bit32s outbufl[BLOCKBUF_SIZE];
	// second output buffer (right channel for opl3 stereo)
	Bit32s outbufr[BLOCKBUF_SIZE];

	// vibrato/tremolo lookup tables (global, to possibly be used by all operators)
	Bit32s vib_lut[BLOCKBUF_SIZE];
	Bit32s trem_lut[BLOCKBUF_SIZE];

	Bits samples_to_process = numsamples;

	for (Bits cursmp=0; cursmp<samples_to_process; cursmp+=endsamples) {
		endsamples = samples_to_process-cursmp;
		if (endsamples>BLOCKBUF_SIZE) endsamples = BLOCKBUF_SIZE;

		memset((void*)&outbufl,0,endsamples*sizeof(Bit32s));
		// clear second output buffer (opl3 stereo)
		if ((ctx->adlibreg)[0x105]&1) memset((void*)&outbufr,0,endsamples*sizeof(Bit32s));

		// calculate vibrato/tremolo lookup tables
		Bit32s vib_tshift = (((ctx->adlibreg)[ARC_PERC_MODE]&0x40)==0) ? 1 : 0;	// 14cents/7cents switching
		for (i=0;i<endsamples;i++) {
			// cycle through vibrato table
			(ctx->vibtab_pos) += (ctx->vibtab_add);
			if ((ctx->vibtab_pos)/FIXEDPT_LFO>=VIBTAB_SIZE) (ctx->vibtab_pos)-=VIBTAB_SIZE*FIXEDPT_LFO;
			vib_lut[i] = (ctx->vib_table)[(ctx->vibtab_pos)/FIXEDPT_LFO]>>vib_tshift;		// 14cents (14/100 of a semitone) or 7cents

			// cycle through tremolo table
			(ctx->tremtab_pos) += (ctx->tremtab_add);
			if ((ctx->tremtab_pos)/FIXEDPT_LFO>=TREMTAB_SIZE) (ctx->tremtab_pos)-=TREMTAB_SIZE*FIXEDPT_LFO;
			if ((ctx->adlibreg)[ARC_PERC_MODE]&0x80) trem_lut[i] = (ctx->trem_table)[(ctx->tremtab_pos)/FIXEDPT_LFO];
			else trem_lut[i] = (ctx->trem_table)[TREMTAB_SIZE+(ctx->tremtab_pos)/FIXEDPT_LFO];
		}

		if ((ctx->adlibreg)[ARC_PERC_MODE]&0x20) {
			//BassDrum
			cptr = &(ctx->op)[6];
			if ((ctx->adlibreg)[ARC_FEEDBACK+6]&1) {
				// additive synthesis
				if (cptr[9].op_state != OF_TYPE_OFF) {
					if (cptr[9].vibrato) {
						(ctx->vibval1) = (ctx->vibval_var1);
						for (i=0;i<endsamples;i++)
							(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
					} else (ctx->vibval1) = (ctx->vibval_const);
					if (cptr[9].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
					else (ctx->tremval1) = (ctx->tremval_const);

					// calculate channel output
					for (i=0;i<endsamples;i++) {
						operator_advance(ctx, &cptr[9],(ctx->vibval1)[i]);
						opfuncs[cptr[9].op_state](ctx, &cptr[9]);
						operator_output(ctx, &cptr[9],0,(ctx->tremval1)[i]);

						Bit32s chanval = cptr[9].cval*2;
						CHANVAL_OUT
					}
				}
			} else {
				// frequency modulation
				if ((cptr[9].op_state != OF_TYPE_OFF) || (cptr[0].op_state != OF_TYPE_OFF)) {
					if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
						(ctx->vibval1) = (ctx->vibval_var1);
						for (i=0;i<endsamples;i++)
							(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
					} else (ctx->vibval1) = (ctx->vibval_const);
					if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
						(ctx->vibval2) = (ctx->vibval_var2);
						for (i=0;i<endsamples;i++)
							(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
					} else (ctx->vibval2) = (ctx->vibval_const);
					if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
					else (ctx->tremval1) = (ctx->tremval_const);
					if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
					else (ctx->tremval2) = (ctx->tremval_const);

					// calculate channel output
					for (i=0;i<endsamples;i++) {
						operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
						opfuncs[cptr[0].op_state](ctx, &cptr[0]);
						operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

						operator_advance(ctx, &cptr[9],(ctx->vibval2)[i]);
						opfuncs[cptr[9].op_state](ctx, &cptr[9]);
						operator_output(ctx, &cptr[9],cptr[0].cval*FIXEDPT,(ctx->tremval2)[i]);

						Bit32s chanval = cptr[9].cval*2;
						CHANVAL_OUT
					}
				}
			}

			//TomTom (j=8)
			if ((ctx->op)[8].op_state != OF_TYPE_OFF) {
				cptr = &(ctx->op)[8];
				if (cptr[0].vibrato) {
					(ctx->vibval3) = (ctx->vibval_var1);
					for (i=0;i<endsamples;i++)
						(ctx->vibval3)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval3) = (ctx->vibval_const);

				if (cptr[0].tremolo) (ctx->tremval3) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval3) = (ctx->tremval_const);

				// calculate channel output
				for (i=0;i<endsamples;i++) {
					operator_advance(ctx, &cptr[0],(ctx->vibval3)[i]);
					opfuncs[cptr[0].op_state](ctx, &cptr[0]);		//TomTom
					operator_output(ctx, &cptr[0],0,(ctx->tremval3)[i]);
					Bit32s chanval = cptr[0].cval*2;
					CHANVAL_OUT
				}
			}

			//Snare/Hihat (j=7), Cymbal (j=8)
			if (((ctx->op)[7].op_state != OF_TYPE_OFF) || ((ctx->op)[16].op_state != OF_TYPE_OFF) ||
				((ctx->op)[17].op_state != OF_TYPE_OFF)) {
				cptr = &(ctx->op)[7];
				if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
					(ctx->vibval1) = (ctx->vibval_var1);
					for (i=0;i<endsamples;i++)
						(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval1) = (ctx->vibval_const);
				if ((cptr[9].vibrato) && (cptr[9].op_state == OF_TYPE_OFF)) {
					(ctx->vibval2) = (ctx->vibval_var2);
					for (i=0;i<endsamples;i++)
						(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval2) = (ctx->vibval_const);

				if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval1) = (ctx->tremval_const);
				if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval2) = (ctx->tremval_const);

				cptr = &(ctx->op)[8];
				if ((cptr[9].vibrato) && (cptr[9].op_state == OF_TYPE_OFF)) {
					(ctx->vibval4) = (ctx->vibval_var2);
					for (i=0;i<endsamples;i++)
						(ctx->vibval4)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval4) = (ctx->vibval_const);

				if (cptr[9].tremolo) (ctx->tremval4) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval4) = (ctx->tremval_const);

				// calculate channel output
				for (i=0;i<endsamples;i++) {
					operator_advance_drums(ctx, &(ctx->op)[7],(ctx->vibval1)[i],&(ctx->op)[7+9],(ctx->vibval2)[i],&(ctx->op)[8+9],(ctx->vibval4)[i]);

					opfuncs[(ctx->op)[7].op_state](ctx, &(ctx->op)[7]);			//Hihat
					operator_output(ctx, &(ctx->op)[7],0,(ctx->tremval1)[i]);

					opfuncs[(ctx->op)[7+9].op_state](ctx, &(ctx->op)[7+9]);		//Snare
					operator_output(ctx, &(ctx->op)[7+9],0,(ctx->tremval2)[i]);

					opfuncs[(ctx->op)[8+9].op_state](ctx, &(ctx->op)[8+9]);		//Cymbal
					operator_output(ctx, &(ctx->op)[8+9],0,(ctx->tremval4)[i]);

					Bit32s chanval = ((ctx->op)[7].cval + (ctx->op)[7+9].cval + (ctx->op)[8+9].cval)*2;
					CHANVAL_OUT
				}
			}
		}

		Bitu max_channel = NUM_CHANNELS;
		if (((ctx->adlibreg)[0x105]&1)==0) max_channel = NUM_CHANNELS/2;
		for (Bits cur_ch=max_channel-1; cur_ch>=0; cur_ch--) {
			// skip drum/percussion operators
			if (((ctx->adlibreg)[ARC_PERC_MODE]&0x20) && (cur_ch >= 6) && (cur_ch < 9)) continue;

			Bitu k = cur_ch;
			if (cur_ch < 9) {
				cptr = &(ctx->op)[cur_ch];
			} else {
				cptr = &(ctx->op)[cur_ch+9];	// second set is operator18-operator35
				k += (-9+256);		// second set uses registers 0x100 onwards
			}
			// check if this operator is part of a 4-op
			if (((ctx->adlibreg)[0x105]&1) && cptr->is_4op_attached) continue;

			// check for FM/AM
			if ((ctx->adlibreg)[ARC_FEEDBACK+k]&1) {
				if (((ctx->adlibreg)[0x105]&1) && cptr->is_4op) {
					if ((ctx->adlibreg)[ARC_FEEDBACK+k+3]&1) {
						// AM-AM-style synthesis (op1[fb] + (op2 * op3) + op4)
						if (cptr[0].op_state != OF_TYPE_OFF) {
							if (cptr[0].vibrato) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
								opfuncs[cptr[0].op_state](ctx, &cptr[0]);
								operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

								Bit32s chanval = cptr[0].cval;
								CHANVAL_OUT
							}
						}

						if ((cptr[3].op_state != OF_TYPE_OFF) || (cptr[9].op_state != OF_TYPE_OFF)) {
							if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if (cptr[9].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);
							if (cptr[3].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval2) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[9],(ctx->vibval1)[i]);
								opfuncs[cptr[9].op_state](ctx, &cptr[9]);
								operator_output(ctx, &cptr[9],0,(ctx->tremval1)[i]);

								operator_advance(ctx, &cptr[3],0);
								opfuncs[cptr[3].op_state](ctx, &cptr[3]);
								operator_output(ctx, &cptr[3],cptr[9].cval*FIXEDPT,(ctx->tremval2)[i]);

								Bit32s chanval = cptr[3].cval;
								CHANVAL_OUT
							}
						}

						if (cptr[3+9].op_state != OF_TYPE_OFF) {
							if (cptr[3+9].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[3+9],0);
								opfuncs[cptr[3+9].op_state](ctx, &cptr[3+9]);
								operator_output(ctx, &cptr[3+9],0,(ctx->tremval1)[i]);

								Bit32s chanval = cptr[3+9].cval;
								CHANVAL_OUT
							}
						}
					} else {
						// AM-FM-style synthesis (op1[fb] + (op2 * op3 * op4))
						if (cptr[0].op_state != OF_TYPE_OFF) {
							if (cptr[0].vibrato) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
								opfuncs[cptr[0].op_state](ctx, &cptr[0]);
								operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

								Bit32s chanval = cptr[0].cval;
								CHANVAL_OUT
							}
						}

						if ((cptr[9].op_state != OF_TYPE_OFF) || (cptr[3].op_state != OF_TYPE_OFF) || (cptr[3+9].op_state != OF_TYPE_OFF)) {
							if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if (cptr[9].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);
							if (cptr[3].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval2) = (ctx->tremval_const);
							if (cptr[3+9].tremolo) (ctx->tremval3) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval3) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[9],(ctx->vibval1)[i]);
								opfuncs[cptr[9].op_state](ctx, &cptr[9]);
								operator_output(ctx, &cptr[9],0,(ctx->tremval1)[i]);

								operator_advance(ctx, &cptr[3],0);
								opfuncs[cptr[3].op_state](ctx, &cptr[3]);
								operator_output(ctx, &cptr[3],cptr[9].cval*FIXEDPT,(ctx->tremval2)[i]);

								operator_advance(ctx, &cptr[3+9],0);
								opfuncs[cptr[3+9].op_state](ctx, &cptr[3+9]);
								operator_output(ctx, &cptr[3+9],cptr[3].cval*FIXEDPT,(ctx->tremval3)[i]);

								Bit32s chanval = cptr[3+9].cval;
								CHANVAL_OUT
							}
						}
					}
					continue;
				}
				// 2op additive synthesis
				if ((cptr[9].op_state == OF_TYPE_OFF) && (cptr[0].op_state == OF_TYPE_OFF)) continue;
				if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
					(ctx->vibval1) = (ctx->vibval_var1);
					for (i=0;i<endsamples;i++)
						(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval1) = (ctx->vibval_const);
				if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
					(ctx->vibval2) = (ctx->vibval_var2);
					for (i=0;i<endsamples;i++)
						(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval2) = (ctx->vibval_const);
				if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval1) = (ctx->tremval_const);
				if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval2) = (ctx->tremval_const);

				// calculate channel output
				for (i=0;i<endsamples;i++) {
					// carrier1
					operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
					opfuncs[cptr[0].op_state](ctx, &cptr[0]);
					operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

					// carrier2
					operator_advance(ctx, &cptr[9],(ctx->vibval2)[i]);
					opfuncs[cptr[9].op_state](ctx, &cptr[9]);
					operator_output(ctx, &cptr[9],0,(ctx->tremval2)[i]);

					Bit32s chanval = cptr[9].cval + cptr[0].cval;
					CHANVAL_OUT
				}
			} else {
				if (((ctx->adlibreg)[0x105]&1) && cptr->is_4op) {
					if ((ctx->adlibreg)[ARC_FEEDBACK+k+3]&1) {
						// FM-AM-style synthesis ((op1[fb] * op2) + (op3 * op4))
						if ((cptr[0].op_state != OF_TYPE_OFF) || (cptr[9].op_state != OF_TYPE_OFF)) {
							if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
								(ctx->vibval2) = (ctx->vibval_var2);
								for (i=0;i<endsamples;i++)
									(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval2) = (ctx->vibval_const);
							if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);
							if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval2) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
								opfuncs[cptr[0].op_state](ctx, &cptr[0]);
								operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

								operator_advance(ctx, &cptr[9],(ctx->vibval2)[i]);
								opfuncs[cptr[9].op_state](ctx, &cptr[9]);
								operator_output(ctx, &cptr[9],cptr[0].cval*FIXEDPT,(ctx->tremval2)[i]);

								Bit32s chanval = cptr[9].cval;
								CHANVAL_OUT
							}
						}

						if ((cptr[3].op_state != OF_TYPE_OFF) || (cptr[3+9].op_state != OF_TYPE_OFF)) {
							if (cptr[3].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);
							if (cptr[3+9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval2) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[3],0);
								opfuncs[cptr[3].op_state](ctx, &cptr[3]);
								operator_output(ctx, &cptr[3],0,(ctx->tremval1)[i]);

								operator_advance(ctx, &cptr[3+9],0);
								opfuncs[cptr[3+9].op_state](ctx, &cptr[3+9]);
								operator_output(ctx, &cptr[3+9],cptr[3].cval*FIXEDPT,(ctx->tremval2)[i]);

								Bit32s chanval = cptr[3+9].cval;
								CHANVAL_OUT
							}
						}

					} else {
						// FM-FM-style synthesis (op1[fb] * op2 * op3 * op4)
						if ((cptr[0].op_state != OF_TYPE_OFF) || (cptr[9].op_state != OF_TYPE_OFF) ||
							(cptr[3].op_state != OF_TYPE_OFF) || (cptr[3+9].op_state != OF_TYPE_OFF)) {
							if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
								(ctx->vibval1) = (ctx->vibval_var1);
								for (i=0;i<endsamples;i++)
									(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval1) = (ctx->vibval_const);
							if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
								(ctx->vibval2) = (ctx->vibval_var2);
								for (i=0;i<endsamples;i++)
									(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
							} else (ctx->vibval2) = (ctx->vibval_const);
							if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval1) = (ctx->tremval_const);
							if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval2) = (ctx->tremval_const);
							if (cptr[3].tremolo) (ctx->tremval3) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval3) = (ctx->tremval_const);
							if (cptr[3+9].tremolo) (ctx->tremval4) = trem_lut;	// tremolo enabled, use table
							else (ctx->tremval4) = (ctx->tremval_const);

							// calculate channel output
							for (i=0;i<endsamples;i++) {
								operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
								opfuncs[cptr[0].op_state](ctx, &cptr[0]);
								operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

								operator_advance(ctx, &cptr[9],(ctx->vibval2)[i]);
								opfuncs[cptr[9].op_state](ctx, &cptr[9]);
								operator_output(ctx, &cptr[9],cptr[0].cval*FIXEDPT,(ctx->tremval2)[i]);

								operator_advance(ctx, &cptr[3],0);
								opfuncs[cptr[3].op_state](ctx, &cptr[3]);
								operator_output(ctx, &cptr[3],cptr[9].cval*FIXEDPT,(ctx->tremval3)[i]);

								operator_advance(ctx, &cptr[3+9],0);
								opfuncs[cptr[3+9].op_state](ctx, &cptr[3+9]);
								operator_output(ctx, &cptr[3+9],cptr[3].cval*FIXEDPT,(ctx->tremval4)[i]);

								Bit32s chanval = cptr[3+9].cval;
								CHANVAL_OUT
							}
						}
					}
					continue;
				}
				// 2op frequency modulation
				if ((cptr[9].op_state == OF_TYPE_OFF) && (cptr[0].op_state == OF_TYPE_OFF)) continue;
				if ((cptr[0].vibrato) && (cptr[0].op_state != OF_TYPE_OFF)) {
					(ctx->vibval1) = (ctx->vibval_var1);
					for (i=0;i<endsamples;i++)
						(ctx->vibval1)[i] = (Bit32s)((vib_lut[i]*cptr[0].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval1) = (ctx->vibval_const);
				if ((cptr[9].vibrato) && (cptr[9].op_state != OF_TYPE_OFF)) {
					(ctx->vibval2) = (ctx->vibval_var2);
					for (i=0;i<endsamples;i++)
						(ctx->vibval2)[i] = (Bit32s)((vib_lut[i]*cptr[9].freq_high/8)*FIXEDPT*VIBFAC);
				} else (ctx->vibval2) = (ctx->vibval_const);
				if (cptr[0].tremolo) (ctx->tremval1) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval1) = (ctx->tremval_const);
				if (cptr[9].tremolo) (ctx->tremval2) = trem_lut;	// tremolo enabled, use table
				else (ctx->tremval2) = (ctx->tremval_const);

				// calculate channel output
				for (i=0;i<endsamples;i++) {
					// modulator
					operator_advance(ctx, &cptr[0],(ctx->vibval1)[i]);
					opfuncs[cptr[0].op_state](ctx, &cptr[0]);
					operator_output(ctx, &cptr[0],(cptr[0].lastcval+cptr[0].cval)*cptr[0].mfbi/2,(ctx->tremval1)[i]);

					// carrier
					operator_advance(ctx, &cptr[9],(ctx->vibval2)[i]);
					opfuncs[cptr[9].op_state](ctx, &cptr[9]);
					operator_output(ctx, &cptr[9],cptr[0].cval*FIXEDPT,(ctx->tremval2)[i]);

					Bit32s chanval = cptr[9].cval;
					CHANVAL_OUT
				}
			}
		}

		if ((ctx->adlibreg)[0x105]&1) {
			// convert to 16bit samples (stereo)
			for (i=0;i<endsamples;i++) {
				clipit16(outbufl[i],sndptr++);
				clipit16(outbufr[i],sndptr++);
			}
		} else {
			// convert to 16bit samples (mono)
			for (i=0;i<endsamples;i++) {
				clipit16(outbufl[i],sndptr++);
				clipit16(outbufl[i],sndptr++);
			}
		}
	}
}
