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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Restore to a particular commit
 */
public class RestoreCommitWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(RestoreCommitWorkflow.class.getName());
    
    private RevCommit revCommit;
	
    public RestoreCommitWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository, RevCommit revCommit) {
        super(workbenchWindow, archiRepository);
        this.revCommit = revCommit;
    }

    @Override
    public void run() {
        logger.info("Restoring to a commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.RestoreCommitWorkflow_0,
                Messages.RestoreCommitWorkflow_1)) {
            return;
        }
        
        try {
            // Delete the contents of the working directory
            logger.info("Deleting contents of working directory"); //$NON-NLS-1$
            archiRepository.deleteWorkingFolderContents();
            
            // Extract the contents of the commit
            logger.info("Extracting the commit"); //$NON-NLS-1$
            archiRepository.extractCommit(revCommit, archiRepository.getWorkingFolder(), false);
            
            // Check that we actually restored the model in case there is no model file in this commit
            if(!archiRepository.getModelFile().exists()) {
                throw new IOException(Messages.RestoreCommitWorkflow_2);
            }
            
            // Commit changes
            logger.info("Committing changes..."); //$NON-NLS-1$
            archiRepository.commitChanges(Messages.RestoreCommitWorkflow_3 + " '" + revCommit.getShortMessage() + "'", false); //$NON-NLS-1$ //$NON-NLS-2$
            
            notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Restore to Commit", ex); //$NON-NLS-1$
            try {
                archiRepository.resetToRef(RepoConstants.HEAD);
            }
            catch(IOException | GitAPIException ex1) {
                logger.log(Level.SEVERE, "Reset to HEAD", ex1); //$NON-NLS-1$
            }
            displayErrorDialog(Messages.RestoreCommitWorkflow_0, ex);
        }
        finally {
            // Close and re-open model
            closeAndRestoreModel();
        }
    }
    
    @Override
    public boolean canRun() {
        if(revCommit == null || !super.canRun()) {
            return false;
        }
        
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            // Commit is not at HEAD and is part of the local branch's history (HEAD)
            //return !utils.isCommitAtHead(revCommit) && utils.isMergedInto(revCommit.getName(), RepoConstants.HEAD);
            
            // Commit is not at HEAD
            return !utils.isCommitAtHead(revCommit);
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Enabled state", ex); //$NON-NLS-1$
            return false;
        }
    }
}
