/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;


/**
 * Interface for a repository listener
 * 
 * @author Phillip Beauvoir
 */
public interface IRepositoryListener {
    
    String REPOSITORY_CHANGED = "repository_changed"; //$NON-NLS-1$
    String HISTORY_CHANGED = "history_changed"; //$NON-NLS-1$
    String BRANCHES_CHANGED = "branches_changed"; //$NON-NLS-1$
    
    void repositoryChanged(String eventName, IArchiRepository repository);
    
}
