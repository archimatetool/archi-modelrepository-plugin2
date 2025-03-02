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
 * Undo the last commit
 */
public class UndoLastCommitWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(UndoLastCommitWorkflow.class.getName());
    
    public UndoLastCommitWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    @Override
    public void run() {
        logger.info("Undoing last commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.UndoLastCommitWorkflow_0,
                Messages.UndoLastCommitWorkflow_1)) {
            return;
        }
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel(true);
        if(modelState.cancelled()) {
            return;
        }

        try {
            logger.info("Resetting to HEAD^"); //$NON-NLS-1$
            archiRepository.resetToRef("HEAD^"); //$NON-NLS-1$
        }
        catch(Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to HEAD^", ex); //$NON-NLS-1$
        }
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
    
    @Override
    public boolean canRun() {
        if(!super.canRun()) {
            return false;
        }
        
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            return utils.hasMoreThanOneCommit()                       // Has to be at least 2 commits 
                    && !utils.isRemoteRefForCurrentBranchAtHead();    // Head commit and remote commit must be different
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Can Undo Last Commit", ex); //$NON-NLS-1$
            return false;
        }
    }
}
