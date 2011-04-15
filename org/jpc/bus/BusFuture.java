package org.jpc.bus;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.lang.reflect.Method;

public class BusFuture
{
    //The data.
    private Object[] returnValue;
    private boolean completed;
    private List<Callback> callbacks;

    class Callback
    {
        Object targetObject;
        Method targetMethod;
    }

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

    //Register a callback. Note that the callback will run in unpredictable thread. Callback will take the
    //future object as a parameter. If you call this after future is available, it will call the callback right
    //there and then.
    public synchronized void addCallback(Object target, String methodName) throws NoSuchMethodException
    {
        Callback c;
        boolean complete;
        synchronized(this) {
            c = new Callback();
            c.targetObject = target;
            c.targetMethod = target.getClass().getDeclaredMethod(methodName, BusFuture.class);
            //Did we make it in time? If not, do not add the callback but execute it immediately.
            complete = completed;
            if(!complete) {
                if(callbacks == null)
                  callbacks = new LinkedList<Callback>();
                callbacks.add(c);
            }
        }
        if(complete) {
            //We raced. Run the callback instantly.
            try {
                c.targetMethod.invoke(c.targetObject, this);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    //The data is available, set it and wake waiters.
    protected void setData(Object[] data)
    {
        //We don't want callbacks to be locked.
        Iterator<Callback> callbackI;
        Callback cb;
        synchronized(this) {
            completed = true;
            returnValue = data;
            notifyAll();
            if(callbacks == null)
                return;
            List<Callback> callbacks2 = callbacks;
            callbacks = null;
            callbackI = callbacks2.iterator();
        }
        while(true) {
            synchronized(this) {
                if(!callbackI.hasNext())
                    return;
                cb = callbackI.next();
            }
            try {
                cb.targetMethod.invoke(cb.targetObject, this);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
