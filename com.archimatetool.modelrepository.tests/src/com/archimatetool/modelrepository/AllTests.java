/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

import com.archimatetool.modelrepository.repository.ArchiRepositoryTests;
import com.archimatetool.modelrepository.repository.BranchInfoTests;
import com.archimatetool.modelrepository.repository.BranchStatusTests;
import com.archimatetool.modelrepository.repository.CommitManifestTests;
import com.archimatetool.modelrepository.repository.GitUtilsTests;
import com.archimatetool.modelrepository.repository.RepoUtilsTests;
import com.archimatetool.modelrepository.treemodel.GroupTests;
import com.archimatetool.modelrepository.treemodel.RepositoryRefTests;
import com.archimatetool.modelrepository.treemodel.RepositoryTreeModelTests;


@Suite
@SelectClasses({
    // repository
    ArchiRepositoryTests.class,
    BranchInfoTests.class,
    BranchStatusTests.class,
    CommitManifestTests.class,
    GitUtilsTests.class,
    RepoUtilsTests.class,
    
    // treemodel
    GroupTests.class,
    RepositoryRefTests.class,
    RepositoryTreeModelTests.class
})

@SuiteDisplayName("All Model Repository Tests")
public class AllTests {
}