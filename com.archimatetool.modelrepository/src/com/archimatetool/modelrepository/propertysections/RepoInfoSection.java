/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;

import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Property Section for Repo information
 * 
 * @author Phillip Beauvoir
 */
public class RepoInfoSection extends AbstractArchiPropertySection {
    
    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof RepositoryRef;
        }
    }
    
    private Text fTextFile, fTextURL, fTextCurrentBranch;
    
    public RepoInfoSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        createLabel(parent, Messages.RepoInfoSection_0, STANDARD_LABEL_WIDTH, SWT.CENTER);
        fTextFile = createSingleTextControl(parent, SWT.READ_ONLY);

        createLabel(parent, Messages.RepoInfoSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        fTextURL = createSingleTextControl(parent, SWT.READ_ONLY);
        
        createLabel(parent, Messages.RepoInfoSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        fTextCurrentBranch = createSingleTextControl(parent, SWT.READ_ONLY);
    }

    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection != getSelection() && selection.getFirstElement() instanceof RepositoryRef) {
            IArchiRepository repo = ((RepositoryRef)selection.getFirstElement()).getArchiRepository();
            
            try {
                fTextFile.setText(repo.getLocalRepositoryFolder().getAbsolutePath());
                fTextURL.setText(StringUtils.safeString(repo.getOnlineRepositoryURL()));
                fTextCurrentBranch.setText(StringUtils.safeString(repo.getCurrentLocalBranchName()));
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
