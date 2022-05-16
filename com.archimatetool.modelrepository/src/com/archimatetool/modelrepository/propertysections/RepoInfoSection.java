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

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IFilter;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.propertysections.AbstractArchiPropertySection;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelrepository.repository.ArchiRepository;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoUtils;
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
            return object instanceof RepositoryRef ||
                    (object instanceof IArchimateModel && RepoUtils.isModelInArchiRepository((IArchimateModel)object));
        }
    }
    
    private IArchiRepository fRepository;
    
    private Text textFile, textCurrentBranch;
    private UpdatingTextControl textURL;
    
    public RepoInfoSection() {
    }

    @Override
    protected void createControls(Composite parent) {
        Group group = getWidgetFactory().createGroup(parent, Messages.RepoInfoSection_3);
        group.setLayout(new GridLayout(2, false));
        GridDataFactory.create(GridData.FILL_BOTH).span(2, 1).applyTo(group);
        
        createLabel(group, Messages.RepoInfoSection_0, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textFile = createSingleTextControl(group, SWT.READ_ONLY);

        createLabel(group, Messages.RepoInfoSection_1, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textURL = new UpdatingTextControl(createSingleTextControl(group, SWT.NONE)) {
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
        
        createLabel(group, Messages.RepoInfoSection_2, STANDARD_LABEL_WIDTH, SWT.CENTER);
        textCurrentBranch = createSingleTextControl(group, SWT.READ_ONLY);
    }

    @Override
    protected void handleSelection(IStructuredSelection selection) {
        if(selection == getSelection()) {
            return;
        }
        
        if(selection.getFirstElement() instanceof RepositoryRef) {
            fRepository = ((RepositoryRef)selection.getFirstElement()).getArchiRepository();
        }
        else if(selection.getFirstElement() instanceof IArchimateModel) {
            fRepository = new ArchiRepository(RepoUtils.getWorkingFolderForModel((IArchimateModel)selection.getFirstElement()));
        }
        else {
            fRepository = null;
        }
        
        if(fRepository != null) {
            textFile.setText(fRepository.getWorkingFolder().getAbsolutePath());

            try(GitUtils utils = GitUtils.open(fRepository.getWorkingFolder())) {
                textURL.setText(StringUtils.safeString(utils.getRemoteURL()));
                textCurrentBranch.setText(StringUtils.safeString(utils.getCurrentLocalBranchName()));
            }
            catch(IOException | GitAPIException ex) {
                logger.log(Level.SEVERE, "Update info", ex); //$NON-NLS-1$
            }
        }
    }
}
