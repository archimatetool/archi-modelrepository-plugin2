/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;

/**
 * Repo Utils
 * 
 * @author Phillip Beauvoir
 */
public class RepoUtils implements IRepositoryConstants {
    
    private static List<String> sshSchemeNames = Arrays.asList(new String[] {"ssh", "ssh+git", "git+ssh"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /**
     * Adapted from org.eclipse.jgit.transport.TransportGitSsh
     * @param url
     * @return true if url is SSH
     */
    public static boolean isSSH(String url) {
        if(!StringUtils.isSet(url)) {
            return false;
        }
        
        URIish uri = null;
        
        try {
            uri = new URIish(url);
        }
        catch(URISyntaxException ex) {
            ex.printStackTrace();
            return false;
        }
        
        if(uri.getScheme() == null) {
            return StringUtils.isSet(uri.getHost())
                    && StringUtils.isSet(uri.getPath());
        }
        
        return sshSchemeNames.contains(uri.getScheme()) 
                && StringUtils.isSet(uri.getHost())
                && StringUtils.isSet(uri.getPath());
    }
    
    public static boolean isHTTP(String url) {
        return !isSSH(url);
    }

    /**
     * Generate a new random folder name for a new repo
     */
    public static File generateNewRepoFolder() {
        File rootFolder = ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder();
        File newFolder;
        
        do {
            newFolder = new File(rootFolder, UUID.randomUUID().toString().split("-")[0]); //$NON-NLS-1$
        }
        while(newFolder.exists()); // just in case
        
        return newFolder;
    }
    
    /**
     * Check if a folder is an Archi Git Repo
     */
    public static boolean isArchiGitRepository(File folder) {
        if(folder == null || !folder.exists() || !folder.isDirectory()) {
            return false;
        }
        
        File gitFolder = new File(folder, ".git"); //$NON-NLS-1$
        File modelFile = new File(folder, MODEL_FILENAME);
        return gitFolder.exists() && gitFolder.isDirectory() && modelFile.exists();
    }
    
    /**
     * @param model
     * @return true if a model is in an Archi repo folder
     */
    public static boolean isModelInArchiRepository(IArchimateModel model) {
        return isArchiGitRepository(getLocalRepositoryFolderForModel(model));
    }
    
    /**
     * Get the enclosing local repo folder for a model
     * It is assumed that the model is located at localRepoFolder/model.archimate
     * @param model
     * @return The folder or null if not in a Archi repo
     */
    public static File getLocalRepositoryFolderForModel(IArchimateModel model) {
        if(model == null) {
            return null;
        }
        
        // File has to be named correctly
        File file = model.getFile();
        if(file == null || !file.getName().equals(MODEL_FILENAME)) {
            return null;
        }
        
        // Parent folder
        File parent = file.getParentFile();
        
        // Must have a .git folder
        if(parent != null && !new File(parent, ".git").isDirectory()) { //$NON-NLS-1$
            return null;
        }
        
        return parent;
    }
    
    /**
     * @return The global user name and email as set in .gitconfig file
     * @throws IOException
     * @throws ConfigInvalidException
     */
    public static PersonIdent getGitConfigUserDetails() throws IOException, ConfigInvalidException {
        StoredConfig config = SystemReader.getInstance().openUserConfig(null, FS.detect());
        config.load();

        String name = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
        String email = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));

        return new PersonIdent(name, email);
    }
    
    /**
     * Save the gloable user name and email as set in .gitconfig file
     * @param name
     * @param email
     * @throws IOException
     * @throws ConfigInvalidException
     */
    public static void saveGitConfigUserDetails(String name, String email) throws IOException, ConfigInvalidException {
        StoredConfig config = SystemReader.getInstance().openUserConfig(null, FS.detect());
        config.load(); // It seems we have to load before save

        config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name);
        config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email);

        config.save();
    }
}
