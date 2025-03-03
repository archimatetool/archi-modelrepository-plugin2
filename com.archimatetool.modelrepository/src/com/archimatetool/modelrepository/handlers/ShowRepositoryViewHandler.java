/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.views.repositories.ModelRepositoryView;


/**
 * Simply show this View in all cases
 * 
 * @author Phillip Beauvoir
 */
public class ShowRepositoryViewHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // activate = false to keep originating part in focus so we can update based on current selection
        ViewManager.toggleViewPart(ModelRepositoryView.ID, false);
        return null;
    }

}
