package org.jpc.jrsr;

import java.io.IOException;

public class UTF8StreamDecoder
{
    int incompleteBuffer;
    int inIncompleteBuffer;
    int fullBuffer;
    long decodePosition;  //Used for error messages.

    private int countCharacters(byte[] array, int start, int length) throws IOException
    {
        int inIBuf = inIncompleteBuffer;
        int fBuf = fullBuffer;
        int count = (inIBuf > 0) ? 1 : 0;
        for(int i = start; i < start + length; i++) {
            if(fBuf > 0) {
                //Continuation.
                if(array[i] >= -64)
                    throw new IOException("Invalid UTF-8 continuation at byte position " + (decodePosition + i));
                else if(++inIBuf == fBuf) {
                    //Character completed. Mark it and reset state.
                    count++;
                    inIBuf = fBuf = 0;
                }
            } else {
                //Sync mark.
                if(array[i] < -64 || (array[i] < 0 && array[i] >= -8))
                    throw new IOException("Invalid UTF-8 character start at byte position " + (decodePosition + i));
                if(array[i] >= 0) {
                    //1 byte characters just increment the sync count.
                    count++;
                } else {
                    //Multibyte character starts.
                    inIBuf = 1;
                    if(array[i] < -32)
                        //2 bytes.
                        fBuf = 2;
                    else if(array[i] < -16)
                        //3 bytes.
                        fBuf = 3;
                    else
                        //4 bytes.
                        fBuf = 4;
                }
            }
        }
        //Dock one character if last character is incomplete.
        if(fBuf > 0)
            count--;
        return count;
    }

    public static int relativeOffset(int[] result, int index)
    {
        int r = 0;
        for(int i = 0; i < index; i++) {
            int s = result[i];
            if(s > 0x10000)
                r++;
            if(s > 0x800)
                r++;
            if(s > 0x80)
                r++;
            r++;
        }
        return r;
    }

    public int[] decode(byte[] array) throws IOException
    {
        return decode(array, 0, array.length);
    }

    public int[] decode(byte[] array, int start, int length) throws IOException
    {
        int chars = countCharacters(array, start, length);
        long origDP = decodePosition;
        decodePosition -= inIncompleteBuffer;
        int[] ret = new int[chars];
        int optr = 0;
        int minMagnitude = 0;
        //No need to check for sequence validity here, because countCharacters() does that.
        for(int i = start; i < start + length; i++) {
            if(fullBuffer > 0) {
                //Continuation.
                incompleteBuffer = incompleteBuffer * 64 + (array[i] + 128);
                if(++inIncompleteBuffer == fullBuffer) {
                    //Character completed. Mark it and reset state.
                    if((incompleteBuffer >= 0xD800 && incompleteBuffer <= 0xDFFF) || (incompleteBuffer >>> 16) > 16 ||
                        (incompleteBuffer & 0xFFFE) == 0xFFFE || incompleteBuffer < minMagnitude)
                        throw new IOException("Invalid character in UTF-8 stream at byte position " + decodePosition);
                    ret[optr++] = incompleteBuffer;
                    incompleteBuffer = inIncompleteBuffer = fullBuffer = 0;
                }
            } else {
                //Sync mark.
                decodePosition = origDP + i;
                if(array[i] >= 0) {
                    //1 byte. Just copy.
                    ret[optr++] = array[i];
                } else {
                    inIncompleteBuffer = 1;
                    if(array[i] < -32) {
                        //2 bytes.
                        fullBuffer = 2;
                        incompleteBuffer = array[i] + 64;
                        minMagnitude = 0x80;
                    } else if(array[i] < -16) {
                        //3 bytes.
                        fullBuffer = 3;
                        incompleteBuffer = array[i] + 32;
                        minMagnitude = 0x800;
                    } else {
                        //4 bytes.
                        fullBuffer = 4;
                        incompleteBuffer = array[i] + 16;
                        minMagnitude = 0x10000;
                    }
                }
            }
        }
        return ret;
    }

    public void sendEOF() throws IOException
    {
        if(inIncompleteBuffer != 0)
            throw new IOException("UTF-8 decoding error, stream ends with incomplete character");
    }
}
