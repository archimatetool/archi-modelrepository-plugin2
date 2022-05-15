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
 * Delete local repo folder
 * 
 * @author Phillip Beauvoir
 */
public class DeleteModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(DeleteModelAction.class.getName());
    
    public DeleteModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_DELETE));
        setText(Messages.DeleteModelAction_0);
        setToolTipText(Messages.DeleteModelAction_1);
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

        if(!MessageDialog.openConfirm(fWindow.getShell(), Messages.DeleteModelAction_0, Messages.DeleteModelAction_2)) {
            return;
        }
        
        try {
            // Close model without asking to save
            IEditorModelManager.INSTANCE.closeModel(getRepository().getModel(), false);
            
            // Notify closing
            RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_CLOSING, getRepository());

            // Delete folder
            FileUtils.deleteFolder(getRepository().getWorkingFolder());
            
            // Delete from Tree Model
            RepositoryRef ref = RepositoryTreeModel.getInstance().findRepositoryRef(getRepository().getWorkingFolder());
            if(ref != null) {
                ref.delete();
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
