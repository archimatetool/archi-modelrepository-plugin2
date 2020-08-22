package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;

import com.archimatetool.model.IArchimateModel;

public interface IArchiRepository extends IRepositoryConstants {

    /**
     * @return The local repository folder (aka the working directory)
     */
    File getLocalRepositoryFolder();

    /**
     * @return The local repository's ".git" folder
     */
    File getLocalGitFolder();

    /**
     * @return The repository name - the file name
     */
    String getName();
    
    /**
     * Update the git config file with the model's name
     */
    void updateName();

    /**
     * @return The .git/model.archimate file in the repository
     */
    File getModelFile();

    /**
     * Return the online URL of the Git repo, taken from the local config file.
     * We assume that there is only one remote per repo, and its name is "origin"
     * @return The online URL or null if not found
     * @throws IOException
     */
    String getOnlineRepositoryURL() throws IOException;

    /**
     * Get the model in the model manager based on its file location
     * @return The model or null if not found
     */
    IArchimateModel getModel();
    
    /**
     * Copy the temp model file and any imnages to the working directory
     * @throws IOException
     */
    void copyModelToWorkingDirectory() throws IOException;
}