package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.UsernamePassword;

/**
 * IArchiRepository interface
 * 
 * @author Phillip Beauvoir
 */
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
     * Clone a model
     * @param repoURL
     * @param npw
     * @param monitor
     * @throws GitAPIException
     * @throws IOException
     */
    void cloneModel(String repoURL, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException, IOException;

    /**
     * @return true if there are changes to commit in the working tree
     * @throws IOException
     * @throws GitAPIException
     */
    boolean hasChangesToCommit() throws IOException, GitAPIException;

    /**
     * Push to Remote
     * @param npw
     * @param monitor
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException;

    /**
     * Add the default "origin" Remote
     * @param URL
     * @return
     * @throws IOException
     * @throws GitAPIException
     * @throws URISyntaxException
     */
    RemoteConfig addRemote(String URL) throws IOException, GitAPIException, URISyntaxException;
    
    /**
     * Remove the the default "origin" Remote
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    public RemoteConfig removeRemote() throws IOException, GitAPIException;

    /**
     * @return The short name of the current local branch
     * @throws IOException
     */
    String getCurrentLocalBranchName() throws IOException;

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
     * Copy the temp model file and any images to the working directory
     * @throws IOException
     */
    void copyModelFileToWorkingDirectory() throws IOException;

    /**
     * Copy the working directory files to the temp model file
     * @throws IOException
     */
    void copyWorkingDirectoryToModelFile() throws IOException;

    /**
     * @return User name and email from config. This is either local or global
     * @throws IOException
     */
    PersonIdent getUserDetails() throws IOException;
}