/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.editor.diagram.IDiagramModelEditor;
import com.archimatetool.editor.ui.services.EditorManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.util.ArchimateModelUtils;

/**
 * Manages restoring open editors when a model is closed and re-opened
 */
@SuppressWarnings("nls")
public class OpenEditorManager {
    
    private static Logger logger = Logger.getLogger(OpenEditorManager.class.getName());
    
    private String activeDiagramModelID;
    private List<String> openDiagramModelIDs;
    
    /**
     * Store the ids of any open diagram editors
     */
    void saveEditors(IArchimateModel model) {
        logger.info(NLS.bind("Saving open editors for ''{0}''", model.getName()));
        
        openDiagramModelIDs = new ArrayList<String>();
        
        // Store the active editor, if any
        IEditorPart activeEditor = getActivePage().getActiveEditor();
        
        for(IEditorReference ref : getActivePage().getEditorReferences()) {
            try {
                IEditorInput input = ref.getEditorInput();
                if(input instanceof DiagramEditorInput) {
                    IDiagramModel dm = ((DiagramEditorInput)input).getDiagramModel();
                    if(dm != null && dm.getArchimateModel() == model) {
                        // Add to list
                        openDiagramModelIDs.add(dm.getId());

                        // Active Editor
                        if(ref.getPart(false) == activeEditor) {
                            activeDiagramModelID = dm.getId();
                        }
                    }
                }
            }
            catch(PartInitException ex) {
                logger.log(Level.SEVERE, "Save Editors", ex);
            }
        }
    }

    /**
     * Re-open any diagram editors in the re-opened model
     */
    void restoreEditors(IArchimateModel model) {
        logger.info(NLS.bind("Restoring open editors for ''{0}''", model.getName()));
        
        IDiagramModelEditor activeEditor = null;
        
        if(openDiagramModelIDs != null) {
            for(String id : openDiagramModelIDs) {
                EObject eObject = ArchimateModelUtils.getObjectByID(model, id);
                if(eObject instanceof IDiagramModel) {
                    IDiagramModelEditor editor = EditorManager.openDiagramEditor((IDiagramModel)eObject, false);
                    
                    // Active Editor
                    if(id == activeDiagramModelID) {
                        activeEditor = editor;
                    }
                }
            }
        }
        
        if(activeEditor != null) {
            getActivePage().activate(activeEditor);
        }
    }
    
    private IWorkbenchPage getActivePage() {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    }
}
