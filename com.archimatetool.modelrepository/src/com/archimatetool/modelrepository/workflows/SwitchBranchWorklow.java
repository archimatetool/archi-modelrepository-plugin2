/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Switch and checkout Branch
 */
public class SwitchBranchWorklow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(SwitchBranchWorklow.class.getName());
    
    private BranchInfo currentBranchInfo;
	
    public SwitchBranchWorklow(IWorkbenchWindow workbenchWindow, BranchInfo currentBranchInfo) {
        super(workbenchWindow, new ArchiRepository(currentBranchInfo.getWorkingFolder()));
        this.currentBranchInfo = currentBranchInfo;
    }

    @Override
    protected void run(GitUtils utils) {
        // If switched branch Ref == current HEAD Ref (i.e current branch and switched branch are same Ref) just switch branch
        if(currentBranchInfo.isRefAtHead()) {
            switchBranch(utils, currentBranchInfo);
            notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
            return;
        }
        
        // Check if the model is open and needs saving
        if(!checkModelNeedsSaving()) {
            return;
        }
        
        // Check if there are uncommitted changes
        if(!checkIfCommitNeeded(utils, true)) {
            return;
        }

        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel(false).orElse(null);

        // Switch branch
        switchBranch(utils, currentBranchInfo);
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
    }
    
    private void switchBranch(GitUtils utils, BranchInfo branchInfo) {
        logger.info("Switching branch to: " + branchInfo.getShortName()); //$NON-NLS-1$
        
        try {
            // If the branch is local just checkout
            if(branchInfo.isLocal()) {
                utils.checkout()
                     .setName(branchInfo.getFullName())
                     .call();
            }
            // If the branch is remote and has no local ref we need to create the local branch and checkout that
            else if(branchInfo.isRemote() && !branchInfo.hasLocalRef()) {
                utils.checkout()
                     .setName(branchInfo.getShortName())
                     .setCreateBranch(true)
                     .setStartPoint(branchInfo.getFullName())
                     .call();
            }
        }
        catch(GitAPIException ex) {
            logger.log(Level.SEVERE, "Switch Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.SwitchBranchWorklow_0, ex);
        }
    }
    
    @Override
    public boolean canRun() {
        return currentBranchInfo != null && !currentBranchInfo.isCurrentBranch() && super.canRun();
    }
}
