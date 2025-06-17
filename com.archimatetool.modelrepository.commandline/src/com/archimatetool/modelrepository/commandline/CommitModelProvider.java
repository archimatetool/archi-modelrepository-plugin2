/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.modelrepository.repository.GitUtils;

/**
 * Command Line interface for committing to a repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository2.modelFolder "modelFolder"
   --modelrepository2.commitModel "commit message"
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CommitModelProvider extends AbstractModelRepositoryProvider {

    public static final String OPTION_COMMIT = "modelrepository2.commitModel"; //$NON-NLS-1$

    public CommitModelProvider() {
    }
    
    @Override
    public void run(CommandLine commandLine) throws Exception {
        if(!hasCorrectOptions(commandLine)) {
            return;
        }
        
        File modelFolder = getFolderOption(commandLine);
        if(modelFolder == null) {
            return;
        }
        
        String commitMessage = commandLine.getOptionValue(OPTION_COMMIT);
        
        try(GitUtils utils = GitUtils.open(modelFolder)) {
            logMessage(NLS.bind("Committing: {0}", commitMessage));
            utils.commitChanges(commitMessage, false);
        }
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_COMMIT)
                .argName("message")
                .hasArg()
                .desc(NLS.bind("Commit changes with the <message> to the local repository in the <path> set in option --{0}.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER))
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    protected boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_COMMIT) && commandLine.hasOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
    }
    
    @Override
    public int getPriority() {
        return 30;
    }
    
    @Override
    protected String getLogPrefix() {
        return "[CommitModelToRepositoryProvider]";
    }
}
