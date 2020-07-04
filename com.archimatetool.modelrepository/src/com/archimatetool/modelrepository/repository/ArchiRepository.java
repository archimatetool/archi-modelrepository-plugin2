/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

/**
 * Representation of a local repository
 * This is a wrapper class around a local repo folder
 * 
 * @author Phillip Beauvoir
 */
public class ArchiRepository implements IArchiRepository {
    
    /**
     * The folder location of the local repository
     */
    private File fLocalRepoFolder;

    public ArchiRepository(File localRepoFolder) {
        fLocalRepoFolder = localRepoFolder;
    }

    @Override
    public File getLocalRepositoryFolder() {
        return fLocalRepoFolder;
    }
    
    @Override
    public File getLocalGitFolder() {
        return new File(getLocalRepositoryFolder(), ".git"); //$NON-NLS-1$
    }

    @Override
    public String getName() {
        String name = null;
        
        // Get the name from the config file
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            name = git.getRepository().getConfig().getString(CONFIG_ARCHI_SECTION, null, CONFIG_KEY_NAME);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return name == null ? Messages.ArchiRepository_0 : name;
    }
    
    @Override
    public void updateName() {
        IArchimateModel model = getModel();
        if(model != null) {
            try(Git git = Git.open(getLocalRepositoryFolder())) {
                StoredConfig config = git.getRepository().getConfig();
                config.setString(CONFIG_ARCHI_SECTION, null, CONFIG_KEY_NAME, model.getName());
                config.save();
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public File getModelFile() {
        // TODO - this will be in the local repo folder and will be named something else
        return new File(getLocalRepositoryFolder(), "/.git/" + "temp.archimate"); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    @Override
    public String getOnlineRepositoryURL() throws IOException {
        try(Git git = Git.open(getLocalRepositoryFolder())) {
            return git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, ConfigConstants.CONFIG_KEY_URL);
        }
    }
    
    @Override
    public IArchimateModel getModel() {
        File modelFile = getModelFile();
        
        for(IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
            if(modelFile.equals(model.getFile())) {
                return model;
            }
        }
        
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if((obj != null) && (obj instanceof ArchiRepository)) {
            return fLocalRepoFolder != null && fLocalRepoFolder.equals(((IArchiRepository)obj).getLocalRepositoryFolder());
        }
        return false;
    }
    
}
