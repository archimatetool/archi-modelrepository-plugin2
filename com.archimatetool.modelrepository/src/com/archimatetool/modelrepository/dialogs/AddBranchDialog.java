/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Add Branch Dialog
 * 
 * @author Phil Beauvoir
 */
public class AddBranchDialog extends TitleAreaDialog {

    public static final int ADD_BRANCH = 1024;
    public static final int ADD_BRANCH_CHECKOUT = 1025;
    
	private Text txtBranch;
	private String branchName;
	
    public AddBranchDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.AddBranchDialog_1);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);
        
        setTitle(Messages.AddBranchDialog_0);
        setMessage(Messages.AddBranchDialog_2, IMessageProvider.INFORMATION);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);

        txtBranch = createTextField(container, Messages.AddBranchDialog_3, SWT.NONE);
        
        txtBranch.addVerifyListener(event -> {
            // Replace space ^ ~ * ? [ \ 
            event.text = event.text.replaceAll("[ :^~*\\?\\[\\\\]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
            
            String currentText = ((Text)event.widget).getText();
            String newText = (currentText.substring(0, event.start) + event.text + currentText.substring(event.end));
            
            setMessage(Messages.AddBranchDialog_2, IMessageProvider.INFORMATION);
            
            boolean isValidRefName = !newText.isEmpty();
            
            if(isValidRefName) {
                isValidRefName = Repository.isValidRefName(RepoConstants.R_HEADS + newText);
                if(!isValidRefName) {
                    if(newText.startsWith(".") || newText.endsWith(".")) { //$NON-NLS-1$ //$NON-NLS-2$
                        setMessage(Messages.AddBranchDialog_4, IMessageProvider.ERROR);
                    }
                    else if(newText.startsWith("/") || newText.endsWith("/")) { //$NON-NLS-1$ //$NON-NLS-2$
                        setMessage(Messages.AddBranchDialog_5, IMessageProvider.ERROR);
                    }
                    else {
                        setMessage(Messages.AddBranchDialog_6, IMessageProvider.ERROR);
                    }
                }
            }
            
            getButton(ADD_BRANCH).setEnabled(isValidRefName);
            getButton(ADD_BRANCH_CHECKOUT).setEnabled(isValidRefName);
        });
        
        return area;
    }
    
    private Text createTextField(Composite container, String message, int style) {
        Label label = new Label(container, SWT.NONE);
        label.setText(message);
        
        Text txt = UIUtils.createSingleTextControl(container, SWT.BORDER | style, false);
        txt.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        return txt;
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        Button b = createButton(parent, ADD_BRANCH, Messages.AddBranchDialog_1, false);
        b.setEnabled(false);
        
        b = createButton(parent, ADD_BRANCH_CHECKOUT, Messages.AddBranchDialog_7, false);
        b.setEnabled(false);
        
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
    
    @Override
    protected void buttonPressed(int buttonId) {
        branchName = txtBranch.getText().trim();
        setReturnCode(buttonId);
        close();
    }

    
    @Override
    protected boolean isResizable() {
        return true;
    }

    public String getBranchName() {
        return branchName;
    }
}