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
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryConstants;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Discard Uncommitted Changes Action
 * 
 * @author Phillip Beauvoir
 */
public class DiscardChangesAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(DiscardChangesAction.class.getName());
    
    public DiscardChangesAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_ABORT));
        setText(Messages.DiscardChangesAction_0);
        setToolTipText(Messages.DiscardChangesAction_0);
    }

    public DiscardChangesAction(IWorkbenchWindow window, IArchiRepository repository) {
        this(window);
        setRepository(repository);
    }

    @Override
    public void run() {
        logger.info("Discarding uncommitted changes..."); //$NON-NLS-1$
        
        try {
            if(!getRepository().hasChangesToCommit()) {
                MessageDialog.openInformation(fWindow.getShell(),
                        Messages.DiscardChangesAction_0,
                        Messages.DiscardChangesAction_1);
                logger.info("Nothing to discard"); //$NON-NLS-1$
                return;
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit Changes", ex); //$NON-NLS-1$
        }
        
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.DiscardChangesAction_0,
                Messages.DiscardChangesAction_2)) {
            return;
        }
        
        OpenEditorManager openEditorManager = new OpenEditorManager();
        
        // Close the model if it's open
        IArchimateModel model = getRepository().getModel();
        if(model != null) {
            try {
                // Store any open diagrams
                openEditorManager.saveEditors(model);
                
                // Close it
                logger.info("Closing model"); //$NON-NLS-1$
                if(!IEditorModelManager.INSTANCE.closeModel(model)) {
                    return;
                }
            }
            catch(IOException ex) {
                logger.log(Level.SEVERE, "Closing model", ex); //$NON-NLS-1$
                return;
            }
        }
        
        // Reset to HEAD
        try {
            logger.info("Resetting to HEAD"); //$NON-NLS-1$
            getRepository().resetToRef(IRepositoryConstants.HEAD);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Reset to HEAD", ex); //$NON-NLS-1$
        }
        
        // Open the model if it was open and any open editors
        if(model != null) {
            logger.info("Opening model"); //$NON-NLS-1$
            model = IEditorModelManager.INSTANCE.openModel(getRepository().getModelFile());
            openEditorManager.restoreEditors(model);
        }
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
}
