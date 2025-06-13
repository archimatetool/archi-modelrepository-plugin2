/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;

/**
 * Add Tag Dialog
 * 
 * @author Phil Beauvoir
 */
public class AddTagDialog extends ExtendedTitleAreaDialog {

    private Text textControlTagName, textControlMessage;
    private String tagName, tagMessage;
    
    public AddTagDialog(Shell parentShell) {
        super(parentShell, "AddTagDialog"); //$NON-NLS-1$
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.AddTagDialog_0);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);
        
        setTitle(Messages.AddTagDialog_1);
        setMessage(Messages.AddTagDialog_2, IMessageProvider.INFORMATION);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(2, false));

        textControlTagName = createTextField(container, Messages.AddTagDialog_3, SWT.NONE);
        
        textControlTagName.addVerifyListener(new RefNameVerifier() {
            @Override
            public void verify(String errorMessage, boolean isValid) {
                if(errorMessage != null) {
                    setMessage(errorMessage, IMessageProvider.ERROR);
                }
                else {
                    setMessage(Messages.AddTagDialog_2, IMessageProvider.INFORMATION);
                }
                
                getButton(IDialogConstants.OK_ID).setEnabled(isValid);
            }
        });
        
        Label label = new Label(container, SWT.NONE);
        label.setText(Messages.AddTagDialog_5);
        GridDataFactory.create(GridData.FILL_HORIZONTAL).span(2, 1).applyTo(label);
        
        textControlMessage = new Text(container, SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.MULTI);
        GridDataFactory.create(GridData.FILL_BOTH).span(2, 1).applyTo(textControlMessage);

        // Tab Traversal and Enter key
        UIUtils.applyTraverseListener(textControlMessage, SWT.TRAVERSE_TAB_NEXT | SWT.TRAVERSE_TAB_PREVIOUS | SWT.TRAVERSE_RETURN);
        
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
        super.createButtonsForButtonBar(parent);
        getButton(IDialogConstants.OK_ID).setEnabled(false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        tagName = textControlTagName.getText().trim();
        tagMessage = textControlMessage.getText();
        setReturnCode(buttonId);
        close();
    }
    
    @Override
    protected boolean isResizable() {
        return true;
    }
    
    @Override
    protected Point getDefaultDialogSize() {
        return new Point(580, 400);
    }

    public String getTagName() {
        return tagName;
    }
    
    public String getTagMessage() {
        return tagMessage;
    }
}