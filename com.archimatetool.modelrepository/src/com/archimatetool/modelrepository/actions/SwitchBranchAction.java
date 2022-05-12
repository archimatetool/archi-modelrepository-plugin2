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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
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
        fBranchInfo = branchInfo;
        setEnabled(shouldBeEnabled());
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
        try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
            if(utils.isRefAtHead(branchInfo.getRef())) {
                switchBranch(branchInfo);
                notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
                return;
            }
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Ref at Head", ex); //$NON-NLS-1$
            return;
        }
        
        // Model is open and needs saving
        IArchimateModel model = getRepository().getModel();
        if(model != null && IEditorModelManager.INSTANCE.isModelDirty(model)) {
            try {
                if(askToSaveModel(model) == SWT.CANCEL) {
                    return;
                }
            }
            catch(IOException ex) {
                logger.log(Level.SEVERE, "Save", ex); //$NON-NLS-1$
                return;
            }
        }
        
        try {
            // There are uncommitted changes
            if(getRepository().hasChangesToCommit()) {
                int response = openYesNoCancelDialog(Messages.SwitchBranchAction_1, Messages.SwitchBranchAction_2);
                // Cancel / Yes
                if(response == SWT.CANCEL || (response == SWT.YES && !commitChanges())) { // Commit dialog
                    // Commit cancelled
                    return;
                }
                // No. Discard changes by resetting to HEAD before switching branch
                else if(response == SWT.NO) {
                    logger.info("Resetting to HEAD"); //$NON-NLS-1$
                    getRepository().resetToRef(Constants.HEAD);
                }
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
            ex.printStackTrace();
            closeModel(false);
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
        return super.shouldBeEnabled() && fBranchInfo != null && !fBranchInfo.isCurrentBranch();
    }
}
