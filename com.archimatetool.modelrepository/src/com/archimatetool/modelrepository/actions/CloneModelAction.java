/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.components.IRunnable;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.dialogs.CloneDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModel;

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
public class CloneModelAction extends AbstractModelAction {
    
    private static Logger logger = Logger.getLogger(CloneModelAction.class.getName());
	
    public CloneModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_CLONE));
        setText(Messages.CloneModelAction_0);
        setToolTipText(getText());
    }

    @Override
    public void run() {
        CloneDialog cloneDialog = new CloneDialog(fWindow.getShell(), Messages.CloneModelAction_0);
        if(cloneDialog.open() != Window.OK) {
            return;
        }
        
        final String url = cloneDialog.getURL();
        final boolean storeCredentials = cloneDialog.doStoreCredentials();
        final UsernamePassword npw = cloneDialog.getUsernamePassword();
        final File folder = RepoUtils.generateNewRepoFolder();
        
        logger.info("Cloning model at: " + url); //$NON-NLS-1$

        setRepository(new ArchiRepository(folder));
        
        try {
            logger.info("Cloning into folder: " + folder.getPath()); //$NON-NLS-1$

            // Ensure folder exists
            folder.mkdirs();
            
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(fWindow.getShell());
            
            IRunnable.run(dialog, monitor -> {
                monitor.beginTask(Messages.CloneModelAction_1, IProgressMonitor.UNKNOWN);
                getRepository().cloneModel(url, npw, new ProgressMonitorWrapper(monitor));
            }, true);

            // Model file
            File modelFile = getRepository().getModelFile();
            
            // If we have one...
            if(modelFile.exists()) {
                logger.info("Model cloned, opening model: " + modelFile); //$NON-NLS-1$
                
                // Open the model
                IEditorModelManager.INSTANCE.openModel(modelFile);
            }
            // Else there were no files so create a new blank model
            else {
                logger.info("Model does not exist, creating a new model"); //$NON-NLS-1$
                
                // New model. This will open in the tree
                IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
                model.setFile(getRepository().getModelFile());
                
                // And save it
                logger.info("Saving the new model"); //$NON-NLS-1$
                IEditorModelManager.INSTANCE.saveModel(model);
                
                // Commit changes
                logger.info("Initial commit on new model"); //$NON-NLS-1$
                getRepository().commitChanges(Messages.CloneModelAction_2, false);
            }
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(getRepository());

            // Store repo credentials if HTTP and option is set
            if(RepoUtils.isHTTP(url) && storeCredentials) {
                CredentialsStorage.getInstance().storeCredentials(getRepository(), npw);
            }
            
            logger.info("Finished cloning model"); //$NON-NLS-1$
        }
        catch(Exception ex) { // Catch all exceptions
            logger.log(Level.SEVERE, "Clone model", ex); //$NON-NLS-1$
            displayErrorDialog(Messages.CloneModelAction_0, ex);
        }
        finally {
            // If this operation is not completed properly delete the repo folder
            if(!RepoUtils.isArchiGitRepository(folder)) {
                try {
                    logger.info("Cleaning up failed clone. Deleting folder " + folder.getPath()); //$NON-NLS-1$
                    FileUtils.deleteFolder(folder);
                }
                catch(IOException ex) {
                    logger.log(Level.SEVERE, "Could not delete folder", ex); //$NON-NLS-1$
                    ex.printStackTrace();
                }
            }
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return true;
    }
}
