package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import com.archimatetool.model.IArchimateModel;

public interface IArchiRepository extends IRepositoryConstants {
    
    /**
     * Initialise this repository
     */
    void init() throws GitAPIException, IOException;
    
    /**
     * Commit any changes
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     * @throws GitAPIException
     * @throws IOException
     */
    RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException;


    /**
     * @return true if there are changes to commit in the working tree
     * @throws IOException
     * @throws GitAPIException
     */
    boolean hasChangesToCommit() throws IOException, GitAPIException;

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
    void setName(String name);

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
    void copyModelToWorkingDirectory() throws IOException, GitAPIException;

    /**
     * @return User name and email from config. This is either local or global
     * @throws IOException
     */
    PersonIdent getUserDetails() throws IOException;
}