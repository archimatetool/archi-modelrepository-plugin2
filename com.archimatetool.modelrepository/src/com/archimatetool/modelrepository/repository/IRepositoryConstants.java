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
public interface IRepositoryConstants {
    
    /**
     * Filename for temp.archimate file
     */
    String TEMP_MODEL_FILENAME = "temp.archimate"; //$NON-NLS-1$

    /**
     * Name of folder for images
     */
    String IMAGES_FOLDER = "images"; //$NON-NLS-1$
    
    /**
     * File name of user name/password for each git repo
     */
    String REPO_CREDENTIALS_FILE = "credentials"; //$NON-NLS-1$
    
    /**
     * File name of SSH identity password for each git repo
     */
    String SSH_CREDENTIALS_FILE = "ssh_credentials"; //$NON-NLS-1$
    
    /**
     * File name of user name/password for Proxy Server
     */
    String PROXY_CREDENTIALS_FILE = "proxy_credentials"; //$NON-NLS-1$
    
    /**
     * Remote git name, assumed that the repo is called "origin"
     */
    String ORIGIN = "origin"; //$NON-NLS-1$

    /**
     * Master branch
     */
    String MASTER = "master"; //$NON-NLS-1$

    /**
     * Head
     */
    String HEAD = "HEAD"; //$NON-NLS-1$
    
    /**
     * Config section where we store our stuff
     */
    String CONFIG_ARCHI_SECTION = "archi"; //$NON-NLS-1$
    
    /**
     * Config name of model
     */
    String CONFIG_KEY_NAME = "name"; //$NON-NLS-1$
}
