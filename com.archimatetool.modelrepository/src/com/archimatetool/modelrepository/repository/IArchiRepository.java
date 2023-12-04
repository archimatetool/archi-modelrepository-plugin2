package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.UsernamePassword;

/**
 * IArchiRepository interface
 * 
 * @author Phillip Beauvoir
 */
public interface IArchiRepository {
    
    /**
     * Initialise this repository
     */
    void init() throws GitAPIException, IOException;
    
    /**
     * Clone a model
     */
    void cloneModel(String repoURL, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException, IOException;

    /**
     * Commit any changes
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     */
    RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException;

    /**
     * @return true if there are changes to commit in the working tree
     */
    boolean hasChangesToCommit() throws IOException, GitAPIException;

    /**
     * Push to Remote
     * @return the single PushResult
     *         As we're only pushing to one remote URI there should only be one PushResult
     */
    PushResult pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException;

    /**
     * Set the default "origin" remote to the given URL
     * @param URL if this is empty or null, the remote is removed else it is added or updated if it already exists
     */
    RemoteConfig setRemote(String URL) throws IOException, GitAPIException, URISyntaxException;
    
    /**
     * Fetch from Remote
     */
    FetchResult fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean isDryrun) throws IOException, GitAPIException;

    /**
     * Do a HARD reset to the given ref
     * @param ref can be "refs/heads/main" for local, or "origin/main" for remote ref
     */
    void resetToRef(String ref) throws IOException, GitAPIException;

    /**
     * @return The short name of the current local branch
     */
    String getCurrentLocalBranchName() throws IOException;

    /**
     * @return The repository's working directory
     */
    File getWorkingFolder();

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
     * Return the remote URL of the Git repo (or null if not found)
     * We assume that there is only one remote per repo, and its name is "origin"
     */
    String getRemoteURL() throws IOException, GitAPIException;

    /**
     * Return the model if it is open in the model manager
     * @return The model, or null if it's not open in the model manager (UI)
     */
    IArchimateModel getOpenModel();
    
    /**
     * @return User name and email from config. This is either local or global config.
     */
    PersonIdent getUserDetails() throws IOException;
    
    /**
     * Save user name and email to local config
     */
    void saveUserDetails(String name, String email) throws IOException;

    /**
     * Extract the contents of a commit to a folder
     * @param commit The commit to extract from
     * @param folder The folder to extract the commit's contents to
     * @param preserveEol if true EOL characters in text files are set according to the repo's "autocrlf" setting.
     *                    On Windows this will be CRLF, else LF
     */
    void extractCommit(RevCommit commit, File folder, boolean preserveEol) throws IOException;
}