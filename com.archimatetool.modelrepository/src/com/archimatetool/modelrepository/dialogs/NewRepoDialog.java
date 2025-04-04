/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;

/**
 * New Repo from Model Dialog
 * 
 * @author Phil Beauvoir
 */
public class NewRepoDialog extends CloneDialog {
    
    public static final int ADD_AND_PUBLISH_ID = 1025;
    
    public NewRepoDialog(Shell parentShell, String title) {
        super(parentShell, title);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Control control = super.createDialogArea(parent);
        
        setMessage(super.getMessage() + "\n"  //$NON-NLS-1$
                + NLS.bind(Messages.NewRepoDialog_2, ModelRepositoryPlugin.getInstance().getUserModelRepositoryFolder()),
                IMessageProvider.INFORMATION);
        
        return control;
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        // If there is more than one bespoke button we have to manually set the position of the Cancel button
        if(!PlatformUtils.isWindows()) {
            createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        }
        
        createButton(parent, IDialogConstants.OK_ID, Messages.NewRepoDialog_0, true);
        
        Button addPublishButton = createButton(parent, ADD_AND_PUBLISH_ID, Messages.NewRepoDialog_1, false);
        addPublishButton.setEnabled(false);
        
        if(PlatformUtils.isWindows()) {
            createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        }
    }
    
    @Override
    protected void buttonPressed(int buttonId) {
        super.buttonPressed(buttonId);
        
        if(ADD_AND_PUBLISH_ID == buttonId) {
            setReturnCode(ADD_AND_PUBLISH_ID);
            close();
        }
    }

    @Override
    protected String validateURLField() {
        // URL is optional
        return null;
    }
    
    @Override
    public void setErrorMessage(String message) {
        super.setErrorMessage(message);
        getButton(ADD_AND_PUBLISH_ID).setEnabled(message == null && !txtURL.getText().trim().isEmpty());
    }
}