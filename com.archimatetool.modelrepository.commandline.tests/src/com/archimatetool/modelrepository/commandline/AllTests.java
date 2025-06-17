/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;



@Suite
@SelectClasses({
    CloneModelProviderTests.class,
    CommitModelProviderTests.class,
    LoadModelProviderTests.class,
    PushModelProviderTests.class,
    SwitchBranchProviderTests.class
})

@SuiteDisplayName("All coArchi2 Command Line Tests")
public class AllTests {
}