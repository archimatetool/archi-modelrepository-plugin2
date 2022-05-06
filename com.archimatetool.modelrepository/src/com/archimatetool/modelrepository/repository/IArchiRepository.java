package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.PullResult;
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
     */
    Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException;

    /**
     * Pull from Remote
     */
    PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException;

    /**
     * Set the default "origin" remote to the given URL
     * @param URL if this is empty or null, the remote is removed else it is added or updated if it already exists
     */
    RemoteConfig setRemote(String URL) throws IOException, GitAPIException, URISyntaxException;
    
    /**
     * Do a HARD reset to the given ref
     * @param ref can be "refs/heads/main" for local, or "origin/main" for remote ref
     */
    void resetToRef(String ref) throws IOException, GitAPIException;

    /**
     * @return true if the latest local HEAD commit and the remote commit are the same
     */
    boolean isHeadAndRemoteSame() throws IOException, GitAPIException;

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
     * @return The repository name - the model's name
     */
    String getName();
    
    /**
     * @return The model.archimate file in the repository
     */
    File getModelFile();

    /**
     * Return the online URL of the Git repo
     * We assume that there is only one remote per repo, and its name is "origin"
     * @return The online URL or null if not found
     * @throws IOException
     * @throws GitAPIException 
     */
    String getOnlineRepositoryURL() throws IOException, GitAPIException;

    /**
     * Get the model in the model manager based on its file location
     * @return The model or null if not found
     */
    IArchimateModel getModel();
    
    /**
     * @return User name and email from config. This is either local or global
     * @throws IOException
     */
    PersonIdent getUserDetails() throws IOException;
    
    /**
     * Save user name and email
     * @param name
     * @param email
     * @throws IOException
     */
    void saveUserDetails(String name, String email) throws IOException;

    /**
     * Extract the contents of a commit to a folder
     * @param commit The commit to extract froms
     * @param folder The folder to extract the commit's contents to
     * @throws IOException
     */
    void extractCommit(RevCommit commit, File folder) throws IOException;
}