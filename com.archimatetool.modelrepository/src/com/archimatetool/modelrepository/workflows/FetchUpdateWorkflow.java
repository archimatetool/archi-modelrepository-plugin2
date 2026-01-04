/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IRunnable;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.SSHCredentials;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

/**
 * Fetch on all repositories in the workspace
 */
public class FetchUpdateWorkflow extends AbstractRepositoryWorkflow {
    
    private static Logger logger = Logger.getLogger(FetchUpdateWorkflow.class.getName());
    
    private record ExceptionDetail(IArchiRepository repo, Exception ex) {}
    
    public FetchUpdateWorkflow(IWorkbenchWindow workbenchWindow) {
        super(workbenchWindow, null);
    }
    
    @Override
    public void run() {
        List<RepositoryRef> refs = RepositoryTreeModel.getInstance().getAllChildRepositoryRefs();
        if(refs.isEmpty()) {
            return;
        }
        
        Set<IArchiRepository> updatedRepos = new HashSet<>();
        List<ExceptionDetail> exceptions = new ArrayList<>();
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());

        try {
            IRunnable.run(dialog, true, true, monitor -> {
                ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor, Messages.FetchUpdateWorkflow_0);
                monitor.beginTask(Messages.FetchUpdateWorkflow_0, IProgressMonitor.UNKNOWN);
                
                for(RepositoryRef repoRef : refs) {
                    if(monitor.isCanceled()) {
                        break;
                    }
                    
                    IArchiRepository repository = repoRef.getArchiRepository();
                    
                    try(GitUtils utils = GitUtils.open(repository.getGitFolder())) {
                        String remoteURL = utils.getRemoteURL().orElse(null);
                        if(remoteURL != null) {
                            ICredentials credentials = RepoUtils.isHTTP(remoteURL) ?
                                                       CredentialsStorage.getInstance().getCredentials(repository) : new SSHCredentials();

                            String message = NLS.bind(Messages.FetchUpdateWorkflow_1, repository.getName(), remoteURL);
                            logger.info(message);
                            monitor.subTask(message);
                            
                            List<FetchResult> fetchResults = utils.fetchFromRemote(credentials.getCredentialsProvider(), wrapper, true);
                            
                            // If there were updates add the repo to be updated
                            for(FetchResult fetchResult : fetchResults) {
                                logFetchResult(fetchResult);
                                if(!fetchResult.getTrackingRefUpdates().isEmpty()) {
                                    updatedRepos.add(repository);
                                }
                            }
                        }
                    }
                    catch(Exception ex) { 
                        // Catch exceptions here and collect them so we can continue to fetch all repos
                        logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
                        exceptions.add(new ExceptionDetail(repository, ex));
                    }
                }
                
            });
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            exceptions.add(new ExceptionDetail(null, ex));
        }
        
        // Fire notifications for updated repos outside of the IRunnable
        for(IArchiRepository repository : updatedRepos) {
            RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(IRepositoryListener.HISTORY_CHANGED, repository);
        }

        // If there were any exceptions display them
        if(!exceptions.isEmpty()) {
            displayExceptions(exceptions);
        }
    }
    
    /**
     * Display any exceptions in a dialog
     */
    private void displayExceptions(List<ExceptionDetail> exceptions) {
        StringBuilder sb = new StringBuilder();
        
        for(ExceptionDetail ed : exceptions) {
            if(ed.repo() != null) {
                sb.append(ed.repo().getName());
                sb.append("\n------\n"); //$NON-NLS-1$
            }
            
            StringWriter sw = new StringWriter();
            try(PrintWriter pw = new PrintWriter(sw)) {
                ed.ex().printStackTrace(pw);
                sb.append(sw);
                sb.append("\n"); //$NON-NLS-1$
            }
        }
        
        displayErrorDialog(Messages.FetchUpdateWorkflow_2, "Exceptions occurred:", sb.toString()); //$NON-NLS-1$
    }
    
    @Override
    public boolean canRun() {
        return !RepositoryTreeModel.getInstance().getAllChildRepositoryRefs().isEmpty();
    }
    
    private void logFetchResult(FetchResult fetchResult) {
        // Remove zero byte from message
        String msgs = getSanitisedOperationResultResultMessages(fetchResult);
        if(StringUtils.isSet(msgs)) {
            logger.info("FetchResult Message: " + msgs); //$NON-NLS-1$
        }

        for(TrackingRefUpdate refUpdate : fetchResult.getTrackingRefUpdates()) {
            logger.info(refUpdate.toString());
        }
    }
}
