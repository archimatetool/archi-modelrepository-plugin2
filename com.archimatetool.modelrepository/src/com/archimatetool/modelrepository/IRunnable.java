package com.archimatetool.modelrepository;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;

/**
 * Copy of Archi's IRunnable
 * There are changes in Archi's IRunnable in Archi version 5.8.0 that we want to use here so, until coArchi2 
 * requires a minuimum version of Archi 5.8.0 we'll use this copy.
 */
public interface IRunnable {
    
    void run(IProgressMonitor monitor) throws Exception;
    
    /**
     * Run the Runnable, catching any Exceptions and re-throwing them
     * @param context The context which is typically a ProgressMonitorDialog or Job, or WizardDialog
     * @param fork if true the runnable should be run in a separate thread and false to run in the same thread
     * @param cancelable <code>true</code> to enable the cancelation, and <code>false</code> to make the operation uncancellable
     * @param runnable The IRunnable to run
     * @throws Exception
     * @since 5.8.0
     */
    static void run(IRunnableContext context, boolean fork, boolean cancelable, IRunnable runnable) throws Exception {
        // The possible exception thrown by runnable.run(monitor)
        AtomicReference<Exception> exception = new AtomicReference<>();
        
        try {
            context.run(fork, cancelable, monitor -> {
                try {
                    runnable.run(monitor);
                }
                catch(Exception ex) {
                    exception.set(ex); // store this...
                }
            });
        }
        catch(InvocationTargetException ex) {
            throw new Exception(ex.getTargetException()); // we want the target exception
        }
        
        if(exception.get() != null) { // ...and throw it
            throw exception.get();
        }
    }
}