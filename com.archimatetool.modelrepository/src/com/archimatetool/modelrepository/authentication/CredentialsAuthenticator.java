/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.archimatetool.modelrepository.repository.RepoUtils;


/**
 * Authenticator for SSH and HTTP
 * 
 * @author Phillip Beauvoir
 */
public final class CredentialsAuthenticator {
    
    /**
     * Factory method to get the TransportConfigCallback for authentication for repoURL
     * npw can be null and is ignored if repoURL is SSH
     */
    public static TransportConfigCallback getTransportConfigCallback(String repoURL, UsernamePassword npw) {
        return new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                transport.setRemoveDeletedRefs(true); // Delete remote branches that we don't have
                
                // SSH
                if(RepoUtils.isSSH(repoURL)) {
                    // TODO: Set SSHCredentialsProvider
                    //transport.setCredentialsProvider(new SSHCredentialsProvider());
                }
                // HTTP
                else if(npw != null) {
                    transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(npw.getUsername(), npw.getPassword()));
                }
            }
        };
    }
}
