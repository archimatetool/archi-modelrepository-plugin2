/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.modelrepository.actions.CloneModelAction;


/**
 * Clone model handler
 * 
 * @author Phillip Beauvoir
 */
public class CloneModelHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        CloneModelAction action = new CloneModelAction(HandlerUtil.getActiveWorkbenchWindowChecked(event));
        action.run();
        
        return null;
    }
    
}
