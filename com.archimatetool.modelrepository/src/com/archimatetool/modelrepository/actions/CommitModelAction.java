/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Commit Model Action
 * 
 * 1. Offer to save the model
 * 2. Check if there is anything to Commit
 * 3. Show Commit dialog
 * 4. Commit
 * 
 * @author Phillip Beauvoir
 */
public class CommitModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(CommitModelAction.class.getName());
    
    public CommitModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_COMMIT));
        setText(Messages.CommitModelAction_0);
        setToolTipText(Messages.CommitModelAction_0);
    }

    public CommitModelAction(IWorkbenchWindow window, IArchiRepository repository) {
        this(window);
        setRepository(repository);
    }

    @Override
    public void run() {
        // Ask to save the model if open and dirty
        IArchimateModel model = getRepository().getModel();
        if(model != null && IEditorModelManager.INSTANCE.isModelDirty(model)) {
            if(!askToSaveModel(model)) {
                return;
            }
        }

        // Then Commit
        try {
            if(getRepository().hasChangesToCommit()) {
                if(commitChanges()) {
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                }
            }
            else {
                MessageDialog.openInformation(fWindow.getShell(),
                        Messages.CommitModelAction_0,
                        Messages.CommitModelAction_1);
            }
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Commit Exception", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.CommitModelAction_2, ex);
        }
    }
}
