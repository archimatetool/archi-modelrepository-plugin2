/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.dialogs.Dialogs;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;

/**
 * Push Model Workflow ("Publish")
 * 
 * @author Phillip Beauvoir
 */
public class PushModelWorkflow extends AbstractPushResultWorkflow {
    
    private static Logger logger = Logger.getLogger(PushModelWorkflow.class.getName());
    
    public PushModelWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    @Override
    public void run() {
        if(!checkRemoteSet()) {
            return;
        }
        
        // Get credentials
        ICredentials credentials = getCredentials();
        if(credentials == null) {
            return;
        }
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        // Check if remote is ahead or Fetch needed first
        try(GitUtils utils = GitUtils.open(archiRepository.getGitFolder())) {
            // If there are ahead commits or remote updates...
            if(BranchInfo.currentLocalBranchInfo(archiRepository.getGitFolder(), Option.COMMIT_STATUS).hasRemoteCommits() ||
                    !fetchDryRun(credentials.getCredentialsProvider(), dialog).getTrackingRefUpdates().isEmpty()) {
                
                int response = Dialogs.openYesNoCancelDialog(workbenchWindow.getShell(), Messages.PushModelWorkflow_0,
                        Messages.PushModelWorkflow_5);
                
                if(response == SWT.YES) {
                    RefreshModelWorkflow workflow = new RefreshModelWorkflow(workbenchWindow, archiRepository);
                    workflow.run();
                    return;
                }
                else if(response == SWT.CANCEL) {
                    return;
                }
            }
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.PushModelWorkflow_0, ex);
            return;
        }
        
        PushResult pushResult = null;
        
        try {
            pushResult = push(credentials.getCredentialsProvider(), dialog);
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Push", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.PushModelWorkflow_0, ex);
        }
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED); // This will also update Branches View
        
        if(pushResult != null) {
            // Logging
            logPushResult(pushResult, logger);
            
            // Show result
            showPushResult(pushResult);
        }
    }
    
    /**
     * Do a Fetch update
     */
    private FetchResult fetchDryRun(CredentialsProvider credentialsProvider, ProgressMonitorDialog dialog) throws Exception {
        logger.info("Fetching with dry run from " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        FetchResult[] fetchResult = new FetchResult[1];
        
        IRunnable.run(dialog, monitor -> {
            try(GitUtils utils = GitUtils.open(archiRepository.getGitFolder())) {
                monitor.beginTask(Messages.PushModelWorkflow_6, IProgressMonitor.UNKNOWN);
                fetchResult[0] = utils.fetchFromRemoteDryRun(credentialsProvider, new ProgressMonitorWrapper(monitor, Messages.PushModelWorkflow_6));
            }
        }, true);
        
        return fetchResult[0];
    }
    
    /**
     * Push to Remote
     */
    private PushResult push(CredentialsProvider credentialsProvider, ProgressMonitorDialog dialog) throws Exception {
        logger.info("Pushing to " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        PushResult[] pushResult = new PushResult[1];
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.PushModelWorkflow_1, IProgressMonitor.UNKNOWN);
            pushResult[0] = archiRepository.pushToRemote(credentialsProvider, new ProgressMonitorWrapper(monitor, Messages.PushModelWorkflow_1));
        }, true);
        
        return pushResult[0];
    }
    
    /**
     * Show Push result status and any error messages
     */
    private void showPushResult(PushResult pushResult) {
        if(pushResult == null) {
            return;
        }

        // Show primary Status result to user
        switch(getPrimaryPushResultStatus(pushResult)) {
            case OK -> {
                MessageDialog.openInformation(workbenchWindow.getShell(), Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_2);
            }
            
            case UP_TO_DATE, NON_EXISTING -> {
                MessageDialog.openInformation(workbenchWindow.getShell(), Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_3);
            }
            
            default -> {
                String errorMessage = getPushResultFullErrorMessage(pushResult);
                if(errorMessage != null) {
                    displayErrorDialog(Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_4, errorMessage);
                }
            }
        }
    }
}
