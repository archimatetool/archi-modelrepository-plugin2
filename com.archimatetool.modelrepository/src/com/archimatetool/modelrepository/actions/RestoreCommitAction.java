/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IRepositoryConstants;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Restore to a particular commit
 */
public class RestoreCommitAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(RestoreCommitAction.class.getName());
    
    private RevCommit fCommit;
	
    public RestoreCommitAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_SYNCED));
        setText(Messages.RestoreCommitAction_0);
        setToolTipText(Messages.RestoreCommitAction_0);
    }

    public void setCommit(RevCommit commit) {
        fCommit = commit;
        setEnabled(shouldBeEnabled());
    }
    
    @Override
    public void run() {
        logger.info("Restoring to a commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.RestoreCommitAction_0,
                Messages.RestoreCommitAction_1)) {
            return;
        }
        
        // Close the model if it's open in the tree
        OpenModelState modelState = closeModel();
        if(modelState.cancelled()) {
            return;
        }
        
        // Delete the working directory
        logger.info("Deleting contents of working directory"); //$NON-NLS-1$
        
        try {
            RepoUtils.deleteContentsOfGitRepository(getRepository().getLocalRepositoryFolder());
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Delete files", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RestoreCommitAction_0, ex);
            return;
        }
        
        // Extract the contents of the commit
        try {
            extractCommit();
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Extract commit", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RestoreCommitAction_0, ex);
            return;
        }
        
        // Check that we actually restored the model in case there is no model file in this commit
        if(!getRepository().getModelFile().exists()) {
            try {
                displayErrorDialog(Messages.RestoreCommitAction_0, Messages.RestoreCommitAction_2);
                // Reset to HEAD
                getRepository().resetToRef(IRepositoryConstants.HEAD);
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Reset to HEAD", ex); //$NON-NLS-1$
            }
            
            // And reload the model
            restoreModel(modelState);
            
            return;
        }
        
        // Reload the model
        restoreModel(modelState);
        
        // Commit changes
        logger.info("Committing changes..."); //$NON-NLS-1$
        
        try {
            getRepository().commitChanges(Messages.RestoreCommitAction_3 + " '" + fCommit.getShortMessage() + "'", false); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Commit", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RestoreCommitAction_4, ex);
        }
        
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        try {
            return super.shouldBeEnabled() && !isCommitLocalHead();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return false;
    }
    
    /**
     * Walk the tree and extract the contents of the commit
     */
    private void extractCommit() throws IOException {
        try(Repository repository = Git.open(getRepository().getLocalRepositoryFolder()).getRepository()) {
            try(TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(fCommit.getTree());
                treeWalk.setRecursive(true);

                while(treeWalk.next()) {
                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);
                    
                    File file = new File(getRepository().getLocalRepositoryFolder(), treeWalk.getPathString());
                    file.getParentFile().mkdirs();
                    
                    try(FileOutputStream out = new FileOutputStream(file)) {
                        loader.copyTo(out);
                    }
                }
            }
        }
    }
    
    private boolean isCommitLocalHead() throws IOException {
        if(fCommit == null) {
            return false;
        }
        
        try(Repository repo = Git.open(getRepository().getLocalRepositoryFolder()).getRepository()) {
            ObjectId headID = repo.resolve(IRepositoryConstants.HEAD);
            ObjectId commitID = fCommit.getId();
            return commitID.equals(headID);
        }
    }
}
