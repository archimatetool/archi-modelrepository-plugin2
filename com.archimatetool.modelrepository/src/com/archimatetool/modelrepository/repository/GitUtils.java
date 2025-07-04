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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;

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
        // Check lock file is deleted
        checkLockFile();
        
        // Add modified files to index
        add().addFilepattern(".").call();
        //add().addFilepattern(RepoConstants.MODEL_FILENAME).addFilepattern(RepoConstants.IMAGES_FOLDER).call();
        
        // Add missing (deleted) files to the index
        for(String s : status().call().getMissing()) {
            rm().addFilepattern(s).call();
        }
        
        return commit()
                .setAuthor(getUserDetails())
                .setMessage(commitMessage)
                .setAmend(amend)
                .setSign(false) // No GPG signing
                .call();
    }

    /**
     * Commit any changes with the manifest
     * @param commitMessage
     * @param amend If true, previous commit is amended
     * @return RevCommit
     */
    public RevCommit commitChangesWithManifest(String commitMessage, boolean amend) throws GitAPIException, IOException {
        String manifest = CommitManifest.createManifestForCommit(this, amend);
        return commitChanges(commitMessage + manifest, amend);
    }
    
    /**
     * Make the initial commit of a model and add the manifest to the commit message
     * @param model
     * @param commitMessage
     * @return RevCommit
     */
    public RevCommit commitModelWithManifest(IArchimateModel model, String commitMessage) throws GitAPIException {
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        return commitChanges(commitMessage + manifest, false);
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
    public PushResult pushToRemote(CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws IOException, GitAPIException {
        Iterable<PushResult> results = push()
                .setCredentialsProvider(credentialsProvider)
                .add(getRepository().getFullBranch()) // Push current branch
                .setPushTags() // Push tags
                .setProgressMonitor(monitor)
                .call();
        
        PushResult pushResult = results.iterator().next(); // Get the first one
        
        // If current branch push is successful, ensure we are tracking it
        // Do this *after* a push attempt in case of failure
        RemoteRefUpdate refUpdate = pushResult.getRemoteUpdate(getRepository().getFullBranch());
        if(refUpdate != null) {
            Status status = refUpdate.getStatus();
            if(status == Status.OK || status == Status.UP_TO_DATE) {
                setTrackedBranch(getRepository().getBranch());
            }
        }
        
        return pushResult;
    }
    
    /**
     * Fetch from Remote
     * @return a List of FetchResults
     * If fetchTags is true the first FetchResult will be for branches and the second for tags.
     * If fetchTags is false the first and only FetchResult will be for branches.
     */
    public List<FetchResult> fetchFromRemote(CredentialsProvider credentialsProvider, ProgressMonitor monitor, boolean fetchTags) throws GitAPIException, IOException {
        List<FetchResult> fetchresults = new ArrayList<>();
        
        // Fetch branches
        FetchResult branchFetchResult = fetch()
                .setCredentialsProvider(credentialsProvider)
                .setProgressMonitor(monitor)
                .setRefSpecs(RepoConstants.REFSPEC_FETCH_ALL_BRANCHES) // Explicitly set this rather than from config file
                .setRemoveDeletedRefs(true) // Delete any remote branch refs that we have but are not on the remote
                .setTagOpt(TagOpt.NO_TAGS)  // We'll fetch tags separately
                .call();
        
        fetchresults.add(branchFetchResult);

        // Fetch tags
        // Do this separately because we want to force fetch tags in case user has the same tag name but on a different commit
        // and also because we want setRemoveDeletedRefs(false) so that local only tags are not deleted
        if(fetchTags) {
            FetchResult tagFetchResult = fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setProgressMonitor(monitor)
                    .setRefSpecs(RepoConstants.REFSPEC_FETCH_ALL_TAGS) // fetch all tags (force)
                    .setRemoveDeletedRefs(false) // Don't delete local tags that we have but are not on the remote
                    .call();
            
            fetchresults.add(tagFetchResult);
        }
        
        // Ensure that the current branch is tracking its remote (if there is one) 
        List<Ref> refs = branchList()
                .setListMode(ListMode.REMOTE)
                .setContains(getRepository().getBranch())
                .call();
        
        if(!refs.isEmpty()) {
            setTrackedBranch(getRepository().getBranch());
        }
        
        // Ensure that all local refs are tracking the remote ref
//        for(Ref ref : branchList().setListMode(ListMode.REMOTE).call()) {
//            String shortName = getRepository().shortenRemoteBranchName(ref.getName());
//            if(shortName != null) {
//                setTrackedBranch(shortName);
//            }
//        }
        
        return fetchresults;
    }
    
    /**
     * Do a dry run Fetch on all branches but no tags on remote to check if there are updates
     */
    public FetchResult fetchFromRemoteDryRun(CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws GitAPIException {
        return fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setDryRun(true)
                    .setProgressMonitor(monitor)
                    .setRefSpecs(RepoConstants.REFSPEC_FETCH_ALL_BRANCHES) // Explicitly set this rather than from config file
                    .setTagOpt(TagOpt.NO_TAGS) // No tags
                    .call();
    }
    
    /**
     * @return User name and email from config file. This is either local or global.
     */
    public PersonIdent getUserDetails() {
        return new PersonIdent(getRepository());
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
     * @return The primary branch, either "main" or "master" or null if either of these are not present
     */
    public String getPrimaryBranch() throws IOException {
        // "main" takes priority
        if(getRepository().exactRef(RepoConstants.R_HEADS_MAIN) != null
                || getRepository().exactRef(RepoConstants.R_REMOTES_ORIGIN_MAIN) != null) {
            return RepoConstants.MAIN;
        }
        
        // then "master"
        if(getRepository().exactRef(RepoConstants.R_HEADS_MASTER) != null
                || getRepository().exactRef(RepoConstants.R_REMOTES_ORIGIN_MASTER) != null) {
            return RepoConstants.MASTER;
        }
        
        // no primary branch
        return null;
    }
    
    /**
     * Delete branches
     * @param force if false a check will be performed whether the branch to be deleted
     *              is already merged into the current branch and deletion will be refused if not merged
     * @param branchNames Any number of branch names. For example, "refs/heads/branch" or "refs/remotes/origin/branch"
     * @return a list of the result of full branch names deleted
     */
    public List<String> deleteBranches(boolean force, String... branchNames) throws GitAPIException {
        // Delete local and remote branch refs
        return branchDelete().setBranchNames(branchNames).setForce(force).call();
    }
    
    /**
     * Delete a remote branch by pushing to repo
     * @param branchName Local type ref like "refs/heads/branch"
     * @return The first PushResult from the call.
     *         As we're only pushing to one remote URI there should only be one PushResult
     */
    public PushResult deleteRemoteBranch(String branchName, CredentialsProvider credentialsProvider, ProgressMonitor monitor) throws GitAPIException {
        Iterable<PushResult> results = push()
                .setCredentialsProvider(credentialsProvider)
                .setRefSpecs(new RefSpec(":" + branchName))
                .setRemote(RepoConstants.ORIGIN)
                .setProgressMonitor(monitor)
                .call();
        
        return results.iterator().next(); // Get the first one
    }
    
    /**
     * Set the default "origin" remote to the given URL
     * @param url if this is empty or null, the remote is removed else it is added or updated if it already exists
     */
    public RemoteConfig setRemote(String url) throws GitAPIException, URISyntaxException {
        // Remove existing remote
        RemoteConfig config = remoteRemove().setRemoteName(RepoConstants.ORIGIN).call();
        
        // Add new one
        if(StringUtils.isSetAfterTrim(url)) {
            config = remoteAdd().setName(RepoConstants.ORIGIN).setUri(new URIish(url)).call();
        }
        
        return config;
    }
    
    /**
     * Return the remote URL of the Git repo (or null if not found)
     * We assume that there is only one remote per repo, and its name is "origin"
     */
    public String getRemoteURL() throws GitAPIException {
        // Could do it this way:
        // return getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION,
        //        RepoConstants.ORIGIN, ConfigConstants.CONFIG_KEY_URL);
        
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
     * Remove remote refs (branches) from the repository and the config file
     * This is based on DeleteBranchCommand but this removes all branch entries from the config file
     * @param url The remote ref URL
     */
    public void removeRemoteRefs(String url) throws IOException, GitAPIException {
        // Delete all (local) remote branch refs *before* setting the remote
        StoredConfig config = getRepository().getConfig();
        boolean changed = false;

        // Get all remote branch refs
        for(Ref ref : branchList().setListMode(ListMode.REMOTE).call()) {
            // Delete the ref of the remote branch
            RefUpdate refUpdate = getRepository().updateRef(ref.getName());
            refUpdate.setForceUpdate(true);
            refUpdate.setRefLogMessage("branch deleted", false); //$NON-NLS-1$

            Result deleteResult = refUpdate.delete();
            switch(deleteResult) {
                case IO_FAILURE, LOCK_FAILURE, REJECTED -> {
                    throw new IOException("Failed to delete remote ref: " + ref.getName()); //$NON-NLS-1$
                }
                default -> {
                    // Remove branch entry from config file
                    String shortName = getRepository().shortenRemoteBranchName(ref.getName());
                    if(shortName != null) {
                        changed |= config.removeSection(ConfigConstants.CONFIG_BRANCH_SECTION, shortName);
                    }
                }
            }
        }

        if(changed) {
            config.save();
        }
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
        return commit != null && isObjectIdAtHead(commit);
    }

    /**
     * Return true if the given Ref is equal to the HEAD position
     */
    public boolean isRefAtHead(Ref ref) throws IOException {
        return ref != null && isObjectIdAtHead(ref.getObjectId());
    }
    
    /**
     * Return true if the given ObjectId is equal to the HEAD position
     */
    public boolean isObjectIdAtHead(ObjectId objectId) throws IOException {
        ObjectId headID = getRepository().resolve(RepoConstants.HEAD);
        return headID != null && objectId != null && headID.equals(objectId);
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
        return RepoConstants.ORIGIN + "/" + getRepository().getBranch();
    }
    
    /**
     * Return a map of tags mapping a commit Id to a list of tag short names
     */
    public Map<String, List<String>> getTagsMap() throws GitAPIException, IOException {
        Map<String, List<String>> tagMap = new HashMap<>();
        
        for(Ref tagRef : tagList().call()) {
            ObjectId tagObjectId = getTagCommitId(tagRef);
            if(tagObjectId != null) {
                // Get the map entry or create a new one
                List<String> tags = tagMap.computeIfAbsent(tagObjectId.getName(), commitId -> new ArrayList<>());
                // Add the tag name
                tags.add(Repository.shortenRefName(tagRef.getName()));
            }
        }
        
        return tagMap;
    }
    
    /**
     * Get the ObjectId for a tag's commit, or null if not found
     */
    public ObjectId getTagCommitId(Ref tagRef) throws IOException {
        // Peel annotated tag to get the tagged object
        ObjectId tagObjectId = getRepository().getRefDatabase().peel(tagRef).getPeeledObjectId();
        
        // Lightweight tag, directly points to the commit
        if(tagObjectId == null) {
            tagObjectId = tagRef.getObjectId();
        }
        
        return tagObjectId;
    }
    
    /**
     * Delete tags
     * @param tagNames Any number of tag names. For example, "refs/tags/tagName"
     * @return a list of the result of full tag names deleted
     */
    public List<String> deleteTags(String... tagNames) throws GitAPIException {
        // Delete local tag refs
        return tagDelete().setTags(tagNames).call();
    }
    
    /**
     * Delete remote tags by pushing to repo
     * @param tagNames Local type ref like "refs/tags/tagName"
     * @return The first PushResult from the call.
     *         As we're only pushing to one remote URI there should only be one PushResult
     */
    public PushResult deleteRemoteTags(CredentialsProvider credentialsProvider, ProgressMonitor monitor, String... tagNames) throws GitAPIException {
        RefSpec[] refSpecs = Arrays.stream(tagNames).map(tagName -> new RefSpec(":" + tagName)).toArray(RefSpec[]::new);
        
        Iterable<PushResult> results = push()
                .setCredentialsProvider(credentialsProvider)
                .setRefSpecs(refSpecs)
                .setRemote(RepoConstants.ORIGIN)
                .setProgressMonitor(monitor)
                .call();
        
        return results.iterator().next(); // Get the first one
    }
    
    /**
     * Get the latest (last) commit at HEAD, or null if it can't be located.
     */
    public RevCommit getLatestCommit() throws IOException {
        ObjectId headID = getRepository().resolve(RepoConstants.HEAD);
        if(headID != null) {
            try(RevWalk revWalk = new RevWalk(getRepository())) {
                return revWalk.parseCommit(headID);
            }
        }
        return null;
    }
    
    /**
     * Return true if there are 2 or more commits for current HEAD
     */
    public boolean hasMoreThanOneCommit() throws IOException, GitAPIException {
        return getCommitCount(2) > 1;
    }
    
    /**
     * Return the commit log count for the current HEAD branch
     * This is expensive and shouldn't be called to often
     */
    public int getCommitCount() throws IOException, GitAPIException {
        return getCommitCount(-1);
    }
    
    private int getCommitCount(int maxCount) throws IOException, GitAPIException {
        int count = 0;
        
        try(RevWalk revWalk = (RevWalk)log().setMaxCount(maxCount).call()) {
            revWalk.setRetainBody(false);
            while(revWalk.next() != null) {
                count++;
            }
        }
        
        return count;
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
            return getFileContents(path, commit, preserveEol);
        }
    }
    
    /**
     * Return the contents of a file as a byte stream in the repo given its RevCommit
     * @param path is the path to the file
     * @param commit the commit to extract from
     * @param preserveEol if true EOL characters in text files are set according to the repo's "autocrlf" setting.
     *                    On Windows this will be CRLF, else LF
     * @return The file contents or null if not found
     */
    public byte[] getFileContents(String path, RevCommit commit, boolean preserveEol) throws IOException {
        try(TreeWalk treeWalk = new TreeWalk(getRepository())) {
            treeWalk.addTree(commit.getTree());
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
        
        if(!RepoConstants.ORIGIN.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE))) {
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,  ConfigConstants.CONFIG_KEY_REMOTE, RepoConstants.ORIGIN);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE, RepoConstants.R_HEADS + branchName);
            config.save();
        }
    }
    
    /**
     * If there's a crash, exception or whatever the lock file remains and needs to be deleted
     * especially before calling the AddCommand
     */
    private void checkLockFile() {
        File lockFile = new File(getRepository().getDirectory(), "index.lock");
        if(lockFile.exists() && lockFile.canWrite()) {
            lockFile.delete();
        }
    }
}
