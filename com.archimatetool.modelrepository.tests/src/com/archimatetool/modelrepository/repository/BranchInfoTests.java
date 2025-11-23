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
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
import com.archimatetool.modelrepository.testsupport.GitHelper;


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
    public void currentLocalBranchInfo_Main() throws Exception {
        RevCommit commit = utils.commitChanges("Commit 1", false);
        BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder(), Option.ALL).orElse(null);
        checkLocalBranchInfo(branchInfo, commit, RepoConstants.MAIN);
        assertTrue(branchInfo.isPrimaryBranch());
    }
    
    @Test
    public void currentLocalBranchInfo_Branch() throws Exception {
        RevCommit commit = utils.commitChanges("Commit 1", false);
        utils.checkout().setName("branch").setCreateBranch(true).call();
        BranchInfo branchInfo = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder(), Option.ALL).orElse(null);
        checkLocalBranchInfo(branchInfo, commit, "branch");
        assertFalse(branchInfo.isPrimaryBranch());
    }
    
    @Test
    public void currentRemoteFullBranchInfo() throws Exception {
        utils.setRemote(GitHelper.createBareRepository().getAbsolutePath());
        RevCommit commit = utils.commitChanges("Commit 1", false);
        utils.pushToRemote(null, null);
        
        BranchInfo branchInfo = BranchInfo.currentRemoteBranchInfo(repo.getWorkingFolder(), Option.ALL).orElseThrow();
        checkRemoteBranchInfo(branchInfo, commit, RepoConstants.MAIN);
        assertTrue(branchInfo.isPrimaryBranch());
        
        // Add another commit but not pushed
        RevCommit commit2 = utils.commitChanges("Commit 2", false);
        branchInfo.refresh();
        assertTrue(branchInfo.hasUnpushedCommits());
        
        // Push it
        utils.pushToRemote(null, null);
        branchInfo.refresh();
        assertEquals(commit2, branchInfo.getLatestCommit().orElse(null));
        
        // Undo last commit
        utils.resetToRef("HEAD^");
        branchInfo.refresh();
        assertTrue(branchInfo.hasRemoteCommits());
    }
    
    private void checkLocalBranchInfo(BranchInfo branchInfo, RevCommit expectedCommit, String expectedBranch) {
        assertFalse(branchInfo.getRef().isSymbolic());
        assertEquals(RepoConstants.R_HEADS + expectedBranch, branchInfo.getFullName());
        assertEquals(expectedCommit, branchInfo.getLatestCommit().orElse(null));
        assertEquals(RepoConstants.R_HEADS + expectedBranch, branchInfo.getLocalBranchName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + expectedBranch, branchInfo.getRemoteBranchName());
        assertEquals(expectedBranch, branchInfo.getShortName());
        assertEquals(repo.getWorkingFolder(), branchInfo.getWorkingFolder());
        assertTrue(branchInfo.hasLocalRef());
        assertFalse(branchInfo.hasRemoteRef());
        assertFalse(branchInfo.hasRemoteCommits());
        assertFalse(branchInfo.hasUnpushedCommits());
        assertTrue(branchInfo.isCurrentBranch());
        assertTrue(branchInfo.isLocal());
        assertTrue(branchInfo.isMerged());
        assertTrue(branchInfo.isRefAtHead());
        assertFalse(branchInfo.isRemote());
        assertFalse(branchInfo.isRemoteDeleted());
    }

    private void checkRemoteBranchInfo(BranchInfo branchInfo, RevCommit expectedCommit, String expectedBranch) {
        assertFalse(branchInfo.getRef().isSymbolic());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + expectedBranch, branchInfo.getFullName());
        assertEquals(expectedCommit, branchInfo.getLatestCommit().orElse(null));
        assertEquals(RepoConstants.R_HEADS + expectedBranch, branchInfo.getLocalBranchName());
        assertEquals(RepoConstants.R_REMOTES_ORIGIN + expectedBranch, branchInfo.getRemoteBranchName());
        assertEquals(expectedBranch, branchInfo.getShortName());
        assertEquals(repo.getWorkingFolder(), branchInfo.getWorkingFolder());
        assertTrue(branchInfo.hasLocalRef());
        assertTrue(branchInfo.hasRemoteRef());
        assertFalse(branchInfo.hasRemoteCommits());
        assertFalse(branchInfo.hasUnpushedCommits());
        assertFalse(branchInfo.isCurrentBranch());
        assertFalse(branchInfo.isLocal());
        assertTrue(branchInfo.isMerged());
        assertTrue(branchInfo.isRefAtHead());
        assertTrue(branchInfo.isRemote());
        assertFalse(branchInfo.isRemoteDeleted());
    }
    
    @Test
    public void testHashCode() throws Exception {
        utils.commitChanges("Commit 1", false);
        BranchInfo branchInfo1 = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder()).orElse(null);
        BranchInfo branchInfo2 = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder()).orElse(null);
        assertEquals(branchInfo1.hashCode(), branchInfo2.hashCode());
    }
    
    @Test
    public void testEquals() throws Exception {
        utils.commitChanges("Commit 1", false);
        BranchInfo branchInfo1 = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder()).orElse(null);
        BranchInfo branchInfo2 = BranchInfo.currentLocalBranchInfo(repo.getWorkingFolder()).orElse(null);
        assertEquals(branchInfo1, branchInfo2);
    }
}
