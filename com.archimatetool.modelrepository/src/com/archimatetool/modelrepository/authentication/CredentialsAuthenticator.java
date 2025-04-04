/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;


/**
 * Authenticator for SSH and HTTP
 * 
 * @author Phillip Beauvoir
 */
public final class CredentialsAuthenticator {
    
    public interface ISSHIdentityProvider {
        File getIdentityFile();
        
        char[] getIdentityPassword() throws StorageException;
    }
    
    /**
     * Set the SshSessionFactory instance to our specialised SshSessionFactory 
     */
    static {
        SshSessionFactory.setInstance(new CustomSshSessionFactory());
    }
    
    /**
     * SSH Identity Provider. Default is with details from Prefs
     */
    private static ISSHIdentityProvider sshIdentityProvider = new ISSHIdentityProvider() {
        @Override
        public File getIdentityFile() {
            if(Platform.getPreferencesService() != null) { // Check Preference Service is running in case background fetch is running and we quit the app
                return new File(ModelRepositoryPlugin.getInstance().getPreferenceStore().getString(IPreferenceConstants.PREFS_SSH_IDENTITY_FILE)); 
            }
            
            return null;
        }
        
        @Override
        public char[] getIdentityPassword() throws StorageException {
            char[] password = null;
            
            if(Platform.getPreferencesService() != null // Check Preference Service is running in case background fetch is running and we quit the app
                    && ModelRepositoryPlugin.getInstance().getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_SSH_IDENTITY_REQUIRES_PASSWORD)) {
                return CredentialsStorage.getInstance().getSSHIdentityFilePassword();
            }
            
            return password;
        }
    };

    public static void setSSHIdentityProvider(ISSHIdentityProvider sshIdentityProvider) {
        CredentialsAuthenticator.sshIdentityProvider = sshIdentityProvider;
    }
    
    public static ISSHIdentityProvider getSSHIdentityProvider() {
        return sshIdentityProvider;
    }
    
    /**
     * Factory method to get the TransportConfigCallback for authentication for repoURL
     * npw can be null and is ignored if repoURL is SSH
     */
    public static TransportConfigCallback getTransportConfigCallback(UsernamePassword npw) {
        return new TransportConfigCallback() {
            @Override
            public void configure(Transport transport) {
                // Delete remote branches that we don't have
                transport.setRemoveDeletedRefs(true);
                
                // SSH
                if(npw == null) {
                    transport.setCredentialsProvider(new SSHCredentialsProvider());
                }
                // HTTPS
                else {
                    transport.setCredentialsProvider(new UsernamePasswordCredentialsProvider(npw.getUsername(), npw.getPassword()));
                }
            }
        };
    }
}
