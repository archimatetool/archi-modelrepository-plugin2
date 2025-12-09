/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperty;
import com.archimatetool.modelrepository.merge.ModelComparison;
import com.archimatetool.modelrepository.merge.ModelComparison.Change;


/**
 * Tree Composite to show comparison between two models
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ComparisonTreeComposite extends Composite {
    
    private TreeViewer fTreeViewer;
    
    private IAction fActionCollapseTree = new Action("Collapse All") {
        @Override
        public void run() {
            fTreeViewer.collapseAll();
        }
        
        @Override
        public ImageDescriptor getImageDescriptor() {
            return IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_COLLAPSEALL);
        }
    };
    
    private IAction fActionExpandTree = new Action("Expand All") {
        @Override
        public void run() {
            fTreeViewer.expandAll();
        }
        
        @Override
        public ImageDescriptor getImageDescriptor() {
            return IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_EXPANDALL);
        }
    };

    
    public ComparisonTreeComposite(Composite parent, int style, ModelComparison modelComparison) {
        super(parent, style);
        
        TreeColumnLayout treeLayout = new TreeColumnLayout();
        setLayout(treeLayout);
        setLayoutData(new GridData(GridData.FILL_BOTH));
        
        fTreeViewer = new TreeViewer(this, SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
        fTreeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        fTreeViewer.getTree().setHeaderVisible(true);
        fTreeViewer.getTree().setLinesVisible(false);
        
        fTreeViewer.setUseHashlookup(true);  // This is important!

        // Columns
        TreeViewerColumn column1 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column1.getColumn().setText("Change");
        treeLayout.setColumnData(column1.getColumn(), new ColumnWeightData(33, true));

        TreeViewerColumn column2 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column2.getColumn().setText(modelComparison.getFirstRevCommit().getShortMessage());
        treeLayout.setColumnData(column2.getColumn(), new ColumnWeightData(33, true));

        TreeViewerColumn column3 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        String message = modelComparison.isWorkingTreeComparison() ? "Working Changes" : modelComparison.getSecondRevCommit().getShortMessage();
        column3.getColumn().setText(message);
        treeLayout.setColumnData(column3.getColumn(), new ColumnWeightData(33, true));

        // Content Provider
        fTreeViewer.setContentProvider(new ContentProvider());

        // Label Provider
        fTreeViewer.setLabelProvider(new LabelCellProvider());
                
        hookContextMenu();

        fTreeViewer.setInput(modelComparison);
    }
    
    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#ComparisonTreePopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                manager.add(fActionCollapseTree);
                manager.add(fActionExpandTree);
            }
        });
        
        Menu menu = menuMgr.createContextMenu(fTreeViewer.getControl());
        fTreeViewer.getControl().setMenu(menu);
    }

    
    public TreeViewer getTreeViewer() {
        return fTreeViewer;
    }

    private class ContentProvider implements ILazyTreeContentProvider {
        private List<?> changes;
        private Map<Change, List<?>> map = new HashMap<>();
        
        List<?> getChildren(Object parentElement) {
            if(parentElement instanceof ModelComparison modelComparison) {
                if(changes == null) {
                    changes = sort(modelComparison.getChangedObjects());
                }
                return changes;
            }
            
            if(parentElement instanceof Change change) {
                return map.computeIfAbsent(change, c -> sort(change.getChanges()));
            }
            
            return Collections.EMPTY_LIST;
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public void updateElement(Object parent, int index) {
            List<?> children = getChildren(parent);
            if(!children.isEmpty()) {
                Object element = children.get(index);
                getTreeViewer().replace(parent, index, element);
                getTreeViewer().setChildCount(element, getChildren(element).size());
            }
        }

        @Override
        public void updateChildCount(Object element, int currentChildCount) {
            getTreeViewer().setChildCount(element, getChildren(element).size());
        }
        
        List<?> sort(List<?> children) {
            children.sort((Object o1, Object o2) -> {
                int cat1 = sortCategory(o1);
                int cat2 = sortCategory(o2);
                if(cat1 != cat2) {
                    return cat1 - cat2;
                }

                if(o1 instanceof Diff d1 && o2 instanceof Diff d2) {
                    // ADD before CHANGE
                    if(d1.getKind() == DifferenceKind.ADD && d2.getKind() == DifferenceKind.CHANGE) {
                        return -1;
                    }
                    // DELETE before ADD
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
                    if(c1.getChangedObject() instanceof INameable nameable1 && c2.getChangedObject() instanceof INameable nameable2) {
                        return nameable1.getName().compareToIgnoreCase(nameable2.getName());
                    }
                }

                return 0;
            });
            
            return children;
        }

        int sortCategory(Object element) {
            if(element instanceof Change change) {
                element = change.getChangedObject();
            }

            if(element instanceof IDiagramModelArchimateComponent dmc) {
                element = dmc.getArchimateConcept();
            }

            if(element instanceof IArchimateModel) {
                return 0;
            }
            if(element instanceof IFolder) {
                return 1;
            }
            if(element instanceof IArchimateElement) {
                return 2;
            }
            if(element instanceof IArchimateRelationship) {
                return 3;
            }
            if(element instanceof IDiagramModel) {
                return 4;
            }

            return 0;
        }

        @Override
        public void dispose() {
            changes = null;
            map = null;
        }
    }
    
    private class LabelCellProvider extends LabelProvider implements ITableLabelProvider {
        
        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if(columnIndex == 0) {
                if(element instanceof Change change) {
                    return ArchiLabelProvider.INSTANCE.getImage(change.getChangedObject());
                }
                else if(element instanceof IArchimateModelObject eObject) {
                    return ArchiLabelProvider.INSTANCE.getImage(eObject);
                }
            }
            
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    if(element instanceof Change change) {
                        return ArchiLabelProvider.INSTANCE.getLabel(change.getChangedObject());
                    }
                    if(element instanceof Diff diff) {
                        return getDiffName(diff);
                    }
                    if(element instanceof EObject eObject) {
                        return ArchiLabelProvider.INSTANCE.getLabel(eObject);
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
        // Get the referenced EObject that changed
        if(diff instanceof ReferenceChange referenceChange) {
            EObject changedObject = referenceChange.getValue();
            String className = changedObject.eClass().getName();
            
            if(changedObject instanceof INameable nameable) {
                return className + " [" + nameable.getName() + "]";
            }
            
            if(changedObject instanceof IProperty property) {
                return className + " [" + property.getKey() + ": " + getObjectAsSingleLine(property.getValue()) + "]";
            }
            
            if(changedObject instanceof IFeature feature) {
                return className + " [" + feature.getName() + ": " + getObjectAsSingleLine(feature.getValue()) + "]";
            }
            
            if(changedObject instanceof IBounds bounds) {
                return className + " [x: " + bounds.getX() + ", y: " + bounds.getY() + ", w: " + bounds.getWidth() + ", h: " + bounds.getHeight() + "]";
            }
            
            return className;
        }
        
        Match match = diff.getMatch();
        EObject eObject = (source == DifferenceSource.LEFT) ? match.getLeft() : match.getRight();
        if(eObject == null) {
            return null;
        }
        
        // Get the referenced Attribute that changed
        if(diff instanceof AttributeChange attributeChange) {
            // Get the Attribute that changed
            EAttribute eAttribute = attributeChange.getAttribute();
            // Get the value of the Attribute
            Object value = eObject.eGet(eAttribute);
            
            StringBuilder sb = new StringBuilder();
            
            if(eObject instanceof IFeature feature) {
                sb.append(feature.getName());
            }
            else {
                sb.append(eObject.eClass().getName());
            }
            
            sb.append(" [");
            sb.append(eAttribute.getName());
            sb.append(": ");
            sb.append(getObjectAsSingleLine(value));
            sb.append("]");
            
            return sb.toString();
        }
        
        return eObject.eClass().getName();
    }
    
    // Convert the object into a String and remove any newlines
    private String getObjectAsSingleLine(Object object) {
        return StringUtils.normaliseNewLineCharacters(String.valueOf(object));
    }

    private String getDiffName(Diff diff) {
        switch(diff.getKind()) {
            case ADD: {
                return "Added";
            }
            case DELETE: {
                return "Deleted";
            }
            case CHANGE: {
                return "Changed";
            }
            case MOVE: {
                return "Moved";
            }
            default:
                return "";
        }
    }
}
