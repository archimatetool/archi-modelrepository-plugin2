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
        
        final File folder = dialog.getFolder();
        
        // Ensure folder exists
        folder.mkdirs();
        
        setRepository(new ArchiRepository(folder));
        
        try {
            // Init
            getRepository().init();
            
            // Set new file location
            fModel.setFile(getRepository().getModelFile());
            
            // Save the model (this will trigger copying the xml file and set the name in the "archi" file)
            IEditorModelManager.INSTANCE.saveModel(fModel);
            
            // Commit changes
            getRepository().commitChanges(Messages.CreateRepoFromModelAction_1, false);
            
            // If we want to publish now...
            if(response == NewRepoDialog.ADD_AND_PUBLISH_ID) {
                final String repoURL = dialog.getURL();
                final boolean storeCredentials = dialog.doStoreCredentials();
                final UsernamePassword npw = dialog.getUsernamePassword();
                
                // Add the remote
                getRepository().addRemote(repoURL);
                
                // Proxy check
                ProxyAuthenticator.update(repoURL);
                
                // Push
                Exception[] exception = new Exception[1];
                
                PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
                    @Override
                    public void run(IProgressMonitor pm) {
                        try {
                            pm.beginTask(Messages.CreateRepoFromModelAction_2, IProgressMonitor.UNKNOWN);
                            getRepository().pushToRemote(npw, new ProgressMonitorWrapper(pm));
                        }
                        catch(Exception ex) {
                            exception[0] = ex;
                        }
                    }
                });

                if(exception[0] != null) {
                    throw exception[0];
                }
                
                // Store repo credentials if HTTP and option is set
                if(RepoUtils.isHTTP(repoURL) && storeCredentials) {
                    SimpleCredentialsStorage scs = new SimpleCredentialsStorage(new File(getRepository().getLocalGitFolder(), REPO_CREDENTIALS_FILE));
                    scs.store(npw);
                }
            }
            
            // Add to the Tree Model last so that the correct status is shown
            RepositoryTreeModel.getInstance().addNewRepositoryRef(getRepository());
        }
        catch(Exception ex) {
            displayErrorDialog(Messages.CreateRepoFromModelAction_0, ex);
        }
    }
}
