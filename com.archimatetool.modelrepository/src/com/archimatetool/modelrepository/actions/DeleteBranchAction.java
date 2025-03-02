/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.workflows.DeleteBranchWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Delete Branch Action
 * 
 * @author Phillip Beauvoir
 */
public class DeleteBranchAction extends AbstractRepositoryAction {
    
    private BranchInfo selectedBranchInfo;
    private IRepositoryWorkflow workflow;
    
    public DeleteBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE));
        setText(Messages.DeleteBranchAction_0);
        setToolTipText(getText());
    }

    public void setBranch(BranchInfo branchInfo) {
        if(selectedBranchInfo != branchInfo) {
            selectedBranchInfo = branchInfo;
            workflow = branchInfo != null ? new DeleteBranchWorkflow(workbenchWindow, branchInfo) : null;
            setEnabled(shouldBeEnabled());
        }
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
