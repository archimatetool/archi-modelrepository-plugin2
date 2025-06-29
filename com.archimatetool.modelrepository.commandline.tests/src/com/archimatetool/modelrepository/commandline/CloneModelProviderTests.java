/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.junit.jupiter.api.Test;

import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class CloneModelProviderTests extends AbstractProviderTests {
    
    private File remoteRepoFolder;
    private File localFolder;
    private File passwordFile;
    
    public CloneModelProviderTests() {
        super(CloneModelProvider.class);
    }
    
    @Test
    public void runProvider() throws Exception {
        setup();
        
        CommandLine commandLine = new DefaultParser().parse(getTestOptions(), getArgs());
        provider.run(commandLine);
        
        IArchiRepository repository = new ArchiRepository(localFolder);
        assertTrue(repository.getGitFolder().exists());
        assertTrue(repository.getModelFile().exists());
    }
    
    @Test
    public void getOptionsCorrect() throws Exception {
        Options options = provider.getOptions();
        assertEquals(1, options.getOptions().size());
        assertTrue(options.hasOption(CloneModelProvider.OPTION_CLONE_MODEL));
    }
    
    private void setup() throws Exception {
        remoteRepoFolder = GitHelper.createBareRepository();
        
        IArchiRepository repository = GitHelper.createNewRepository().init();
        repository.setRemote(remoteRepoFolder.getAbsolutePath());
        
        GitHelper.createSimpleModelInTestRepo(repository);
        
        try(GitUtils utils = GitUtils.open(repository.getWorkingFolder())) {
            utils.commitChanges("Commit 1", false);
            utils.pushToRemote(null, null);
        }
        
        localFolder = new File(GitHelper.getTempTestsFolder(), "testFolder");
        localFolder.mkdirs();
        
        passwordFile = writePasswordFile(localFolder);
    }
    
    private String[] getArgs() {
        return new String[] {
                getFullOption(CloneModelProvider.OPTION_CLONE_MODEL), remoteRepoFolder.getAbsolutePath(),
                getFullOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER), localFolder.getAbsolutePath(),
                getFullOption(CoreModelRepositoryProvider.OPTION_USERNAME), "Fido",
                getFullOption(CoreModelRepositoryProvider.OPTION_PASSFILE), passwordFile.getAbsolutePath()
        };
    }
}
