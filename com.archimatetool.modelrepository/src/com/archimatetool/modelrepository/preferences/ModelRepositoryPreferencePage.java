/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.preferences;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.RepoUtils;


/**
 * Model Repository Preferences Page
 * 
 * @author Phillip Beauvoir
 */
public class ModelRepositoryPreferencePage
extends PreferencePage
implements IWorkbenchPreferencePage, IPreferenceConstants {
    
    private static Logger logger = Logger.getLogger(ModelRepositoryPreferencePage.class.getName());
    
    public static final String ID = "com.archimatetool.com.modelrepository.preferences.ModelRepositoryPreferencePage";  //$NON-NLS-1$
    
    private static final String HELP_ID = "com.archimatetool.com.modelrepository.prefs"; //$NON-NLS-1$
    
    private Text fUserNameTextField;
    private Text fUserEmailTextField;
    
    private Text fUserRepoFolderTextField;
    
	public ModelRepositoryPreferencePage() {
		setPreferenceStore(ModelRepositoryPlugin.INSTANCE.getPreferenceStore());
	}
	
    @Override
    protected Control createContents(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, HELP_ID);

        Composite client = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.marginWidth = layout.marginHeight = 0;
        client.setLayout(layout);
        
        // User Details Group
        Group userDetailsGroup = new Group(client, SWT.NULL);
        userDetailsGroup.setText(Messages.ModelRepositoryPreferencePage_0);
        userDetailsGroup.setLayout(new GridLayout(2, false));
        userDetailsGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        Label label = new Label(userDetailsGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_1);
        
        fUserNameTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        fUserNameTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        label = new Label(userDetailsGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_2);
        
        fUserEmailTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        fUserEmailTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        
        // Workspace Group
        Group workspaceGroup = new Group(client, SWT.NULL);
        workspaceGroup.setText(Messages.ModelRepositoryPreferencePage_3);
        workspaceGroup.setLayout(new GridLayout(3, false));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.widthHint = 500;
        workspaceGroup.setLayoutData(gd);
        
        // Workspace folder location
        label = new Label(workspaceGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_4);
        
        fUserRepoFolderTextField = UIUtils.createSingleTextControl(workspaceGroup, SWT.BORDER, false);
        fUserRepoFolderTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        Button folderButton = new Button(workspaceGroup, SWT.PUSH);
        folderButton.setText(Messages.ModelRepositoryPreferencePage_5);
        folderButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String folderPath = chooseFolderPath();
                if(folderPath != null) {
                    fUserRepoFolderTextField.setText(folderPath);
                }
            }
        });
        
        setValues();
        
        return client;
    }

    private String chooseFolderPath() {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_6);
        dialog.setMessage(Messages.ModelRepositoryPreferencePage_10);
        File file = new File(fUserRepoFolderTextField.getText());
        if(file.exists()) {
            dialog.setFilterPath(fUserRepoFolderTextField.getText());
        }
        return dialog.open();
    }

    private void setValues() {
        // Gobal user details
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());
        
        // Workspace folder
        fUserRepoFolderTextField.setText(getPreferenceStore().getString(PREFS_REPOSITORY_FOLDER));
    }
    
    @Override
    public boolean performOk() {
        String name = fUserNameTextField.getText();
        String email = fUserEmailTextField.getText();
        
        try {
            RepoUtils.saveGitConfigUserDetails(name, email);
        }
        catch(IOException | ConfigInvalidException ex) {
            logger.log(Level.WARNING, "Could not save user details.", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }
        
        getPreferenceStore().setValue(PREFS_REPOSITORY_FOLDER, fUserRepoFolderTextField.getText());
        
        return true;
    }
    
    @Override
    protected void performDefaults() {
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());

        fUserRepoFolderTextField.setText(getPreferenceStore().getDefaultString(PREFS_REPOSITORY_FOLDER));
    }
    
    
    private PersonIdent getUserDetails() {
        try {
            return RepoUtils.getGitConfigUserDetails();
        }
        catch(IOException | ConfigInvalidException ex) {
            ex.printStackTrace();
            logger.log(Level.WARNING, "Could not get user details.", ex); //$NON-NLS-1$
        }
        
        // Default
        return new PersonIdent(getPreferenceStore().getDefaultString(PREFS_COMMIT_USER_NAME), getPreferenceStore().getDefaultString(PREFS_COMMIT_USER_EMAIL));
    }
    
    @Override
    public void init(IWorkbench workbench) {
    }
}