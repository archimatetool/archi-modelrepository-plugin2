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
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
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
            return object instanceof RepositoryRef;
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
        textUserName = new UpdatingTextControl(createSingleTextControl(group, SWT.NONE)) {
            @Override
            protected void textChanged(String newText) {
                storeUserName(newText);
            }
        };
        
        // Password
        createLabel(group, Messages.AuthSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textPassword = new UpdatingTextControl(createSingleTextControl(group, SWT.PASSWORD)) {
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
                // TODO: Open Preferences page
//                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getPart().getSite().getShell(),
//                        ModelRepositoryPreferencePage.ID, null, null);
//                if(dialog != null) {
//                    dialog.open();
//                }
            }
        });
        
        // TODO: Remove this
        enableControls(false);
    }
    
    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection.getFirstElement() instanceof RepositoryRef) {
            fRepository = ((RepositoryRef)selection.getFirstElement()).getArchiRepository();
            // TODO: enable this
            // updateControls();
        }
        else {
            System.err.println(getClass() + " failed to get element for " + selection.getFirstElement()); //$NON-NLS-1$
        }
    }
    
    @SuppressWarnings("unused") // TODO: Remove this
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

        // TODO:
        // HTTP so show credentials
        if(isHTTP) {

        }
    }
    
    private void storeUserName(String userName) {
        // TODO:
    }
    
    private void storePassword(char[] password) {
        // TODO:
    }
    
    private void enableControls(boolean enable) {
        textUserName.setEnabled(enable);
        textPassword.setEnabled(enable);
        prefsButton.setEnabled(enable);
    }
}
