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
import java.util.Iterator;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.modelrepository.GitHelper;
import com.archimatetool.modelrepository.TestFiles;
import com.archimatetool.modelrepository.repository.CommitManifest.ObjectChange;


@SuppressWarnings("nls")
public class CommitManifestTests {
    
    private static class TestModel {
        IArchimateModel model;
        IArchimateConcept concept1;
        IArchimateConcept concept2;
        //IDiagramModel diagramModel;
        
        TestModel() throws IOException {
            model = TestFiles.loadTestArchimateModel(TestFiles.TEST_MODEL_SIMPLE);
            concept1 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-e025a5fbfc6248f9b25a2fff5a205986");
            concept2 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-5e313935f5c84609af081172e80a7457");
            //diagramModel = (IDiagramModel)ArchimateModelUtils.getObjectByID(model, "id-d2adff72876b4a44ac0dfbc3a620a827");
        }
        
        void save() throws IOException {
            GitHelper.saveModel(model);
        }
    }
    
    private static boolean hasObjectChange(Set<ObjectChange> changes, String id, String type) {
        for(ObjectChange change : changes) {
            if(change.id().equals(id) && change.type().equals(type)) {
                return true;
            }
        }
        return false;
    }
    
    private IArchiRepository repo;
    private GitUtils utils;
    
    private void createRepo() throws Exception {
        repo = GitHelper.createNewRepository().init();
        utils = GitUtils.open(repo.getGitFolder());
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws Exception {
        if(utils != null) {
            utils.close();
        }
        
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    
    @Test
    public void createManifestForInitialCommit() {
        IArchimateModel model = GitHelper.createSimpleModel();
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        
        Set<ObjectChange> changes = CommitManifest.getObjectChangesFromCommitMessage(manifest);
        assertEquals(11, changes.size());
        
        for(ObjectChange change : changes) {
            assertEquals(CommitManifest.ADDED, change.type());
            assertNotNull(ArchimateModelUtils.getObjectByID(model, change.id()));
        }
    }

    @Test
    public void createManifestForCommit() throws Exception {
        createRepo();
        
        TestModel testModel = new TestModel();
        GitHelper.saveModelToTestRepo(testModel.model, repo);
        
        // Initial commit of model
        RevCommit commit = utils.commitModelWithManifest(testModel.model, "Commit 1");
        String manifest = CommitManifest.getManifestFromCommitMessage(commit.getFullMessage());
        Set<ObjectChange> changes = CommitManifest.getObjectChangesFromCommitMessage(manifest);
        assertEquals(14, changes.size());
        
        for(ObjectChange change : changes) {
            assertEquals(CommitManifest.ADDED, change.type());
        }
        
        // make some changes
        testModel.model.setName("new name");
        testModel.concept1.setName("new name");
        testModel.concept2.setName("new name");
        
        // Save it
        testModel.save();
        
        // Get manifest
        manifest = CommitManifest.createManifestForCommit(utils, false);
        
        changes = CommitManifest.getObjectChangesFromCommitMessage(manifest);
        assertEquals(3, changes.size());
        
        assertTrue(hasObjectChange(changes, testModel.model.getId(), CommitManifest.MODIFIED));
        assertTrue(hasObjectChange(changes, testModel.concept1.getId(), CommitManifest.MODIFIED));
        assertTrue(hasObjectChange(changes, testModel.concept2.getId(), CommitManifest.MODIFIED));
    }
    
    @Test
    public void createManifestForCommit_Amend() throws Exception {
        createRepo();
        
        TestModel testModel = new TestModel();
        GitHelper.saveModelToTestRepo(testModel.model, repo);
        
        // Initial commit of model
        RevCommit commit = utils.commitModelWithManifest(testModel.model, "Commit 1");
        String manifest = CommitManifest.getManifestFromCommitMessage(commit.getFullMessage());
        Set<ObjectChange> changes = CommitManifest.getObjectChangesFromCommitMessage(manifest);
        assertEquals(14, changes.size());
        
        for(ObjectChange change : changes) {
            assertEquals(CommitManifest.ADDED, change.type());
        }
        
        // make some changes
        testModel.model.setName("new name");
        testModel.concept1.setName("new name");
        testModel.concept2.setName("new name");
        
        // Save it
        testModel.save();
        
        // Get manifest
        manifest = CommitManifest.createManifestForCommit(utils, true);
        
        changes = CommitManifest.getObjectChangesFromCommitMessage(manifest);
        assertEquals(14, changes.size());
        
        for(ObjectChange change : changes) {
            assertEquals(CommitManifest.ADDED, change.type());
        }
        
        assertTrue(hasObjectChange(changes, testModel.model.getId(), CommitManifest.ADDED));
        assertTrue(hasObjectChange(changes, testModel.concept1.getId(), CommitManifest.ADDED));
        assertTrue(hasObjectChange(changes, testModel.concept2.getId(), CommitManifest.ADDED));
    }
    
    @Test
    public void getCommitMessageWithoutManifest() {
        IArchimateModel model = GitHelper.createSimpleModel();
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        
        String commitMessage = "Commit Message\n\nMessage Body";
        String result = CommitManifest.getCommitMessageWithoutManifest(commitMessage + manifest);
        assertEquals(commitMessage, result);
        
        // No message
        result = CommitManifest.getManifestFromCommitMessage(manifest);
        assertEquals(manifest.strip(), result); // remove leading newlines
    }
    
    @Test
    public void containsChange_InitialCommit() {
        IArchimateModel model = GitHelper.createSimpleModel();
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        String commitMessage = "Commit Message\n\nMessage Body" + manifest;
        
        assertTrue(CommitManifest.containsChange(commitMessage, model.getId()));
        for(Iterator<EObject> iter = model.eAllContents(); iter.hasNext();) {
            if(iter.next() instanceof IIdentifier eObject) {
                assertTrue(CommitManifest.containsChange(commitMessage, eObject.getId()));
            }
        }
    }
    
    @Test
    public void containsChange_InCommit() throws Exception {
        createRepo();
        
        // Save model to repo
        IArchimateModel model = TestFiles.loadTestArchimateModel(TestFiles.TEST_MODEL_SIMPLE);
        GitHelper.saveModelToTestRepo(model, repo);
        
        // Initial commit of model
        utils.commitModelWithManifest(model, "Commit 1");
        
        // make some changes
        model.setName("changed");
        IArchimateConcept concept1 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-e025a5fbfc6248f9b25a2fff5a205986");
        concept1.setName("changed");
        IArchimateConcept concept2 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-5e313935f5c84609af081172e80a7457");
        concept2.setName("changed");
        
        // Save it
        GitHelper.saveModel(model);
        
        // Commit it
        RevCommit commit = utils.commitChangesWithManifest("Commit 2", false);
        String commitMessage = commit.getFullMessage();
        
        assertEquals(3, CommitManifest.getObjectChangesFromCommitMessage(commitMessage).size());
        
        assertTrue(CommitManifest.containsChange(commitMessage, model.getId()));
        assertTrue(CommitManifest.containsChange(commitMessage, concept1.getId()));
        assertTrue(CommitManifest.containsChange(commitMessage, concept2.getId()));
    }
    
    @Test
    public void getObjectChangesFromCommitMessage() {
        IArchimateModel model = GitHelper.createSimpleModel();
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        String commitMessage = "Commit Message\n\nMessage Body" + manifest;
        
        Set<ObjectChange> changes = CommitManifest.getObjectChangesFromCommitMessage(commitMessage);
        for(ObjectChange change : changes) {
            assertEquals(CommitManifest.ADDED, change.type());
            assertNotNull(ArchimateModelUtils.getObjectByID(model, change.id()));
        }
    }

    @Test
    public void getManifestFromCommitMessage() {
        IArchimateModel model = GitHelper.createSimpleModel();
        String manifest = CommitManifest.createManifestForInitialCommit(model);
        
        String commitMessageWithManifest = "Commit Message\n\nMessage Body" + manifest;
        
        String result = CommitManifest.getManifestFromCommitMessage(commitMessageWithManifest);
        assertEquals(manifest.strip(), result); // remove leading newlines
        
        // Now set the commit message to a manifest to check this is ignored
        commitMessageWithManifest = manifest.strip() + manifest;
        result = CommitManifest.getManifestFromCommitMessage(commitMessageWithManifest);
        assertEquals(manifest.strip(), result); // remove leading newlines
        
        // No message
        commitMessageWithManifest = "" + manifest;
        result = CommitManifest.getManifestFromCommitMessage(commitMessageWithManifest);
        assertEquals(manifest.strip(), result); // remove leading newlines
    }
    
    @Test
    public void getManifestFromCommitMessage_WithNoManifestShouldBeNull() {
        // Test the cases where there is no manifest in the commit message
        assertNull(CommitManifest.getManifestFromCommitMessage(""));
        assertNull(CommitManifest.getManifestFromCommitMessage("Commit Message\n\nMessage Body"));
        assertNull(CommitManifest.getManifestFromCommitMessage("<manifest>"));
    }
    
    @Test
    public void isValidObject() {
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createArchimateModel()));
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createFolder()));
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createBusinessActor()));
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createAssociationRelationship()));
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createArchimateDiagramModel()));
        assertTrue(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createSketchModel()));
        
        assertFalse(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createBounds()));
        assertFalse(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createDiagramModelNote()));
        assertFalse(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createDiagramModelGroup()));
        assertFalse(CommitManifest.isValidObject(IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject()));
    }
}
