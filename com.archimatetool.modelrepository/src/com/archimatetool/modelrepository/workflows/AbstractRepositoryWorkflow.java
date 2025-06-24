/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.SSHCredentials;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.CommitDialog;
import com.archimatetool.modelrepository.dialogs.Dialogs;
import com.archimatetool.modelrepository.dialogs.ErrorMessageDialog;
import com.archimatetool.modelrepository.dialogs.UserNamePasswordDialog;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;

/**
 * Abstract workflow in the UI.
 * Each workflow might ask for user input and show message dialogs and error dialogs.
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractRepositoryWorkflow implements IRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(AbstractRepositoryWorkflow.class.getName());
    
    protected IWorkbenchWindow workbenchWindow;
    protected IArchiRepository archiRepository;

    public AbstractRepositoryWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        this.workbenchWindow = workbenchWindow;
        this.archiRepository = archiRepository;
    }
    
    /**
     * Display an errror dialog with exception shown in text box
     */
    protected void displayErrorDialog(String title, Throwable ex) {
        ex.printStackTrace();
        ErrorMessageDialog.open(workbenchWindow.getShell(),
                title,
                Messages.AbstractWorkflow_0,
                ex);
    }

    /**
     * Display an error dialog with a detailed message shown in text box
     */
    protected void displayErrorDialog(String title, String message, String detailMessage) {
        ErrorMessageDialog.open(workbenchWindow.getShell(),
                title,
                message,
                detailMessage);
    }
    
    /**
     * Display a simple errror dialog with title and message
     */
    protected void displayErrorDialog(String title, String message) {
        MessageDialog.openError(workbenchWindow.getShell(),
                title,
                Messages.AbstractWorkflow_0 +
                    "\n" + //$NON-NLS-1$
                    message);
    }

    /**
     * If the model is open return whether it is dirty
     */
    protected boolean isModelDirty() {
        IArchimateModel model = archiRepository.getOpenModel();
        return model != null && IEditorModelManager.INSTANCE.isModelDirty(model);
    }
    
    /**
     * If the model is open check whether it is dirty and needs saving.
     * If it is, the user is asked to save the model.
     * Return false if the user cancels or an exception occcurs.
     * Return true if the model doesn't need saving or user saved the model.
     */
    protected boolean checkModelNeedsSaving() {
        // Model is open and needs saving
        IArchimateModel model = archiRepository.getOpenModel();
        if(model != null && IEditorModelManager.INSTANCE.isModelDirty(model)) {
            try {
                if(askToSaveModel(model) == SWT.CANCEL) {
                    return false;
                }
            }
            catch(IOException ex) {
                logger.log(Level.SEVERE, "Save", ex); //$NON-NLS-1$
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Ask to save the model. If user clicks yes, model is saved
     * @param model The model
     * @return SWT.YES, SWT.NO or SWT.CANCEL
     */
    protected int askToSaveModel(IArchimateModel model) throws IOException {
        int response = Dialogs.openYesNoCancelDialog(workbenchWindow.getShell(), Messages.AbstractWorkflow_1, Messages.AbstractWorkflow_2);
        if(response == SWT.YES) {
            IEditorModelManager.INSTANCE.saveModel(model);
        }
        return response;
    }
    
    /**
     * Check if there are changes that need to be committed before proceeding.
     * Ask the user to commit with a Yes/No/Cancel dialog.
     * If user cancels, return false.
     * If Yes, open the Commit dialog.
     * If user cancels, return false.
     * If No, reset the working dir with a hard reset to clear uncommitted changes
     * Return true
     */
    protected boolean checkIfCommitNeeded() {
        try {
            if(archiRepository.hasChangesToCommit()) {
                int response = Dialogs.openYesNoCancelDialog(workbenchWindow.getShell(), Messages.AbstractWorkflow_3, Messages.AbstractWorkflow_4);
                // Cancel
                if(response == SWT.CANCEL) {
                    // Cancel
                    return false;
                }
                // Yes
                else if(response == SWT.YES) {
                    // Commit Dialog
                    return commitChanges();
                }
                // No. Discard changes by resetting to HEAD
                else if(response == SWT.NO) {
                    logger.info("Resetting to HEAD"); //$NON-NLS-1$
                    archiRepository.resetToRef(RepoConstants.HEAD);

                    // Close and re-open the reset model
                    closeAndRestoreModel();
                    
                    // Notify in case history was showing working tree
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                }
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
            ex.printStackTrace();
            return false;
        }
        
        return true;
    }

    /**
     * Commit changes. Show user dialog.
     * @return true if successful, false otherwise
     */
    protected boolean commitChanges() {
        CommitDialog commitDialog = new CommitDialog(workbenchWindow.getShell(), archiRepository);
        int response = commitDialog.open();
        
        if(response == Window.OK) {
            String commitMessage = commitDialog.getCommitMessage();
            boolean amend = commitDialog.getAmend();
            
            try {
                logger.info("Commiting changes for: " + archiRepository.getModelFile()); //$NON-NLS-1$
                archiRepository.commitChangesWithManifest(commitMessage, amend);
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Commit Exception", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.AbstractWorkflow_3, ex);
                return false;
            }
            finally {
                notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Check that there is a Remote set
     */
    protected boolean checkRemoteSet() {
        // No repository URL set
        try {
            if(archiRepository.getRemoteURL() == null) {
                logger.warning("Remote not set for: " + archiRepository.getWorkingFolder()); //$NON-NLS-1$
                MessageDialog.openError(workbenchWindow.getShell(), Messages.AbstractWorkflow_5, Messages.AbstractWorkflow_6);
                return false;
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Remote", ex); //$NON-NLS-1$
            ex.printStackTrace();
            return false;
        }
        
        return true;
    }
    
    /**
     * If HTTP return UsernamePassword or null if user cancels dialog
     * If SSH return SSHCredentials
     * On Exception return null so caller needs to return from the workflow
     */
    protected ICredentials getCredentials() {
        try {
            // HTTP
            if(RepoUtils.isHTTP(archiRepository.getRemoteURL())) {
                return getUsernamePassword();
            }
            // SSH
            else {
                return new SSHCredentials();
            }
        }
        catch(IOException | GitAPIException | StorageException ex) {
            logger.log(Level.SEVERE, "User Credentials", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.AbstractRepositoryWorkflow_0, ex);
        }
        
        return null;
    }
    
    /**
     * Get user name and password from credentials file if present or from dialog
     */
    protected UsernamePassword getUsernamePassword() throws StorageException {
        // Get credentials from storage
        UsernamePassword npw = CredentialsStorage.getInstance().getCredentials(archiRepository);
        
        // Ask the user if no username set
        if(!npw.isUsernameSet()) {
            logger.info("Asking for user credentials"); //$NON-NLS-1$
            UserNamePasswordDialog dialog = new UserNamePasswordDialog(workbenchWindow.getShell(), archiRepository);
            if(dialog.open() == Window.OK) {
                npw = new UsernamePassword(dialog.getUsername(), dialog.getPassword());
            }
            else {
                npw = null;
            }
        }
        
        return npw;
    }
    
    /**
     * Notify that the repo changed
     */
    protected void notifyChangeListeners(String eventName) {
        RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(eventName, archiRepository);
    }
    
    /**
     * If the model is open in the models tree, close it, asking to save if needed if askSaveModel is true.
     * OpenModelState is returned in all cases.
     */
    protected OpenModelState closeModel(boolean askSaveModel) {
        return new OpenModelState().closeModel(archiRepository, askSaveModel);
    }
    
    /**
     * Re-open this repo's model in the models tree and any previously opened editors
     */
    protected IArchimateModel restoreModel(OpenModelState modelState) {
        return modelState != null ? modelState.restoreModel() : null;
    }
    
    /**
     * If the model is open in the models tree, close it and re-open it without asking
     */
    protected IArchimateModel closeAndRestoreModel() {
        return closeModel(false).restoreModel();
    }
    
    /**
     * Run the workflow
     */
    @Override
    public void run() {
    }
    
    /**
     * @return true if the workflow can run
     */
    @Override
    public boolean canRun() {
        return archiRepository != null && archiRepository.getWorkingFolder().exists();
    }
}
