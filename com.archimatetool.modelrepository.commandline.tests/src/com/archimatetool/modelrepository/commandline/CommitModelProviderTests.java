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
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class CommitModelProviderTests extends AbstractProviderTests {
    
    public CommitModelProviderTests() {
        super(CommitModelProvider.class);
    }
    
    @Test
    public void runProvider() throws Exception {
        IArchiRepository repository = GitHelper.createNewRepository().init();
        IArchimateModel model = GitHelper.createSimpleModelInTestRepo(repository);
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            assertTrue(utils.hasChangesToCommit());

            // Run provider
            CommandLine commandLine = new DefaultParser().parse(getTestOptions(),
                                                                getArgs(repository.getWorkingFolder().getAbsolutePath(), "Commit 1"));
            provider.run(commandLine);
            
            // First commit should be initial model commit
            assertFalse(utils.hasChangesToCommit());
            RevCommit commit = utils.getLatestCommit().orElseThrow();
            assertEquals("Commit 1", commit.getShortMessage());
            assertTrue(commit.getFullMessage().contains("<manifest version=\"1.0.0\">"));
            assertEquals(1, utils.getCommitCount());
            
            // Run again with no commit needed
            provider.run(commandLine);
            assertEquals(1, utils.getCommitCount());
            
            // Make a change
            model.setName("Changed");
            GitHelper.saveModel(model);
            assertTrue(utils.hasChangesToCommit());
            
            // Run again
            commandLine = new DefaultParser().parse(getTestOptions(),
                    getArgs(repository.getWorkingFolder().getAbsolutePath(), "Commit 2"));
            provider.run(commandLine);
            commit = utils.getLatestCommit().orElseThrow();
            assertEquals("Commit 2", commit.getShortMessage());
            assertTrue(commit.getFullMessage().contains("<manifest version=\"1.0.0\">"));
            assertEquals(2, utils.getCommitCount());
        }
    }
    
    @Test
    public void getOptionsCorrect() throws Exception {
        Options options = provider.getOptions();
        assertEquals(1, options.getOptions().size());
        assertTrue(options.hasOption(CommitModelProvider.OPTION_COMMIT));
    }
    
    private String[] getArgs(String folder, String commitMessage) {
        return new String[] {
                getFullOption(CommitModelProvider.OPTION_COMMIT), commitMessage,
                getFullOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER), folder
        };
    }
}
