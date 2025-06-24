/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
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
	
    public CreateRepoFromModelWorkflow(IWorkbenchWindow window, IArchimateModel model) {
        super(window, null);
        this.model = model;
    }

    @Override
    public void run() {
        NewRepoDialog dialog = new NewRepoDialog(workbenchWindow.getShell(), Messages.CreateRepoFromModelWorkflow_0);
        int response = dialog.open();
        if(response == Window.CANCEL) {
            return;
        }
        
        logger.info("Adding model to workspace..."); //$NON-NLS-1$
        
        File folder = RepoUtils.generateNewRepoFolder();
        String repoURL = dialog.getURL();
        ICredentials credentials = dialog.getCredentials();
        boolean storeCredentials = dialog.doStoreCredentials();
        archiRepository = new ArchiRepository(folder);
        
        try {
            // Ensure folder exists
            folder.mkdirs();
            
            // Init
            logger.info("Initialising new repository at: " + folder.getPath()); //$NON-NLS-1$
            archiRepository.init();
            
            // Add the remote if it's set
            if(StringUtils.isSetAfterTrim(repoURL)) {
                logger.info("Adding remote: " + repoURL); //$NON-NLS-1$
                archiRepository.setRemote(repoURL);
            }
            
            // Set new file location
            model.setFile(archiRepository.getModelFile());
            
            // Save the model
            logger.info("Saving the model to: " + model.getFile()); //$NON-NLS-1$
            IEditorModelManager.INSTANCE.saveModel(model);
            
            // Commit changes
            logger.info("Doing a first commit"); //$NON-NLS-1$
            archiRepository.commitModelWithManifest(model, Messages.CreateRepoFromModelWorkflow_1);
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(archiRepository);

            // If we want to publish now then push...
            if(response == NewRepoDialog.ADD_AND_PUBLISH_ID) {
                push(repoURL, credentials.getCredentialsProvider());
            }
            
            // Store repo credentials if HTTP and option is set
            if(credentials instanceof UsernamePassword npw && storeCredentials) {
                CredentialsStorage.getInstance().storeCredentials(archiRepository, npw);
            }
            
            logger.info("Finished creating repository from model"); //$NON-NLS-1$
        }
        catch(Exception ex) {
            logger.log(Level.SEVERE, "Creating repository from model", ex); //$NON-NLS-1$
            
            // In case of an exception remove the remote
            try {
                archiRepository.setRemote(null);
            }
            catch(Exception ex1) {
                ex.printStackTrace();
            }
            
            displayErrorDialog(Messages.CreateRepoFromModelWorkflow_0, ex);
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
    private void push(final String repoURL, CredentialsProvider credentialsProvider) throws Exception {
        logger.info("Pushing to remote: " + repoURL); //$NON-NLS-1$
        
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
        
        IRunnable.run(dialog, monitor -> {
            monitor.beginTask(Messages.CreateRepoFromModelWorkflow_2, IProgressMonitor.UNKNOWN);
            
            PushResult pushResult = archiRepository.pushToRemote(credentialsProvider, new ProgressMonitorWrapper(monitor,
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
