/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.dialogs.CommitDialog;
import com.archimatetool.modelrepository.repository.IArchiRepository;
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
	    return getRepository() != null && getRepository().getLocalRepositoryFolder().exists();
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
