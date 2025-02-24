/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Status of Branches
 * 
 * @author Phillip Beauvoir
 */
public class BranchStatus {
    
    private Map<String, BranchInfo> infos = new HashMap<>();
    
    private BranchInfo currentLocalBranchInfo;
    private BranchInfo currentRemoteBranchInfo;
    
    /**
     * BranchStatus is a list of all branches in the repo, local and remote, as a list of BranchInfo objects.
     * @param fullStatus if true all BranchInfo status info is calculated. Setting this to false can mean a more lightweight instance.
     */
    public BranchStatus(File repoFolder, boolean fullStatus) throws IOException, GitAPIException {
        try(Git git = Git.open(repoFolder)) {
            Repository repository = git.getRepository();

            // Get all known branches
            for(Ref ref : git.branchList().setListMode(ListMode.ALL).call()) {
                BranchInfo info = new BranchInfo(repository, ref, fullStatus);
                infos.put(info.getFullName(), info);
            }
            
            // Get current local branch
            String head = repository.getFullBranch();
            if(head != null) {
                currentLocalBranchInfo = infos.get(head);
            }
            
            // Get current remote branch if there is one (can be null)
            if(currentLocalBranchInfo != null) {
                String remoteName = currentLocalBranchInfo.getRemoteBranchNameFor();
                currentRemoteBranchInfo = infos.get(remoteName);
            }
        }
    }
    
    /**
     * @return All branches
     */
    public List<BranchInfo> getAllBranches() {
        return new ArrayList<BranchInfo>(infos.values());
    }

    /**
     * @return A union of local branches and remote branches that we are not tracking
     */
    public List<BranchInfo> getLocalAndUntrackedRemoteBranches() {
        return infos.values().stream()
                .filter(info -> info.isLocal()                         // All local branches or
                        || (info.isRemote() && !info.hasLocalRef() ))  // All remote branches that don't have a local ref
                .collect(Collectors.toList());
    }
    
    /**
     * @return All local branches
     */
    public List<BranchInfo> getLocalBranches() {
        return infos.values().stream()
                .filter(info -> info.isLocal())
                .collect(Collectors.toList());
    }
    
    /**
     * @return All remote branches
     */
    public List<BranchInfo> getRemoteBranches() {
        return infos.values().stream()
                .filter(info -> info.isRemote())
                .collect(Collectors.toList());
    }
    
    public BranchInfo getCurrentLocalBranchInfo() {
        return currentLocalBranchInfo;
    }
    
    public BranchInfo getCurrentRemoteBranchInfo() {
        return currentRemoteBranchInfo;
    }
}
