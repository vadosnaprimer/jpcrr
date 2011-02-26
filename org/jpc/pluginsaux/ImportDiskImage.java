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

import org.jpc.pluginsaux.ConstantTableLayout;
import static org.jpc.Misc.errorDialog;
import org.jpc.mkfs.*;
import org.jpc.images.BaseImageFactory;
import org.jpc.images.JPCRRStandardImageDecoder;
import org.jpc.images.ImageID;
import org.jpc.images.BaseImage;
import org.jpc.bus.Bus;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.callShowOptionDialog;


import javax.swing.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.*;

public class ImportDiskImage implements ActionListener, KeyListener
{
    private JFrame window;
    private JTextField imageNameField;
    private JButton imageFileSelect;
    private JTextField imageFileField;
    private JComboBox imageTypeField;
    private JLabel errorMessage;
    private JButton okButton;
    private JButton cancelButton;

    private final static int OPTION_PANES = 5;
    private JPanel[] optionPanels;
    private JCheckBox[] oGuess;
    private JCheckBox[] oDoubleside;
    private JTextField[] oSides;
    private JTextField[] oTracks;
    private JTextField[] oSectors;
    private JCheckBox[] oLabelP;
    private JTextField[] oLabelF;
    private JCheckBox[] oTstampP;
    private JTextField[] oTstampF;
    private int[] sideMin;
    private int[] sideMax;
    private int[] trackMin;
    private int[] trackMax;
    private int[] sectorMin;
    private int[] sectorMax;

    private Bus bus;
    private long oldTypeMask;

    private static String NOT_VALID = "<No valid choices>";
    private static String FLOPPY = "Floppy Disk";
    private static String HDD = "Hard Disk";
    private static String CDROM = "CD-ROM Disk";
    private static String BIOS = "BIOS image";
    private static String IMAGE = "JPC-RR image";

    private GridBagConstraints getGridBagConstraints1x1(int x, int y, boolean scalex)
    {
        return new GridBagConstraints(x, y, 1, 1, scalex ? 1 : 0, 0, GridBagConstraints.CENTER,
            scalex ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    }

    private GridBagConstraints getGridBagConstraints2x1(int x, int y, boolean scalex)
    {
        return new GridBagConstraints(x, y, 2, 1, scalex ? 1 : 0, 0, GridBagConstraints.CENTER,
            scalex ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
    }

    public ImportDiskImage(Bus _bus)
    {
        oldTypeMask = -1;
        bus = _bus;

        window = new JFrame("Import Disk Image");
        JPanel wpanel = new JPanel();
        wpanel.setLayout(new GridBagLayout());
        window.add(wpanel);

        wpanel.add(new JLabel("Image name"), getGridBagConstraints1x1(0,0, false));
        wpanel.add(imageNameField = new JTextField("", 40), getGridBagConstraints1x1(1,0, true));
        wpanel.add(imageFileSelect = new JButton("Image file/directory"), getGridBagConstraints1x1(0,1, false));
        wpanel.add(imageFileField = new JTextField("", 40), getGridBagConstraints1x1(1,1, true));
        wpanel.add(new JLabel("Image type"), getGridBagConstraints1x1(0,2, false));
        wpanel.add(imageTypeField = new JComboBox(), getGridBagConstraints1x1(1,2, true));

        imageNameField.addKeyListener(this);
        imageNameField.addActionListener(this);
        imageFileSelect.addKeyListener(this);
        imageFileSelect.addActionListener(this);
        imageFileField.addKeyListener(this);
        imageFileField.addActionListener(this);
        imageTypeField.addActionListener(this);

        optionPanels = new JPanel[OPTION_PANES];
        oGuess = new JCheckBox[OPTION_PANES];
        oDoubleside = new JCheckBox[OPTION_PANES];
        oSides = new JTextField[OPTION_PANES];
        oTracks = new JTextField[OPTION_PANES];
        oSectors = new JTextField[OPTION_PANES];
        oLabelP = new JCheckBox[OPTION_PANES];
        oLabelF = new JTextField[OPTION_PANES];
        oTstampP = new JCheckBox[OPTION_PANES];
        oTstampF = new JTextField[OPTION_PANES];
        sideMin = new int[]{1, 1, 1, 1, 0};
        sideMax = new int[]{2, 2, 16, 16, 0};
        trackMin = new int[]{1, 1, 2, 2, 0};
        trackMax = new int[]{256, 256, 1024, 1024, 0};
        sectorMin = new int[]{1, 1, 1, 1, 0};
        sectorMax = new int[]{255, 255, 256, 256, 0};

        wpanel.add(optionPanels[0] = getFloppyFromFilePanel(), getGridBagConstraints2x1(0, 3, true));
        wpanel.add(optionPanels[1] = getFloppyFromDirectoryPanel(), getGridBagConstraints2x1(0, 3, true));
        wpanel.add(optionPanels[2] = getHDDFromFilePanel(), getGridBagConstraints2x1(0, 3, true));
        wpanel.add(optionPanels[3] = getHDDFromDirectoryPanel(), getGridBagConstraints2x1(0, 3, true));
        wpanel.add(optionPanels[4] = getOtherTypePanel(), getGridBagConstraints2x1(0, 3, true));

        for(int i = 0; i < optionPanels.length; i++)
            optionPanels[i].setVisible(i == 4);

        JPanel bpanel = new JPanel();
        bpanel.setLayout(new GridBagLayout());
        wpanel.add(bpanel, getGridBagConstraints2x1(0, 4, true));

        bpanel.add(errorMessage = new JLabel(""), getGridBagConstraints1x1(0, 0, true));
        bpanel.add(okButton = new JButton("Ok"), getGridBagConstraints1x1(1, 0, false));
        bpanel.add(cancelButton = new JButton("Cancel"), getGridBagConstraints1x1(2, 0, false));

        cancelButton.addActionListener(this);
        okButton.addActionListener(this);


        keyTyped(null);

        window.pack();
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setVisible(true);
    }

    private JPanel getFloppyFromFilePanel()
    {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Options"));
        p.setLayout(new GridBagLayout());
        p.add(oGuess[0] = new JCheckBox("Guess Geometry", true), getGridBagConstraints1x1(1, 0, true));
        p.add(oDoubleside[0] = new JCheckBox("Double sided", true), getGridBagConstraints1x1(1, 1, true));
        p.add(new JLabel("Tracks"), getGridBagConstraints1x1(0, 2, false));
        p.add(oTracks[0] = new JTextField("80"), getGridBagConstraints1x1(1, 2, true));
        p.add(new JLabel("Sectors"), getGridBagConstraints1x1(0, 3, false));
        p.add(oSectors[0] = new JTextField("18"), getGridBagConstraints1x1(1, 3, true));

        oGuess[0].addKeyListener(this);
        oDoubleside[0].addKeyListener(this);
        oTracks[0].addKeyListener(this);
        oSectors[0].addKeyListener(this);
        oGuess[0].addActionListener(this);
        oDoubleside[0].addActionListener(this);
        oTracks[0].addActionListener(this);
        oSectors[0].addActionListener(this);

        return p;
    }

    private JPanel getFloppyFromDirectoryPanel()
    {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Options"));
        p.setLayout(new GridBagLayout());
        p.add(oGuess[1] = new JCheckBox("Guess Geometry", true), getGridBagConstraints1x1(1, 0, true));
        p.add(oDoubleside[1] = new JCheckBox("Double sided", true), getGridBagConstraints1x1(1, 1, true));
        p.add(new JLabel("Tracks"), getGridBagConstraints1x1(0, 2, false));
        p.add(oTracks[1] = new JTextField("80"), getGridBagConstraints1x1(1, 2, true));
        p.add(new JLabel("Sectors"), getGridBagConstraints1x1(0, 3, false));
        p.add(oSectors[1] = new JTextField("18"), getGridBagConstraints1x1(1, 3, true));
        p.add(oLabelP[1] = new JCheckBox("Volume label"), getGridBagConstraints1x1(0, 4, false));
        p.add(oLabelF[1] = new JTextField(""), getGridBagConstraints1x1(1, 4, true));
        p.add(oTstampP[1] = new JCheckBox("Timestamps"), getGridBagConstraints1x1(0, 5, false));
        p.add(oTstampF[1] = new JTextField("19900101000000"), getGridBagConstraints1x1(1, 5, true));

        oGuess[1].addKeyListener(this);
        oGuess[1].addActionListener(this);
        oDoubleside[1].addKeyListener(this);
        oDoubleside[1].addActionListener(this);
        oTracks[1].addKeyListener(this);
        oTracks[1].addActionListener(this);
        oSectors[1].addKeyListener(this);
        oSectors[1].addActionListener(this);
        oLabelP[1].addKeyListener(this);
        oLabelP[1].addActionListener(this);
        oLabelF[1].addKeyListener(this);
        oLabelF[1].addActionListener(this);
        oTstampP[1].addKeyListener(this);
        oTstampP[1].addActionListener(this);
        oTstampF[1].addKeyListener(this);
        oTstampF[1].addActionListener(this);

        return p;
    }

    private JPanel getHDDFromFilePanel()
    {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Options"));
        p.setLayout(new GridBagLayout());
        p.add(new JLabel("Sides"), getGridBagConstraints1x1(0, 0, false));
        p.add(oSides[2] = new JTextField("16"), getGridBagConstraints1x1(1, 0, true));
        p.add(new JLabel("Tracks"), getGridBagConstraints1x1(0, 1, false));
        p.add(oTracks[2] = new JTextField("16"), getGridBagConstraints1x1(1, 1, true));
        p.add(new JLabel("Sectors"), getGridBagConstraints1x1(0, 2, false));
        p.add(oSectors[2] = new JTextField("63"), getGridBagConstraints1x1(1, 2, true));

        oSides[2].addKeyListener(this);
        oSides[2].addActionListener(this);
        oTracks[2].addKeyListener(this);
        oTracks[2].addActionListener(this);
        oSectors[2].addKeyListener(this);
        oSectors[2].addActionListener(this);

        return p;
    }

    private JPanel getHDDFromDirectoryPanel()
    {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder("Options"));
        p.setLayout(new GridBagLayout());
        p.add(oGuess[3] = new JCheckBox("Guess Geometry", true), getGridBagConstraints1x1(1, 0, true));
        p.add(new JLabel("Sides"), getGridBagConstraints1x1(0, 1, false));
        p.add(oSides[3] = new JTextField("16"), getGridBagConstraints1x1(1, 1, true));
        p.add(new JLabel("Tracks"), getGridBagConstraints1x1(0, 2, false));
        p.add(oTracks[3] = new JTextField("16"), getGridBagConstraints1x1(1, 2, true));
        p.add(new JLabel("Sectors"), getGridBagConstraints1x1(0, 3, false));
        p.add(oSectors[3] = new JTextField("63"), getGridBagConstraints1x1(1, 3, true));
        p.add(oLabelP[3] = new JCheckBox("Volume label"), getGridBagConstraints1x1(0, 4, false));
        p.add(oLabelF[3] = new JTextField(""), getGridBagConstraints1x1(1, 4, true));
        p.add(oTstampP[3] = new JCheckBox("Timestamps"), getGridBagConstraints1x1(0, 5, false));
        p.add(oTstampF[3] = new JTextField("19900101000000"), getGridBagConstraints1x1(1, 5, true));

        oGuess[3].addKeyListener(this);
        oGuess[3].addActionListener(this);
        oSides[3].addKeyListener(this);
        oSides[3].addActionListener(this);
        oTracks[3].addKeyListener(this);
        oTracks[3].addActionListener(this);
        oSectors[3].addKeyListener(this);
        oSectors[3].addActionListener(this);
        oLabelP[3].addKeyListener(this);
        oLabelP[3].addActionListener(this);
        oLabelF[3].addKeyListener(this);
        oLabelF[3].addActionListener(this);
        oTstampP[3].addKeyListener(this);
        oTstampP[3].addActionListener(this);
        oTstampF[3].addKeyListener(this);
        oTstampF[3].addActionListener(this);

        return p;
    }

    private JPanel getOtherTypePanel()
    {
        JPanel p = new JPanel();
        p.add(new JLabel("No options available"));
        p.setBorder(BorderFactory.createTitledBorder("Options"));
        return p;
    }

    private boolean checkVolumeLabel(String text)
    {
        if(text.length() > 11)
            return false;
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if(c == 34 || c == 124)
                return false;
            if(c >= 33 && c <= 41)
                continue;
            if(c == 45)
                continue;
            if(c >= 48 && c <= 57)
                continue;
            if(c >= 64 && c <= 90)
                continue;
            if(c >= 94 && c <= 126)
                continue;
            if(c >= 160 && c <= 255)
                continue;
            return false;
        }
        return true;
    }

    private boolean checkTimeStamp(String text)
    {
        try {
            TreeDirectoryFile.dosFormatTimeStamp(text);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    private String handlePanel(int i)
    {
        if(!optionPanels[i].isVisible())
            return null;
        if(oDoubleside[i] != null && oGuess[i] != null)
            oDoubleside[i].setEnabled(!oGuess[i].isSelected());
        if(oSides[i] != null && oGuess[i] != null)
            oSides[i].setEnabled(!oGuess[i].isSelected());
        if(oTracks[i] != null && oGuess[i] != null)
            oTracks[i].setEnabled(!oGuess[i].isSelected());
        if(oSectors[i] != null && oGuess[i] != null)
            oSectors[i].setEnabled(!oGuess[i].isSelected());
        if(oLabelF[i] != null)
            oLabelF[i].setEnabled(oLabelP[i].isSelected());
        if(oTstampF[i] != null)
            oTstampF[i].setEnabled(oTstampP[i].isSelected());

        if(oLabelF[i] != null && oLabelP[i].isSelected() && !checkVolumeLabel(oLabelF[i].getText()))
            return "Bad volume label";
        if(oTstampF[i] != null && oTstampP[i].isSelected() && !checkTimeStamp(oTstampF[i].getText()))
            return "Bad timestamp";

        if(oGuess[i] != null && oGuess[i].isSelected())
            return null;

        int sides = 0;
        int tracks = 0;
        int sectors = 0;

        if(oDoubleside[i] != null)
            sides = oDoubleside[i].isSelected() ? 2 : 1;

        try {
            if(oSides[i] != null)
                sides = Integer.parseInt(oSides[i].getText());
            if(sides < sideMin[i] || sides > sideMax[i])
                throw new Exception("Bad tracks");
        } catch(Exception e) {
            return "Bad side count (" + sideMin[i] + "-" + sideMax[i] + ")";
        }
        try {
            if(oTracks[i] != null)
                tracks = Integer.parseInt(oTracks[i].getText());
            if(tracks < trackMin[i] || tracks > trackMax[i])
                throw new Exception("Bad tracks");
        } catch(Exception e) {
            return "Bad track count (" + trackMin[i] + "-" + trackMax[i] + ")";
        }
        try {
            if(oSectors[i] != null)
                sectors = Integer.parseInt(oSectors[i].getText());
            if(sectors < sectorMin[i] || sectors > sectorMax[i])
                throw new Exception("Bad sectors");
        } catch(Exception e) {
            return "Bad sector count (" + sectorMin[i] + "-" + sectorMax[i] + ")";
        }

        long expectedSize = 512L * sides * tracks * sectors;
        File f = new File(imageFileField.getText());
        if(!f.isFile() || f.length() != expectedSize)
            return "File is " + f.length() + " bytes, expected " + expectedSize;

        return null;
    }

    private void updateVisibleOptionsCard()
    {
        boolean isFile = (new File(imageFileField.getText())).isFile();
        boolean isDirectory = (new File(imageFileField.getText())).isDirectory();
        int field;
        if(isFile && FLOPPY.equals(imageTypeField.getSelectedItem()))
            field = 0;
        else if(isDirectory && FLOPPY.equals(imageTypeField.getSelectedItem()))
            field = 1;
        else if(isFile && HDD.equals(imageTypeField.getSelectedItem()))
            field = 2;
        else if(isDirectory && HDD.equals(imageTypeField.getSelectedItem()))
            field = 3;
        else
            field = 4;

        if(optionPanels[field].isVisible())
                return;
        for(int i = 0; i < optionPanels.length; i++)
            optionPanels[i].setVisible(i == field);
        window.pack();
    }

    private void setTypes(long mask)
    {
        if(mask == oldTypeMask)
            return;  //Don't mess up by changing the dropdown when there's nothing to change.
        oldTypeMask = mask;

        imageTypeField.removeAllItems();

        if((mask & 0x1F) == 0) {
            imageTypeField.addItem(NOT_VALID);
            imageTypeField.setSelectedItem(NOT_VALID);
            imageTypeField.setEnabled(false);
            return;
        }
        imageTypeField.setEnabled(true);
        if((mask & 0x10) != 0) {
            imageTypeField.addItem(IMAGE);
            imageTypeField.setSelectedItem(IMAGE);
        }
        if((mask & 0x8) != 0) {
            imageTypeField.addItem(BIOS);
            imageTypeField.setSelectedItem(BIOS);
        }
        if((mask & 0x4) != 0) {
            imageTypeField.addItem(CDROM);
            imageTypeField.setSelectedItem(CDROM);
        }
        if((mask & 0x2) != 0) {
            imageTypeField.addItem(HDD);
            imageTypeField.setSelectedItem(HDD);
        }
        if((mask & 0x1) != 0) {
            imageTypeField.addItem(FLOPPY);
            imageTypeField.setSelectedItem(FLOPPY);
        }
    }

    private String setTypesForFile()
    {
        String image = imageFileField.getText();
        if(image.equals("")) {
            setTypes(0);  //Nothing is valid.
            return "Image file is blank";
        }
        File imageF = new File(image);
        if(!imageF.exists()) {
            setTypes(0);  //Nothing is valid.
            return "Image file does not exist";
        }
        if(imageF.isDirectory()) {
            if(!imageF.canRead()) {
                setTypes(0);  //Nothing is valid.
                return "Image directory is not readable";
            }
            setTypes(3);
            return null;  //Floppy or HDD.
        } else if(imageF.isFile()) {
            if(!imageF.canRead()) {
                setTypes(0);  //Nothing is valid.
                return "Image directory is not readable";
            }
            try {
                long mask = 0;
                long len = imageF.length();
                if(len > 1 && len < 262144)
                    mask |= 0x8;  //BIOS OK.
                if((len % 512) == 0 && len > 0)
                    mask |= 0x7;  //Floppy, HDD and CDROM OK.
                RandomAccessFile f = new RandomAccessFile(imageF, "r");
                byte[] headerbuffer = new byte[24];
                if(f.read(headerbuffer) == 24 && headerbuffer[0] == (byte)'I' && headerbuffer[1] == (byte)'M' &&
                    headerbuffer[2] == (byte)'A' && headerbuffer[3] == (byte)'G' && headerbuffer[4] == (byte)'E') {
                    mask |= 0x10;
                }
                f.close();
                setTypes(mask);
                return (mask == 0) ? "Nothing valid can be built from that file" : null;
            } catch(Exception e) {
                setTypes(0);  //Nothing is valid.
                return "Image can't be read";
            }
        } else {
            setTypes(0);  //Nothing is valid.
            return "Image is neither file nor directory";
        }
    }

    private String checkName()
    {
        String image = imageNameField.getText();
        if("".equals(image))
            return "No image name specified";
        if(image.startsWith("/"))
            return "Image name can't start with '/'";
        if(image.indexOf("//") >= 0)
            return "Image name can't have '//'";
        if(image.lastIndexOf("/") == image.length() - 1)
            return "Image name can't end in '/'";
        return null;
    }

    private String checkPanels()
    {
        String x = null;
        for(int i = 0; i < optionPanels.length; i++)
            if(x == null)
                x = handlePanel(i);
            else
                handlePanel(i);
        return x;
    }

    private void revalidateForm()
    {
        String x = null;
        if(x == null)
            x = checkName();
        else
            checkName();
        if(x == null)
            x = setTypesForFile();
        else
            setTypesForFile();
        updateVisibleOptionsCard();
        if(x == null)
            x = checkPanels();
        else
            checkPanels();

        String prevError = errorMessage.getText();
        if(prevError.equals((x == null) ? "" : x))
             return;
        if(x != null) {
            okButton.setEnabled(false);
            errorMessage.setText(x);
        } else {
            okButton.setEnabled(true);
            errorMessage.setText("");
        }
    }

    private void selectImage()
    {
        JFileChooser fc = new JFileChooser();
        fc.setApproveButtonText("Select");
        fc.setDialogTitle("Select image file or directory to import");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int returnVal = fc.showOpenDialog(window);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            imageFileField.setText(fc.getSelectedFile().getAbsolutePath());
            keyTyped(null);
        }
    }

    private void runImport(List<String> args) throws Exception
    {
        Object[] args2 = args.toArray();
        Object[] o = bus.executeCommandSynchronous("make-image", args2);
        callShowOptionDialog(null, "New image (ID " + o[0] + ") imported",
            "Image imported", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
            "Dismiss");
    }

    private int getSides() throws Exception
    {
        for(int i = 0; i < optionPanels.length; i++) {
            if(!optionPanels[i].isVisible())
                continue;
            if(oGuess[i] != null && oGuess[i].isSelected())
                return 0;
            if(oDoubleside[i] != null)
                return oDoubleside[i].isSelected() ? 2 : 1;
            if(oSides[i] != null)
                return Integer.parseInt(oSides[i].getText());
        }
        return 0;
    }

    private int getTracks() throws Exception
    {
        for(int i = 0; i < optionPanels.length; i++) {
            if(!optionPanels[i].isVisible())
                continue;
            if(oGuess[i] != null && oGuess[i].isSelected())
                return 0;
            if(oTracks[i] != null)
                return Integer.parseInt(oTracks[i].getText());
        }
        return 0;
    }

    private int getSectors() throws Exception
    {
        for(int i = 0; i < optionPanels.length; i++) {
            if(!optionPanels[i].isVisible())
                continue;
            if(oGuess[i] != null && oGuess[i].isSelected())
                return 0;
            if(oSectors[i] != null)
                return Integer.parseInt(oSectors[i].getText());
        }
        return 0;
    }

    private String getVolumeLabel()
    {
        for(int i = 0; i < optionPanels.length; i++) {
            if(!optionPanels[i].isVisible())
                continue;
            if(oLabelP[i].isSelected())
                return oLabelF[i].getText();
        }
        return null;
    }

    private String getTimestamp()
    {
        for(int i = 0; i < optionPanels.length; i++) {
            if(!optionPanels[i].isVisible())
                continue;
            if(oTstampP[i].isSelected())
                return oTstampF[i].getText();
        }
        return null;
    }

    private void doImportD(String type) throws Exception
    {
        List<String> x = new ArrayList<String>();
        x.add(imageNameField.getText());
        x.add(imageFileField.getText());
        x.add(type);
        int sides = getSides();
        int tracks = getTracks();
        int sectors = getSectors();
        String volumeLabel = getVolumeLabel();
        String timestamp = getTimestamp();
        if(sides != 0) {
            x.add("sides=" + sides);
            x.add("tracks=" + tracks);
            x.add("sectors=" + sectors);
        }
        if(volumeLabel != null)
            x.add("volumelabel=" + volumeLabel);
        if(timestamp != null)
            x.add("timestamp=" + timestamp);
        runImport(x);
    }

    private void doImportF(String type) throws Exception
    {
        List<String> x = new ArrayList<String>();
        x.add(imageNameField.getText());
        x.add(imageFileField.getText());
        x.add(type);
        int sides = getSides();
        int tracks = getTracks();
        int sectors = getSectors();
        if(sides != 0) {
            x.add("sides=" + sides);
            x.add("tracks=" + tracks);
            x.add("sectors=" + sectors);
        }
        runImport(x);
    }

    private void doImport() throws Exception
    {
        boolean isFile = (new File(imageFileField.getText())).isFile();
        boolean isDirectory = (new File(imageFileField.getText())).isDirectory();
        if(isFile) {
            if(FLOPPY.equals(imageTypeField.getSelectedItem()))
                doImportF("FLOPPY");
            else if(HDD.equals(imageTypeField.getSelectedItem()))
                doImportF("HARDDRIVE");
            else if(CDROM.equals(imageTypeField.getSelectedItem()))
                doImportF("CDROM");
            else if(BIOS.equals(imageTypeField.getSelectedItem()))
                doImportF("BIOS");
            else if(IMAGE.equals(imageTypeField.getSelectedItem()))
                doImportF("IMAGE");
            else
                throw new Exception("Unknown image type");
        } else if(isDirectory) {
            if(FLOPPY.equals(imageTypeField.getSelectedItem()))
                doImportF("FLOPPY");
            else if(HDD.equals(imageTypeField.getSelectedItem()))
                doImportF("HARDDRIVE");
            else
                throw new Exception("This image type can't be made out of directories");
        } else
            throw new Exception("Not file nor directory, can't make image out of this");
    }

    public void actionPerformed(ActionEvent evt)
    {
        revalidateForm();
        if(evt.getSource() == cancelButton) {
            window.dispose();
        } else if(evt.getSource() == imageFileSelect) {
            selectImage();
        } else if(evt.getSource() == okButton) {
           if(!errorMessage.getText().equals(""))
               return;
           try {
               doImport();
               window.dispose();
           } catch(Exception e) {
               errorDialog(e, "Error making image", window, "Dismiss");
           }
        }
    }

    public void keyTyped(KeyEvent event)
    {
        revalidateForm();
    }

    public void keyPressed(KeyEvent event)
    {
        keyTyped(event);
    }

    public void keyReleased(KeyEvent event)
    {
        keyTyped(event);
    }
}
