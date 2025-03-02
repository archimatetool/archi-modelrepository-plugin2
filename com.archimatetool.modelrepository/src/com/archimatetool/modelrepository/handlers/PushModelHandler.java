/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;
import com.archimatetool.modelrepository.workflows.PushModelWorkflow;


/**
 * Push model handler
 * 
 * @author Phillip Beauvoir
 */
public class PushModelHandler extends AbstractModelHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IArchiRepository repository = getActiveArchiRepository();
        
        if(repository != null) {
            IRepositoryWorkflow workflow = new PushModelWorkflow(HandlerUtil.getActiveWorkbenchWindowChecked(event), repository);
            if(workflow.canRun()) {
                workflow.run();
            }
        }
        
        return null;
    }
    
    @Override
    public boolean isEnabled() {
        return getActiveArchiRepository() != null;
    }
}
