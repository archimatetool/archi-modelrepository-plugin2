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
import org.eclipse.jgit.api.FetchCommand;
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
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

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
        // Add modified files to index
        git.add().addFilepattern(".").call();
        //git.add().addFilepattern(IRepositoryConstants.MODEL_FILENAME).addFilepattern(IRepositoryConstants.IMAGES_FOLDER).call();
        
        // Add missing files to index
        Status status = git.status().call();
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
     * @return The first PushResult from the call.
     *         As we're only pushing to one remote URI there should only be one PushResult
     */
    public PushResult pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pushCommand.setProgressMonitor(monitor);
        Iterable<PushResult> results = pushCommand.call();
        PushResult pushResult = results.iterator().next(); // Get the first one
        
        // If successful, ensure we are tracking the current branch
        // Do this *after* a push attempt in case of failure
        RemoteRefUpdate.Status status = getPushResultStatus(pushResult);
        if(status == RemoteRefUpdate.Status.OK || status == RemoteRefUpdate.Status.UP_TO_DATE) {
            setTrackedBranch(git.getRepository().getBranch());
        }
        
        return pushResult;
    }
    
    /**
     * @return a PushResult Status or null if there isn't one
     */
    public static RemoteRefUpdate.Status getPushResultStatus(PushResult pushResult) {
        // As we're only pushing one Ref to one remote URI there should only be one RemoteRefUpdate
        for(RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
            return refUpdate.getStatus();
        }

        return null;
    }
    
    /**
     * Get an error message from a PushResult or null
     */
    public static String getPushResultErrorMessage(PushResult pushResult) {
        StringBuilder sb = new StringBuilder();
        
        pushResult.getRemoteUpdates().stream()
                  .filter(refUpdate -> refUpdate.getStatus() != RemoteRefUpdate.Status.OK)           // Ignore OK
                  .filter(refUpdate -> refUpdate.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE)   // Ignore Up to date
                  .forEach(refUpdate -> {
                      if(StringUtils.isSet(pushResult.getMessages())) {
                          sb.append(pushResult.getMessages() + "\n");
                      }
                      sb.append(refUpdate.getStatus().name() + "\n"); // Status enum name
                  });
            
        
        return sb.length() > 1 ?  sb.toString() : null; // 1 character == "\n"
    }
    
    /**
     * Pull from Remote
     */
    public PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException {
        PullCommand pullCommand = git.pull();
        pullCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pullCommand.setRebase(false); // Merge, not rebase
        pullCommand.setProgressMonitor(monitor);
        return pullCommand.call();
    }
    
    /**
     * Fetch from Remote
     */
    public FetchResult fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean isDryrun) throws GitAPIException {
        FetchCommand fetchCommand = git.fetch();
        fetchCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        fetchCommand.setProgressMonitor(monitor);
        fetchCommand.setDryRun(isDryrun);
        return fetchCommand.call();
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
    public Iterable<PushResult> deleteRemoteBranch(String branchName, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException {
        PushCommand pushCommand = git.push();
        pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        RefSpec refSpec = new RefSpec(":" + branchName);
        pushCommand.setRefSpecs(refSpec);
        pushCommand.setRemote(IRepositoryConstants.ORIGIN);
        pushCommand.setProgressMonitor(monitor);
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
     * Return the remote URL of the Git repo (or null if not found)
     * We assume that there is only one remote per repo, and its name is "origin"
     */
    public String getRemoteURL() throws GitAPIException {
        // Could do it this way:
        // return git.getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION,
        //        Constants.DEFAULT_REMOTE_NAME, ConfigConstants.CONFIG_KEY_URL);
        
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
     * Return true if the remote Ref for the current branch exists and is at HEAD
     * 
     * Note that if the remote Ref does not exist (is null) this will return false,
     * so callers might need to check {@link #getRemoteRefForCurrentBranch()} as well.
     */
    public boolean isRemoteRefForCurrentBranchAtHead() throws IOException {
        Ref remoteRef = getRemoteRefForCurrentBranch();
        return remoteRef != null && isRefAtHead(remoteRef);
    }
    
    /**
     * Return the remote Ref for the current branch that HEAD points to, or null if there is no remote ref
     */
    public Ref getRemoteRefForCurrentBranch() throws IOException {
        return git.getRepository().findRef(getRemoteRefNameForCurrentBranch());
    }
    
    /**
     * Return the remote Ref name for the current branch that HEAD points to
     * This does not mean that the remote Ref exists. For that, call {@link #getRemoteRefForCurrentBranch()}
     */
    public String getRemoteRefNameForCurrentBranch() throws IOException {
        return IRepositoryConstants.ORIGIN + "/" + git.getRepository().getBranch();
    }
    
    /**
     * Return true if there are 2 or more commits for current HEAD
     */
    public boolean hasMoreThanOneCommit() throws IOException, GitAPIException {
        int count = 0;
        
        try(RevWalk revWalk = (RevWalk)git.log().setMaxCount(2).call()) {
            revWalk.setRetainBody(false);
            while(revWalk.next() != null) {
                count++;
            }
            revWalk.dispose();
        }
        
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
     * @param revStr The id of the commit to extract from.
     *               This could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     * @param folder The folder to extract the commit's contents to
     */
    public void extractCommit(String revStr, File folder) throws IOException {
        // Get the ObjectId of revStr
        ObjectId commitId = git.getRepository().resolve(revStr);
        if(commitId == null) {
            return;
        }
        
        // Find the commit in the RevWalk
        try(RevWalk revWalk = new RevWalk(git.getRepository())) {
            RevCommit commit = revWalk.parseCommit(commitId);
            if(commit != null) {
                // Extract commit contents
                extractCommit(commit, folder);
            }
        }
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
    
    /**
     * Return a common ancestor base commit from two points in the repository
     * revStr1 and revStr2 could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     */
    public RevCommit getBaseCommit(String revStr1, String revStr2) throws IOException {
        // Walk the repository to find a common ancestor commit and extract the model from that
        try(RevWalk revWalk = new RevWalk(git.getRepository())) {
            revWalk.setRetainBody(false); // no need for this
            revWalk.setRevFilter(RevFilter.MERGE_BASE); // Merge Base = Common Ancestor

            ObjectId id1 = git.getRepository().resolve(revStr1);
            if(id1 != null) {
                revWalk.markStart(revWalk.parseCommit(id1));
            }

            ObjectId id2 = git.getRepository().resolve(revStr2);
            if(id2 != null) {
                revWalk.markStart(revWalk.parseCommit(id2));
            }

            return revWalk.next();
        }
    }
    
    /**
     * Return the contents of a file in the repo given its ref
     * path is the path to the file
     * revStr could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     * @return The file contents or null if not found
     */
    public byte[] getFileContents(String path, String revStr) throws IOException {
        byte[] bytes = null;

        ObjectId commitId = git.getRepository().resolve(revStr);
        if(commitId == null) {
            return null;
        }

        try(RevWalk revWalk = new RevWalk(git.getRepository())) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            // now try to find a specific file
            try(TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                // Not found, return null
                if(!treeWalk.next()) {
                    return null;
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = git.getRepository().open(objectId);
                bytes = loader.getBytes();
            }

            revWalk.dispose();
        }
        
        return bytes;
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
