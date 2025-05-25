/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
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
public class RepoInfoSection extends AbstractArchiPropertySection {
    
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
                
                // If changing repository URL or unsetting it ask for conformation
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
                        // Update History and Branches Views
                        RepositoryListenerManager.getInstance().fireRepositoryChangedEvent(IRepositoryListener.HISTORY_CHANGED, repository);
                    }
                    
                    // Set remote
                    logger.info("Setting remote URL to: " + newText); //$NON-NLS-1$
                    repository.setRemote(newText);
                }
                catch(IOException | GitAPIException | URISyntaxException ex) {
                    logger.log(Level.SEVERE, "Set Remote", ex); //$NON-NLS-1$
                }
            }
        };
        
        createLabel(group, Messages.RepoInfoSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textCurrentBranch = createSingleTextControl(group, SWT.READ_ONLY | SWT.BORDER);
        
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
            repository = new ArchiRepository(RepoUtils.getWorkingFolderForModel(model));
        }
        else {
            repository = null;
        }
        
        if(repository != null) {
            textFile.setText(repository.getWorkingFolder().getAbsolutePath());

            try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
                textURL.setText(utils.getRemoteURL());
                textCurrentBranch.setText(StringUtils.safeString(utils.getCurrentLocalBranchName()));
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Update info", ex); //$NON-NLS-1$
            }
        }
    }
}
