/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperty;
import com.archimatetool.modelrepository.repository.ModelComparison;
import com.archimatetool.modelrepository.repository.ModelComparison.Change;


/**
 * Tree Composite to show comparison between two models
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ComparisonTreeComposite extends Composite {
    
    private TreeViewer fTreeViewer;
    
    private ModelComparison modelComparison;
    
    public ComparisonTreeComposite(Composite parent, int style, ModelComparison modelComparison) {
        super(parent, style);
        
        this.modelComparison = modelComparison;
        
        TreeColumnLayout treeLayout = new TreeColumnLayout();
        setLayout(treeLayout);
        setLayoutData(new GridData(GridData.FILL_BOTH));
        
        fTreeViewer = new TreeViewer(this, SWT.MULTI | SWT.FULL_SELECTION);
        fTreeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        fTreeViewer.getTree().setHeaderVisible(true);
        fTreeViewer.getTree().setLinesVisible(false);

        // Columns
        TreeViewerColumn column1 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column1.getColumn().setText("Change");
        treeLayout.setColumnData(column1.getColumn(), new ColumnWeightData(20, true));

        TreeViewerColumn column2 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column2.getColumn().setText(modelComparison.getFirstRevCommit().getShortMessage());
        treeLayout.setColumnData(column2.getColumn(), new ColumnWeightData(40, true));

        TreeViewerColumn column3 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        String message = modelComparison.isWorkingTreeComparison() ? "Working Changes" : modelComparison.getSecondRevCommit().getShortMessage();
        column3.getColumn().setText(message);
        treeLayout.setColumnData(column3.getColumn(), new ColumnWeightData(40, true));

        // Content Provider
        fTreeViewer.setContentProvider(new ContentProvider());

        // Label Provider
        fTreeViewer.setLabelProvider(new LabelCellProvider());
        
        fTreeViewer.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2) {
                int cat1 = category(o1);
                int cat2 = category(o2);
                if(cat1 != cat2) {
                    return cat1 - cat2;
                }
                
                // DELETE before ADD
                if(o1 instanceof Diff d1 && o2 instanceof Diff d2) {
                    if(d1.getKind() == DifferenceKind.DELETE && d2.getKind() == DifferenceKind.ADD) {
                        return -1;
                    }
                }

                if(o1 instanceof EObject && o2 instanceof EObject) {
                    String s1 = ArchiLabelProvider.INSTANCE.getLabel(o1);
                    String s2 = ArchiLabelProvider.INSTANCE.getLabel(o2);
                    return s1.compareToIgnoreCase(s2);
                }
                
                if(o1 instanceof Change c1 && o2 instanceof Change c2) {
                    if(c1.getParent() instanceof INameable && c2.getParent() instanceof INameable) {
                        return ((INameable)c1.getParent()).getName().compareToIgnoreCase(((INameable)c2.getParent()).getName());
                    }
                }
                
                return 0;
            }
            
            @Override
            public int category(Object element) {
                if(element instanceof Change change) {
                    element = change.getParent();
                }
                
                if(element instanceof IArchimateModel) {
                    return 0;
                }
                if(element instanceof IFolder) {
                    return 1;
                }
                if(element instanceof IArchimateConcept) {
                    return 2;
                }
                if(element instanceof IDiagramModel) {
                    return 3;
                }
                
                return 0;
            }
        });

        fTreeViewer.setInput(modelComparison);
        fTreeViewer.expandAll();
    }
    
    public TreeViewer getTreeViewer() {
        return fTreeViewer;
    }

    private class ContentProvider implements ITreeContentProvider {
        
        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        @Override
        public void dispose() {
        }

        @Override
        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if(parentElement instanceof ModelComparison modelComparison) {
                return modelComparison.getChangedObjects().toArray();
            }
            
            if(parentElement instanceof Change change) {
                List<Object> list = new ArrayList<>();
                list.addAll(modelComparison.getDifferences(change.getParent())); // Differences of parent object
                list.addAll(change.getEObjects());               // Child objects
                return list.toArray();
            }
            
            if(parentElement instanceof EObject eObject) {
                return modelComparison.getDifferences(eObject).toArray();
            }
            
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return true; // If we expand all tree nodes returning true works fine
            //return getChildren(element).length > 0;
        }
    }
    
    private class LabelCellProvider extends LabelProvider implements ITableLabelProvider {
        
        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if(columnIndex == 0) {
                if(element instanceof Change change) {
                    return ArchiLabelProvider.INSTANCE.getImage(change.getParent());
                }
                if(element instanceof ReferenceChange referenceChange) {
                    return ArchiLabelProvider.INSTANCE.getImage(referenceChange.getValue());
                }
                if(element instanceof IProperty) {
                    return null;
                }
                if(element instanceof EObject eObject) {
                    return ArchiLabelProvider.INSTANCE.getImage(modelComparison.getParent(eObject));
                }
            }
            
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    if(element instanceof Change change) {
                        return ArchiLabelProvider.INSTANCE.getLabel(change.getParent());
                    }
                    if(element instanceof Diff diff) {
                        return diff.getKind().getName();
                    }
                    if(element instanceof IProperty property) {
                        return property.eClass().getName();
                    }
                    if(element instanceof EObject eObject) {
                        return ArchiLabelProvider.INSTANCE.getLabel(modelComparison.getParent(eObject));
                    }
                    return null;

                case 1:
                    if(element instanceof Diff diff) {
                        if(diff.getKind() == DifferenceKind.ADD) {
                            return null;
                        }
                        return getChangedObjectAsString(diff, DifferenceSource.RIGHT); // Opposite!
                    }
                    return null;

                case 2:
                    if(element instanceof Diff diff) {
                        if(diff.getKind() == DifferenceKind.DELETE) {
                            return null;
                        }
                        return getChangedObjectAsString(diff, DifferenceSource.LEFT); // Opposite!
                    }
                    return null;

                default:
                    return null;
            }
        }
    }
    
    /**
     * Return the underlying object that changed as a string representation
     */
    private String getChangedObjectAsString(Diff diff, DifferenceSource source) {
        Match match = diff.getMatch();
        
        EObject eObject = (source == DifferenceSource.LEFT) ? match.getLeft() : match.getRight();
        if(eObject == null) {
            return null;
        }
        
        // Get the referenced EObject that changed
        if(diff instanceof ReferenceChange referenceChange) {
            EObject changedObject = referenceChange.getValue();
            String className = changedObject.eClass().getName();
            
            if(changedObject instanceof INameable nameable) {
                return className + " [" + nameable.getName() + "]";
            }
            
            if(changedObject instanceof IProperty property) {
                return className + " [" + property.getKey() + ": " + property.getValue() + "]";
            }
            
            if(changedObject instanceof IFeature feature) {
                return className + " [" + feature.getName() + ": " + feature.getValue() + "]";
            }
            
            if(changedObject instanceof IBounds bounds) {
                return className + " [x: " + bounds.getX() + ", y: " + bounds.getY() + ", w: " + bounds.getWidth() + ", h: " + bounds.getHeight() + "]";
            }
            
            return className;
        }
        
        // Get the referenced Attribute that changed
        if(diff instanceof AttributeChange attributeChange) {
            EAttribute eAttribute = attributeChange.getAttribute();
            Object value = eObject.eGet(eAttribute);
            
            String s = eObject.eClass().getName();
            
            if(eObject instanceof IFeature feature) {
                s = feature.getName();
            }
            
            s += " [" + eAttribute.getName() + ": " + value + "]";
            
            return s;
        }
        
        return eObject.eClass().getName();
    }

}
