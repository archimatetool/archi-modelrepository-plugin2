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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.archimatetool.modelrepository.GitHelper;
import com.archimatetool.modelrepository.TestFiles;
import com.archimatetool.modelrepository.repository.CommitManifest.ObjectChange;


@SuppressWarnings("nls")
public class CommitManifestTests {
    
    private static class TestModel {
        IArchimateModel model;
        IFolder businessFolder;
        IFolder applicationFolder;
        IArchimateConcept concept1;
        IArchimateConcept concept2;
        IDiagramModel diagramModel;
        IDiagramModelArchimateObject dmObject1;
        //IDiagramModelArchimateObject dmObject2;
        
        TestModel() throws IOException {
            model = TestFiles.loadTestArchimateModel(TestFiles.TEST_MODEL_SIMPLE);
            businessFolder = (IFolder)ArchimateModelUtils.getObjectByID(model, "id-da67028aff3d4527bb003b1225c9bbf0");
            applicationFolder = (IFolder)ArchimateModelUtils.getObjectByID(model, "id-e5219142f8da4db2bf88779c892c6092");
            concept1 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-e025a5fbfc6248f9b25a2fff5a205986");
            concept2 = (IArchimateConcept)ArchimateModelUtils.getObjectByID(model, "id-5e313935f5c84609af081172e80a7457");
            diagramModel = (IDiagramModel)ArchimateModelUtils.getObjectByID(model, "id-d2adff72876b4a44ac0dfbc3a620a827");
            dmObject1 = (IDiagramModelArchimateObject)ArchimateModelUtils.getObjectByID(model, "id-861adda5dfb445bebbfb2c1aeef97338");
            //dmObject2 = (IDiagramModelArchimateObject)ArchimateModelUtils.getObjectByID(model, "id-bccf29c3eaa04ad380fcfc6f1707d197");
        }
        
        IArchimateModelObject createModelElementAndAddToModel(EClass eClass) {
            EObject element = IArchimateFactory.eINSTANCE.create(eClass);
            model.getDefaultFolderForObject(element).getElements().add(element);
            return (IArchimateModelObject)element;
        }
        
        void save() throws IOException {
            GitHelper.saveModel(model);
        }
    }
    
    private static boolean hasObjectChange(Collection<ObjectChange> changes, String id, String type) {
        for(ObjectChange change : changes) {
            if(change.id().equals(id) && change.type().equals(type)) {
                return true;
            }
        }
        return false;
    }
    
    private static Set<ObjectChange> getObjectChangesFromCommit(RevCommit commit) {
        String manifest = CommitManifest.getManifestFromCommitMessage(commit.getFullMessage());
        return CommitManifest.getObjectChangesFromCommitMessage(manifest);
    }
    
    private static List<ObjectChange> getObjectChangesFromCommitAsList(RevCommit commit) {
        return new ArrayList<>(getObjectChangesFromCommit(commit));
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
        Set<ObjectChange> changes = getObjectChangesFromCommit(commit);
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
        String manifest = CommitManifest.createManifestForCommit(utils, false);
        
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
        Set<ObjectChange> changes = getObjectChangesFromCommit(commit);
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
        String manifest = CommitManifest.createManifestForCommit(utils, true);
        
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
    public void getCommitMessageWithoutManifest_NullIsEmptyString() {
        assertEquals("", CommitManifest.getCommitMessageWithoutManifest(null));
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
        TestModel testModel = new TestModel();
        GitHelper.saveModelToTestRepo(testModel.model, repo);
        
        // Initial commit of model
        utils.commitModelWithManifest(testModel.model, "Commit 1");
        
        // make some changes
        testModel.model.setName("changed");
        testModel.concept1.setName("changed");
        testModel.concept2.setName("changed");
        
        // Save it
        GitHelper.saveModel(testModel.model);
        
        // Commit it
        RevCommit commit = utils.commitChangesWithManifest("Commit 2", false);
        String commitMessage = commit.getFullMessage();
        
        assertEquals(3, getObjectChangesFromCommit(commit).size());
        
        assertTrue(CommitManifest.containsChange(commitMessage, testModel.model.getId()));
        assertTrue(CommitManifest.containsChange(commitMessage, testModel.concept1.getId()));
        assertTrue(CommitManifest.containsChange(commitMessage, testModel.concept2.getId()));
    }
    
    @Test
    public void hasCorrectObjectChange() throws Exception {
        createRepo();
        
        TestModel testModel = new TestModel();
        GitHelper.saveModelToTestRepo(testModel.model, repo);
        
        // Initial commit of model
        utils.commitModelWithManifest(testModel.model, "Initial");
        
        // A concept added to a folder should be ADDED
        IArchimateModelObject concept = testModel.createModelElementAndAddToModel(IArchimatePackage.eINSTANCE.getBusinessActor());
        testModel.save();
        RevCommit commit = utils.commitChangesWithManifest("Add concept", false);
        List<ObjectChange> changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(concept.getId(), CommitManifest.ADDED), changes.get(0));
        
        // A concept moved to another folder should be MOVED
        testModel.applicationFolder.getElements().add(concept); // Move it to Application folder just for testing
        testModel.save();
        commit = utils.commitChangesWithManifest("Move concept", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(concept.getId(), CommitManifest.MOVED), changes.get(0));

        // A concept deleted from a folder should be DELETED
        ((IFolder)concept.eContainer()).getElements().remove(concept);
        testModel.save();
        commit = utils.commitChangesWithManifest("Delete concept", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(concept.getId(), CommitManifest.DELETED), changes.get(0));

        // A property added should be MODIFIED
        testModel.businessFolder.getProperties().add(IArchimateFactory.eINSTANCE.createProperty("name", "value"));
        testModel.save();
        commit = utils.commitChangesWithManifest("Add property", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A property modified should be MODIFIED
        testModel.businessFolder.getProperties().get(0).setValue("changed");
        testModel.save();
        commit = utils.commitChangesWithManifest("Modify property", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));

        // A property deleted should be MODIFIED
        testModel.businessFolder.getProperties().clear();
        testModel.save();
        commit = utils.commitChangesWithManifest("Delete property", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A feature added should be MODIFIED
        testModel.businessFolder.getFeatures().putString("feature", "value");
        testModel.save();
        commit = utils.commitChangesWithManifest("Add feature", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A feature modified should be MODIFIED
        testModel.businessFolder.getFeatures().get(0).setValue("changed");
        testModel.save();
        commit = utils.commitChangesWithManifest("Modify feature", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));

        // A feature deleted should be MODIFIED
        testModel.businessFolder.getFeatures().clear();
        testModel.save();
        commit = utils.commitChangesWithManifest("Delete feature", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.businessFolder.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A profile added should be the model and MODIFIED
        testModel.model.getProfiles().add(IArchimateFactory.eINSTANCE.createProfile());
        testModel.save();
        commit = utils.commitChangesWithManifest("Add profile", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.model.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A profile modified should be the model and MODIFIED
        testModel.model.getProfiles().get(0).setName("Profile");
        testModel.save();
        commit = utils.commitChangesWithManifest("Modify profile", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.model.getId(), CommitManifest.MODIFIED), changes.get(0));

        // A profile deleted should be the model and MODIFIED
        testModel.model.getProfiles().clear();
        testModel.save();
        commit = utils.commitChangesWithManifest("Delete profile", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.model.getId(), CommitManifest.MODIFIED), changes.get(0));
        
        // A bounds change should be the diagram and MODIFIED
        testModel.dmObject1.getBounds().setX(0);
        testModel.save();
        commit = utils.commitChangesWithManifest("Change bounds", false);
        changes = getObjectChangesFromCommitAsList(commit);
        assertEquals(1, changes.size());
        assertEquals(new ObjectChange(testModel.diagramModel.getId(), CommitManifest.MODIFIED), changes.get(0));
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
    public void getManifestFromCommitMessage_NullIsEmptyString() {
        assertNull(CommitManifest.getManifestFromCommitMessage(null));
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
