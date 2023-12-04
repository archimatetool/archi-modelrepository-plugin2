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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.ui.UIUtils;
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
 * Tree Composite to show comparison between two models
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ComparisonTreeComposite extends Composite {
    
    private TreeViewer fTreeViewer;
    
    private IArchiRepository repository;
    private RevCommit revCommit1, revCommit2;
    
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
    
    public ComparisonTreeComposite(Composite parent, int style, IArchiRepository repository, RevCommit revCommit1, RevCommit revCommit2) {
        super(parent, style);
        
        this.repository = repository;
        this.revCommit1 = revCommit1;
        this.revCommit2 = revCommit2;
        
        TreeColumnLayout treeLayout = new TreeColumnLayout();
        setLayout(treeLayout);
        setLayoutData(new GridData(GridData.FILL_BOTH));
        
        fTreeViewer = new TreeViewer(this, SWT.MULTI | SWT.FULL_SELECTION);
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
        String message = revCommit2 != null ? revCommit2.getShortMessage() : "Working Changes";
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

        fTreeViewer.setInput(createComparison());
        fTreeViewer.expandAll();
    }

    /**
     * Create the EMF Comparison between the two models in the two revisions (or working tree)
     */
    private Comparison createComparison() {
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            // Load the model from first commit
            IArchimateModel model1 = loadModel(utils, revCommit1.getName());
            
            // Load the model from the second commit or the working tree. If the second commit is null, load the working tree
            IArchimateModel model2 = revCommit2 != null ? loadModel(utils, revCommit2.getName()) : getWorkingTreeModel();
            
            IComparisonScope scope = new DefaultComparisonScope(model2, model1, null); // Left/Right are swapped!
            return EMFCompare.builder().build().compare(scope);
        }
        catch(IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
    
    private IArchimateModel getWorkingTreeModel() throws IOException {
        // Do we have the model open in the UI?
        IArchimateModel model = repository.getOpenModel();
        
        // No, so load it
        if(model == null) {
            model = IEditorModelManager.INSTANCE.load(repository.getModelFile());
        }
        
        return model;
    }
    
    /**
     * Load a model from its revision string
     */
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
            if(parentElement instanceof Comparison comparison) {
                return getChangedObjects(comparison).toArray();
            }
            
            if(parentElement instanceof Change change) {
                List<Object> list = new ArrayList<>();
                list.addAll(getDifferences(change.getParent())); // Differences of parent object
                list.addAll(change.getEObjects());               // Child objects
                return list.toArray();
            }
            
            if(parentElement instanceof EObject eObject) {
                return getDifferences(eObject).toArray();
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
                    return ArchiLabelProvider.INSTANCE.getImage(getParent(eObject));
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
                        return ArchiLabelProvider.INSTANCE.getLabel(getParent(eObject));
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
        
        if(eObject instanceof IDiagramModelComponent dmc) {
            eObject = dmc.getDiagramModel();
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
