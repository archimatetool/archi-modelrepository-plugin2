/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;

/**
 * A Runnable that can throw an Exception
 * 
 * @author Phillip Beauvoir
 */
public interface RunnableRequest {
    
    void run(IProgressMonitor monitor) throws Exception;
    
    /**
     * Run the Runnable, catching any Exceptions and re-throwing them
     */
    static void run(IRunnableContext context, RunnableRequest runnable, boolean fork) throws Exception {
        Exception[] exception = new Exception[1];
        
        try {
            context.run(fork, true, monitor -> {
                try {
                    runnable.run(monitor);
                }
                catch(Exception ex) {
                    exception[0] = ex;
                }
            });
        }
        catch(InvocationTargetException ex) {
            exception[0] = new Exception(ex.getTargetException()); // we want the target exception
        }
        catch(InterruptedException ex) {
            exception[0] = ex;
        }
        
        if(exception[0] != null) {
            throw exception[0];
        }
    }
}