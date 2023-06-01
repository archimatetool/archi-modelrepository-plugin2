/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.archimatetool.modelrepository.repository.AllRepositoryTests;
import com.archimatetool.modelrepository.treemodel.AllTreeModelTests;

@RunWith(Suite.class)

@Suite.SuiteClasses({
    AllRepositoryTests.class,
    AllTreeModelTests.class
})


public class AllTests {
}