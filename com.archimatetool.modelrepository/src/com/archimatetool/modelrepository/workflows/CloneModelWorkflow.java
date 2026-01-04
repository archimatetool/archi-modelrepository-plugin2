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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IRunnable;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.CloneDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

/**
 * CloneModel Workflow
 * 
 * @author Phillip Beauvoir
 */
public class CloneModelWorkflow extends AbstractRepositoryWorkflow {

    private static Logger logger = Logger.getLogger(CloneModelWorkflow.class.getName());

    public CloneModelWorkflow(IWorkbenchWindow workbenchWindow, IArchiRepository archiRepository) {
        super(workbenchWindow, archiRepository);
    }

    /**
     * Clone a model
     * 
     * 1. Get user credentials
     * 2. Check Proxy
     * 3. Clone from Remote
     * 4. If working directory files exist load the model from these and save it to the model.archimate file
     * 5. If working directory files do not exist create a new model.archimate file and save it
     * 6. Store user credentials if prefs agree
     */
    @Override
    public void run() {
        CloneDialog cloneDialog = new CloneDialog(workbenchWindow.getShell(), Messages.CloneModelWorkflow_0);
        if(cloneDialog.open() != Window.OK) {
            return;
        }
        
        String url = cloneDialog.getURL();
        ICredentials credentials = cloneDialog.getCredentials();
        boolean storeCredentials = cloneDialog.doStoreCredentials();
        File folder = RepoUtils.generateNewRepoFolder();
        archiRepository = new ArchiRepository(folder);
        
        logger.info("Cloning model at: " + url); //$NON-NLS-1$

        try {
            logger.info("Cloning into folder: " + folder.getPath()); //$NON-NLS-1$

            // Ensure folder exists
            folder.mkdirs();
            
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(workbenchWindow.getShell());
            
            IRunnable.run(dialog, true, true, monitor -> {
                monitor.beginTask(Messages.CloneModelWorkflow_1, IProgressMonitor.UNKNOWN);
                archiRepository.cloneModel(url, credentials.getCredentialsProvider(), new ProgressMonitorWrapper(monitor, Messages.CloneModelWorkflow_1));
            });

            // Get the main model file
            File modelFile = archiRepository.getModelFile();
            
            // We have one so open it...
            if(modelFile.exists()) {
                logger.info("Model cloned, opening model: " + modelFile); //$NON-NLS-1$
                
                // Open the model
                IEditorModelManager.INSTANCE.openModel(modelFile);
            }
            // Else there is no model file...
            else {
                // If there are other files present it's not a blank repo
                File[] files = folder.listFiles((dir, name) -> {
                    name = name.toLowerCase();
                    return !(name.equals(".git") || name.startsWith("readme")); //$NON-NLS-1$ //$NON-NLS-2$
                });
                
                if(files != null && files.length != 0) {
                    logger.info("Model doesn't exist, but repo is not empty!"); //$NON-NLS-1$
                    
                    if(!MessageDialog.openQuestion(workbenchWindow.getShell(),
                            Messages.CloneModelWorkflow_0,
                            Messages.CloneModelWorkflow_3)) {
                        
                        // Delete it
                        deleteRepository();
                        return;
                    }
                }
                
                logger.info("Model does not exist, creating a new model"); //$NON-NLS-1$
                
                // New model. This will open in the tree
                IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
                model.setFile(archiRepository.getModelFile());
                
                // And save it
                logger.info("Saving the new model"); //$NON-NLS-1$
                IEditorModelManager.INSTANCE.saveModel(model);
                
                // Commit changes
                try(GitUtils utils = GitUtils.open(archiRepository.getWorkingFolder())) {
                    logger.info("Initial commit on new model"); //$NON-NLS-1$
                    utils.commitModelWithManifest(model, Messages.CloneModelWorkflow_2);
                }
            }
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(archiRepository);

            // Store repo credentials if HTTP and option is set
            if(credentials instanceof UsernamePassword npw && storeCredentials) {
                CredentialsStorage.getInstance().storeCredentials(archiRepository, npw);
            }
            
            logger.info("Finished cloning model"); //$NON-NLS-1$
        }
        catch(Exception ex) { // Catch all exceptions
            // If this does not complete properly close the model and delete the repo folder
            logger.log(Level.SEVERE, "Clone model", ex); //$NON-NLS-1$
            closeModel(false);
            deleteRepository();
            displayErrorDialog(Messages.CloneModelWorkflow_0, ex);
        }
    }
    
    @Override
    public boolean canRun() {
        return true;
    }
}
