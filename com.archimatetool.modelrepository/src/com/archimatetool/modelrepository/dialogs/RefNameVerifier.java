/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Verify that a ref name (tag, branch) is valid
 * 
 * @author Phil Beauvoir
 */
public abstract class RefNameVerifier implements VerifyListener {
    
    @Override
    public void verifyText(VerifyEvent event) {
        // Replace space ^ ~ * ? [ \ 
        event.text = event.text.replaceAll("[ :^~*\\?\\[\\\\]", "_"); //$NON-NLS-1$ //$NON-NLS-2$

        Text control = (Text)event.widget;
        String currentText = control.getText();
        String newText = (currentText.substring(0, event.start) + event.text + currentText.substring(event.end));
        
        String errorMessage = null;
        boolean isValid = !newText.isEmpty();

        if(isValid) {
            isValid = Repository.isValidRefName(RepoConstants.R_HEADS + newText);
            
            if(!isValid) {
                if(newText.contains("..")) { //$NON-NLS-1$
                    errorMessage = Messages.RefNameVerifier_4;
                }
                else if(newText.startsWith(".") || newText.endsWith(".")) { //$NON-NLS-1$ //$NON-NLS-2$
                    errorMessage = Messages.RefNameVerifier_0;
                }
                else if(newText.startsWith("/") || newText.endsWith("/")) { //$NON-NLS-1$ //$NON-NLS-2$
                    errorMessage = Messages.RefNameVerifier_1;
                }
                else if(newText.endsWith(".lock")) { //$NON-NLS-1$
                    errorMessage = Messages.RefNameVerifier_3;
                }
                else {
                    errorMessage = Messages.RefNameVerifier_2;
                }
            }
        }
        
        verify(errorMessage, isValid);
    }

    /**
     * Sub-classes over-ride this to set the error message and check if name is valid
     */
    public abstract void verify(String errorMessage, boolean isValid);
}