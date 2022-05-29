/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Switch and checkout Branch
 * 
 * 1. 
 */
public class SwitchBranchAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(SwitchBranchAction.class.getName());
    
    private BranchInfo fBranchInfo;
	
    public SwitchBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_BRANCHES));
        setText(Messages.SwitchBranchAction_0);
        setToolTipText(Messages.SwitchBranchAction_0);
    }

    public void setBranch(BranchInfo branchInfo) {
        if(fBranchInfo != branchInfo) {
            fBranchInfo = branchInfo;
            setEnabled(shouldBeEnabled());
        }
    }
    
    @Override
    public void setRepository(IArchiRepository repository) {
        fBranchInfo = null;
        super.setRepository(repository);
    }

    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }
        
        // Keep a local reference in case a notification event from the UI changes the current branch selection
        BranchInfo branchInfo = fBranchInfo;
        
        logger.info("Switching branch to: " + branchInfo.getShortName()); //$NON-NLS-1$
        
        // If switched branch Ref == current HEAD Ref (i.e current branch and switched branch are same Ref) just switch branch
        if(branchInfo.isRefAtHead()) {
            switchBranch(branchInfo);
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

        OpenModelState modelState = closeModel(false);

        switchBranch(branchInfo);
        
        restoreModel(modelState);
        
        // Notify listeners last because a new UI selection will trigger an updated BranchInfo here
        notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
    }
    
    private boolean switchBranch(BranchInfo branchInfo) {
        logger.info("Switching branch to: " + branchInfo.getShortName()); //$NON-NLS-1$
        
        try(Git git = Git.open(getRepository().getWorkingFolder())) {
            // If the branch is local just checkout
            if(branchInfo.isLocal()) {
                git.checkout().setName(branchInfo.getFullName()).call();
            }
            // If the branch is remote and has no local ref we need to create the local branch and switch to that
            else if(branchInfo.isRemote() && !branchInfo.hasLocalRef()) {
                String branchName = branchInfo.getShortName();
                
                // Create local branch at point of remote branch ref
                Ref ref = git.branchCreate()
                        .setName(branchName)
                        .setStartPoint(branchInfo.getFullName())
                        .call();
                
                // checkout
                git.checkout().setName(ref.getName()).call();
                
                return true;
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Switch Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.SwitchBranchAction_1, ex);
        }

        return false;
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return fBranchInfo != null && !fBranchInfo.isCurrentBranch() && super.shouldBeEnabled();
    }
}
