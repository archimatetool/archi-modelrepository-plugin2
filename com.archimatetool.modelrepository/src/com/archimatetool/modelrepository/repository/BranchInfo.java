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
import org.eclipse.jgit.lib.Constants;
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
@SuppressWarnings("nls")
public class BranchInfo {
    
    private final static String LOCAL_PREFIX = Constants.R_HEADS;
    private final static String REMOTE_PREFIX = Constants.R_REMOTES + IRepositoryConstants.ORIGIN + "/";

    private Ref ref;
    
    private boolean isRemoteDeleted;
    private boolean isCurrentBranch;
    private boolean hasLocalRef;
    private boolean hasRemoteRef;
    private boolean hasUnpushedCommits;
    private boolean hasRemoteCommits;
    private boolean isMerged;
    
    private RevCommit latestCommit;
    
    private File repoDir; 
    
    /**
     * Get Current Local BranchInfo.
     * @param fullStatus if true all BranchInfo status info is calculated. Setting this to false can mean a more lightweight instance.
     */
    public static BranchInfo currentLocalBranchInfo(File repoDir, boolean fullStatus) throws IOException, GitAPIException {
        try(Repository repository = Git.open(repoDir).getRepository()) {
            return new BranchInfo(repository, repository.exactRef(Constants.HEAD).getTarget(), fullStatus);
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
            Ref ref = repository.findRef(getFullName());  // Ref will be a different object with a new Repository instance so renew it
            init(repository, ref, true);
        }
    }
    
    /**
     * Initialise this BranchInfo from Ref and the Repository
     */
    private void init(Repository repository, Ref ref, boolean fullStatus) throws IOException, GitAPIException {
        this.ref = ref;
        
        hasLocalRef = repository.findRef(getLocalBranchNameFor()) != null;
        hasRemoteRef = repository.findRef(getRemoteBranchNameFor()) != null;
        isCurrentBranch = getFullName().equals(repository.getFullBranch());

        // If fullStatus is true get this information
        if(fullStatus) {
            getCommitStatus(repository);
            getIsRemoteDeleted(repository);
            getRevWalkStatus(repository);
        }
    }
    
    public Ref getRef() {
        return ref;
    }
    
    public String getFullName() {
        return ref.getName();
    }
    
    public String getShortName() {
        String branchName = getFullName();
        
        if(branchName.startsWith(LOCAL_PREFIX)) {
            return branchName.substring(LOCAL_PREFIX.length());
        }
        if(branchName.startsWith(REMOTE_PREFIX)) {
            return branchName.substring(REMOTE_PREFIX.length());
        }
        
        return branchName;
    }
    
    public boolean isLocal() {
        return getFullName().startsWith(LOCAL_PREFIX);
    }

    public boolean isRemote() {
        return getFullName().startsWith(REMOTE_PREFIX);
    }

    public boolean hasLocalRef() {
        return hasLocalRef;
    }

    public boolean hasRemoteRef() {
        return hasRemoteRef;
    }

    public boolean isCurrentBranch() {
        return isCurrentBranch;
    }
    
    public String getRemoteBranchNameFor() {
        return REMOTE_PREFIX + getShortName();
    }
    
    public String getLocalBranchNameFor() {
        return LOCAL_PREFIX + getShortName();
    }
    
    public boolean isMainBranch() {
        return IRepositoryConstants.MAIN.equals(getShortName());
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof BranchInfo) &&
                repoDir.equals(((BranchInfo)obj).repoDir) &&
                getFullName().equals(((BranchInfo)obj).getFullName());
    }
    
    // ======================================================================================
    // These status methods are a little expensive so are only called when fullStatus is true
    // ======================================================================================
    
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

    /*
     * Figure out whether the remote branch has been deleted
     * 1. We have a local branch ref
     * 2. We are tracking it
     * 3. But it does not have a remote branch ref
     */
    private void getIsRemoteDeleted(Repository repository) throws IOException {
        if(isRemote()) {
            isRemoteDeleted = false;
        }
        
        // Is it being tracked?
        BranchConfig branchConfig = new BranchConfig(repository.getConfig(), getShortName());
        boolean isBeingTracked = branchConfig.getRemoteTrackingBranch() != null;
        
        // Does it have a remote ref?
        boolean hasNoRemoteBranchFor = repository.findRef(getRemoteBranchNameFor()) == null;
        
        // Is being tracked but no remote ref
        isRemoteDeleted = isBeingTracked && hasNoRemoteBranchFor;
    }

    /**
     * Get status of this branch from a RevWalk
     * This will get the latest commit for this branch
     * and whether this branch is merged into another
     */
    private void getRevWalkStatus(Repository repository) throws GitAPIException, IOException {
        try(RevWalk revWalk = new RevWalk(repository)) {
            // Get the latest commit for this branch
            latestCommit = revWalk.parseCommit(ref.getObjectId());

            // If this is the master branch isMerged is true
            if(isMainBranch()) {
                isMerged = true;
            }
            // Else this is another branch
            else {
                // Get ALL other branch refs
                List<Ref> otherRefs = Git.wrap(repository).branchList().setListMode(ListMode.ALL).call();
                otherRefs.remove(ref); // remove this one

                // Don't need this for the general RevWalk
                revWalk.setRetainBody(false);
                
                // In-built method
                List<Ref> refs = RevWalkUtils.findBranchesReachableFrom(latestCommit, revWalk, otherRefs);
                isMerged = !refs.isEmpty(); // If there are other reachable branches then this is merged

                /* Another way to do this...
                for(Ref otherRef : otherRefs) {
                    // Get the other branch's latest commit
                    RevCommit otherHead = revWalk.parseCommit(otherRef.getObjectId());

                    // If this head is an ancestor of, or the same as, the other head then this is merged
                    if(revWalk.isMergedInto(latestCommit, otherHead)) {
                        isMerged = true;
                        break;
                    }
                }
                */
            }

            revWalk.dispose();
        } // close the RevWalk in all cases
    }
    
    private void getCommitStatus(Repository repository) throws IOException {
        BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(repository, getShortName());
        if(trackingStatus != null) {
            hasUnpushedCommits = trackingStatus.getAheadCount() > 0;
            hasRemoteCommits = trackingStatus.getBehindCount() > 0;
        }
    }
}