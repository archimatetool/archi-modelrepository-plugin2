/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.GitHelper;


@SuppressWarnings("nls")
public class GitUtilsTests {
    
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
    public void commitChanges() throws Exception {
        // Has 1 commit
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        RevCommit commit = utils.commitChanges("Message", false);
        assertEquals("Message", commit.getFullMessage());
        assertEquals(1, utils.getCommitCount());

        // Has 2 commits
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "456", StandardOpenOption.APPEND);
        RevCommit commit2 = utils.commitChanges("Message 2", false);
        assertEquals("Message 2", commit2.getFullMessage());
        assertEquals(2, utils.getCommitCount());

        // Has amended commit (2)
        RevCommit commit3 = utils.commitChanges("Message 3", true);
        assertEquals("Message 3", commit3.getFullMessage());
        assertEquals(2, utils.getCommitCount());
    }
    
    @Test
    public void commitChangesWithManifest_ShouldNotHaveManifest() throws Exception {
        // Should not have a manifest on inital commit. We should add one manually.
        GitHelper.createSimpleModelInTestRepo(repo);
        RevCommit commit = utils.commitChangesWithManifest("Commit 1", false);
        assertEquals("Commit 1", commit.getFullMessage());
    }
    
    @Test
    public void commitChangesWithManifest_ShouldHaveManifest() throws Exception {
        IArchimateModel model = GitHelper.createSimpleModelInTestRepo(repo);
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        
        // Initial commit has manifest
        RevCommit commit = utils.commitModelWithManifest(model, "Commit 1");
        assertEquals("Commit 1", commit.getShortMessage());
        assertEquals("Commit 1" + manifest, commit.getFullMessage());
        
        // Next commit should have one too
        model.setName("changed");
        GitHelper.saveModel(model);
        commit = utils.commitChangesWithManifest("Commit 2", false);
        
        assertEquals("Commit 2", commit.getShortMessage());
        assertNotEquals("Commit 2", commit.getFullMessage());
    }
    
    @Test
    public void commitModelWithManifest() throws Exception {
        IArchimateModel model = GitHelper.createSimpleModelInTestRepo(repo);
        RevCommit commit = utils.commitModelWithManifest(model, "Commit 1");
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        
        assertEquals("Commit 1", commit.getShortMessage());
        assertEquals("Commit 1" + manifest, commit.getFullMessage());
    }

    @Test
    public void hasChangesToCommit() throws Exception {
        // No files creates so false
        assertFalse(utils.hasChangesToCommit());

        // Create new file, should be true
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        assertTrue(utils.hasChangesToCommit());

        // Commit file, should be false
        utils.commitChanges("Message", false);
        assertFalse(utils.hasChangesToCommit());

        // Create exact same file, should be false
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        assertFalse(utils.hasChangesToCommit());
    }
    
    @Test
    public void pushToRemote() throws Exception {
        utils.setRemote(GitHelper.createBareRepository());

        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        utils.commitChanges("Message", false);

        PushResult pushResult = utils.pushToRemote(null, null);

        Collection<RemoteRefUpdate> refUpdates = pushResult.getRemoteUpdates();
        assertEquals(1, refUpdates.size());

        RemoteRefUpdate refUpdate = refUpdates.iterator().next();
        assertEquals(Status.OK, refUpdate.getStatus());
        assertEquals(RepoConstants.R_HEADS_MAIN, refUpdate.getSrcRef());
        assertEquals(RepoConstants.R_HEADS_MAIN, refUpdate.getRemoteName());
        assertEquals(Result.NEW, refUpdate.getTrackingRefUpdate().getResult());
    }
    
    @Test
    public void getPushResultStatus() throws Exception {
        utils.setRemote(GitHelper.createBareRepository());
        
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        utils.commitChanges("Message", false);
        
        PushResult pushResult = utils.pushToRemote(null, null);
        assertEquals(Status.OK, GitUtils.getPrimaryPushResultStatus(pushResult));
        
        // And check error message
        assertNull(GitUtils.getPushResultFullErrorMessage(pushResult));
        
        pushResult = utils.pushToRemote(null, null);
        assertEquals(Status.UP_TO_DATE, GitUtils.getPrimaryPushResultStatus(pushResult));

        // And check error message
        assertNull(GitUtils.getPushResultFullErrorMessage(pushResult));
    }
    
    @Test
    public void fetchFromRemote() throws Exception {
        utils.setRemote(GitHelper.createBareRepository());

        // Commit to main branch
        GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        utils.commitChanges("Message", false);

        // Push to remote
        utils.pushToRemote(null, null);

        String localBranchName = RepoConstants.R_HEADS + "branch";
        String remoteBranchName = RepoConstants.R_REMOTES_ORIGIN + "branch";

        // Create and checkout new branch
        utils.branchCreate().setName("branch").call();
        utils.checkout().setName(localBranchName).call();

        // Commit to new branch
        GitHelper.writeFileToTestRepo(repo, "file2.txt", "123");
        utils.commitChanges("Message", false);

        // Push to remote
        utils.pushToRemote(null, null);

        // Checkout main branch
        utils.checkout().setName(RepoConstants.R_HEADS_MAIN).call();

        // Delete new branch remote
        utils.deleteBranches(true, remoteBranchName);

        // Fetch
        FetchResult fetchResult = utils.fetchFromRemote(null, null, false);

        Collection<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates();
        assertEquals(1, refUpdates.size());

        TrackingRefUpdate refUpdate = refUpdates.iterator().next();
        assertEquals(localBranchName, refUpdate.getRemoteName());
        assertEquals(remoteBranchName, refUpdate.getLocalName());
        assertEquals(Result.NEW, refUpdate.getResult());
    }
    
    @Test
    public void saveUserDetails() throws Exception {
        utils.saveUserDetails("Montgomery Flange", "m.flange@drama.org");
        PersonIdent ident = utils.getUserDetails();
        assertEquals("Montgomery Flange", ident.getName());
        assertEquals("m.flange@drama.org", ident.getEmailAddress());
    }
    
    @Test
    public void getCurrentLocalBranchName() throws Exception {
        assertEquals(RepoConstants.MAIN, utils.getCurrentLocalBranchName());
    }
    
    @Test
    public void getPrimaryBranch() throws Exception {
        assertNull(utils.getPrimaryBranch());
        utils.commitChanges("Message", false);
        assertEquals(RepoConstants.MAIN, utils.getPrimaryBranch());
    }
    
    @Test
    public void deleteBranches() throws Exception {
        utils.commitChanges("Message", false);

        // Create new branch
        Ref ref = utils.branchCreate().setName("branch").call();
        assertEquals(RepoConstants.R_HEADS + "branch", ref.getName());

        List<String> result = utils.deleteBranches(false, RepoConstants.R_HEADS + "branch");
        assertEquals(RepoConstants.R_HEADS + "branch", result.get(0));
    }
    
    @Test
    public void deleteRemoteBranch() throws Exception {
        utils.setRemote(GitHelper.createBareRepository());

        utils.commitChanges("Message", false);

        String localBranchName = RepoConstants.R_HEADS + "branch";

        // Create new branch
        Ref ref = utils.branchCreate().setName("branch").call();
        assertEquals(localBranchName, ref.getName());

        // Checkout new branch
        utils.checkout().setName(localBranchName).call();

        // Push it
        utils.pushToRemote(null, null);

        PushResult pushResult = utils.deleteRemoteBranch(localBranchName, null, null);

        Collection<RemoteRefUpdate> refUpdates = pushResult.getRemoteUpdates();
        assertEquals(1, refUpdates.size());

        RemoteRefUpdate refUpdate = refUpdates.iterator().next();
        assertEquals(Status.OK, refUpdate.getStatus());
        assertEquals(localBranchName, refUpdate.getRemoteName());
        assertEquals(Result.FORCED, refUpdate.getTrackingRefUpdate().getResult());
    }
    
    @Test
    public void setRemote() throws Exception {
        String url = "https://www.somewherethereish.net/myRepo.git";
        utils.setRemote(url);

        List<RemoteConfig> remotes = utils.remoteList().call();
        assertEquals(1, remotes.size());
        
        RemoteConfig config = remotes.get(0);
        assertEquals(RepoConstants.ORIGIN, config.getName());
        assertEquals(1, config.getURIs().size());
        assertEquals(url, config.getURIs().get(0).toASCIIString());
    }
    
    @Test
    public void setRemoteToNull() throws Exception {
        String url = "https://www.somewherethereish.net/myRepo.git";
        RemoteConfig config = utils.setRemote(url); // Set a remote
        
        assertEquals(RepoConstants.ORIGIN, config.getName());
        assertEquals(1, config.getURIs().size());
        assertEquals(url, config.getURIs().get(0).toASCIIString());
        
        // One remote
        List<RemoteConfig> remotes = utils.remoteList().call();
        assertEquals(1, remotes.size());

        // Remove the remote
        config = utils.setRemote(null);

        // Returned RemoteConfig will have same values 
        assertEquals(RepoConstants.ORIGIN, config.getName());
        assertEquals(1, config.getURIs().size());
        assertEquals(url, config.getURIs().get(0).toASCIIString());

        // Zero remotes
        remotes = utils.remoteList().call();
        assertEquals(0, remotes.size());
    }
    
    @Test
    public void getRemoteURL() throws Exception {
        String url = "https://www.somewherethereish.net/myRepo.git";
        utils.setRemote(url);
        assertEquals(url, utils.getRemoteURL());
    }
    
    @Test
    public void removeRemoteRefs() throws Exception {
        // Remote
        String url = GitHelper.createBareRepository();
        utils.setRemote(url);
        
        // One commit
        utils.commitChanges("Message", false);
        
        // Push main branch
        utils.pushToRemote(null, null);
        
        // Create new branch and push
        utils.checkout().setCreateBranch(true).setName("branch").call();
        utils.pushToRemote(null, null);

        // Check we have refs
        List<Ref> refs = utils.branchList().setListMode(ListMode.REMOTE).call();
        assertEquals(2, refs.size());
        assertEquals("refs/remotes/origin/branch", refs.get(0).getName());
        assertEquals("refs/remotes/origin/main", refs.get(1).getName());
        
        // Check config file has branch entries
        StoredConfig config = utils.getRepository().getConfig();
        assertEquals(2, config.getSubsections(ConfigConstants.CONFIG_BRANCH_SECTION).size());

        // Remove refs
        utils.removeRemoteRefs(url);
        
        // Should be removed
        refs = utils.branchList().setListMode(ListMode.REMOTE).call();
        assertEquals(0, refs.size());
        
        // Check config file branch entries are removed
        config = utils.getRepository().getConfig();
        assertEquals(0, config.getSubsections(ConfigConstants.CONFIG_BRANCH_SECTION).size());
    }
    
    @Test
    public void resetToRef() throws Exception {
        // Create main branch
        utils.commitChanges("Message", false);

        // Create file
        File file = GitHelper.writeFileToTestRepo(repo, "file1.txt", "123");
        assertTrue(file.exists());

        // Reset
        utils.resetToRef(RepoConstants.HEAD);
        assertFalse(file.exists());
    }
    
    @Test
    public void isCommitAtHead() throws Exception {
        RevCommit revCommit1 = utils.commitChanges("Message 1", false);
        assertTrue(utils.isCommitAtHead(revCommit1));
        
        RevCommit revCommit2 = utils.commitChanges("Message 2", false);
        assertTrue(utils.isCommitAtHead(revCommit2));
        assertFalse(utils.isCommitAtHead(revCommit1));
    }
    
    @Test
    public void isRefAtHead() throws Exception {
        utils.commitChanges("Message 1", false);
        utils.commitChanges("Message 2", false);

        Ref ref = utils.getRepository().exactRef("HEAD");
        assertTrue(utils.isRefAtHead(ref));

        // Go to previous commit
        utils.resetToRef("HEAD^");
        assertFalse(utils.isRefAtHead(ref));
    }

    @Test
    public void isObjectIdAtHead() throws Exception {
        utils.commitChanges("Message 1", false);
        Ref ref = utils.getRepository().exactRef(utils.getRepository().getFullBranch());
        assertTrue(utils.isObjectIdAtHead(ref.getObjectId()));
    }

    @Test
    public void isRemoteRefForCurrentBranchAtHead_False() throws Exception {
        assertFalse(utils.isRemoteRefForCurrentBranchAtHead());
    }
    
    @Test
    public void getRemoteRefForCurrentBranch() throws Exception {
        utils.setRemote(GitHelper.createBareRepository());
        utils.commitChanges("Message 1", false);
        assertNull(utils.getRemoteRefForCurrentBranch());
        
        utils.pushToRemote(null, null);
        assertEquals(RepoConstants.R_REMOTES_ORIGIN_MAIN, utils.getRemoteRefForCurrentBranch().getName());
    }
    
    @Test
    public void getRemoteRefNameForCurrentBranch() throws Exception {
        assertEquals(RepoConstants.ORIGIN_MAIN, utils.getRemoteRefNameForCurrentBranch());
    }
    
    @Test
    public void getLatestCommit() throws Exception {
        utils.commitChanges("Message 1", false);
        utils.commitChanges("Message 2", false);
        
        RevCommit commit = utils.getLatestCommit();
        assertEquals("Message 2", commit.getFullMessage());
    }
    
    @Test
    public void hasMoreThanOneCommit() throws Exception {
        utils.commitChanges("Message 1", false);
        assertFalse(utils.hasMoreThanOneCommit());
        utils.commitChanges("Message 2", false);
        assertTrue(utils.hasMoreThanOneCommit());
    }
    
    @Test
    public void getCommitCount() throws Exception {
        utils.commitChanges("Message 1", false);
        utils.commitChanges("Message 2", false);
        utils.commitChanges("Message 3", false);
        assertEquals(3, utils.getCommitCount());
    }
    
    @Test
    public void getCommitParentCount() throws Exception {
        // First commit, no parent
        utils.commitChanges("Message 1", false);
        assertEquals(0, utils.getCommitParentCount("HEAD"));
        
        // Another commit, one parent
        utils.commitChanges("Message 2", false);
        assertEquals(1, utils.getCommitParentCount("HEAD"));
        
        // Create new branch
        utils.branchCreate().setName("branch").call();

        // Checkout new branch
        utils.checkout().setName(RepoConstants.R_HEADS + "branch").call();
        
        // New commit in this branch
        RevCommit commit = utils.commitChanges("Message 3", false);
        
        // Checkout main branch
        utils.checkout().setName(RepoConstants.R_HEADS + "main").call();
        
        // Merge new commit into main branch
        utils.merge().include(commit).setFastForward(FastForwardMode.NO_FF).setMessage("Merged").call();
        
        // So now we have 2 parents
        assertEquals(2, utils.getCommitParentCount("HEAD"));
    }

    @Test
    public void getBaseCommit() throws Exception {
        // First commit
        RevCommit baseCommit = utils.commitChanges("Message 1", false);
        
        // Create new branch
        utils.branchCreate().setName("branch").call();

        // Checkout new branch
        utils.checkout().setName(RepoConstants.R_HEADS + "branch").call();
        
        // New commit in this branch
        RevCommit branchCommit = utils.commitChanges("Message 3", false);
        
        // Checkout main branch
        utils.checkout().setName(RepoConstants.R_HEADS + "main").call();
        
        // New commit in this branch
        utils.commitChanges("Message 2", false);

        assertEquals(baseCommit.getId(), utils.getBaseCommit("HEAD", branchCommit.getName()));
    }

    @Test
    public void isMergedInto() throws Exception {
        // First commit
        RevCommit baseCommit = utils.commitChanges("Message 1", false);
        
        // Create new branch
        utils.branchCreate().setName("branch").call();

        // Checkout new branch
        utils.checkout().setName(RepoConstants.R_HEADS + "branch").call();
        
        // New commit in this branch
        RevCommit branchCommit = utils.commitChanges("Message 3", false);
        
        // Checkout main branch
        utils.checkout().setName(RepoConstants.R_HEADS + "main").call();
        
        // New commit in this branch
        utils.commitChanges("Message 2", false);
        
        assertTrue(utils.isMergedInto(baseCommit.getName(), "HEAD"));
        assertFalse(utils.isMergedInto(branchCommit.getName(), "HEAD"));
    }

    @Test
    public void extractCommitRevStr() throws Exception {
        testExtractCommit(false);
    }
    
    @Test
    public void extractCommitRevCommit() throws Exception {
        testExtractCommit(true);
    }
    
    private void testExtractCommit(boolean useCommit) throws Exception {
        GitHelper.writeFileToTestRepo(repo, "file.txt", "123\n456");
        RevCommit commit = utils.commitChanges("Message 1", false);
        
        File outFolder =  new File(GitHelper.getTempTestsFolder(), "out");
        File outFile = new File(outFolder, "file.txt");

        if(useCommit) {
            utils.extractCommit(commit, outFolder, false);
        }
        else {
            utils.extractCommit(commit.getName(), outFolder, false);
        }
        
        assertTrue(outFile.exists());
        String contents = Files.readString(outFile.toPath());
        assertEquals("123\n456", contents);
    }

    @Test
    public void getFileContentsRevStr() throws Exception {
        testFileContents(false);
    }

    @Test
    public void getFileContentsRevCommit() throws Exception {
        testFileContents(true);
    }
    
    private void testFileContents(boolean useCommit) throws Exception {
        GitHelper.writeFileToTestRepo(repo, "file.txt", "123\n456");
        RevCommit commit = utils.commitChanges("Message 1", false);
        
        byte[] contents;
        
        if(useCommit) {
            contents = utils.getFileContents("file.txt", commit, false);
        }
        else {
            contents = utils.getFileContents("file.txt", commit.getName(), false);
        }
        
        assertEquals("123\n456", new String(contents));
    }
}
