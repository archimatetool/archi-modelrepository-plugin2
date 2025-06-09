/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.GitHelper;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;


@SuppressWarnings("nls")
public class BranchInfoTests {
    
    private IArchiRepository repo;
    private GitUtils utils;
    
    @BeforeEach
    public void runOnceBeforeEachTest() throws Exception {
        repo = GitHelper.createNewRepository().init();
        utils = GitUtils.open(repo.getGitFolder());
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws IOException {
        utils.close();
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    
    @Test
    public void currentLocalBranchInfo() throws Exception {
        RevCommit commit = utils.commitChanges("Commit 1", false);
        
        BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder(), Option.ALL);
        
        assertFalse(branchInfo.getRef().isSymbolic());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfo.getFullName());
        assertEquals(commit, branchInfo.getLatestCommit());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfo.getLocalBranchName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfo.getRemoteBranchName());
        assertEquals(RepoConstants.MAIN, branchInfo.getShortName());
        assertEquals(repo.getWorkingFolder(), branchInfo.getWorkingFolder());
        assertTrue(branchInfo.hasLocalRef());
        assertFalse(branchInfo.hasRemoteRef());
        assertFalse(branchInfo.hasRemoteCommits());
        assertFalse(branchInfo.hasUnpushedCommits());
        assertTrue(branchInfo.isCurrentBranch());
        assertTrue(branchInfo.isLocal());
        assertTrue(branchInfo.isMerged());
        assertTrue(branchInfo.isPrimaryBranch());
        assertTrue(branchInfo.isRefAtHead());
        assertFalse(branchInfo.isRemote());
        assertFalse(branchInfo.isRemoteDeleted());
    }

    @Test
    public void currentRemoteFullBranchInfo() throws Exception {
        repo.setRemote(GitHelper.createBareRepository());
        RevCommit commit = utils.commitChanges("Commit 1", false);
        utils.pushToRemote(null, null);
        
        BranchInfo branchInfo = BranchInfo.currentRemoteBranchInfo(repo.getWorkingFolder(), Option.ALL);
        
        assertFalse(branchInfo.getRef().isSymbolic());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfo.getFullName());
        assertEquals(commit, branchInfo.getLatestCommit());
        assertEquals(RepoConstants.R_HEADS_MAIN, branchInfo.getLocalBranchName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, branchInfo.getRemoteBranchName());
        assertEquals(RepoConstants.MAIN, branchInfo.getShortName());
        assertEquals(repo.getWorkingFolder(), branchInfo.getWorkingFolder());
        assertTrue(branchInfo.hasLocalRef());
        assertTrue(branchInfo.hasRemoteRef());
        assertFalse(branchInfo.hasRemoteCommits());
        assertFalse(branchInfo.hasUnpushedCommits());
        assertFalse(branchInfo.isCurrentBranch());
        assertFalse(branchInfo.isLocal());
        assertTrue(branchInfo.isMerged());
        assertTrue(branchInfo.isPrimaryBranch());
        assertTrue(branchInfo.isRefAtHead());
        assertTrue(branchInfo.isRemote());
        assertFalse(branchInfo.isRemoteDeleted());
        
        // Add another commit but not pushed
        RevCommit commit2 = utils.commitChanges("Commit 2", false);
        branchInfo.refresh();
        assertTrue(branchInfo.hasUnpushedCommits());
        
        // Push it
        utils.pushToRemote(null, null);
        branchInfo.refresh();
        assertEquals(commit2, branchInfo.getLatestCommit());
        
        // Undo last commit
        utils.resetToRef("HEAD^");
        branchInfo.refresh();
        assertTrue(branchInfo.hasRemoteCommits());
    }
    
}
