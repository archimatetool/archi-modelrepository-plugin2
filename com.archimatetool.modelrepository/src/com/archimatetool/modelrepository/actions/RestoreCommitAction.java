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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
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
        if(fCommit != commit) {
            fCommit = commit;
            setEnabled(shouldBeEnabled());
        }
    }
    
    @Override
    public void setRepository(IArchiRepository repository) {
        fCommit = null;
        super.setRepository(repository);
    }
    
    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }

        logger.info("Restoring to a commit..."); //$NON-NLS-1$
        
        if(!MessageDialog.openConfirm(fWindow.getShell(),
                Messages.RestoreCommitAction_0,
                Messages.RestoreCommitAction_1)) {
            return;
        }
        
        try {
            // Delete the contents of the working directory
            logger.info("Deleting contents of working directory"); //$NON-NLS-1$
            RepoUtils.deleteContentsOfGitRepository(getRepository().getWorkingFolder());
            
            // Extract the contents of the commit
            logger.info("Extracting the oommit"); //$NON-NLS-1$
            getRepository().extractCommit(fCommit, getRepository().getWorkingFolder(), false);
            
            // Check that we actually restored the model in case there is no model file in this commit
            if(!getRepository().getModelFile().exists()) {
                throw new IOException(Messages.RestoreCommitAction_2);
            }
            
            // Commit changes
            logger.info("Committing changes..."); //$NON-NLS-1$
            getRepository().commitChanges(Messages.RestoreCommitAction_3 + " '" + fCommit.getShortMessage() + "'", false); //$NON-NLS-1$ //$NON-NLS-2$
            
            notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Restore to Commit", ex); //$NON-NLS-1$
            try {
                getRepository().resetToRef(Constants.HEAD);
            }
            catch(IOException | GitAPIException ex1) {
                logger.log(Level.SEVERE, "Reset to HEAD", ex1); //$NON-NLS-1$
            }
            displayErrorDialog(Messages.RestoreCommitAction_0, ex);
        }
        finally {
            // Close and re-open model
            OpenModelState modelState = closeModel(false);
            restoreModel(modelState);
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        if(fCommit == null || !super.shouldBeEnabled()) {
            return false;
        }
        
        try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
            // Commit is not at HEAD and is part of the local branch's history (HEAD)
            return !utils.isCommitAtHead(fCommit) && utils.isMergedInto(fCommit.getName(), Constants.HEAD);
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Enabled state", ex); //$NON-NLS-1$
        }
        
        return false;
    }
}
