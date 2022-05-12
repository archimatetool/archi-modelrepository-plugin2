/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.UsernamePassword;

/**
 * Git Utils
 * 
 * This is a wrapper around a JGit Git instance
 * It means we can call several git operations while the Git instance is open and close it when finished
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class GitUtils implements AutoCloseable {
    
    private Git git;
    
    public static GitUtils open(File repoFolder) throws IOException {
        return new GitUtils(repoFolder);
    }
    
    /**
     * @return The Git instance which can be used for more git operations
     *         It's advised to not close this instance but rather to close the GitUtils instance
     */
    public Git getGit() {
        return git;
    }
    
    private GitUtils(File repoFolder) throws IOException {
        git = Git.open(repoFolder);
    }
    
    /**
     * Commit any changes
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     */
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException {
        Status status = git.status().call();
        
        // Nothing changed
        if(status.isClean()) {
            return null;
        }
        
        // Add modified files to index
        git.add().addFilepattern(".").call();
        //git.add().addFilepattern(IRepositoryConstants.MODEL_FILENAME).addFilepattern(IRepositoryConstants.IMAGES_FOLDER).call();
        
        // Add missing files to index
        for(String s : status.getMissing()) {
            git.rm().addFilepattern(s).call();
        }
        
        // Commit
        CommitCommand commitCommand = git.commit();
        PersonIdent userDetails = getUserDetails();
        commitCommand.setAuthor(userDetails);
        commitCommand.setMessage(commitMessage);
        commitCommand.setAmend(amend);
        return commitCommand.call();
    }

    /**
     * @return true if there are changes to commit in the working tree
     */
    public boolean hasChangesToCommit() throws GitAPIException {
        return !git.status().call().isClean();
    }

    /**
     * Push to Remote
     */
    public Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        // Ensure we are tracking the current branch
        setTrackedBranch(git.getRepository().getBranch());

        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pushCommand.setProgressMonitor(monitor);
        return pushCommand.call();
    }
    
    /**
     * Pull from Remote
     */
    public PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        // Ensure we are tracking the current branch
        setTrackedBranch(git.getRepository().getBranch());

        PullCommand pullCommand = git.pull();
        pullCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pullCommand.setRebase(false); // Merge, not rebase
        pullCommand.setProgressMonitor(monitor);
        return pullCommand.call();
    }
    
    /**
     * Return the online URL of the Git repo (or null if not found)
     * We assume that there is only one remote per repo, and its name is "origin"
     */
    public String getOnlineRepositoryURL() throws GitAPIException {
        List<RemoteConfig> remotes = git.remoteList().call();
        if(!remotes.isEmpty()) {
            List<URIish> uris = remotes.get(0).getURIs();
            if(!uris.isEmpty()) {
                return uris.get(0).toASCIIString();
            }
        }
        return null;
    }
    
    /**
     * @return User name and email from config file. This is either local or global.
     */
    public PersonIdent getUserDetails() {
        StoredConfig config = git.getRepository().getConfig();
        String name = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
        String email = StringUtils.safeString(config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));
        return new PersonIdent(name, email);
    }

    /**
     * Save user name and email to local config file
     */
    public void saveUserDetails(String name, String email) throws IOException {
        // Get global user details from global .gitconfig for comparison
        PersonIdent global = new PersonIdent("", "");
        
        try {
            global = RepoUtils.getGitConfigUserDetails();
        }
        catch(ConfigInvalidException ex) {
            ex.printStackTrace();
        }
        
        // Save to local config
        StoredConfig config = git.getRepository().getConfig();

        // If global name == local name or blank then unset
        if(!StringUtils.isSet(name) || global.getName().equals(name)) {
            config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME);
        }
        // Set
        else {
            config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name);
        }

        // If global email == local email or blank then unset
        if(!StringUtils.isSet(email) || global.getEmailAddress().equals(email)) {
            config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL);
        }
        else {
            config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email);
        }

        config.save();
    }

    /**
     * Return the current local branch name that HEAD points to
     */
    public String getCurrentLocalBranchName() throws IOException {
        return git.getRepository().getBranch();
    }
    
    /**
     * Delete branches
     * @param force if false a check will be performed whether the branch to be deleted
     *              is already merged into the current branch and deletion will be refused if not merged
     * @param branchNames Any number of branch names. For example, "refs/heads/branch" or "refs/remotes/origin/branch"
     * @return a list of the result of full branch names deleted
     */
    public List<String> deleteBranch(boolean force, String... branchNames) throws GitAPIException {
        // Delete local and remote branch refs
        return git.branchDelete().setBranchNames(branchNames).setForce(force).call();
    }
    
    /**
     * Delete a remote branch by pushing to repo
     * @param branchName Local type ref like "refs/heads/branch"
     */
    public Iterable<PushResult> deleteRemoteBranch(String branchName, UsernamePassword npw) throws GitAPIException {
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        RefSpec refSpec = new RefSpec(":" + branchName);
        pushCommand.setRefSpecs(refSpec);
        pushCommand.setRemote(IRepositoryConstants.ORIGIN);
        return pushCommand.call();
    }
    
    /**
     * Set the default "origin" remote to the given URL
     * @param URL if this is empty or null, the remote is removed else it is added or updated if it already exists
     */
    public RemoteConfig setRemote(String URL) throws GitAPIException, URISyntaxException {
        RemoteConfig config;
        
        // Remove existing remote
        config = git.remoteRemove().setRemoteName(IRepositoryConstants.ORIGIN).call();
        
        // Add new one
        if(StringUtils.isSetAfterTrim(URL)) {
            config = git.remoteAdd().setName(IRepositoryConstants.ORIGIN).setUri(new URIish(URL)).call();
        }
        
        return config;
    }

    /**
     * Do a HARD reset to the given ref
     * @param ref can be "refs/heads/main" for local, or "origin/main" for remote ref
     */
    public void resetToRef(String ref) throws GitAPIException {
        // Reset
        git.reset().setRef(ref).setMode(ResetType.HARD).call();
        
        // Clean extra files
        git.clean().setCleanDirectories(true).call();
    }

    /**
     * Return true if the given RevCommit is at HEAD
     */
    public boolean isCommitAtHead(RevCommit commit) throws IOException {
        ObjectId headID = git.getRepository().resolve(Constants.HEAD);
        ObjectId commitID = commit.getId();
        return headID != null && commitID != null && headID.equals(commitID);
    }

    /**
     * Return true if the given Ref is at HEAD
     */
    public boolean isRefAtHead(Ref ref) throws IOException {
        ObjectId headID = git.getRepository().resolve(Constants.HEAD);
        return headID != null && ref != null && headID.equals(ref.getObjectId());
    }

    /**
     * @return true if the remote Ref for the current branch is at HEAD
     */
    public boolean isRemoteRefForCurrentBranchAtHead() throws IOException {
        Ref onlineRef = git.getRepository().findRef(IRepositoryConstants.ORIGIN + "/" + git.getRepository().getBranch());
        return isRefAtHead(onlineRef);
    }

    /**
     * Return true if there are 2 or more commits for current HEAD
     */
    public boolean hasMoreThanOneCommit() throws IOException, GitAPIException {
        int count = 0;
        
        RevWalk revWalk = (RevWalk)git.log().setMaxCount(2).call();
        while(revWalk.next() != null) {
            count++;
        }
        revWalk.dispose(); // dispose will also close the RevWalk
        
        return count > 1;
    }
    
    /**
     * Return the number of parent commits for the commit at revision revstr (HEAD, ref name, etc)
     */
    public int getCommitParentCount(String revstr) throws IOException {
        ObjectId objectID = git.getRepository().resolve(revstr);
        return objectID != null ? git.getRepository().parseCommit(objectID).getParentCount() : 0;
    }

    /**
     * Extract the contents of a commit to a folder
     * @param commit The commit to extract from
     * @param folder The folder to extract the commit's contents to
     */
    public void extractCommit(RevCommit commit, File folder) throws IOException {
        // Walk the tree and extract the contents of the commit
        try(TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while(treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = git.getRepository().open(objectId);
                
                File file = new File(folder, treeWalk.getPathString());
                file.getParentFile().mkdirs();
                
                try(FileOutputStream out = new FileOutputStream(file)) {
                    loader.copyTo(out);
                }
            }
        }
    }
    
    @Override
    public void close() {
        git.close();
    }
    
    /**
     * Set the local branch to track "origin"
     */
    private void setTrackedBranch(String branchName) throws IOException {
        if(branchName == null) {
            return;
        }
        
        StoredConfig config = git.getRepository().getConfig();
        
        if(!IRepositoryConstants.ORIGIN.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE))) {
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, IRepositoryConstants.ORIGIN);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
            config.save();
        }
    }
}
