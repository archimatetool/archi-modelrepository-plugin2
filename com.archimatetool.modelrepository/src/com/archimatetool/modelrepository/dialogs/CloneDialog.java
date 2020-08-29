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
import com.archimatetool.modelrepository.repository.FileHandler;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Clone Dialog
 * 
 * @author Phil Beauvoir
 */
public class CloneDialog extends TitleAreaDialog {
    
    private String title;

    protected Text txtURL;
    protected Text txtFolder;
    protected Text txtUsername;
    protected Text txtPassword;

    protected Button storeCredentialsButton;
    
    private String URL;
    private String folder;
    private String username;
    private String password;
    private boolean doStoreCredentials;
    
    public CloneDialog(Shell parentShell, String title) {
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
        setMessage(Messages.CloneDialog_0, IMessageProvider.INFORMATION);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));
        setTitle(title);

        Composite area = (Composite) super.createDialogArea(parent);
        
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(3, false);
        container.setLayout(layout);
        
        addControls(container);
        
        return area;
    }
    
    protected void addControls(Composite parent) {
        txtFolder = createTextField(parent, Messages.CloneDialog_1, 1, SWT.NONE);
        txtFolder.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                validateFields();
            }
        });
        
        Button folderButton = new Button(parent, SWT.PUSH);
        folderButton.setText(Messages.CloneDialog_2);
        folderButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String folderPath = chooseFolderPath();
                if(folderPath != null) {
                    txtFolder.setText(folderPath);
                }
            }
        });

        txtURL = createTextField(parent, Messages.CloneDialog_3, 2, SWT.NONE);
        txtURL.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                boolean isHTTP = RepoUtils.isHTTP(txtURL.getText());
                txtUsername.setEnabled(isHTTP);
                txtPassword.setEnabled(isHTTP);
                storeCredentialsButton.setEnabled(isHTTP);
                validateFields();
            }
        });
        
        txtUsername = createTextField(parent, Messages.CloneDialog_4, 2, SWT.NONE);
        txtPassword = createTextField(parent, Messages.CloneDialog_5, 2, SWT.PASSWORD);
        
        storeCredentialsButton = new Button(parent, SWT.CHECK);
        storeCredentialsButton.setText(Messages.CloneDialog_6);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 3;
        storeCredentialsButton.setLayoutData(gd);
        storeCredentialsButton.setSelection(ModelRepositoryPlugin.INSTANCE.getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_STORE_REPO_CREDENTIALS));
    }
    
    protected Text createTextField(Composite container, String message, int span, int style) {
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
        dialog.setText(Messages.CloneDialog_7);
        dialog.setMessage(Messages.CloneDialog_8);
        dialog.setFilterPath(txtFolder.getText());
        return dialog.open();
    }

    protected void validateFields() {
        String folderError = validateFolderField();
        if(folderError != null) {
            setErrorMessage(folderError);
            return;
        }
        
        String urlError = validateURLField();
        if(urlError != null) {
            setErrorMessage(urlError);
            return;
        }
        
        setErrorMessage(null);
    }
    
    protected String validateFolderField() {
        // Folder path
        String folderPath = txtFolder.getText();
        if(!StringUtils.isSetAfterTrim(folderPath)) {
            return Messages.CloneDialog_9;
        }
        
        File file = new File(folderPath);
        
        if(file.isFile()) {
            return Messages.CloneDialog_10;
        }
        
        if(!FileHandler.isFolderEmpty(file)) {
            return Messages.CloneDialog_11;
        }
        
        return null;
    }
    
    protected String validateURLField() {
        String url = txtURL.getText();
        if(!StringUtils.isSetAfterTrim(url)) {
            return Messages.CloneDialog_12;
        }
        
        return null;
    }
    
    @Override
    protected boolean isResizable() {
        return true;
    }

    // save content of the Text fields because they get disposed
    // as soon as the Dialog closes
    protected void saveInput() {
        username = txtUsername.getText().trim();
        password = txtPassword.getText().trim();
        URL = txtURL.getText().trim();
        folder = txtFolder.getText().trim();
        doStoreCredentials = storeCredentialsButton.getSelection();
    }

    @Override
    protected void buttonPressed(int buttonId) {
        saveInput();
        super.buttonPressed(buttonId);
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
        Button okButton = createButton(parent, IDialogConstants.OK_ID, Messages.CloneDialog_13, true);
        okButton.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
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