package org.jpc.bus;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import org.jpc.Misc;
import org.jpc.pluginsbase.Plugins;
import static org.jpc.Misc.errorDialog;
import static org.jpc.Misc.parseString;
import static org.jpc.Misc.castToByte;
import static org.jpc.Misc.castToShort;
import static org.jpc.Misc.castToInt;
import static org.jpc.Misc.castToLong;
import static org.jpc.Misc.castToString;
import static org.jpc.Misc.messageForException;

public class Bus
{
    private class ObjectMethod
    {
        private final static int KIND_COMMAND = 0;
        private final static int KIND_EVENT = 1;
        private final static int KIND_SHUTDOWN = 2;

        ObjectMethod(Object target, String method, int kind) throws Exception
        {
            obj = target;
            Class<?> oclass = obj.getClass();
            if(kind == KIND_COMMAND)
                meth = oclass.getMethod(method, BusRequest.class, String.class, Object[].class);
            else if(kind == KIND_EVENT)
                meth = oclass.getMethod(method, String.class, Object[].class);
            else if(kind == KIND_SHUTDOWN)
                meth = oclass.getMethod(method);
        }

        void callCommand(BusRequest req, String cmd, Object[] args) throws InvocationTargetException,
            NoSuchMethodException
        {
            try {
                meth.invoke(obj, req, cmd, args);
            } catch(IllegalAccessException e) {
                errorDialog(e, "Error invoking command handler", null, "Ignore");
                throw new NoSuchMethodException("Error invoking command handler: " + messageForException(e, true));
            }
        }

        void callEvent(String cmd, Object[] args) throws InvocationTargetException
        {
            try {
                meth.invoke(obj, cmd, args);
            } catch(IllegalAccessException e) {
                errorDialog(e, "Error invoking event handler", null, "Ignore");
            }
        }

        boolean callShutdown()
        {
            try {
                return (Boolean)meth.invoke(obj);
            } catch(InvocationTargetException e) {
                errorDialog(e.getCause(), "Error in shutdown method", null, "Ignore");
            } catch(IllegalAccessException e) {
                errorDialog(e, "Error invoking shutdown method", null, "Ignore");
            }
            return false;
        }

        Object obj;
        Method meth;
    };

    private Map<String, ObjectMethod> commands;
    private Map<ObjectMethod, String> events;
    private Map<Integer, Object> plugins;
    private Set<ObjectMethod> shutdownHandlers;
    private BusInternalCommands internal;
    private DisplaySupport hudSupport;
    private ImageService images;
    boolean killed;
    boolean shutdownInProgress;
    int nextPluginIndex;

    //Execute a command synchronously.
    //@command is the command to execute.
    //@args is array of arguments for the command.
    public Object[] executeCommandSynchronous(String command, Object[] args) throws NoSuchMethodException,
        InvocationTargetException
    {
        BusFuture f = executeCommandAsynchronous(command, args);
        while(f != null)
             try {
                 return f.waitComplete();
             } catch(InterruptedException e) {
             }
        return null;
    }

    //Launch command but don't wait for it to complete.
    //@command is the command to execute.
    //@args is array of arguments for the command.
    public BusFuture executeCommandAsynchronous(String command, Object[] args) throws NoSuchMethodException,
        InvocationTargetException
    {
        ObjectMethod m = commands.get(command);
        if(m == null) {
            System.err.println("Unknown method: '" + command + "'.");
            throw new NoSuchMethodException("Bad bus command '" + command + "'");
        }
        BusFuture f = new BusFuture();
        m.callCommand(new BusRequest(f), command, args);
        return f;
    }

    //Invoke event handlers for an event.
    //@event is the event to invoke.
    //@args is array of arguments for the event.
    public void invokeEvent(String event, Object[] args)
    {
        for(Map.Entry<ObjectMethod, String> m : events.entrySet())
            if(m.getValue().equals(event))
                try {
                    m.getKey().callEvent(event, args);
                } catch(Exception e) {
                }
    }

    //Set Command handler. The prototype is void func(BusRequest req, String cmd, Object[] args).
    public synchronized void setCommandHandler(Object target, String method, String command)
    {
        try {
            commands.put(command, new ObjectMethod(target, method, ObjectMethod.KIND_COMMAND));
        } catch(Exception e) {
            errorDialog(e, "Can't find handler for command binding", null, "Ignore");
        }
    }

    //Set Event handler. The prototype is void func(BusRequest req, String cmd, Object[] args).
    //req will be null.
    public synchronized void setEventHandler(Object target, String method, String event)
    {
        try {
            events.put(new ObjectMethod(target, method, ObjectMethod.KIND_EVENT), event);
        } catch(Exception e) {
            errorDialog(e, "Can't find handler for event binding", null, "Ignore");
        }
    }

    //Shutdown events are even more special, the prototype is boolean func()
    public synchronized void setShutdownHandler(Object target, String method)
    {
        try {
            shutdownHandlers.add(new ObjectMethod(target, method, ObjectMethod.KIND_SHUTDOWN));
        } catch(Exception e) {
            errorDialog(e, "Can't find handler for shutdown binding", null, "Ignore");
        }
    }

    //Detach object from bus (unregistering all command and event handlers it has)
    public synchronized void detachObject(Object target)
    {
        boolean allRemoved = false;
        while(!allRemoved) {
            allRemoved = true;
            for(Map.Entry<String, ObjectMethod> m : commands.entrySet())
                if(m.getValue().obj == target) {
                    allRemoved = false;
                    commands.remove(m.getKey());
                    break;
                }
            for(Map.Entry<ObjectMethod, String> m : events.entrySet())
                if(m.getKey().obj == target) {
                    allRemoved = false;
                    events.remove(m.getKey());
                    break;
                }
            for(ObjectMethod m : shutdownHandlers)
                if(m.obj == target) {
                    allRemoved = false;
                    shutdownHandlers.remove(m);
                    break;
                }
        }
    }

    //Execute string command.
    public String[] executeStringCommand(String fullCommand)
    {
        String[] parsed;
        if(fullCommand == null || fullCommand.equals(""))
            return new String[]{"Empty command"};
        try {
            parsed = parseString(fullCommand);
        } catch(Exception e) {
            return new String[]{"Error parsing command syntax", messageForException(e, true) };
        }
        if(parsed == null)
            return new String[]{"Empty command"};
        Object[] args = new Object[parsed.length - 1];
        for(int i = 0; i < args.length; i++)
            args[i] = parsed[i + 1];
        Object[] ret;
        try {
            ret = executeCommandSynchronous(parsed[0], args);
        } catch(InvocationTargetException e) {
            return new String[]{parsed[0] + ": Error in command handler", messageForException(e, true) };
        } catch(NoSuchMethodException e) {
            errorDialog(e, "Unknown command " + parsed[0], null, "Ignore");
            return new String[]{parsed[0] + ": Unknown command" };
        }
        if(ret == null)
            return null;
        String[] ret2 = new String[ret.length];
        for(int i = 0; i < ret.length; i++)
            ret2[i] = ret[i].toString();
        return ret2;
    }

    //Call shutdown handlers on some object and unregister it if successful.
    private boolean callShutdownHandlers(Object target)
    {
        boolean succeeded = true;
        System.err.println("Shutting down " + target.getClass().getName() + "#" + System.identityHashCode(target) +
            "...");
        for(ObjectMethod m : shutdownHandlers)
            if(m.obj == target)
                if(!m.callShutdown())
                    succeeded = false;
        if(succeeded) {
            detachObject(target);
            System.err.println("Shut down " + target.getClass().getName() + "#" + System.identityHashCode(target) +
                ".");
        } else {
            System.err.println(target.getClass().getName() + "#" + System.identityHashCode(target) +
                " doesn't want to shut down.");
        }
        return succeeded;
    }

    //Shut down the emulator gracefully. Can be called from any of plugin main threads or from dedicated
    //shutdown thread.
    public void shutdownEmulator()
    {
        shutdownEmulator(true);
    }

    private void shutdownEmulator(boolean manual)
    {
        shutdownInProgress = true;
        Iterator<ObjectMethod> i = null;
        while(!shutdownHandlers.isEmpty()) {
            if(i == null)
                i = shutdownHandlers.iterator();
            ObjectMethod m = i.next();
            if(callShutdownHandlers(m.obj))
                i = null;
        }
        killed = true;
        if(manual)
            System.exit(0);
    }

    //Do quick ungraceful shutdown.
    public void killEmulator()
    {
        killed = true;
        System.exit(1);
    }

    public boolean isShuttingDown()
    {
        return shutdownInProgress;
    }

    public void loadPlugin(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, InvocationTargetException
    {
        if(args == null || args.length < 1)
            throw new IllegalArgumentException("Command needs at least one argument");

        String pluginClassName = null;
        Object plugin = null;
        Constructor cc = null;
        String[] arguments = null;
        Class<?> pluginc = null;
        int i = 0;

        try {
            pluginClassName = castToString(args[0]);
            if(args.length > 1)
                arguments = new String[args.length - 1];
            for(i = 1; i < args.length; i++)
                    arguments[i - 1] = castToString(args[i]);
        } catch(Exception e) {
            throw new IllegalArgumentException("Error parsing argument #" + (i + 1) + ": " + messageForException(e, true));
        }

        try {
            pluginc = Class.forName(pluginClassName);
        } catch(Exception e) {
            throw new IllegalArgumentException("Plugin '" + pluginClassName + "' doesn't exist");
        }

        if(arguments != null)
            try {
                cc = pluginc.getConstructor(Bus.class, String[].class);
            } catch(Exception e) {
                throw new IllegalArgumentException("Plugin '" + pluginClassName + "' doesn't take arguments");
            }
        else
            try {
                cc = pluginc.getConstructor(Bus.class);
            } catch(Exception e) {
                throw new IllegalArgumentException("Plugin '" + pluginClassName + "' takes arguments");
            }

        try {
            if(arguments != null)
                plugin = cc.newInstance(this, arguments);
            else
                plugin = cc.newInstance(this);
        } catch(InvocationTargetException e) {
            Throwable e2 = e.getCause();
            errorDialog(e2, "Error in plugin constructor", null, "Dismiss");
            throw new InvocationTargetException(e2, "Error in plugin constructor: " + messageForException(e, true));
        } catch(Exception e) {
            errorDialog(e, "Failed invoke plugin constructor", null, "Dismiss");
            throw new IllegalArgumentException("Failed to invoke plugin constructor: " + messageForException(e, true));
        }

        plugins.put(new Integer(nextPluginIndex++), plugin);
        System.err.println("Plugin " + pluginClassName + "#" + System.identityHashCode(plugin) +
            " loaded as plugin #" + (nextPluginIndex - 1) + ".");
        req.doReturnL(nextPluginIndex - 1);
    }

    public void unloadPlugin(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException, InvocationTargetException
    {
        if(args == null || args.length != 1)
            throw new IllegalArgumentException("Command needs an argument");
        Integer i = null;
        Object o;
        try {
            i =  new Integer(castToInt(args[0]));
            o = plugins.get(i);
            if(o == null)
                throw new Exception("No plugin #" + i + " found.");
        } catch(Exception e) {
            throw new IllegalArgumentException("Error looking up plugin to unload: " + messageForException(e, true));
        }
        try {
            if(callShutdownHandlers(o)) {
                plugins.remove(i);
                req.doReturn();
            } else
                throw new IllegalArgumentException("Plugin does not want to shut down.");
        } catch(Exception e) {
            throw new InvocationTargetException(e, "Error trying to shut down plugin: " + messageForException(e, true));
        }
    }

    public void listPlugins(BusRequest req, String cmd, Object[] args)
    {
        for(Map.Entry<Integer, Object> x : plugins.entrySet())
            System.err.println("Plugin #" + x.getKey() + ": " + x.getValue().getClass().getName());
        req.doReturn();
    }


    public void doEmulatorExit(BusRequest req, String cmd, Object[] args)
    {
        System.exit(0);
    }

    public void doEmulatorKill(BusRequest req, String cmd, Object[] args)
    {
        String fileName = "crashdump-" + System.currentTimeMillis() + ".text";
        try {
            OutputStream o = new FileOutputStream(fileName);
            Misc.doCrashDump(o);
            o.close();
            System.err.println("Crash dump saved to '" + fileName + "'.");
            killEmulator();
        } catch(Exception e) {
            System.err.println("Failed to save crash dump to '" + fileName + "':" + messageForException(e, true));
        }
        req.doReturn();
    }

    public void getSetEmuName(BusRequest req, String cmd, Object[] args) throws IllegalArgumentException
    {
        String oldname = Misc.emuname;
        if(args == null || args.length > 1)
            throw new IllegalArgumentException("Command takes an optional argument");
        try {
            if(args.length == 1) {
                Misc.emuname = castToString(args[0]);
                if(Misc.emuname.equals("<delete>"))
                    Misc.emuname = null;
                invokeEvent("emuname-changed", new Object[]{Misc.emuname});
            }
        } catch(Exception e) {
            throw new IllegalArgumentException("Invalid name: " + messageForException(e, true));
        }
        if(oldname != null)
            req.doReturnL(oldname);
        else
            req.doReturn();
    }

    private class ShutdownHook extends Thread
    {
        public void run()
        {
            if(killed)
                return;   //Ungraceful shutdown (or all plugins already shut down).
            shutdownEmulator(false);
        }
    }

    public Bus()
    {
        commands = new HashMap<String, ObjectMethod>();
        events = new HashMap<ObjectMethod, String>();
        shutdownHandlers = new HashSet<ObjectMethod>();
        plugins = new HashMap<Integer, Object>();
        killed = false;
        shutdownInProgress = false;
        nextPluginIndex = 0;
        internal = new BusInternalCommands(this);
        hudSupport = new DisplaySupport(this);
        //images = new ImageService(this);
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        setCommandHandler(this, "loadPlugin", "load");
        setCommandHandler(this, "unloadPlugin", "unload");
        setCommandHandler(this, "listPlugins", "plugins");
        setCommandHandler(this, "doEmulatorKill", "kill");
        setCommandHandler(this, "doEmulatorExit", "exit");
        setCommandHandler(this, "getSetEmuName", "emuname");
        //TODO: Get rid of this.
        pluginManager = new Plugins();
        setCommandHandler(this, "getPluginManager", "get-plugin-manager");
    }

    //TODO: Get rid of this.
    Plugins pluginManager;

    public void getPluginManager(BusRequest req, String cmd, Object[] args)
    {
        req.doReturnL(pluginManager);
    }
}
