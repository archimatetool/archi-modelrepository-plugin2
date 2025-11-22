/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.workflows;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.editor.diagram.IDiagramModelEditor;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.ui.services.EditorManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.util.ArchimateModelUtils;

/**
 * Saves and restores the state of a model if it's open in the Models tree
 * and restores any open editors when the model is closed and re-opened
 */
@SuppressWarnings("nls")
public class OpenModelState {
    
    private static Logger logger = Logger.getLogger(OpenModelState.class.getName());
    
    private File modelFile;
    
    private String activeDiagramModelID;
    private List<String> openDiagramModelIDs;
    private IEditorPart activeEditor; // This might be an editor from a different model
    
    // If this is false it means that the model was open in the Models Tree, dirty, and the user cancelled saving it
    private boolean modelClosed = true;
    
    /**
     * Close the model if it's open in the Models Tree
     */
    OpenModelState closeModel(IArchimateModel model, boolean askSaveModel) {
        if(model == null) {
            return this;
        }
        
        modelFile = model.getFile();

        // Store any open diagrams
        saveOpenEditors(model);

        try {
            // Close it
            logger.info("Closing model...");
            modelClosed = IEditorModelManager.INSTANCE.closeModel(model, askSaveModel);
            logger.info(modelClosed ? "Closed Model" : "User cancelled");
        }
        catch(IOException ex) {
            logger.log(Level.SEVERE, "Closing model", ex);
        }
        
        return this;
    }
    
    Optional<IArchimateModel> restoreModel() {
        IArchimateModel model = null;
        
        if(modelFile != null && modelClosed) {
            logger.info("Restoring model");
            model = IEditorModelManager.INSTANCE.openModel(modelFile);
            if(model != null) {
                restoreOpenEditors(model);
            }
        }
        
        return Optional.ofNullable(model);
    }

    /**
     * @return The result of asking user if the model should be saved
     *         true if user cancelled
     */
    boolean cancelled() {
        return !modelClosed;
    }
    
    /**
     * Store the ids of any open diagram editors
     */
    private void saveOpenEditors(IArchimateModel model) {
        logger.info(NLS.bind("Saving open editors for ''{0}''", model.getName()));
        
        openDiagramModelIDs = new ArrayList<>();
        
        // Store the active editor, if any
        activeEditor = getActivePage().getActiveEditor();
        
        for(IEditorReference ref : getActivePage().getEditorReferences()) {
            try {
                if(ref.getEditorInput() instanceof DiagramEditorInput input) {
                    IDiagramModel dm = input.getDiagramModel();
                    if(dm != null && dm.getArchimateModel() == model) {
                        // Add to list
                        openDiagramModelIDs.add(dm.getId());

                        // Active Editor is one that we will close
                        if(ref.getPart(false) == activeEditor) {
                            activeDiagramModelID = dm.getId();
                        }
                    }
                }
            }
            catch(PartInitException ex) {
                logger.log(Level.SEVERE, "Save Open Editors", ex);
            }
        }
    }

    /**
     * Re-open any diagram editors in the re-opened model
     */
    private void restoreOpenEditors(IArchimateModel model) {
        if(model == null) {
            return;
        }
        
        if(openDiagramModelIDs != null) {
            logger.info(NLS.bind("Restoring open editors for ''{0}''", model.getName()));
            
            for(String id : openDiagramModelIDs) {
                EObject eObject = ArchimateModelUtils.getObjectByID(model, id);
                if(eObject instanceof IDiagramModel dm) {
                    IDiagramModelEditor editor = EditorManager.openDiagramEditor(dm, false);
                    
                    // Active Editor
                    if(id == activeDiagramModelID) {
                        activeEditor = editor;
                    }
                }
            }
            
            if(activeEditor != null) {
                getActivePage().bringToTop(activeEditor);
            }
        }
    }
    
    private IWorkbenchPage getActivePage() {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }
}
