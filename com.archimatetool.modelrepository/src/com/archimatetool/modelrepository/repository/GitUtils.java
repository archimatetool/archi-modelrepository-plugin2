/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.UsernamePassword;

/**
 * Extends JGit's Git class to offer some convenience methods.
 * It means we can call several Git and GitUtils operations while the Git instance is open
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class GitUtils extends Git {
    
    private final boolean closeRepo;
    
    public static GitUtils open(File repoFolder) throws IOException {
        return new GitUtils(Git.open(repoFolder).getRepository(), true);
    }
    
    public static GitUtils wrap(Repository repository) {
        return new GitUtils(repository, false);
    }
    
    private GitUtils(Repository repository, boolean closeRepo) {
        super(repository);
        this.closeRepo = closeRepo;
    }

    /**
     * Commit any changes
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     */
    public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException {
        // Add modified files to index
        add().addFilepattern(".").call();
        //git.add().addFilepattern(IRepositoryConstants.MODEL_FILENAME).addFilepattern(IRepositoryConstants.IMAGES_FOLDER).call();
        
        // Add missing (deleted) files to the index
        Status status = status().call();
        for(String s : status.getMissing()) {
            rm().addFilepattern(s).call();
        }
        
        // Commit
        CommitCommand commitCommand = commit();
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
        return !status().call().isClean();
    }

    /**
     * Push to Remote
     * @return The first PushResult from the call.
     *         As we're only pushing to one remote URI there should only be one PushResult
     */
    public PushResult pushToRemote(UsernamePassword npw, ProgressMonitor monitor) throws IOException, GitAPIException {
        PushCommand pushCommand = push();
        pushCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pushCommand.setProgressMonitor(monitor);
        Iterable<PushResult> results = pushCommand.call();
        PushResult pushResult = results.iterator().next(); // Get the first one
        
        // If successful, ensure we are tracking the current branch
        // Do this *after* a push attempt in case of failure
        RemoteRefUpdate.Status status = getPushResultStatus(pushResult);
        if(status == RemoteRefUpdate.Status.OK || status == RemoteRefUpdate.Status.UP_TO_DATE) {
            setTrackedBranch(getRepository().getBranch());
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
        PullCommand pullCommand = pull();
        pullCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        pullCommand.setRebase(false); // Merge, not rebase
        pullCommand.setProgressMonitor(monitor);
        return pullCommand.call();
    }
    
    /**
     * Fetch from Remote
     */
    public FetchResult fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean isDryrun) throws GitAPIException {
        FetchCommand fetchCommand = fetch();
        fetchCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(npw));
        fetchCommand.setProgressMonitor(monitor);
        fetchCommand.setDryRun(isDryrun);
        return fetchCommand.call();
    }
    
    /**
     * @return User name and email from config file. This is either local or global.
     */
    public PersonIdent getUserDetails() {
        StoredConfig config = getRepository().getConfig();
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
        StoredConfig config = getRepository().getConfig();

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
        return getRepository().getBranch();
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
        return branchDelete().setBranchNames(branchNames).setForce(force).call();
    }
    
    /**
     * Delete a remote branch by pushing to repo
     * @param branchName Local type ref like "refs/heads/branch"
     */
    public Iterable<PushResult> deleteRemoteBranch(String branchName, UsernamePassword npw, ProgressMonitor monitor) throws GitAPIException {
        PushCommand pushCommand = push();
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
        config = remoteRemove().setRemoteName(IRepositoryConstants.ORIGIN).call();
        
        // Add new one
        if(StringUtils.isSetAfterTrim(URL)) {
            config = remoteAdd().setName(IRepositoryConstants.ORIGIN).setUri(new URIish(URL)).call();
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
        
        List<RemoteConfig> remotes = remoteList().call();
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
        reset().setRef(ref).setMode(ResetType.HARD).call();
        
        // Clean extra files
        clean().setCleanDirectories(true).call();
    }

    /**
     * Return true if the given RevCommit is equal to the HEAD position
     */
    public boolean isCommitAtHead(RevCommit commit) throws IOException {
        ObjectId headID = getRepository().resolve(Constants.HEAD);
        ObjectId commitID = commit.getId();
        return headID != null && commitID != null && headID.equals(commitID);
    }

    /**
     * Return true if the given Ref is equal to the HEAD position
     */
    public boolean isRefAtHead(Ref ref) throws IOException {
        ObjectId headID = getRepository().resolve(Constants.HEAD);
        return headID != null && ref != null && headID.equals(ref.getObjectId());
    }

    /**
     * Return true if the remote Ref for the current branch exists and is equal to the HEAD position
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
        return getRepository().findRef(getRemoteRefNameForCurrentBranch());
    }
    
    /**
     * Return the remote Ref name for the current branch that HEAD points to
     * This does not mean that the remote Ref exists. For that, call {@link #getRemoteRefForCurrentBranch()}
     */
    public String getRemoteRefNameForCurrentBranch() throws IOException {
        return IRepositoryConstants.ORIGIN + "/" + getRepository().getBranch();
    }
    
    /**
     * Return true if there are 2 or more commits for current HEAD
     */
    public boolean hasMoreThanOneCommit() throws IOException, GitAPIException {
        int count = 0;
        
        try(RevWalk revWalk = (RevWalk)log().setMaxCount(2).call()) {
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
        ObjectId objectID = getRepository().resolve(revstr);
        return objectID != null ? getRepository().parseCommit(objectID).getParentCount() : 0;
    }

    /**
     * Return a common ancestor base commit from two points in the repository
     * revStr1 and revStr2 could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     */
    public RevCommit getBaseCommit(String revStr1, String revStr2) throws IOException {
        // Walk the repository to find a common ancestor commit and extract the model from that
        try(RevWalk revWalk = new RevWalk(getRepository())) {
            revWalk.setRetainBody(false); // no need for this
            revWalk.setRevFilter(RevFilter.MERGE_BASE); // Merge Base = Common Ancestor

            ObjectId id1 = getRepository().resolve(revStr1);
            if(id1 != null) {
                revWalk.markStart(revWalk.parseCommit(id1));
            }

            ObjectId id2 = getRepository().resolve(revStr2);
            if(id2 != null) {
                revWalk.markStart(revWalk.parseCommit(id2));
            }

            return revWalk.next();
        }
    }
    
    /**
     * Determine if a commit is reachable from another commit.
     * revStr1 and revStr2 could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     * 
     * @param baseRevStr commit the caller thinks is reachable from tipRevStr
     * @param tipRevStr  commit to start iteration from, and which is most likely a descendant (child) of baseRevStr
     * @return if there is a path directly from tipRevStr to baseRevStr (and thus baseRevStr is fully merged into tipRevStr; false otherwise.
     */
    public boolean isMergedInto(String baseRevStr, String tipRevStr) throws IOException {
        try(RevWalk revWalk = new RevWalk(getRepository())) {
            ObjectId baseID = getRepository().resolve(baseRevStr);
            RevCommit baseCommit = revWalk.lookupCommit(baseID);
            
            ObjectId tipID = getRepository().resolve(tipRevStr);
            RevCommit tipCommit = revWalk.lookupCommit(tipID);
            
            return revWalk.isMergedInto(baseCommit, tipCommit);
        }
    }
    
    /**
     * Extract the contents of a commit to a folder
     * @param revStr The id of the commit to extract from.
     *               This could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     * @param folder The folder to extract the commit's contents to
     * @param preserveEol if true EOL characters in text files are set according to the repo's "autocrlf" setting.
     *                    On Windows this will be CRLF, else LF
     */
    public void extractCommit(String revStr, File folder, boolean preserveEol) throws IOException {
        // Get the ObjectId of revStr
        ObjectId commitId = getRepository().resolve(revStr);
        if(commitId == null) {
            return;
        }
        
        // Find the commit in the RevWalk
        try(RevWalk revWalk = new RevWalk(getRepository())) {
            RevCommit commit = revWalk.parseCommit(commitId);
            if(commit != null) {
                // Extract commit contents
                extractCommit(commit, folder, preserveEol);
            }
        }
    }
    
    /**
     * Extract the contents of a commit to a folder
     * @param commit The commit to extract from
     * @param folder The folder to extract the commit's contents to
     * @param preserveEol if true EOL characters in text files are set according to the repo's "autocrlf" setting.
     *                    On Windows this will be CRLF, else LF
     */
    public void extractCommit(RevCommit commit, File folder, boolean preserveEol) throws IOException {
        // Walk the tree and extract the contents of the commit
        try(TreeWalk treeWalk = new TreeWalk(getRepository())) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while(treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = getRepository().open(objectId);
                
                File file = new File(folder, treeWalk.getPathString());
                file.getParentFile().mkdirs();
                
                try(FileOutputStream fos = new FileOutputStream(file)) {
                    // Wrap the output stream in another stream to respect the line ending setting
                    if(preserveEol) {
                        EolStreamType eolStreamType = treeWalk.getEolStreamType(OperationType.CHECKOUT_OP);
                        try(OutputStream out = EolStreamTypeUtil.wrapOutputStream(fos, eolStreamType)) {
                            loader.copyTo(out);
                        }
                    }
                    else {
                        loader.copyTo(fos);
                    }
                }
            }
        }
    }
    
    /**
     * Return the contents of a file as a byte stream in the repo given its ref
     * @param path is the path to the file
     * @param revStr could be "HEAD" or "refs/remotes/origin/main", or a SHA-1 - same as for Repository#resolve()
     * @param preserveEol if true EOL characters in text files are set according to the repo's "autocrlf" setting.
     *                    On Windows this will be CRLF, else LF
     * @return The file contents or null if not found
     */
    public byte[] getFileContents(String path, String revStr, boolean preserveEol) throws IOException {
        ObjectId commitId = getRepository().resolve(revStr);
        if(commitId == null) {
            return null;
        }

        try(RevWalk revWalk = new RevWalk(getRepository())) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            // now try to find a specific file
            try(TreeWalk treeWalk = new TreeWalk(getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                // Not found, return null
                if(!treeWalk.next()) {
                    return null;
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = getRepository().open(objectId);
                
                // Use a stream in case ObjectLoader.isLarge
                try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    // Wrap the output stream in another stream to respect the line ending setting
                    if(preserveEol) {
                        EolStreamType eolStreamType = treeWalk.getEolStreamType(OperationType.CHECKOUT_OP);
                        try(OutputStream out = EolStreamTypeUtil.wrapOutputStream(baos, eolStreamType)) {
                            loader.copyTo(out);
                        }
                    }
                    else {
                        loader.copyTo(baos);
                    }
                    
                    return baos.toByteArray();
                }
            }
        }
    }
    
    @Override
    public void close() {
        // we have to close the repository
        if(closeRepo) {
            getRepository().close();
        }
        else {
            new Error("Closing GitUtils is not necessary.").printStackTrace();
        }
    }
    
    /**
     * Set the local branch to track "origin"
     */
    private void setTrackedBranch(String branchName) throws IOException {
        if(branchName == null) {
            return;
        }
        
        StoredConfig config = getRepository().getConfig();
        
        if(!IRepositoryConstants.ORIGIN.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE))) {
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, IRepositoryConstants.ORIGIN);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branchName);
            config.save();
        }
    }
}
