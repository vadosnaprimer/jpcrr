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
import org.jpc.diskimages.ImageMaker;
import org.jpc.diskimages.ImageLibrary;
import org.jpc.images.JPCRRStandardImageDecoder;
import org.jpc.images.ImageID;
import org.jpc.images.BaseImage;
import static org.jpc.Misc.tempname;
import static org.jpc.Misc.callShowOptionDialog;
import static org.jpc.diskimages.DiskImage.getLibrary;


import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.io.*;

public class ImportDiskImage implements ActionListener, KeyListener
{
    private JFrame window;
    private JPanel cpanel;
    private JPanel lpanel;
    private JPanel rpanel;
    private JTextField imageName;
    private JTextField imageFile;
    private JComboBox imageType;
    private JLabel feedback;
    private JButton importDisk;
    private JButton cancel;
    private JCheckBox stdGeometry;
    private JCheckBox doublesided;
    private JTextField sides;
    private JLabel sidesFixed;
    private JTextField sectors;
    private JLabel sectorsFixed;
    private JTextField tracks;
    private JLabel tracksFixed;
    private JCheckBox volumeLabelLabel;
    private JTextField volumeLabel;
    private JCheckBox createTimeLabel;
    private JTextField createTime;
    private static String NOT_VALID = "<No valid choice>";
    private static String FLOPPY = "Floppy Disk";
    private static String HDD = "Hard Disk";
    private static String CDROM = "CD-ROM Disk";
    private static String BIOS = "BIOS image";
    private static String IMAGE = "JPC-RR image";

    private boolean fileSelected;
    private long fileSelectedLength;
    private boolean directorySelected;
    private boolean constructed;
    private long fileCase;

    public ImportDiskImage()
    {
        int height = 9;
        fileCase = -1;  //Initial.
        window = new JFrame("Import Disk Image");

        GridBagLayout clayout = new GridBagLayout();
        cpanel = new JPanel(clayout);
        GridBagLayout llayout = new GridBagLayout();
        lpanel = new JPanel(llayout);

        rpanel = new JPanel();
        rpanel.setLayout(new BoxLayout(rpanel, BoxLayout.Y_AXIS));

        rpanel.add(cpanel);
        rpanel.add(lpanel);
        window.add(rpanel);

        add(new JLabel("New image name"), 0, 0);
        add(imageName = new JTextField("", 50), 1, 0);
        imageName.addKeyListener(this);

        JButton select;
        add(select = new JButton("Image file/directory"), 0, 1);
        add(imageFile = new JTextField("", 50), 1, 1);
        imageFile.addKeyListener(this);
        select.addActionListener(this);
        select.setActionCommand("SELECT");

        add(new JLabel("Image Type"), 0, 2);
        add(imageType = new JComboBox(), 1, 2);
        imageType.addActionListener(this);
        setNoValidChoice(imageType);

        add(stdGeometry = new JCheckBox("Standard geometry"), 0, 3);
        stdGeometry.setEnabled(false);
        stdGeometry.addActionListener(this);

        add(doublesided = new JCheckBox("Double-sided"), 0, 4);
        doublesided.setEnabled(false);
        doublesided.addActionListener(this);

        add(new JLabel("Sides"), 0, 5);
        add(sides = new JTextField("16", 50), 1, 5);
        add(sidesFixed = new JLabel("N/A"), 1, 5);
        sides.setVisible(false);
        sides.addKeyListener(this);

        add(new JLabel("Sectors"), 0, 6);
        add(sectors = new JTextField("63", 50), 1, 6);
        add(sectorsFixed = new JLabel("N/A"), 1, 6);
        sectors.setVisible(false);
        sectors.addKeyListener(this);

        add(new JLabel("Tracks"), 0, 7);
        add(tracks = new JTextField("16", 50), 1, 7);
        add(tracksFixed = new JLabel("N/A"), 1, 7);
        tracks.setVisible(false);
        tracks.addKeyListener(this);

        add(volumeLabelLabel = new JCheckBox("Volume label"), 0, 8);
        add(volumeLabel = new JTextField("", 50), 1, 8);
        volumeLabel.setEnabled(false);
        volumeLabelLabel.setEnabled(false);
        volumeLabelLabel.addActionListener(this);
        volumeLabel.addKeyListener(this);

        add(createTimeLabel = new JCheckBox("File timestamps"), 0, 9);
        add(createTime = new JTextField("19900101000000", 50), 1, 9);
        createTime.setEnabled(false);
        createTimeLabel.setEnabled(false);
        createTimeLabel.addActionListener(this);
        createTime.addKeyListener(this);

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        lpanel.add(feedback = new JLabel(""), c);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        lpanel.add(importDisk = new JButton("Import"), c);

        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        lpanel.add(cancel = new JButton("Cancel"), c);

        importDisk.setActionCommand("IMPORT");
        importDisk.addActionListener(this);
        importDisk.setEnabled(false);
        cancel.setActionCommand("CANCEL");
        cancel.addActionListener(this);

        revalidateForm();

        constructed = true;
        keyTyped(null);

        window.pack();
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setVisible(true);
    }

    private void selectImage()
    {
        JFileChooser fc = new JFileChooser();
        fc.setApproveButtonText("Select");
        fc.setDialogTitle("Select image file or directory to import");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        int returnVal = fc.showOpenDialog(window);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            imageFile.setText(fc.getSelectedFile().getAbsolutePath());
            keyTyped(null);
        }
    }

    private void setNoValidChoice(JComboBox box)
    {
        box.removeAllItems();
        box.addItem(NOT_VALID);
        box.setSelectedItem(NOT_VALID);
        box.setEnabled(false);
    }

    private void add(JComponent component, int x, int y)
    {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        if(x == 1)
            c.fill = GridBagConstraints.HORIZONTAL;
        cpanel.add(component, c);
    }

    private int checkVolumeLabel(String text)
    {
        if(text.length() > 11)
            return 1;
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if(c == 34 || c == 124)
                return 2;
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
            return 2;
        }
        return 0;
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

    private void revalidateForm()
    {
        if("".equals(imageName.getText())) {
            feedback.setText("No image name specified");
            importDisk.setEnabled(false);
            return;
        }
        if(imageName.getText().startsWith("/")) {
            feedback.setText("Image name can't start with '/'");
            importDisk.setEnabled(false);
            return;
        }
        if(imageName.getText().indexOf("//") >= 0) {
            feedback.setText("Image name can't have '//'");
            importDisk.setEnabled(false);
            return;
        }
        if(imageName.getText().lastIndexOf("/") == imageName.getText().length() - 1) {
            feedback.setText("Image name can't end in '/'");
            importDisk.setEnabled(false);
            return;
        }
        if("".equals(imageFile.getText())) {
            feedback.setText("No image file specified");
            importDisk.setEnabled(false);
            return;
        }
        String _filename = imageFile.getText();
        File fileObject = new File(_filename);
        if(!fileObject.exists()) {
            feedback.setText("Image file does not exist");
            importDisk.setEnabled(false);
            return;
        }
        if(!fileObject.isFile() && !fileObject.isDirectory()) {
            feedback.setText("Special devices not allowed as image files");
            importDisk.setEnabled(false);
            return;
        }

        int sides = getSides();
        int sectors = getSectors();
        int tracks = getTracks();
        String type = (String)imageType.getSelectedItem();
        if(FLOPPY.equals(type)) {
            if(sides < 1 || sides > 2) {
                feedback.setText("Sides out of range (1-2)");
                importDisk.setEnabled(false);
                return;
            }
            if(sectors < 1 || sectors > 255) {
                feedback.setText("Sectors out of range (1-255)");
                importDisk.setEnabled(false);
                return;
            }
            if(tracks < 1 || tracks > 256) {
                feedback.setText("Tracks out of range (1-256)");
                importDisk.setEnabled(false);
                return;
            }
        } else if(HDD.equals(type)) {
            if(sides < 1 || sides > 16) {
                feedback.setText("Sides out of range (1-16)");
                importDisk.setEnabled(false);
                return;
            }
            if(sectors < 1 || sectors > 63) {
                feedback.setText("Sectors out of range (1-63)");
                importDisk.setEnabled(false);
                return;
            }
            if(tracks < 1 || tracks > 1024) {
                feedback.setText("Tracks out of range (1-1024)");
                importDisk.setEnabled(false);
                return;
            }
        } else if(IMAGE.equals(type)) {
        } else if(CDROM.equals(type)) {
        } else if(BIOS.equals(type)) {
        } else {
            feedback.setText("Bad image type");
            importDisk.setEnabled(false);
            return;
        }
        if(volumeLabelLabel.isEnabled() && volumeLabelLabel.isSelected()) {
            int res = 0;
            res = checkVolumeLabel(volumeLabel.getText());
            if(res == 1) {
                feedback.setText("Volume label too long");
                importDisk.setEnabled(false);
                return;
            }
            if(res == 2) {
                feedback.setText("Invalid character in volume label");
                importDisk.setEnabled(false);
                return;
            }
        }
        if(createTimeLabel.isEnabled() && createTimeLabel.isSelected()) {
            if(!checkTimeStamp(createTime.getText())) {
                feedback.setText("Bad timestamp (YYYYMMDDHHMMSS)");
                importDisk.setEnabled(false);
                return;
            }
        }
        feedback.setText("");
        importDisk.setEnabled(true);
    }

    public class ImportTask implements Runnable
    {
        String name;
        String file;
        int typeCode;
        int sides;
        int sectors;
        int tracks;
        String label;
        String timestamp;

        private ImageID writeImage(String out, String src, ImageMaker.IFormat format) throws IOException
        {
            BaseImage input;
            File srcFile = new File(src);
            if(format.typeCode == 3 || format.typeCode == 2) {
                BaseImage.Type type;
                if(format.typeCode == 2)
                    type = BaseImage.Type.CDROM;
                else
                    type = BaseImage.Type.BIOS;
                //Read the image.
                if(!srcFile.isFile())
                    throw new IOException("CD/BIOS images can only be made out of regular files");
                FileRawDiskImage input2 = new FileRawDiskImage(src, 0, 0, 0, type);
                return JPCRRStandardImageEncoder.writeImage(out, input2);
             } else if(format.typeCode == 0 || format.typeCode == 1) {
                BaseImage.Type type;
                if(format.typeCode == 0)
                    type = BaseImage.Type.FLOPPY;
                else
                    type = BaseImage.Type.HARDDRIVE;
                if(srcFile.isFile()) {
                    input = new FileRawDiskImage(src, format.sides, format.tracks, format.sectors, type);
                } else if(srcFile.isDirectory()) {
                    TreeDirectoryFile root = TreeDirectoryFile.importTree(src, format.volumeLabel, format.timestamp);
                    input = new TreeRawDiskImage(root, format, format.volumeLabel, type);
                } else
                    throw new IOException("Source is neither regular file nor directory");
                return JPCRRStandardImageEncoder.writeImage(out, input);
            } else if(format.typeCode == -1) {
                if(!srcFile.isFile())
                    throw new IOException("Didn't I check that this JPC-RR image is a regular file?");
                BaseImage input2 = JPCRRStandardImageDecoder.readImage(src);
                return JPCRRStandardImageEncoder.writeImage(out, input2);
            } else
                throw new IOException("BUG: Invalid image type code " + format.typeCode);
        }

        private ImageID warpedRun() throws Exception
        {
            ImageID id = null;
            int index;
            RandomAccessFile output;
            String finalName = getLibrary().getPathPrefix() + name;
            ImageMaker.IFormat fmt = new ImageMaker.IFormat(null);
            fmt.typeCode = typeCode;
            fmt.tracks = tracks;
            fmt.sectors = sectors;
            fmt.sides = sides;
            fmt.timestamp = timestamp;
            fmt.volumeLabel = label;

            index = finalName.lastIndexOf("/");
            if(index < 0)
                index = finalName.lastIndexOf(File.separator);
            File dirFile = new File(finalName.substring(0, index));
            if(!dirFile.isDirectory())
                if(!dirFile.mkdirs())
                    throw new IOException("Can't create directory '" + dirFile.getAbsolutePath() + "'");

            try {
                id = writeImage(finalName, file, fmt);
            } catch(Exception e) {
                throw e;
            }
            getLibrary().insertFileName(id, finalName, name);
            return id;
        }

        public void run()
        {
            ImageID id = null;
            try {
                id = warpedRun();
            } catch(Exception e) {
                doImportFinished(e, null);
                return;
            }
            doImportFinished(null, id);
        }
    }

    private void importDisk() throws IOException
    {
        String _imageName = imageName.getText();
        String _imageFile = imageFile.getText();
        String _imageType = (String)imageType.getSelectedItem();
        int sides = getSides();
        int sectors = getSectors();
        int tracks = getTracks();
        String label = null;
        if(volumeLabelLabel.isEnabled() && volumeLabelLabel.isSelected())
            label = volumeLabel.getText();
        String timestamp = null;
        if(createTimeLabel.isEnabled() && createTimeLabel.isSelected())
            timestamp = createTime.getText();
        int typeCode = -1;
        if(FLOPPY.equals(_imageType)) {
            typeCode = 0;
            if(sides < 1 || sides > 2 || sectors < 1 || sectors > 255 || tracks < 1 || tracks > 256)
                throw new IOException("Illegal floppy geometry " + sides + " sides " + sectors + " sectors " +
                    tracks + " tracks");
        } else if(HDD.equals(_imageType)) {
            typeCode = 1;
            if(sides < 1 || sides > 16 || sectors < 1 || sectors > 63 || tracks < 1 || tracks > 1024)
                throw new IOException("Illegal HDD geometry " + sides + " sides " + sectors + " sectors " +
                    tracks + " tracks");
        } else if(CDROM.equals(_imageType)) {
            typeCode = 2;
        } else if(BIOS.equals(_imageType)) {
            typeCode = 3;
        } else if(IMAGE.equals(_imageType)) {
            typeCode = -1;
        } else {
            throw new IOException("Illegal Image type: " + _imageType);
        }
        ImportTask t = new ImportTask();
        t.name = _imageName;
        t.file = _imageFile;
        t.typeCode = typeCode;
        t.sides = sides;
        t.sectors = sectors;
        t.tracks = tracks;
        t.label = label;
        t.timestamp = timestamp;
        (new Thread(t, "Import task thread")).start();
    }

    private void doImportFinished(Exception failure, ImageID id)
    {
       final Exception failure2 = failure;
       final ImageID id2 = id;
       try {
           SwingUtilities.invokeLater(new Runnable() { public void run() { importFinished(failure2, id2); }});
       } catch(Exception e) {
       }
    }

    private void importFinished(Exception failure, ImageID id)
    {
        if(failure != null) {
            errorDialog(failure, "Error making image", window, "Dismiss");
            cancel.setEnabled(true);
            keyTyped(null);
        } else {
            //Get rid of window.
            callShowOptionDialog(null, "New image (ID " + id + ") imported",
               "Image imported", JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{"Dismiss"},
               "Dismiss");
            window.dispose();
        }
    }

    public void actionPerformed(ActionEvent evt)
    {
        String command = evt.getActionCommand();
        if("IMPORT".equals(command)) {
            feedback.setText("Importing disk...");
            importDisk.setEnabled(false);
            cancel.setEnabled(false);
            try {
                importDisk();
            } catch(Exception e) {
                errorDialog(e, "Error making image", window, "Dismiss");
                cancel.setEnabled(true);
                keyTyped(null);
            }
        } else if("CANCEL".equals(command)) {
            window.dispose();
        } else if("SELECT".equals(command)) {
            selectImage();
        } else {
            keyTyped(null);
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

    private void updateFile()
    {
        String _filename = imageFile.getText();
        if(!"".equals(_filename)) {
            String firstType = null;
            File fileObject = new File(_filename);
            if(fileObject.exists() && fileObject.isFile()) {
                RandomAccessFile f = null;
                fileSelected = true;
                directorySelected = false;
                fileSelectedLength = fileObject.length();
                if(fileCase == fileSelectedLength)
                    return;
                fileCase = fileSelectedLength;
                imageType.removeAllItems();
                try {
                    f = new RandomAccessFile(fileObject, "r");
                    byte[] b = new byte[24];
                    f.readFully(b);
                    f.close();
                    f = null;
                    if(b[0] != 73 || b[1] != 77 || b[2] != 65 || b[3] != 71 || b[4] != 69)
                        throw new IOException("Not a disk image file");
                    imageType.addItem(IMAGE);
                    if(firstType == null)
                        firstType = IMAGE;
                } catch(Exception e) {
                    if(f != null)
                        try {
                            f.close();
                        } catch(Exception g) {
                        }
                }
                if(fileSelectedLength > 0 && fileSelectedLength <= 262144) {
                    imageType.addItem(BIOS);
                    if(firstType == null)
                        firstType = BIOS;
                }
                if(fileObject.length() > 0 && (fileObject.length() % 512) == 0) {
                    imageType.addItem(FLOPPY);
                    imageType.addItem(HDD);
                    imageType.addItem(CDROM);
                    if(firstType == null)
                        firstType = FLOPPY;
                }
            }
            if(fileObject.exists() && fileObject.isDirectory()) {
                if(fileCase == -2)
                    return;
                fileCase = -2;
                fileSelected = false;
                directorySelected = true;
                imageType.removeAllItems();
                imageType.addItem(FLOPPY);
                imageType.addItem(HDD);
                if(firstType == null)
                    firstType = FLOPPY;
            }
            if(firstType != null) {
                imageType.setSelectedItem(firstType);
                imageType.setEnabled(true);
            } else {
                if(fileCase == -3)
                    return;
                fileCase = -3;
                fileSelected = false;
                directorySelected = false;
                setNoValidChoice(imageType);
            }
        } else {
            if(fileCase == -4)
                return;
            fileCase = -4;
            fileSelected = false;
            directorySelected = false;
            setNoValidChoice(imageType);
        }
    }

    public void keyTyped(KeyEvent event)
    {
        if(!constructed)
            return;

        updateFile();
        changeGeometry();

        if(directorySelected) {
            createTimeLabel.setEnabled(true);
            volumeLabelLabel.setEnabled(true);
            if(createTimeLabel.isSelected())
                createTime.setEnabled(true);
            else
                createTime.setEnabled(false);
            if(volumeLabelLabel.isSelected())
                volumeLabel.setEnabled(true);
            else
                volumeLabel.setEnabled(false);
        } else {
            volumeLabel.setEnabled(false);
            volumeLabelLabel.setEnabled(false);
            createTime.setEnabled(false);
            createTimeLabel.setEnabled(false);
        }

        revalidateForm();
        window.pack();
    }

    private int textToInt(String text)
    {
        if(text == null)
            return -1;
        try {
            int v = Integer.parseInt(text);
            if(v <= 0)
                return -1;
            return v;
        } catch(NumberFormatException e) {
            return -1;
        }
    }

    //40, 41, 42, 43 tracks, 1 sides, 1-15 sectors (up to 320kB).

    private boolean stdGeometryValid(long len)
    {
        if((len % 512) != 0)
            return false;
        if(len < 320 * 1024 && (len % 20480) != 0)
            return true;  //40 tracks, single sided.
        if(len < 320 * 1024 && (len % 20992) != 0)
            return true;  //41 tracks, single sided.
        if(len < 320 * 1024 && (len % 21504) != 0)
            return true;  //42 tracks, single sided.
        if(len < 320 * 1024 && (len % 22016) != 0)
            return true;  //43 tracks, single sided.
        if(len < 320 * 1024)
            return false;  //Cutoff for single-sided.
        if(len < 720 * 1024 && (len % 40960) != 0)
            return true;  //40 tracks, double sided.
        if(len < 720 * 1024 && (len % 41984) != 0)
            return true;  //41 tracks, double sided.
        if(len < 720 * 1024 && (len % 43008) != 0)
            return true;  //42 tracks, double sided.
        if(len < 720 * 1024 && (len % 44032) != 0)
            return true;  //43 tracks, double sided.
        if(len < 320 * 1024)
            return false;  //Cutoff for 40 tracks double sided.
        if(len > 4079616)
            return false;  //exceeds 83 tracks, 48 sectors double sided.
        if((len % 81920) != 0)
            return true;  //80 tracks, double sided.
        if((len % 82944) != 0)
            return true;  //81 tracks, double sided.
        if((len % 83968) != 0)
            return true;  //82 tracks, double sided.
        if((len % 84992) != 0)
            return true;  //83 tracks, double sided.
        return false;
    }

    private int stdGeometrySides(long len)
    {
        if(len < 320 * 1024)
            return 1;
        else
            return 2;
    }

    private int getSides()
    {
        String type = (String)imageType.getSelectedItem();
        if(FLOPPY.equals(type)) {
            if(stdGeometry.isEnabled() && stdGeometry.isSelected())
                return stdGeometrySides(fileSelectedLength);
            return doublesided.isSelected() ? 2 : 1;
        } else if(HDD.equals(type)) {
            return textToInt(sides.getText());
        } else
            return -1;
    }

    private boolean forceSides()
    {
        String type = (String)imageType.getSelectedItem();
        if(HDD.equals(type))
            return false;
        return true;
    }

    private long sideSectors(long len)
    {
        if(len < 320 * 1024)
            return len / 512;
        else
            return len / 1024;
    }

    private int stdGeometrySectors(long len)
    {
        long sideSects = sideSectors(len);
        int baseTracks = 80;
        if(sideSects < 720)
            baseTracks = 40;
        for(int i = 0; i < 4; i++) {
            if(((int)sideSects % (baseTracks + i)) == 0)
                return (int)sideSects / (baseTracks + i);
        }
        return -1;
    }

    private int getSectors()
    {
        String type = (String)imageType.getSelectedItem();
        if(stdGeometry.isEnabled() && stdGeometry.isSelected())
            return stdGeometrySectors(fileSelectedLength);
        if(HDD.equals(type) || FLOPPY.equals(type))
            return textToInt(sectors.getText());
        else
            return -1;
    }

    private boolean forceSectors()
    {
        String type = (String)imageType.getSelectedItem();
        if(stdGeometry.isEnabled() && stdGeometry.isSelected())
            return true;
        if(HDD.equals(type) || FLOPPY.equals(type))
            return false;
        return true;
    }

    private int stdGeometryTracks(long len)
    {
        long sideSects = sideSectors(len);
        int baseTracks = 80;
        if(sideSects < 720)
            baseTracks = 40;
        for(int i = 0; i < 4; i++) {
            if(((int)sideSects % (baseTracks + i)) == 0)
                return baseTracks + i;
        }
        return -1;
    }

    private int getTracks()
    {
        String type = (String)imageType.getSelectedItem();
        if(!HDD.equals(type) && !FLOPPY.equals(type))
            return -1;
        if(stdGeometry.isEnabled() && stdGeometry.isSelected())
            return stdGeometryTracks(fileSelectedLength);
        if(directorySelected)
            return textToInt(tracks.getText());
        else if(fileSelected) {
            int trackBound = 256;
            if(HDD.equals(type))
                trackBound = 1024;
            if((fileSelectedLength % 512) != 0)
                return -2;
            long totalSectors = fileSelectedLength / 512;
            if((totalSectors % getSides()) != 0)
                return -2;
            totalSectors /= getSides();
            if((totalSectors % getSectors()) != 0)
                return -2;
            totalSectors /= getSectors();
            if(totalSectors < 1 || totalSectors > trackBound)
                return -2;
            return (int)totalSectors;
        } else {
            return -1;
        }
    }

    private boolean forceTracks()
    {
        String type = (String)imageType.getSelectedItem();
        if(!HDD.equals(type) && !FLOPPY.equals(type))
            return true;
        if(stdGeometry.isEnabled() && stdGeometry.isSelected())
            return true;
        if(directorySelected)
            return false;
        return true;
    }

    private void changeGeometry()
    {
        String type = (String)imageType.getSelectedItem();
        if(FLOPPY.equals(type) && fileSelected)
            stdGeometry.setEnabled(stdGeometryValid(fileSelectedLength));
        else
            stdGeometry.setEnabled(false);
        if(FLOPPY.equals(type) && !stdGeometry.isSelected())
            doublesided.setEnabled(true);
        else
            doublesided.setEnabled(false);

        if(forceSides()) {
            sides.setVisible(false);
            sidesFixed.setVisible(true);
            int x = getSides();
            if(x >= 0)
                sidesFixed.setText("" + x);
            else if(x == -1)
                sidesFixed.setText("N/A");
            else
                sidesFixed.setText("Unsatisfiable");
        } else {
            sides.setVisible(true);
            sidesFixed.setVisible(false);
        }

        if(forceSectors()) {
            sectors.setVisible(false);
            sectorsFixed.setVisible(true);
            int x = getSectors();
            if(x >= 0)
                sectorsFixed.setText("" + x);
            else if(x == -1)
                sectorsFixed.setText("N/A");
            else
                sectorsFixed.setText("Unsatisfiable");
        } else {
            sectors.setVisible(true);
            sectorsFixed.setVisible(false);
        }

        if(forceTracks()) {
            tracks.setVisible(false);
            tracksFixed.setVisible(true);
            int x = getTracks();
            if(x >= 0)
                tracksFixed.setText("" + x);
            else if(x == -1)
                tracksFixed.setText("N/A");
            else
                tracksFixed.setText("Unsatisfiable");
        } else {
            tracks.setVisible(true);
            tracksFixed.setVisible(false);
        }
    }
}
