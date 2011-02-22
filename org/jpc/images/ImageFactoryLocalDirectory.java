package org.jpc.images;

import java.io.*;
import java.util.*;
import org.jpc.jrsr.UnicodeOutputStream;
import org.jpc.jrsr.UTF8OutputStream;
import org.jpc.jrsr.UnicodeInputStream;
import org.jpc.jrsr.UTF8InputStream;
import static org.jpc.Misc.nextParseLine;

public class ImageFactoryLocalDirectory implements ImageFactoryBase
{
    private ImageOffer[] offers;
    private Map<String, PrivData> cache;
    private String directoryPrefix;
    private long hits;
    private long misses;

    class PrivData
    {
        String fullPath;
        long fileTS;
        long fileSize;
        ImageID id;
        BaseImage.Type type;
    };

    public ImageFactoryLocalDirectory(String directory) throws IOException
    {
        long ts1 = System.currentTimeMillis();
        cache = new HashMap<String, PrivData>();
        directoryPrefix = directory;
        File d = new File(directory);
        if(!d.isDirectory())
            throw new IOException("'" + directory + "' is not an directory");
        loadCache(d);
        scanDirectory(d);
        saveCache(d);
        long ts2 = System.currentTimeMillis();
        System.err.println("Loaded " + (hits + misses) + " images (" + hits + " cache hits, " + misses + " cache " +
            "misses) in " + (ts2 - ts1) + "ms.");
    }

    private void scanDirectory(File directory)
    {
        List<ImageOffer> offs = new ArrayList<ImageOffer>();
        scanDirectory("", "", directory, offs);
        ImageOffer[] x = new ImageOffer[0];
        offers = offs.toArray(x);
    }

    private void scanDirectory(String prefix, String pathPrefix, File directory, List<ImageOffer> offs)
    {
        File[] fileList = directory.listFiles();
        for(int i = 0; i < fileList.length; i++) {
            File imageFile = fileList[i];
            String fileName = directoryPrefix + pathPrefix + imageFile.getName();
            String imageName = prefix + imageFile.getName();
            try {
                if(imageFile.isDirectory())
                    scanDirectory(prefix + imageFile.getName() + "/",
                        pathPrefix + imageFile.getName() + File.separator, imageFile, offs);
                else if(imageFile.isFile())
                    handleFile(imageName, imageFile, offs);
            } catch(IOException e) {
                System.err.println("Can't load \"" + imageFile.getName() + "\": " + e.getMessage());
            }
        }
    }

    private void handleFile(String imageName, File file, List<ImageOffer> offs) throws IOException
    {
        if(imageName.equals(".cache"))
            return;
        if(cache.containsKey(imageName)) {
            PrivData data = cache.get(imageName);
            data.fullPath = file.getAbsolutePath();
            if(data.fileTS == file.lastModified() && data.fileSize == file.length()) {
                System.err.println("Loaded '" + imageName + "' " + data.type + "/" + data.id + " (cache hit)");
                hits++;
                //Assume this is correct.
                ImageOffer o = new ImageOffer();
                o.from = this;
                o.name = imageName;
                o.id = data.id;
                o.type = data.type;
                o.privdata = data;
                offs.add(o);
                return;
            }
        }

        //Parse the image info.
        BaseImage.Type[] t = new BaseImage.Type[1];
        ImageID id = JPCRRStandardImageDecoder.readIDFromImage(file, t);
        System.err.println("Loaded '" + imageName + "' " + t[0] + "/" + id + " (cache miss)");
        misses++;

        //Fill the privdata and offer.
        PrivData newPrivData = new PrivData();
        newPrivData.fullPath = file.getAbsolutePath();
        newPrivData.fileTS = file.lastModified();
        newPrivData.fileSize = file.length();
        newPrivData.id = id;
        newPrivData.type = t[0];
        cache.put(imageName, newPrivData);

        ImageOffer o = new ImageOffer();
        o.from = this;
        o.name = imageName;
        o.id = id;
        o.type = t[0];
        o.privdata = newPrivData;
        offs.add(o);
    }

    private void loadCache(File directory)
    {
        try {
            UnicodeInputStream in = new UTF8InputStream(new FileInputStream(directory.getAbsolutePath() +
                File.separator + ".cache"), false);
            String[] r;
            while((r = nextParseLine(in)) != null) {
                try {
                    String name = r[0];
                    long ts = Long.parseLong(r[1]);
                    long size = Long.parseLong(r[2]);
                    BaseImage.Type type = null;
                    if(r[3].equals("FLOPPY"))
                        type = BaseImage.Type.FLOPPY;
                    if(r[3].equals("HARDDRIVE"))
                        type = BaseImage.Type.HARDDRIVE;
                    if(r[3].equals("CDROM"))
                        type = BaseImage.Type.CDROM;
                    if(r[3].equals("BIOS"))
                        type = BaseImage.Type.BIOS;
                    ImageID id = new ImageID(r[4]);

                    PrivData p = new PrivData();
                    p.fullPath = null; //Filled later.
                    p.fileTS = ts;
                    p.fileSize = size;
                    p.type = type;
                    p.id = id;
                    cache.put(name, p);
                } catch(Exception e) {
                    //Skip bad entry.
                }
            //TODO
            }
            in.close();
        } catch(Exception e) {
            //This is cache, we don't care if it fails to load.
        }
    }

    private void saveCache(File directory)
    {
        try {
            UnicodeOutputStream out = new UTF8OutputStream(new FileOutputStream(directory.getAbsolutePath() +
                File.separator + ".cache"), false);
            for(Map.Entry<String, PrivData> e : cache.entrySet())
                out.encodeLine(e.getKey(), e.getValue().fileTS, e.getValue().fileSize, e.getValue().type,
                    e.getValue().id);
            out.close();
        } catch(Exception e) {
            e.printStackTrace();
            //This is cache, we don't care if it fails to save.
        }
    }

    public ImageOffer[] getOffers()
    {
        return offers;
    }

    public BaseImage lookup(ImageID id) throws IOException
    {
        for(ImageOffer o : offers)
            if(o.id.equals(id))
                return JPCRRStandardImageDecoder.readImage(((PrivData)o.privdata).fullPath);
        return null;
    }
};
