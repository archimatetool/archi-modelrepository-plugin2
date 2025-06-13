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
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Push Model Workflow ("Publish")
 * 
 * @author Phillip Beauvoir
 */
public class PushModelWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(PushModelWorkflow.class.getName());
    
    public PushModelWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    @Override
    public void run() {
        // TODO: Refresh (fetch) and resolve any merge conflicts first
        
        // TODO: This (and model dirty/needs committing) should be done in Refresh (fetch)
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
        
        PushResult pushResult = null;
        
        try {
            pushResult = push(npw);
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Push", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.PushModelWorkflow_0, ex);
        }
        finally {
            // Notify listeners
            notifyChangeListeners(IRepositoryListener.HISTORY_CHANGED); // This will also update Branches View
        }
        
        // Show result
        if(pushResult != null) {
            showPushResult(pushResult);
        }
    }
    
    /**
     * Push to Remote
     */
    private PushResult push(UsernamePassword npw) throws Exception {
        logger.info("Pushing to " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        PushResult[] pushResult = new PushResult[1];
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
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
        for(RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
            logger.info("PushResult status for " + refUpdate.getRemoteName() + ": " +refUpdate.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        Status status = GitUtils.getPrimaryPushResultStatus(pushResult);
        
        // OK
        if(status == Status.OK) {
            MessageDialog.openInformation(workbenchWindow.getShell(), Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_2);
        }
        // Up to date
        else if(status == Status.UP_TO_DATE) {
            MessageDialog.openInformation(workbenchWindow.getShell(), Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_3);
        }
        // Ugh!
        else {
            String errorMessage = GitUtils.getPushResultErrorMessage(pushResult);
            if(errorMessage != null) {
                logger.warning("Push returned errors: " + errorMessage); //$NON-NLS-1$
                displayErrorDialog(Messages.PushModelWorkflow_0, Messages.PushModelWorkflow_4, errorMessage);
            }
        }
    }
}
