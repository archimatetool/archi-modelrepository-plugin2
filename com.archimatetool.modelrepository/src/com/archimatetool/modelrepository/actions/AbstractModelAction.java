/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;

/**
 * Abstract ModelAction
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractModelAction extends Action implements IModelRepositoryAction {
	
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
     * Ask to save the model
     * @param model The model
     * @return True if the model was saved
     */
    protected boolean askToSaveModel(IArchimateModel model) {
        boolean doSave = MessageDialog.openConfirm(fWindow.getShell(),
                Messages.AbstractModelAction_1,
                Messages.AbstractModelAction_2);

        if(doSave) {
            try {
                doSave = IEditorModelManager.INSTANCE.saveModel(model);
            }
            catch(IOException ex) {
                doSave = false;
                displayErrorDialog(Messages.AbstractModelAction_1, ex);
            }
        }
        
        return doSave;
    }
    
    /**
     * Notify that the repo changed
     */
    protected void notifyChangeListeners(String eventName) {
        RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(eventName, getRepository());
    }
    
    @Override
    public void dispose() {
    }
}
