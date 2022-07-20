/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Credentials Storage for Repositories
 * 
 * NOTE: THIS IS A TEMPORARY IMPLEMENTATION FOR TESTING!!!!!!
 *       PASSWORDS ARE STORED IN CLEAR TEXT!!!!
 *       YOU HAVE BEEN WARNED!!
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CredentialsStorage {
    
    private static CredentialsStorage instance = new CredentialsStorage();
    
    public static CredentialsStorage getInstance() {
        return instance;
    }
    
    private File fStorageFile;
    private Properties fProperties;

    private CredentialsStorage() {
        // Change this to wherever you want to store unencrypted stuff
        
        // Set a VM argument with a file path
        // -DcoArchi.credentials=pathToFile
        String credentialsFile = System.getProperty("coArchi.credentials"); //$NON-NLS-1$
        if(credentialsFile != null) {
            fStorageFile = new File(credentialsFile);
        }
        else {
            fStorageFile = new File(System.getProperty("user.home"), "coArchiStore");
        }
    }

    public UsernamePassword getCredentials(IArchiRepository repo) throws IOException {
        String url = getRemoteURL(repo);
        if(url == null) {
            return null;
        }
        
        Properties properties = getProperties();
        
        String userName = properties.getProperty(url + ":username", null);
        String pw = properties.getProperty(url + ":pw", "");
        
        if(userName != null) {
            return new UsernamePassword(userName, pw.toCharArray());
        }
        
        return null;
    }
    
    public void storeCredentials(IArchiRepository repo, UsernamePassword npw) throws IOException {
        if(npw != null) {
            storeUserName(repo, npw.getUsername());
            storePassword(repo, npw.getPassword());
        }
    }
    
    public void storeUserName(IArchiRepository repo, String userName) throws IOException {
        String url = getRemoteURL(repo);
        if(url == null) {
            return;
        }
        
        String key = url + ":username";
        
        // If userName not set remove it
        if(!StringUtils.isSet(userName)) {
            getProperties().remove(key);
        }
        else {
            getProperties().setProperty(key, userName);
        }
        
        saveProperties();
    }
    
    public void storePassword(IArchiRepository repo, char[] password) throws IOException {
        String url = getRemoteURL(repo);
        if(url == null) {
            return;
        }
        
        String key = url + ":pw";

        // If password not set remove it
        if(password == null || password.length == 0) {
            getProperties().remove(key);
        }
        else {
            getProperties().setProperty(key, new String(password));
        }
        
        saveProperties();
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
    
    private Properties getProperties() throws IOException {
        if(fProperties == null) {
            fProperties = new Properties();
            
            if(fStorageFile.exists()) {
                try(FileInputStream is = new FileInputStream(fStorageFile)) {
                    fProperties.load(is);
                }
            }
        }
        
        return fProperties;
    }
    
    private void saveProperties() throws IOException {
        fStorageFile.getParentFile().mkdirs(); // Ensure parent folder exists

        try(FileOutputStream out = new FileOutputStream(fStorageFile)) {
            getProperties().store(out, null);
        }
    }
}
