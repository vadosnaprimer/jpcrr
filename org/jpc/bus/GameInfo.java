package org.jpc.bus;

public class GameInfo
{
    private String gameName;
    private String[] names;
    private String[] nicks;

    public void setGameName(String name)
    {
        gameName = name;
    }

    public String getGameName()
    {
        return gameName;
    }

    public int getNameCount()
    {
        return names.length;
    }

    public void insertName(int pos, String name, String nick)
    {
        String[] newNames = new String[names.length + 1];
        String[] newNicks = new String[nicks.length + 1];
        for(int i = 0; i < newNames.length; i++)
            if(i < pos) {
                newNames[i] = names[i];
                newNicks[i] = nicks[i];
            } else if(i == pos) {
                newNames[i] = name;
                newNicks[i] = nick;
            } else {
                newNames[i] = names[i - 1];
                newNicks[i] = nicks[i - 1];
            }
        names = newNames;
        nicks = newNicks;
    }

    public String getName(int pos)
    {
        return names[pos];
    }

    public String getNick(int pos)
    {
        return nicks[pos];
    }

    public String[][] rewriteExtraHeaders(String[][] old)
    {
         //Put fake header if none.
         if(old == null)
             old = new String[1][];

        //First count number of other headers.
        int headerCount = 0;
        for(String[] header : old) {
            boolean interesting = true;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                interesting = false;
            if(header[0].equals("AUTHORNICKS"))
                interesting = false;
            if(header[0].equals("AUTHORFULL"))
                interesting = false;
            if(header[0].equals("GAMENAME"))
                interesting = false;
            if(!interesting)
                continue;
            headerCount++;
        }

        //Then count number of headers required by authors.
        int cat1 = 0;
        int cat2 = 0;
        for(int i = 0; i < names.length; i++) {
            if(names[i] != null && nicks[i] == null)
               if(cat1++ == 0)
                   headerCount++;
            if(names[i] == null && nicks[i] != null)
               if(cat2++ == 0)
                   headerCount++;
            if(names[i] != null && nicks[i] != null)
               headerCount++;
        }
        if(!("".equals(gameName)))
            headerCount++;

        //All headers removed?
        if(headerCount == 0)
            return null;

        //Copy the other headers.
        String[][] newHeaders = new String[headerCount][];
        int i = 0;
        for(String[] header : old) {
            boolean interesting = true;
            if(header == null || header.length == 0)
                continue;
            if(header[0].equals("AUTHORS"))
                interesting = false;
            if(header[0].equals("AUTHORNICKS"))
                interesting = false;
            if(header[0].equals("AUTHORFULL"))
                interesting = false;
            if(header[0].equals("GAMENAME"))
                interesting = false;
            if(!interesting)
                continue;
            newHeaders[i++] = header;
        }

        //Write AUTHORS header.
        if(cat1 > 0) {
            String[] table = new String[cat1 + 1];
            newHeaders[i++] = table;
            table[0] = "AUTHORS";
            int j = 1;
            for(i = 0; i < names.length; i++)
                if(names[i] != null && nicks[i] == null)
                    table[j++] = names[i];
        }

        //Write AUTHORNICKS header.
        if(cat2 > 0) {
            String[] table = new String[cat2 + 1];
            newHeaders[i++] = table;
            table[0] = "AUTHORNICKS";
            int j = 1;
            for(i = 0; i < names.length; i++)
                if(names[i] == null && nicks[i] != null)
                    table[j++] = nicks[i];
        }

        //Write AUTHORFULL headers.
        for(i = 0; i < names.length; i++)
            if(names[i] != null && names[i] != null) {
                String[] table = new String[3];
                newHeaders[i++] = table;
                table[0] = "AUTHORFULL";
                table[1] = names[i];
                table[2] = nicks[i];
            }
        //Write GAMENAME header.
        if(!("".equals(gameName)))
            newHeaders[i++] = new String[]{"GAMENAME", gameName};

        return newHeaders;
    }

    public GameInfo()
    {
        this(null);
    }

    public GameInfo(String[][] headers)
    {
        gameName = "";
        names = new String[0];
        nicks = new String[0];
        if(headers == null)
            return;
        for(String[] line : headers) {
            if(line.length == 2 && "GAMENAME".equals(line[0]))
                gameName = line[1];
            if(line.length >= 2 && "AUTHORS".equals(line[0]))
                for(int i = 1; i < line.length; i++)
                    insertName(names.length, line[i], null);
            if(line.length >= 2 && "AUTHORNICKS".equals(line[0]))
                for(int i = 1; i < line.length; i++)
                    insertName(names.length, null, line[i]);
            if(line.length == 3 && "AUTHORFULL".equals(line[0]))
                insertName(names.length, line[1], line[2]);
        }
    }
}
