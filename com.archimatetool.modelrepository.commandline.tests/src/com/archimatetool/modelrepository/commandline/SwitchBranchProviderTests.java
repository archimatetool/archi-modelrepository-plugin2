/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class SwitchBranchProviderTests extends AbstractProviderTests {
    
    private IArchiRepository repository;
    
    public SwitchBranchProviderTests() {
        super(SwitchBranchProvider.class);
    }
    
    @Test
    public void runProvider() throws Exception {
        setup();
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            assertEquals("main", repository.getCurrentLocalBranchName().orElse(null));

            CommandLine commandLine = new DefaultParser().parse(getTestOptions(), getArgs());
            provider.run(commandLine);
            
            assertEquals("branch", repository.getCurrentLocalBranchName().orElse(null));
        }
    }
    
    @Test
    public void getOptionsCorrect() throws Exception {
        Options options = provider.getOptions();
        assertEquals(1, options.getOptions().size());
        assertTrue(options.hasOption(SwitchBranchProvider.OPTION_SWITCH_BRANCH));
    }
    
    private void setup() throws Exception {
        repository = GitHelper.createNewRepository().init();
        GitHelper.createSimpleModelInTestRepo(repository);
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            utils.commitChanges("Commit 1", false);
            utils.branchCreate().setName("branch").call();
            utils.commitChanges("Commit 2", false);
        }
    }
    
    private String[] getArgs() {
        return new String[] {
                getFullOption(SwitchBranchProvider.OPTION_SWITCH_BRANCH), "branch",
                getFullOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER), repository.getWorkingFolder().getAbsolutePath(),
        };
    }
}
