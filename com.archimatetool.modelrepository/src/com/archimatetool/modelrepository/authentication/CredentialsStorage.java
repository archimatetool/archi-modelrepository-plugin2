/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Credentials Storage for Repositories using Eclipse secure storage
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CredentialsStorage {
    
    private static Logger logger = Logger.getLogger(CredentialsStorage.class.getName());
    
    private static CredentialsStorage instance = new CredentialsStorage();
    
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String SSH_PASSWORD = "sshPassword";
    
    public static CredentialsStorage getInstance() {
        return instance;
    }
    
    private CredentialsStorage() {
    }

    /**
     * Return UsernamePassword. UsernamePassword and its username and password will never be null
     */
    public UsernamePassword getCredentials(IArchiRepository repo) throws StorageException {
        ISecurePreferences repoNode = getRepositoryNode(repo);
        return repoNode != null ? new UsernamePassword(repoNode.get(USERNAME, ""), repoNode.get(PASSWORD, "").toCharArray())
                                : new UsernamePassword("", new char[0]); // repo node was null
    }
    
    public void storeCredentials(IArchiRepository repo, UsernamePassword npw) throws StorageException, IOException {
        if(npw != null) {
            logger.info("Storing user credentials for: " + repo.getWorkingFolder());
            ISecurePreferences node = getRepositoryNode(repo);
            storeEntry(USERNAME, npw.getUsername(), node);
            storeEntry(PASSWORD, getSafeString(npw.getPassword()), node);
        }
    }
    
    public void storeUserName(IArchiRepository repo, String userName) throws StorageException, IOException {
        logger.info("Storing user name for: " + repo.getWorkingFolder());
        storeEntry(USERNAME, userName, getRepositoryNode(repo));
    }
    
    public void storePassword(IArchiRepository repo, char[] password) throws StorageException, IOException {
        logger.info("Storing password for: " + repo.getWorkingFolder());
        storeEntry(PASSWORD, getSafeString(password), getRepositoryNode(repo));
    }
    
    public char[] getSSHIdentityFilePassword() throws StorageException {
        return getMainNode().get(SSH_PASSWORD, "").toCharArray();
    }
    
    public void storeSSHIdentityFilePassword(char[] password) throws StorageException, IOException {
        logger.info("Storing SSH Identifier password");
        storeEntry(SSH_PASSWORD, getSafeString(password), getMainNode());
    }
    
    private void storeEntry(String entryName, String value, ISecurePreferences node) throws StorageException, IOException {
        if(node == null) {
            return;
        }
        
        // If set
        if(StringUtils.isSet(value)) {
            node.put(entryName, value, true);
        }
        // Else remove it
        else {
            node.remove(entryName);
        }
        
        node.flush();
    }
    
    // This is the node for all our secure entries. We could clear it with getMainNode().removeNode()
    private ISecurePreferences getMainNode() {
        return SecurePreferencesFactory.getDefault().node(ModelRepositoryPlugin.PLUGIN_ID);
    }

    private ISecurePreferences getRepositoryNode(IArchiRepository repo) {
        // Get the repository URL to be used as key for the node
        String url = null;
        try {
            url = repo.getRemoteURL();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
        }
        
        // This is the child node for this repository
        return url != null ? getMainNode().node(URLEncoder.encode(url, StandardCharsets.UTF_8)) : null;
    }
    
    private String getSafeString(char[] chars) {
        return chars == null ? "" : new String(chars);
    }
}
