/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.YesNoType;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * GPG signing CredentialsProvider
 * 
 * @author Phillip Beauvoir
 */
public class GPGCredentialsProvider extends CredentialsProvider {
    
    private static GPGCredentialsProvider defaultProvider = new GPGCredentialsProvider();
    
    public static GPGCredentialsProvider getDefault() {
        return defaultProvider;
    }
    
    public static void setDefault(GPGCredentialsProvider provider) {
        defaultProvider = provider;
    }

    public GPGCredentialsProvider() {
        // Set the default signer to BouncyCastleGpgSigner in JGit 6.10
        //if(GpgSigner.getDefault() == null) {
        //    GpgSigner.setDefault(BouncyCastleGpgSignerFactory.create());
        //}
        
        // For JGit 7.0.0 and later the above doesn't work, so do this...
        // Ensure Bouncy Castle is registered, otherwise we will not have
        // AES/OCB support needed for some passphrase-protected encrypted GPG keys.
        // See https://github.com/eclipse-jgit/jgit/issues/173
        if(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public String getSigningKey() {
        // TODO: get from preferences/config
        return null;
    }
    
    protected char[] getSecretKey() throws StorageException {
        char[] pw = CredentialsStorage.getInstance().getSecureEntry(CredentialsStorage.GPG_PASSWORD);
        return (pw != null && pw.length != 0) ? pw : new char[0]; // can't be null
    }
    
    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        for(CredentialItem item : items) {
            if(item instanceof YesNoType yesNoType) {
                yesNoType.setValue(true);
            }
            // Password for GPG private key file
            else if(item instanceof Password password) {
                try {
                    password.setValue(getSecretKey());
                }
                catch(StorageException ex) {
                    ex.printStackTrace();
                    throw new UnsupportedCredentialItem(uri, "Could not get gpg password."); //$NON-NLS-1$
                }
            }
        }

        return true;
    }
}
