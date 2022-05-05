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
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel();
        if(modelState.cancelled()) {
            return;
        }
        
        // Reset to HEAD
        try {
            logger.info("Resetting to HEAD"); //$NON-NLS-1$
            getRepository().resetToRef(IRepositoryConstants.HEAD);
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to HEAD", ex); //$NON-NLS-1$
        }
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
}
