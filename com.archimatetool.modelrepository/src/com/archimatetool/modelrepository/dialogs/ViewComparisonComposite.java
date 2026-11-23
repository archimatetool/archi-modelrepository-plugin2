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
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Scale;

import com.archimatetool.editor.diagram.util.DiagramImageFactory;
import com.archimatetool.editor.diagram.util.DiagramImageFactory.Option;
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
    
    private enum State {
        NONE,
        SINGLE,
        DUAL
    }
    
    private State state = State.NONE;
    
    private Map<IDiagramModel, Image> images = new HashMap<>();
    
    public ViewComparisonComposite(Composite parent, int style) {
        super(parent, style);
        setLayoutData(new GridData(GridData.FILL_BOTH));
        setLayout(GridLayoutFactory.fillDefaults().create());
        
        addDisposeListener(e -> {
            for(Image image : images.values()) {
                image.dispose();
            }
            images = null;
        });
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
        if(state == State.SINGLE) {
            return;
        }
        
        clear();
        c1 = new ViewComposite(this, lastScaleValue1);
        layout();
        state = State.SINGLE;
    }
    
    private void createDualView() {
        if(state == State.DUAL) {
            return;
        }
        
        clear();
        sash = new SashForm(this, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));
        sash.setBackground(sash.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        c1 = new ViewComposite(sash, lastScaleValue1);
        c2 = new ViewComposite(sash, lastScaleValue2);
        layout();
        state = State.DUAL;
    }
    
    public void clear() {
        if(state == State.NONE) {
            return;
        }
        
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
        
        state = State.NONE;
    }
    
    private class ViewComposite extends Composite {
        private Canvas viewCanvas;
        private Image image;
        private Scale scale;
        
        private IDiagramModel diagramModel;
        
        private final AtomicInteger lastScaleValue;
        private final float[] zoom = { 1.0f };
        
        // On Windows with multi displays we need to refresh the image in the label
        private Listener zoomListener = e -> {
            setScale(scale.getSelection());
        };
        
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
                setScale(scale.getSelection());
                lastScaleValue.set(scale.getSelection());
            }));
            
            ScrolledComposite sc = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL );
            sc.setLayoutData(new GridData(GridData.FILL_BOTH));
            
            viewCanvas = new Canvas(sc, SWT.DOUBLE_BUFFERED);
            sc.setContent(viewCanvas);
            
            viewCanvas.addPaintListener(e -> {
                if(image != null && !image.isDisposed()) {
                    e.gc.setInterpolation(SWT.HIGH);
                    
                    Rectangle imgBounds = image.getBounds();
                    int destWidth = Math.round(imgBounds.width * zoom[0]);
                    int destHeight = Math.round(imgBounds.height * zoom[0]);
                    
                    e.gc.drawImage(image,
                        0, 0, imgBounds.width, imgBounds.height, // Source bounds
                        0, 0, destWidth, destHeight);            // Scaled destination bounds
                }
            });
            
            addDisposeListener(e -> {
                getShell().removeListener(SWT.ZoomChanged, zoomListener);
                diagramModel = null;
            });
            
            getShell().addListener(SWT.ZoomChanged, zoomListener);
        }
        
        private void setDiagramModel(IDiagramModel diagramModel) {
            if(this.diagramModel == diagramModel) {
                return;
            }
            
            this.diagramModel = diagramModel;
            
            if(diagramModel != null) {
                image = images.computeIfAbsent(diagramModel, key -> {
                    BusyIndicator.showWhile(Display.getCurrent(), () -> {
                        try(DiagramImageFactory imageFactory = DiagramImageFactory.withShell()) {
                            image =  imageFactory.createImage(diagramModel, 1, 5, Option.UPSCALE);
                        }
                    });
                    return image;
                });
            }
            
            scale.setVisible(diagramModel != null);
            setScale(diagramModel != null ? lastScaleValue.get() : 0);
        }

        private void setScale(int scale) {
            if(image == null || image.isDisposed()) {
                return;
            }
            
            zoom[0] = scale / 10.0f;
            Rectangle imgBounds = image.getBounds();
            int newWidth = Math.round(imgBounds.width * zoom[0]);
            int newHeight = Math.round(imgBounds.height * zoom[0]);
            viewCanvas.setSize(newWidth, newHeight);
        }
    }
}
