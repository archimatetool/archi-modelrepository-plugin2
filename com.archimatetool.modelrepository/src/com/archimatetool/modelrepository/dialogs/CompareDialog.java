/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Compare Dialog
 * 
 * @author Phil Beauvoir
 */
@SuppressWarnings("nls")
public class CompareDialog extends ExtendedTitleAreaDialog {
    
    private IArchiRepository repository;
    private RevCommit revCommit1, revCommit2;
    
    public CompareDialog(Shell parentShell, IArchiRepository repository, RevCommit revCommit1, RevCommit revCommit2) {
        this(parentShell, repository);

        // Ensure commits are in correct time order
        if(revCommit1.getCommitTime() < revCommit2.getCommitTime()) {
            this.revCommit1 = revCommit1;
            this.revCommit2 = revCommit2;
        }
        else {
            this.revCommit1 = revCommit2;
            this.revCommit2 = revCommit1;
        }
    }
    
    public CompareDialog(Shell parentShell, IArchiRepository repository, RevCommit revCommit) {
        this(parentShell, repository);
        revCommit1 = revCommit;
    }

    private CompareDialog(Shell parentShell, IArchiRepository repository) {
        super(parentShell, "CompareDialog");
        setTitle("Compare");
        this.repository = repository;
    }
    
    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Compare");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        String message = revCommit2 != null ? "'" + revCommit2.getShortMessage() + "'" : "Working Changes";
        
        setMessage("Compare commit '" + revCommit1.getShortMessage() + "' with " + message, IMessageProvider.NONE);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        
        new ComparisonTreeComposite(container, SWT.BORDER, repository, revCommit1, revCommit2);
        
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