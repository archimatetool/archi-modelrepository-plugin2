/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
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
@SuppressWarnings("nls")
public class RepoUtils implements IRepositoryConstants {
    
    private static Set<String> sshSchemeNames = Set.of("ssh", "ssh+git", "git+ssh");

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
            newFolder = new File(rootFolder, UUID.randomUUID().toString().split("-")[0]);
        }
        while(newFolder.exists()); // just in case
        
        return newFolder;
    }
    
    /**
     * Check if a folder is an Archi Git Repo
     * Return true if there is a model file named "model.archimate" in the folder and it has a .git sub-folder
     */
    public static boolean isArchiGitRepository(File folder) {
        if(folder == null || !folder.exists() || !folder.isDirectory()) {
            return false;
        }
        
        File gitFolder = new File(folder, ".git");
        File modelFile = new File(folder, MODEL_FILENAME);
        return gitFolder.isDirectory() && modelFile.exists();
    }
    
    /**
     * @param model
     * @return true if a model is in an Archi repo folder
     */
    public static boolean isModelInArchiRepository(IArchimateModel model) {
        return getWorkingFolderForModel(model) != null;
    }
    
    /**
     * Get the enclosing repo folder (sorking dir) for a model
     * It is assumed that the model file is named "model.archimate", exists, and has a .git folder
     * @return The folder or null if the model is not in a Archi repo
     */
    public static File getWorkingFolderForModel(IArchimateModel model) {
        File parentFolder = (model != null && model.getFile() != null) ? model.getFile().getParentFile() : null;
        return isArchiGitRepository(parentFolder) ? parentFolder : null;
    }
    
    /**
     * Delete the contents of the local repository folder *except* the .git folder
     * @param folder The local repository folder
     */
    public static void deleteContentsOfGitRepository(File folder) throws IOException {
        Path gitFolder = new File(folder, ".git").toPath();
        
        Files.walk(folder.toPath())
             .filter(path -> !path.startsWith(gitFolder)) // Not the .git folder
             .sorted(Comparator.reverseOrder())           // Has to be sorted in reverse order to prevent removal of a non-empty directory
             .map(Path::toFile)
             .forEach(File::delete);
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
