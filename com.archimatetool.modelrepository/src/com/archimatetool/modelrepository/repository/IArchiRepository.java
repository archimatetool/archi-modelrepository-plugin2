package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.CredentialsProvider;

import com.archimatetool.model.IArchimateModel;

/**
 * IArchiRepository interface
 * 
 * @author Phillip Beauvoir
 */
public interface IArchiRepository {
    
    /**
     * Initialise this repository
     */
    IArchiRepository init() throws GitAPIException, IOException;
    
    /**
     * Clone a model
     */
    void cloneModel(String repoURL, CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws GitAPIException, IOException;

    /**
     * @return The repository's working directory
     */
    File getWorkingFolder();

    /**
     * Delete the contents of the local repository folder *but not* the .git folder
     */
    void deleteWorkingFolderContents() throws IOException;

    /**
     * @return The repository's ".git" directory
     */
    File getGitFolder();

    /**
     * @return The repository name - the model's name
     */
    String getName();
    
    /**
     * @return The model.archimate file in the repository
     */
    File getModelFile();

    /**
     * Return the model if it is open in the model manager
     * @return The model, or optional empty if it's not open in the model manager (UI)
     */
    Optional<IArchimateModel> getOpenModel();
}