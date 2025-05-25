/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.preferences.ModelRepositoryPreferencePage;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Property Section for Authentication
 * 
 * @author Phillip Beauvoir
 */
public class AuthSection extends AbstractArchiPropertySection {
    
    private static Logger logger = Logger.getLogger(AuthSection.class.getName());
    
    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof RepositoryRef ||
                    (object instanceof IArchimateModel model && RepoUtils.isModelInArchiRepository(model));
        }
    }
    
    private IArchiRepository repository;
    
    private Button prefsButton;
    
    private UpdatingTextControl textUserName;
    private UpdatingTextControl textPassword;
    
    public AuthSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        Group group = getWidgetFactory().createGroup(parent, Messages.AuthSection_0);
        group.setLayout(new GridLayout(2, false));
        GridDataFactory.create(GridData.FILL_BOTH).span(2, 1).applyTo(group);
        
        // User name
        createLabel(group, Messages.AuthSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textUserName = new UpdatingTextControl(createSingleTextControl(group, SWT.BORDER)) {
            @Override
            protected void textChanged(String previousText, String newText) {
                storeUserName(newText);
            }
        };
        
        // Password
        createLabel(group, Messages.AuthSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textPassword = new UpdatingTextControl(createSingleTextControl(group, SWT.PASSWORD | SWT.BORDER)) {
            @Override
            protected void textChanged(String previousText, String newText) {
                storePassword(newText.toCharArray());
            }
        };

        // SSH Preferences
        prefsButton = getWidgetFactory().createButton(group, Messages.AuthSection_3, SWT.PUSH);
        prefsButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getPart().getSite().getShell(),
                        ModelRepositoryPreferencePage.ID, null, null);
                if(dialog != null) {
                    dialog.open();
                }
            }
        });
        
        // Help ID
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);
    }
    
    @Override
    protected void handleSelection(IStructuredSelection selection) {
        // Update controls in all cases in case the online repo URL has been changed from SSH to HTTPS or vice-verca
        //if(selection == getSelection()) {
        //    return;
        //}
        
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
            updateControls();
        }
        else {
            System.err.println(getClass() + " failed to get element for " + selection.getFirstElement()); //$NON-NLS-1$
        }
    }
    
    private void updateControls() {
        textUserName.setText(""); //$NON-NLS-1$
        textPassword.setText(""); //$NON-NLS-1$

        // Is this HTTP or SSH?
        boolean isHTTP = true;
        
        try {
            isHTTP = RepoUtils.isHTTP(repository.getRemoteURL());
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Get Auth", ex); //$NON-NLS-1$
            enableControls(false);
            return;
        }

        textUserName.setEnabled(isHTTP);
        textPassword.setEnabled(isHTTP);
        prefsButton.setEnabled(!isHTTP);

        // HTTP so show credentials
        if(isHTTP) {
            try {
                UsernamePassword npw = CredentialsStorage.getInstance().getCredentials(repository);
                textUserName.setText(npw.getUsername());
                if(npw.isPasswordSet()) {
                    textPassword.setText("********"); //$NON-NLS-1$
                }
            }
            catch(StorageException ex) {
                showError(ex);
            }
        }
    }
    
    private void storeUserName(String userName) {
        try {
            CredentialsStorage.getInstance().storeUserName(repository, userName);
        }
        catch(StorageException | IOException ex) {
            showError(ex);
        }
    }
    
    private void storePassword(char[] password) {
        try {
            CredentialsStorage.getInstance().storePassword(repository, password);
        }
        catch(StorageException | IOException ex) {
            showError(ex);
        }
    }
    
    private void enableControls(boolean enable) {
        textUserName.setEnabled(enable);
        textPassword.setEnabled(enable);
        prefsButton.setEnabled(enable);
    }
    
    private void showError(Exception ex) {
        ex.printStackTrace();
        MessageDialog.openError(getPart().getSite().getShell(),
                Messages.AuthSection_4,
                Messages.AuthSection_5 +
                        " " + //$NON-NLS-1$
                        ex.getMessage());
    }

}
