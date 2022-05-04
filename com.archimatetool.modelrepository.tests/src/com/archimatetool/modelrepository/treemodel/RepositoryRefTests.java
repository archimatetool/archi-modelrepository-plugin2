/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;

import junit.framework.JUnit4TestAdapter;


@SuppressWarnings("nls")
public class RepositoryRefTests {
    
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(RepositoryRefTests.class);
    }
    
    @Test
    public void getArchiRepository_Repo() {
        IArchiRepository repo = new ArchiRepository(new File("folder"));
        RepositoryRef ref = new RepositoryRef(repo);
        assertSame(repo, ref.getArchiRepository());
    }

    @Test
    public void getArchiRepository_File() {
        File file = new File("folder");
        RepositoryRef ref = new RepositoryRef(file);
        assertSame(file, ref.getArchiRepository().getLocalRepositoryFolder());
    }
    
    @Test
    public void getParent() {
        RepositoryRef ref = createRepositoryRef();
        assertNull(ref.getParent());
        
        Group group = new Group("Parent");
        group.add(ref);
        assertSame(group, ref.getParent());
    }
    
    @Test
    public void delete() {
        RepositoryRef ref = createRepositoryRef();
        Group group = new Group("Parent");
        group.add(ref);
        ref.delete();
        assertNull(ref.getParent());
        assertTrue(group.getAllChildRepositoryRefs().isEmpty());
    }
    
    @Test
    public void getName() {
        RepositoryRef ref = createRepositoryRef();
        assertNotNull(ref.getName());
    }
    
    private RepositoryRef createRepositoryRef() {
        return new RepositoryRef(new File("folder"));
    }
}
