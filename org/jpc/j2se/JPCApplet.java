/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 
    Details (including contact information) can be found at: 

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.j2se;

import java.util.*;
import java.util.jar.*;
import java.net.*;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

import org.jpc.support.*;
import org.jpc.emulator.*;
import org.jpc.emulator.memory.*;
import org.jpc.emulator.processor.*;

public class JPCApplet extends JApplet
{
    private static JPCApplet inUse = null;
    private static byte[] hdaImage;
    private static String hdaName;
    private static boolean forceImageRefresh;

    private static String titleText = "JPC released under GPL2 (www-jpc.physics.ox.ac.uk)";

    private PC pc;
    private MonitorPanel monitorPanel;
    private DownloadPanel downloadPanel;


    static 
    {
        hdaImage = null;
        hdaName = null;
        forceImageRefresh = true;
    }

    public JPCApplet()
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Throwable t) {}
    }
    
    public synchronized void init() 
    {
        if (inUse != null)
        {
            return;
        }
        inUse = this;
    }

    public synchronized void start() 
    {
        if ((inUse != this) && (inUse != null))
        {
            JLabel msg = new JLabel("Another applet is already running!");
            msg.setHorizontalTextPosition(SwingConstants.CENTER);
            getContentPane().add(BorderLayout.CENTER, msg);
            return;
        }
        inUse = this;

        stop();
        
        JPanel pp = new JPanel(new BorderLayout(10, 10));
        Border cb = BorderFactory.createCompoundBorder(BorderFactory.createLoweredBevelBorder(), BorderFactory.createLineBorder(new Color(0xFFA6CAF0), 15));
        Border tb = BorderFactory.createTitledBorder(cb, titleText, TitledBorder.CENTER, TitledBorder.BOTTOM);
        pp.setBorder(tb);

        // getAppletContext().showDocument(new URL("http://www-jpc.physics.ox.ac.uk"), "_self");

        getContentPane().add("Center", pp);
        
        JPCBuilder jb = new JPCBuilder(pp);
        jb.build();
    }

    public synchronized void stop() 
    {
        if (inUse != this)
            return;

        if (monitorPanel != null)
            monitorPanel.stop();

        if (pc != null)
            pc.dispose();

        monitorPanel = null;
        pc = null;

        System.gc();
    }

    public synchronized void destroy() 
    {
        if (inUse != this)
            return;

        stop();

        inUse = null;
    }
    
   
    private String[] buildArgs(String hda)
    {
        String[] args;

        if (hda.contains("linux"))
            args = new String[0];
        else
            args = new String[] { "-fda", "mem:floppy.img", "-boot", "fda"};

        return args;
    }

    private File getDiskImageFile(String img) throws Exception
    {
        String tmpDir = System.getProperty("java.io.tmpdir");
        File tempFile = new File(tmpDir, "JPC_" + img.replace(".img", ".tmp"));
        tempFile.deleteOnExit();

        if ((tempFile.createNewFile()) || (forceImageRefresh))
        {
            String jar = img.replace(".img", ".jar");
            
            URL jarUrl = new URL("jar:" + getCodeBase().toExternalForm() + jar + "!/");
            JarURLConnection jarConn = (JarURLConnection) jarUrl.openConnection(); 
            int length = jarConn.getContentLength();
            JarFile jarFile = jarConn.getJarFile();
            CounterStream in = new CounterStream(jarFile.getInputStream(jarFile.getEntry(img)));

           
            FileOutputStream out = new FileOutputStream(tempFile);
            
            byte[] buffer = new byte[4*1024];
            while (true)
            {
                int read = in.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);

                if (downloadPanel != null)
                    downloadPanel.updateValues(in.getReadCount(), length);
            }
            in.close();
            out.close();

            JPCApplet.forceImageRefresh = false;
        }

        return tempFile;
    }
        
    private byte[] getDiskImageBlob(String img) throws Exception
    {
        if ((!(img.equals(hdaName))) || (forceImageRefresh))
        {
            String jar = img.replace(".img", ".jar");
            URL jarUrl = new URL("jar:" + getCodeBase().toExternalForm() + jar + "!/");
            JarURLConnection jarConn = (JarURLConnection) jarUrl.openConnection(); 
            int length = jarConn.getContentLength();
            JarFile jarFile = jarConn.getJarFile();
            CounterStream in = new CounterStream(jarFile.getInputStream(jarFile.getEntry(img)));
            
            ByteArrayOutputStream out = new ByteArrayOutputStream(8*1024*1024);
            
            byte[] buffer = new byte[4*1024];
            while (true)
            {
                int read = in.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);
                
                if (downloadPanel != null)
                    downloadPanel.updateValues(in.getReadCount(), length);
            }
            in.close();
            JPCApplet.forceImageRefresh = false;

            return out.toByteArray();
        }
        return hdaImage;
    }
        
    private String[] buildArgsFromJar()
    {
        String[] args = new String[] { "-fda", "mem:floppy.img", "-boot", "fda" , "", "" };
        
        String[] imageFiles = getFilesFromJar("img");
        for (int i = 0; i < imageFiles.length; i++)
            if (!(imageFiles[i].equals("floppy.img")))
            {
                if (imageFiles[i].equals("linux.img"))
                {
                    args = new String[] { "-hda", "mem:linux.img"};
                    break;
                }
                args[4] = "-hda";
                args[5] = "mem:" + imageFiles[i];
                break;
            }

        return args;
    }

    private String[] getFilesFromJar(String extension)
    {
        Vector files = new Vector();

        try
        {
 	    ClassLoader cl = JPCApplet.class.getClassLoader();

            if (cl instanceof URLClassLoader) 
            {
                URLClassLoader ucl = (URLClassLoader) cl;
                URL[] urls = ucl.getURLs();
                for (int i = 0; i < urls.length; i++) 
                {
                    URL url = urls[i];
                    if ((url.getFile().endsWith(".jar")) && (url.toString().startsWith(getCodeBase().toString())))
                    {
                        System.out.println("Checking " + url + " for ." + extension + " files:");
                        URL jarUrl = new URL("jar:" + url.toExternalForm() + "!/");
                        JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection(); 
                        Enumeration e = jarConnection.getJarFile().entries();
                        while (e.hasMoreElements())
                        { 
                            String s = ((JarEntry) e.nextElement()).toString();
                            if (s.endsWith("." + extension))
                            {
                                System.out.println(s);
                                files.addElement(s);
                            }
                        }
                    }
                }
            }
            else
            {
                System.out.print("cl NOT instanceof URLClassLoader: ");
                System.out.println("Not able to find ." + extension + " file");
            }
        }
        catch (Exception e) 
        {
            System.out.println(e);
        }

        return (String[]) files.toArray(new String[files.size()]);
     }

    class CounterStream extends FilterInputStream
    {
        int count;
        
        CounterStream(InputStream src)
        {
            super(src);
        }

        public int getReadCount()
        {
            return count;
        }
        
        public int read() throws IOException
        {
            int r = super.read();
            if (r > 0)
                count += r;
            return r;
        }

        public int read(byte[] b)  throws IOException
        {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            int r = super.read(b, off, len);
            if (r > 0)
                count += r;
            return r;
        }
    }

    class DownloadPanel extends JPanel implements Runnable
    {
        private boolean running;
        private Thread runner;
        JProgressBar progress;
        int bytesRead;
        int totalBytes;

        DownloadPanel()
        {
            super(new BorderLayout(10, 10));

            progress = new JProgressBar();
            progress.setStringPainted(true);
            progress.setString("Downloading Disk Image...");
 
            bytesRead = 0;
            totalBytes = 1;
            
            URL url = JPCApplet.class.getClassLoader().getResource("JPCLogo.png");
            if (url != null)
            {
                JLabel logo = new JLabel(new ImageIcon(url));
                add("Center", logo);
            }
            add("South", progress);
        }
        
        public void run()
        {
            if (totalBytes > 0)
            {
                progress.setIndeterminate(false);
                progress.setValue(100 * bytesRead / totalBytes);
            }
            else
            {
                progress.setIndeterminate(true);
                progress.setString("Downloaded "+ bytesRead +" bytes");
            }
        }

        void updateValues(int read, int total)
        {
            bytesRead = read;
            totalBytes = total;
            SwingUtilities.invokeLater(this);
        }

        void showMessage(String message)
        {
            progress.setString(message);
            repaint();
        }
    }

    class MonitorPanel extends PCMonitor implements Runnable
    {
        private boolean running;
        private Thread runner;
 
        MonitorPanel(PC pc)
        {
            super(new BorderLayout(10, 10), pc);
        }

        protected synchronized void stop()
        {
            running = false;
            try
            {
                runner.join(5000);
            }
            catch (Throwable t) {}

            try
            {
                runner.stop();
            }
            catch (Throwable t) {}
            runner = null;
            
            stopUpdateThread();
        }

        protected synchronized void start()
        {
            if (running)
                return;

            int p = Math.max(Thread.currentThread().getThreadGroup().getMaxPriority()-4, Thread.MIN_PRIORITY+1);

            startUpdateThread(p);

            running = true;
            runner = new Thread(this, "PC Execute Task");
            runner.setPriority(p);
            runner.start();
        }

        public void run()
        {
            pc.start();
            try
            {
                while (running) 
                    pc.execute();
            }
            catch (Exception e)
            {
                System.err.println("Caught exception @ Address:0x" + Integer.toHexString(pc.getProcessor().getInstructionPointer()));
                System.err.println(e);
                e.printStackTrace();
            }
            finally
            {
                pc.stop();
            }
        }

    }


    class JPCBuilder implements Runnable
    {
        private Container contentPane;

        JPCBuilder(Container contentPane)
        {
            this.contentPane = contentPane;
        }

        public void build()
        {
            Thread runner = new Thread(this, "JPC Builder Task");
            int p = Math.min(Thread.currentThread().getThreadGroup().getMaxPriority(), Thread.MAX_PRIORITY-2);
            runner.setPriority(p);
            runner.start();
        }

        public void run()
        {
            downloadPanel = new DownloadPanel();
            contentPane.add("Center", downloadPanel);
            downloadPanel.validate();
            
            String[] args = buildArgs(getParameter("hda"));
            
            try
            {
                pc = PC.createPC(args, new VirtualClock()); 
            }
            catch (Exception e) 
            {
                System.out.println(e);
            }
            
            RawBlockDevice hda = null;
            try
            {
                File img = getDiskImageFile(getParameter("hda"));
                hda = new RawBlockDevice(new FileBackedSeekableIODevice(img.getAbsolutePath()));
            }
            catch (SecurityException e) 
            {
                try
                {
                    String hdaParam = getParameter("hda");
                    hdaImage = getDiskImageBlob(hdaParam);
                    hdaName = hdaParam;
                    hda = new RawBlockDevice(new ArrayBackedSeekableIODevice(hdaName, hdaImage));
                }
                catch (Exception f) 
                {
                    System.out.println("Failed getting disk image from blob: " + f);
                }
            }
            catch (Exception e) 
            {
                System.out.println("Failed getting disk image from file: " + e);
            }
            
            pc.getDrives().setHardDrive(0, hda);
            pc.reset();
            
            monitorPanel = new MonitorPanel(pc);
            
            contentPane.remove(downloadPanel);
            contentPane.add("Center", monitorPanel);
            contentPane.add("South", new KeyTypingPanel(monitorPanel));
            
            monitorPanel.validate();
            monitorPanel.setVisible(true);
            monitorPanel.start();
        }

    }

}
