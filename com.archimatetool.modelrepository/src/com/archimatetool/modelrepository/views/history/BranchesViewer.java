/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.IOException;
import java.text.Collator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchStatus;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Branches Viewer
 * 
 * @author Phillip Beauvoir
 */
public class BranchesViewer extends ComboViewer {
    
    private static Logger logger = Logger.getLogger(BranchesViewer.class.getName());

    public BranchesViewer(Composite parent) {
        super(parent, SWT.READ_ONLY);
        
        setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if(inputElement instanceof BranchStatus branchStatus) {
                    return branchStatus.getLocalAndUntrackedRemoteBranches().toArray();
                }
                
                return new Object[0];
            }
        });
        
        
        setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                BranchInfo branchInfo = (BranchInfo)element;
                String branchName = branchInfo.getShortName();
                
                if(branchInfo.isCurrentBranch()) {
                    branchName += " " + Messages.BranchesViewer_0; //$NON-NLS-1$
                }
                
                return branchName;
            }
        });
        
        setComparator(new ViewerComparator(Collator.getInstance()) {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                BranchInfo b1 = (BranchInfo)e1;
                BranchInfo b2 = (BranchInfo)e2;
                return getComparator().compare(b1.getShortName(), b2.getShortName());
            }
        });
    }

    void setRepository(IArchiRepository archiRepo) {
        if(archiRepo == null) {
            setInput(null);
            return;
        }
        
        // Get BranchStatus
        BranchStatus branchStatus = null;
        
        try {
            branchStatus = new BranchStatus(archiRepo.getWorkingFolder(), false);
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
        }
        
        setInput(branchStatus);
        
        // Set selection to current branch
        if(branchStatus != null) {
            BranchInfo branchInfo = branchStatus.getCurrentLocalBranchInfo();
            if(branchInfo != null) {
                setSelection(new StructuredSelection(branchInfo));
            }
        }
        
        // Avoid bogus horizontal scrollbar cheese
        Display.getCurrent().asyncExec(() -> {
            if(!getControl().isDisposed()) {
                getControl().getParent().layout();
            }
        });
    }
}
