/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.services.ViewManager;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.views.branches.BranchesView;

/**
 * Show In Branches View Action
 * 
 * @author Phillip Beauvoir
 */
public class ShowInBranchesViewAction extends AbstractModelAction {
    
    public ShowInBranchesViewAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_BRANCHES));
        setText(Messages.ShowInBranchesViewAction_0);
        setToolTipText(Messages.ShowInBranchesViewAction_1);
    }
    
    @Override
    public void run() {
        ViewManager.showViewPart(BranchesView.ID, false);
    }
}
