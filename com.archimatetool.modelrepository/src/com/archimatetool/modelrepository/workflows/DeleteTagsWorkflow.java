/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.TagInfo;

/**
 * Delete Tags Workflow
 * 
 * @author Phillip Beauvoir
 */
public class DeleteTagsWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(DeleteTagsWorkflow.class.getName());
    
    private TagInfo[] tagInfos;
    
    public DeleteTagsWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository repository, TagInfo... tagInfos) {
        super(workbenchWindow, repository);
        this.tagInfos = tagInfos;
    }

    @Override
    public void run() {
        // Check that there is a repository URL set
        if(!checkRemoteSet()) {
            return;
        }

        // Confirm
        if(!MessageDialog.openConfirm(workbenchWindow.getShell(),
                Messages.DeleteTagsWorkflow_0,
                Messages.DeleteTagsWorkflow_1)) {
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
        catch(Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Get Credentials", ex); //$NON-NLS-1$
            return;
        }
        
        try {
            deleteTags(npw, tagInfos);
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Delete Branch", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.DeleteBranchWorkflow_0, ex);
        }
        finally {
            notifyChangeListeners(IRepositoryListener.TAGS_CHANGED);
        }
    }
    
    private void deleteTags(UsernamePassword npw, TagInfo... tagInfos) throws Exception {
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.DeleteTagsWorkflow_0, IProgressMonitor.UNKNOWN);
            
            String[] tagNames = Arrays.stream(tagInfos).map(TagInfo::getFullName).toArray(String[]::new);
            String names = String.join(", ", tagNames); //$NON-NLS-1$

            try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
                // Delete the remote tags
                logger.info("Deleting remote tags: " + names); //$NON-NLS-1$
                PushResult pushResult = utils.deleteRemoteTags(npw, new ProgressMonitorWrapper(monitor, Messages.DeleteTagsWorkflow_0), tagNames);
                
                // Logging
                for(String msg : GitUtils.getPushResultMessageList(pushResult)) {
                    logger.info(msg);
                }
                
                // Get any errors in Push Result and throw exception
                Status status = GitUtils.getPrimaryPushResultStatus(pushResult);
                
                if(status != Status.OK && status != Status.UP_TO_DATE && status != Status.NON_EXISTING) {
                    String errorMessage = GitUtils.getPushResultFullErrorMessage(pushResult);
                    if(errorMessage == null) {
                        errorMessage = "Unknown error"; //$NON-NLS-1$
                    }
                    throw new GitAPIException(errorMessage) {};
                }

                // If OK, delete local tags
                logger.info("Deleting local tags: " + names); //$NON-NLS-1$
                utils.deleteTags(tagNames);
            }
        }, true);
    }
    
    @Override
    public boolean canRun() {
        return tagInfos != null && tagInfos.length != 0 && super.canRun();
    }
}
