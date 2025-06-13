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
@SuppressWarnings("nls")
public interface IRepositoryListener {
    
    String MODEL_SAVED = "model_saved";
    String MODEL_RENAMED = "model_renamed";
    String REPOSITORY_DELETED = "repository_deleted";
    String HISTORY_CHANGED = "history_changed";
    String BRANCHES_CHANGED = "branches_changed";
    String TAGS_CHANGED = "tags_changed";
    
    void repositoryChanged(String eventName, IArchiRepository repository);
    
}
