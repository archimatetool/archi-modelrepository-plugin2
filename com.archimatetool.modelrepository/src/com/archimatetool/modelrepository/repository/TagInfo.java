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
            // Get all branches now
            List<Ref> branchRefs = git.branchList().setListMode(ListMode.ALL).call();
            
            try(RevWalk revWalk = new RevWalk(git.getRepository())) {
                for(Ref tagRef : git.tagList().call()) {
                    list.add(new TagInfo(git.getRepository(), tagRef, revWalk, branchRefs));
                }
            }
        }
        
        return list;
    }
    
    TagInfo(Repository repository, Ref ref, RevWalk revWalk, List<Ref> branchRefs) throws IOException {
        repoDir = repository.getWorkTree();
        this.ref = ref.getTarget();
        
        commit = getCommit(repository, revWalk);
        tag = getRevTag(repository, revWalk);
        isOrphaned = getIsOrphaned(repository, revWalk, branchRefs);
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
        // Check if any branch is reachable from this tag
        for(Ref branchRef : branchRefs) {
            // Get commit for branch
            RevCommit branchCommit = revWalk.parseCommit(branchRef.getObjectId());
            // Reachable so return false
            if(revWalk.isMergedInto(commit, branchCommit)) {
                return false;
            }
        }
        
        // Got here so must be true
        return true;
    }

    /**
     * If this is an annotated tag return RevTag, else null
     */
    private RevTag getRevTag(Repository repository, RevWalk revWalk) throws IOException {
        return revWalk.parseAny(ref.getObjectId()) instanceof RevTag revTag ? revTag : null;
    }

    private RevCommit getCommit(Repository repository, RevWalk revWalk) throws IOException {
        return revWalk.parseCommit(ref.getObjectId());
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof TagInfo other
                && Objects.equals(repoDir, other.repoDir) && Objects.equals(getFullName(), other.getFullName());
    }
    
}