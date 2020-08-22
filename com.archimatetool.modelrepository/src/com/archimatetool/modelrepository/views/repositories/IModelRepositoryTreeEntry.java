/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import org.eclipse.swt.graphics.Image;

/**
 * Tree Entry
 * 
 * @author Phillip Beauvoir
 */
public interface IModelRepositoryTreeEntry {
    
    /**
     * Delete this entry
     */
    void delete();
    
    /**
     * @return The Parent Group of this entry
     */
    Group getParent();

    /**
     * @return The name
     */
    String getName();
    
    /**
     * @return The image
     */
    Image getImage();
}