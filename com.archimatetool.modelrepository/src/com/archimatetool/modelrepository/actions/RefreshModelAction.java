/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.MergeHandler;
import com.archimatetool.modelrepository.repository.MergeHandler.MergeHandlerResult;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Refresh Model Action
 * 
 * @author Phillip Beauvoir
 */
public class RefreshModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(RefreshModelAction.class.getName());
    
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
        catch(IOException | GitAPIException | StorageException ex) {
            logger.log(Level.SEVERE, "User Details", ex); //$NON-NLS-1$
            return;
        }

        // Fetch from Remote
        FetchResult fetchResult = null;
        try {
            fetchResult = fetch(npw);
        }
        catch(Exception ex) {
            // If this exception is thrown then the remote doesn't have the current branch ref
            // TODO: quietly absorb this?
            if(ex instanceof RefNotAdvertisedException) {
                logger.warning(ex.getMessage());
                MessageDialog.openWarning(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_1);
            }
            else {
                displayErrorDialog(Messages.RefreshModelAction_0, ex);
                logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            }
            
            return;
        }
        
        // Check for tracking updates
        boolean hasTrackingRefUpdates = !fetchResult.getTrackingRefUpdates().isEmpty();
        if(hasTrackingRefUpdates) {
            logger.info("Found new tracking ref updates."); //$NON-NLS-1$
        }

        MergeHandlerResult mergeHandlerResult = MergeHandlerResult.MERGED_OK;
        
        try {
            // Get the remote tracking branch info
            BranchInfo remoteBranchInfo = BranchInfo.currentRemoteBranchInfo(getRepository().getWorkingFolder(), false);
            
            // There wasn't a remote tracked branch to merge
            if(remoteBranchInfo == null) {
                if(hasTrackingRefUpdates) {
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                }
                
                MessageDialog.openInformation(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_2);
                return;
            }
            
            // Try to merge
            mergeHandlerResult = MergeHandler.getInstance().merge(getRepository(), remoteBranchInfo);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RefreshModelAction_0, ex);
            return;
        }
        
        // User cancelled
        if(mergeHandlerResult == MergeHandlerResult.CANCELLED) {
            if(hasTrackingRefUpdates) {
                notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
            }
            return;
        }

        // Merge is already up to date
        if(mergeHandlerResult == MergeHandlerResult.ALREADY_UP_TO_DATE) {
            if(hasTrackingRefUpdates) {
                notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
            }
            MessageDialog.openInformation(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_2);
            return;
        }
        
        // MERGED_OK or MERGED_WITH_CONFLICTS_RESOLVED
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        // Close and re-open model
        OpenModelState modelState = closeModel(false);
        restoreModel(modelState);
        
        if(mergeHandlerResult == MergeHandlerResult.MERGED_OK) {
            MessageDialog.openInformation(fWindow.getShell(), Messages.RefreshModelAction_0, Messages.RefreshModelAction_5);
        }
    }
    
    private FetchResult fetch(UsernamePassword npw) throws Exception {
        logger.info("Fetching from " + getRepository().getRemoteURL()); //$NON-NLS-1$

        FetchResult[] fetchResult = new FetchResult[1];

        ProgressMonitorDialog dialog = new ProgressMonitorDialog(fWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.RefreshModelAction_3, IProgressMonitor.UNKNOWN);
            fetchResult[0] = getRepository().fetchFromRemote(npw, new ProgressMonitorWrapper(monitor), false);
        }, true);

        return fetchResult[0];
    }
}
