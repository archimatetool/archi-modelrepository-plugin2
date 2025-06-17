/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.eclipse.gef.commands.CommandStack;
import org.junit.jupiter.api.Test;

import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.testsupport.GitHelper;


@SuppressWarnings("nls")
public class LoadModelProviderTests extends AbstractProviderTests {
    
    private IArchiRepository repository;
    
    public LoadModelProviderTests() {
        super(LoadModelProvider.class);
    }
    
    @Test
    public void runProvider() throws Exception {
        setup();
        
        CommandLine commandLine = new DefaultParser().parse(getTestOptions(), getArgs(repository.getWorkingFolder().getAbsolutePath()));
        provider.run(commandLine);
        
        IArchimateModel model = CommandLineState.getModel();
        assertNotNull(model);
        assertNotNull(model.getAdapter(IArchiveManager.class));
        assertNotNull(model.getAdapter(CommandStack.class));
    }
    
    @Test
    public void modelNotFoundException() throws Exception {
        CommandLine commandLine = new DefaultParser().parse(getTestOptions(), getArgs("bogus/folder"));
        
        assertThrows(IOException.class, () -> {
            provider.run(commandLine);
        });
    }
    
    @Test
    public void getOptionsCorrect() throws Exception {
        Options options = provider.getOptions();
        assertEquals(1, options.getOptions().size());
        assertTrue(options.hasOption(LoadModelProvider.OPTION_LOAD_MODEL));
    }
    
    private void setup() throws Exception {
        CommandLineState.setModel(null);
        repository = GitHelper.createNewRepository().init();
        GitHelper.createSimpleModelInTestRepo(repository);
    }
    
    private String[] getArgs(String folder) {
        return new String[] {
                getFullOption(LoadModelProvider.OPTION_LOAD_MODEL),
                getFullOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER), folder
        };
    }
}
