/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.history;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.ui.components.UpdatingTableColumnLayout;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchStatus;
import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * History Table Viewer
 */
public class HistoryTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(HistoryTableViewer.class.getName());
    
    private RevCommit fLocalCommit, fOriginCommit;
    private BranchInfo fSelectedBranch;
    
    public HistoryTableViewer(Composite parent) {
        super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
        
        setup(parent);
        
        setContentProvider(new HistoryContentProvider());
        setLabelProvider(new HistoryLabelProvider());
        
        ColumnViewerToolTipSupport.enableFor(this);
        
        setUseHashlookup(true);
    }

    private void setup(Composite parent) {
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.HistoryTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.HistoryTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(50, false));

        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.HistoryTableViewer_2);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.HistoryTableViewer_3);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    }
    
    void doSetInput(IArchiRepository archiRepo) {
        // Get BranchStatus and currentLocalBranch
        try {
            BranchStatus branchStatus = new BranchStatus(archiRepo.getWorkingFolder());
            if(branchStatus != null) {
                fSelectedBranch = branchStatus.getCurrentLocalBranch();
            }
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
        }

        setInput(archiRepo);
        
        // Do the Layout kludge
        ((UpdatingTableColumnLayout)getTable().getParent().getLayout()).doRelayout();

        // Select first row. This will ensure we only load the first few commits
        Object element = getElementAt(0);
        if(element != null) {
            setSelection(new StructuredSelection(element), true);
        }
    }
    
    void setSelectedBranch(BranchInfo branchInfo) {
        if(branchInfo != null && branchInfo.equals(fSelectedBranch)) {
            return;
        }

        fSelectedBranch = branchInfo;
        
        setInput(getInput());
        
        // Layout kludge
        ((UpdatingTableColumnLayout)getTable().getParent().getLayout()).doRelayout();
        
        // Select first row. This will ensure we only load the first few commits
        Object element = getElementAt(0);
        if(element != null) {
            setSelection(new StructuredSelection(element), true);
        }
    }
    
    // ===============================================================================================
    // ===================================== Table Model =============================================
    // ===============================================================================================
    
    /**
     * The Model for the Table.
     */
    private class HistoryContentProvider implements ILazyContentProvider {
        List<RevCommit> commits;
        RevWalk theRevWalk;
        
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            dispose();
            
            if(!(newInput instanceof IArchiRepository
                    && ((IArchiRepository)newInput).getWorkingFolder().exists() // Local Repo might have been deleted
                    && fSelectedBranch != null)) {
                setItemCount(0);
                return;
            }
            
            commits = new ArrayList<>();

            try(Repository repository = Git.open(((IArchiRepository)newInput).getWorkingFolder()).getRepository()) {
                setItemCount(getCommitCount(repository));
                theRevWalk = getRevWalk(repository);
            }
            catch(IOException ex) {
                setItemCount(0);
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Rev Walk", ex); //$NON-NLS-1$
            }
        }
        
        RevWalk getRevWalk(Repository repository) throws IOException {
            try(RevWalk revWalk = new RevWalk(repository)) {
                // Find the local branch commit start
                ObjectId localCommitID = repository.resolve(fSelectedBranch.getLocalBranchNameFor());
                if(localCommitID != null) {
                    fLocalCommit = revWalk.parseCommit(localCommitID);
                    revWalk.markStart(fLocalCommit);
                }

                // Find the remote branch commit start
                ObjectId remoteCommitID = repository.resolve(fSelectedBranch.getRemoteBranchNameFor());
                if(remoteCommitID != null) {
                    fOriginCommit = revWalk.parseCommit(remoteCommitID);
                    revWalk.markStart(fOriginCommit);
                }

                return revWalk;
            } // close the RevWalk
        }
        
        int getCommitCount(Repository repository) throws IOException {
            RevWalk revWalk = getRevWalk(repository);
            revWalk.setRetainBody(false); // Don't need this for the general RevWalk
            
            // Count the commits
            int count = 0;
            while(revWalk.next() != null) {
                count++;
            }
            
            revWalk.dispose();
            
            return count;
        }

        @Override
        public void updateElement(int index) {
            // Lazily load the RevCommits into the list
            while(commits.size() <= index) {
                try {
                    commits.add(theRevWalk.next());
                }
                catch(IOException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Rev Walk", ex); //$NON-NLS-1$
                }
            }
            
            replace(commits.get(index), index);
        }

        @Override
        public void dispose() {
            if(theRevWalk != null) {
                theRevWalk.dispose();
                theRevWalk = null;
            }
            
            commits = null;
            fLocalCommit = null;
            fOriginCommit = null;
        }
    }
    
    // ===============================================================================================
	// ===================================== Label Model =============================================
	// ===============================================================================================

    private class HistoryLabelProvider extends CellLabelProvider {
        
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        
        public String getColumnText(RevCommit commit, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    return commit.getName().substring(0, 8);
                    
                case 1:
                    return commit.getShortMessage();
                    
                case 2:
                    return commit.getAuthorIdent().getName();
                
                case 3:
                    return dateFormat.format(new Date(commit.getCommitTime() * 1000L));
                    
                default:
                    return null;
            }
        }

        @Override
        public void update(ViewerCell cell) {
            if(cell.getElement() instanceof RevCommit) {
                RevCommit commit = (RevCommit)cell.getElement();
                
                cell.setText(getColumnText(commit, cell.getColumnIndex()));
                
                if(cell.getColumnIndex() == 1) {
                    Image image = null;
                    
                    if(commit.equals(fLocalCommit) && commit.equals(fOriginCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_HISTORY_VIEW);
                    }
                    else if(commit.equals(fOriginCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_REMOTE);
                    }
                    else if(commit.equals(fLocalCommit)) {
                        image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_LOCAL);
                    }
                    
                    cell.setImage(image);
                }
            }
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(element instanceof RevCommit) {
                RevCommit commit = (RevCommit)element;
                
                String s = ""; //$NON-NLS-1$
                
                if(commit.equals(fLocalCommit) && commit.equals(fOriginCommit)) {
                    s += Messages.HistoryTableViewer_4 + " "; //$NON-NLS-1$
                }
                else if(commit.equals(fLocalCommit)) {
                    s += Messages.HistoryTableViewer_5 + " "; //$NON-NLS-1$
                }
                else if(commit.equals(fOriginCommit)) {
                    s += Messages.HistoryTableViewer_6 + " "; //$NON-NLS-1$
                }
                
                s += commit.getShortMessage().trim();
                
                return s;
            }
            
            return null;
        }
    }
}
