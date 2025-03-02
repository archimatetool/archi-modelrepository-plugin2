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
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.dialogs.AddBranchDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Add Branch Workflow
 * 
 * @author Phillip Beauvoir
 */
public class AddBranchWorkflow extends AbstractRepositoryWorkflow {

    private static Logger logger = Logger.getLogger(AddBranchWorkflow.class.getName());
    
    private BranchInfo currentBranchInfo;

    public AddBranchWorkflow(IWorkbenchWindow workbenchWindow, BranchInfo currentBranchInfo) {
        super(workbenchWindow, new ArchiRepository(currentBranchInfo.getWorkingFolder()));
        this.currentBranchInfo = currentBranchInfo;
    }

    /**
     * Add a Branch
     */
    @Override
    public void run() {
        AddBranchDialog dialog = new AddBranchDialog(workbenchWindow.getShell());
        int retVal = dialog.open();
        String branchName = dialog.getBranchName();
        
        if(retVal == IDialogConstants.CANCEL_ID || !StringUtils.isSet(branchName)) {
            return;
        }
        
        // Then Commit
        logger.info("Adding branch..."); //$NON-NLS-1$
        
        try(Git git = Git.open(archiRepository.getWorkingFolder())) {
            String fullName = RepoConstants.R_HEADS + branchName;
            
            // If the branch exists show error
            if(git.getRepository().exactRef(fullName) != null) {
                logger.info("Branch already exists"); //$NON-NLS-1$
                MessageDialog.openError(workbenchWindow.getShell(),
                        Messages.AddBranchWorkflow_0,
                        NLS.bind(Messages.AddBranchWorkflow_1, branchName));
            }
            else {
                // Create the branch
                logger.info("Creating branch: " + branchName); //$NON-NLS-1$
                git.branchCreate().setName(branchName).call();

                // Checkout if option set
                if(retVal == AddBranchDialog.ADD_BRANCH_CHECKOUT) {
                    logger.info("Checking out branch: " + branchName); //$NON-NLS-1$
                    git.checkout().setName(fullName).call();
                }
                
                // Notify listeners
                notifyChangeListeners(IRepositoryListener.BRANCHES_CHANGED);
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Add Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.AddBranchWorkflow_0, ex);
        }
    }

    @Override
    public boolean canRun() {
        return currentBranchInfo != null && currentBranchInfo.isCurrentBranch() && super.canRun();
    }
}
