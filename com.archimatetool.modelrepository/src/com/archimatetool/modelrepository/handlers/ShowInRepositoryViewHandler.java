/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryView;


/**
 * Show In Repository View Handler
 * This extends AbstractModelHandler so it is active only when in a repository context
 * 
 * @author Phillip Beauvoir
 */
public class ShowInRepositoryViewHandler extends AbstractModelHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ModelRepositoryView part = (ModelRepositoryView)ViewManager.showViewPart(ModelRepositoryView.ID, false);
        
        // This is not really necessary as ModelRepositoryView synchronises model selections anyway
        if(part != null && getActiveArchimateModel() != null) {
            part.selectObject(getActiveArchimateModel());
        }
        
        return null;
    }
}
