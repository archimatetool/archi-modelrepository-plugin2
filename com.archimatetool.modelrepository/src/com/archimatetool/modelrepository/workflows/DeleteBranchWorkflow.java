/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Delete Branch Workflow
 * 
 * @author Phillip Beauvoir
 */
public class DeleteBranchWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(DeleteBranchWorkflow.class.getName());
    
    private BranchInfo currentBranchInfo;
    
    public DeleteBranchWorkflow(IWorkbenchWindow workbenchWindow, BranchInfo currentBranchInfo) {
        super(workbenchWindow, new ArchiRepository(currentBranchInfo.getWorkingFolder()));
        this.currentBranchInfo = currentBranchInfo;
    }

    @Override
    public void run() {
        // If the branch has a remote ref of the same name (tracked or untracked) or is a remote branch
        boolean deleteRemoteBranch = currentBranchInfo.hasRemoteRef() || currentBranchInfo.isRemote();
        
        // Check that there is a repository URL set
        if(deleteRemoteBranch && !checkRemoteSet()) {
            return;
        }

        // Confirm
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.DeleteBranchWorkflow_0,
                NLS.bind(Messages.DeleteBranchWorkflow_1, currentBranchInfo.getShortName()))) {
            return;
        }
        
        // Delete remote (and local) branch
        if(deleteRemoteBranch) {
            try {
                // Get credentials if HTTP
                UsernamePassword npw = null;
                if(RepoUtils.isHTTP(archiRepository.getRemoteURL())) {
                    npw = getUsernamePassword();
                    if(npw == null) { // User cancelled or there are no stored credentials
                        return;
                    }
                }
                
                deleteLocalAndRemoteBranch(currentBranchInfo, npw);
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Delete Branch", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.DeleteBranchWorkflow_0, ex);
            }
        }
        // Just delete the local ref and the non-origin remote ref (if it has one)
        else {
            try {
                deleteLocalBranch(currentBranchInfo);
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Delete Branch", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.DeleteBranchWorkflow_0, ex);
            }
        }
        
        // Notify listeners last
        notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
    }
    
    /**
     * Delete the local and remote refs and push to the remote deleting the remote branch
     */
    private void deleteLocalAndRemoteBranch(BranchInfo branchInfo, UsernamePassword npw) throws Exception {
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.DeleteBranchWorkflow_0, IProgressMonitor.UNKNOWN);
            
            try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
                // Delete the remote branch first in case of error
                logger.info("Deleting remote branch: " + branchInfo.getLocalBranchName()); //$NON-NLS-1$
                PushResult pushResult = utils.deleteRemoteBranch(branchInfo.getLocalBranchName(), npw, new ProgressMonitorWrapper(monitor,
                                                                                        Messages.DeleteBranchWorkflow_0));
                logResult(pushResult);
                
                // Then delete local and tracked branch
                logger.info("Deleting local branch: " + branchInfo.getShortName()); //$NON-NLS-1$
                utils.deleteBranches(true, // force the delete even if the branch hasn't been merged
                                   branchInfo.getLocalBranchName(),
                                   branchInfo.getRemoteBranchName());
                
            }
        }, true);
    }
    
    /**
     * Delete local branch
     */
    private void deleteLocalBranch(BranchInfo branchInfo) throws IOException, GitAPIException {
        logger.info("Deleting local branch: " + branchInfo.getShortName()); //$NON-NLS-1$

        // Delete local and tracked branch
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            utils.deleteBranches(true, // force the delete even if the branch hasn't been merged
                               branchInfo.getLocalBranchName(),
                               branchInfo.getRemoteBranchName());
        }
    }
    
    private void logResult(PushResult pushResult) {
        String messages = GitUtils.getPushResultMessages(pushResult);
        if(StringUtils.isSet(messages)) {
            logger.info("PushResult message: " + messages); //$NON-NLS-1$
        }
        
        for(RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
            logger.info("PushResult status for " + refUpdate.getRemoteName() + ": " +refUpdate.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    @Override
    public boolean canRun() {
        return currentBranchInfo != null && 
               !currentBranchInfo.isCurrentBranch() &&    // Not current branch
               !currentBranchInfo.isPrimaryBranch() &&    // Not primary branch
               super.canRun();
    }

}
