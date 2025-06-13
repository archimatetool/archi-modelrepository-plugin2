/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.views.tags.TagsView;


/**
 * This extends AbstractModelHandler so it is active only when in a repository context
 * 
 * @author Phillip Beauvoir
 */
public class ShowInTagsViewHandler extends AbstractModelHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // activate = false to keep originating part in focus so we can update based on current selection
        ViewManager.showViewPart(TagsView.ID, false);
        return null;
    }

    @Override
    public boolean isEnabled() {
        return getActiveArchiRepository() != null;
    }

}
