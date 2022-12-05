/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
    
    private static CredentialsStorage instance = new CredentialsStorage();
    
    public static CredentialsStorage getInstance() {
        return instance;
    }
    
    private CredentialsStorage() {
    }

    public UsernamePassword getCredentials(IArchiRepository repo) throws StorageException {
        ISecurePreferences repoNode = getRepositoryNode(repo);
        if(repoNode == null) {
            return null;
        }
        
        return new UsernamePassword(repoNode.get("username", ""), repoNode.get("password", "").toCharArray());
    }
    
    public void storeCredentials(IArchiRepository repo, UsernamePassword npw) throws StorageException, IOException {
        if(npw != null) {
            storeUserName(repo, npw.getUsername());
            storePassword(repo, npw.getPassword());
        }
    }
    
    public void storeUserName(IArchiRepository repo, String userName) throws StorageException, IOException {
        ISecurePreferences repoNode = getRepositoryNode(repo);
        if(repoNode == null) {
            return;
        }
        
        // If set
        if(StringUtils.isSet(userName)) {
            repoNode.put("username", userName, true);
        }
        // Else remove it
        else {
            repoNode.remove("username");
        }
        
        repoNode.flush();
    }
    
    public void storePassword(IArchiRepository repo, char[] password) throws StorageException, IOException {
        ISecurePreferences repoNode = getRepositoryNode(repo);
        if(repoNode == null) {
            return;
        }
        
        // If set
        if(password != null && password.length > 0) {
            repoNode.put("password", new String(password), true);
        }
        // Else remove it
        else {
            repoNode.remove("username");
        }
        
        repoNode.flush();
    }
    
    public char[] getSSHIdentityFilePassword() throws StorageException {
        return getCoArchiNode().get("sshPassword", "").toCharArray();
    }
    
    public void setSSHIdentityFilePassword(char[] password) throws StorageException, IOException {
        ISecurePreferences node = getCoArchiNode();
        
        // If set
        if(password != null && password.length > 0) {
            node.put("sshPassword", new String(password), true);
        }
        // Else remove it
        else {
            node.remove("sshPassword");
        }
        
        node.flush();
    }
    
    private ISecurePreferences getRepositoryNode(IArchiRepository repo) {
        // Get repository URL
        String url = getRemoteURL(repo);
        if(url == null) {
            return null;
        }
        
        // This is the child node for this repository
        return getCoArchiNode().node(URLEncoder.encode(url, StandardCharsets.UTF_8));
    }
    
    private ISecurePreferences getCoArchiNode() {
        // Get Secure Prefs root node
        ISecurePreferences rootNode = SecurePreferencesFactory.getDefault();
        
        // This is the coArchi node for all secure entries. We could clear it with coArchiNode.removeNode()
        return rootNode.node(ModelRepositoryPlugin.PLUGIN_ID);
    }
    
    private String getRemoteURL(IArchiRepository repo) {
        try {
            return repo.getRemoteURL();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
