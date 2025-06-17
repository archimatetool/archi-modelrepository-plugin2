/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class TagInfoTests {
    
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
    public void getTags() throws Exception {
        RevCommit commit1 = utils.commitChanges("Commit 1", false);
        RevCommit commit2 = utils.commitChanges("Commit 2", false);
        
        Ref ref1 = addAnnotatedTag(commit1, "tag1");
        Ref ref2 = addAnnotatedTag(commit2, "tag2");
        
        List<TagInfo> tagInfos = TagInfo.getTags(repo.getGitFolder());
        assertEquals(2, tagInfos.size());
        
        TagInfo tagInfo1 = tagInfos.get(0);
        assertFalse(tagInfo1.getRef().isSymbolic());
        assertEquals(RepoConstants.R_TAGS + "tag1", tagInfo1.getFullName());
        assertEquals("tag1", tagInfo1.getShortName());
        assertEquals(repo.getWorkingFolder(), tagInfo1.getWorkingFolder());
        assertEquals(commit1, tagInfo1.getCommit());
        assertEquals(ref1.getObjectId(), tagInfo1.getRef().getObjectId());
        assertFalse(tagInfo1.isOrphaned());
        assertTrue(tagInfo1.isAnnotated());
        
        RevTag revTag = tagInfo1.getTag();
        assertNotNull(revTag);
        assertEquals("Hello World", revTag.getShortMessage());
        assertEquals("Montgomery Flange", revTag.getTaggerIdent().getName());
        assertEquals("m.flange@drama.org", revTag.getTaggerIdent().getEmailAddress());
       
        TagInfo tagInfo2 = tagInfos.get(1);
        assertFalse(tagInfo2.getRef().isSymbolic());
        assertEquals(RepoConstants.R_TAGS + "tag2", tagInfo2.getFullName());
        assertEquals("tag2", tagInfo2.getShortName());
        assertEquals(repo.getWorkingFolder(), tagInfo2.getWorkingFolder());
        assertEquals(commit2, tagInfo2.getCommit());
        assertEquals(ref2.getObjectId(), tagInfo2.getRef().getObjectId());
        assertFalse(tagInfo2.isOrphaned());
        assertTrue(tagInfo2.isAnnotated());
    }
    
    @Test
    public void isOrphaned() throws Exception {
        utils.commitChanges("Commit 1", false);
        RevCommit commit = utils.commitChanges("Commit 2", false);
        addAnnotatedTag(commit, "tag1");
        
        // Undo second commit
        utils.resetToRef("HEAD^");
        
        List<TagInfo> tagInfos = TagInfo.getTags(repo.getGitFolder());
        assertEquals(1, tagInfos.size());
        
        TagInfo tagInfo = tagInfos.get(0);
        assertTrue(tagInfo.isOrphaned());
    }
    
    @Test
    public void isLightweightTag() throws Exception {
        RevCommit commit1 = utils.commitChanges("Commit 1", false);
        addLightweightTag(commit1, "tag1");
        
        List<TagInfo> tagInfos = TagInfo.getTags(repo.getGitFolder());
        assertEquals(1, tagInfos.size());
        
        TagInfo tagInfo = tagInfos.get(0);
        assertFalse(tagInfo.getRef().isSymbolic());
        assertFalse(tagInfo.isAnnotated());
        assertNull(tagInfo.getTag());
    }

    private Ref addLightweightTag(RevCommit commit, String name) throws GitAPIException {
        return utils.tag()
                    .setObjectId(commit)
                    .setName(name)
                    .setAnnotated(false)
                    .call();
    }
    
    private Ref addAnnotatedTag(RevCommit commit, String name) throws GitAPIException {
        return utils.tag()
                    .setObjectId(commit)
                    .setName(name)
                    .setMessage("Hello World")
                    .setTagger(new PersonIdent("Montgomery Flange", "m.flange@drama.org"))
                    .call();
    }
}
