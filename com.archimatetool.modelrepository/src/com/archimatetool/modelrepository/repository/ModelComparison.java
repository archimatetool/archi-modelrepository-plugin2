/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

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
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.util.ArchimateModelUtils;

/**
 * Represents a comparison of changes between two models
 * The models can be extracted from commits and the working tree
 * 
 * Must call init() to load the models and get the Comparison
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ModelComparison {
    
    public class Change2 {
        private EObject parent;
        private Set<EObject> children = new HashSet<>();
        private Set<Diff> diffs = new HashSet<>();
        
        
        public Change2(EObject parent) {
            this.parent = parent;
        }
        
        public EObject getParent() {
            return parent;
        }
        
        public Set<Diff> getDiffs() {
            return diffs;
        }
        
        public void addDiff(Diff diff) {
            diffs.add(diff);
            
            Match match = diff.getMatch();
            EObject left = match.getLeft();
            if(left != null) {
                addChild(left);
            }
            
            // Reference of object
            if(diff instanceof ReferenceChange referenceChange) {
                //EObject eObject = referenceChange.getValue();
                //EObject parent = ModelComparison.getParent(eObject);
                //addChild(eObject);
            }
        }
        
        public EObject getRootOf(DifferenceSource source) {
            for(Diff diff : getDiffs()) {
                Match match = diff.getMatch();
                EObject eObject = source == DifferenceSource.LEFT ? match.getLeft() : match.getRight();
                if(eObject != null) {
                    EObject root = getRootParent(eObject);
                    if(root != null) {
                        return root;
                    }
                }
            }
            
            return null;
        }
        
        public Set<EObject> getChildren() {
            return children;
        }
        
        public void addChild(EObject eObject) {
            if(eObject != null && eObject != parent) {
                children.add(eObject);
            }
        }
        
        public void print() {
            System.out.println(getObjectName(getParent()));
            
            for(Diff diff : getDifferences(getParent())) {
                //System.out.println(" - " + diff);
            }
            
            for(Diff diff : getDiffs()) {
                System.out.println(" d:  - " + diff);
            }
            
            for(EObject child : getChildren()) {
                System.out.println(" c:  - " + getObjectName(child));
            }
            
            //System.out.println("  - l: " + getRootOf(DifferenceSource.LEFT));
            //System.out.println("  - r: " + getRootOf(DifferenceSource.RIGHT));
        }
    }

    
    public void getChangedObjects2() {
        Map<EObject, Change2> changes = new HashMap<>();

        for(Diff diff : comparison.getDifferences()) {
            Match match = diff.getMatch();
            
            EObject left = match.getLeft();      // Left is the most recent, can be null
            EObject right = match.getRight();    // Right is previous, can be null
            
            if(left != null) {
                EObject rootParent = getRootParent(left);
                
                Change2 change = changes.get(rootParent);
                if(change == null) {
                    change = new Change2(rootParent);
                    changes.put(rootParent, change);
                }

                change.addDiff(diff);
            }
        }
        
        System.out.println("---------------------");
        for(Change2 change : changes.values()) {
            change.print();
        }
        System.out.println();
    }
    
    private void debug() {
        printDiffs();
        getChangedObjects2();
    }
    
    private String getObjectName(EObject eObject) {
        String name = ArchiLabelProvider.INSTANCE.getLabel(eObject);
        if(name.isEmpty()) {
            name = eObject.toString();
        }
        else {
            name += " - " + eObject.eClass().getName();
        }
        return name;
    }

    private void printDiffs() {
        System.out.println("---------------------");
        for(Diff diff : comparison.getDifferences()) {
            printDiff(diff);
        }
        System.out.println();
    }
    
    private void printDiff(Diff diff) {
        Match match = diff.getMatch();
        
        EObject left = match.getLeft();      // Left is the most recent, can be null
        EObject right = match.getRight();    // Right is previous, can be null
        
        System.out.println("d: " + diff);
        System.out.println("l: " + left);
        System.out.println("r: " + right);
        
        if(diff instanceof ReferenceChange refChange) {
            System.out.println("v: " + refChange.getValue());
        }
        if(diff instanceof AttributeChange attChange) {
            System.out.println("vl: " + left.eGet(attChange.getAttribute()));
            System.out.println("vr: " + right.eGet(attChange.getAttribute()));
        }
        
        System.out.println();
    }
    
    
    
    /**
     * Represents a set of EObjects that have changed and their parent object
     */
    public static class Change {
        private EObject parent, left, right;
        private Set<EObject> eObjects = new HashSet<>();

        public Change(EObject parent, EObject left, EObject right) {
            this.parent = parent;
            this.left = left;
            this.right = right;
        }

        public void add(EObject eObject) {
            if(eObject != parent) {
                eObjects.add(eObject);
            }
        }

        public EObject getParent() {
            return parent;
        }
        
        public Set<EObject> getEObjects() {
            return eObjects;
        }
        
        public EObject getLeftParent() {
            if(left != null) {
                return getRootParent(left);
            }
            
            return null;
        }
        
        public EObject getRightParent() {
            if(right != null) {
                return getRootParent(right);
            }
            
            return null;
        }
    }
    
    private IArchiRepository repository;
    private RevCommit revCommit1, revCommit2;
    private IArchimateModel model1, model2;
    
    private Comparison comparison;

    
    /**
     * Use this for when we are comparing revCommit with the working tree
     * @param repository
     * @param revCommit1
     */
    public ModelComparison(IArchiRepository repository, RevCommit revCommit) {
        this.repository = repository;
        this.revCommit1 = revCommit;
    }

    /**
     * @param repository The Repository
     * @param revCommit1 Revision Commit 1
     * @param revCommit2 Revision Commit 2
     */
    public ModelComparison(IArchiRepository repository, RevCommit revCommit1, RevCommit revCommit2) {
        this.repository = repository;
        this.revCommit1 = revCommit1;
        this.revCommit2 = revCommit2;
        
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
    
    /**
     * @return true if we are comparing a commit with the working tree
     */
    public boolean isWorkingTreeComparison() {
        return revCommit2 == null;
    }
    
    /**
     * @return The first RevCommit
     */
    public RevCommit getFirstRevCommit() {
        return revCommit1;
    }
    
    /**
     * @return The second RevCommit, or null if we are comparing with the working tree
     */
    public RevCommit getSecondRevCommit() {
        return revCommit2;
    }
    
    /**
     * Load the two models to be compared and create the Comparison
     * @throws IOException
     */
    public Comparison init() throws IOException {
        if(comparison == null) {
            try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
                // Load the model from first commit
                model1 = loadModel(utils, revCommit1.getName());
                
                if(model1 == null) {
                    throw new IOException("Model was null for " + revCommit1.getName());
                }

                // Load the model from the second commit or the working tree. If the second commit is null, load the working tree
                model2 = isWorkingTreeComparison() ? getWorkingTreeModel() : loadModel(utils, revCommit2.getName());
                
                if(model2 == null) {
                    throw new IOException("Model was null for " + (isWorkingTreeComparison() ? "working tree" : revCommit1.getName()));
                }
                
                IComparisonScope scope = new DefaultComparisonScope(model2, model1, null); // Left/Right are swapped!
                comparison = EMFCompare.builder().build().compare(scope);
                
                // TODO: Remove this
                debug();
            }
        }
        
        return comparison;
    }
    
    /**
     * @return The Changed objects
     */
    public Collection<Change> getChangedObjects() {
        Map<EObject, Change> changes = new HashMap<>();

        for(Diff diff : comparison.getDifferences()) {
            Match match = diff.getMatch();
            
            EObject left = match.getLeft();      // Left is the most recent, can be null
            EObject right = match.getRight();    // Right is previous, can be null
            
            if(left != null) {
                EObject parent = getRootParent(left);
                
                Change change = changes.get(parent);
                if(change == null) {
                    change = new Change(parent, left, right);
                    changes.put(parent, change);
                }

                change.add(left);
            }
        }
        
        return changes.values();
    }
    
    /**
     * @return The list of differences for eObject. Never null, but can be empty
     */
    public List<Diff> getDifferences(EObject eObject) {
        Match match = comparison.getMatch(eObject);
        if(match != null) {
            return match.getDifferences();
        }
        return new ArrayList<>();
    }

    /**
     * Get the parent eContainer of eObject if eObject is Bounds, Properties, Feature, Profile
     * Else return the object itself
     */
    public static EObject getParent(EObject eObject) {
        if(eObject != null && (!(eObject instanceof IArchimateModelObject) || eObject instanceof IProfile)) {
            eObject = eObject.eContainer();
        }
        
        return eObject;
    }
    
    /**
     * If eObject is Bounds, Properties, Feature or Profile then return the parent object.
     * If eObject is a diagram component return the diagram
     */
    private static EObject getRootParent(EObject eObject) {
        eObject = getParent(eObject);
        
        if(eObject instanceof IDiagramModelComponent dmc) {
            eObject = dmc.getDiagramModel();
        }
        
        return eObject;
    }
    
    /**
     * Find an object by ID in the first model (the oldest commit)
     */
    public EObject findObjectInFirstModel(String id) {
        return ArchimateModelUtils.getObjectByID(model1, id);
    }
    
    /**
     * Find an object by ID in the second model (the later commit, or working tree)
     */
    public EObject findObjectInSecondModel(String id) {
        return ArchimateModelUtils.getObjectByID(model2, id);
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

}
