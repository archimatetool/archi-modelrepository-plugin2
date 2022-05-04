/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import com.archimatetool.modelrepository.repository.AllRepositoryTests;
import com.archimatetool.modelrepository.treemodel.AllTreeModelTests;

import junit.framework.TestSuite;

@SuppressWarnings("nls")
public class AllTests {

    public static junit.framework.Test suite() {
		TestSuite suite = new TestSuite("com.archimatetool.modelrepository");

        suite.addTest(AllRepositoryTests.suite());
        suite.addTest(AllTreeModelTests.suite());
		
        return suite;
	}

}