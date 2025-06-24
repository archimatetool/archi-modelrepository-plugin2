/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * Credentials type
 * 
 * @author Phillip Beauvoir
 */
public interface ICredentials {
    
    /**
     * @return The CredentialsProvider for this Credentials
     */
    CredentialsProvider getCredentialsProvider();
}
