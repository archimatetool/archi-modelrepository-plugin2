/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.modelrepository.repository.ModelComparison;
import com.archimatetool.modelrepository.repository.ModelComparison.Change;

/**
 * Compare Dialog between two revisions
 * 
 * @author Phil Beauvoir
 */
@SuppressWarnings("nls")
public class CompareDialog extends ExtendedTitleAreaDialog {
    
    private ModelComparison modelComparison;
    
    private ViewComparisonComposite viewComparisonComp;
    
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
        
        SashForm sash = new SashForm(container, SWT.VERTICAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));
        
        // Comparison Tree
        ComparisonTreeComposite treeComposite = new ComparisonTreeComposite(sash, SWT.BORDER, modelComparison);
        
        // Listen to tree selections
        treeComposite.getTreeViewer().addSelectionChangedListener(event -> {
            Object selected = event.getStructuredSelection().getFirstElement();
            
            // Selected a Change object
            if(selected instanceof Change change) {
                // Selected a DiagramModel
                if(change.getParent() instanceof IDiagramModel changedDiagramModel) {
                    EObject eObject = modelComparison.findObjectInFirstModel(changedDiagramModel.getId());
                    if(eObject instanceof IDiagramModel other) {
                        viewComparisonComp.setDiagramModels(other, changedDiagramModel);
                    }
                    else {
                        viewComparisonComp.setDiagramModel(changedDiagramModel);
                    }
                }
                else {
                    viewComparisonComp.clear();
                }
            }
            else if(selected instanceof ReferenceChange referenceChange) {
                if(referenceChange.getValue() instanceof IDiagramModel changedDiagramModel) {
                    viewComparisonComp.setDiagramModel(changedDiagramModel);
                }
                else {
                    viewComparisonComp.clear();
                }
            }
            else {
                viewComparisonComp.clear();
            }
        });
        
        // View Comparison
        viewComparisonComp = new ViewComparisonComposite(sash, SWT.BORDER);
        
        sash.setWeights(new int[] { 60, 40 });
        
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