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
     * Filename for temp.archimate file
     */
    String TEMP_MODEL_FILENAME = "temp.archimate";
    
    /**
     * Filename for model.archimate file
     */
    String MODEL_FILENAME = "model.archimate";

    /**
     * Name of folder for images
     */
    String IMAGES_FOLDER = "images";
    
    /**
     * File name of user name/password for each git repo
     */
    String REPO_CREDENTIALS_FILE = "credentials";
    
    /**
     * File name of SSH identity password for each git repo
     */
    String SSH_CREDENTIALS_FILE = "ssh_credentials";
    
    /**
     * File name of user name/password for Proxy Server
     */
    String PROXY_CREDENTIALS_FILE = "proxy_credentials";
    
    /**
     * Remote git name, assumed that the repo is called "origin"
     */
    String ORIGIN = "origin";

    /**
     * Master branch
     */
    String MASTER = "master";

    /**
     * Head
     */
    String HEAD = "HEAD";
}
