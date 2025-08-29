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

import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.ArchiRepository;

/**
 * Command Line interface for loading a repository model and cloning from online Repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository2.cloneModel "url"
   --modelrepository2.modelFolder "pathtoFolder"
   --modelrepository2.userName "userName"
   --modelrepository2.passFile "pathtoPasswordFile"
   --modelrepository2.identityFile "pathtoIdentityFile"
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CloneModelProvider extends AbstractModelRepositoryProvider {

    public static final String OPTION_CLONE_MODEL = "modelrepository2.cloneModel"; //$NON-NLS-1$
    
    public CloneModelProvider() {
    }
    
    @Override
    public void run(CommandLine commandLine) throws Exception {
        if(!hasCorrectOptions(commandLine)) {
            return;
        }
        
        String url = commandLine.getOptionValue(OPTION_CLONE_MODEL);
        if(!StringUtils.isSet(url)) {
            logError("No URL set.");
            return;
        }
        
        File folder = getFolderOption(commandLine);
        if(folder == null) {
            return;
        }
        
        if(!checkCredentials(commandLine, url)) {
            return;
        }
        
        ICredentials credentials = getCredentials(commandLine, url);
        
        try {
            // Delete target folder first in case it's not empty
            logMessage(NLS.bind("Deleting target folder {0}", folder));
            FileUtils.deleteFolder(folder);
            
            logMessage(NLS.bind("Cloning from {0} to {1}", url, folder));
            new ArchiRepository(folder).cloneModel(url, credentials.getCredentialsProvider(), new CLIProgressMonitor());
        }
        finally {
            if(credentials instanceof UsernamePassword npw) {
                npw.clear(); // Clear this
            }
        }
        
        logMessage("Model cloned!");
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_CLONE_MODEL)
                .hasArg()
                .argName("url")
                .desc(NLS.bind("Clone a model from remote <url> to the folder <path> set in option --{0}.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER))
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    protected boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_CLONE_MODEL) && commandLine.hasOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
    
    @Override
    protected String getLogPrefix() {
        return "[Clone Repository Model]";
    }
}
