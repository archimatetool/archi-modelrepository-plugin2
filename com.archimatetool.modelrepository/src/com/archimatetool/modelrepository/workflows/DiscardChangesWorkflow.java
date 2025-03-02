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

import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Discard Uncommitted Changes Workflow
 * 
 * @author Phillip Beauvoir
 */
public class DiscardChangesWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(DiscardChangesWorkflow.class.getName());
    
    public DiscardChangesWorkflow(IWorkbenchWindow window, IArchiRepository repository) {
        super(window, repository);
    }

    @Override
    public void run() {
        logger.info("Discarding uncommitted changes..."); //$NON-NLS-1$
        
        try {
            if(!archiRepository.hasChangesToCommit()) {
                MessageDialog.openInformation(workbenchWindow.getShell(),
                        Messages.DiscardChangesWorkflow_0,
                        Messages.DiscardChangesWorkflow_1);
                logger.info("Nothing to discard"); //$NON-NLS-1$
                return;
            }
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
            return;
        }
        
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.DiscardChangesWorkflow_0,
                Messages.DiscardChangesWorkflow_2)) {
            return;
        }
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel(true);
        if(modelState.cancelled()) {
            return;
        }
        
        // Reset to HEAD
        try {
            logger.info("Resetting to HEAD"); //$NON-NLS-1$
            archiRepository.resetToRef(RepoConstants.HEAD);
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to HEAD", ex); //$NON-NLS-1$
        }
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
}
