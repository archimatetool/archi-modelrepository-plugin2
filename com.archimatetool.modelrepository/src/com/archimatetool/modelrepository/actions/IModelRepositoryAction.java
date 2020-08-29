/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jface.action.IAction;

import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryConstants;

/**
 * Interface for Actions
 * 
 * @author Phillip Beauvoir
 */
public interface IModelRepositoryAction extends IAction, IRepositoryConstants {

    /**
     * Set the repository
     * @param repository
     */
    void setRepository(IArchiRepository repository);
    
    /**
     * @return The repository
     */
    IArchiRepository getRepository();
    
    /**
     * Update enabled state
     */
    void update();

    /**
     * Dispose of action
     */
    void dispose();
}