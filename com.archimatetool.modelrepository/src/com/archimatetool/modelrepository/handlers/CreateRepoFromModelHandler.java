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
import com.archimatetool.modelrepository.actions.CreateRepoFromModelAction;
import com.archimatetool.modelrepository.repository.RepoUtils;


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
            CreateRepoFromModelAction action = new CreateRepoFromModelAction(HandlerUtil.getActiveWorkbenchWindowChecked(event), model);
            action.run();
        }
        
        return null;
    }
    
    @Override
    public boolean isEnabled() {
        IArchimateModel model = getActiveArchimateModel();
        return model != null && !RepoUtils.isModelInArchiRepository(model);
    }
}
