/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import org.eclipse.jgit.lib.Constants;

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
     * "main" branch
     */
    String MAIN = "main";

    /**
     * "master" branch
     */
    String MASTER = "master";
    
    /**
     * Prefix for local branch Refs
     */
    String LOCAL_PREFIX = Constants.R_HEADS;
    
    /**
     * Prefix for remote branch Refs
     */
    String REMOTE_PREFIX = Constants.R_REMOTES + IRepositoryConstants.ORIGIN + "/";
}
