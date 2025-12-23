/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.GitUtils;
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
    protected void run(GitUtils utils) {
        logger.info("Discarding uncommitted changes..."); //$NON-NLS-1$
        
        try {
            if(!utils.hasChangesToCommit()) {
                MessageDialog.openInformation(workbenchWindow.getShell(),
                        Messages.DiscardChangesWorkflow_0,
                        Messages.DiscardChangesWorkflow_1);
                logger.info("Nothing to discard"); //$NON-NLS-1$
                return;
            }
        }
        catch(GitAPIException ex) {
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
        OpenModelState modelState = closeModel(true).orElse(null);
        if(modelState != null && modelState.cancelled()) { // User cancelled closing the model
            return;
        }
        
        // Use a BusyIndicator rather than a ProgressMonitor so we don't see the model closing and re-opening.
        BusyIndicator.showWhile(workbenchWindow.getShell().getDisplay(), () -> {
            try {
                // Reset to HEAD
                logger.info("Resetting to HEAD"); //$NON-NLS-1$
                utils.resetToRef(RepoConstants.HEAD);
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Reset to HEAD", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.SwitchBranchWorklow_0, ex);
            }
            
            // Open the model if it was open before and any open editors
            restoreModel(modelState);
        });
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
}
