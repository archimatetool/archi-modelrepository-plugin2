/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    
    private final static int SCALE_MAX = 10;
    
    // Remember scale values for each panel
    private static AtomicInteger lastScaleValue1 = new AtomicInteger(SCALE_MAX);
    private static AtomicInteger lastScaleValue2 = new AtomicInteger(SCALE_MAX);
    
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
            c1 = new ViewComposite(this, lastScaleValue1);
            layout();
        }
    }
    
    private void createDualView() {
        if(sash == null) {
            clear();
            sash = new SashForm(this, SWT.HORIZONTAL);
            sash.setLayoutData(new GridData(GridData.FILL_BOTH));
            sash.setBackground(sash.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
            c1 = new ViewComposite(sash, lastScaleValue1);
            c2 = new ViewComposite(sash, lastScaleValue2);
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
        
        private AtomicInteger lastScaleValue;
        
        private ViewComposite(Composite parent, AtomicInteger lastScaleValue) {
            super(parent, SWT.NONE);
            
            this.lastScaleValue = lastScaleValue;
            
            setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
            setBackgroundMode(SWT.INHERIT_FORCE);
            setLayout(new GridLayout());
            setLayoutData(new GridData(GridData.FILL_BOTH));
            
            scale = new Scale(this, SWT.HORIZONTAL);
            scale.setMinimum(1);
            scale.setMaximum(SCALE_MAX);
            scale.setPageIncrement(2);
            scale.setSelection(lastScaleValue.get());
            
            scale.addSelectionListener(SelectionListener.widgetSelectedAdapter(event -> {
                setScaledImage(scale.getSelection());
                lastScaleValue.set(scale.getSelection());
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
            scaledImages = new HashMap<>();
            
            this.diagramModel = diagramModel;
            
            scale.setVisible(diagramModel != null);
            setScaledImage(diagramModel != null ? lastScaleValue.get() : 0);
        }

        private void setScaledImage(int scale) {
            Image image = null;
            
            if(scale > 0) {
                image = scaledImages.get(scale);
                if(image == null) {
                    image = DiagramUtils.createImage(diagramModel, (double)scale / SCALE_MAX, 5);
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
