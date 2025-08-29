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

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.authentication.ICredentials;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Command Line interface for pushin a repository model to an online Repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository2.pushModel
   --modelrepository2.modelFolder "pathtoFolder"
   --modelrepository2.userName "userName"
   --modelrepository2.passFile "pathtoPasswordFile"
   --modelrepository2.identityFile "pathtoIdentityFile"
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class PushModelProvider extends AbstractModelRepositoryProvider {

    public static final String OPTION_PUSH_MODEL = "modelrepository2.pushModel"; //$NON-NLS-1$
    
    public PushModelProvider() {
    }
    
    @Override
    public void run(CommandLine commandLine) throws Exception {
        if(!hasCorrectOptions(commandLine)) {
            return;
        }
        
        File folder = getFolderOption(commandLine);
        if(folder == null) {
            return;
        }
        
        IArchiRepository repository = new ArchiRepository(folder);
        
        String url = repository.getRemoteURL();
        if(!StringUtils.isSet(url)) {
            logError("No URL set.");
            return;
        }
        
        if(!checkCredentials(commandLine, url)) {
            return;
        }
        
        ICredentials credentials = getCredentials(commandLine, url);
        
        try {
            logMessage(NLS.bind("Pushing from {0} to {1}", folder, url));
            repository.pushToRemote(credentials.getCredentialsProvider(), new CLIProgressMonitor());
        }
        finally {
            if(credentials instanceof UsernamePassword npw) {
                npw.clear(); // Clear this
            }
        }
        
        logMessage("Model pushed!");
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_PUSH_MODEL)
                .desc("Push a model from folder <path> to remote repository.")
                .desc(NLS.bind("Push a model in the folder <path> set in option --{0} to the remote repository.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER))
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    protected boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_PUSH_MODEL) && commandLine.hasOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
    }
    
    @Override
    public int getPriority() {
        return 40;
    }
    
    @Override
    protected String getLogPrefix() {
        return "[PushModelToRepositoryProvider]";
    }
}
