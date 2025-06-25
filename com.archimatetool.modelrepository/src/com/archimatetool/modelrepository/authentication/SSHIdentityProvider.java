/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.security.storage.StorageException;

import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;


/**
 * Default SSH Identity Provider
 * 
 * @author Phillip Beauvoir
 */
public class SSHIdentityProvider implements IIdentityProvider {
    
    /**
     * Default instance is with details from Prefs
     */
    private static IIdentityProvider instance = new SSHIdentityProvider();
    
    public static void setInstance(IIdentityProvider provider) {
        instance = provider;
    }
    
    public static IIdentityProvider getInstance() {
        return instance;
    }
    
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
}
