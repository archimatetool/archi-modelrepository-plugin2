/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.ProxyAuthenticator;
import com.archimatetool.modelrepository.authentication.SimpleCredentialsStorage;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.NewRepoDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

/**
 * Create an online repo from an existing model
 * 
 * @author Phillip Beauvoir
 */
public class CreateRepoFromModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(CreateRepoFromModelAction.class.getName());
    
    private IArchimateModel fModel;
	
    public CreateRepoFromModelAction(IWorkbenchWindow window, IArchimateModel model) {
        super(window);
        
        fModel = model;
        
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_CREATE_REPOSITORY));
        setText(Messages.CreateRepoFromModelAction_0);
        setToolTipText(getText());
    }

    @Override
    public void run() {
        NewRepoDialog dialog = new NewRepoDialog(fWindow.getShell(), Messages.CreateRepoFromModelAction_0);
        int response = dialog.open();
        if(response == Window.CANCEL) {
            return;
        }
        
        final File folder = RepoUtils.generateNewRepoFolder();
        final String repoURL = dialog.getURL();
        final boolean storeCredentials = dialog.doStoreCredentials();
        final UsernamePassword npw = dialog.getUsernamePassword();
        
        setRepository(new ArchiRepository(folder));
        
        try {
            // Ensure folder exists
            folder.mkdirs();
            
            // Init
            logger.info("Initialising New Repo at: " + folder.getPath()); //$NON-NLS-1$
            getRepository().init();
            
            // Add the remote if it's set
            if(StringUtils.isSet(repoURL)) {
                logger.info("Adding Remote: " + repoURL); //$NON-NLS-1$
                getRepository().addRemote(repoURL);
            }
            
            // Set new file location
            fModel.setFile(getRepository().getModelFile());
            
            // Save the model (this will trigger setting the name in the "archi" file)
            logger.info("Saving the model to: " + fModel.getFile()); //$NON-NLS-1$
            IEditorModelManager.INSTANCE.saveModel(fModel);
            
            // Commit changes
            logger.info("Doing a first commit"); //$NON-NLS-1$
            getRepository().commitChanges(Messages.CreateRepoFromModelAction_1, false);
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(getRepository());

            // If we want to publish now then push...
            if(response == NewRepoDialog.ADD_AND_PUBLISH_ID) {
                push(repoURL, npw);
            }
            
            // Store repo credentials if HTTP and option is set
            if(RepoUtils.isHTTP(repoURL) && storeCredentials) {
                SimpleCredentialsStorage scs = new SimpleCredentialsStorage(new File(getRepository().getLocalGitFolder(), REPO_CREDENTIALS_FILE));
                scs.store(npw);
            }
            
            logger.info("Finished Creating Repo from Model"); //$NON-NLS-1$
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Create Repo From Model Exception", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.CreateRepoFromModelAction_0, ex);
        }
        finally {
            // If the folder is empty because of an error, delete it
            if(folder.exists() && folder.list().length == 0) {
                folder.delete();
            }
        }
    }
    
    /**
     * Push to remote
     */
    private void push(final String repoURL, final UsernamePassword npw) throws Exception {
        logger.info("Pushing to remote: " + repoURL); //$NON-NLS-1$
        
        // Proxy check
        ProxyAuthenticator.update(repoURL);
        
        // Store exception
        Exception[] exception = new Exception[1];
        
        // Push
        
        // If using this be careful that no UI operations are included as this could lead to an SWT Invalid thread access exception
        PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor pm) {
                try {
                    pm.beginTask(Messages.CreateRepoFromModelAction_2, IProgressMonitor.UNKNOWN);
                    Iterable<PushResult> pushResult = getRepository().pushToRemote(npw, new ProgressMonitorWrapper(pm));
                    
                    // Get any errors in Push Results
                    String errorMessage = getPushResultErrorMessage(pushResult);
                    if(errorMessage.length() > 0) {
                        throw new GitAPIException(errorMessage) {};
                    }
                }
                catch(Exception ex) {
                    exception[0] = ex;
                }
            }
        });

        if(exception[0] != null) {
            // In case of an exception remove the remote
            getRepository().removeRemote();
            throw exception[0];
        }
    }
    
    /**
     * Get any errors in Push Results
     */
    private String getPushResultErrorMessage(Iterable<PushResult> pushResult) {
        StringBuilder sb = new StringBuilder();
        
        pushResult.forEach(result -> {
            result.getRemoteUpdates().stream()
                    .filter(update -> update.getStatus() != RemoteRefUpdate.Status.OK)
                    .filter(update -> update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE)
                    .forEach(update -> {
                        if(StringUtils.isSet(result.getMessages())) {
                            sb.append(result.getMessages() + "\n"); //$NON-NLS-1$
                        }
                        else {
                            sb.append(update.getStatus().toString() + "\n"); //$NON-NLS-1$
                        }
                    });
            
        });
        
        return sb.toString();
    }
}
