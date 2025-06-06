/**
 * This program and the accompanying materials are made available under the
 * terms of the License which accompanies this distribution in the file
 * LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.YesNoType;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Our extended CredentialsProvider
 * 
 * @author Phillip Beauvoir
 */
public class SSHCredentialsProvider extends CredentialsProvider {

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
                    password.setValue(CredentialsAuthenticator.getSSHIdentityProvider().getIdentityPassword());
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
