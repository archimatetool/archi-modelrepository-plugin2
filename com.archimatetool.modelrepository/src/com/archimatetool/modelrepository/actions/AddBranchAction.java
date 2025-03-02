/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.workflows.AddBranchWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Add a Branch
 */
public class AddBranchAction extends AbstractRepositoryAction {
    
    private BranchInfo selectedBranchInfo;
    private IRepositoryWorkflow workflow;
	
    public AddBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_NEW_BRANCH));
        setText(Messages.AddBranchAction_0);
        setToolTipText(getText());
    }

    public void setBranch(BranchInfo branchInfo) {
        if(selectedBranchInfo != branchInfo) {
            selectedBranchInfo = branchInfo;
            workflow = branchInfo != null ? new AddBranchWorkflow(workbenchWindow, branchInfo) : null;
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
