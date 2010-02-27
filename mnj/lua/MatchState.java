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
import java.util.Vector;

final class MatchState
{
  Lua L;
  /** The entire string that is the subject of the match. */
  String src;
  /** The subject's length. */
  int end;
  /** Total number of captures (finished or unfinished). */
  int level;
  /** Each capture element is a 2-element array of (index, len). */
  Vector<Object> capture = new Vector<Object>();
  // :todo: consider adding the pattern string as a member (and removing
  // p parameter from methods).

  // :todo: consider removing end parameter, if end always == // src.length()
  MatchState(Lua L, String src, int end)
  {
    this.L = L;
    this.src = src;
    this.end = end;
  }

  /**
   * Returns the length of capture <var>i</var>.
   */
  private int captureLen(int i)
  {
    int[] c = (int[])capture.elementAt(i);
    return c[1];
  }

  /**
   * Returns the init index of capture <var>i</var>.
   */
  private int captureInit(int i)
  {
    int[] c = (int[])capture.elementAt(i);
    return c[0];
  }

  /**
   * Returns the 2-element array for the capture <var>i</var>.
   */
  private int[] capture(int i)
  {
    return (int[])capture.elementAt(i);
  }

  int capInvalid()
  {
    return L.error("invalid capture index");
  }

  int malBra()
  {
    return L.error("malformed pattern (missing '[')");
  }

  int capUnfinished()
  {
    return L.error("unfinished capture");
  }

  int malEsc()
  {
    return L.error("malformed pattern (ends with '%')");
  }

  char check_capture(char l)
  {
    l -= '1';   // relies on wraparound.
    if (l >= level || captureLen(l) == CAP_UNFINISHED)
      capInvalid();
    return l;
  }

  int capture_to_close()
  {
    int lev = level;
    for (lev--; lev>=0; lev--)
      if (captureLen(lev) == CAP_UNFINISHED)
        return lev;
    return capInvalid();
  }

  int classend(String p, int pi)
  {
    switch (p.charAt(pi++))
    {
      case L_ESC:
        // assert pi < p.length() // checked by callers
        return pi+1;

      case '[':
        if (p.length() == pi)
          return malBra();
        if (p.charAt(pi) == '^')
          ++pi;
        do    // look for a ']'
        {
          if (p.length() == pi)
            return malBra();
          if (p.charAt(pi++) == L_ESC)
          {
            if (p.length() == pi)
              return malBra();
            ++pi;     // skip escapes (e.g. '%]')
            if (p.length() == pi)
              return malBra();
          }
        } while (p.charAt(pi) != ']');
        return pi+1;

      default:
        return pi;
    }
  }

  /**
   * @param c   char match.
   * @param cl  character class.
   */
  static boolean match_class(char c, char cl)
  {
    boolean res;
    switch (Character.toLowerCase(cl))
    {
      case 'a' : res = Syntax.isalpha(c); break;
      case 'c' : res = Syntax.iscntrl(c); break;
      case 'd' : res = Syntax.isdigit(c); break;
      case 'l' : res = Syntax.islower(c); break;
      case 'p' : res = Syntax.ispunct(c); break;
      case 's' : res = Syntax.isspace(c); break;
      case 'u' : res = Syntax.isupper(c); break;
      case 'w' : res = Syntax.isalnum(c); break;
      case 'x' : res = Syntax.isxdigit(c); break;
      case 'z' : res = (c == 0); break;
      default: return (cl == c);
    }
    return Character.isLowerCase(cl) ? res : !res;
  }

  /**
   * @param pi  index in p of start of class.
   * @param ec  index in p of end of class.
   */
  static boolean matchbracketclass(char c, String p, int pi, int ec)
  {
    // :todo: consider changing char c to int c, then -1 could be used
    // represent a guard value at the beginning and end of all strings (a
    // better NUL).  -1 of course would match no positive class.

    // assert p.charAt(pi) == '[';
    // assert p.charAt(ec) == ']';
    boolean sig = true;
    if (p.charAt(pi+1) == '^')
    {
      sig = false;
      ++pi;     // skip the '6'
    }
    while (++pi < ec)
    {
      if (p.charAt(pi) == L_ESC)
      {
        ++pi;
        if (match_class(c, p.charAt(pi)))
          return sig;
      }
      else if ((p.charAt(pi+1) == '-') && (pi+2 < ec))
      {
        pi += 2;
        if (p.charAt(pi-2) <= c && c <= p.charAt(pi))
          return sig;
      }
      else if (p.charAt(pi) == c)
      {
        return sig;
      }
    }
    return !sig;
  }

  static boolean singlematch(char c, String p, int pi, int ep)
  {
    switch (p.charAt(pi))
    {
      case '.': return true;    // matches any char
      case L_ESC: return match_class(c, p.charAt(pi+1));
      case '[': return matchbracketclass(c, p, pi, ep-1);
      default: return p.charAt(pi) == c;
    }
  }

  // Generally all the various match functions from PUC-Rio which take a
  // MatchState and return a "const char *" are transformed into
  // instance methods that take and return string indexes.

  int matchbalance(int si, String p, int pi)
  {
    if (pi+1 >= p.length())
      L.error("unbalanced pattern");
    if (si >= end || src.charAt(si) != p.charAt(pi))
    {
      return -1;
    }
    char b = p.charAt(pi);
    char e = p.charAt(pi+1);
    int cont = 1;
    while (++si < end)
    {
      if (src.charAt(si) == e)
      {
        if (--cont == 0)
          return si+1;
      }
      else if (src.charAt(si) == b)
      {
        ++cont;
      }
    }
    return -1;  // string ends out of balance
  }

  int max_expand(int si, String p, int pi, int ep)
  {
    int i = 0;  // counts maximum expand for item
    while (si+i < end && singlematch(src.charAt(si+i), p, pi, ep))
    {
      ++i;
    }
    // keeps trying to match with the maximum repetitions
    while (i >= 0)
    {
      int res = match(si+i, p, ep+1);
      if (res >= 0)
        return res;
      --i;      // else didn't match; reduce 1 repetition to try again
    }
    return -1;
  }

  int min_expand(int si, String p, int pi, int ep)
  {
    while (true)
    {
      int res = match(si, p, ep+1);
      if (res >= 0)
        return res;
      else if (si < end && singlematch(src.charAt(si), p, pi, ep))
        ++si;   // try with one more repetition
      else
        return -1;
    }
  }

  int start_capture(int si, String p, int pi, int what)
  {
    capture.setSize(level + 1);
    capture.setElementAt(new int[] { si, what }, level);
    ++level;
    int res = match(si, p, pi);
    if (res < 0)        // match failed
    {
      --level;
    }
    return res;
  }

  int end_capture(int si, String p, int pi)
  {
    int l = capture_to_close();
    capture(l)[1] = si - captureInit(l);        // close it
    int res = match(si, p, pi);
    if (res < 0)        // match failed?
    {
      capture(l)[1] = CAP_UNFINISHED;   // undo capture
    }
    return res;
  }

  int match_capture(int si, char l)
  {
    l = check_capture(l);
    int len = captureLen(l);
    if (end - si >= len &&
        src.regionMatches(false,
            captureInit(l),
            src,
            si,
            len))
    {
      return si+len;
    }
    return -1;
  }

  static final char L_ESC = '%';
  static final String SPECIALS = "^$*+?.([%-";
  private static final int CAP_UNFINISHED = -1;
  private static final int CAP_POSITION = -2;

  /**
   * @param si  index of subject at which to attempt match.
   * @param p   pattern string.
   * @param pi  index into pattern (from which to being matching).
   * @return the index of the end of the match, -1 for no match.
   */
  int match(int si, String p, int pi)
  {
    // This code has been considerably changed in the transformation
    // from C to Java.  There are the following non-obvious changes:
    // - The C code routinely relies on NUL being accessible at the end of
    //   the pattern string.  In Java we can't do this, so we use many
    //   more explicit length checks and pull error cases into this
    //   function.  :todo: consider appending NUL to the pattern string.
    // - The C code uses a "goto dflt" which is difficult to transform in
    //   the usual way.
init:   // labelled while loop emulates "goto init", which we use to
        // optimize tail recursion.
    while (true)
    {
      if (p.length() == pi)     // end of pattern
        return si;              // match succeeded
      switch (p.charAt(pi))
      {
        case '(':
          if (p.length() == pi + 1)
          {
            return capUnfinished();
          }
          if (p.charAt(pi+1) == ')')  // position capture?
            return start_capture(si, p, pi+2, CAP_POSITION);
          return start_capture(si, p, pi+1, CAP_UNFINISHED);

        case ')':       // end capture
          return end_capture(si, p, pi+1);

        case L_ESC:
          if (p.length() == pi + 1)
          {
            return malEsc();
          }
          switch (p.charAt(pi+1))
          {
            case 'b':   // balanced string?
              si = matchbalance(si, p, pi+2);
              if (si < 0)
                return si;
              pi += 4;
              // else return match(ms, s, p+4);
              continue init;    // goto init

            case 'f':   // frontier
              {
                pi += 2;
                if (p.length() == pi || p.charAt(pi) != '[')
                  return L.error("missing '[' after '%f' in pattern");
                int ep = classend(p, pi);   // indexes what is next
                char previous = (si == 0) ? '\0' : src.charAt(si-1);
                char at = (si == end) ? '\0' : src.charAt(si);
                if (matchbracketclass(previous, p, pi, ep-1) ||
                    !matchbracketclass(at, p, pi, ep-1))
                {
                  return -1;
                }
                pi = ep;
                // else return match(ms, s, ep);
              }
              continue init;    // goto init

            default:
              if (Syntax.isdigit(p.charAt(pi+1))) // capture results (%0-%09)?
              {
                si = match_capture(si, p.charAt(pi+1));
                if (si < 0)
                  return si;
                pi += 2;
                // else return match(ms, s, p+2);
                continue init;  // goto init
              }
              // We emulate a goto dflt by a fallthrough to the next
              // case (of the outer switch) and making sure that the
              // next case has no effect when we fallthrough to it from here.
              // goto dflt;
          }
          // FALLTHROUGH
        case '$':
          if (p.charAt(pi) == '$')
          {
            if (p.length() == pi+1)      // is the '$' the last char in pattern?
              return (si == end) ? si : -1;     // check end of string
            // else goto dflt;
          }
          // FALLTHROUGH
        default:        // it is a pattern item
          {
            int ep = classend(p, pi);   // indexes what is next
            boolean m = si < end && singlematch(src.charAt(si), p, pi, ep);
            if (p.length() > ep)
            {
              switch (p.charAt(ep))
              {
                case '?':       // optional
                  if (m)
                  {
                    int res = match(si+1, p, ep+1);
                    if (res >= 0)
                      return res;
                  }
                  pi = ep+1;
                  // else return match(s, ep+1);
                  continue init;      // goto init

                case '*':       // 0 or more repetitions
                  return max_expand(si, p, pi, ep);

                case '+':       // 1 or more repetitions
                  return m ? max_expand(si+1, p, pi, ep) : -1;

                case '-':       // 0 or more repetitions (minimum)
                  return min_expand(si, p, pi, ep);
              }
            }
            // else or default:
            if (!m)
              return -1;
            ++si;
            pi = ep;
            // return match(ms, s+1, ep);
            continue init;
          }
      }
    }
  }

  /**
   * @param s  index of start of match.
   * @param e  index of end of match.
   */
  Object onecapture(int i, int s, int e)
  {
    if (i >= level)
    {
      if (i == 0)       // level == 0, too
         return src.substring(s, e);    // add whole match
      else
        capInvalid();
        // NOTREACHED;
    }
    int l = captureLen(i);
    if (l == CAP_UNFINISHED)
      capUnfinished();
    if (l == CAP_POSITION)
      return L.valueOfNumber(captureInit(i) +1);
    return src.substring(captureInit(i), captureInit(i) + l);
  }

  void push_onecapture(int i, int s, int e)
  {
    L.push(onecapture(i, s, e));
  }

  /**
   * @param s  index of start of match.
   * @param e  index of end of match.
   */
  int push_captures(int s, int e)
  {
    int nlevels = (level == 0 && s >= 0) ? 1 : level;
    for (int i=0; i<nlevels; ++i)
      push_onecapture(i, s, e);
    return nlevels;     // number of strings pushed
  }

  /** A helper for gsub.  Equivalent to add_s from lstrlib.c. */
  void adds(StringBuffer b, int si, int ei)
  {
    String news = L.toString(L.value(3));
    int l = news.length();
    for (int i=0; i<l; ++i)
    {
      if (news.charAt(i) != L_ESC)
      {
        b.append(news.charAt(i));
      }
      else
      {
        ++i;    // skip L_ESC
        if (!Syntax.isdigit(news.charAt(i)))
        {
          b.append(news.charAt(i));
        }
        else if (news.charAt(i) == '0')
        {
          b.append(src.substring(si, ei));
        }
        else
        {
          // add capture to accumulated result
          b.append(L.toString(onecapture(news.charAt(i) - '1', si, ei)));
        }
      }
    }
  }

  /** A helper for gsub.  Equivalent to add_value from lstrlib.c. */
  void addvalue(StringBuffer b, int si, int ei)
  {
    switch (L.type(3))
    {
      case Lua.TNUMBER:
      case Lua.TSTRING:
        adds(b, si, ei);
        return;

      case Lua.TFUNCTION:
        {
          L.pushValue(3);
          int n = push_captures(si, ei);
          L.call(n, 1);
        }
        break;

      case Lua.TTABLE:
        L.push(L.getTable(L.value(3), onecapture(0, si, ei)));
        break;

      default:
      {
        L.argError(3, "string/function/table expected");
        return;
      }
    }
    if (!L.toBoolean(L.value(-1)))      // nil or false
    {
      L.pop(1);
      L.pushString(src.substring(si, ei));
    }
    else if (!L.isString(L.value(-1)))
    {
      L.error("invalid replacement value (a " +
          L.typeName(L.type(-1)) + ")");
    }
    b.append(L.toString(L.value(-1)));  // add result to accumulator
    L.pop(1);
  }
}
