/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.osgi.util.NLS;

import com.archimatetool.commandline.AbstractCommandLineProvider;
import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator;
import com.archimatetool.modelrepository.authentication.CredentialsAuthenticator.ISSHIdentityProvider;
import com.archimatetool.modelrepository.authentication.UsernamePassword;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;

/**
 * Command Line interface for loading a repository model and cloning an Archi model from online Repository
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository.cloneModel "url"
   --modelrepository.loadModel "cloneFolder"
   --modelrepository.userName "userName"
   --modelrepository.passFile "/pathtoPasswordFile"
   --modelrepository.identityFile "/pathtoIdentityFile"
 * 
 * This will clone an online Archi model repository into clonefolder.
 * 
 * @author Phillip Beauvoir
 */
public class LoadModelFromRepositoryProvider extends AbstractCommandLineProvider {

    static final String PREFIX = "[LoadModelFromRepositoryProvider]"; //$NON-NLS-1$
    
    static final String OPTION_CLONE_MODEL = "modelrepository.cloneModel"; //$NON-NLS-1$
    static final String OPTION_LOAD_MODEL = "modelrepository.loadModel"; //$NON-NLS-1$
    static final String OPTION_USERNAME = "modelrepository.userName"; //$NON-NLS-1$
    static final String OPTION_PASSFILE = "modelrepository.passFile"; //$NON-NLS-1$
    static final String OPTION_SSH_IDENTITY_FILE = "modelrepository.identityFile"; //$NON-NLS-1$
    
    public LoadModelFromRepositoryProvider() {
    }
    
    @Override
    public void run(CommandLine commandLine) throws Exception {
        if(!hasCorrectOptions(commandLine)) {
            return;
        }
        
        // loadModel folder must be set in both cases:
        // 1. Just load an existing local repository model
        // 2. Clone repository model to folder specified in (1)
        
        String optionFolder = commandLine.getOptionValue(OPTION_LOAD_MODEL);
        if(!StringUtils.isSet(optionFolder)) {
            logError(NLS.bind(Messages.LoadModelFromRepositoryProvider_0, OPTION_LOAD_MODEL));
            return;
        }
        
        File modelFolder = new File(optionFolder);
        
        // Clone
        if(commandLine.hasOption(OPTION_CLONE_MODEL)) {
            cloneModel(commandLine, modelFolder);
        }
        
        // Load model
        loadModel(modelFolder);
    }
    
    private void cloneModel(CommandLine commandLine, File modelFolder) throws IOException, GitAPIException {
        String url = commandLine.getOptionValue(OPTION_CLONE_MODEL);
        String username = commandLine.getOptionValue(OPTION_USERNAME);
        char[] password = getPasswordFromFile(commandLine);
        File identityFile = getSSHIdentityFile(commandLine);
        
        boolean isSSH = RepoUtils.isSSH(url);
        boolean isHTTP = !isSSH;
        
        if(!StringUtils.isSet(url)) {
            logError(Messages.LoadModelFromRepositoryProvider_1);
            return;
        }
        
        // HTTP requires user name
        if(isHTTP && !StringUtils.isSet(username)) {
            logError(Messages.LoadModelFromRepositoryProvider_2);
            return;
        }
        
        // If using HTTP then password is needed for connection
        // If using SSH then password is optional for the identity file
        if(isHTTP && (password == null || password.length == 0)) {
            logError(Messages.LoadModelFromRepositoryProvider_3);
            return;
        }
        
        // SSH needs identity file
        if(isSSH && identityFile == null) {
            logError(Messages.LoadModelFromRepositoryProvider_4);
            return;
        }
        
        logMessage(NLS.bind(Messages.LoadModelFromRepositoryProvider_5, url, modelFolder));
        
        // Delete clone folder
        FileUtils.deleteFolder(modelFolder);
        
        IArchiRepository repo = new ArchiRepository(modelFolder);
        
        // SSH
        if(isSSH) {
            // Set this to return our details rather than using the defaults from App prefs
            CredentialsAuthenticator.setSSHIdentityProvider(new ISSHIdentityProvider() {
                @Override
                public File getIdentityFile() {
                    return identityFile;
                }

                @Override
                public char[] getIdentityPassword() {
                    return password;
                }
            });
            
            repo.cloneModel(url, null, null);
        }
        // HTTPS
        else {
            UsernamePassword npw = new UsernamePassword(username, password);
            try {
                repo.cloneModel(url, npw, null);
            }
            finally {
                npw.clear(); // Clear this
            }
        }
        
        logMessage(Messages.LoadModelFromRepositoryProvider_6);
    }
    
    private void loadModel(File modelFolder) throws IOException {
        IArchiRepository repo = new ArchiRepository(modelFolder);
        
        logMessage(NLS.bind(Messages.LoadModelFromRepositoryProvider_7, modelFolder));

        IArchimateModel model = IEditorModelManager.INSTANCE.load(repo.getModelFile());
        
        if(model == null) {
            throw new IOException(NLS.bind(Messages.LoadModelFromRepositoryProvider_8, modelFolder));
        }

        CommandLineState.setModel(model);
        
        logMessage(NLS.bind(Messages.LoadModelFromRepositoryProvider_9, model.getName()));
    }

    private char[] getPasswordFromFile(CommandLine commandLine) throws IOException {
        String path = commandLine.getOptionValue(OPTION_PASSFILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                // Read in as string and trim because some Linux text apps can append a new line char
                String s = Files.readString(file.toPath()).trim();
                return s.toCharArray();
            }
        }
        return null;
    }
            
    private File getSSHIdentityFile(CommandLine commandLine) {
        String path = commandLine.getOptionValue(OPTION_SSH_IDENTITY_FILE);
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.exists() && file.canRead()) {
                return file;
            }
        }
        
        return null;
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_LOAD_MODEL)
                .hasArg()
                .argName(Messages.LoadModelFromRepositoryProvider_10)
                .desc(NLS.bind(Messages.LoadModelFromRepositoryProvider_11, OPTION_CLONE_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_CLONE_MODEL)
                .hasArg()
                .argName(Messages.LoadModelFromRepositoryProvider_12)
                .desc(NLS.bind(Messages.LoadModelFromRepositoryProvider_13, OPTION_LOAD_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_USERNAME)
                .hasArg()
                .argName(Messages.LoadModelFromRepositoryProvider_14)
                .desc(NLS.bind(Messages.LoadModelFromRepositoryProvider_15, OPTION_CLONE_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_PASSFILE)
                .hasArg()
                .argName(Messages.LoadModelFromRepositoryProvider_16)
                .desc(NLS.bind(Messages.LoadModelFromRepositoryProvider_17, OPTION_CLONE_MODEL))
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_SSH_IDENTITY_FILE)
                .hasArg()
                .argName(Messages.LoadModelFromRepositoryProvider_18)
                .desc(NLS.bind(Messages.LoadModelFromRepositoryProvider_19, OPTION_CLONE_MODEL))
                .build();
        options.addOption(option);

        return options;
    }
    
    private boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_CLONE_MODEL) || commandLine.hasOption(OPTION_LOAD_MODEL);
    }
    
    @Override
    public int getPriority() {
        return PRIORITY_LOAD_OR_CREATE_MODEL;
    }
    
    @Override
    protected String getLogPrefix() {
        return PREFIX;
    }
}
