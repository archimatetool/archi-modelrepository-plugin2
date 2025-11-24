/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.views.tags;

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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.TagInfo;


/**
 * Tags Table Viewer
 */
public class TagsTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(TagsTableViewer.class.getName());
    
    public TagsTableViewer(Composite parent) {
        super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        
        getTable().setHeaderVisible(true);
        getTable().setLinesVisible(false);
        
        TableColumnLayout tableLayout = (TableColumnLayout)parent.getLayout();
        
        // Name
        TableViewerColumn column = new TableViewerColumn(this, SWT.NONE, 0);
        column.getColumn().setText(Messages.TagsTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(25, false));
        
        // Commit message
        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.TagsTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(35, false));
        
        // Author
        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.TagsTableViewer_2);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));

        // Date
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.TagsTableViewer_3);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));

        setContentProvider(new TagsContentProvider());
        setLabelProvider(new TagsLabelProvider());
        
        setComparator(new ViewerComparator(Collator.getInstance()) {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                TagInfo t1 = (TagInfo)e1;
                TagInfo t2 = (TagInfo)e2;
                return getComparator().compare(t1.getShortName(), t2.getShortName());
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
	// =================================== Content Provider ==========================================
	// ===============================================================================================
    
    private static class TagsContentProvider implements IStructuredContentProvider {
        @Override
        public Object[] getElements(Object parent) {
            if(parent instanceof IArchiRepository repo) {
                // Local Repo was deleted
                if(!repo.getWorkingFolder().exists()) {
                    return new Object[0];
                }
                
                try {
                    return TagInfo.getTags(repo.getWorkingFolder()).toArray();
                }
                catch(IOException | GitAPIException ex) {
                    ex.printStackTrace();
                    logger.log(Level.SEVERE, "Get Tags", ex); //$NON-NLS-1$
                }
            }
            
            return new Object[0];
        }
    }

    // ===============================================================================================
	// =================================== Label Provider ============================================
	// ===============================================================================================

    private static class TagsLabelProvider extends CellLabelProvider {
        final DateFormat dateFormat = DateFormat.getDateTimeInstance();
        final Color redColor = new Color(255, 64, 0);
        
        private String getColumnText(TagInfo tagInfo, int columnIndex) {
            RevTag revtag = tagInfo.getTag().orElse(null);
            RevCommit revCommit = tagInfo.getCommit().orElse(null);
            PersonIdent personIdent = revtag != null ? revtag.getTaggerIdent() : revCommit != null ? revCommit.getAuthorIdent() : null;
            
            return switch(columnIndex) {
                // Tag Name
                case 0 -> {
                    String name = tagInfo.getShortName();
                    if(tagInfo.isOrphaned()) {
                        name += " " + Messages.TagsTableViewer_4; //$NON-NLS-1$
                    }
                    yield name;
                }
                
                // Commit message
                case 1 -> {
                    yield revCommit != null ? revCommit.getShortMessage() : null;
                }
                
                // Author of annotated tag or commit
                case 2 -> {
                    yield personIdent != null ? personIdent.getName() : null;
                }

                // Date of annotated tag or commit
                case 3 -> {
                    Date date = (revtag != null && personIdent != null) ?  Date.from(personIdent.getWhenAsInstant())
                                                        : revCommit != null ? new Date(revCommit.getCommitTime() * 1000L) : null;
                    yield dateFormat.format(date);
                }
                
                default -> {
                    yield ""; //$NON-NLS-1$
                }
            };
        }

        @Override
        public void update(ViewerCell cell) {
            if(cell.getElement() instanceof TagInfo tagInfo) {
                // Red text for orphaned tags
                cell.setForeground(tagInfo.isOrphaned() ? redColor : null);
                
                cell.setText(getColumnText(tagInfo, cell.getColumnIndex()));
                
                switch(cell.getColumnIndex()) {
                    case 0:
                        cell.setImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_TAG));
                        break;

                    default:
                        break;
                }
            }
        }
    }
}
