/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutCommand.Stage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Refresh Model Action
 * 
 * @author Phillip Beauvoir
 */
public class RefreshModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(PushModelAction.class.getName());
    
    public RefreshModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_REFRESH));
        setText(Messages.RefreshModelAction_0);
        setToolTipText(Messages.RefreshModelAction_0);
    }

    public RefreshModelAction(IWorkbenchWindow window, IArchiRepository repository) {
        this(window);
        setRepository(repository);
    }

    @Override
    public void run() {
        if(!shouldBeEnabled()) {
            setEnabled(false);
            return;
        }
        
        // Check that there is a repository URL set
        if(!checkRemoteSet()) {
            return;
        }
        
        // Check if the model is open and needs saving
        if(!checkModelNeedsSaving()) {
            return;
        }

        // Check if there are uncommitted changes
        if(!checkIfCommitNeeded()) {
            return;
        }

        // Get credentials if HTTP
        UsernamePassword npw = null;
        try {
            if(RepoUtils.isHTTP(getRepository().getRemoteURL())) {
                npw = getUsernamePassword();
                if(npw == null) { // User cancelled or there are no stored credentials
                    return;
                }
            }
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "User Details", ex); //$NON-NLS-1$
            return;
        }

        PullResult pullResult = null;
        
        try {
            pullResult = pull(npw);
        }
        catch(Exception ex) {
            // If this exception is thrown then the remote doesn't have the current branch ref
            // TODO: quietly absorb this?
            if(ex instanceof RefNotAdvertisedException) {
                logger.warning(ex.getMessage());
                MessageDialog.openWarning(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_1);
            }
            else {
                closeModel(false); // Safety precaution
                displayErrorDialog(Messages.RefreshModelAction_0, ex);
                logger.log(Level.SEVERE, "Pull", ex); //$NON-NLS-1$
            }
            
            return;
        }

        // Check for tracking updates
        boolean hasTrackingRefUpdates = pullResult.getFetchResult() != null && !pullResult.getFetchResult().getTrackingRefUpdates().isEmpty();
        if(hasTrackingRefUpdates) {
            logger.info("Found new tracking ref updates."); //$NON-NLS-1$
        }

        MergeStatus mergeStatus = pullResult.getMergeResult().getMergeStatus();
        logger.info("Merge Status is: " + mergeStatus); //$NON-NLS-1$
        
        // Merge is already up to date...
        if(mergeStatus == MergeStatus.ALREADY_UP_TO_DATE) {
            // No new tracked refs, nothing to update in the UI
            if(!hasTrackingRefUpdates) {
                MessageDialog.openInformation(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_2);
                return;
            }
        }
        // Merge failure
        else if(mergeStatus == MergeStatus.CONFLICTING) {
            // The following is temporary code
            // TODO: Handle conflicts in a dialog and show logical diff
            
            int response = MessageDialog.open(MessageDialog.QUESTION,
                    fWindow.getShell(),
                    "Refresh", //$NON-NLS-1$
                    "Bummer, there's a conflict. What do you want from life?", //$NON-NLS-1$
                    SWT.NONE,
                    "My stuff", //$NON-NLS-1$
                    "Their stuff", //$NON-NLS-1$
                    "Cancel"); //$NON-NLS-1$

            // Cancel
            if(response == -1 || response == 2) {
                // Reset and clear
                try {
                    getRepository().resetToRef(Constants.HEAD);
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                }
                return;
            }

            try(GitUtils utils = GitUtils.open(getRepository().getWorkingFolder())) {
                Git git = utils.getGit();
                
                // Take ours or theirs
                checkout(git, response == 0 ? Stage.OURS : Stage.THEIRS, new ArrayList<>(pullResult.getMergeResult().getConflicts().keySet()));
                
                // Commit
                if(utils.hasChangesToCommit()) {
                    String mergeMessage = NLS.bind("Merge branch ''{0}'' with conflicts solved", git.getRepository().getBranch()); //$NON-NLS-1$
                    
                    // TODO: I'm not really clear about the consequences of setting "amend" to true, there may be an advantage
                    //       Setting to true does eliminate some unneccessary commits, but they are lost from the history
                    utils.commitChanges(mergeMessage, false);
                }
            }
            catch(IOException | GitAPIException ex) {
                ex.printStackTrace();
            }
        }
        // Pull not successful, could be MergeStatus.FAILED, MergeStatus.ABORTED, MergeStatus.NOT_SUPPORTED or MergeStatus.CHECKOUT_CONFLICT
        else if(!pullResult.isSuccessful()) {
            // Reset and clear
            try {
                getRepository().resetToRef(Constants.HEAD);
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Reset", ex); //$NON-NLS-1$
            }
            
            displayErrorDialog(Messages.RefreshModelAction_0, Messages.RefreshModelAction_4 + " " + mergeStatus); //$NON-NLS-1$
            return;
        }
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED); // This will also update Branches View
        
        // Close model
        OpenModelState modelState = closeModel(false);

        // Successful git merge on Pull, but is it a successful logical merge?
        
        // At this point the remote branch will have been merged and will consist of one or more commits.
        // So we need to actually load the broken model (ignoring any load exceptions) and see what's missing/unresolved.
        
        // A side-effect of pulling a broken model and it being in the git history is that "Extact Model from this Commit"
        // and "Restore to this Commit" and "Undo Last Commit" will extract a corrupt model.
        
        // TODO: Resolve this
        // For now we'll leave it up to you to sort out manually...
        if(!isModelIntegral()) {
            MessageDialog.openError(fWindow.getShell(), Messages.RefreshModelAction_0, "Model is not integral! You need to fix this manually."); //$NON-NLS-1$
            return;
        }
        
        // Open model
        restoreModel(modelState);
        
        // Show good vibes
        if(pullResult.isSuccessful()) {
            MessageDialog.openInformation(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_5);
        }
    }
    
    private PullResult pull(UsernamePassword npw) throws Exception {
        logger.info("Pulling from " + getRepository().getRemoteURL()); //$NON-NLS-1$

        // Store exception
        Exception[] exception = new Exception[1];
        
        PullResult[] pullResult = new PullResult[1];

        // If using this be careful that no UI operations are peformed as this could lead to an SWT Invalid thread access exception
        PlatformUI.getWorkbench().getProgressService().busyCursorWhile(monitor -> {
            try {
                monitor.beginTask(Messages.RefreshModelAction_3, IProgressMonitor.UNKNOWN);
                pullResult[0] = getRepository().pullFromRemote(npw, new ProgressMonitorWrapper(monitor));
            }
            catch(IOException | GitAPIException ex) {
                exception[0] = ex;
            }
        });
        
        if(exception[0] != null) {
            throw exception[0];
        }
        
        return pullResult[0];
    }
    
    /**
     * Check out conflicting files either from us or them
     */
    private void checkout(Git git, Stage stage, List<String> paths) throws GitAPIException {
        CheckoutCommand checkoutCommand = git.checkout();
        checkoutCommand.setStage(stage);
        checkoutCommand.addPaths(paths);
        checkoutCommand.call();
    }
}
