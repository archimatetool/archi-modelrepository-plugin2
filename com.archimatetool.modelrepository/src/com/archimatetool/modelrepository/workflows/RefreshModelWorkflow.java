/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

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

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.MergeHandler;
import com.archimatetool.modelrepository.repository.MergeHandler.MergeHandlerResult;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Refresh Model Workflow
 * 
 * @author Phillip Beauvoir
 */
public class RefreshModelWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(RefreshModelWorkflow.class.getName());
    
    public RefreshModelWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    @Override
    public void run() {
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
            if(RepoUtils.isHTTP(archiRepository.getRemoteURL())) {
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
                MessageDialog.openWarning(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_1);
            }
            else {
                logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.RefreshModelWorkflow_0, ex);
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
            BranchInfo remoteBranchInfo = BranchInfo.currentRemoteBranchInfo(archiRepository.getWorkingFolder(), false);
            
            // There wasn't a remote tracked branch to merge
            if(remoteBranchInfo == null) {
                if(hasTrackingRefUpdates) {
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                }
                
                MessageDialog.openInformation(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_2);
                return;
            }
            
            // Try to merge
            mergeHandlerResult = MergeHandler.getInstance().merge(archiRepository, remoteBranchInfo);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RefreshModelWorkflow_0, ex);
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
            MessageDialog.openInformation(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_2);
            return;
        }
        
        // MERGED_OK or MERGED_WITH_CONFLICTS_RESOLVED
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        // Close and re-open model
        closeAndRestoreModel();
        
        if(mergeHandlerResult == MergeHandlerResult.MERGED_OK) {
            MessageDialog.openInformation(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_3);
        }
    }
    
    private FetchResult fetch(UsernamePassword npw) throws Exception {
        logger.info("Fetching from " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        FetchResult[] fetchResult = new FetchResult[1];

        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.RefreshModelWorkflow_4, IProgressMonitor.UNKNOWN);
            fetchResult[0] = archiRepository.fetchFromRemote(npw, new ProgressMonitorWrapper(monitor), false);
        }, true);

        return fetchResult[0];
    }
}
