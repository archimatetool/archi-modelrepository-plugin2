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

import com.archimatetool.commandline.CommandLineState;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;

/**
 * Command Line interface for loading a repository model
 * 
 * Usage - (should be all on one line):
 * 
 * Archi -consoleLog -nosplash -application com.archimatetool.commandline.app
   --modelrepository2.modelFolder "modelFolder"
   --modelrepository2.loadModel
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class LoadModelProvider extends AbstractModelRepositoryProvider {

    public static final String OPTION_LOAD_MODEL = "modelrepository2.loadModel"; //$NON-NLS-1$

    public LoadModelProvider() {
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
        
        logMessage(NLS.bind("Loading model at {0}", modelFolder));
        IArchimateModel model = IEditorModelManager.INSTANCE.load(new ArchiRepository(modelFolder).getModelFile());
        
        if(model == null) {
            throw new IOException(NLS.bind("Model was not found at {0}!", modelFolder));
        }

        CommandLineState.setModel(model);
        
        logMessage(NLS.bind("Loaded model: {0}", model.getName()));
    }
    
    @Override
    public Options getOptions() {
        Options options = new Options();
        
        Option option = Option.builder()
                .longOpt(OPTION_LOAD_MODEL)
                .desc(NLS.bind("Load a model from the local repository in the <path> set in option --{0}.", CoreModelRepositoryProvider.OPTION_MODEL_FOLDER))
                .build();
        options.addOption(option);
        
        return options;
    }
    
    @Override
    protected boolean hasCorrectOptions(CommandLine commandLine) {
        return commandLine.hasOption(OPTION_LOAD_MODEL) && commandLine.hasOption(CoreModelRepositoryProvider.OPTION_MODEL_FOLDER);
    }
    
    @Override
    public int getPriority() {
        return 20;
    }
    
    @Override
    protected String getLogPrefix() {
        return "[LoadModelFromRepositoryProvider]";
    }
}
