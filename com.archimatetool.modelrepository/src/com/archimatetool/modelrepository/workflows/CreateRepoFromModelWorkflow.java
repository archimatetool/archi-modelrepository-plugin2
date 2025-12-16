/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.NewRepoDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

/**
 * Create an online repo from an existing model
 * 
 * @author Phillip Beauvoir
 */
public class CreateRepoFromModelWorkflow extends AbstractPushResultWorkflow {
    
    private static Logger logger = Logger.getLogger(CreateRepoFromModelWorkflow.class.getName());
    
    private IArchimateModel model;
    private File modelFile;
	
    public CreateRepoFromModelWorkflow(IWorkbenchWindow window, IArchimateModel model) {
        super(window, null);
        this.model = model;
        modelFile = model.getFile();
    }

    @Override
    public void run() {
        NewRepoDialog dialog = new NewRepoDialog(workbenchWindow.getShell(), Messages.CreateRepoFromModelWorkflow_0);
        int response = dialog.open();
        if(response == Window.CANCEL) {
            return;
        }
        
        String repoURL = dialog.getURL();
        ICredentials credentials = dialog.getCredentials();
        CredentialsProvider credentialsProvider = credentials.getCredentialsProvider();

        // If Publish was selected check if the online repo is empty
        if(response == NewRepoDialog.ADD_AND_PUBLISH_ID) {
            try {
                if(!isRemoteEmpty(repoURL, credentialsProvider)) {
                    logger.info("Remote is not empty: " + repoURL); //$NON-NLS-1$
                    displayErrorDialog(Messages.CreateRepoFromModelWorkflow_0, Messages.CreateRepoFromModelWorkflow_3);
                    return;
                }
            }
            catch(Exception ex) {
                logger.log(Level.SEVERE, "Checking if remote is empty", ex); //$NON-NLS-1$
                displayErrorDialog(Messages.CreateRepoFromModelWorkflow_0, ex);
                return;
            }
        }
        
        logger.info("Adding model to workspace..."); //$NON-NLS-1$
        
        File folder = RepoUtils.generateNewRepoFolder();
        boolean storeCredentials = dialog.doStoreCredentials();
        archiRepository = new ArchiRepository(folder);
        
        // Ensure folder exists
        folder.mkdirs();
        
        // Init
        try {
            logger.info("Initialising new repository at: " + folder.getPath()); //$NON-NLS-1$
            archiRepository.init();
        }
        catch(GitAPIException | IOException ex) {
            logger.log(Level.SEVERE, "Initialising new repository", ex); //$NON-NLS-1$
            deleteRepository();
            displayErrorDialog(Messages.CreateRepoFromModelWorkflow_0, ex);
            return;
        }
        
        try(GitUtils utils = GitUtils.open(folder)) {
            // Add the remote if it's set
            if(StringUtils.isSetAfterTrim(repoURL)) {
                logger.info("Adding remote: " + repoURL); //$NON-NLS-1$
                utils.setRemote(repoURL);
            }
            
            // Set new file location
            model.setFile(archiRepository.getModelFile());
            
            // Save the model
            logger.info("Saving the model to: " + model.getFile()); //$NON-NLS-1$
            IEditorModelManager.INSTANCE.saveModel(model);
            
            // Commit changes
            logger.info("Doing a first commit"); //$NON-NLS-1$
            utils.commitModelWithManifest(model, Messages.CreateRepoFromModelWorkflow_1);
            
            // If we want to publish now then push...
            if(response == NewRepoDialog.ADD_AND_PUBLISH_ID) {
                push(utils, repoURL, credentialsProvider);
            }
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(archiRepository);

            // Store repo credentials if HTTP and option is set
            if(credentials instanceof UsernamePassword npw && storeCredentials) {
                CredentialsStorage.getInstance().storeCredentials(archiRepository, npw);
            }
            
            logger.info("Finished creating repository from model"); //$NON-NLS-1$
        }
        catch(Exception ex) {
            // If this does not complete properly restore the original model file name and delete the repo folder
            logger.log(Level.SEVERE, "Creating repository from model", ex); //$NON-NLS-1$
            model.setFile(modelFile);
            deleteRepository();
            displayErrorDialog(Messages.CreateRepoFromModelWorkflow_0, ex);
        }
    }
    
    /**
     * Check if the online remote is empty
     */
    private boolean isRemoteEmpty(String repoURL, CredentialsProvider credentialsProvider) throws Exception {
        logger.info("Checking if remote is empty: " + repoURL); //$NON-NLS-1$
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        AtomicReference<Collection<Ref>> refsResult = new AtomicReference<>();
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.CreateRepoFromModelWorkflow_4, IProgressMonitor.UNKNOWN);
            
            Collection<Ref> refs = Git.lsRemoteRepository()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(repoURL)
                    .setHeads(true)       // Only list branch heads (refs/heads/*)
                    .call();
            
            refsResult.set(refs);
        }, true);
        
        return refsResult.get().isEmpty();
    }
        
    /**
     * Push to remote
     */
    private void push(GitUtils utils, String repoURL, CredentialsProvider credentialsProvider) throws Exception {
        logger.info("Pushing to remote: " + repoURL); //$NON-NLS-1$
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.CreateRepoFromModelWorkflow_2, IProgressMonitor.UNKNOWN);
            
            PushResult pushResult = utils.pushToRemote(credentialsProvider, new ProgressMonitorWrapper(monitor,
                                                                                          Messages.CreateRepoFromModelWorkflow_2));
            
            // Logging
            logPushResult(pushResult, logger);
            
            // Status
            checkPushResultStatus(pushResult);
            
        }, true);
    }
    
    @Override
    public boolean canRun() {
        return model != null;
    }
}
