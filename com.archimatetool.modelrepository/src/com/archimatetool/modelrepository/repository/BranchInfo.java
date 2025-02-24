/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.BranchConfig;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * BranchInfo
 * 
 * @author Phillip Beauvoir
 */
public class BranchInfo {
    
    private Ref ref;
    
    private boolean isRemoteDeleted;
    private boolean isCurrentBranch;
    private boolean hasLocalRef;
    private boolean hasRemoteRef;
    private boolean isRefAtHead;
    private boolean hasUnpushedCommits;
    private boolean hasRemoteCommits;
    private boolean isMerged;
    private boolean isPrimaryBranch;
    
    private RevCommit latestCommit;
    
    private File repoDir; 
    
    private boolean hasFullStatus;
    
    /**
     * Get Current Local BranchInfo
     * @param fullStatus if true all BranchInfo status info is calculated. Setting this to false can mean a more lightweight instance.
     */
    public static BranchInfo currentLocalBranchInfo(File repoDir, boolean fullStatus) throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            return new BranchInfo(repository, repository.exactRef(RepoConstants.HEAD).getTarget(), fullStatus);
        }
    }
    
    /**
     * Get Current Remote BranchInfo or null if the current branch is not tracking a remote branch
     * @param fullStatus if true all BranchInfo status info is calculated. Setting this to false can mean a more lightweight instance.
     */
    public static BranchInfo currentRemoteBranchInfo(File repoDir, boolean fullStatus) throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            Ref remoteRef = repository.exactRef(RepoConstants.R_REMOTES_ORIGIN + repository.getBranch());
            return remoteRef != null ? new BranchInfo(repository, remoteRef.getTarget(), fullStatus) : null;
        }
    }

    BranchInfo(Repository repository, Ref ref, boolean fullStatus) throws IOException, GitAPIException {
        repoDir = repository.getWorkTree();
        init(repository, ref, fullStatus);
    }
    
    /**
     * Refresh this BranchInfo with full updated information
     */
    public void refresh() throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            Ref ref = repository.exactRef(getFullName());  // Ref will be a different object with a new Repository instance so renew it
            init(repository, ref, true);
        }
    }
    
    /**
     * Initialise this BranchInfo from Ref and the Repository
     */
    private void init(Repository repository, Ref ref, boolean fullStatus) throws IOException, GitAPIException {
        this.ref = ref.getTarget(); // Important! Get the target in case it's a symbolic Ref
        
        hasFullStatus = fullStatus;
        
        hasLocalRef = repository.exactRef(getLocalBranchNameFor()) != null;
        hasRemoteRef = repository.exactRef(getRemoteBranchNameFor()) != null;
        isCurrentBranch = getFullName().equals(repository.getFullBranch());
        isRefAtHead = GitUtils.wrap(repository).isRefAtHead(ref);

        // If fullStatus is true get this information
        if(fullStatus) {
            getIsPrimaryBranch(repository);
            getCommitStatus(repository);
            getIsRemoteDeleted(repository);
            getRevWalkStatus(repository);
        }
    }
    
    /**
     * @return True if this BranchInfo has all status info calculated
     */
    public boolean hasFullStatus() {
        return hasFullStatus;
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
    
    public String getRemoteBranchNameFor() {
        return RepoConstants.R_REMOTES_ORIGIN + getShortName();
    }
    
    public String getLocalBranchNameFor() {
        return RepoConstants.R_HEADS + getShortName();
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof BranchInfo branchInfo &&
               repoDir.equals(branchInfo.repoDir) &&
               getFullName().equals(branchInfo.getFullName());
    }
    
    // ======================================================================================
    // These status methods are a little expensive so are only called when fullStatus is true
    // ======================================================================================
    
    public boolean isPrimaryBranch() {
        // If this is "main" then return true
        if(RepoConstants.MAIN.equals(getShortName())) {
            return true;
        }

        return isPrimaryBranch;
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
    
    private void getIsPrimaryBranch(Repository repository) throws IOException {
        // If this is "master" then determine if it is the primary branch
        if(RepoConstants.MASTER.equals(getShortName())) {
            isPrimaryBranch = RepoConstants.MASTER.equals(GitUtils.wrap(repository).getPrimaryBranch());
        }
    }
    
    /*
     * Figure out whether the remote branch has been deleted
     * 1. We have a local branch ref
     * 2. We are tracking it
     * 3. But it does not have a remote branch ref
     */
    private void getIsRemoteDeleted(Repository repository) throws IOException {
        if(isRemote()) {
            isRemoteDeleted = false;
            return;
        }
        
        // Is it being tracked?
        BranchConfig branchConfig = new BranchConfig(repository.getConfig(), getShortName());
        boolean isBeingTracked = branchConfig.getRemoteTrackingBranch() != null;
        
        // Does it have a remote ref?
        boolean hasNoRemoteBranchFor = repository.exactRef(getRemoteBranchNameFor()) == null;
        
        // Is being tracked but no remote ref
        isRemoteDeleted = isBeingTracked && hasNoRemoteBranchFor;
    }

    /**
     * Get status of this branch from a RevWalk
     * This will get the latest commit for this branch
     * and whether this branch is merged into another
     */
    private void getRevWalkStatus(Repository repository) throws GitAPIException, IOException {
        // Get the latest commit for this branch
        try(RevWalk revWalk = new RevWalk(repository)) {
            latestCommit = revWalk.parseCommit(ref.getObjectId());
        }
        
        // If this is the primary branch isMerged is true
        if(isPrimaryBranch()) {
            isMerged = true;
        }
        // Else this is another branch...
        else {
            // Get ALL other branch refs
            List<Ref> otherRefs = Git.wrap(repository).branchList()
                                                      .setContains(ref.getName()) // only the branches that contain this Ref an ancestor are returned
                                                      .setListMode(ListMode.ALL)  // Remote and Local
                                                      .call();
            // Remove this Ref
            otherRefs.remove(ref);
            
            // If there are other reachable branches then this is merged
            isMerged = !otherRefs.isEmpty();
        }
    }
    
    private void getCommitStatus(Repository repository) throws IOException {
        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, getShortName());
        if(trackingStatus != null) {
            hasUnpushedCommits = trackingStatus.getAheadCount() > 0;
            hasRemoteCommits = trackingStatus.getBehindCount() > 0;
        }
    }
}