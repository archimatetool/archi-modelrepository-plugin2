/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.branches;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.archimatetool.editor.ui.ColorFactory;
import com.archimatetool.editor.ui.FontFactory;
import com.archimatetool.editor.ui.components.UpdatingTableColumnLayout;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchStatus;
import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * Branches Table Viewer
 */
public class BranchesTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(BranchesTableViewer.class.getName());
    
    public BranchesTableViewer(Composite parent) {
        super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.BranchesTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.BranchesTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.BranchesTableViewer_6);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.BranchesTableViewer_7);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 4);
        column.getColumn().setText(Messages.BranchesTableViewer_8);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 5);
        column.getColumn().setText(Messages.BranchesTableViewer_9);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));

        setContentProvider(new BranchesContentProvider());
        setLabelProvider(new BranchesLabelProvider());
        
        setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                BranchInfo b1 = (BranchInfo)e1;
                BranchInfo b2 = (BranchInfo)e2;
                return b1.getShortName().compareToIgnoreCase(b2.getShortName());
            }
        });
    }

    void doSetInput(IArchiRepository archiRepo) {
        setInput(archiRepo);
        
        // Do the Layout kludge
        ((UpdatingTableColumnLayout)getTable().getParent().getLayout()).doRelayout();

        // Select first row
        //Object element = getElementAt(0);
        //if(element != null) {
        //    setSelection(new StructuredSelection(element));
        //}
    }
    
    // ===============================================================================================
	// ===================================== Table Model ==============================================
	// ===============================================================================================
    
    /**
     * The Model for the Table.
     */
   private static class BranchesContentProvider implements IStructuredContentProvider {
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
        }

        @Override
        public void dispose() {
        }
        
        @Override
        public Object[] getElements(Object parent) {
            if(parent instanceof IArchiRepository) {
                IArchiRepository repo = (IArchiRepository)parent;
                
                // Local Repo was deleted
                if(!repo.getWorkingFolder().exists()) {
                    return new Object[0];
                }
                
                try {
                    BranchStatus status = new BranchStatus(repo.getWorkingFolder(), true);
                    return status.getLocalAndUntrackedRemoteBranches().toArray();
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
                }
            }
            
            return new Object[0];
        }
    }

    // ===============================================================================================
	// ===================================== Label Model ==============================================
	// ===============================================================================================

    private static class BranchesLabelProvider extends CellLabelProvider {
        
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        
        public String getColumnText(BranchInfo branchInfo, int columnIndex) {
            RevCommit latestCommit = branchInfo.getLatestCommit();
            
            switch(columnIndex) {
                case 0:
                    String name = branchInfo.getShortName();
                    if(branchInfo.isCurrentBranch()) {
                        name += " " + Messages.BranchesTableViewer_2; //$NON-NLS-1$
                    }
                    return name;

                case 1:
                    if(branchInfo.isRemoteDeleted()) {
                        return Messages.BranchesTableViewer_3;
                    }
                    if(branchInfo.hasRemoteRef()) {
                        return Messages.BranchesTableViewer_4;
                    }
                    else {
                        return Messages.BranchesTableViewer_5;
                    }
                 
                case 2:
                    return latestCommit == null ? "" : latestCommit.getCommitterIdent().getName(); //$NON-NLS-1$
                    
                case 3:
                    return latestCommit == null ? "" : dateFormat.format(new Date(latestCommit.getCommitTime() * 1000L)); //$NON-NLS-1$
                    
                case 4:
                    String text;
                    
                    if(branchInfo.hasUnpushedCommits()) {
                        text = Messages.BranchesTableViewer_10;
                    }
                    else if(branchInfo.hasRemoteCommits() || branchInfo.isRemote()) {
                        text = Messages.BranchesTableViewer_11;
                    }
                    else if(branchInfo.hasUnpushedCommits() && branchInfo.hasRemoteCommits()) {
                        text = Messages.BranchesTableViewer_12;
                    }
                    else if(branchInfo.hasUnpushedCommits() && branchInfo.hasRemoteCommits()) {
                        text = Messages.BranchesTableViewer_13;
                    }
                    else {
                        text = Messages.BranchesTableViewer_14;
                    }
                    
                    return text;
                    
                case 5:
                    return branchInfo.isMerged() ? Messages.BranchesTableViewer_15 : Messages.BranchesTableViewer_16;
                    
                default:
                    return ""; //$NON-NLS-1$
            }
        }

        @Override
        public void update(ViewerCell cell) {
            // Need to clear this first
            cell.setForeground(null);
            
            if(cell.getElement() instanceof BranchInfo) {
                BranchInfo branchInfo = (BranchInfo)cell.getElement();
                
                cell.setText(getColumnText(branchInfo, cell.getColumnIndex()));
                
                if(branchInfo.isCurrentBranch() && cell.getColumnIndex() == 0) {
                    cell.setFont(FontFactory.SystemFontBold);
                }
                else {
                    cell.setFont(null);
                }
                
                // Red text for "deleted" branches
                if(branchInfo.isRemoteDeleted()) {
                    cell.setForeground(ColorFactory.get(255, 64, 0));
                }
                
                switch(cell.getColumnIndex()) {
                    case 0:
                        cell.setImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_BRANCH));
                        break;

                    default:
                        break;
                }
            }
        }
    }
}
