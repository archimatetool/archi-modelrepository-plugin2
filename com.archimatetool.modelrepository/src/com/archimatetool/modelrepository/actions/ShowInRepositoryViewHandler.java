/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.archimatetool.editor.actions.AbstractModelSelectionHandler;
import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryView;


/**
 * Show In Repository View Handler
 * 
 * @author Phillip Beauvoir
 */
public class ShowInRepositoryViewHandler extends AbstractModelSelectionHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ModelRepositoryView part = (ModelRepositoryView)ViewManager.showViewPart(ModelRepositoryView.ID, false);
        
        if(part != null && getActiveArchimateModel() != null) {
            part.selectObject(getActiveArchimateModel());
        }
        
        return null;
    }

    @Override
    public void updateState() {
        // Do nothing
    }
    
    @Override
    public boolean isEnabled() {
        return RepoUtils.isModelInArchiRepository(getActiveArchimateModel());
    }

}
