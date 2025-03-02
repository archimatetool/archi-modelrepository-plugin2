/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.views.history.HistoryView;

/**
 * Show in History action
 * 
 * @author Phillip Beauvoir
 */
public class ShowInHistoryAction extends AbstractRepositoryAction {
    
    public ShowInHistoryAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_HISTORY_VIEW));
        setText(Messages.ShowInHistoryAction_0);
        setToolTipText(Messages.ShowInHistoryAction_0);
    }
    
    @Override
    public void run() {
        ViewManager.showViewPart(HistoryView.ID, false);
    }
}
