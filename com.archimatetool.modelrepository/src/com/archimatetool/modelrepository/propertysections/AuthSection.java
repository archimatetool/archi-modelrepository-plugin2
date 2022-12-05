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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.model.IArchimateModel;
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
                    (object instanceof IArchimateModel && RepoUtils.isModelInArchiRepository((IArchimateModel)object));
        }
    }
    
    private IArchiRepository fRepository;
    
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
        
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan = 2;
        //group.setLayoutData(gd);
        
        // User name
        createLabel(group, Messages.AuthSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textUserName = new UpdatingTextControl(createSingleTextControl(group, SWT.BORDER)) {
            @Override
            protected void textChanged(String newText) {
                storeUserName(newText);
            }
        };
        
        // Password
        createLabel(group, Messages.AuthSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textPassword = new UpdatingTextControl(createSingleTextControl(group, SWT.PASSWORD | SWT.BORDER)) {
            @Override
            protected void textChanged(String newText) {
                setNotifications(false); // Setting the password might invoke the primary password dialog and cause a focus out event
                storePassword(newText.toCharArray());
                setNotifications(true);
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
    }
    
    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection == getSelection()) {
            return;
        }
        
        if(selection.getFirstElement() instanceof RepositoryRef) {
            fRepository = ((RepositoryRef)selection.getFirstElement()).getArchiRepository();
        }
        else if(selection.getFirstElement() instanceof IArchimateModel) {
            fRepository = new ArchiRepository(RepoUtils.getWorkingFolderForModel((IArchimateModel)selection.getFirstElement()));
        }
        else {
            fRepository = null;
        }

        if(fRepository != null) {
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
            isHTTP = RepoUtils.isHTTP(fRepository.getRemoteURL());
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
                UsernamePassword npw = CredentialsStorage.getInstance().getCredentials(fRepository);
                if(npw != null) {
                    textUserName.setText(npw.getUsername());

                    if(npw.getPassword() != null && npw.getPassword().length > 0) {
                        textPassword.setText("********"); //$NON-NLS-1$
                    }
                }
            }
            catch(StorageException ex) {
                showError(ex);
            }
        }
    }
    
    private void storeUserName(String userName) {
        try {
            CredentialsStorage.getInstance().storeUserName(fRepository, userName);
        }
        catch(StorageException | IOException ex) {
            showError(ex);
        }
    }
    
    private void storePassword(char[] password) {
        try {
            CredentialsStorage.getInstance().storePassword(fRepository, password);
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
        MessageDialog.openError(Display.getCurrent().getActiveShell(),
                Messages.AuthSection_4,
                Messages.AuthSection_5 +
                        " " + //$NON-NLS-1$
                        ex.getMessage());
    }

}
