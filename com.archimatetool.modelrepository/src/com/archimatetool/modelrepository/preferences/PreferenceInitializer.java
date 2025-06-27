/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.preferences;

import java.io.File;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jgit.revwalk.RevSort;

import com.archimatetool.editor.ArchiPlugin;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;



/**
 * Initialize default preference values
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class PreferenceInitializer extends AbstractPreferenceInitializer implements IPreferenceConstants {

    @Override
    public void initializeDefaultPreferences() {
		IPreferenceStore store = ModelRepositoryPlugin.getInstance().getPreferenceStore();
        
        store.setDefault(PREFS_COMMIT_USER_NAME, System.getProperty("user.name"));
		store.setDefault(PREFS_COMMIT_USER_EMAIL, "");
		
		store.setDefault(PREFS_REPOSITORY_FOLDER, new File(ArchiPlugin.INSTANCE.getUserDataFolder(), "repositories").getAbsolutePath());
		store.setDefault(PREFS_STORE_REPO_CREDENTIALS, true);
		
		store.setDefault(PREFS_SSH_IDENTITY_FILE, new File(System.getProperty("user.home"), ".ssh/id_ed25519").getAbsolutePath());
		store.setDefault(PREFS_SSH_SCAN_DIR, false);
		
		store.setDefault(PREFS_FETCH_IN_BACKGROUND, false);
		store.setDefault(PREFS_FETCH_IN_BACKGROUND_INTERVAL, 60);
		
		store.setDefault(PREFS_HISTORY_SORT_STRATEGY, RevSort.TOPO.name());
    }
}
