/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.io.File;

import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.dialogs.NewRepoDialog;
import com.archimatetool.modelrepository.repository.ArchiRepository;
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
        setText("Add Model to Workspace");
        setToolTipText(getText());
    }

    @Override
    public void run() {
        NewRepoDialog dialog = new NewRepoDialog(fWindow.getShell(), "New Repository");
        if(dialog.open() != Window.OK) {
            return;
        }
        
        //final String repoURL = dialog.getURL();
        //final boolean storeCredentials = dialog.doStoreCredentials();
        //final UsernamePassword npw = dialog.getUsernamePassword();
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
            getRepository().commitChanges("First Commit", false);
            
            // TODO Store repo credentials if HTTP and option is set
//            if(RepoUtils.isHTTP(repoURL) && storeCredentials) {
//                SimpleCredentialsStorage scs = new SimpleCredentialsStorage(new File(getRepository().getLocalGitFolder(), IGraficoConstants.REPO_CREDENTIALS_FILE));
//                scs.store(npw);
//            }
//            
            // TODO Push to remote if URL is set
            // ProxyAuthenticator.update(repoURL);

            // Add to the Tree Model last so that the correct status is shown
            RepositoryTreeModel.getInstance().addNewRepositoryRef(getRepository());
        }
        catch(Exception ex) {
            displayErrorDialog("New Model Repository", ex);
        }
    }
}
