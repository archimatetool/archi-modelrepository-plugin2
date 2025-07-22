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
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;
import com.archimatetool.modelrepository.workflows.RestoreCommitWorkflow;

/**
 * Restore to a particular commit
 */
public class RestoreCommitAction extends AbstractRepositoryAction {
    
    private RevCommit revCommit;
    private IRepositoryWorkflow workflow;
	
    public RestoreCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_SYNCED));
        setText(Messages.RestoreCommitAction_0);
        setToolTipText(getText());
    }

    public void setCommit(IArchiRepository archiRepository, RevCommit commit) {
        if(revCommit != commit) {
            revCommit = commit;
            workflow = archiRepository != null && revCommit != null ? new RestoreCommitWorkflow(workbenchWindow, archiRepository, revCommit) : null;
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
