/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.workflows.AddBranchWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Add a Branch
 */
public class AddBranchAction extends AbstractRepositoryAction {
    
    private ObjectId objectId;
    private IRepositoryWorkflow workflow;
	
    public AddBranchAction(IWorkbenchWindow window, String text) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_NEW_BRANCH));
        setText(text);
        setToolTipText(getText());
    }

    public void setObjectId(IArchiRepository archiRepository, ObjectId objectId) {
        if(this.objectId != objectId) {
            this.objectId = objectId;
            workflow = objectId != null ? new AddBranchWorkflow(workbenchWindow, archiRepository, objectId) : null;
            update();
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
