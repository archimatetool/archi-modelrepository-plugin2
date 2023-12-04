/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;

import com.archimatetool.editor.diagram.util.DiagramUtils;
import com.archimatetool.model.IDiagramModel;

/**
 * Composite to compare two Diagram Models
 * 
 * @author Phillip Beauvoir
 */
public class ViewComparisonComposite extends Composite {
    
    private SashForm sash;
    private ViewComposite c1, c2;

    public ViewComparisonComposite(Composite parent, int style) {
        super(parent, style);
        setLayoutData(new GridData(GridData.FILL_BOTH));
        setLayout(GridLayoutFactory.fillDefaults().create());
    }

    public void setDiagramModel(IDiagramModel dm) {
        createSingleView();
        c1.setDiagramModel(dm);
    }
    
    public void setDiagramModels(IDiagramModel dm1, IDiagramModel dm2) {
        createDualView();
        c1.setDiagramModel(dm1);
        c2.setDiagramModel(dm2);
    }
    
    private void createSingleView() {
        if(sash != null) {
            clear();
        }
        
        if(c1 == null) {
            c1 = new ViewComposite(this);
            layout();
        }
    }
    
    private void createDualView() {
        if(sash == null) {
            clear();
            sash = new SashForm(this, SWT.HORIZONTAL);
            sash.setLayoutData(new GridData(GridData.FILL_BOTH));
            sash.setBackground(sash.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
            c1 = new ViewComposite(sash);
            c2 = new ViewComposite(sash);
            layout();
        }
    }
    
    public void clear() {
        if(c1 != null) {
            c1.dispose();
            c1 = null;
        }
        if(c2 != null) {
            c2.dispose();
            c2 = null;
        }
        if(sash != null) {
            sash.dispose();
            sash = null;
        }
    }
    
    private class ViewComposite extends Composite {
        private Label viewLabel;
        private Scale scale;
        
        private IDiagramModel diagramModel;
        private Map<Integer, Image> scaledImages;
        
        private final static int SCALES = 6;
        
        private ViewComposite(Composite parent) {
            super(parent, SWT.NONE);
            
            setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
            setBackgroundMode(SWT.INHERIT_FORCE);
            setLayout(new GridLayout());
            setLayoutData(new GridData(GridData.FILL_BOTH));
            
            scale = new Scale(this, SWT.HORIZONTAL);
            scale.setMinimum(1);
            scale.setMaximum(SCALES);
            scale.setSelection(SCALES);
            
            scale.addSelectionListener(SelectionListener.widgetSelectedAdapter(event -> {
                setScaledImage(scale.getSelection());
            }));
            
            ScrolledComposite scImage = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL );
            scImage.setLayoutData(new GridData(GridData.FILL_BOTH));
            viewLabel = new Label(scImage, SWT.NONE);
            scImage.setContent(viewLabel);
            
            addDisposeListener(e -> {
                disposeImages();
                diagramModel = null;
            });
        }
        
        private void setDiagramModel(IDiagramModel diagramModel) {
            if(this.diagramModel == diagramModel) {
                return;
            }
            
            disposeImages();
            scaledImages = new HashMap<Integer, Image>();
            
            this.diagramModel = diagramModel;
            
            scale.setVisible(diagramModel != null);
            setScaledImage(diagramModel != null ? scale.getSelection() : 0);
        }

        private void setScaledImage(int scale) {
            Image image = null;
            
            if(scale > 0) {
                image = scaledImages.get(scale);
                if(image == null) {
                    image = DiagramUtils.createImage(diagramModel, (double)scale / SCALES, 5);
                    scaledImages.put(scale, image);
                }
            }
            
            viewLabel.setImage(image);
            viewLabel.setSize(viewLabel.computeSize( SWT.DEFAULT, SWT.DEFAULT));
        }
        
        private void disposeImages() {
            if(scaledImages != null) {
                for(Image image : scaledImages.values()) {
                    image.dispose();
                }
            }
        }
    }
}
