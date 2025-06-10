/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
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

/**
 * Add Branch Dialog
 * 
 * @author Phil Beauvoir
 */
public class AddBranchDialog extends TitleAreaDialog {

    public static final int ADD_BRANCH = 1024;
    public static final int ADD_BRANCH_CHECKOUT = 1025;
    
	private Text textControl;
	private String name;
	
	private boolean checkoutButton;
	
    public AddBranchDialog(Shell parentShell, boolean checkoutButton) {
        super(parentShell);
        this.checkoutButton = checkoutButton;
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

        textControl = createTextField(container, Messages.AddBranchDialog_3, SWT.NONE);
        
        textControl.addVerifyListener(new RefNameVerifier() {
            @Override
            public void verify(String errorMessage, boolean isValid) {
                if(errorMessage != null) {
                    setMessage(errorMessage, IMessageProvider.ERROR);
                }
                else {
                    setMessage(Messages.AddBranchDialog_2, IMessageProvider.INFORMATION);
                }
                
                getButton(ADD_BRANCH).setEnabled(isValid);
                
                if(checkoutButton) {
                    getButton(ADD_BRANCH_CHECKOUT).setEnabled(isValid);
                }
            }
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
        Button addButton = createButton(parent, ADD_BRANCH, Messages.AddBranchDialog_1, false);
        addButton.setEnabled(false);
        
        if(checkoutButton) {
            Button addCheckoutButton = createButton(parent, ADD_BRANCH_CHECKOUT, Messages.AddBranchDialog_4, false);
            addCheckoutButton.setEnabled(false);
        }
        
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
    
    @Override
    protected void buttonPressed(int buttonId) {
        name = textControl.getText().trim();
        setReturnCode(buttonId);
        close();
    }
    
    @Override
    protected boolean isResizable() {
        return true;
    }

    public String getName() {
        return name;
    }
}