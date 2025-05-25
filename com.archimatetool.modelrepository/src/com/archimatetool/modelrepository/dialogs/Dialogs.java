/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/**
 * Various dialogs
 * 
 * @author Phillip Beauvoir
 */
public class Dialogs {
    
    /**
     * Open a YES/NO/CANCEL dialog 
     * @param parentShell Parent shell
     * @param dialogTitle Title
     * @param message Message
     * @return SWT.YES, SWT.NO or SWT.CANCEL
     */
    public static int openYesNoCancelDialog(Shell parentShell, String dialogTitle, String message) {
        switch(MessageDialog.open(MessageDialog.CONFIRM,
                parentShell,
                dialogTitle,
                message,
                SWT.NONE,
                IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL)) {
            case 0:
                return SWT.YES;
            case 1:
                return SWT.NO;
            default:
                return SWT.CANCEL;
        }
    }
}
