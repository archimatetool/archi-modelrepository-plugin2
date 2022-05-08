/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * Delete model handler
 * 
 * @author Phillip Beauvoir
 */
public class DeleteModelHandler extends AbstractModelHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IArchiRepository repository = getActiveArchiRepository();
        if(repository != null) {
            DeleteModelAction action = new DeleteModelAction(HandlerUtil.getActiveWorkbenchWindowChecked(event), repository);
            action.run();
        }
        
        return null;
    }
    
}
