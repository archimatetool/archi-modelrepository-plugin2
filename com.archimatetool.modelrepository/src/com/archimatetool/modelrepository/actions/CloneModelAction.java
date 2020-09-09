/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.authentication.ProxyAuthenticator;
import com.archimatetool.modelrepository.authentication.SimpleCredentialsStorage;
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
	
    public CloneModelAction(IWorkbenchWindow window) {
        super(window);
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_CLONE));
        setText(Messages.CloneModelAction_0);
        setToolTipText(getText());
    }

    @Override
    public void run() {
        CloneDialog dialog = new CloneDialog(fWindow.getShell(), Messages.CloneModelAction_0);
        if(dialog.open() != Window.OK) {
            return;
        }
        
        final String repoURL = dialog.getURL();
        final boolean storeCredentials = dialog.doStoreCredentials();
        final UsernamePassword npw = dialog.getUsernamePassword();
        final File folder = RepoUtils.generateNewRepoFolder();
        
        // Ensure folder exists
        folder.mkdirs();

        setRepository(new ArchiRepository(folder));
        
        try {
            // Proxy check
            ProxyAuthenticator.update(repoURL);

            // Clone
            Exception[] exception = new Exception[1];

            PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
                @Override
                public void run(IProgressMonitor pm) {
                    try {
                        pm.beginTask(Messages.CloneModelAction_1, IProgressMonitor.UNKNOWN);
                        getRepository().cloneModel(repoURL, npw, new ProgressMonitorWrapper(pm));
                    }
                    catch(Exception ex) {
                        exception[0] = ex;
                    }
                }
            });

            if(exception[0] != null) {
                throw exception[0];
            }
            
            // Model file
            File modelFile = getRepository().getModelFile();
            
            // If we have one...
            if(modelFile.exists()) {
                // Open the model
                IArchimateModel model = IEditorModelManager.INSTANCE.openModel(modelFile);

                // Set the name
                if(model != null) {
                    getRepository().setName(model.getName());
                }
            }
            // Else there were no files so create a new blank model
            else {
                // New model. This will open in the tree
                IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
                model.setFile(getRepository().getModelFile());
                
                // And Save it (this will trigger setting the name in the "archi" file)
                IEditorModelManager.INSTANCE.saveModel(model);
                
                // Commit changes
                getRepository().commitChanges(Messages.CloneModelAction_2, false);
            }
            
            // Add to the Tree Model
            RepositoryTreeModel.getInstance().addNewRepositoryRef(getRepository());

            // Store repo credentials if HTTP and option is set
            if(RepoUtils.isHTTP(repoURL) && storeCredentials) {
                SimpleCredentialsStorage scs = new SimpleCredentialsStorage(new File(getRepository().getLocalGitFolder(), REPO_CREDENTIALS_FILE));
                scs.store(npw);
            }
        }
        catch(Exception ex) { // Catch all exceptions
            displayErrorDialog(Messages.CloneModelAction_0, ex);
        }
    }
    
    @Override
    protected boolean shouldBeEnabled() {
        return true;
    }
}
