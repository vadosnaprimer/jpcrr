package exceptiondefs;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;

class ExceptionDefProcessor
{
    private static Map<Class<?>, String> classes;

    static class UTFStream
    {
        FileOutputStream stream;

        UTFStream(String name) throws IOException
        {
            stream = new FileOutputStream(name);
        }

        void println(String str)
        {
            try {
                ByteBuffer buf;
                buf = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(str));
                byte[] buf2 = new byte[buf.remaining() + 1];
                buf.get(buf2, 0, buf.remaining());
                buf2[buf2.length - 1] = 10;
                stream.write(buf2);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        void close()
        {
            try {
                stream.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static char identity(char x)
    {
        return x;
    }

    private static String getRevision() throws IOException
    {
        String x = "$Format:%h by %cn on %ci$";
        if(identity(x.charAt(0)) != '$') {
            System.err.println("Detected revision: " + x + ".");
            return x;
        }
        ProcessBuilder gitproc = new ProcessBuilder();
        gitproc.command("git", "log", "--pretty=format:%h by %cn on %ci", "-1");
        Process git = gitproc.start();
        InputStream output = git.getInputStream();
        while(true) {
            try {
                if(git.waitFor() != 0)
                    throw new IOException("Git subprocess failed");
                break;
            } catch(InterruptedException e) {
            }
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(output));
        x = r.readLine();
        r.close();

       System.err.println("Detected revision: " + x + ".");
       return x;
    }

    private static String escapeString(String s)
    {
        StringBuffer r = new StringBuffer();;
        for(int i = 0; i < s.length(); i++) {
            char x = s.charAt(i);
            if(x == '\"')
                r.append("\"");
            else if(x == '\\')
                r.append("\\");
            else
                r.append(x);
        }
        return r.toString();
    }

    private static void doClass(String line)
    {
        int split = line.indexOf(32);
        String clazz = line.substring(0, split);
        String desc = line.substring(split + 1);
        Class<?> classObject;

        try {
            classObject = Class.forName(clazz);
        } catch(Exception e) {
            System.err.println("Warning: Can't find class \"" + clazz + "\", dropping.");
            return;
        }
        classes.put(classObject, desc);
    }

    public static void main(String[] args)
    {
        classes = new HashMap<Class<?>, String>();

        if(args == null || args.length < 1) {
            System.err.println("Syntax: java ExceptionDefProcessor <inputfile>");
            return;
        }

        String autoexec = args[0];
        try {
            BufferedReader kbd2 = new BufferedReader(new InputStreamReader(
                new FileInputStream(autoexec), "UTF-8"));
            while(true) {
                String cmd = kbd2.readLine();
                if(cmd == null)
                    break;
                if(!cmd.equals(""))
                    doClass(cmd);
            }
        } catch (Exception e) {
            System.err.println("Failed to load exception defintions: " + e.getMessage());
        }

        Class<?> failingClass = null;
        do {
            if(failingClass != null)
                classes.put(failingClass, failingClass.getName());
            failingClass = null;
            for(Map.Entry<Class<?>, String> x : classes.entrySet()) {
                Class<?> superclass = x.getKey().getSuperclass();
                if(x.getKey().getName().equals("java.lang.Error") ||
                    x.getKey().getName().equals("java.lang.RuntimeException"))
                    continue;
                if(!classes.containsKey(superclass)) {
                    System.err.println("Warning: Missing superclass \"" + superclass.getName() + "\" for \"" +
                        x.getKey().getName() + "\".");
                    failingClass = superclass;
                    break;
                }
            }
        } while(failingClass != null);

        UTFStream stream = null;
        try {
            stream = new UTFStream("org/jpc/Exceptions.java");
        } catch(Exception e) {
            System.err.println("Can't open org/jpc/Exceptions.java: " + e.getMessage());
            return;
        }

        stream.println("package org.jpc;");
        stream.println("import java.util.*;");
        stream.println("class Exceptions {");
        stream.println("public static Map<String,String> classes;");
        stream.println("static {");
        stream.println("classes = new HashMap<String,String>();");
        Class<?> out = null;
        String desc = null;
        do {
            if(out != null) {
                classes.remove(out);
                stream.println("classes.put(\"" + out.getName() + "\", \"" + desc + "\");");
            }
            out = null;
            for(Map.Entry<Class<?>, String> x : classes.entrySet()) {
                Class<?> superclass = x.getKey().getSuperclass();
                if(!classes.containsKey(superclass)) {
                    out = x.getKey();
                    desc = x.getValue();
                    break;
                }
            }
        } while(out != null);
        stream.println("}}");
        stream.close();

        try {
            stream = new UTFStream("org/jpc/Revision.java");
        } catch(Exception e) {
            System.err.println("Can't open org/jpc/Revision.java: " + e.getMessage());
            return;
        }
        stream.println("package org.jpc;");
        stream.println("public class Revision {");
        stream.println("public static String getRevision() {");
        try {
	    stream.println("return \"" + escapeString(getRevision()) + "\";");
        } catch(Exception e) {
            System.err.println("Can't get revision: " + e.getMessage());
            return;
        }
        stream.println("}}");
        stream.close();

    }
}
