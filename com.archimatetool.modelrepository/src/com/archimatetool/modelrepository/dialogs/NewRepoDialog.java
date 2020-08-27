/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.io.File;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * New Repo Dialog
 * 
 * @author Phil Beauvoir
 */
public class NewRepoDialog extends TitleAreaDialog {
    
    private String title;

	private Text txtURL;
	private Text txtFolder;
    private Text txtUsername;
    private Text txtPassword;

    private Button storeCredentialsButton;
    
    private String URL;
    private String folder;
    private String username;
    private String password;
    private boolean doStoreCredentials;

    public NewRepoDialog(Shell parentShell, String title) {
        super(parentShell);
        this.title = title;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setMessage("Enter details.", IMessageProvider.INFORMATION);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));
        setTitle(title);

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(3, false);
        container.setLayout(layout);
        
        txtFolder = createTextField(container, "Folder:", 1, SWT.NONE);
        txtFolder.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                validateFields();
            }
        });
        
        Button folderButton = new Button(container, SWT.PUSH);
        folderButton.setText("Choose...");
        folderButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String folderPath = chooseFolderPath();
                if(folderPath != null) {
                    txtFolder.setText(folderPath);
                }
            }
        });

        txtURL = createTextField(container, "URL:", 2, SWT.NONE);
        
        txtURL.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                boolean isHTTP = RepoUtils.isHTTP(txtURL.getText());
                txtUsername.setEnabled(isHTTP);
                txtPassword.setEnabled(isHTTP);
                storeCredentialsButton.setEnabled(isHTTP);
            }
        });
        
        txtUsername = createTextField(container, "User Name:", 2, SWT.NONE);
        txtPassword = createTextField(container, "Password:", 2, SWT.PASSWORD);
        createPreferenceButton(container);
        
        return area;
    }
    
    private Text createTextField(Composite container, String message, int span, int style) {
        Label label = new Label(container, SWT.NONE);
        label.setText(message);
        
        Text txt = UIUtils.createSingleTextControl(container, SWT.BORDER | style, false);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = span;
        txt.setLayoutData(gd);
        
        return txt;
    }
    
    private String chooseFolderPath() {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText("Choose Empty Folder");
        dialog.setMessage("Choose an empty folder for the repository.");
        dialog.setFilterPath(txtFolder.getText());
        return dialog.open();
    }

    private void createPreferenceButton(Composite container) {
        storeCredentialsButton = new Button(container, SWT.CHECK);
        storeCredentialsButton.setText("Store user name and password");
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 3;
        storeCredentialsButton.setLayoutData(gd);
        storeCredentialsButton.setSelection(ModelRepositoryPlugin.INSTANCE.getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_STORE_REPO_CREDENTIALS));
    }

    private void validateFields() {
        // Folder path
        String folderPath = txtFolder.getText();
        if(!StringUtils.isSetAfterTrim(folderPath)) {
            setErrorMessage("Select an empty folder");
            return;
        }
        
        File file = new File(folderPath);
        
        if(file.isFile()) {
            setErrorMessage("Invalid path");
            return;
        }
        
        if(file.isDirectory() && file.list().length > 0) {
            setErrorMessage("Folder should be empty");
            return;
        }
        
        setErrorMessage(null);
    }
    
    @Override
    protected boolean isResizable() {
        return true;
    }

    // save content of the Text fields because they get disposed
    // as soon as the Dialog closes
    private void saveInput() {
        username = txtUsername.getText().trim();
        password = txtPassword.getText().trim();
        URL = txtURL.getText().trim();
        folder = txtFolder.getText().trim();
        doStoreCredentials = storeCredentialsButton.getSelection();
    }

    @Override
    protected void okPressed() {
        saveInput();
        super.okPressed();
    }
    
    /**
     * Update the page status
     */
    @Override
    public void setErrorMessage(String message) {
        super.setErrorMessage(message);
        getButton(IDialogConstants.OK_ID).setEnabled(message == null);
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        getButton(IDialogConstants.OK_ID).setEnabled(false);
    }

    public UsernamePassword getUsernamePassword() {
        return new UsernamePassword(username, password);
    }
    
    public String getURL() {
        return URL;
    }
    
    public File getFolder() {
        return new File(folder);
    }
    
    public boolean doStoreCredentials() {
        return doStoreCredentials;
    }
}