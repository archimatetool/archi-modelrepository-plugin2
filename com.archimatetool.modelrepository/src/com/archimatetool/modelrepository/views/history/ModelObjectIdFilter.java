/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * RevFilter that filters on a model object's Id
 * This simply returns true if the model in the given commit contains an object with the given Id
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class ModelObjectIdFilter extends RevFilter {
    
    private GitUtils utils;
    private String modelObjectId;
    
    /**
     * @param repository The git repo
     * @param modelObjectId The id of the model object to filter on
     */
    public ModelObjectIdFilter(Repository repository, String modelObjectId) {
        utils = GitUtils.wrap(repository);
        this.modelObjectId = modelObjectId;
    }

    @Override
    public boolean include(RevWalk revWalk, RevCommit commit)
            throws StopWalkException, MissingObjectException, IncorrectObjectTypeException, IOException {
        
        RevCommit nextCommit = null;
        
        // Load the model from this commit
        IArchimateModel model = loadModel(utils, commit);
        
        // Find the object in the model
        EObject eObject = ArchimateModelUtils.getObjectByID(model, modelObjectId);
        if(eObject != null) {
            // Get the next commit
            try(RevWalk walk = new RevWalk(utils.getRepository())) {
                walk.markStart(walk.parseCommit(commit.getId()));
                nextCommit = walk.next();
                nextCommit = walk.next();
            }
            
            // If there is a next commit
            if(nextCommit != null) {
                // Load the model from the next commit
                IArchimateModel nextModel = loadModel(utils, nextCommit);
                
                // Find the object in the next model
                EObject eObject2 = ArchimateModelUtils.getObjectByID(nextModel, modelObjectId);
                if(eObject2 != null) {
                    // Compare and get diffs
                    IComparisonScope scope = new DefaultComparisonScope(eObject2, eObject, null);
                    Comparison comparison = EMFCompare.builder().build().compare(scope);
                    EList<Diff> diffs = comparison.getDifferences();
                    return !diffs.isEmpty();
                }
            }
            
            // no next commit or object not found in next model, so this commit must be where the object was first added
            return true;
        }
        
        return false;
    }
    
    private IArchimateModel loadModel(GitUtils utils, RevCommit commit) throws IOException {
        File tempFolder = Files.createTempDirectory("archi-").toFile();
        
        try {
            utils.extractCommit(commit, tempFolder, false);
            
            // Load it
            File modelFile = new File(tempFolder, RepoConstants.MODEL_FILENAME);
            return modelFile.exists() ? IEditorModelManager.INSTANCE.load(modelFile) : null;
        }
        finally {
            FileUtils.deleteFolder(tempFolder);
        }
    }

    @Override
    public RevFilter clone() {
        return this;
    }
}