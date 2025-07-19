/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;

/**
 * Tag Info
 * 
 * @author Phillip Beauvoir
 */
public class TagInfo {
    
    private File repoDir; 
    private Ref ref;
    
    private RevTag tag;
    private RevCommit commit;
    
    private boolean isOrphaned;
    
    public static List<TagInfo> getTags(File repoFolder) throws IOException, GitAPIException {
        List<TagInfo> list = new ArrayList<>();
        
        try(Git git = Git.open(repoFolder)) {
            List<Ref> tags = git.tagList().call();
            if(!tags.isEmpty()) {
                // Get all branches now
                List<Ref> branchRefs = git.branchList().setListMode(ListMode.ALL).call();
                
                // Use two RevWalks, one for the commit and tag, and one for getIsOrphaned
                try(RevWalk revWalk1 = new RevWalk(git.getRepository()); RevWalk revWalk2 = new RevWalk(git.getRepository())) {
                    revWalk2.setRetainBody(false); // This one doesn't need to retain body
                    
                    for(Ref tagRef : tags) {
                        list.add(new TagInfo(git.getRepository(), tagRef, revWalk1, revWalk2, branchRefs));
                    }
                }
            }
        }
        
        return list;
    }
    
    TagInfo(Repository repository, Ref ref, RevWalk revWalk1, RevWalk revWalk2,  List<Ref> branchRefs) throws IOException {
        this.ref = ref;
        repoDir = repository.getWorkTree();
        
        // Use a separate RevWalk for these
        commit = getCommit(repository, revWalk1);
        tag = getRevTag(repository, revWalk1);
        
        // And a separate RevWalk for this
        isOrphaned = getIsOrphaned(repository, revWalk2, branchRefs);
    }
    
    public File getWorkingFolder() {
        return repoDir;
    }
    
    public Ref getRef() {
        return ref;
    }
    
    public RevTag getTag() {
        return tag;
    }
    
    public boolean isAnnotated() {
        return tag != null;
    }
    
    public String getFullName() {
        return ref.getName();
    }
    
    public String getShortName() {
        return getFullName().substring(RepoConstants.R_TAGS.length());
    }
    
    public RevCommit getCommit() {
        return commit;
    }
    
    public boolean isOrphaned() {
        return isOrphaned;
    }
    
    private boolean getIsOrphaned(Repository repository, RevWalk revWalk, List<Ref> branchRefs) throws IOException {
        // If there are no other reachable branches from the tag's commit
        return commit != null ? RevWalkUtils.findBranchesReachableFrom(commit, revWalk, branchRefs).isEmpty() : true;
    }

    /**
     * If this is an annotated tag return RevTag, else null
     */
    private RevTag getRevTag(Repository repository, RevWalk revWalk) throws IOException {
        return ref.getObjectId() != null ? (revWalk.parseAny(ref.getObjectId()) instanceof RevTag revTag ? revTag : null) : null;
    }

    private RevCommit getCommit(Repository repository, RevWalk revWalk) throws IOException {
        return ref.getObjectId() != null ? revWalk.parseCommit(ref.getObjectId()) : null;
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof TagInfo other
                && Objects.equals(repoDir, other.repoDir) && Objects.equals(getFullName(), other.getFullName());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(repoDir, getFullName());
    }

}