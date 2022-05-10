/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import junit.framework.TestSuite;

@SuppressWarnings("nls")
public class AllRepositoryTests {

    public static junit.framework.Test suite() {
		TestSuite suite = new TestSuite("com.archimatetool.modelrepository.repository");
		
        suite.addTest(ArchiRepositoryTests.suite());
        suite.addTest(GitUtilsTests.suite());
        suite.addTest(RepoUtilsTests.suite());

        return suite;
	}

}