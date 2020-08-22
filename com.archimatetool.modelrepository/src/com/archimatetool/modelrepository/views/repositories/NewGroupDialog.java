/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.repositories;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;


/**
 * "New Group" Dialog
 */
public class NewGroupDialog {
	
    /**
     * Owner Shell
     */
    private Shell fShell;
    
    /**
     * The new Group Name
     */
    private String fNewGroupName;
    
	/**
	 * Constructor
	 */
	public NewGroupDialog(Shell shell) {
	    fShell = shell;
	}
	
    /**
     * @return The new Group name null if not set
     */
    public String getGroupName() {
        return fNewGroupName;
    }
	
    /**
     * Throw up a dialog asking for a Resource Group name
     * @return True if the user entered valid input, false if cancelled
     */
    public boolean open() {
        InputDialog dialog = new InputDialog(fShell,
                Messages.NewGroupDialog_0,
                Messages.NewGroupDialog_1,
                "New Group", //$NON-NLS-1$
                new InputValidator());
        
        int code = dialog.open();
        
        if(code == Window.OK) {
            fNewGroupName = dialog.getValue();
            return true;
        }
        else {
            return false;
        }
    }
    
    /**
     * Validate user input
     */
    private class InputValidator implements IInputValidator {
        
        @Override
        public String isValid(String newText) {
            if("".equals(newText.trim())) { //$NON-NLS-1$
                return Messages.NewGroupDialog_2;
            }
            
            return null;
        }
    }
}
