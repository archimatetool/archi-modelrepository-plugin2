/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;
import com.archimatetool.modelrepository.workflows.ResetToRemoteCommitWorkflow;

/**
 * Reset HEAD to the remote commit if there is one
 */
public class ResetToRemoteCommitAction extends AbstractRepositoryAction {
    
    private IRepositoryWorkflow workflow;
    
    public ResetToRemoteCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_RESET));
        setText(Messages.ResetToRemoteCommitAction_0);
        setToolTipText(Messages.ResetToRemoteCommitAction_0);
    }
    
    @Override
    public void setRepository(IArchiRepository archiRepository) {
        workflow = new ResetToRemoteCommitWorkflow(workbenchWindow, archiRepository);
        super.setRepository(archiRepository);
    }

    @Override
    public void run() {
        if(shouldBeEnabled()) {
            workflow.run();
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return workflow != null && workflow.canRun();
    }
}
