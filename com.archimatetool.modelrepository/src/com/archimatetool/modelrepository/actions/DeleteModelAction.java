/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

/**
 * Delete and/or remove from RepositoryTreeModel local repo model
 * 
 * If the repo is present in RepositoryTreeModel we give two choices - remove or delete
 * Remove removes the reference from the RepositoryTreeModel while Delete does that and physically deletes the repo folder
 * 
 * It is possible that the user can re-open the repo model from the MRU list, or directly.
 * In that case the model might not be in the RepositoryTreeModel so the only option offered is Delete.
 * 
 * @author Phillip Beauvoir
 */
public class DeleteModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(DeleteModelAction.class.getName());
    
    public DeleteModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE));
        setText(Messages.DeleteModelAction_0);
        setToolTipText(Messages.DeleteModelAction_0);
    }

    public DeleteModelAction(IWorkbenchWindow window, IArchiRepository repository) {
        this(window);
        setRepository(repository);
    }

    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }
        
        boolean deleteRepo = false;
        
        // Do we have an entry in the RepositoryTreeModel?
        RepositoryRef repoRef = RepositoryTreeModel.getInstance().findRepositoryRef(getRepository().getWorkingFolder());
        
        // If the repo is present in the RepositoryTreeModel offer to remove or delete
        if(repoRef != null) {
            int response = MessageDialog.open(MessageDialog.QUESTION,
                    fWindow.getShell(),
                    Messages.DeleteModelAction_0,
                    Messages.DeleteModelAction_2,
                    SWT.NONE,
                    Messages.DeleteModelAction_3,
                    Messages.DeleteModelAction_4,
                    Messages.DeleteModelAction_5);

            // Cancel
            if(response == -1 || response == 2) {
                return;
            }

            // Delete
            deleteRepo = response == 1;
        }
        // Model is open but not present in the RepositoryTreeModel
        else {
            if(!MessageDialog.openConfirm(fWindow.getShell(), Messages.DeleteModelAction_0, Messages.DeleteModelAction_1)) {
                return;
            }
            
            deleteRepo = true;
        }
        
        try {
            // Close model without asking to save
            IEditorModelManager.INSTANCE.closeModel(getRepository().getModel(), false);
            
            // Delete repo
            if(deleteRepo) {
                FileUtils.deleteFolder(getRepository().getWorkingFolder());
            }
            
            // Delete from RepositoryTreeModel
            if(repoRef != null) {
                repoRef.delete();
                RepositoryTreeModel.getInstance().saveManifest();
            }
            
            // Notify deleted
            RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_DELETED, getRepository());
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Delete Model", ex); //$NON-NLS-1$
        }
    }
}
