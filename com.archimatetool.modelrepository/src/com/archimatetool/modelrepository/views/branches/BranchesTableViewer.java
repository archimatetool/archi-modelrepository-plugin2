/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.branches;

import java.io.IOException;
import java.text.Collator;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.ui.FontFactory;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.BranchInfo.Option;
import com.archimatetool.modelrepository.repository.BranchStatus;
import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * Branches Table Viewer
 */
public class BranchesTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(BranchesTableViewer.class.getName());
    
    public BranchesTableViewer(Composite parent) {
        super(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.BranchesTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
        
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
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
        
        column = new TableViewerColumn(this, SWT.NONE, 5);
        column.getColumn().setText(Messages.BranchesTableViewer_9);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));

        setContentProvider(new BranchesContentProvider());
        setLabelProvider(new BranchesLabelProvider());
        
        setComparator(new ViewerComparator(Collator.getInstance()) {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                BranchInfo b1 = (BranchInfo)e1;
                BranchInfo b2 = (BranchInfo)e2;
                return getComparator().compare(b1.getShortName(), b2.getShortName());
            }
        });
    }

    void doSetInput(IArchiRepository archiRepo) {
        setInput(archiRepo);
        
        // Avoid bogus horizontal scrollbar cheese
        Display.getCurrent().asyncExec(() -> {
            if(!getTable().isDisposed()) {
                getTable().getParent().layout();
            }
        });
    }
    
    // ===============================================================================================
	// ===================================== Table Model ==============================================
	// ===============================================================================================
    
    /**
     * The Model for the Table.
     */
   private static class BranchesContentProvider implements IStructuredContentProvider {
        @Override
        public Object[] getElements(Object parent) {
            if(parent instanceof IArchiRepository repo) {
                // Local Repo was deleted
                if(!repo.getWorkingFolder().exists()) {
                    return new Object[0];
                }
                
                try {
                    BranchStatus status = new BranchStatus(repo.getWorkingFolder(), Option.ALL);
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
        final DateFormat dateFormat = DateFormat.getDateTimeInstance();
        final Color redColor = new Color(255, 64, 0);
        
        private String getColumnText(BranchInfo branchInfo, int columnIndex) {
            RevCommit latestCommit = branchInfo.getLatestCommit();
            
            return switch(columnIndex) {
                // Branch Name
                case 0 -> {
                    String name = branchInfo.getShortName();
                    if(branchInfo.isCurrentBranch()) {
                        name += " " + Messages.BranchesTableViewer_2; //$NON-NLS-1$
                    }
                    yield name;
                }
                
                // Branch Status
                case 1 -> {
                    if(branchInfo.isRemoteDeleted()) {
                        yield Messages.BranchesTableViewer_3;
                    }
                    if(branchInfo.hasRemoteRef()) {
                        yield Messages.BranchesTableViewer_4;
                    }
                    else {
                        yield Messages.BranchesTableViewer_5;
                    }
                }
                
                // Latest Commit Author
                case 2 -> {
                    yield latestCommit == null ? "" : latestCommit.getCommitterIdent().getName(); //$NON-NLS-1$
                }
                
                // Latest Commit Date
                case 3 -> {
                    yield latestCommit == null ? "" : dateFormat.format(new Date(latestCommit.getCommitTime() * 1000L)); //$NON-NLS-1$
                }
                
                // Commit Status
                case 4 -> {
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
                    else {
                        text = Messages.BranchesTableViewer_14;
                    }
                    
                    yield text;
                }
                
                // Merge Status
                case 5 -> {
                    yield branchInfo.isMerged() ? Messages.BranchesTableViewer_15 : Messages.BranchesTableViewer_16;
                }
                
                default -> {
                    yield ""; //$NON-NLS-1$
                }
            };
        }

        @Override
        public void update(ViewerCell cell) {
            if(cell.getElement() instanceof BranchInfo branchInfo) {
                // Red text for "deleted" branches
                cell.setForeground(branchInfo.isRemoteDeleted() ? redColor : null);

                cell.setText(getColumnText(branchInfo, cell.getColumnIndex()));
                
                if(branchInfo.isCurrentBranch() && cell.getColumnIndex() == 0) {
                    cell.setFont(FontFactory.SystemFontBold);
                }
                else {
                    cell.setFont(null);
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
