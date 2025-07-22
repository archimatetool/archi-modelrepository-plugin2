/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Abstract Repository Action
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractRepositoryAction extends Action implements IRepositoryAction {
    
    protected IArchiRepository archiRepository;
	protected IWorkbenchWindow workbenchWindow;
	
	protected AbstractRepositoryAction(IWorkbenchWindow workbenchWindow) {
	    this.workbenchWindow = workbenchWindow;
	}
	
	@Override
	public void setRepository(IArchiRepository archiRepository) {
	    this.archiRepository = archiRepository;
	    update();
	}
	
    @Override
	public void update() {
        setEnabled(shouldBeEnabled());
	}
	 
	/**
	 * @return true if this action should be enabled
	 */
	protected boolean shouldBeEnabled() {
	    return archiRepository != null && archiRepository.getWorkingFolder().exists();
	}
}
