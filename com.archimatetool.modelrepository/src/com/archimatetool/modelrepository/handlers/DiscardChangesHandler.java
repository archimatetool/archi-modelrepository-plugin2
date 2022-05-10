/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.modelrepository.actions.DiscardChangesAction;
import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * Discard changes handler
 * 
 * @author Phillip Beauvoir
 */
public class DiscardChangesHandler extends AbstractModelHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IArchiRepository repository = getActiveArchiRepository();
        if(repository != null) {
            DiscardChangesAction action = new DiscardChangesAction(HandlerUtil.getActiveWorkbenchWindowChecked(event), repository);
            action.run();
        }
        
        return null;
    }
    
    @Override
    public boolean isEnabled() {
        return getActiveArchiRepository() != null;
    }
}
