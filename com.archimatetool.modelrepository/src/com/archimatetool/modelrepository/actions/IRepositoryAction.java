/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jface.action.IAction;

import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Interface for Actions
 * 
 * @author Phillip Beauvoir
 */
public interface IRepositoryAction extends IAction {

    /**
     * Set the repository
     * @param repository
     */
    void setRepository(IArchiRepository repository);
    
    /**
     * Update enabled state
     */
    void update();
}