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

/**
 * Commit Model Workflow
 * 
 * @author Phillip Beauvoir
 */
public class CommitModelWorkflow extends AbstractRepositoryWorkflow {

    private static Logger logger = Logger.getLogger(CommitModelWorkflow.class.getName());

    public CommitModelWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    /**
     * Commit Model
     * 1. Offer to save the model
     * 2. Check if there is anything to Commit
     * 3. Show Commit dialog
     * 4. Commit
     */
    @Override
    public void run() {
        // Check if the model is open and needs saving
        if(!checkModelNeedsSaving()) {
            return;
        }

        // Then Commit
        logger.info("Committing changes..."); //$NON-NLS-1$
        
        try {
            if(archiRepository.hasChangesToCommit()) {
                commitChanges();
            }
            else {
                logger.info("Nothing to commit"); //$NON-NLS-1$
                MessageDialog.openInformation(workbenchWindow.getShell(),
                        Messages.CommitModelWorkflow_0,
                        Messages.CommitModelWorkflow_1);
            }
        }
        catch(GitAPIException | IOException ex) {
            logger.log(Level.SEVERE, "Commit", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.CommitModelWorkflow_0, ex);
        }
    }
}
