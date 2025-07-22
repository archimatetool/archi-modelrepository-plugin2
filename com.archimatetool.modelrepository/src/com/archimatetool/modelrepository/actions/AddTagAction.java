/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.workflows.AddTagWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Add a Tag
 */
public class AddTagAction extends AbstractRepositoryAction {
    
    private RevCommit revCommit;
    private IRepositoryWorkflow workflow;
	
    public AddTagAction(IWorkbenchWindow window, String text) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_NEW_TAG));
        setText(text);
        setToolTipText(getText());
    }

    public void setCommit(IArchiRepository archiRepository, RevCommit commit) {
        if(this.revCommit != commit) {
            this.revCommit = commit;
            workflow = revCommit != null ? new AddTagWorkflow(workbenchWindow, archiRepository, revCommit) : null;
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
