/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.merge;

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
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

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
    
/*

- A Comparison consists of a number of Diffs
- A Diff has a Match
- A Match gives us Left (current) and Right (previous) objects from the current and previous models
- If Left is null the object was deleted
- If Right is null the object was added

- The Diff can be:
    - ReferenceChange: a child object added/deleted.
                       getValue() returns this child object.
                       This child object can be an element, relation, profile, feature, bounds. 
    - AttributeChange: an existing attribute of an object is changed.
                       getAttribute() is used get the attribute to get its value from left or right.

*/
    
    /**
     * Represents a model object change of interest.
     * changedObject is the object of interest to display in a tree.
     * children are the child objects (in a View)
     * diffs are the diffs of interest related to the changed object.
     */
    public static class Change {
        private EObject changedObject;
        private Set<Diff> diffs = new HashSet<>();
        private Map<EObject, Change> children = new HashMap<>();
        private List<Object> changes;
        
        public Change(EObject changedObject) {
            this.changedObject = changedObject;
        }
        
        public EObject getChangedObject() {
            return changedObject;
        }
        
        public Set<Diff> getDiffs() {
            return diffs;
        }
        
        public Collection<Change> getChildren() {
            return children.values();
        }
        
        public List<Object> getChanges() {
            if(changes == null) {
                changes = new ArrayList<>();
                changes.addAll(getDiffs());
                changes.addAll(getChildren());
            }
            return changes;
        }
        
        private Change addChild(EObject eObject) {
            return children.computeIfAbsent(eObject, object -> new Change(object));
        }
        
        private void addDiff(Diff diff) {
            diffs.add(diff);
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
    public ModelComparison init() throws IOException {
        if(comparison != null) {
            return this;
        }
        
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
            
            // Create Comparison
            comparison = MergeFactory.createComparison(model2, model1, null);  // Left/Right are swapped!
        }
        
        return this;
    }
    
    /**
     * @return The Comparison. Ensure init() is called first.
     */
    public Comparison getComparison() {
        return comparison;
    }
    
    /**
     * @return The Objects that show interesting changes
     */
    public Collection<Change> getChangedObjects() {
        Map<EObject, Change> changes = new HashMap<>();

        for(Diff diff : comparison.getDifferences()) {
            Match match = diff.getMatch();
            
            EObject changedObject = match.getLeft();      // Left is the most recent, can be null
            
            if(changedObject != null) {
                // Root parent of changed object
                EObject rootObject = getRootParent(changedObject);
                
                // Reference of object (object added/deleted/moved)
                if(diff instanceof ReferenceChange referenceChange) {
                    // If the changed object is a folder, get the referenceChange (child) object
                    if(changedObject instanceof IFolder) {
                        rootObject = referenceChange.getValue();
                    }
                    // If the changed object is a diagram object container and referenceChange is a child dmo, get the dmo
                    if(changedObject instanceof IDiagramModelContainer && referenceChange.getValue() instanceof IDiagramModelObject dmo) {
                        // TODO: Removing this line fixes the bug of getting the wrong IDiagramModel, but do we need to do it?
                        //rootObject = getRootParent(dmo);
                        changedObject = dmo;
                    }
                }
                
                // Add it
                Change change = changes.computeIfAbsent(rootObject, object -> new Change(object));
                
                // If the parent object is a Diagram Model Component add it as a child
                if(rootObject instanceof IDiagramModel) {
                    EObject eObject = getParent(changedObject);
                    if(eObject instanceof IDiagramModelComponent && eObject != rootObject) {
                        change = change.addChild(eObject);
                    }
                }

                // Add the diff
                change.addDiff(diff);
            }
        }
        
        // Debug
        // printChanges(changes.values());
        
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
    private static EObject getParent(EObject eObject) {
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
        // Load it from file in all cases, not from the open model in the Models Tree
        // For example, if we do a model comparison when we restore a commit the restored commit is written to the working folder
        return IEditorModelManager.INSTANCE.load(repository.getModelFile());
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
    
    

    // ================================ DEBUG STUFF ==========================================
    
    void printChanges(Collection<Change> changes) {
        System.out.println("================ NEW COMPARISON ================");
        System.out.println();
        
        System.out.println("----------- Diffs ------------");
        System.out.println();
        for(Diff diff : comparison.getDifferences()) {
            printDiff(diff);
        }
        
        System.out.println("---------- Changes -----------");
        System.out.println();
        for(Change change : changes) {
            printChange(change);
        }

        System.out.println();
        System.out.println();
    }
    
    void printDiff(Diff diff) {
        Match match = diff.getMatch();
        
        EObject left = match.getLeft();      // Left is the most recent, can be null
        EObject right = match.getRight();    // Right is previous, can be null
        
        System.out.println("diff:            " + diff);
        System.out.println("left:            " + left);
        System.out.println("right:           " + right);
        
        if(diff instanceof ReferenceChange refChange) {
            System.out.println("reference:       " + refChange.getValue());
        }
        if(diff instanceof AttributeChange attChange) {
            System.out.println("attribute left:  " + left.eGet(attChange.getAttribute()));
            System.out.println("attribute right: " + right.eGet(attChange.getAttribute()));
        }
        
        System.out.println();
    }
    
    void printChange(Change change) {
        System.out.println(getObjectName(change.getChangedObject()));
        
        for(Diff diff : change.getDiffs()) {
            System.out.println(" diff:  -  " + diff);
        }
        
        for(Change child : change.getChildren()) {
            printChange(child);
        }
        
        System.out.println();
    }

    String getObjectName(EObject eObject) {
        String name = ArchiLabelProvider.INSTANCE.getLabel(eObject);
        if(name.isEmpty()) {
            name = eObject.toString();
        }
        else {
            name += " - " + eObject.eClass().getName();
        }
        return name;
    }
}
