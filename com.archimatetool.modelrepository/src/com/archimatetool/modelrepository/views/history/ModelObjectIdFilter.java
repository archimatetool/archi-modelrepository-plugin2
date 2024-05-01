/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

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
        
        byte[] fileContents = utils.getFileContents(RepoConstants.MODEL_FILENAME, commit, false);
        if(fileContents != null) {
            String contents = new String(fileContents, "UTF-8");
            if(contents.contains("id=\"" + modelObjectId + "\"")) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public RevFilter clone() {
        return this;
    }
}