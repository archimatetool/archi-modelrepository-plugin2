/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator.ISSHIdentityProvider;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Abstract Command Line Provider
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public abstract class AbstractModelRepositoryProvider extends AbstractCommandLineProvider {
    
    protected UsernamePassword setCredentials(CommandLine commandLine, String url) throws IOException {
        String username = commandLine.getOptionValue(CoreModelRepositoryProvider.OPTION_USERNAME);
        char[] password = getPasswordFromFile(commandLine);
        File identityFile = getSSHIdentityFile(commandLine);
        
        if(!checkCredentials(url, username, password, identityFile)) {
            return null;
        }
        
        // Get UsernamePassword if HTTP, otherwise set SSH credentials
        UsernamePassword npw = null;
        if(RepoUtils.isHTTP(url)) {
            npw = new UsernamePassword(username, password);
        }
        // SSH
        else {
            setCredentialsAuthenticator(identityFile, password);
        }
        
        return npw;
    }
    
    protected File getFolderOption(CommandLine commandLine) {
        String optionFolder = commandLine.getOptionValue(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
        
        if(!StringUtils.isSet(optionFolder)) {
            logError(NLS.bind("No target folder set. Use the --{0} <path> option.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER));
            return null;
        }
        
        return new File(optionFolder);
    }

    private boolean checkCredentials(String url, String username, char[] password, File identityFile) {
        boolean isSSH = RepoUtils.isSSH(url);
        boolean isHTTP = !isSSH;
        
        // HTTP requires user name
        if(isHTTP && !StringUtils.isSet(username)) {
            logError("No user name set.");
            return false;
        }
        
        // If using HTTP then password is needed for connection
        // If using SSH then password is optional for the identity file
        if(isHTTP && (password == null || password.length == 0)) {
            logError("No password found in password file.");
            return false;
        }
        
        // SSH needs identity file
        if(isSSH && identityFile == null) {
            logError("URL is SSH protocol but no identity file found.");
            return false;
        }
        
        return true;
    }

    // Set this to return provided details rather than using those from Archi preferences
    private void setCredentialsAuthenticator(File identityFile, char[] identityPassword) {
        CredentialsAuthenticator.setSSHIdentityProvider(new ISSHIdentityProvider() {
            @Override
            public File getIdentityFile() {
                return identityFile;
            }

            @Override
            public char[] getIdentityPassword() {
                return identityPassword;
            }
        });
    }

    private char[] getPasswordFromFile(CommandLine commandLine) throws IOException {
        String path = commandLine.getOptionValue(CoreModelRepositoryProvider.OPTION_PASSFILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                // Read in as string and trim because some Linux text apps can append a new line char
                String s = Files.readString(file.toPath()).trim();
                return s.toCharArray();
            }
        }
        return null;
    }

    private File getSSHIdentityFile(CommandLine commandLine) {
        String path = commandLine.getOptionValue(CoreModelRepositoryProvider.OPTION_SSH_IDENTITY_FILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                return file;
            }
        }
        
        return null;
    }
 }
