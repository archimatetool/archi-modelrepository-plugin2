/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.authentication;

import java.util.Arrays;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * Username and Password pair
 * 
 * @author Phillip Beauvoir
 */
public class UsernamePassword implements ICredentials {

    private String username;
    private char[] password;
    
    public UsernamePassword(String username, char[] password) {
        this.username = username;
        this.password = password != null ? password.clone() : null;
    }
    
    public char[] getPassword() {
        return password;
    }
    
    public String getUsername() {
        return username;
    }
    
    public boolean isUsernameSet() {
        return username != null && username.length() > 0;
    }
    
    public boolean isPasswordSet() {
        return password != null && password.length > 0;
    }
    
    /**
     * Destroy the saved username and password
     */
    public void clear() {
        username = null;

        if(password != null) {
            Arrays.fill(password, (char)0);
            password = null;
        }
    }
    
    @Override
    public CredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(getUsername(), getPassword());
    }
}