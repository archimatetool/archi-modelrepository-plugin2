/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.util.EnumSet;
import java.util.logging.Logger;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Workflow that does a Push operation 
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractPushResultWorkflow extends AbstractRepositoryWorkflow {
    
    protected static final EnumSet<Status> ALLOWED = EnumSet.of(Status.OK, Status.UP_TO_DATE, Status.NON_EXISTING);

    protected AbstractPushResultWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    /**
     * Log all messages from the PushResult
     */
    protected void logPushResult(PushResult pushResult, Logger logger) {
        if(pushResult == null) {
            return;
        }
        
        for(RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
            logger.info("PushResult status for " + refUpdate.getRemoteName() + ": " + refUpdate.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
            if(refUpdate.getMessage() != null) {
                logger.info("RefUpdate message: " + refUpdate.getMessage()); //$NON-NLS-1$
            }
        }
        
        String msg = getSanitisedPushResultMessages(pushResult);
        if(StringUtils.isSet(msg)) {
            logger.info("Message: " + msg); //$NON-NLS-1$
        }
    }
    
    /**
     * @return the primary PushResult Status or null if there isn't one.
     * If we are pushing just the current branch there will be just one ref update in the PushResult and one Status.
     * If pushing tags or more than one branch there can be more than one ref update.
     */
    protected Status getPrimaryPushResultStatus(PushResult pushResult) {
        if(pushResult == null) {
            return null;
        }
        
        Status status = null;
        
        // Iterate thru all pushed refs, current branch and tags, and get the primary one
        for(RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
            switch(refUpdate.getStatus()) {
                // OK over-rides UP_TO_DATE and means remote ref was updated
                case OK -> {
                    status = Status.OK;
                }
                
                // UP_TO_DATE is secondary and means remote ref was up to date
                // NON_EXISTING is secondary and can occur when when deleting a missing remote branch or tag
                case UP_TO_DATE, NON_EXISTING -> {
                    if(status != Status.OK) {
                        status = refUpdate.getStatus();
                    }
                }
                
                // Other status, so return it immediately
                default -> {
                    return refUpdate.getStatus();
                }
            }
        }
        
        return status;
    }
    
    /**
     * Check the primary push result status and if the Status is not OK or UP_TO_DATE or NON_EXISTING throw a GitAPIException
     */
    protected void checkPushResultStatus(PushResult pushResult) throws GitAPIException {
        // Get any errors in Push Result and throw exception
        Status status = getPrimaryPushResultStatus(pushResult);
        
        if(!ALLOWED.contains(status)) {
            String errorMessage = getPushResultFullErrorMessage(pushResult);
            
            if(errorMessage == null) {
                errorMessage = "Unknown error"; //$NON-NLS-1$
            }
            
            throw new GitAPIException(errorMessage) {};
        }
    }
    
    /**
     * Get a formatted full error message from the PushResult or null
     */
    protected String getPushResultFullErrorMessage(PushResult pushResult) {
        if(pushResult == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        
        pushResult.getRemoteUpdates().stream()
                  .filter(refUpdate -> !ALLOWED.contains(refUpdate.getStatus()))        // Ignore OK, UP_TO_DATE and NON_EXISTING
                  .forEach(refUpdate -> {
                      sb.append(refUpdate.getStatus().name()); // Status enum name
                      sb.append('\n');
                      sb.append(refUpdate.getRemoteName());    // Remote ref name
                      
                      if(refUpdate.getMessage() != null) {
                          sb.append('\n');
                          sb.append(refUpdate.getMessage());
                      }
                      
                      String msgs = getSanitisedPushResultMessages(pushResult);
                      if(StringUtils.isSet(msgs)) {
                          sb.append('\n');
                          sb.append(msgs);
                      }
                  });
            
        String result = sb.toString().trim();
        return result.length() > 1 ? result : null; // 1 character == "\n"
    }
    
    /**
     * There is a bug in JGit where the first byte of PushResult's message can be a zero byte.
     * This means that the message won't display in a dialog box on Windows, so we remove it.
     * @return The sanitised messages from a PushResult, or null.
     */
    private String getSanitisedPushResultMessages(PushResult pushResult) {
        return pushResult.getMessages().replace("\0", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
