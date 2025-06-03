package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

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
    IArchiRepository init() throws GitAPIException, IOException;
    
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
     * Commit any changes with the manifest
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     */
    RevCommit commitChangesWithManifest(String commitMessage, boolean amend) throws GitAPIException, IOException;
    
    /**
     * Make the initial commit of a model and add the manifest to the commit message
     * @param model
     * @param commitMessage
     * @return RevCommit
     */
    RevCommit commitModelWithManifest(IArchimateModel model, String commitMessage) throws GitAPIException, IOException;

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
     * @param url if this is empty or null, the remote is removed else it is added or updated if it already exists
     */
    RemoteConfig setRemote(String url) throws IOException, GitAPIException, URISyntaxException;
    
    /**
     * Return the remote URL of the Git repo (or null if not found)
     * We assume that there is only one remote per repo, and its name is "origin"
     */
    String getRemoteURL() throws IOException, GitAPIException;

    /**
     * Remove remote refs (branches) from the repository and the config file
     * This is based on DeleteBranchCommand but this removes all branch entries from the config file
     * @param url The remote ref URL
     */
    void removeRemoteRefs(String url) throws IOException, GitAPIException;

    /**
     * Fetch from Remote
     */
    List<FetchResult> fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean fetchTags, boolean isDryrun) throws IOException, GitAPIException;

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