/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import org.eclipse.swt.graphics.Image;

import com.archimatetool.editor.ui.IArchiLabelProvider;
import com.archimatetool.modelrepository.treemodel.IModelRepositoryTreeEntry;


public class LabelProvider implements IArchiLabelProvider {

    @Override
    public Image getImage(Object element) {
        if(element instanceof IModelRepositoryTreeEntry entry) {
            return entry.getImage();
        }
        
        return null;
    }

    @Override
    public String getLabel(Object element) {
        if(element instanceof IModelRepositoryTreeEntry entry) {
            return entry.getName();
        }
        
        return null;
    }
}
