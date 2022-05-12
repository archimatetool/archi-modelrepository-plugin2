/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IRepositoryConstants;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Delete Branch Action
 * 
 * @author Phillip Beauvoir
 */
public class DeleteBranchAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(DeleteBranchAction.class.getName());
    
    private BranchInfo fBranchInfo;
    
    public DeleteBranchAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE));
        setText(Messages.DeleteBranchAction_0);
        setToolTipText(Messages.DeleteBranchAction_0);
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
        
        // Keep a local reference in case of a notification event changing the current branch selection in the UI
        BranchInfo branchInfo = fBranchInfo;

        // Confirm
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.DeleteBranchAction_0,
                NLS.bind(Messages.DeleteBranchAction_1, branchInfo.getShortName()))) {
            return;
        }
        
        // If the branch has a remote ref of the same name (tracked or untracked) or is a remote branch
        boolean deleteRemoteBranch = branchInfo.hasRemoteRef() || branchInfo.isRemote();
        
        // Delete remote (and local) branch
        if(deleteRemoteBranch) {
            try {
                // Get credentials if HTTP
                UsernamePassword npw = null;
                if(RepoUtils.isHTTP(getRepository().getRemoteURL())) {
                    npw = getUsernamePassword();
                    if(npw == null) { // User cancelled or there are no stored credentials
                        return;
                    }
                }
                
                deleteRemoteBranch(branchInfo, npw);
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Delete Branch", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.DeleteBranchAction_0, ex);
            }
        }
        // Just delete the local ref and the non-origin remote ref (if it has one)
        else {
            try {
                deleteLocalBranch(branchInfo);
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Delete Branch", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.DeleteBranchAction_0, ex);
            }
        }
        
        // Notify listeners last
        notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
    }
    
    /**
     * Delete the local and remote refs and push to the remote deleting the remote branch
     */
    private void deleteRemoteBranch(BranchInfo branchInfo, UsernamePassword npw) throws Exception {
        logger.info("Deleting remote branch: " + branchInfo.getShortName()); //$NON-NLS-1$

        // Store exception
        Exception[] exception = new Exception[1];

        // If using this be careful that no UI operations are peformed as this could lead to an SWT Invalid thread access exception
        PlatformUI.getWorkbench().getProgressService().busyCursorWhile(monitor -> {
            try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
                // Delete the remote branch first in case of error
                utils.deleteRemoteBranch(branchInfo.getLocalBranchNameFor(), npw);

                // Then delete local and tracked branch
                utils.deleteBranch(true, // force the delete even if the branch hasn't been merged
                                   branchInfo.getLocalBranchNameFor(),
                                   branchInfo.getRemoteBranchNameFor());
                
            }
            catch(IOException | GitAPIException ex) {
                exception[0] = ex;
            }
        });
        
        if(exception[0] != null) {
            throw exception[0];
        }
    }
    
    /**
     * Delete local branch
     */
    private void deleteLocalBranch(BranchInfo branchInfo) throws IOException, GitAPIException {
        logger.info("Deleting local branch: " + branchInfo.getShortName()); //$NON-NLS-1$

        // Delete local and tracked branch
        try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
            utils.deleteBranch(true, // force the delete even if the branch hasn't been merged
                               branchInfo.getLocalBranchNameFor(),
                               branchInfo.getRemoteBranchNameFor());
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return super.shouldBeEnabled() &&
                fBranchInfo != null && 
                !fBranchInfo.isCurrentBranch() && // Not current branch
                !IRepositoryConstants.MAIN.equals(fBranchInfo.getShortName()); // Not main branch
    }

}
