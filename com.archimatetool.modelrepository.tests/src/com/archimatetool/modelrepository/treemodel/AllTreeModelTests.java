/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.treemodel;

import junit.framework.TestSuite;

@SuppressWarnings("nls")
public class AllTreeModelTests {

    public static junit.framework.Test suite() {
		TestSuite suite = new TestSuite("com.archimatetool.modelrepository.treemodel");
		
        suite.addTest(GroupTests.suite());
        suite.addTest(RepositoryRefTests.suite());
        suite.addTest(RepositoryTreeModelTests.suite());

        return suite;
	}

}