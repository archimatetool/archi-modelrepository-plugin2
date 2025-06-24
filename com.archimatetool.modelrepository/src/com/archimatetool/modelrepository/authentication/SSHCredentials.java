/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * SSH Credentials
 * 
 * @author Phillip Beauvoir
 */
public class SSHCredentials implements ICredentials {
    
    @Override
    public CredentialsProvider getCredentialsProvider() {
        return new SSHCredentialsProvider();
    }
}