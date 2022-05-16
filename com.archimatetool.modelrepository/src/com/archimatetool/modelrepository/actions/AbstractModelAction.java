/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.CommitDialog;
import com.archimatetool.modelrepository.dialogs.UserNamePasswordDialog;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;

/**
 * Abstract ModelAction
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractModelAction extends Action implements IModelRepositoryAction {
    
    private static Logger logger = Logger.getLogger(AbstractModelAction.class.getName());
	
	private IArchiRepository fRepository;
	
	protected IWorkbenchWindow fWindow;
	
	protected AbstractModelAction(IWorkbenchWindow window) {
	    fWindow = window;
	}
	
	@Override
	public void setRepository(IArchiRepository repository) {
	    fRepository = repository;
	    setEnabled(shouldBeEnabled());
	}
	
	@Override
	public IArchiRepository getRepository() {
	    return fRepository;
	}
	
    @Override
	public void update() {
        setEnabled(shouldBeEnabled());
	}
	 
	/**
	 * @return true if this action should be enabled
	 */
	protected boolean shouldBeEnabled() {
	    return getRepository() != null && getRepository().getWorkingFolder().exists();
	}
	
    /**
     * Display an errror dialog
     * @param title
     * @param ex
     */
    protected void displayErrorDialog(String title, Throwable ex) {
        ex.printStackTrace();
        
        String message = ex.getMessage();
        
        if(ex instanceof InvocationTargetException) {
            ex = ex.getCause();
        }
        
        if(ex instanceof JGitInternalException) {
            ex = ex.getCause();
        }
        
        if(ex != null) {
            message = ex.getMessage();
        }
        
        displayErrorDialog(title, message);
    }

    /**
     * Display an errror dialog
     * @param title
     * @param message
     */
    protected void displayErrorDialog(String title, String message) {
        MessageDialog.openError(fWindow.getShell(),
                title,
                Messages.AbstractModelAction_0 +
                    "\n" + //$NON-NLS-1$
                    message);
    }

    /**
     * If the model is open check whether it is dirty and needs saving.
     * If it is, the user is asked to save the model.
     * Return false if the user cancels or an exception occcurs.
     * Return true if the model doesn't need saving or user saved the model.
     */
    boolean checkModelNeedsSaving() {
        // Model is open and needs saving
        IArchimateModel model = getRepository().getModel();
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
        int response = openYesNoCancelDialog(Messages.AbstractModelAction_1, Messages.AbstractModelAction_2);
        if(response == SWT.YES) {
            IEditorModelManager.INSTANCE.saveModel(model);
        }
        return response;
    }
    
    /**
     * Open a YES/NO/CANCEL dialog
     * @return SWT.YES, SWT.NO or SWT.CANCEL
     */
    protected int openYesNoCancelDialog(String title, String message) {
        switch(MessageDialog.open(MessageDialog.CONFIRM,
                fWindow.getShell(),
                title,
                message,
                SWT.NONE,
                IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL)) {
            case 0:
                return SWT.YES;
            case 1:
                return SWT.NO;
            default:
                return SWT.CANCEL;
        }
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
    boolean checkIfCommitNeeded() {
        try {
            if(getRepository().hasChangesToCommit()) {
                int response = openYesNoCancelDialog(Messages.AbstractModelAction_3, Messages.AbstractModelAction_4);
                // Cancel
                if(response == SWT.CANCEL) {
                    // Cancel
                    return false;
                }
                // Yes
                else if(response == SWT.YES) {
                    // Commit Dialog
                    boolean didCommit = commitChanges();
                    if(didCommit) {
                        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                    }
                    // Commit Dialog cancel
                    else {
                        return false;
                    }
                }
                // No. Discard changes by resetting to HEAD before merging
                else if(response == SWT.NO) {
                    logger.info("Resetting to HEAD"); //$NON-NLS-1$
                    getRepository().resetToRef(Constants.HEAD);
                }
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
            ex.printStackTrace();
            closeModel(false); // Safety precaution
            return false;
        }
        
        return true;
    }
    
    /**
     * Commit changes. Show user dialog.
     * @return true if successful, false otherwise
     */
    protected boolean commitChanges() {
        CommitDialog commitDialog = new CommitDialog(fWindow.getShell(), getRepository());
        int response = commitDialog.open();
        
        if(response == Window.OK) {
            String commitMessage = commitDialog.getCommitMessage();
            boolean amend = commitDialog.getAmend();
            
            try {
                logger.info("Commiting changes for: " + getRepository().getModelFile()); //$NON-NLS-1$
                getRepository().commitChanges(commitMessage, amend);
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Commit Exception", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.AbstractModelAction_3, ex);
                return false;
            }
            
            return true;
        }
        
        return false;
    }

    /**
     * Get user name and password from credentials file if prefs set or from dialog
     */
    protected UsernamePassword getUsernamePassword() {
        // TODO: Get credentials from encrypted storage and return that, if present
        
        /*
         * This is for testing and debugging purposes only.
         * Set a VM argument with a path referencing a file containing username/password
         * -DcoArchi.credentials=pathToFile
         */
        String credentialsFile = System.getProperty("coArchi.credentials"); //$NON-NLS-1$
        if(credentialsFile != null) {
            File file = new File(credentialsFile);
            if(file.exists()) {
                try(FileInputStream is = new FileInputStream(file)) {
                    Properties props = new Properties();
                    props.load(is);
                    return new UsernamePassword(props.getProperty("username", ""), //$NON-NLS-1$ //$NON-NLS-2$
                            props.getProperty("password", "").toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$
                }
                catch(IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        
        // Else ask the user
        UserNamePasswordDialog dialog = new UserNamePasswordDialog(fWindow.getShell());
        if(dialog.open() == Window.OK) {
            return new UsernamePassword(dialog.getUsername(), dialog.getPassword());
        }

        return null;
    }
    
    /**
     * Notify that the repo changed
     */
    protected void notifyChangeListeners(String eventName) {
        RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(eventName, getRepository());
    }
    
    /**
     * If the model is open in the models tree, close it, asking to save if needed if askSaveModel is true.
     * OpenModelState is returned in all cases.
     */
    protected OpenModelState closeModel(boolean askSaveModel) {
        OpenModelState modelState = new OpenModelState();
        modelState.closeModel(getRepository().getModel(), askSaveModel);
        return modelState;
    }
    
    /**
     * Re-open this repo's model in the models tree and any previously opened editors
     */
    protected IArchimateModel restoreModel(OpenModelState modelState) {
        return modelState != null ? modelState.restoreModel(getRepository().getModelFile()) : null;
    }

    @Override
    public void dispose() {
    }
}
