/*
    JPC-RR: A x86 PC Hardware Emulator
    Release 1

    Copyright (C) 2007-2009 Isis Innovation Limited
    Copyright (C) 2009-2010 H. Ilari Liusvaara

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

    Based on JPC x86 PC Hardware emulator,
    A project from the Physics Dept, The University of Oxford

    Details about original JPC can be found at:

    www-jpc.physics.ox.ac.uk

*/

package org.jpc.pluginsaux;

import org.jpc.emulator.PC;
import org.jpc.diskimages.DiskImage;
import org.jpc.emulator.DriveSet;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.callShowOptionDialog;
import org.jpc.pluginsbase.*;
import org.jpc.plugins.RAWVideoDumper;
import org.jpc.plugins.RAWAudioDumper;

import javax.swing.*;
import java.util.*;
import java.io.*;
import java.awt.event.*;
import java.awt.*;

public class DumpControlDialog implements ActionListener, WindowListener, KeyListener
{
    private JFrame window;
    private JPanel cpanel;
    private JPanel dpanel;
    private JPanel wpanel;
    private JTextField videoDestination;
    private JTextField videoSkip;
    private JButton videoStart;
    private JButton videoStop;
    private RAWVideoDumper videoDumpInProgress;
    private Plugins vManager;
    private PC attachedPC;
    private boolean closeable;
    private boolean ready;
    private boolean cleared;

    private Map<String, JLabel> audioSourceLabels;
    private Map<String, JTextField> audioDestinations;
    private Map<String, JTextField> audioSkips;
    private Map<String, JButton> audioStartButtons;
    private Map<String, JButton> audioStopButtons;
    private Map<String, RAWAudioDumper> audioDumpInProgress;

    private boolean shutdownAudioDumper(String source)
    {
        if(!audioDumpInProgress.containsKey(source))
            return true;
        if(audioDumpInProgress.get(source) == null)
            return true;

        return vManager.unregisterPlugin(audioDumpInProgress.get(source));
    }

    private void startAudioSrc(String source)
    {
        String dst = audioDestinations.get(source).getText();
        long skip = getSkip(audioSkips.get(source).getText());
        System.err.println("Source=" + source + ", dst=" + dst + ", skip=" + skip + ".");
        try {
            RAWAudioDumper audioDump;
            if(skip > 0)
                audioDump = new RAWAudioDumper(vManager, "src=" + source + ",file=" + dst + ",offset=" + skip);
            else
                audioDump = new RAWAudioDumper(vManager, "src=" + source + ",file=" + dst);
            vManager.registerPlugin(audioDump);
            audioDumpInProgress.put(source, audioDump);
        } catch(Exception e) {
            errorDialog(e, "Failed to start audio dumper", window, "Dismiss");
            return;
        }

        audioDestinations.get(source).setEnabled(false);
        audioSkips.get(source).setEnabled(false);
        audioStartButtons.get(source).setEnabled(false);
        audioStopButtons.get(source).setEnabled(true);
    }

    private void stopAudioSrc(String source)
    {
        if(!shutdownAudioDumper(source)) {
            callShowOptionDialog(window, "Dumper for " + source + " doesn't want to shut down" , "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
            return;
        }
        audioDumpInProgress.put(source, null);
        audioDestinations.get(source).setEnabled(true);
        audioSkips.get(source).setEnabled(true);
        audioStartButtons.get(source).setEnabled(false);
        audioStopButtons.get(source).setEnabled(false);
        keyTyped(null);
    }

    private void startVideoSrc()
    {
        String dst = videoDestination.getText();
        System.err.println("Source=<VIDEO>, dst=" + dst + ", skip=N/A.");
        try {
            videoDumpInProgress = new RAWVideoDumper(vManager, "rawoutput=" + dst);
            vManager.registerPlugin(videoDumpInProgress);
        } catch(Exception e) {
            errorDialog(e, "Failed to start video dumper", window, "Dismiss");
            videoDumpInProgress = null;
            return;
        }
        videoDestination.setEnabled(false);
        videoStart.setEnabled(false);
        videoStop.setEnabled(true);
    }

    private void stopVideoSrc()
    {
        if(videoDumpInProgress != null && !vManager.unregisterPlugin(videoDumpInProgress)) {
            callShowOptionDialog(window, "Video dumper doesn't want to shut down" , "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
            return;
        }
        videoDumpInProgress = null;
        videoDestination.setEnabled(true);
        videoStart.setEnabled(false);
        videoStop.setEnabled(false);
        keyTyped(null);
    }

    public void clearDumps()
    {
        for(Map.Entry<String, JLabel> x : audioSourceLabels.entrySet()) {
            String name = x.getKey();
            if(!shutdownAudioDumper(name))
                callShowOptionDialog(window, "Dumper for " + name + " doesn't want to shut down" , "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Ignore"}, "Ignore");
            dpanel.remove(audioSourceLabels.get(name));
            dpanel.remove(audioDestinations.get(name));
            dpanel.remove(audioSkips.get(name));
            dpanel.remove(audioStartButtons.get(name));
            dpanel.remove(audioStopButtons.get(name));
        }
        audioSourceLabels.clear();
        audioDestinations.clear();
        audioSkips.clear();
        audioStartButtons.clear();
        audioStopButtons.clear();
        audioDumpInProgress.clear();
        dpanel.validate();
        window.pack();
        cleared = true;
    }

    public DumpControlDialog(Plugins manager) throws Exception
    {
        vManager = manager;
        closeable = false;
        cleared = true;
        window = new JFrame("Dump control");

        GridBagLayout dlayout = new GridBagLayout();
        dpanel = new JPanel(dlayout);

        GridBagLayout clayout = new GridBagLayout();
        cpanel = new JPanel(clayout);

        wpanel = new JPanel();
        wpanel.setLayout(new BoxLayout(wpanel, BoxLayout.Y_AXIS));
        wpanel.add(dpanel);
        wpanel.add(cpanel);
        window.add(wpanel);

        GridBagConstraints c = new GridBagConstraints();

        audioSourceLabels = new HashMap<String, JLabel>();
        audioDestinations =  new HashMap<String, JTextField>();
        audioSkips = new HashMap<String, JTextField>();
        audioStartButtons = new HashMap<String, JButton>();
        audioStopButtons = new HashMap<String, JButton>();
        audioDumpInProgress = new HashMap<String, RAWAudioDumper>();

        //Some padding space.
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        cpanel.add(new JLabel(""), c);

        //Close button.
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton close = new JButton("Close");
        close.setActionCommand("CLOSE");
        close.addActionListener(this);
        cpanel.add(close, c);

        //Field labels.
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(new JLabel("Source"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(new JLabel("Destination"), c);

        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(new JLabel("Skip"), c);

        //Entry for video.
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(new JLabel("<VIDEO>"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(videoDestination = new JTextField("", 40), c);
        videoDestination.addKeyListener(this);

        c.gridx = 2;
        c.gridy = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        dpanel.add(videoSkip = new JTextField("N/A", 15), c);
        videoSkip.setEnabled(false);

        c.gridx = 3;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        dpanel.add(videoStart = new JButton("Start"), c);
        videoStart.setActionCommand("START");
        videoStart.setEnabled(false);
        videoStart.addActionListener(this);

        c.gridx = 4;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        dpanel.add(videoStop = new JButton("Stop"), c);
        videoStop.setActionCommand("STOP");
        videoStop.setEnabled(false);
        videoStop.addActionListener(this);

        window.pack();
        window.addWindowListener(this);
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ready = true;
    }

    public void popUp(PC pc) throws Exception
    {
        window.setVisible(true);
        attachedPC = pc;
        int i = 2;
        GridBagConstraints c = new GridBagConstraints();
        JLabel aSource;
        JTextField aDestination;
        JTextField aSkip;
        JButton aStart;
        JButton aStop;

        if(!cleared)
            return;

        for(String name : pc.getSoundOutputs()) {

            c.gridx = 0;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            dpanel.add(aSource = new JLabel(name), c);

            c.gridx = 1;
            c.gridy = i;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            dpanel.add(aDestination = new JTextField("", 40), c);
            aDestination.addKeyListener(this);

            c.gridx = 2;
            c.gridy = i;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            dpanel.add(aSkip = new JTextField("", 15), c);
            aSkip.addKeyListener(this);

            c.gridx = 3;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            dpanel.add(aStart = new JButton("Start"), c);
            aStart.setActionCommand("START-" + name);
            aStart.setEnabled(false);
            aStart.addActionListener(this);

            c.gridx = 4;
            c.gridy = i;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            dpanel.add(aStop = new JButton("Stop"), c);
            aStop.setActionCommand("STOP-" + name);
            aStop.setEnabled(false);
            aStop.addActionListener(this);

            audioSourceLabels.put(name, aSource);
            audioDestinations.put(name, aDestination);
            audioSkips.put(name, aSkip);
            audioStartButtons.put(name, aStart);
            audioStopButtons.put(name, aStop);

            i++;
        }
        cleared = false;
        dpanel.validate();
        window.pack();

    }

    public synchronized void waitClose()
    {
        if(closeable) {
            closeable = false;
            return;
        }
        while(!closeable) {
            try {
                wait();
            } catch(InterruptedException e) {
            }
        }
        closeable = false;
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        if(command == null)
            return;

        if("CLOSE".equals(command)) {
            window.setVisible(false);
            synchronized(this) {
                closeable = true;
                notifyAll();
            }
        } else if("START".equals(command)) {
            startVideoSrc();
        } else if("STOP".equals(command)) {
            stopVideoSrc();
        } else if(command.startsWith("START-")) {
            startAudioSrc(command.substring(6));
        } else if(command.startsWith("STOP-")) {
            stopAudioSrc(command.substring(5));
        }
    }

    public void windowActivated(WindowEvent e) { /* Not interested. */ }
    public void windowClosed(WindowEvent e) { /* Not interested. */ }
    public void windowDeactivated(WindowEvent e) { /* Not interested. */ }
    public void windowDeiconified(WindowEvent e) { /* Not interested. */ }
    public void windowIconified(WindowEvent e) { /* Not interested. */ }
    public void windowOpened(WindowEvent e) { /* Not interested. */ }

    public void windowClosing(WindowEvent e)
    {
        window.setVisible(false);
        synchronized(this) {
            closeable = true;
            notifyAll();
        }
    }

    public void keyPressed(KeyEvent event)
    {
        keyTyped(event);
    }

    public void keyReleased(KeyEvent event)
    {
        keyTyped(event);
    }

    private boolean isValidSkip(String skip)
    {
        return skip.matches("(|0|[1-9][0-9]*)(.[0-9]{1,9})?");
    }

    private long getSkip(String skip)
    {
        long val = 0;
        int place = 0;
        for(int i = 0; i < skip.length(); i++) {
            char x = skip.charAt(i);
            if(place == 0 && x >= '0' && x <= '9')
                val = val * 10 + (long)(x - '0');
            else if(x == '.') {
                place = 100000000;
                val = val * 1000000000;
            } else if(x >= '0' && x <= '9') {
                val = val + place * (long)(x - '0');
                place /= 10;
            }
        }
        return val;
    }

    public void keyTyped(KeyEvent event)
    {
        if(!ready)
            return;

        if("".equals(videoDestination.getText()))
            videoStart.setEnabled(false);
        else
            videoStart.setEnabled(videoDumpInProgress == null);

        for(Map.Entry<String, JTextField> x : audioDestinations.entrySet()) {
            String name = x.getKey();

            if("".equals(x.getValue().getText()) || !isValidSkip(audioSkips.get(name).getText()))
                audioStartButtons.get(name).setEnabled(false);
            else
                audioStartButtons.get(name).setEnabled(!audioDumpInProgress.containsKey(name) ||
                    audioDumpInProgress.get(name) == null);
        }

    }
}
