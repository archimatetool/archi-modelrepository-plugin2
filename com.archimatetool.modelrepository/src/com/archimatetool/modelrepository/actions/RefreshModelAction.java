/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;
import com.archimatetool.modelrepository.workflows.RefreshModelWorkflow;

/**
 * Refresh Model Action
 * 
 * @author Phillip Beauvoir
 */
public class RefreshModelAction extends AbstractRepositoryAction {
    
    public RefreshModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_REFRESH));
        setText(Messages.RefreshModelAction_0);
        setToolTipText(Messages.RefreshModelAction_0);
    }

    @Override
    public void run() {
        IRepositoryWorkflow workflow = new RefreshModelWorkflow(workbenchWindow, archiRepository);
        if(workflow.canRun()) {
            workflow.run();
        }
    }
}
