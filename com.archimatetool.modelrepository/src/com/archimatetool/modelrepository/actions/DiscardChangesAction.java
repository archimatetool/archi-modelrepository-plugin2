/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.workflows.DiscardChangesWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Discard Uncommitted Changes Action
 * 
 * @author Phillip Beauvoir
 */
public class DiscardChangesAction extends AbstractRepositoryAction {
    
    public DiscardChangesAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_ABORT));
        setText(Messages.DiscardChangesAction_0);
        setToolTipText(Messages.DiscardChangesAction_0);
    }

    @Override
    public void run() {
        IRepositoryWorkflow workflow = new DiscardChangesWorkflow(workbenchWindow, archiRepository);
        if(workflow.canRun()) {
            workflow.run();
        }
    }
}
