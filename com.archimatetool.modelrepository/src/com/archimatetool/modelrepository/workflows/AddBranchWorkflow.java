/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.dialogs.AddBranchDialog;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Add Branch Workflow
 * 
 * @author Phillip Beauvoir
 */
public class AddBranchWorkflow extends AbstractRepositoryWorkflow {

    private static Logger logger = Logger.getLogger(AddBranchWorkflow.class.getName());
    
    // The ObjectId of the point where the new branch will be added
    private ObjectId objectId;

    public AddBranchWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository repository, ObjectId objectId) {
        super(workbenchWindow, repository);
        this.objectId = objectId;
    }

    @Override
    public void run() {
        boolean isAtHead = false;
        boolean hasChangesToCommit = false;
        
        try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
            isAtHead = utils.isObjectIdAtHead(objectId);
            hasChangesToCommit = archiRepository.hasChangesToCommit();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.WARNING, "Add Branch", ex); //$NON-NLS-1$
        }
        
        // Add checkout button if objectId is at HEAD or the model is not dirty and no commits needed
        AddBranchDialog dialog = new AddBranchDialog(workbenchWindow.getShell(), isAtHead || !(hasChangesToCommit || isModelDirty()));
        int retVal = dialog.open();
        String branchName = dialog.getName();
        
        if(retVal == IDialogConstants.CANCEL_ID || !StringUtils.isSet(branchName)) {
            return;
        }
        
        logger.info("Adding branch..."); //$NON-NLS-1$
        
        try(Git git = Git.open(archiRepository.getWorkingFolder())) {
            String fullName = RepoConstants.R_HEADS + branchName;
            
            // If the branch exists show error
            if(git.getRepository().exactRef(fullName) != null) {
                logger.info("Branch already exists"); //$NON-NLS-1$
                MessageDialog.openError(workbenchWindow.getShell(),
                        Messages.AddBranchWorkflow_0,
                        NLS.bind(Messages.AddBranchWorkflow_1, branchName));
                return;
            }
            
            // Create the branch
            logger.info("Creating branch: " + branchName); //$NON-NLS-1$
            git.branchCreate()
               .setStartPoint(objectId.getName()) // Use ObjectID for start point
               .setName(branchName)
               .call();

            // Checkout if option set
            if(retVal == AddBranchDialog.ADD_BRANCH_CHECKOUT) {
                OpenModelState modelState = null;

                // If we're not at HEAD we need to reload the model
                if(!isAtHead) {
                    modelState = closeModel(false).orElse(null);
                }

                logger.info("Checking out branch: " + branchName); //$NON-NLS-1$
                git.checkout()
                   .setName(fullName)
                   .call();

                // Open the model if it was open before and any open editors
                restoreModel(modelState);
            }

            // Notify listeners
            notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
        }
        catch(Exception ex) { // Catch all exceptions in case of JGitInternalException
            logger.log(Level.SEVERE, "Add Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.AddBranchWorkflow_0, ex);
        }
    }

    @Override
    public boolean canRun() {
        return objectId != null && super.canRun();
    }
}
