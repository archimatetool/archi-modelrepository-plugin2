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
import org.eclipse.jgit.api.Git;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.modelrepository.repository.BranchStatus;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Command Line interface for switching branch in a repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository2.modelFolder "modelFolder"
   --modelrepository2.switchBranch "branch"
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class SwitchBranchProvider extends AbstractModelRepositoryProvider {

    public static final String OPTION_SWITCH_BRANCH = "modelrepository2.switchBranch"; //$NON-NLS-1$

    public SwitchBranchProvider() {
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
        
        String branch = commandLine.getOptionValue(OPTION_SWITCH_BRANCH);
        
        // If there is a local ref just switch to it
        // Else if there is a remote ref create new tracking branch and switch to it
        // Else create new branch at HEAD and switch to it
        
        BranchStatus status = new BranchStatus(modelFolder);
        boolean localRefExists = status.find(RepoConstants.R_HEADS + branch) != null;
        boolean remoteRefExists = status.find(RepoConstants.R_REMOTES_ORIGIN + branch) != null;
        
        if(localRefExists) {
            logMessage(NLS.bind("Switching to existing branch: {0}", branch));
        }
        else {
            logMessage(NLS.bind("Switching to and creating branch: {0}", branch));
        }
        
        try(Git git = Git.open(modelFolder)) {
            git.checkout()
                .setName(branch)
                .setCreateBranch(!localRefExists)
                .setStartPoint(remoteRefExists ? RepoConstants.R_REMOTES_ORIGIN + branch : null)
                .call();
        }
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_SWITCH_BRANCH)
                .argName("branch")
                .hasArg()
                .desc(NLS.bind("Switch to <branch> in the local repository in the <path> set in option --{0}.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER))
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    protected boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_SWITCH_BRANCH) && commandLine.hasOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
    }
    
    @Override
    public int getPriority() {
        return 15;
    }
    
    @Override
    protected String getLogPrefix() {
        return "[Switch Repository Branch]";
    }
}
