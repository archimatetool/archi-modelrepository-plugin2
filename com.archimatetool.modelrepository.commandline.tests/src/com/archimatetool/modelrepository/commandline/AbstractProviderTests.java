/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.modelrepository.testsupport.GitHelper;

/**
 * Abstract Provider Tests
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
abstract class AbstractProviderTests {
    
    private Class<? extends AbstractModelRepositoryProvider> clazz;
    protected AbstractModelRepositoryProvider provider;
    protected CoreModelRepositoryProvider coreProvider;
    
    public AbstractProviderTests(Class<? extends AbstractModelRepositoryProvider> clazz) {
        this.clazz = clazz;
        coreProvider = new CoreModelRepositoryProvider(); // These options are required
    }

    @BeforeEach
    public void runOnceBeforeEachTest() throws Exception {
        provider = clazz.getDeclaredConstructor().newInstance();
        ((AbstractCommandLineProvider)provider).doLog = false;
    }
    
    @AfterEach
    public void runOnceAfterEachTest() throws Exception {
        FileUtils.deleteFolder(GitHelper.getTempTestsFolder());
    }
    
    @Test
    public void hasCorrectOptions() throws Exception {
        Builder builder = CommandLine.builder();
        getTestOptions().getOptions().stream().forEach(option -> builder.addOption(option));
        assertTrue(provider.hasCorrectOptions(builder.build()));
    }

    /**
     * Get provider options and core options
     */
    protected Options getTestOptions() {
        Options options = new Options();
        options.addOptions(provider.getOptions());     // Provider options
        options.addOptions(coreProvider.getOptions()); // Core options
        return options;
    }
    
    /**
     * Prepend "--" to option
     */
    protected String getFullOption(String option) {
        return "--" + option;
    }
    
    protected File writePasswordFile(File folder) throws IOException {
        File file = new File(folder, "pw.txt");
        Files.write(file.toPath(), "password".getBytes());
        return file;
    }
}