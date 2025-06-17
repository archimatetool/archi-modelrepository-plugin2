/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class CommitModelProviderTests extends AbstractProviderTests {
    
    private IArchiRepository repository;
    
    public CommitModelProviderTests() {
        super(CommitModelProvider.class);
    }
    
    @Test
    public void runProvider() throws Exception {
        setup();
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            assertTrue(utils.hasChangesToCommit());

            CommandLine commandLine = new DefaultParser().parse(getTestOptions(), getArgs());
            provider.run(commandLine);
            
            assertFalse(utils.hasChangesToCommit());
        }
    }
    
    @Test
    public void getOptionsCorrect() throws Exception {
        Options options = provider.getOptions();
        assertEquals(1, options.getOptions().size());
        assertTrue(options.hasOption(CommitModelProvider.OPTION_COMMIT));
    }
    
    private void setup() throws Exception {
        repository = GitHelper.createNewRepository().init();
        GitHelper.createSimpleModelInTestRepo(repository);
    }
    
    private String[] getArgs() {
        return new String[] {
                getFullOption(CommitModelProvider.OPTION_COMMIT), "Commit 1\n\nA Message",
                getFullOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER), repository.getWorkingFolder().getAbsolutePath(),
        };
    }
}
