/*  $Header: //info.ravenbrook.com/project/jili/version/1.1/code/mnj/lua/StringLib.java#1 $
 * Copyright (c) 2006 Nokia Corporation and/or its subsidiary(-ies).
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

// Modified 2009-12-26 by Ilari Liusvaara
// Split -> StringLib -> StringLib, MatchState and FormatItem as J2SE
// doesn't like multiple classes in the same file.

package mnj.lua;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Contains Lua's string library.
 * The library can be opened using the {@link #open} method.
 */
public final class StringLib extends LuaJavaCallback
{
  // Each function in the string library corresponds to an instance of
  // this class which is associated (the 'which' member) with an integer
  // which is unique within this class.  They are taken from the following
  // set.
  private static final int BYTE = 1;
  private static final int CHAR = 2;
  private static final int DUMP = 3;
  private static final int FIND = 4;
  private static final int FORMAT = 5;
  private static final int GFIND = 6;
  private static final int GMATCH = 7;
  private static final int GSUB = 8;
  private static final int LEN = 9;
  private static final int LOWER = 10;
  private static final int MATCH = 11;
  private static final int REP = 12;
  private static final int REVERSE = 13;
  private static final int SUB = 14;
  private static final int UPPER = 15;

  private static final int GMATCH_AUX= 16;

  private static final StringLib GMATCH_AUX_FUN = new StringLib(GMATCH_AUX);

  /**
   * Which library function this object represents.  This value should
   * be one of the "enums" defined in the class.
   */
  private int which;

  /** Constructs instance, filling in the 'which' member. */
  private StringLib(int which)
  {
    this.which = which;
  }

  /**
   * Adjusts the output of string.format so that %e and %g use 'e'
   * instead of 'E' to indicate the exponent.  In other words so that
   * string.format follows the ISO C (ISO 9899) standard for printf.
   */
  public void formatISO()
  {
    FormatItem.E_LOWER = 'e';
  }

  /**
   * Implements all of the functions in the Lua string library.  Do not
   * call directly.
   * @param L  the Lua state in which to execute.
   * @return number of returned parameters, as per convention.
   */
  public int luaFunction(Lua L)
  {
    switch (which)
    {
      case BYTE:
        return byteFunction(L);
      case CHAR:
        return charFunction(L);
      case DUMP:
        return dump(L);
      case FIND:
        return find(L);
      case FORMAT:
        return format(L);
      case GMATCH:
        return gmatch(L);
      case GSUB:
        return gsub(L);
      case LEN:
        return len(L);
      case LOWER:
        return lower(L);
      case MATCH:
        return match(L);
      case REP:
        return rep(L);
      case REVERSE:
        return reverse(L);
      case SUB:
        return sub(L);
      case UPPER:
        return upper(L);
      case GMATCH_AUX:
        return gmatchaux(L);
    }
    return 0;
  }

  /**
   * Opens the string library into the given Lua state.  This registers
   * the symbols of the string library in a newly created table called
   * "string".
   * @param L  The Lua state into which to open.
   */
  public static void open(Lua L)
  {
    Object lib = L.register("string");

    r(L, "byte", BYTE);
    r(L, "char", CHAR);
    r(L, "dump", DUMP);
    r(L, "find", FIND);
    r(L, "format", FORMAT);
    r(L, "gfind", GFIND);
    r(L, "gmatch", GMATCH);
    r(L, "gsub", GSUB);
    r(L, "len", LEN);
    r(L, "lower", LOWER);
    r(L, "match", MATCH);
    r(L, "rep", REP);
    r(L, "reverse", REVERSE);
    r(L, "sub", SUB);
    r(L, "upper", UPPER);

    LuaTable mt = new LuaTable();
    L.setMetatable("", mt);     // set string metatable
    L.setField(mt, "__index", lib);
  }

  /** Register a function. */
  private static void r(Lua L, String name, int which)
  {
    StringLib f = new StringLib(which);
    Object lib = L.getGlobal("string");
    L.setField(lib, name, f);
  }

  /** Implements string.byte.  Name mangled to avoid keyword. */
  private static int byteFunction(Lua L)
  {
    String s = L.checkString(1);
    int posi = posrelat(L.optInt(2, 1), s);
    int pose = posrelat(L.optInt(3, posi), s);
    if (posi <= 0)
    {
      posi = 1;
    }
    if (pose > s.length())
    {
      pose = s.length();
    }
    if (posi > pose)
    {
      return 0; // empty interval; return no values
    }
    int n = pose - posi + 1;
    for (int i=0; i<n; ++i)
    {
      L.pushNumber(s.charAt(posi+i-1));
    }
    return n;
  }

  /** Implements string.char.  Name mangled to avoid keyword. */
  private static int charFunction(Lua L)
  {
    int n = L.getTop(); // number of arguments
    StringBuffer b = new StringBuffer();
    for (int i=1; i<=n; ++i)
    {
      int c = L.checkInt(i);
      L.argCheck((char)c == c, i, "invalid value");
      b.append((char)c);
    }
    L.push(b.toString());
    return 1;
  }

  /** Implements string.dump. */
  private static int dump(Lua L)
  {
    L.checkType(1, Lua.TFUNCTION);
    L.setTop(1);
    try
    {
      ByteArrayOutputStream s = new ByteArrayOutputStream();
      Lua.dump(L.value(1), s);
      byte[] a = s.toByteArray();
      s = null;
      StringBuffer b = new StringBuffer();
      for (int i=0; i<a.length; ++i)
      {
        b.append((char)(a[i]&0xff));
      }
      L.pushString(b.toString());
      return 1;
    }
    catch (IOException e_)
    {
      L.error("unabe to dump given function");
    }
    // NOTREACHED
    return 0;
  }

  /** Helper for find and match.  Equivalent to str_find_aux. */
  private static int findAux(Lua L, boolean isFind)
  {
    String s = L.checkString(1);
    String p = L.checkString(2);
    int l1 = s.length();
    int l2 = p.length();
    int init = posrelat(L.optInt(3, 1), s) - 1;
    if (init < 0)
    {
      init = 0;
    }
    else if (init > l1)
    {
      init = l1;
    }
    if (isFind && (L.toBoolean(L.value(4)) ||   // explicit request
        strpbrk(p, MatchState.SPECIALS) < 0)) // or no special characters?
    {   // do a plain search
      int off = lmemfind(s.substring(init), l1-init, p, l2);
      if (off >= 0)
      {
        L.pushNumber(init+off+1);
        L.pushNumber(init+off+l2);
        return 2;
      }
    }
    else
    {
      MatchState ms = new MatchState(L, s, l1);
      boolean anchor = p.charAt(0) == '^';
      int si = init;
      do
      {
        ms.level = 0;
        int res = ms.match(si, p, anchor ? 1 : 0);
        if (res >= 0)
        {
          if (isFind)
          {
            L.pushNumber(si + 1);       // start
            L.pushNumber(res);          // end
            return ms.push_captures(-1, -1) + 2;
          }     // else
          return ms.push_captures(si, res);
        }
      } while (si++ < ms.end && !anchor);
    }
    L.pushNil();        // not found
    return 1;
  }

  /** Implements string.find. */
  private static int find(Lua L)
  {
    return findAux(L, true);
  }

  /** Implement string.match.  Operates slightly differently from the
   * PUC-Rio code because instead of storing the iteration state as
   * upvalues of the C closure the iteration state is stored in an
   * Object[3] and kept on the stack.
   */
  private static int gmatch(Lua L)
  {
    Object[] state = new Object[3];
    state[0] = L.checkString(1);
    state[1] = L.checkString(2);
    state[2] = new Integer(0);
    L.push(GMATCH_AUX_FUN);
    L.push(state);
    return 2;
  }

  /**
   * Expects the iteration state, an Object[3] (see {@link
   * #gmatch}), to be first on the stack.
   */
  private static int gmatchaux(Lua L)
  {
    Object[] state = (Object[])L.value(1);
    String s = (String)state[0];
    String p = (String)state[1];
    int i = ((Integer)state[2]).intValue();
    MatchState ms = new MatchState(L, s, s.length());
    for ( ; i <= ms.end ; ++i)
    {
      ms.level = 0;
      int e = ms.match(i, p, 0);
      if (e >= 0)
      {
        int newstart = e;
        if (e == i)     // empty match?
          ++newstart;   // go at least one position
        state[2] = new Integer(newstart);
        return ms.push_captures(i, e);
      }
    }
    return 0;   // not found.
  }

  /** Implements string.gsub. */
  private static int gsub(Lua L)
  {
    String s = L.checkString(1);
    int sl = s.length();
    String p = L.checkString(2);
    int maxn = L.optInt(4, sl+1);
    boolean anchor = false;
    if (p.length() > 0)
    {
      anchor = p.charAt(0) == '^';
    }
    if (anchor)
      p = p.substring(1);
    MatchState ms = new MatchState(L, s, sl);
    StringBuffer b = new StringBuffer();

    int n = 0;
    int si = 0;
    while (n < maxn)
    {
      ms.level = 0;
      int e = ms.match(si, p, 0);
      if (e >= 0)
      {
        ++n;
        ms.addvalue(b, si, e);
      }
      if (e >= 0 && e > si)     // non empty match?
        si = e; // skip it
      else if (si < ms.end)
        b.append(s.charAt(si++));
      else
        break;
      if (anchor)
        break;
    }
    b.append(s.substring(si));
    L.pushString(b.toString());
    L.pushNumber(n);    // number of substitutions
    return 2;
  }

  static void addquoted(Lua L, StringBuffer b, int arg)
  {
    String s = L.checkString(arg);
    int l = s.length();
    b.append('"');
    for (int i=0; i<l; ++i)
    {
      switch (s.charAt(i))
      {
        case '"': case '\\': case '\n':
          b.append('\\');
          b.append(s.charAt(i));
          break;

        case '\r':
          b.append("\\r");
          break;

        case '\0':
          b.append("\\000");
          break;

        default:
          b.append(s.charAt(i));
          break;
      }
    }
    b.append('"');
  }

  static int format(Lua L)
  {
    int arg = 1;
    String strfrmt = L.checkString(1);
    int sfl = strfrmt.length();
    StringBuffer b = new StringBuffer();
    int i=0;
    while (i < sfl)
    {
      if (strfrmt.charAt(i) != MatchState.L_ESC)
      {
        b.append(strfrmt.charAt(i++));
      }
      else if (strfrmt.charAt(++i) == MatchState.L_ESC)
      {
        b.append(strfrmt.charAt(i++));
      }
      else      // format item
      {
        ++arg;
        FormatItem item = new FormatItem(L, strfrmt.substring(i));
        i += item.length();
        switch (item.type())
        {
          case 'c':
            item.formatChar(b, (char)L.checkNumber(arg));
            break;

          case 'd': case 'i':
          case 'o': case 'u': case 'x': case 'X':
          // :todo: should be unsigned conversions cope better with
          // negative number?
            item.formatInteger(b, (long)L.checkNumber(arg));
            break;

          case 'e': case 'E': case 'f':
          case 'g': case 'G':
            item.formatFloat(b, L.checkNumber(arg));
            break;

          case 'q':
            addquoted(L, b, arg);
            break;

          case 's':
            item.formatString(b, L.checkString(arg));
            break;

          default:
            return L.error("invalid option to 'format'");
        }
      }
    }
    L.pushString(b.toString());
    return 1;
  }

  /** Implements string.len. */
  private static int len(Lua L)
  {
    String s = L.checkString(1);
    L.pushNumber(s.length());
    return 1;
  }

  /** Implements string.lower. */
  private static int lower(Lua L)
  {
    String s = L.checkString(1);
    L.push(s.toLowerCase());
    return 1;
  }

  /** Implements string.match. */
  private static int match(Lua L)
  {
    return findAux(L, false);
  }

  /** Implements string.rep. */
  private static int rep(Lua L)
  {
    String s = L.checkString(1);
    int n = L.checkInt(2);
    StringBuffer b = new StringBuffer();
    for (int i=0; i<n; ++i)
    {
      b.append(s);
    }
    L.push(b.toString());
    return 1;
  }

  /** Implements string.reverse. */
  private static int reverse(Lua L)
  {
    String s = L.checkString(1);
    StringBuffer b = new StringBuffer();
    int l = s.length();
    while (--l >= 0)
    {
      b.append(s.charAt(l));
    }
    L.push(b.toString());
    return 1;
  }

  /** Helper for {@link #sub} and friends. */
  private static int posrelat(int pos, String s)
  {
    if (pos >= 0)
    {
      return pos;
    }
    int len = s.length();
    return len+pos+1;
  }

  /** Implements string.sub. */
  private static int sub(Lua L)
  {
    String s = L.checkString(1);
    int start = posrelat(L.checkInt(2), s);
    int end = posrelat(L.optInt(3, -1), s);
    if (start < 1)
    {
      start = 1;
    }
    if (end > s.length())
    {
      end = s.length();
    }
    if (start <= end)
    {
      L.push(s.substring(start-1, end));
    }
    else
    {
      L.pushLiteral("");
    }
    return 1;
  }

  /** Implements string.upper. */
  private static int upper(Lua L)
  {
    String s = L.checkString(1);
    L.push(s.toUpperCase());
    return 1;
  }

  /**
   * @return  character index of start of match (-1 if no match).
   */
  private static int lmemfind(String s1, int l1, String s2, int l2)
  {
    if (l2 == 0)
    {
      return 0; // empty strings are everywhere
    }
    else if (l2 > l1)
    {
      return -1;        // avoids a negative l1
    }
    return s1.indexOf(s2);
  }

  /**
   * Just like C's strpbrk.
   * @return an index into <var>s</var> or -1 for no match.
   */
  private static int strpbrk(String s, String set)
  {
    int l = set.length();
    for (int i=0; i<l; ++i)
    {
      int idx = s.indexOf(set.charAt(i));
      if (idx >= 0)
        return idx;
    }
    return -1;
  }
}
