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
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.Dialogs;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoConstants;
import com.archimatetool.modelrepository.repository.RepoUtils;

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
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        // Check if remote is ahead or Fetch needed first
        try(GitUtils utils = GitUtils.open(archiRepository.getGitFolder())) {
            // If there are ahead commits...
            BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(archiRepository.getGitFolder(), Option.COMMIT_STATUS);
            if(branchInfo.hasRemoteCommits()) {
                int response = Dialogs.openYesNoCancelDialog(workbenchWindow.getShell(), Messages.PushModelWorkflow_0,
                        "There are new updates on the remote repository, do you want to Refresh first?");
                
                if(response == SWT.YES) {
                    RefreshModelWorkflow workflow = new RefreshModelWorkflow(workbenchWindow, archiRepository);
                    workflow.run();
                    return;
                }
                
                if(response == SWT.CANCEL) {
                    return;
                }
            }
            
            FetchResult fetchResult = fetchDryRun(npw, dialog);
            if(!fetchResult.getTrackingRefUpdates().isEmpty()) {
                int response = Dialogs.openYesNoCancelDialog(workbenchWindow.getShell(), Messages.PushModelWorkflow_0,
                        "There are new updates on the remote repository, do you want to Refresh first?");
                
                if(response == SWT.YES) {
                    RefreshModelWorkflow workflow = new RefreshModelWorkflow(workbenchWindow, archiRepository);
                    workflow.run();
                    return;
                }
                
                if(response == SWT.CANCEL) {
                    return;
                }
            }
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.PushModelWorkflow_0, ex);
            return;
        }
        
        try {
            PushResult pushResult = push(npw, dialog);
            
            // Logging
            logPushResult(pushResult, logger);
            
            // Show result
            showPushResult(pushResult);
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Push", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.PushModelWorkflow_0, ex);
        }
        
        // Notify listeners
        notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED); // This will also update Branches View
    }
    
    /**
     * Do a Fetch update
     */
    private FetchResult fetchDryRun(UsernamePassword npw, ProgressMonitorDialog dialog) throws Exception {
        logger.info("Fetching with dry run from " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        FetchResult[] fetchResult = new FetchResult[1];
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask("Checking remote for updates...", IProgressMonitor.UNKNOWN);
            
            try(GitUtils utils = GitUtils.open(archiRepository.getGitFolder())) {
                String currentBranch = utils.getRepository().getBranch();
                String refSpec = RepoConstants.R_HEADS + currentBranch + ":" + 
                                            RepoConstants.R_REMOTES_ORIGIN + currentBranch ;
                
                fetchResult[0] = utils.fetch()
                                      .setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw))
                                      .setDryRun(true)
                                      .setRefSpecs(refSpec)
                                      .call();
            }
        }, true);
        
        return fetchResult[0];
    }
    
    /**
     * Push to Remote
     */
    private PushResult push(UsernamePassword npw, ProgressMonitorDialog dialog) throws Exception {
        logger.info("Pushing to " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        PushResult[] pushResult = new PushResult[1];
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.PushModelWorkflow_1, IProgressMonitor.UNKNOWN);
            pushResult[0] = archiRepository.pushToRemote(npw, new ProgressMonitorWrapper(monitor, Messages.PushModelWorkflow_1));
        }, true);
        
        return pushResult[0];
    }
    
    /**
     * Show Push result status and any error messages
     */
    private void showPushResult(PushResult pushResult) {
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
