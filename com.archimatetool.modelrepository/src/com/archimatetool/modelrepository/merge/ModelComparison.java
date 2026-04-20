/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.merge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;

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
    
    /**
     * Represents a model object change of interest.
     */
    public static class Change {
        private EObject changedObject;
        private Set<Diff> diffs = new HashSet<>();
        private Map<EObject, Change> children = new HashMap<>();
        private List<Object> changes;
        
        /** @param changedObject root object this change node represents (often a diagram or folder) */
        public Change(EObject changedObject) {
            this.changedObject = changedObject;
        }
        
        /** @return root {@link EObject} for this change tree node */
        public EObject getChangedObject() {
            return changedObject;
        }
        
        /** @return direct {@link Diff}s attached to this node */
        public Set<Diff> getDiffs() {
            return diffs;
        }
        
        /** @return nested change nodes (e.g. diagram components under a view) */
        public Collection<Change> getChildren() {
            return children.values();
        }
        
        /** @return lazily built flat list of diffs plus child {@link Change} objects for UI trees */
        public List<Object> getChanges() {
            if(changes == null) {
                changes = new ArrayList<>();
                changes.addAll(getDiffs());
                changes.addAll(getChildren());
            }
            return changes;
        }
        
        /** @return existing or new child node keyed by {@code eObject} */
        private Change addChild(EObject eObject) {
            return children.computeIfAbsent(eObject, object -> new Change(object));
        }
        
        /** Registers a diff belonging to this change node. */
        private void addDiff(Diff diff) {
            diffs.add(diff);
        }
    }

    
    private IArchiRepository repository;
    private RevCommit revCommit1, revCommit2;
    private IArchimateModel model1, model2;
    private Comparison comparison;

    
    /**
     * Compare {@code revCommit} to the current working-tree model ({@link #getSecondRevCommit()} stays {@code null}).
     *
     * @param repository repo whose working folder is read
     * @param revCommit  baseline commit
     */
    public ModelComparison(IArchiRepository repository, RevCommit revCommit) {
        this.repository = repository;
        this.revCommit1 = revCommit;
    }

    /**
     * Compare two commits; stores them in chronological order (older = {@link #getFirstRevCommit()}).
     *
     * @param repository repo whose Git data is used
     * @param revCommit1 one side
     * @param revCommit2 other side
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
     * Loads {@code model1}/{@code model2} and builds {@link #getComparison()} (idempotent).
     *
     * @return {@code this}
     * @throws IOException if a model file is missing or compare setup fails
     */
    public ModelComparison init() throws IOException {
        if(comparison != null) {
            return this;
        }
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            // Load the model from first commit
            model1 = ModelLoader.loadModel(utils, revCommit1.getName());
            
            if(model1 == null) {
                throw new IOException("Model was null for " + revCommit1.getName());
            }

            // Load the model from the second commit or the working tree
            model2 = isWorkingTreeComparison() ? ModelLoader.loadWorkingTreeModel(repository) : ModelLoader.loadModel(utils, revCommit2.getName());
            
            if(model2 == null) {
                throw new IOException("Model was null for " + (isWorkingTreeComparison() ? "working tree" : revCommit1.getName()));
            }
            
            // Create Comparison
            comparison = MergeFactory.createComparison(model2, model1, null);  // Left/Right are swapped!
            
            // Log any diagnostic errors and warnings
            for(Diagnostic diagnostic : comparison.getDiagnostic().getChildren()) {
                if(diagnostic.getSeverity() == Diagnostic.WARNING || diagnostic.getSeverity() == Diagnostic.ERROR) {
                    System.out.println("[ModelComparison] " + diagnostic.getMessage());
                }
            }
        }
        
        return this;
    }
    
    /** @return EMF Compare result after {@link #init()}, or {@code null} before init */
    public Comparison getComparison() {
        return comparison;
    }
    
    /**
     * Groups interesting diffs into a tree of {@link Change} nodes for UI presentation.
     *
     * @return top-level change roots (e.g. per diagram)
     */
    public List<Change> getChangedObjects() {
        Map<EObject, Change> changes = new HashMap<>();

        for(Diff diff : comparison.getDifferences()) {
            Match match = diff.getMatch();
            EObject changedObject = match.getLeft();
            
            if(changedObject != null) {
                EObject rootObject = getRootParent(changedObject);
                
                if(diff instanceof ReferenceChange referenceChange) {
                    if(changedObject instanceof IFolder && !(referenceChange.getValue() instanceof IProperty)
                                                        && !(referenceChange.getValue() instanceof IFeature)) {
                        rootObject = referenceChange.getValue();
                    }
                    if(changedObject instanceof IDiagramModelContainer && referenceChange.getValue() instanceof IDiagramModelObject dmo) {
                        changedObject = dmo;
                    }
                }
                
                Change change = changes.computeIfAbsent(rootObject, object -> new Change(object));
                
                if(rootObject instanceof IDiagramModel) {
                    EObject eObject = getParent(changedObject);
                    if(eObject instanceof IDiagramModelComponent && eObject != rootObject) {
                        change = change.addChild(eObject);
                    }
                }

                if(isInteresting(diff)) {
                    change.addDiff(diff);
                }
            }
        }
        
        return new ArrayList<>(changes.values());
    }
    
    /**
     * Filters out noise such as automatic {@link IBounds} additions.
     *
     * @param diff candidate diff on the LEFT side
     */
    public boolean isInteresting(Diff diff) {
        if(diff instanceof ReferenceChange referenceChange && referenceChange.getValue() instanceof IBounds && referenceChange.getKind() == DifferenceKind.ADD) {
            return false;
        }
        return true;
    }
    
    /**
     * @param eObject object in the “first” (older) model tree
     * @return all diffs on that object’s match, or an empty list
     */
    public List<Diff> getDifferences(EObject eObject) {
        Match match = comparison.getMatch(eObject);
        return match != null ? match.getDifferences() : new ArrayList<>();
    }

    /** Walks past non-{@link IArchimateModelObject} wrappers unless {@link IProfile}. */
    private static EObject getParent(EObject eObject) {
        if(eObject != null && (!(eObject instanceof IArchimateModelObject) || eObject instanceof IProfile)) {
            eObject = eObject.eContainer();
        }
        return eObject;
    }
    
    /** For diagram components, returns the owning {@link IDiagramModel}. */
    private static EObject getRootParent(EObject eObject) {
        eObject = getParent(eObject);
        if(eObject instanceof IDiagramModelComponent dmc) {
            eObject = dmc.getDiagramModel();
        }
        return eObject;
    }
    
    /** @param id Archi element id */
    public EObject findObjectInFirstModel(String id) {
        return ArchimateModelUtils.getObjectByID(model1, id);
    }
    
    /** @param id Archi element id */
    public EObject findObjectInSecondModel(String id) {
        return ArchimateModelUtils.getObjectByID(model2, id);
    }

    /** Label plus EMF class name for debug / compare UI. */
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
