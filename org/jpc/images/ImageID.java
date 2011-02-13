package org.jpc.images;
import java.io.*;
import org.jpc.emulator.SRDumpable;
import org.jpc.emulator.SRDumper;
import org.jpc.emulator.SRLoader;
import org.jpc.emulator.StatusDumper;

public class ImageID implements Comparable, SRDumpable
{
    private byte[] id;

    public ImageID(byte[] _id)
    {
        if(_id != null) {
            id = new byte[_id.length];
            System.arraycopy(_id, 0, id, 0, _id.length);
        } else
            id = null;
    }

    public byte[] getIDAsBytes()
    {
        if(id == null)
            return null;
        byte[] _id = new byte[id.length];
        System.arraycopy(id, 0, _id, 0, id.length);
        return _id;
    }

    private static int hexValueForChar(char x)
    {
        switch(x) {
        case '0':           return 0;
        case '1':           return 1;
        case '2':           return 2;
        case '3':           return 3;
        case '4':           return 4;
        case '5':           return 5;
        case '6':           return 6;
        case '7':           return 7;
        case '8':           return 8;
        case '9':           return 9;
        case 'A': case 'a': return 10;
        case 'B': case 'b': return 11;
        case 'C': case 'c': return 12;
        case 'D': case 'd': return 13;
        case 'E': case 'e': return 14;
        case 'F': case 'f': return 15;
        };
        return 0xFFFF;
    }

    private static char charForHexValue(int x)
    {
        switch(x) {
        case 0:  return '0';
        case 1:  return '1';
        case 2:  return '2';
        case 3:  return '3';
        case 4:  return '4';
        case 5:  return '5';
        case 6:  return '6';
        case 7:  return '7';
        case 8:  return '8';
        case 9:  return '9';
        case 10: return 'a';
        case 11: return 'b';
        case 12: return 'c';
        case 13: return 'd';
        case 14: return 'e';
        case 15: return 'f';
        }
        return 'X';
    }

    public ImageID(String _id) throws IllegalArgumentException
    {
        if(_id != null) {
            if(_id.length() % 2 != 0)
                throw new IllegalArgumentException("Bad image ID '" + _id + "'");
            id = new byte[_id.length() / 2];
            for(int i = 0; i < _id.length() / 2; i++) {
                int y = (hexValueForChar(_id.charAt(2 * i)) << 4) | hexValueForChar(_id.charAt(2 * i + 1));
                if(y > 0xFF)
                    throw new IllegalArgumentException("Bad image ID '" + _id + "'");
                id[i] = (byte)y;
            }
        } else
            id = null;
    }

    public String getIDAsString()
    {
        if(id == null)
            return null;
        StringBuffer buff = new StringBuffer(2 * id.length);
        for(int i = 0; i < id.length; i++) {
            buff.append(charForHexValue(((int)id[i] & 0xFF) >>> 4));
            buff.append(charForHexValue(id[i] & 0xF));
        }
        return buff.toString();
    }

    public boolean equals(Object x)
    {
        if(x == null || !(x instanceof ImageID))
            return false;
        ImageID _x = (ImageID)x;
        if(id == _x.id)
            return true;
        if(id == null || _x.id == null || id.length != _x.id.length)
            return false;
        byte c = 0;
        for(int i = 0; i < id.length; i++)
            c |= (id[i] ^ _x.id[i]);
        return (c == 0);
    }

    public int hashCode()
    {
        int x = 0;
        if(id.length > 0)
            x |= ((int)id[0] & 0xFF);
        if(id.length > 1)
            x |= (((int)id[1] & 0xFF) << 8);
        if(id.length > 2)
            x |= (((int)id[2] & 0xFF) << 16);
        if(id.length > 3)
            x |= (((int)id[3] & 0xFF) << 24);
        return x;
    }

    public Object clone()
    {
        return new ImageID(id);
    }

    public int compareTo(Object x) {
        if(x == null)
            throw new NullPointerException();
        if(!(x instanceof ImageID))
            throw new ClassCastException("Can't compare ImageID with something not an ImageID");
        ImageID _x = (ImageID)x;
        if(id == _x.id)
            return 0;
        if(id == null)
            return -1;
        if(_x.id == null)
            return 1;
        int minLen = Math.min(id.length, _x.id.length);
        for(int i = 0; i < id.length; i++) {
            if(((int)id[i] & 0xFF) < ((int)(_x.id[i]) & 0xFF))
                return -1;
            if(((int)id[i] & 0xFF) > ((int)(_x.id[i]) & 0xFF))
                return 1;
        }
        if(id.length < _x.id.length)
            return -1;
        if(id.length > _x.id.length)
            return 1;
        return 0;
    }

    public ImageID(SRLoader input) throws IOException
    {
        input.objectCreated(this);
        id = input.loadArrayByte();
    }

    public void dumpSRPartial(SRDumper output) throws IOException
    {
        output.dumpArray(id);
    }

    public void dumpStatusPartial(StatusDumper output)
    {
        //super.dumpStatusPartial(output); <no superclass>
        if(id == null)
            output.println("\tid null");
        else
            output.println("\tid " + getIDAsString());
    }

    //Dump status.
    public void dumpStatus(StatusDumper output)
    {
        if(output.dumped(this))
            return;

        output.println("#" + output.objectNumber(this) + ": ImageID:");
        dumpStatusPartial(output);
        output.endObject();
    }

    public String toString()
    {
        String s = getIDAsString();
        if(s == null)
            s = "null";
        return s;
    }
}
