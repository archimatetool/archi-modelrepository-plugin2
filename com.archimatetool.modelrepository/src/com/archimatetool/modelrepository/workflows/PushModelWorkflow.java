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
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.ICredentials;
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
        
        PushResult pushResult = null;
        
        try {
            pushResult = push(credentials.getCredentialsProvider());
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
     * Push to Remote
     */
    private PushResult push(CredentialsProvider credentialsProvider) throws Exception {
        logger.info("Pushing to " + archiRepository.getRemoteURL()); //$NON-NLS-1$

        PushResult[] pushResult = new PushResult[1];
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
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
