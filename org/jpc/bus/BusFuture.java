package org.jpc.bus;

public class BusFuture
{
    //The data.
    private Object[] returnValue;
    private boolean completed;

    //Returns true if data is availalble, false otherwise.
    public boolean isComplete()
    {
        return completed;
    }

    //Suspends current thread until data is available (and return it)
    public synchronized Object[] waitComplete() throws InterruptedException
    {
        while(!completed)
            wait();
        return returnValue;
    }

    //Return the data. Returns null if no data is yet available.
    public Object[] getData()
    {
        if(!completed)
             return null;
        return returnValue;
    }

    //The data is available, set it and wake waiters.
    protected synchronized void setData(Object[] data)
    {
        completed = true;
        returnValue = data;
        notifyAll();
    }
}
