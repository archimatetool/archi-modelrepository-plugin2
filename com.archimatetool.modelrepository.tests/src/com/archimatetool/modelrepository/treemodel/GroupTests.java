/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;


@SuppressWarnings("nls")
public class GroupTests {
    
    @BeforeAll
    public static void runOnceBeforeAllTests() {
        // Don't save to file
        RepositoryTreeModel.saveToManifest = false;
    }
    
    @Test
    public void getName() {
        Group group = new Group("Group");
        assertEquals("Group", group.getName());
        
        group.setName("Another", false);
        assertEquals("Another", group.getName());
    }
    
    @Test
    public void addNewRepositoryRef_Repository() {
        Group group = new Group("");
        IArchiRepository repo = new ArchiRepository(new File("folder"));
        RepositoryRef ref = group.addNewRepositoryRef(repo);
        
        assertSame(group, ref.getParent());
        assertSame(repo, ref.getArchiRepository());
    }

    @Test
    public void addNewRepositoryRef_Folder() {
        Group group = new Group("");
        File folder = new File("folder");
        RepositoryRef ref = group.addNewRepositoryRef(folder);
        
        assertSame(group, ref.getParent());
        assertSame(folder, ref.getArchiRepository().getWorkingFolder());
    }
    
    @Test
    public void addNewGroup() {
        Group group = new Group("");
        Group group2 = group.addNewGroup("");
        
        assertSame(group, group2.getParent());
        assertTrue(group.getGroups().contains(group2));
    }
    
    @Test
    public void add_CannotAddToSelf() {
        assertThrows(IllegalArgumentException.class, () -> {
            Group group = new Group("");
            group.add(group);
        });
    }
    
    @Test
    public void add_CannotAddRepositoryTreeModel() {
        assertThrows(IllegalArgumentException.class, () -> {
            Group group = new Group("");
            group.add(RepositoryTreeModel.getInstance());
        });
    }

    @Test
    public void delete() {
        Group group = new Group("");
        Group group2 = group.addNewGroup("");
        group2.delete();
        assertNull(group2.getParent());
        assertTrue(group.getGroups().isEmpty());
    }

    @Test
    public void getRepositoryRefs() {
        Group group = new Group("");
        assertEquals(0, group.getRepositoryRefs().size());
    }

    @Test
    public void getGroups() {
        Group group = new Group("");
        assertEquals(0, group.getGroups().size());
    }
    
    
    @Test
    public void getAllChildRepositoryRefs() {
        Group group1 = new Group("");
        RepositoryRef ref1 = group1.addNewRepositoryRef(new File("folder"));
        
        Group group2 = group1.addNewGroup("");
        RepositoryRef ref2 = group2.addNewRepositoryRef(new File("folder2"));
        
        List<RepositoryRef> refs = group1.getAllChildRepositoryRefs();
        assertEquals(2, refs.size());
        assertTrue(refs.contains(ref1));
        assertTrue(refs.contains(ref2));
    }

    @Test
    public void getAllChildGroups() {
        Group group1 = new Group("");
        Group group2 = group1.addNewGroup("");
        Group group3 = group2.addNewGroup("");
        
        List<Group> groups = group1.getAllChildGroups();
        assertEquals(2, groups.size());
        assertTrue(groups.contains(group2));
        assertTrue(groups.contains(group3));
    }
    
    @Test
    public void getAll() {
        Group parent = new Group("");
        Group group2 = parent.addNewGroup("");
        RepositoryRef ref1 = parent.addNewRepositoryRef(new File("folder"));
        group2.addNewRepositoryRef(new File("folder2")); // this one should not be returned
        
        List<IModelRepositoryTreeEntry> entries = parent.getAll();
        assertEquals(2, entries.size());
        assertTrue(entries.contains(group2));
        assertTrue(entries.contains(ref1));
    }
}
