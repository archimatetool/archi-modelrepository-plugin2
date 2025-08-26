/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.RepoConstants;

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
            
            // Check there are changes
            if(!utils.hasChangesToCommit()) {
                logMessage("No changes to commit.");
                return;
            }
            
            // If it's the initial commit
            if(utils.getLatestCommit() == null) {
                // Load model
                File modelFile = new File(modelFolder, RepoConstants.MODEL_FILENAME);
                if(!modelFile.exists()) {
                    throw new IOException(NLS.bind("Model file {0} does not exist.", modelFile));
                }
                // Initial commit with full manifest
                IArchimateModel model = IEditorModelManager.INSTANCE.load(modelFile);
                utils.commitModelWithManifest(model, commitMessage);
            }
            // Another commit
            else {
                utils.commitChangesWithManifest(commitMessage, false);
            }
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
