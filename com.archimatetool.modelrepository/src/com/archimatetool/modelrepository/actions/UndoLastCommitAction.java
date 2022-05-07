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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Undo the last commit
 */
public class UndoLastCommitAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(UndoLastCommitAction.class.getName());
    
    public UndoLastCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_UNDO_COMMIT));
        setText(Messages.UndoLastCommitAction_0);
        setToolTipText(Messages.UndoLastCommitAction_0);
    }

    @Override
    public void run() {
        logger.info("Undoing last commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.UndoLastCommitAction_0,
                Messages.UndoLastCommitAction_1)) {
            return;
        }
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel();
        if(modelState.cancelled()) {
            return;
        }

        try {
            logger.info("Resetting to HEAD^"); //$NON-NLS-1$
            getRepository().resetToRef("HEAD^"); //$NON-NLS-1$
        }
        catch(Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to HEAD^", ex); //$NON-NLS-1$
        }
        
        // Open the model if it was open before and any open editors
        restoreModel(modelState);
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        if(!super.shouldBeEnabled()) {
            return false;
        }
        
        try {
            // If HEAD commit count is 1 or 0 then there's nothing to undo
            if(!hasMoreThanOneCommit()) {
                return false;
            }

            // Otherwise head and remote should be different
            return !getRepository().isHeadAndRemoteSame();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Reset to HEAD^", ex); //$NON-NLS-1$
        }
        
        return false;
    }
    
    /**
     * Walk the commit tree and count commits untik we get more than one
     */
    protected boolean hasMoreThanOneCommit() throws IOException {
        int count = 0;

        // If HEAD commit count is 1 then there's nothing to undo
        try(Repository repository = Git.open(getRepository().getLocalRepositoryFolder()).getRepository()) {
            try(RevWalk revWalk = new RevWalk(repository)) {
                // We are interested in the HEAD
                ObjectId objectID = repository.resolve(Constants.HEAD);
                if(objectID == null) { // can be null!
                    revWalk.dispose();
                    return false;
                }
                
                revWalk.markStart(revWalk.parseCommit(objectID));
                
                while(revWalk.next() != null) {
                    count++;
                    if(count > 1) {
                        break;
                    }
                }
                
                revWalk.dispose();
            }
        }
        
        return count > 1;
    }
}
