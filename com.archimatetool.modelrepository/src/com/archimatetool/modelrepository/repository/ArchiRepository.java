/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
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
        try {
            return ArchiRepositoryProperties.open(this).getProperty("name", Messages.ArchiRepository_0); //$NON-NLS-1$
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }

        return Messages.ArchiRepository_0;
    }
    
    @Override
    public void updateName() {
        IArchimateModel model = getModel();
        if(model != null) {
            try {
                ArchiRepositoryProperties properties = ArchiRepositoryProperties.open(this);
                properties.setProperty("name", model.getName()); //$NON-NLS-1$
                properties.save();
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public File getModelFile() {
        return new File(getLocalGitFolder(), MODEL_FILENAME);
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
    public void copyModelToWorkingDirectory() throws IOException {
        // Delete the images folder
        File imagesFolder = new File(getLocalRepositoryFolder(), IMAGES_FOLDER);
        FileUtils.deleteFolder(imagesFolder);
        
        File modelFile = getModelFile();
        
        // Model is in archive format and contains images
        if(IArchiveManager.FACTORY.isArchiveFile(modelFile)) {
            imagesFolder.mkdirs();
            
            ZipFile zipFile = new ZipFile(modelFile);
            
            // Open model zip file and extract and copy all entries
            for(Enumeration<? extends ZipEntry> enm = zipFile.entries(); enm.hasMoreElements();) {
                ZipEntry zipEntry = enm.nextElement();
                String entryName = zipEntry.getName();
                InputStream in = zipFile.getInputStream(zipEntry);
                File outFile = null;
                
                if(entryName.startsWith("images/")) { //$NON-NLS-1$
                    outFile = new File(getLocalRepositoryFolder(), entryName);
                }
                if(entryName.equalsIgnoreCase("model.xml")) { //$NON-NLS-1$
                    outFile = new File(getLocalRepositoryFolder(), MODEL_FILENAME);
                }
                
                if(outFile != null) {
                    Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                
                in.close();
            }
            
            zipFile.close();
        }
        // A normal file so copy it
        else {
            File outFile = new File(getLocalRepositoryFolder(), MODEL_FILENAME);
            Files.copy(getModelFile().toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if((obj != null) && (obj instanceof ArchiRepository)) {
            return fLocalRepoFolder != null && fLocalRepoFolder.equals(((IArchiRepository)obj).getLocalRepositoryFolder());
        }
        return false;
    }
    
}
