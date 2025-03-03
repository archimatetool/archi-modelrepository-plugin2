/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.workflows.CreateRepoFromModelWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;


/**
 * Create a Repo from existing model handler
 * 
 * @author Phillip Beauvoir
 */
public class CreateRepoFromModelHandler extends AbstractModelHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IArchimateModel model = getActiveArchimateModel();
        
        if(model != null) {
            IRepositoryWorkflow workflow = new CreateRepoFromModelWorkflow(HandlerUtil.getActiveWorkbenchWindowChecked(event), model);
            if(workflow.canRun()) {
                workflow.run();
            }
        }
        
        return null;
    }
    
    @Override
    public boolean isEnabled() {
        IArchimateModel model = getActiveArchimateModel();
        return model != null && !RepoUtils.isModelInArchiRepository(model); // Model is *not* in repository
    }
}
