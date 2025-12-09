/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.AbstractTreeViewer;
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
import com.archimatetool.model.IArchimatePackage;
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
public class ComparisonTreeComposite extends Composite {
    
    private TreeViewer treeViewer;
    
    private IAction actionExpandSelected = new Action(Messages.ComparisonTreeComposite_0) {
        @Override
        public void run() {
            Object selected = getTreeViewer().getStructuredSelection().getFirstElement();
            if(selected != null && getTreeViewer().isExpandable(selected)) {
                treeViewer.expandToLevel(selected, AbstractTreeViewer.ALL_LEVELS);
            }
        }
        
        @Override
        public ImageDescriptor getImageDescriptor() {
            return IArchiImages.ImageFactory.getImageDescriptor(IArchiImages.ICON_EXPANDALL);
        }
    };
    
    private IAction actionCollapseSelected = new Action(Messages.ComparisonTreeComposite_1) {
        @Override
        public void run() {
            Object selected = getTreeViewer().getStructuredSelection().getFirstElement();
            if(selected != null && getTreeViewer().getExpandedState(selected)) {
                treeViewer.collapseToLevel(selected, AbstractTreeViewer.ALL_LEVELS);
            }
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
        
        // Don't use SWT.MULTI.
        // Expanding/collapsing multiple selected nodes in large models is massively slow so we only expand/collapse the first selected node.
        // If mult-selection is enabled the user might think that expand/collapse applies to all selected nodes
        treeViewer = new TreeViewer(this, SWT.FULL_SELECTION | SWT.VIRTUAL);
        treeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        treeViewer.getTree().setHeaderVisible(true);
        treeViewer.getTree().setLinesVisible(false);
        
        treeViewer.setUseHashlookup(true);  // This is important!

        // Columns
        TreeViewerColumn column1 = new TreeViewerColumn(treeViewer, SWT.NONE);
        column1.getColumn().setText(Messages.ComparisonTreeComposite_2);
        treeLayout.setColumnData(column1.getColumn(), new ColumnWeightData(33, true));

        TreeViewerColumn column2 = new TreeViewerColumn(treeViewer, SWT.NONE);
        column2.getColumn().setText(modelComparison.getFirstRevCommit().getShortMessage());
        treeLayout.setColumnData(column2.getColumn(), new ColumnWeightData(33, true));

        TreeViewerColumn column3 = new TreeViewerColumn(treeViewer, SWT.NONE);
        String message = modelComparison.isWorkingTreeComparison() ? Messages.ComparisonTreeComposite_3 : modelComparison.getSecondRevCommit().getShortMessage();
        column3.getColumn().setText(message);
        treeLayout.setColumnData(column3.getColumn(), new ColumnWeightData(33, true));

        // Content Provider
        treeViewer.setContentProvider(new ContentProvider());

        // Label Provider
        treeViewer.setLabelProvider(new LabelCellProvider());
                
        hookContextMenu();

        treeViewer.setInput(modelComparison);
    }
    
    /**
     * Hook into a right-click menu
     */
    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#ComparisonTreePopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        
        menuMgr.addMenuListener(manager -> {
            Object selected = getTreeViewer().getStructuredSelection().getFirstElement();
            
            if(selected != null) {
                // Expand selected
                if(getTreeViewer().isExpandable(selected)) {
                    manager.add(actionExpandSelected);
                }
                // Collapse selected
                if(getTreeViewer().getExpandedState(selected)) {
                    manager.add(actionCollapseSelected);
                }
            }
        });
        
        Menu menu = menuMgr.createContextMenu(treeViewer.getControl());
        treeViewer.getControl().setMenu(menu);
    }

    
    public TreeViewer getTreeViewer() {
        return treeViewer;
    }

    // Organise objects into sub-folders
    private record TreeFolder(String name, List<Change> children) {}

    private class ContentProvider implements ILazyTreeContentProvider {
        private List<Object> treeList;
        private Map<Change, List<?>> changesMap = new HashMap<>();
        
        List<?> getChildren(Object parentElement) {
            if(parentElement instanceof ModelComparison modelComparison) {
                if(treeList == null) {
                    treeList = new ArrayList<>();
                    
                    TreeFolder foldersFolder = new TreeFolder(Messages.ComparisonTreeComposite_4, new ArrayList<Change>());
                    TreeFolder elementsFolder = new TreeFolder(Messages.ComparisonTreeComposite_5, new ArrayList<Change>());
                    TreeFolder relationsFolder = new TreeFolder(Messages.ComparisonTreeComposite_6, new ArrayList<Change>());
                    TreeFolder viewsFolder = new TreeFolder(Messages.ComparisonTreeComposite_7, new ArrayList<Change>());

                    for(Change change : modelComparison.getChangedObjects()) {
                        EObject eObject = change.getChangedObject();
                        if(eObject instanceof IDiagramModelArchimateComponent dmc) {
                            eObject = dmc.getArchimateConcept();
                        }
                        
                        switch(eObject) {
                            case IArchimateModel e -> {
                                treeList.add(0, change);
                            }
                            case IFolder e -> {
                                foldersFolder.children().add(change);
                            }
                            case IArchimateElement e -> {
                                elementsFolder.children().add(change);
                            }
                            case IArchimateRelationship e -> {
                                relationsFolder.children().add(change);
                            }
                            case IDiagramModel e -> {
                                viewsFolder.children().add(change);
                            }
                            default -> {
                            }
                        }
                    }
                    
                    if(!foldersFolder.children().isEmpty()) {
                        sort(foldersFolder.children());
                        treeList.add(foldersFolder);
                    }
                    
                    if(!elementsFolder.children().isEmpty()) {
                        sortElements(elementsFolder.children());
                        treeList.add(elementsFolder);
                    }
                    
                    if(!relationsFolder.children().isEmpty()) {
                        sortRelations(relationsFolder.children());
                        treeList.add(relationsFolder);
                    }
                    
                    if(!viewsFolder.children().isEmpty()) {
                        sort(viewsFolder.children());
                        treeList.add(viewsFolder);
                    }
                }
                
                return treeList;
            }
            
            if(parentElement instanceof TreeFolder treeFolder) {
                return treeFolder.children();
            }
            
            if(parentElement instanceof Change change) {
                return changesMap.computeIfAbsent(change, c -> sort(change.getChanges()));
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
                
                if(o1 instanceof Change c1 && o2 instanceof Change c2) {
                    String s1 = ArchiLabelProvider.INSTANCE.getLabel(c1.getChangedObject());
                    String s2 = ArchiLabelProvider.INSTANCE.getLabel(c2.getChangedObject());
                    return String.CASE_INSENSITIVE_ORDER.compare(s1, s2);
                }

                return 0;
            });
            
            return children;
        }
        
        // Elements sorted by category -> class name -> element name
        List<Change> sortElements(List<Change> changes) {
            Collator collator = Collator.getInstance();
            
            changes.sort(Comparator.comparingInt((Change change) -> { // Category
                if(change.getChangedObject() instanceof IArchimateElement element) {
                    if(IArchimatePackage.eINSTANCE.getStrategyElement().isSuperTypeOf(element.eClass())) {
                        return 0;
                    }
                    if(IArchimatePackage.eINSTANCE.getBusinessElement().isSuperTypeOf(element.eClass())) {
                        return 1;
                    }
                    if(IArchimatePackage.eINSTANCE.getApplicationElement().isSuperTypeOf(element.eClass())) {
                        return 2;
                    }
                    if(IArchimatePackage.eINSTANCE.getTechnologyElement().isSuperTypeOf(element.eClass())) {
                        return 3;
                    }
                    if(IArchimatePackage.eINSTANCE.getPhysicalElement().isSuperTypeOf(element.eClass())) {
                        return 4;
                    }
                    if(IArchimatePackage.eINSTANCE.getMotivationElement().isSuperTypeOf(element.eClass())) {
                        return 5;
                    }
                    if(IArchimatePackage.eINSTANCE.getImplementationMigrationElement().isSuperTypeOf(element.eClass())) {
                        return 6;
                    }
                }
                
                return 10;
                
            })
            .thenComparing(change -> change.getChangedObject().eClass().getName(), collator::compare) // EClass name
            .thenComparing(change -> ArchiLabelProvider.INSTANCE.getLabel(change.getChangedObject()), collator::compare)); // Element name
            
            return changes;
        }
        
        // Relations sorted by class name -> relation name
        List<Change> sortRelations(List<Change> changes) {
            Collator collator = Collator.getInstance();

            changes.sort(Comparator.comparing((Change change) -> change.getChangedObject().eClass().getName(), collator::compare) // EClass name
                    .thenComparing(change -> ArchiLabelProvider.INSTANCE.getLabel(change.getChangedObject()), collator::compare) // Relation name
            );
            
            return changes;
        }
        
        @Override
        public void dispose() {
            treeList = null;
            changesMap = null;
        }
    }
    
    private class LabelCellProvider extends LabelProvider implements ITableLabelProvider {
        
        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if(columnIndex == 0) {
                switch(element) {
                    case TreeFolder treeFolder: {
                        return IArchiImages.ImageFactory.getImage(IArchiImages.ICON_FOLDER_DEFAULT);
                    }
                    case Change change: {
                        return ArchiLabelProvider.INSTANCE.getImage(change.getChangedObject());
                    }
                    case IArchimateModelObject eObject: {
                        return ArchiLabelProvider.INSTANCE.getImage(eObject);
                    }
                    default: {
                        return null;
                    }
                }
            }
            
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    switch(element) {
                        case TreeFolder treeFolder: {
                            return treeFolder.name();
                        }
                        case Change change: {
                            return ArchiLabelProvider.INSTANCE.getLabel(change.getChangedObject());
                        }
                        case Diff diff: {
                            return getDiffName(diff);
                        }
                        case EObject eObject: {
                            return ArchiLabelProvider.INSTANCE.getLabel(eObject);
                        }
                        default: {
                            return null;
                        }
                    }

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
                return className + " [" + nameable.getName() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            
            if(changedObject instanceof IProperty property) {
                return className + " [" + property.getKey() + ": " + getObjectAsSingleLine(property.getValue()) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            
            if(changedObject instanceof IFeature feature) {
                return className + " [" + feature.getName() + ": " + getObjectAsSingleLine(feature.getValue()) + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
            
            if(changedObject instanceof IBounds bounds) {
                return className + " [x: " + bounds.getX() + ", y: " + bounds.getY() + ", w: " + bounds.getWidth() + ", h: " + bounds.getHeight() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
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
            
            sb.append(" ["); //$NON-NLS-1$
            sb.append(eAttribute.getName());
            sb.append(": "); //$NON-NLS-1$
            sb.append(getObjectAsSingleLine(value));
            sb.append("]"); //$NON-NLS-1$
            
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
                return Messages.ComparisonTreeComposite_8;
            }
            case DELETE: {
                return Messages.ComparisonTreeComposite_9;
            }
            case CHANGE: {
                return Messages.ComparisonTreeComposite_10;
            }
            case MOVE: {
                return Messages.ComparisonTreeComposite_11;
            }
            default:
                return ""; //$NON-NLS-1$
        }
    }
}
