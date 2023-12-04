/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.modelrepository.repository.ModelComparison;

/**
 * Compare Dialog between two revisions
 * 
 * @author Phil Beauvoir
 */
@SuppressWarnings("nls")
public class CompareDialog extends ExtendedTitleAreaDialog {
    
    private ModelComparison modelComparison;
    
    public CompareDialog(Shell parentShell, ModelComparison modelComparison) {
        super(parentShell, "CompareDialog");
        setTitle("Compare");
        this.modelComparison = modelComparison;
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Compare");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        String message = modelComparison.isWorkingTreeComparison() ? "Working Changes" : "'" + modelComparison.getSecondRevCommit().getShortMessage() + "'";
        
        setMessage("Compare commit '" + modelComparison.getFirstRevCommit().getShortMessage() + "' with " + message, IMessageProvider.NONE);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        
        new ComparisonTreeComposite(container, SWT.BORDER, modelComparison);
        
        return area;
    }
    
    @Override
    protected boolean isResizable() {
        return true;
    }
    
    @Override
    protected Point getDefaultDialogSize() {
        return new Point(600, 450);
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        // create Done button
        createButton(parent, IDialogConstants.OK_ID, "Done", true);
    }
}