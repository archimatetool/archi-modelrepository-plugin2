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
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IRunnable;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Delete Branch Workflow
 * 
 * @author Phillip Beauvoir
 */
public class DeleteBranchWorkflow extends AbstractPushResultWorkflow {
    
    private static Logger logger = Logger.getLogger(DeleteBranchWorkflow.class.getName());
    
    private BranchInfo currentBranchInfo;
    
    public DeleteBranchWorkflow(IWorkbenchWindow workbenchWindow, BranchInfo currentBranchInfo) {
        super(workbenchWindow, new ArchiRepository(currentBranchInfo.getWorkingFolder()));
        this.currentBranchInfo = currentBranchInfo;
    }

    @Override
    protected void run(GitUtils utils) {
        // If the branch has a remote ref of the same name (tracked or untracked) or is a remote branch
        boolean deleteRemoteBranch = currentBranchInfo.hasRemoteRef() || currentBranchInfo.isRemote();
        
        // Check that there is a repository URL set
        if(deleteRemoteBranch && !checkRemoteSet(utils)) {
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
                // Get credentials
                ICredentials credentials = getCredentials(utils).orElse(null);
                if(credentials == null) {
                    return;
                }
                
                deleteLocalAndRemoteBranch(utils, currentBranchInfo, credentials.getCredentialsProvider());
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
    private void deleteLocalAndRemoteBranch(GitUtils utils, BranchInfo branchInfo, CredentialsProvider credentialsProvider) throws Exception {
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, true, true, monitor -> {
            monitor.beginTask(Messages.DeleteBranchWorkflow_0, IProgressMonitor.UNKNOWN);
            
            // Delete the remote branch first in case of error
            logger.info("Deleting remote branch: " + branchInfo.getLocalBranchName()); //$NON-NLS-1$
            PushResult pushResult = utils.deleteRemoteBranch(branchInfo.getLocalBranchName(), credentialsProvider, new ProgressMonitorWrapper(monitor,
                                                                                    Messages.DeleteBranchWorkflow_0));
            
            // Logging
            logPushResult(pushResult, logger);
            
            // Status
            checkPushResultStatus(pushResult);

            // If OK, delete local and tracked branch
            logger.info("Deleting local branch: " + branchInfo.getShortName()); //$NON-NLS-1$
            utils.deleteBranches(true, // force the delete even if the branch hasn't been merged
                               branchInfo.getLocalBranchName(),
                               branchInfo.getRemoteBranchName());
        });
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
    
    @Override
    public boolean canRun() {
        return currentBranchInfo != null && 
               !currentBranchInfo.isCurrentBranch() &&    // Not current branch
               !currentBranchInfo.isPrimaryBranch() &&    // Not primary branch
               super.canRun();
    }

}
