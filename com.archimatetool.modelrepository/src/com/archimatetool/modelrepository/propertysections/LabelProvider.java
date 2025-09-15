/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;

import com.archimatetool.modelrepository.treemodel.IModelRepositoryTreeEntry;


/**
 * Label Provider for property section
 * 
 * @author Phillip Beauvoir
 */
public class LabelProvider implements ILabelProvider {
    
    /**
     * Adapter factory to return this LabelProvider for the host Archi Property section.
     * This IAdapterFactory is registered in plugin.xml
     */
    public static class LabelProviderAdapterFactory implements IAdapterFactory {
        private LabelProvider labelProvider = new LabelProvider();
        
        @Override
        public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
            if(adapterType == ILabelProvider.class) {
                return adapterType.cast(labelProvider);
            }
            
            return null;            
        }
    }

    @Override
    public Image getImage(Object element) {
        return element instanceof IModelRepositoryTreeEntry entry ? entry.getImage() : null;
    }

    @Override
    public String getText(Object element) {
        return element instanceof IModelRepositoryTreeEntry entry ? entry.getName() : null;
    }

    @Override
    public boolean isLabelProperty(Object element, String property) {
        return false;
    }

    @Override
    public void addListener(ILabelProviderListener listener) {
    }

    @Override
    public void removeListener(ILabelProviderListener listener) {
    }
    
    @Override
    public void dispose() {
    }
}
