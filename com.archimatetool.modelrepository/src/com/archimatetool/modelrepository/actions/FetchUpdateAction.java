/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.SSHCredentials;
import com.archimatetool.modelrepository.dialogs.ErrorMessageDialog;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;
import com.archimatetool.modelrepository.workflows.ProgressMonitorWrapper;

/**
 * Fetch on all repositories in the workspace
 */
public class FetchUpdateAction extends Action {
    
    private static Logger logger = Logger.getLogger(FetchUpdateAction.class.getName());
    
    private IWorkbenchWindow workbenchWindow;
    
    public FetchUpdateAction(IWorkbenchWindow workbenchWindow) {
        this.workbenchWindow = workbenchWindow;
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_REFRESH));
        setActionDefinitionId("com.archimatetool.modelrepository.command.fetchUpdate"); //$NON-NLS-1$
        setText(Messages.FetchUpdateAction_0);
        setToolTipText(getText());
        setEnabled();
    }
    
    @Override
    public void run() {
        List<RepositoryRef> refs = RepositoryTreeModel.getInstance().getAllChildRepositoryRefs();
        if(refs.isEmpty()) {
            return;
        }
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        Set<IArchiRepository> updatedRepos = new HashSet<>();
        
        try {
            IRunnable.run(dialog, monitor -> {
                ProgressMonitorWrapper wrapper = new ProgressMonitorWrapper(monitor, Messages.FetchUpdateAction_1);
                monitor.beginTask(Messages.FetchUpdateAction_1, IProgressMonitor.UNKNOWN);
                
                for(RepositoryRef repoRef : refs) {
                    if(monitor.isCanceled()) {
                        break;
                    }
                    
                    IArchiRepository repository = repoRef.getArchiRepository();
                    
                    try(GitUtils utils = GitUtils.open(repository.getGitFolder())) {
                        String remoteURL = utils.getRemoteURL();
                        if(remoteURL != null) {
                            ICredentials credentials = RepoUtils.isHTTP(remoteURL) ?
                                                       CredentialsStorage.getInstance().getCredentials(repository) : new SSHCredentials();

                            String message = NLS.bind(Messages.FetchUpdateAction_2, repository.getName(), remoteURL);
                            logger.info(message);
                            monitor.subTask(message);
                            
                            List<FetchResult> fetchResults = utils.fetchFromRemote(credentials.getCredentialsProvider(), wrapper, true);
                            
                            // If there were updates add the repo to be updated
                            for(FetchResult fetchResult : fetchResults) {
                                if(!fetchResult.getTrackingRefUpdates().isEmpty()) {
                                    updatedRepos.add(repository);
                                }
                            }
                        }
                    }
                }
                
            }, true);
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Fetch", ex); //$NON-NLS-1$
            ex.printStackTrace();
            ErrorMessageDialog.open(workbenchWindow.getShell(),
                    Messages.FetchUpdateAction_3,
                    Messages.FetchUpdateAction_4,
                    ex);
        }
        finally {
            // Fire notifications for updated repos outside of the IRunnable
            for(IArchiRepository repository : updatedRepos) {
                RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(IRepositoryListener.HISTORY_CHANGED, repository);
            }
        }
    }
    
    public void setEnabled() {
        setEnabled(!RepositoryTreeModel.getInstance().getAllChildRepositoryRefs().isEmpty());
    }
}
