/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
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
    public void run() {
        // If switched branch Ref == current HEAD Ref (i.e current branch and switched branch are same Ref) just switch branch
        if(currentBranchInfo.isRefAtHead()) {
            switchBranch(currentBranchInfo);
            notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
            return;
        }
        
        // Check if the model is open and needs saving
        if(!checkModelNeedsSaving()) {
            return;
        }
        
        // Check if there are uncommitted changes
        if(!checkIfCommitNeeded()) {
            return;
        }

        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel(false);

        // Switch branch
        switchBranch(currentBranchInfo);
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
    }
    
    private void switchBranch(BranchInfo branchInfo) {
        logger.info("Switching branch to: " + branchInfo.getShortName()); //$NON-NLS-1$
        
        try(Git git = Git.open(archiRepository.getWorkingFolder())) {
            // If the branch is local just checkout
            if(branchInfo.isLocal()) {
                git.checkout()
                   .setName(branchInfo.getFullName())
                   .call();
            }
            // If the branch is remote and has no local ref we need to create the local branch and checkout that
            else if(branchInfo.isRemote() && !branchInfo.hasLocalRef()) {
                git.checkout()
                   .setName(branchInfo.getShortName())
                   .setCreateBranch(true)
                   .setStartPoint(branchInfo.getFullName())
                   .call();
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Switch Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.SwitchBranchWorklow_0, ex);
        }
    }
    
    @Override
    public boolean canRun() {
        return currentBranchInfo != null && !currentBranchInfo.isCurrentBranch() && super.canRun();
    }
}
