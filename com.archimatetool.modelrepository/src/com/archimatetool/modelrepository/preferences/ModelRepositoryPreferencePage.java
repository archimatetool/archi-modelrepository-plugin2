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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
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
    
    private static final String HELP_ID = "com.archimatetool.com.modelrepository.prefs"; //$NON-NLS-1$
    
    private Text fUserNameTextField;
    private Text fUserEmailTextField;
    
    private Text fUserRepoFolderTextField;
    
    private Button fSSHIdentitySelectButton;
    private Text fSSHIdentityFileTextField;
    private Button fSSHIdentityRequiresPasswordButton;
    private Text fSSHIdentityPasswordTextField;
    private Button fSSHScanDirButton;
    
    private boolean sshPasswordChanged;
    
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
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(userDetailsGroup);
        
        new Label(userDetailsGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_1);
        
        fUserNameTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fUserNameTextField);

        new Label(userDetailsGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_2);
        
        fUserEmailTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fUserEmailTextField);
        
        
        // Workspace Group
        Group workspaceGroup = new Group(client, SWT.NULL);
        workspaceGroup.setText(Messages.ModelRepositoryPreferencePage_3);
        workspaceGroup.setLayout(new GridLayout(3, false));
        GridDataFactory.create(GridData.FILL_HORIZONTAL).hint(500, SWT.DEFAULT).applyTo(workspaceGroup);
        
        // Workspace folder location
        new Label(workspaceGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_4);
        
        fUserRepoFolderTextField = UIUtils.createSingleTextControl(workspaceGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fUserRepoFolderTextField);
        
        Button folderButton = new Button(workspaceGroup, SWT.PUSH);
        folderButton.setText(Messages.ModelRepositoryPreferencePage_5);
        folderButton.addSelectionListener(widgetSelectedAdapter(event -> {
            String folderPath = chooseFolderPath();
            if(folderPath != null) {
                fUserRepoFolderTextField.setText(folderPath);
            }
        }));
        
        // Authentication Group
        Group authGroup = new Group(client, SWT.NULL);
        authGroup.setText(Messages.ModelRepositoryPreferencePage_7);
        authGroup.setLayout(new GridLayout());
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(authGroup);
        
        // SSH Credentials Group
        Group sshGroup = new Group(authGroup, SWT.NULL);
        sshGroup.setText(Messages.ModelRepositoryPreferencePage_8);
        sshGroup.setLayout(new GridLayout(3, false));
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(sshGroup);

        fSSHScanDirButton = new Button(sshGroup, SWT.CHECK);
        fSSHScanDirButton.setText(Messages.ModelRepositoryPreferencePage_9);
        GridDataFactory.fillDefaults().span(3, 0).applyTo(fSSHScanDirButton);
        fSSHScanDirButton.addSelectionListener(widgetSelectedAdapter(event -> {
            fSSHIdentityFileTextField.setEnabled(!fSSHScanDirButton.getSelection());
            fSSHIdentitySelectButton.setEnabled(!fSSHScanDirButton.getSelection());
        }));

        new Label(sshGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_11);
        
        fSSHIdentityFileTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fSSHIdentityFileTextField);
        
        fSSHIdentitySelectButton = new Button(sshGroup, SWT.PUSH);
        fSSHIdentitySelectButton.setText(Messages.ModelRepositoryPreferencePage_12);
        fSSHIdentitySelectButton.addSelectionListener(widgetSelectedAdapter(event -> {
            String identityFile = chooseIdentityFile();
            if(identityFile != null) {
                fSSHIdentityFileTextField.setText(identityFile);
            }
        }));
        
        fSSHIdentityRequiresPasswordButton = new Button(sshGroup, SWT.CHECK);
        fSSHIdentityRequiresPasswordButton.setText(Messages.ModelRepositoryPreferencePage_13);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).span(4, SWT.DEFAULT).applyTo(fSSHIdentityRequiresPasswordButton);
        fSSHIdentityRequiresPasswordButton.addSelectionListener(widgetSelectedAdapter(event -> {
            updateIdentityControls();
        }));
        
        new Label(sshGroup, SWT.NULL).setText(Messages.ModelRepositoryPreferencePage_14);
        fSSHIdentityPasswordTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER | SWT.PASSWORD, false);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).applyTo(fSSHIdentityPasswordTextField);
        fSSHIdentityPasswordTextField.setEnabled(false);
        
        setValues();
        
        return client;
    }

    private String chooseFolderPath() {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_6);
        dialog.setMessage(Messages.ModelRepositoryPreferencePage_10);
        File file = new File(fUserRepoFolderTextField.getText());
        if(file.exists()) {
            dialog.setFilterPath(fUserRepoFolderTextField.getText());
        }
        return dialog.open();
    }

    private String chooseIdentityFile() {
        FileDialog dialog = new FileDialog(getShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_15);
        File file = new File(fSSHIdentityFileTextField.getText());
        dialog.setFilterPath(file.getParent());
        return dialog.open();
    }

    private void updateIdentityControls() {
        fSSHIdentityPasswordTextField.setEnabled(fSSHIdentityRequiresPasswordButton.getSelection());
    }
    
    private void setValues() {
        // Gobal user details
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());
        
        // Workspace folder
        fUserRepoFolderTextField.setText(getPreferenceStore().getString(PREFS_REPOSITORY_FOLDER));
        
        // SSH details
        fSSHScanDirButton.setSelection(getPreferenceStore().getBoolean(PREFS_SSH_SCAN_DIR));
        fSSHIdentityFileTextField.setText(getPreferenceStore().getString(PREFS_SSH_IDENTITY_FILE));
        fSSHIdentityRequiresPasswordButton.setSelection(getPreferenceStore().getBoolean(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD));
        
        fSSHIdentityFileTextField.setEnabled(!fSSHScanDirButton.getSelection());
        fSSHIdentitySelectButton.setEnabled(!fSSHScanDirButton.getSelection());
        
        try {
            char[] pw = CredentialsStorage.getInstance().getSSHIdentityFilePassword();
            fSSHIdentityPasswordTextField.setText(pw.length == 0 ? "" : "********"); //$NON-NLS-1$ //$NON-NLS-2$
            fSSHIdentityPasswordTextField.addModifyListener(event -> {
                sshPasswordChanged = true;
            });
        }
        catch(StorageException ex) {
            ex.printStackTrace();
            showErrorDialog(ex);
        }
        
        updateIdentityControls();
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
        
        // SSH
        getPreferenceStore().setValue(PREFS_SSH_SCAN_DIR, fSSHScanDirButton.getSelection());
        getPreferenceStore().setValue(PREFS_SSH_IDENTITY_FILE, fSSHIdentityFileTextField.getText());
        getPreferenceStore().setValue(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD, fSSHIdentityRequiresPasswordButton.getSelection());

        // If SSH password changed
        if(sshPasswordChanged) {
            try {
                CredentialsStorage.getInstance().setSSHIdentityFilePassword(fSSHIdentityPasswordTextField.getTextChars());
            }
            catch(StorageException | IOException ex) {
                ex.printStackTrace();
                showErrorDialog(ex);
            }
        }
        
        return true;
    }
    
    @Override
    protected void performDefaults() {
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());

        fUserRepoFolderTextField.setText(getPreferenceStore().getDefaultString(PREFS_REPOSITORY_FOLDER));
        
        fSSHScanDirButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_SSH_SCAN_DIR));
        fSSHIdentityFileTextField.setText(getPreferenceStore().getDefaultString(PREFS_SSH_IDENTITY_FILE));
        fSSHIdentityRequiresPasswordButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD));
        fSSHIdentityPasswordTextField.setText(""); //$NON-NLS-1$
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
    
    private void showErrorDialog(Object obj) {
        MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                Messages.ModelRepositoryPreferencePage_16,
                obj.toString());
    }
    
    @Override
    public void init(IWorkbench workbench) {
    }
}