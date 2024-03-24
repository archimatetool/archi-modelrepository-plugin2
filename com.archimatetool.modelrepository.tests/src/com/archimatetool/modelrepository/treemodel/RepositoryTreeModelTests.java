/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


@SuppressWarnings("nls")
public class RepositoryTreeModelTests {
    
    @BeforeAll
    public static void runOnceBeforeAllTests() {
        // Don't save to file
        RepositoryTreeModel.saveToManifest = false;
    }

    @Test
    public void findRepositoryRef() {
        File folder1 = new File("folder1");
        File folder2 = new File("folder2");
        
        Group group1 = RepositoryTreeModel.getInstance().addNewGroup("");
        RepositoryRef ref1 = group1.addNewRepositoryRef(folder1);
        
        Group group2 = group1.addNewGroup("");
        RepositoryRef ref2 = group2.addNewRepositoryRef(folder2);
        
        assertEquals(ref1, RepositoryTreeModel.getInstance().findRepositoryRef(folder1));
        assertEquals(ref2, RepositoryTreeModel.getInstance().findRepositoryRef(folder2));
    }
}
