/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;

/**
 * BranchInfo
 * 
 * @author Phillip Beauvoir
 */
public class BranchInfo {
    
    private File repoDir; 
    private Ref ref;
    
    private boolean isCurrentBranch;
    private boolean hasLocalRef;
    private boolean hasRemoteRef;
    private boolean isRefAtHead;
    private boolean isPrimaryBranch;
    private RevCommit latestCommit;
    private boolean isRemoteDeleted;
    private boolean hasUnpushedCommits;
    private boolean hasRemoteCommits;
    private boolean isMerged;
    private Option[] options;
    
    /**
     * Extra options from the BranchInfo
     */
    public enum Option {
        ALL,
        ISREMOTEDELETED,
        COMMIT_STATUS,
        ISMERGED
    }
    
    /**
     * @return A BranchInfo for the current local branch ("HEAD") or null if ref not found
     */
    public static BranchInfo currentLocalBranchInfo(File repoDir, Option... options) throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            Ref ref = repository.exactRef(RepoConstants.HEAD);
            return ref != null ? new BranchInfo(repository, ref, null, options) : null;
        }
    }
    
    /**
     * @return A BranchInfo for the current remote branch ("refs/remotes/origin/branch") or null if ref not found
     *         (the current branch is not tracking a remote branch)
     */
    public static BranchInfo currentRemoteBranchInfo(File repoDir, Option... options) throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            Ref ref = repository.exactRef(RepoConstants.R_REMOTES_ORIGIN + repository.getBranch());
            return ref != null ? new BranchInfo(repository, ref, null, options) : null;
        }
    }

    BranchInfo(Repository repository, Ref ref, RevWalk revWalk, Option... options) throws IOException, GitAPIException {
        repoDir = repository.getWorkTree();
        this.options = options;
        init(repository, ref, revWalk);
    }
    
    /**
     * Initialise this BranchInfo from Ref and the Repository
     */
    protected void init(Repository repository, Ref ref, RevWalk revWalk) throws IOException, GitAPIException {
        this.ref = ref.getTarget(); // Important - get the target Ref in case it's a symbolic Ref

        // Core queries
        hasLocalRef = repository.exactRef(getLocalBranchName()) != null;
        hasRemoteRef = repository.exactRef(getRemoteBranchName()) != null;
        isCurrentBranch = getFullName().equals(repository.getFullBranch());
        isPrimaryBranch = isPrimaryBranch(repository);
        isRefAtHead = GitUtils.wrap(repository).isRefAtHead(ref);
        latestCommit = getLatestCommit(repository);
        
        // Optional and more expensive queries
        if(options != null && options.length > 0) {
            EnumSet<Option> set = EnumSet.of(options[0], options);
            
            if(set.contains(Option.ISREMOTEDELETED) || set.contains(Option.ALL)) {
                isRemoteDeleted = isRemoteDeleted(repository);
            }
            
            if(set.contains(Option.COMMIT_STATUS) || set.contains(Option.ALL)) {
                updateCommitStatus(repository);
            }

            if(set.contains(Option.ISMERGED) || set.contains(Option.ALL)) {
                isMerged = isMergedIntoOtherBranches(repository, revWalk);
            }
        }
    }
    
    public File getWorkingFolder() {
        return repoDir;
    }
    
    public Ref getRef() {
        return ref;
    }
    
    public String getFullName() {
        return ref.getName();
    }
    
    public String getShortName() {
        String branchName = getFullName();
        
        if(branchName.startsWith(RepoConstants.R_HEADS)) {
            return branchName.substring(RepoConstants.R_HEADS.length());
        }
        if(branchName.startsWith(RepoConstants.R_REMOTES_ORIGIN)) {
            return branchName.substring(RepoConstants.R_REMOTES_ORIGIN.length());
        }
        
        return branchName;
    }
    
    public boolean isLocal() {
        return getFullName().startsWith(RepoConstants.R_HEADS);
    }

    public boolean isRemote() {
        return getFullName().startsWith(RepoConstants.R_REMOTES_ORIGIN);
    }

    public boolean hasLocalRef() {
        return hasLocalRef;
    }

    public boolean hasRemoteRef() {
        return hasRemoteRef;
    }
    
    /**
     * @return true if the Ref of this branch is equal to the HEAD position
     */
    public boolean isRefAtHead() {
        return isRefAtHead;
    }

    public boolean isCurrentBranch() {
        return isCurrentBranch;
    }
    
    /**
     * @return true if this branch is "main" or "master"
     */
    public boolean isPrimaryBranch() {
        return isPrimaryBranch;
    }
    
    public String getRemoteBranchName() {
        return RepoConstants.R_REMOTES_ORIGIN + getShortName();
    }
    
    public String getLocalBranchName() {
        return RepoConstants.R_HEADS + getShortName();
    }
    
    public RevCommit getLatestCommit() {
        return latestCommit;
    }

    public boolean isMerged() {
        return isMerged;
    }
    
    public boolean isRemoteDeleted() {
        return isRemoteDeleted;
    }
    
    public boolean hasRemoteCommits() {
        return hasRemoteCommits;
    }
    
    public boolean hasUnpushedCommits() {
        return hasUnpushedCommits;
    }
    
    public void refresh() throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            Ref newRref = repository.exactRef(getFullName());  // Ref will be a different object with a new Repository instance so renew it
            init(repository, newRref, null);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof BranchInfo other
                && Objects.equals(repoDir, other.repoDir) && Objects.equals(getFullName(), other.getFullName());
    }
    
    // ======================================================================================
    // These methods are a little expensive so are done once on initialisation
    // ======================================================================================

    /**
     * If this is "main" or "master" then return true if it is the primary branch
     */
    private boolean isPrimaryBranch(Repository repository) throws IOException {
        return RepoConstants.MAIN.equals(getShortName()) || 
               (RepoConstants.MASTER.equals(getShortName()) && RepoConstants.MASTER.equals(GitUtils.wrap(repository).getPrimaryBranch()));
    }

    /**
     * Get the latest commit
     */
    private RevCommit getLatestCommit(Repository repository) throws IOException {
        try(RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(ref.getObjectId());
        }
    }
    
    /**
     * Figure out whether the remote branch has been deleted
     * 1. We have a local branch ref
     * 2. We are tracking it
     * 3. But it does not have a remote branch ref
     */
    private boolean isRemoteDeleted(Repository repository) throws IOException {
        if(isRemote()) {
            return false;
        }
        
        // Is it being tracked?
        boolean isTracked = new BranchConfig(repository.getConfig(), getShortName()).getRemoteTrackingBranch() != null;
        
        // Does it have a remote ref?
        boolean noRemote = repository.exactRef(getRemoteBranchName()) == null;
        
        // Is being tracked but no remote ref
        return isTracked && noRemote;
    }

    /**
     * Get commit tracking status of ahead/behind count
     */
    private void updateCommitStatus(Repository repository) throws IOException {
        BranchTrackingStatus status = BranchTrackingStatus.of(repository, getShortName());
        if(status != null) {
            hasUnpushedCommits = status.getAheadCount() > 0;
            hasRemoteCommits = status.getBehindCount() > 0;
        }
    }
    
    /**
     * Update merge status of this branch from a RevWalk
     * This is slow and expensive so re-use a RevWalk if called multiple times
     * @param revWalk if this is not null use it for RevWalkUtils.findBranchesReachableFrom
     */
    private boolean isMergedIntoOtherBranches(Repository repository, RevWalk revWalk) throws GitAPIException, IOException {
        if(isPrimaryBranch()) {
            return true;
        }
        
        // Get ALL other branch refs
        List<Ref> otherRefs = Git.wrap(repository).branchList()
                                                  .setListMode(ListMode.ALL)
                                                  .call();
        
        // Remove this Ref
        otherRefs.remove(ref);
        
        try(RevWalk walk = revWalk != null ? revWalk : new RevWalk(repository)) {
            // If there are other reachable branches then this is merged
            return !RevWalkUtils.findBranchesReachableFrom(latestCommit, walk, otherRefs).isEmpty();
        }
    }
}