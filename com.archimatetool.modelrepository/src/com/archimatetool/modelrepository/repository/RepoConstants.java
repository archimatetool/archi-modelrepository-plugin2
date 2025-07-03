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
     * HEAD
     */
    public static final String HEAD = "HEAD";

    /**
     * Prefix for local refs
     */
    public static final String R_HEADS = "refs/heads/";
    
    /**
     * Prefix for remote refs
     */
    public static final String R_REMOTES = "refs/remotes/";

    /**
     * Prefix for tag refs
     */
    public static final String R_TAGS = "refs/tags/";

    /**
     * Ref prefix for origin remote
     */
    public static final String R_REMOTES_ORIGIN = "refs/remotes/origin/";
    
    /**
     * Full ref of "refs/heads/main"
     */
    public static final String R_HEADS_MAIN = R_HEADS + MAIN;

    /**
     * Full ref of "refs/heads/master"
     */
    public static final String R_HEADS_MASTER = R_HEADS + MASTER;
    
    /**
     * Ref prefix for origin remote main
     */
    public static final String R_REMOTES_ORIGIN_MAIN = "refs/remotes/origin/main";
    
    /**
     * origin remote main
     */
    public static final String ORIGIN_MAIN = "origin/main";

    /**
     * Ref prefix for origin remote master
     */
    public static final String R_REMOTES_ORIGIN_MASTER = "refs/remotes/origin/master";
    
    /**
     * RefSpec for Fetch all forced branches
     */
    public static final String REFSPEC_FETCH_ALL_BRANCHES = "+refs/heads/*:refs/remotes/origin/*";
    
    /**
     * RefSpec for Fetch all forced tags
     */
    public static final String REFSPEC_FETCH_ALL_TAGS = "+refs/tags/*:refs/tags/*";

}
