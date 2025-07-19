/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.merge.MergeHandler;
import com.archimatetool.modelrepository.merge.MergeHandler.MergeHandlerResult;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;

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

        // Get credentials
        ICredentials credentials = getCredentials();
        if(credentials == null) {
            return;
        }
        
        // Fetch from Remote
        // The first FetchResult will be for branches and the second for tags.
        List<FetchResult> fetchResults = null;
        try {
            fetchResults = fetch(credentials.getCredentialsProvider());
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.RefreshModelWorkflow_0, ex);
            return;
        }
        
        logFetchResults(fetchResults);
        
        // Check if there are either branch or tag tracking updates
        boolean hasTrackingRefUpdates = !(fetchResults.get(0).getTrackingRefUpdates().isEmpty() &&
                                          fetchResults.get(1).getTrackingRefUpdates().isEmpty());

        MergeHandlerResult mergeHandlerResult;
        
        try {
            // Get the remote tracking branch info
            BranchInfo remoteBranchInfo = BranchInfo.currentRemoteBranchInfo(archiRepository.getWorkingFolder());
            
            // There wasn't a remote tracked branch to merge or there is but it's at HEAD
            if(remoteBranchInfo == null || remoteBranchInfo.isRefAtHead()) {
                if(hasTrackingRefUpdates) {
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                }
                
                MessageDialog.openInformation(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_2);
                return;
            }
            
            // Check whether HEAD and the remote branch share a base commit
            // If they don't it means that HEAD is on an orphaned branch probably because
            // user set a remote URL containing a different model
            try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
                RevCommit mergeBase = utils.getBaseCommit(RepoConstants.HEAD, remoteBranchInfo.getRemoteBranchName());
                if(mergeBase == null) {
                    notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
                    displayErrorDialog(Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_6 + ' '
                                        + Messages.RefreshModelWorkflow_7 + "\n\n" + Messages.RefreshModelWorkflow_8); //$NON-NLS-1$
                    return;
                }
            }
            
            // Try to merge
            mergeHandlerResult = MergeHandler.getInstance().merge(archiRepository, remoteBranchInfo);
        }
        catch(IOException | GitAPIException ex) {
            logger.log(Level.SEVERE, "Merge", ex); //$NON-NLS-1$
            
            // Reset to HEAD in case of repo being in temporary merge state or other bad state
            try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
                logger.info("Resetting to HEAD."); //$NON-NLS-1$
                utils.resetToRef(RepoConstants.HEAD);
            }
            catch(IOException | GitAPIException ex1) {
                ex1.printStackTrace();
                logger.log(Level.SEVERE, "Merge", ex1); //$NON-NLS-1$
            }
            
            notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
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
        
        // Close and re-open model *before* notification
        closeAndRestoreModel();
        
        // Notify
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED);
        
        if(mergeHandlerResult == MergeHandlerResult.MERGED_OK) {
            MessageDialog.openInformation(workbenchWindow.getShell(), Messages.RefreshModelWorkflow_0, Messages.RefreshModelWorkflow_5);
        }
    }
    
    private List<FetchResult> fetch(CredentialsProvider credentialsProvider) throws Exception {
        logger.info("Fetching from " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        AtomicReference<List<FetchResult>> fetchResults = new AtomicReference<>();

        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.RefreshModelWorkflow_4, IProgressMonitor.UNKNOWN);
            fetchResults.set(archiRepository.fetchFromRemote(credentialsProvider, new ProgressMonitorWrapper(monitor, Messages.RefreshModelWorkflow_4), true));
        }, true);

        return fetchResults.get();
    }
    
    private void logFetchResults(List<FetchResult> fetchResults) {
        if(fetchResults != null) {
            for(FetchResult fetchResult : fetchResults) {
                // Remove zero byte from message
                String msg = fetchResult.getMessages().replace("\0", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
                if(StringUtils.isSet(msg)) {
                    logger.info("FetchResult Message: " + msg); //$NON-NLS-1$
                }
                for(TrackingRefUpdate refUpdate : fetchResult.getTrackingRefUpdates()) {
                    logger.info(refUpdate.toString());
                }
            }
        }
    }
}
