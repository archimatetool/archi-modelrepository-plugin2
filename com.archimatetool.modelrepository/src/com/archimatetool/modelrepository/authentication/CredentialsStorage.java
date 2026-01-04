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

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.GitUtils;
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
    
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String SSH_PASSWORD = "sshPassword";
    public static final String GPG_PASSWORD = "gpgPassword";
    
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
    
    /**
     * Store UsernamePassword for a repository
     */
    public void storeCredentials(IArchiRepository repo, UsernamePassword npw) throws StorageException, IOException {
        if(npw != null) {
            logger.info("Storing user credentials for: " + repo.getWorkingFolder());
            ISecurePreferences node = getRepositoryNode(repo);
            storeEntry(USERNAME, npw.getUsername(), node);
            storeEntry(PASSWORD, getSafeString(npw.getPassword()), node);
        }
    }
    
    public void clearCredentials(IArchiRepository repo) throws StorageException, IOException {
        logger.info("Clearing user credentials for: " + repo.getWorkingFolder());
        ISecurePreferences node = getRepositoryNode(repo);
        storeEntry(USERNAME, null, node);
        storeEntry(PASSWORD, null, node);
    }
    
    /**
     * Store username for a repository
     */
    public void storeUserName(IArchiRepository repo, String username) throws StorageException, IOException {
        logger.info("Storing user name for: " + repo.getWorkingFolder());
        storeEntry(USERNAME, username, getRepositoryNode(repo));
    }
    
    /**
     * Store password for a repository
     */
    public void storePassword(IArchiRepository repo, char[] password) throws StorageException, IOException {
        logger.info("Storing password for: " + repo.getWorkingFolder());
        storeEntry(PASSWORD, getSafeString(password), getRepositoryNode(repo));
    }
    
    /**
     * @return a secure entry (password/PAT) stored at /com.archimatetool.modelrepository/entryName
     */
    public char[] getSecureEntry(String entryName) throws StorageException {
        return getMainNode().get(entryName, "").toCharArray();
    }
    
    /**
     * Store a secure entry (password/PAT) at /com.archimatetool.modelrepository/entryName
     */
    public void storeSecureEntry(String entryName, char[] data) throws StorageException, IOException {
        logger.info("Storing secure entry: " + entryName);
        storeEntry(entryName, getSafeString(data), getMainNode());
    }
    
    /**
     * @return true if there is a string entry
     */
    public boolean hasEntry(String entryName) throws StorageException {
        return getMainNode().get(entryName, "").length() > 0;
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
        try(GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
            url = utils.getRemoteURL().orElse(null);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        // This is the child node for this repository
        return url != null ? getMainNode().node(URLEncoder.encode(url, StandardCharsets.UTF_8)) : null;
    }
    
    private String getSafeString(char[] chars) {
        return chars == null ? "" : new String(chars);
    }
}
