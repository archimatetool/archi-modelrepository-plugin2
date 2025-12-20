/**
 * This program and the accompanying materials are made available under the
 * terms of the License which accompanies this distribution in the file
 * LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;

/**
 * Our extended SshSessionFactory
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CustomSshSessionFactory extends SshdSessionFactory {

    /**
        Ideally we'd like to do something like:
           Config.setProperty("StrictHostKeyChecking", "no")
           
        You can add the following to a ~/.ssh/config file:
           Host *
           StrictHostKeyChecking no
     */
    private boolean verifyServerKeys = false;

    /**
     * Whether to scan the ~/.ssh directory for all known keys with names "id_rsa, id_dsa, id_ecdsa, id_ed25519"
     */
    private boolean useDefaultIdentities = false;
    
    public CustomSshSessionFactory() {
        // Set ProxyDataFactory to null to allow SSH connections through the proxy if it's enabled
        super(null, null);
    }
    
    /**
     * By default the ~/.ssh directory is scanned for all supported private key files
     * But we can return the identity file as set in Preferences or set of files
     */
    @Override
    protected List<Path> getDefaultIdentities(File sshDir) {
        if(useDefaultIdentities) {
            return super.getDefaultIdentities(sshDir);
        }
        
        // If preference set and not using ACLI scan the SSH directory for all non-public files
        if(PlatformUI.isWorkbenchRunning()
                && Platform.getPreferencesService() != null  // Check Preference Service is running in case background fetch is running and we quit the app
                && ModelRepositoryPlugin.getInstance().getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_SSH_SCAN_DIR)) {
            
            File[] files = sshDir.listFiles();
            if(files != null) {
                List<Path> paths = Arrays.asList(files).stream()
                        .filter(file -> !file.getName().endsWith(".pub") && !file.getName().startsWith("known_hosts"))
                        .map(File::toPath)
                        .collect(Collectors.toList());
            
                if(!paths.isEmpty()) {
                    return paths;
                }
            }
        }
        
        // Single default identity file
        File identityFile = SSHCredentialsProvider.getDefault().getIdentityFile();
        if(identityFile != null && identityFile.exists() && identityFile.isFile()) {
            return List.of(identityFile.toPath());
        }
        
        return Collections.emptyList();
    }

    /**
     * We can over-ride this to not verify server keys and not write to the known_hosts file
     */
    @Override
    protected ServerKeyDatabase getServerKeyDatabase(File homeDir, File sshDir) {
        if(verifyServerKeys) {
            return super.getServerKeyDatabase(homeDir, sshDir);
        }

        return new ServerKeyDatabase() {
            @Override
            public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
                return new ArrayList<>();
            }

            @Override
            public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey, Configuration config,
                    CredentialsProvider provider) {
                return true;
            }
        };
    }
}
