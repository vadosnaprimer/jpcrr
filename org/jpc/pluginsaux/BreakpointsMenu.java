package org.jpc.pluginsaux;

import javax.swing.*;
import java.awt.event.*;
import java.awt.FlowLayout;
import org.jpc.bus.*;
import static org.jpc.Misc.castToBoolean;
import static org.jpc.Misc.castToLong;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_VRETRACE_START;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_VRETRACE_END;
import static org.jpc.emulator.TraceTrap.TRACE_STOP_BIOS_KBD;

public class BreakpointsMenu implements ActionListener
{
    private JMenu menu;
    private JCheckBoxMenuItem trapItemVRS;
    private JCheckBoxMenuItem trapItemVRE;
    private JCheckBoxMenuItem trapItemBKI;
    private Bus bus;
    private long duration;
    private boolean customSel;
    private static long[] stopTime;
    private static String[] stopLabel;
    private JCheckBoxMenuItem[] trapItemTimed;
    private JCheckBoxMenuItem trapItemCustom;

    private JFrame customWindow;
    private JTextField customDuration;
    private JButton customOK;
    private JButton customCancel;

    static
    {
        stopTime = new long[] {-1, 0, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000, 2000000,
            5000000, 10000000, 20000000, 50000000, 100000000, 200000000, 500000000, 1000000000, 2000000000,
            5000000000L, 10000000000L, 20000000000L, 50000000000L};
        stopLabel = new String[] {"(unbounded)", "(singlestep)", "1µs", "2µs", "5µs", "10µs", "20µs", "50µs", "100µs",
            "200µs", "500µs","1ms", "2ms", "5ms", "10ms", "20ms", "50ms", "100ms", "200ms", "500ms", "1s", "2s", "5s",
            "10s", "20s", "50s"};
    }

    public BreakpointsMenu(Bus _bus)
    {
        bus = _bus;

        bus.setEventHandler(this, "trapDurationChanged", "trap-duration-changed");
        bus.setEventHandler(this, "trapFlagsChanged", "trap-flags-changed");


        menu = new JMenu("Breakpoints");

        trapItemVRS = new JCheckBoxMenuItem("Break on vertical retrace starting");
        menu.add(trapItemVRS);
        trapItemVRS.addActionListener(this);

        trapItemVRE = new JCheckBoxMenuItem("Break on vertical retrace ending");
        menu.add(trapItemVRE);
        trapItemVRE.addActionListener(this);

        trapItemBKI = new JCheckBoxMenuItem("Break on BIOS keyboard activity");
        menu.add(trapItemBKI);
        trapItemBKI.addActionListener(this);

        JMenu timedMenu = new JMenu("Break on timeout");
        menu.add(timedMenu);
        trapItemTimed = new JCheckBoxMenuItem[stopTime.length];
        for(int i = 0; i < stopTime.length; i++) {
            trapItemTimed[i] = new JCheckBoxMenuItem(stopLabel[i]);
            timedMenu.add(trapItemTimed[i]);
            trapItemTimed[i].addActionListener(this);
        }
        trapItemCustom = new JCheckBoxMenuItem("Custom ()...");
        timedMenu.add(trapItemCustom);
        trapItemCustom.addActionListener(this);

        bus.executeCommandNoFault("do-trap-callbacks", null);
    }

    public JMenu getMenu()
    {
        return menu;
    }

    private void trapFlagsChanged(String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command takes an argument");
        final long flags = castToLong(args[0]);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                trapItemVRS.setSelected((flags & TRACE_STOP_VRETRACE_START) != 0);
                trapItemVRE.setSelected((flags & TRACE_STOP_VRETRACE_END) != 0);
                trapItemBKI.setSelected((flags & TRACE_STOP_BIOS_KBD) != 0);
            }
        });
    }

    public void trapDurationChanged(String cmd, Object[] args) throws IllegalArgumentException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Event takes an argument");
        duration = castToLong(args[0]);
        final long duration2 = duration;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                boolean seenNC = false;
                for(int i = 0; i < trapItemTimed.length; i++) {
                    boolean match = (duration2 == stopTime[i]);
                    trapItemTimed[i].setSelected(match);
                    seenNC = seenNC || match;
                }
                trapItemCustom.setSelected(!seenNC);
                customSel = !seenNC;
                if(!seenNC)
                    trapItemCustom.setText("Custom (" + ((double)duration2 / 1000000000) + "s)...");
            }
        });
    }

    private void handleCustomSelect()
    {
        if(customWindow != null)
            return;
        customWindow = new JFrame("Enter timeout");
        customWindow.setLayout(new FlowLayout());
        if(duration >= 0)
            customDuration = new JTextField("" + duration, 15);
        else
            customDuration = new JTextField("10000000000", 15);
        customOK = new JButton("OK");
        customCancel = new JButton("Cancel");
        customOK.addActionListener(this);
        customCancel.addActionListener(this);
        customWindow.add(customDuration);
        customWindow.add(customOK);
        customWindow.add(customCancel);
        customWindow.pack();
        customWindow.show(true);
    }

    private long parseTimeout(String str)
    {
        //TODO: Support some other forms.
        try {
            return Long.parseLong(str);
        } catch(Exception e) {
            return -1;
        }
    }

    private void setCustomTimeout(long timeout)
    {
        bus.executeCommandNoFault("trap-timed", new Object[]{timeout});
        for(int i = 0; i < trapItemTimed.length; i++)
            trapItemTimed[i].setSelected(false);
        trapItemCustom.setSelected(true);
        trapItemCustom.setText("Custom (" + ((double)duration / 1000000000) + "s)...");
        customSel = true;
    }

    public void actionPerformed(ActionEvent evt)
    {
        if(evt.getSource() == trapItemVRS) {
            bus.executeCommandNoFault("trap-vertical-retrace-start", new Object[]{trapItemVRS.isSelected()});
        } else if(evt.getSource() == trapItemVRE) {
            bus.executeCommandNoFault("trap-vertical-retrace-end", new Object[]{trapItemVRE.isSelected()});
        } else if(evt.getSource() == trapItemBKI) {
            bus.executeCommandNoFault("trap-bios-keyboard-input", new Object[]{trapItemBKI.isSelected()});
        } else if(customCancel != null && evt.getSource() == customCancel) {
            customWindow.dispose();
            customWindow = null;
        } else if(customOK != null && evt.getSource() == customOK) {
            long timeout = parseTimeout(customDuration.getText());
            if(timeout < 0)
                callShowOptionDialog(customWindow, "Can't parse timeout!", "Error", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"}, "Dismiss");
            else {
                customWindow.dispose();
                customWindow = null;
                setCustomTimeout(timeout);
            }
        } else if(evt.getSource() == trapItemCustom) {
            trapItemCustom.setSelected(customSel);
            handleCustomSelect();
        } else {
            for(int i = 0; i < trapItemTimed.length; i++) {
                if(evt.getSource() == trapItemTimed[i]) {
                    trapItemTimed[i].setSelected(true);
                    trapItemCustom.setSelected(false);
                    bus.executeCommandNoFault("trap-timed", new Object[]{stopTime[i]});
                    customSel = false;
                } else {
                    trapItemTimed[i].setSelected(false);
                }
            }
        }
    }
};
