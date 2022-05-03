/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import com.archimatetool.modelrepository.repository.ArchiRepositoryTests;
import com.archimatetool.modelrepository.repository.RepoUtilsTests;

import junit.framework.TestSuite;

@SuppressWarnings("nls")
public class AllTests {

    public static junit.framework.Test suite() {
		TestSuite suite = new TestSuite("com.archimatetool.modelrepository");

        suite.addTest(ArchiRepositoryTests.suite());
        suite.addTest(RepoUtilsTests.suite());
		
        return suite;
	}

}