/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Property Section for Repo information
 * 
 * @author Phillip Beauvoir
 */
public class RepoInfoSection extends AbstractArchiPropertySection implements IRepositoryListener {
    
    private static Logger logger = Logger.getLogger(RepoInfoSection.class.getName());

    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof RepositoryRef ||
                    (object instanceof IArchimateModel && RepoUtils.isModelInArchiRepository((IArchimateModel)object));
        }
    }
    
    private IArchiRepository repository;
    
    private Text textFile, textCurrentBranch;
    private UpdatingTextControl textURL;
    
    public RepoInfoSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        Group group = getWidgetFactory().createGroup(parent, Messages.RepoInfoSection_3);
        group.setLayout(new GridLayout(2, false));
        GridDataFactory.create(GridData.FILL_BOTH).span(2, 1).applyTo(group);
        
        createLabel(group, Messages.RepoInfoSection_0, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textFile = createSingleTextControl(group, SWT.READ_ONLY | SWT.BORDER);

        createLabel(group, Messages.RepoInfoSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textURL = new UpdatingTextControl(createSingleTextControl(group, SWT.BORDER)) {
            @Override
            protected void textChanged(String previousText, String newText) {
                if(repository == null) {
                    return;
                }
                
                // If changing repository URL or unsetting it ask for confirmation
                if(!previousText.isBlank()) {
                    // Dialog will cause a focus out event and trigger textChanged again
                    setNotifications(false);
                    
                    // Ask user
                    boolean result = MessageDialog.openConfirm(getPart().getSite().getShell(),
                                      Messages.RepoInfoSection_4,
                                      Messages.RepoInfoSection_5);
                    
                    setNotifications(true);
                    
                    if(!result) { // Cancel
                        textURL.setText(previousText);
                        return;
                    }
                }
                
                try {
                    // If changing url delete all (local) remote branch refs *before* setting the remote
                    if(!previousText.isBlank()) {
                        logger.info("Deleting remote branch refs"); //$NON-NLS-1$
                        repository.removeRemoteRefs(newText);
                        
                        // And delete HTTP credentials
                        if(RepoUtils.isHTTP(newText)) {
                            logger.info("Removing credentials"); //$NON-NLS-1$
                            CredentialsStorage.getInstance().storeCredentials(repository, new UsernamePassword(null, null));
                        }
                        
                        // Update History and Branches Views
                        RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(IRepositoryListener.HISTORY_CHANGED, repository);
                    }
                    
                    // Set remote
                    logger.info("Setting remote URL to: " + newText); //$NON-NLS-1$
                    repository.setRemote(newText);
                }
                catch(Exception ex) {
                    logger.log(Level.SEVERE, "Set Remote", ex); //$NON-NLS-1$
                }
            }
        };
        
        createLabel(group, Messages.RepoInfoSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textCurrentBranch = createSingleTextControl(group, SWT.READ_ONLY | SWT.BORDER);
        
        // Listen to branch changes
        RepositoryListenerManager.getInstance().addListener(this);
        
        // Help ID
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);
    }

    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection == getSelection()) {
            return;
        }
        
        if(selection.getFirstElement() instanceof RepositoryRef ref) {
            repository = ref.getArchiRepository();
        }
        else if(selection.getFirstElement() instanceof IArchimateModel model) {
            repository = RepoUtils.getWorkingFolderForModel(model)
                                  .map(ArchiRepository::new)
                                  .orElse(null);
        }
        else {
            repository = null;
        }
        
        updateControls();
    }
    
    private void updateControls() {
        if(repository != null) {
            textFile.setText(repository.getWorkingFolder().getAbsolutePath());

            try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
                textURL.setText(utils.getRemoteURL().orElse("")); //$NON-NLS-1$
                textCurrentBranch.setText(utils.getCurrentLocalBranchName().orElse("")); //$NON-NLS-1$
            }
            catch(IOException ex) {
                logger.log(Level.SEVERE, "Update info", ex); //$NON-NLS-1$
            }
        }
    }
    
    @Override
    public void repositoryChanged(String eventName, IArchiRepository repo) {
        // Branch changed
        if(Objects.equals(IRepositoryListener.BRANCHES_CHANGED, eventName) && Objects.equals(repository, repo)) {
            updateControls();
        }
    }

    @Override
    public void dispose() {
        RepositoryListenerManager.getInstance().removeListener(this);
        super.dispose();
    }
}
