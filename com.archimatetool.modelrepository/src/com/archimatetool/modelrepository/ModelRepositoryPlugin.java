/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.preferences.IPreferenceConstants;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.IRepositoryListener;
import com.archimatetool.modelrepository.repository.RepoUtils;
import com.archimatetool.modelrepository.repository.RepositoryListenerManager;



/**
 * Activator
 * 
 * @author Phillip Beauvoir
 */
public class ModelRepositoryPlugin extends AbstractUIPlugin implements PropertyChangeListener {

    public static final String PLUGIN_ID = "com.archimatetool.modelrepository"; //$NON-NLS-1$
    
    /**
     * The shared instance
     */
    public static ModelRepositoryPlugin INSTANCE;
    
    public ModelRepositoryPlugin() {
        INSTANCE = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        IEditorModelManager.INSTANCE.addPropertyChangeListener(this);
        
        // TODO Proxy
        // Set this first
        // ProxyAuthenticator.init();
    }
    
    @Override
    public void stop(BundleContext context) throws Exception {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(this);
        super.stop(context);
    }
    
    /**
     * @return The File Location of this plugin
     */
    public File getPluginFolder() {
        URL url = getBundle().getEntry("/"); //$NON-NLS-1$
        try {
            url = FileLocator.resolve(url);
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        return new File(url.getPath());
    }
    
    /**
     * @return The folder where we store repositories
     */
    public File getUserModelRepositoryFolder() {
        // Get from preferences
        String path = getPreferenceStore().getString(IPreferenceConstants.PREFS_REPOSITORY_FOLDER);
        
        if(StringUtils.isSet(path)) {
            File file = new File(path);
            if(file.canWrite()) {
                return file;
            }
        }
        
        // Default
        path = getPreferenceStore().getDefaultString(IPreferenceConstants.PREFS_REPOSITORY_FOLDER);
        return new File(path);
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Notify on Save
        if(evt.getPropertyName().equals(IEditorModelManager.PROPERTY_MODEL_SAVED)) {
            IArchimateModel model = (IArchimateModel)evt.getNewValue();
            if(RepoUtils.isModelInLocalRepository(model)) {
                IArchiRepository repo = new ArchiRepository(RepoUtils.getLocalRepositoryFolderForModel(model));
                
                // Update the model's name in the config file
                repo.updateName();
                
                // Notify listeners that the repo changed
                RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(IRepositoryListener.REPOSITORY_CHANGED, repo);
            }
        }
    }
    
    public void log(int severity, String message, Throwable ex) {
        getLog().log(
                new Status(severity, INSTANCE.getBundle().getSymbolicName(), IStatus.OK, message, ex));
    }
}
