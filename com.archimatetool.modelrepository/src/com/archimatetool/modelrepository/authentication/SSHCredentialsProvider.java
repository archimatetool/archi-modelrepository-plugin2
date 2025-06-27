/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;
import java.util.Objects;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.YesNoType;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;

import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;

/**
 * CredentialsProvider for SSH
 * 
 * @author Phillip Beauvoir
 */
public class SSHCredentialsProvider extends CredentialsProvider {
    
    public SSHCredentialsProvider() {
        // Set the SshSessionFactory instance to our CustomSshSessionFactory
        // And reset it if the identity file has changed
        if(!(SshSessionFactory.getInstance() instanceof CustomSshSessionFactory factory) || !Objects.equals(factory.getIdentityFile(), getIdentityFile())) {
            SshSessionFactory.setInstance(new CustomSshSessionFactory(getIdentityFile()));
        }
    }
    
    /**
     * @return The identity file
     */
    protected File getIdentityFile() {
        if(Platform.getPreferencesService() != null) { // Check Preference Service is running in case background fetch is running and we quit the app
            return new File(ModelRepositoryPlugin.getInstance().getPreferenceStore().getString(IPreferenceConstants.PREFS_SSH_IDENTITY_FILE)); 
        }

        return null;
    }
    
    /**
     * @return The password to access the identity file
     */
    protected char[] getIdentityPassword() throws StorageException {
        char[] pw = CredentialsStorage.getInstance().getSecureEntry(CredentialsStorage.SSH_PASSWORD);
        return (pw != null && pw.length != 0) ? pw : null;
    }
    
    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        // This is never called
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        for(CredentialItem item : items) {
            // For verifying and storing the key in the known_hosts file
            if(item instanceof YesNoType yesNoType) {
                yesNoType.setValue(true);
            }
            // Password for ssh file
            else if(item instanceof Password password) {
                try {
                    password.setValue(getIdentityPassword());
                }
                catch(StorageException ex) {
                    ex.printStackTrace();
                    throw new UnsupportedCredentialItem(uri, "Could not get identity password."); //$NON-NLS-1$
                }
            }
        }

        return true;
    }
}
