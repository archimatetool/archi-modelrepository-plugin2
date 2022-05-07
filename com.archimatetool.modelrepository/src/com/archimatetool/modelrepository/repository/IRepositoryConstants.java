/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;


/**
 * Repository Constants
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public interface IRepositoryConstants {
    
    /**
     * Filename for model.archimate file in working folder
     */
    String MODEL_FILENAME = "model.archimate";
    
    /**
     * Folder for storing images
     */
    String IMAGES_FOLDER = "images";

    /**
     * Remote git name, assumed that the repo is called "origin"
     */
    String ORIGIN = "origin";

    /**
     * Main branch
     */
    String MAIN = "main";
}
