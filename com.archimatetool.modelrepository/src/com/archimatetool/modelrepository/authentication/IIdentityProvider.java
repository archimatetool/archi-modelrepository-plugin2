/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;

import org.eclipse.equinox.security.storage.StorageException;

/**
 * Interface for a provider to supply identity file and password to access that file
 * This is to access things like SSH key files that have been secured with a password.
 * 
 * @author Phillip Beauvoir
 */
public interface IIdentityProvider {
    
    /**
     * @return The identity file
     */
    File getIdentityFile();
    
    /**
     * @return The password to access the identity file
     */
    char[] getIdentityPassword() throws StorageException;
}