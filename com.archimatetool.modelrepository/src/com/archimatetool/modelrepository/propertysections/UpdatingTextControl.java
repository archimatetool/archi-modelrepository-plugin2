/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;



/**
 * Wrapper Control for Text or StyledText Control to retrieve and update a text value
 * 
 * TODO: Move this to Main Archi code
 * 
 * @author Phillip Beauvoir
 */
public abstract class UpdatingTextControl {
    
    private Control fTextControl;
    private Listener eventListener = this::handleEvent;
    
    /**
     * @param textControl The Text Control
     */
    public UpdatingTextControl(Control textControl) {
        fTextControl = textControl;
        
        // FocusOut updates the text
        textControl.addListener(SWT.FocusOut, eventListener);
        
        // Listen for Enter (or Ctrl+Enter) keypress
        textControl.addListener(SWT.DefaultSelection, eventListener);
        
        textControl.addDisposeListener((event)-> {
            textControl.removeListener(SWT.FocusOut, eventListener);
            textControl.removeListener(SWT.DefaultSelection, eventListener);
        });
    }
    
    public Control getTextControl() {
        return fTextControl;
    }
    
    public void setEditable(boolean editable) {
        if(isStyledTextControl()) {
            ((StyledText)getTextControl()).setEditable(editable);
        }
        else {
            ((Text)getTextControl()).setEditable(editable);
        }
    }
    
    private void handleEvent(Event event) {
        textChanged(getText());
    }
    
    public String getText() {
        if(isStyledTextControl()) {
            return ((StyledText)getTextControl()).getText();
        }
        else {
            return ((Text)getTextControl()).getText();
        }
    }
    
    public void setText(String s) {
        if(isStyledTextControl()) {
            ((StyledText)getTextControl()).setText(s);
        }
        else {
            ((Text)getTextControl()).setText(s);
        }
    }
    
    private boolean isStyledTextControl() {
        return getTextControl() instanceof StyledText;
    }

    /**
     * Clients must over-ride this to react to text changes
     * @param newText The new changed text
     */
    protected abstract void textChanged(String newText);
}
