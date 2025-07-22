/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.workflows.ExtractModelFromCommitWorkflow;
import com.archimatetool.modelrepository.workflows.IRepositoryWorkflow;

/**
 * Checkout a commit and extract the .archimate file from it
 */
public class ExtractModelFromCommitAction extends AbstractRepositoryAction {
    
    private RevCommit revCommit;
    private IRepositoryWorkflow workflow;
    
    public ExtractModelFromCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_MODELS));
        setText(Messages.ExtractModelFromCommitAction_0);
        setToolTipText(getText());
    }
    
    public void setCommit(IArchiRepository archiRepository, RevCommit commit) {
        if(revCommit != commit) {
            revCommit = commit;
            workflow = archiRepository != null && revCommit != null ? new ExtractModelFromCommitWorkflow(workbenchWindow, archiRepository, revCommit) : null;
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
