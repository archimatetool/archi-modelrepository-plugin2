/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Reset HEAD to the remote commit if there is one
 */
public class ResetToRemoteCommitWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(ResetToRemoteCommitWorkflow.class.getName());
    
    public ResetToRemoteCommitWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    @Override
    public void run() {
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.ResetToRemoteCommitWorkflow_0,
                Messages.ResetToRemoteCommitWorkflow_1)) {
            return;
        }
        
        logger.info("Resetting to a remote commit..."); //$NON-NLS-1$
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel(true);
        if(modelState.cancelled()) {
            return;
        }
        
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            String remoteRefName = utils.getRemoteRefNameForCurrentBranch();
            logger.info("Resetting to: " + remoteRefName); //$NON-NLS-1$
            utils.resetToRef(remoteRefName);
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to Ref", ex); //$NON-NLS-1$
        }
        
        // Reload the model
        restoreModel(modelState);
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
    
    @Override
    public boolean canRun() {
        if(!super.canRun()) {
            return false;
        }

        // Return true if there is a remote Ref for the current branch && HEAD and the remote Ref are not the same
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            // We have to check there is actually is a remote Ref or the logic doesn't work
            return utils.getRemoteRefForCurrentBranch() != null && !utils.isRemoteRefForCurrentBranchAtHead();
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
            return false;
        }
    }
}
