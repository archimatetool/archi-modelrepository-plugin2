/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.propertysections;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.treemodel.RepositoryRef;


/**
 * Property Section for Repo information
 * 
 * @author Phillip Beauvoir
 */
public class RepoInfoSection extends AbstractArchiPropertySection {
    
    private static Logger logger = Logger.getLogger(RepoInfoSection.class.getName());

    public static class Filter implements IFilter {
        @Override
        public boolean select(Object object) {
            return object instanceof RepositoryRef;
        }
    }
    
    private IArchiRepository fRepository;
    
    private Text textFile, textCurrentBranch;
    private UpdatingTextControl textURL;
    
    public RepoInfoSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        createLabel(parent, Messages.RepoInfoSection_0, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textFile = createSingleTextControl(parent, SWT.READ_ONLY);

        createLabel(parent, Messages.RepoInfoSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textURL = new UpdatingTextControl(createSingleTextControl(parent, SWT.NONE)) {
            @Override
            protected void textChanged(String newText) {
                if(fRepository != null) {
                    try {
                        logger.info("Setting remote URL to: " + newText); //$NON-NLS-1$
                        fRepository.setRemote(newText);
                    }
                    catch(IOException | GitAPIException | URISyntaxException ex) {
                        logger.log(Level.SEVERE, "Set Remote", ex); //$NON-NLS-1$
                    }
                }
            }
        };
        
        createLabel(parent, Messages.RepoInfoSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textCurrentBranch = createSingleTextControl(parent, SWT.READ_ONLY);
    }

    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection == getSelection()) {
            return;
        }
        
        if(selection.getFirstElement() instanceof RepositoryRef) {
            fRepository = ((RepositoryRef)selection.getFirstElement()).getArchiRepository();
            
            textFile.setText(fRepository.getWorkingFolder().getAbsolutePath());

            try(GitUtils utils = GitUtils.open(fRepository.getWorkingFolder())) {
                textURL.setText(StringUtils.safeString(utils.getRemoteURL()));
                textCurrentBranch.setText(StringUtils.safeString(utils.getCurrentLocalBranchName()));
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Update info", ex); //$NON-NLS-1$
            }
        }
        else {
            fRepository = null;
        }
    }
}
