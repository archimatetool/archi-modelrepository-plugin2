/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.preferences;


/**
 * Constant definitions for plug-in preferences
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public interface IPreferenceConstants {
    
    String PREFS_COMMIT_USER_NAME = "userName";
    String PREFS_COMMIT_USER_EMAIL = "userEmail";
    
    String PREFS_REPOSITORY_FOLDER = "repoFolder";
    
    String PREFS_STORE_REPO_CREDENTIALS = "storeCredentials";
    
    String PREFS_SCAN_REPOSITORY_FOLDER = "scanRepoFolder";
    String PREFS_SSH_IDENTITY_FILE = "sshIdentityFile";
    String PREFS_SSH_IDENTITY_REQUIRES_PASSWORD = "sshIdentityRequiresPassword";
    String PREFS_SSH_SCAN_DIR = "sshScanSshDir";
    
    String PREFS_FETCH_IN_BACKGROUND = "fetchInBackground";
    String PREFS_FETCH_IN_BACKGROUND_INTERVAL = "fetchInBackgroundInterval";
    
    String PREFS_HISTORY_SORT_STRATEGY = "historySortStrategy";
 }
