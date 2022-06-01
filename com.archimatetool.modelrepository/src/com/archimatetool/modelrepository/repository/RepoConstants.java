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
public final class RepoConstants {
    
    /**
     * Filename for model.archimate file in working folder
     */
    public static final String MODEL_FILENAME = "model.archimate";
    
    /**
     * Folder for storing images
     */
    public static final String IMAGES_FOLDER = "images";

    /**
     * Remote git name, assumed that the repo is called "origin"
     */
    public static final String ORIGIN = "origin";

    /**
     * "main" branch
     */
    public static final String MAIN = "main";

    /**
     * "master" branch
     */
    public static final String MASTER = "master";
    
    /**
     * Prefix for local branch Refs
     */
    public static final String LOCAL_PREFIX = Constants.R_HEADS;
    
    /**
     * Prefix for remote branch Refs
     */
    public static final String REMOTE_PREFIX = Constants.R_REMOTES + ORIGIN + "/";
}
