/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Property Section for User Details for repo
 * 
 * @author Phillip Beauvoir
 */
public class UserDetailsSection extends AbstractArchiPropertySection {
    
    private static Logger logger = Logger.getLogger(UserDetailsSection.class.getName());
    
    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof RepositoryRef ||
                    (object instanceof IArchimateModel model && RepoUtils.isModelInArchiRepository(model));
        }
    }
    
    private IArchiRepository fRepository;
    private UserText fTextName;
    private UserText fTextEmail;
 
    private class UserText {
        Text text;
        String field;
        String localValue, globalValue;

        Listener listener = e -> {
            String newValue = text.getText();

            // Different value so save and store
            if(!localValue.equals(newValue)) {
                localValue = newValue;
                saveToLocalConfig(field, globalValue, localValue);
            }
        };

        UserText(Composite parent, String field) {
            this.field = field;
            
            text = createSingleTextControl(parent, SWT.BORDER);
            
            text.addListener(SWT.DefaultSelection, listener);
            text.addListener(SWT.FocusOut, listener);
            
            text.addDisposeListener((event) -> {
                text.removeListener(SWT.DefaultSelection, listener);
                text.removeListener(SWT.FocusOut, listener);
            });
        }

        void setText(String globalValue, String localValue) {
            this.globalValue = globalValue;
            this.localValue = globalValue.equals(localValue) ? "" : localValue; //$NON-NLS-1$
            text.setText(this.localValue);
            
            // Hint
            text.setMessage(globalValue);
        }
    }
    
    public UserDetailsSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        Group group = getWidgetFactory().createGroup(parent, Messages.UserDetailsSection_0);
        group.setLayout(new GridLayout(2, false));
        GridDataFactory.create(GridData.FILL_BOTH).span(2, 1).applyTo(group);
        
        createLabel(group, Messages.UserDetailsSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        fTextName = new UserText(group, ConfigConstants.CONFIG_KEY_NAME);
        
        createLabel(group, Messages.UserDetailsSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        fTextEmail = new UserText(group, ConfigConstants.CONFIG_KEY_EMAIL);
        
        // Help ID
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);
    }
    
    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection == getSelection()) {
            return;
        }
        
        if(selection.getFirstElement() instanceof RepositoryRef ref) {
            fRepository = ref.getArchiRepository();
        }
        else if(selection.getFirstElement() instanceof IArchimateModel model) {
            fRepository = new ArchiRepository(RepoUtils.getWorkingFolderForModel(model));
        }
        else {
            fRepository = null;
        }

        if(fRepository != null) {
            String globalName = "", globalEmail = ""; //$NON-NLS-1$ //$NON-NLS-2$
            String localName = "", localEmail = ""; //$NON-NLS-1$ //$NON-NLS-2$
            
            // Get global name, email
            try {
                PersonIdent global = RepoUtils.getGitConfigUserDetails();
                globalName = global.getName();
                globalEmail = global.getEmailAddress();
            }
            catch(IOException | ConfigInvalidException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Get Config", ex); //$NON-NLS-1$
            }
            
            // Get local name, email
            try {
                PersonIdent local = fRepository.getUserDetails();
                localName = local.getName();
                localEmail = local.getEmailAddress();
            }
            catch(IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "User Details", ex); //$NON-NLS-1$
            }
            
            fTextName.setText(globalName, localName);
            fTextEmail.setText(globalEmail, localEmail);
        }
    }
    
    private void saveToLocalConfig(String name, String globalValue, String localValue) {
        try(Git git = Git.open(fRepository.getWorkingFolder())) {
            StoredConfig config = git.getRepository().getConfig();
            
            logger.info("Saving user config"); //$NON-NLS-1$
            
            // Unset if blank or same as 
            if(!StringUtils.isSet(localValue) || globalValue.equals(localValue)) {
                config.unset(ConfigConstants.CONFIG_USER_SECTION, null, name);
            }
            // Set
            else {
                config.setString(ConfigConstants.CONFIG_USER_SECTION, null, name, localValue);
            }
            
            config.save();
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Save Config", ex); //$NON-NLS-1$
        }
    }
}
