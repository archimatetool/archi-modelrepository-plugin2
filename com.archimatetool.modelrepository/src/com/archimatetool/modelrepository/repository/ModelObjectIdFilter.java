/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

/**
 * RevFilter that filters on a model object's Id
 * This simply returns true if the manifest in the commit message contains the change objectId
 * 
 * @author Phillip Beauvoir
 */
public class ModelObjectIdFilter extends RevFilter {
    
    private String objectId;
    
    /**
     * @param objectId The id of the model object to filter on
     */
    public ModelObjectIdFilter(String objectId) {
        this.objectId = objectId;
    }

    @Override
    public boolean include(RevWalk revWalk, RevCommit commit) throws StopWalkException, MissingObjectException, IncorrectObjectTypeException, IOException {
        return CommitManifest.containsChange(commit.getFullMessage(), objectId);
    }

    @Override
    public RevFilter clone() {
        return this;
    }
}