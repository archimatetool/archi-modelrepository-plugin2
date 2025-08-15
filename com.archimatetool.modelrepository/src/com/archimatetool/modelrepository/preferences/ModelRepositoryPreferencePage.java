/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.preferences;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.authentication.CredentialsStorage;
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
    
    private Text userNameTextField;
    private Text userEmailTextField;
    
    private Text userRepoFolderTextField;
    
    private Button sshIdentitySelectButton;
    private Text sshIdentityFileTextField;
    private Text sshIdentityPasswordTextField;
    private Button sshScanDirButton;
    
    private boolean sshPasswordChanged;
    
	public ModelRepositoryPreferencePage() {
		setPreferenceStore(ModelRepositoryPlugin.getInstance().getPreferenceStore());
	}
	
    @Override
    protected Control createContents(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);

        Composite client = new Composite(parent, SWT.NULL);
        GridLayoutFactory.fillDefaults().applyTo(client);
        
        // User Details Group
        Group userDetailsGroup = new Group(client, SWT.NULL);
        userDetailsGroup.setText(Messages.ModelRepositoryPreferencePage_0);
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(userDetailsGroup);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(userDetailsGroup);
        
        new Label(userDetailsGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_1);
        
        userNameTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        userNameTextField.setMessage(Messages.ModelRepositoryPreferencePage_17);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(userNameTextField);

        new Label(userDetailsGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_2);
        
        userEmailTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        userEmailTextField.setMessage(Messages.ModelRepositoryPreferencePage_18);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(userEmailTextField);
        
        // Workspace Group
        Group workspaceGroup = new Group(client, SWT.NULL);
        workspaceGroup.setText(Messages.ModelRepositoryPreferencePage_3);
        GridLayoutFactory.swtDefaults().numColumns(3).applyTo(workspaceGroup);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).hint(500, SWT.DEFAULT).applyTo(workspaceGroup);
        
        // Workspace folder location
        new Label(workspaceGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_4);
        
        userRepoFolderTextField = UIUtils.createSingleTextControl(workspaceGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(userRepoFolderTextField);
        
        Button folderButton = new Button(workspaceGroup, SWT.PUSH);
        folderButton.setText(Messages.ModelRepositoryPreferencePage_5);
        folderButton.addSelectionListener(widgetSelectedAdapter(event -> {
            String folderPath = chooseFolderPath();
            if(folderPath != null) {
                userRepoFolderTextField.setText(folderPath);
            }
        }));
        
        // Authentication Group
        Group authGroup = new Group(client, SWT.NULL);
        authGroup.setText(Messages.ModelRepositoryPreferencePage_7);
        GridLayoutFactory.swtDefaults().applyTo(authGroup);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(authGroup);
        
        // SSH Credentials Group
        Group sshGroup = new Group(authGroup, SWT.NULL);
        sshGroup.setText(Messages.ModelRepositoryPreferencePage_8);
        GridLayoutFactory.swtDefaults().numColumns(3).applyTo(sshGroup);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(sshGroup);

        sshScanDirButton = new Button(sshGroup, SWT.CHECK);
        sshScanDirButton.setText(Messages.ModelRepositoryPreferencePage_9);
        GridDataFactory.fillDefaults().span(3, 0).applyTo(sshScanDirButton);
        sshScanDirButton.addSelectionListener(widgetSelectedAdapter(event -> {
            sshIdentityFileTextField.setEnabled(!sshScanDirButton.getSelection());
            sshIdentitySelectButton.setEnabled(!sshScanDirButton.getSelection());
        }));

        new Label(sshGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_11);
        
        sshIdentityFileTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(sshIdentityFileTextField);
        
        sshIdentitySelectButton = new Button(sshGroup, SWT.PUSH);
        sshIdentitySelectButton.setText(Messages.ModelRepositoryPreferencePage_12);
        sshIdentitySelectButton.addSelectionListener(widgetSelectedAdapter(event -> {
            String identityFile = chooseSSHIdentityFile();
            if(identityFile != null) {
                sshIdentityFileTextField.setText(identityFile);
            }
        }));
        
        new Label(sshGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_14);
        sshIdentityPasswordTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER | SWT.PASSWORD, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(sshIdentityPasswordTextField);
        
        setValues();
        
        return client;
    }

    private String chooseFolderPath() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_6);
        dialog.setMessage(Messages.ModelRepositoryPreferencePage_10);
        File file = new File(userRepoFolderTextField.getText());
        if(file.exists()) {
            dialog.setFilterPath(userRepoFolderTextField.getText());
        }
        return dialog.open();
    }

    private String chooseSSHIdentityFile() {
        FileDialog dialog = new FileDialog(getShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_15);
        File file = new File(sshIdentityFileTextField.getText());
        dialog.setFilterPath(file.getParent());
        return dialog.open();
    }

    private void setValues() {
        // Gobal user details
        PersonIdent result = getUserDetails();
        userNameTextField.setText(result.getName());
        userEmailTextField.setText(result.getEmailAddress());
        
        // Workspace folder
        userRepoFolderTextField.setText(getPreferenceStore().getString(PREFS_REPOSITORY_FOLDER));
        
        // SSH details
        sshScanDirButton.setSelection(getPreferenceStore().getBoolean(PREFS_SSH_SCAN_DIR));
        sshIdentityFileTextField.setText(getPreferenceStore().getString(PREFS_SSH_IDENTITY_FILE));
        
        sshIdentityFileTextField.setEnabled(!sshScanDirButton.getSelection());
        sshIdentitySelectButton.setEnabled(!sshScanDirButton.getSelection());
        
        try {
            if(CredentialsStorage.getInstance().hasEntry(CredentialsStorage.SSH_PASSWORD)) {
                sshIdentityPasswordTextField.setText("********"); //$NON-NLS-1$
            }
        }
        catch(StorageException ex) {
            logger.log(Level.WARNING, "Could not get user password.", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }

        sshIdentityPasswordTextField.addModifyListener(event -> {
            sshPasswordChanged = true;
        });
    }
    
    @Override
    public boolean performOk() {
        try {
            RepoUtils.saveGitConfigUserDetails(userNameTextField.getText(), userEmailTextField.getText());
        }
        catch(IOException | ConfigInvalidException ex) {
            logger.log(Level.WARNING, "Could not save user details.", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }
        
        getPreferenceStore().setValue(PREFS_REPOSITORY_FOLDER, userRepoFolderTextField.getText());
        
        // SSH
        getPreferenceStore().setValue(PREFS_SSH_SCAN_DIR, sshScanDirButton.getSelection());
        getPreferenceStore().setValue(PREFS_SSH_IDENTITY_FILE, sshIdentityFileTextField.getText());

        // If SSH password changed
        if(sshPasswordChanged) {
            try {
                CredentialsStorage.getInstance().storeSecureEntry(CredentialsStorage.SSH_PASSWORD, sshIdentityPasswordTextField.getTextChars());
            }
            catch(StorageException | IOException ex) {
                logger.log(Level.WARNING, "Could not save user password.", ex); //$NON-NLS-1$
                ex.printStackTrace();
            }
        }
        
        return true;
    }
    
    @Override
    protected void performDefaults() {
        PersonIdent result = getUserDetails();
        userNameTextField.setText(result.getName());
        userEmailTextField.setText(result.getEmailAddress());

        userRepoFolderTextField.setText(getPreferenceStore().getDefaultString(PREFS_REPOSITORY_FOLDER));
        
        sshScanDirButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_SSH_SCAN_DIR));
        sshIdentityFileTextField.setText(getPreferenceStore().getDefaultString(PREFS_SSH_IDENTITY_FILE));
        sshIdentityPasswordTextField.setText(""); //$NON-NLS-1$
        
        super.performDefaults();
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