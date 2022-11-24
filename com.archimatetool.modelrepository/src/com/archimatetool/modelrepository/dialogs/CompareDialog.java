/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.ui.IArchiImages;
import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.INameable;
import com.archimatetool.model.IProperty;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * POC Compare Dialog
 * 
 * @author Phil Beauvoir
 */
@SuppressWarnings("nls")
public class CompareDialog extends ExtendedTitleAreaDialog {
    
    private IArchiRepository repo;
    private RevCommit revCommit1, revCommit2;
    
    private TreeViewer fTreeViewer;

    public CompareDialog(Shell parentShell, IArchiRepository repo, RevCommit revCommit1, RevCommit revCommit2) {
        super(parentShell, "CompareDialog");
        setTitle("Compare");
        
        this.repo = repo;

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

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Compare");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setMessage("Compare " + revCommit1.getShortMessage() + " with " + revCommit2.getShortMessage(), IMessageProvider.NONE);
        setTitleImage(IArchiImages.ImageFactory.getImage(IArchiImages.ECLIPSE_IMAGE_NEW_WIZARD));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        
        createTreeControl(container);
        
        return area;
    }
    
    private void createTreeControl(Composite parent) {
        Composite treeComp = new Composite(parent, SWT.BORDER);
        TreeColumnLayout treeLayout = new TreeColumnLayout();
        treeComp.setLayout(treeLayout);
        treeComp.setLayoutData(new GridData(GridData.FILL_BOTH));

        fTreeViewer = new TreeViewer(treeComp, SWT.MULTI | SWT.FULL_SELECTION);
        fTreeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

        // Mac Silicon Item height
        UIUtils.fixMacSiliconItemHeight(fTreeViewer.getTree());

        fTreeViewer.getTree().setHeaderVisible(true);
        fTreeViewer.getTree().setLinesVisible(false);

        // Columns
        TreeViewerColumn column1 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column1.getColumn().setText("Change");
        treeLayout.setColumnData(column1.getColumn(), new ColumnWeightData(20, true));

        TreeViewerColumn column2 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column2.getColumn().setText(revCommit1.getShortMessage());
        treeLayout.setColumnData(column2.getColumn(), new ColumnWeightData(40, true));

        TreeViewerColumn column3 = new TreeViewerColumn(fTreeViewer, SWT.NONE);
        column3.getColumn().setText(revCommit2.getShortMessage());
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
                
                // DEKETE before ADD
                if(o1 instanceof Diff && o2 instanceof Diff) {
                    Diff d1 = (Diff)o1;
                    Diff d2 = (Diff)o2;
                    if(d1.getKind() == DifferenceKind.DELETE && d2.getKind() == DifferenceKind.ADD) {
                        return -1;
                    }
                }

                if(o1 instanceof EObject && o2 instanceof EObject) {
                    String s1 = ArchiLabelProvider.INSTANCE.getLabel(o1);
                    String s2 = ArchiLabelProvider.INSTANCE.getLabel(o2);
                    return s1.compareToIgnoreCase(s2);
                }
                
                if(o1 instanceof Change && o2 instanceof Change) {
                    Change c1 = (Change)o1;
                    Change c2 = (Change)o2;
                    if(c1.getParent() instanceof INameable && c2.getParent() instanceof INameable) {
                        return ((INameable)c1.getParent()).getName().compareToIgnoreCase(((INameable)c2.getParent()).getName());
                    }
                }
                
                return 0;
            }
            
            @Override
            public int category(Object element) {
                if(element instanceof Change) {
                    element = ((Change)element).getParent();
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

        fTreeViewer.setInput(createComparison());
        fTreeViewer.expandAll();
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


    private Comparison createComparison() {
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            IArchimateModel m1 = loadModel(utils, revCommit1.getName());
            IArchimateModel m2 = loadModel(utils, revCommit2.getName());
            
            IComparisonScope scope = new DefaultComparisonScope(m2, m1, null); // Left/Right are swapped!
            return EMFCompare.builder().build().compare(scope);
        }
        catch(IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    private IArchimateModel loadModel(GitUtils utils, String revStr) throws IOException {
        File tempFolder = Files.createTempDirectory("archi-").toFile();
        
        try {
            utils.extractCommit(revStr, tempFolder, false);
            
            // Load it
            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            return modelFile.exists() ? IEditorModelManager.INSTANCE.load(modelFile) : null;
        }
        finally {
            FileUtils.deleteFolder(tempFolder);
        }
    }
    
    
    private static class Change {
        private EObject parent;
        private Set<EObject> eObjects = new HashSet<>();

        Change(EObject parent) {
            this.parent = parent;
        }

        void add(EObject eObject) {
            if(eObject != parent) {
                eObjects.add(eObject);
            }
        }

        EObject getParent() {
            return parent;
        }
        
        Set<EObject> getEObjects() {
            return eObjects;
        }
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
            if(parentElement instanceof Comparison) {
                return getChangedObjects((Comparison)parentElement).toArray();
            }
            
            if(parentElement instanceof Change) {
                Change change = (Change)parentElement;
                List<Object> list = new ArrayList<>();
                list.addAll(getDifferences(change.getParent())); // Differences of parent object
                list.addAll(change.getEObjects());               // Child objects
                return list.toArray();
            }
            
            if(parentElement instanceof EObject) {
                return getDifferences((EObject)parentElement).toArray();
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
                if(element instanceof Change) {
                    Change change = (Change)element;
                    return ArchiLabelProvider.INSTANCE.getImage(change.getParent());
                }
                if(element instanceof ReferenceChange) {
                    return ArchiLabelProvider.INSTANCE.getImage(((ReferenceChange)element).getValue());
                }
                if(element instanceof IProperty) {
                    return null;
                }
                if(element instanceof EObject) {
                    return ArchiLabelProvider.INSTANCE.getImage(getParent((EObject)element));
                }
            }
            
            return null;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    if(element instanceof Change) {
                        Change change = (Change)element;
                        return ArchiLabelProvider.INSTANCE.getLabel(change.getParent());
                    }
                    if(element instanceof Diff) {
                        return ((Diff)element).getKind().getName();
                    }
                    if(element instanceof IProperty) {
                        return ((IProperty)element).eClass().getName();
                    }
                    if(element instanceof EObject) {
                        return ArchiLabelProvider.INSTANCE.getLabel(getParent((EObject)element));
                    }
                    return null;

                case 1:
                    if(element instanceof Diff) {
                        Diff diff = (Diff)element;
                        if(diff.getKind() == DifferenceKind.ADD) {
                            return null;
                        }
                        return getChangedObjectAsString(diff, DifferenceSource.RIGHT); // Opposite!
                    }
                    return null;

                case 2:
                    if(element instanceof Diff) {
                        Diff diff = (Diff)element;
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
    
    private Comparison getComparison() {
        return (Comparison)fTreeViewer.getInput();
    }
    
    private List<Diff> getDifferences(EObject eObject) {
        Match match = getComparison().getMatch(eObject);
        if(match != null) {
            return match.getDifferences();
        }
        return new ArrayList<>();
    }

    private Collection<Change> getChangedObjects(Comparison comparison) {
        Map<EObject, Change> changes = new HashMap<>();

        for(Diff diff : comparison.getDifferences()) {
            Match match = diff.getMatch();
            
            //EObject eObject = match.getLeft() != null ? match.getLeft() : match.getRight();
            EObject eObject = match.getLeft(); // Taking Left is sufficient
            
            if(eObject != null) {
                EObject parent = getRootParent(eObject);
                
                Change change = changes.get(parent);
                if(change == null) {
                    change = new Change(parent);
                    changes.put(parent, change);
                }

                change.add(eObject);
            }
        }
        
        return changes.values();
    }
    
    /**
     * If eObject is Bounds, Properties, Feature etc then return the parent object
     */
    private EObject getParent(EObject eObject) {
        if(!(eObject instanceof IArchimateModelObject)) {
            eObject = eObject.eContainer();
        }
        
        return eObject;
    }
    
    /**
     * If eObject is Bounds, Properties, Feature etc then return the parent object
     * And if eObject is a diagram component return the diagram
     */
    private EObject getRootParent(EObject eObject) {
        eObject = getParent(eObject);
        
        if(eObject instanceof IDiagramModelComponent) {
            eObject = ((IDiagramModelComponent)eObject).getDiagramModel();
        }
        
        return eObject;
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
        if(diff instanceof ReferenceChange) {
            EObject changedObject = ((ReferenceChange)diff).getValue();
            String className = changedObject.eClass().getName();
            
            if(changedObject instanceof INameable) {
                INameable nameable = (INameable)changedObject;
                return className + " [" + nameable.getName() + "]";
            }
            
            if(changedObject instanceof IProperty) {
                IProperty property = (IProperty)changedObject;
                return className + " [" + property.getKey() + ": " + property.getValue() + "]";
            }
            
            if(changedObject instanceof IFeature) {
                IFeature feature = (IFeature)changedObject;
                return className + " [" + feature.getName() + ": " + feature.getValue() + "]";
            }
            
            if(changedObject instanceof IBounds) {
                IBounds bounds = (IBounds)changedObject;
                return className + " [x: " + bounds.getX() + ", y: " + bounds.getY() + ", w: " + bounds.getWidth() + ", h: " + bounds.getHeight() + "]";
            }
            
            return className;
        }
        
        // Get the referenced Attribute that changed
        if(diff instanceof AttributeChange) {
            EAttribute changedAttribute = ((AttributeChange)diff).getAttribute();
            Object value = eObject.eGet(changedAttribute);
            
            String s = eObject.eClass().getName();
            
            if(eObject instanceof IFeature) {
                s = ((IFeature)eObject).getName();
            }
            
            s += " [" + changedAttribute.getName() + ": " + value + "]";
            
            return s;
        }
        
        return eObject.eClass().getName();
    }

}