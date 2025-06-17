/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.commandline;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.archimatetool.commandline.AbstractCommandLineProvider;

/**
 * Core Model Command Line Provider
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CoreModelRepositoryProvider extends AbstractCommandLineProvider {
    
    public static final String OPTION_USERNAME = "modelrepository2.userName"; //$NON-NLS-1$
    public static final String OPTION_PASSFILE = "modelrepository2.passFile"; //$NON-NLS-1$
    public static final String OPTION_SSH_IDENTITY_FILE = "modelrepository2.identityFile"; //$NON-NLS-1$
    public static final String OPTION_MODEL_FOLDER = "modelrepository2.modelFolder"; //$NON-NLS-1$
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_USERNAME)
                .argName("user_name")
                .hasArg()
                .desc("Online repository login user name. Used with HTTP protocol.")
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_PASSFILE)
                .argName("password_file")
                .hasArg()
                .desc("Path to a file containing the HTTP login password or the password to the SSH identity file.")
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_SSH_IDENTITY_FILE)
                .argName("identity_file")
                .hasArg()
                .desc("Path to SSH identity file. Used with SSH protocol.")
                .build();
        options.addOption(option);
        
        option = Option.builder()
                .longOpt(OPTION_MODEL_FOLDER)
                .argName("path")
                .hasArg()
                .desc("Set the model repository folder at <path>.")
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
 }
