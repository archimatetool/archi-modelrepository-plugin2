/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class BranchStatusTests {
    
    private IArchiRepository repo;
    private GitUtils utils;
    private BranchStatus status;
    
    @BeforeEach
    public void runOnceBeforeEachTest() throws Exception {
        repo = GitHelper.createNewRepository().init();
        utils = GitUtils.open(repo.getGitFolder());
        utils.setRemote(GitHelper.createBareRepository().getAbsolutePath());
        
        utils.commitChanges("Commit 1", false);
        utils.pushToRemote(null, null);
        
        utils.branchCreate().setName("branch").call();
        utils.checkout().setName(RepoConstants.R_HEADS + "branch").call();
        utils.pushToRemote(null, null);
        
        utils.branchCreate().setName("local").call();

        utils.checkout().setName(RepoConstants.R_HEADS_MAIN).call();
        
        status = new BranchStatus(repo.getWorkingFolder(), Option.ALL);
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws IOException {
        utils.close();
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    
    @Test
    public void getAllBranches() throws Exception {
        List<BranchInfo> branchInfos = status.getAllBranches();
        assertEquals(5, branchInfos.size());
        assertEquals(RepoConstants.R_HEADS + "branch", branchInfos.get(0).getFullName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfos.get(1).getFullName());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfos.get(2).getFullName());
        assertEquals(RepoConstants.R_HEADS + "local", branchInfos.get(3).getFullName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + "branch", branchInfos.get(4).getFullName());
    }

    @Test
    public void getLocalAndUntrackedRemoteBranches() throws Exception {
        // Delete local branch
        utils.deleteBranches(false, RepoConstants.R_HEADS + "branch");
        
        status = new BranchStatus(repo.getWorkingFolder(), Option.ALL);
        
        List<BranchInfo> branchInfos = status.getLocalAndUntrackedRemoteBranches();
        assertEquals(3, branchInfos.size());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfos.get(0).getFullName());
        assertEquals(RepoConstants.R_HEADS + "local", branchInfos.get(1).getFullName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + "branch", branchInfos.get(2).getFullName());
    }
    
    @Test
    public void getLocalBranches() throws Exception {
        List<BranchInfo> branchInfos = status.getLocalBranches();
        assertEquals(3, branchInfos.size());
        assertEquals(RepoConstants.R_HEADS + "branch", branchInfos.get(0).getFullName());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfos.get(1).getFullName());
        assertEquals(RepoConstants.R_HEADS + "local", branchInfos.get(2).getFullName());
    }
    
    @Test
    public void getRemoteBranches() throws Exception {
        List<BranchInfo> branchInfos = status.getRemoteBranches();
        assertEquals(2, branchInfos.size());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfos.get(0).getFullName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + "branch", branchInfos.get(1).getFullName());
    }

    @Test
    public void getCurrentLocalBranchInfo() throws Exception {
        BranchInfo branchInfo = status.getCurrentLocalBranchInfo().orElseThrow();
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfo.getFullName());
    }

    @Test
    public void getCurrentRemoteBranchInfo() throws Exception {
        BranchInfo branchInfo = status.getCurrentRemoteBranchInfo().orElseThrow();
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfo.getFullName());
    }

    @Test
    public void find() {
        assertTrue(status.find(RepoConstants.R_HEADS + "bogus").isEmpty());
        
        assertNotNull(status.find(RepoConstants.R_HEADS_MAIN));
        assertNotNull(status.find(RepoConstants.R_REMOTES_ORIGIN_MAIN));
        
        assertNotNull(status.find(RepoConstants.R_HEADS + "branch"));
        assertNotNull(status.find(RepoConstants.R_REMOTES_ORIGIN + "branch"));
        
        assertNotNull(status.find(RepoConstants.R_HEADS + "local"));
        assertTrue(status.find(RepoConstants.R_REMOTES_ORIGIN + "local").isEmpty());
    }
}
