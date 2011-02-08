package org.jpc.bus;

public class BusRequest
{
    private BusFuture future;

    protected BusRequest(BusFuture fut)
    {
        future = fut;
    }

    //Return nothing.
    public void doReturn()
    {
        future.setData(null);
    }

    //Return a value (use NULL to return nothing).
    public void doReturnA(Object[] ret)
    {
        future.setData(ret);
    }

    //Return a value (use NULL to return nothing).
    public void doReturnL(Object... ret)
    {
        future.setData(ret);
    }

}