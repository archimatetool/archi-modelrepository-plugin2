/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.modelrepository.repository.CommitManifest;

/**
 * Message Viewer for RevCommit and RevTag
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class RevMessageViewer {

    private StyledText styledText;
    
    // Set to true to show the manifest in commits
    private boolean showManifest = Platform.inDevelopmentMode();
    
    public RevMessageViewer(Composite parent) {
        styledText = new StyledText(parent, SWT.V_SCROLL | SWT.READ_ONLY | SWT.WRAP | SWT.BORDER);
        styledText.setLayoutData(new GridData(GridData.FILL_BOTH));
        styledText.setMargins(5, 5, 5, 5);
        styledText.setFont(JFaceResources.getFontRegistry().get(JFaceResources.TEXT_FONT));
    }
    
    public void setRevObject(RevObject revObject) {
        if(revObject == null) {
            styledText.setText("");
            return;
        }
        
        String message = "";
        
        if(revObject instanceof RevCommit revCommit) {
            message = showManifest ? revCommit.getFullMessage() : CommitManifest.getCommitMessageWithoutManifest(revCommit.getFullMessage());
        }
        else if(revObject instanceof RevTag revTag) {
            message = revTag.getFullMessage();
        }
        
        styledText.setText(message);

        // The first line is bold
        int firstLineLength = message.indexOf('\n');
        firstLineLength = firstLineLength == -1 ? message.length() : firstLineLength;

        StyleRange style = new StyleRange();
        style.start = 0;
        style.length = firstLineLength;
        style.fontStyle = SWT.BOLD;
        styledText.setStyleRange(style);
    }
}
