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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import com.archimatetool.editor.ui.FontFactory;
import com.archimatetool.editor.ui.ThemeUtils;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.IArchiRepository;


/**
 * History Table Viewer
 */
public class HistoryTableViewer extends TableViewer {
    
    private static Logger logger = Logger.getLogger(HistoryTableViewer.class.getName());
    
    private RevCommit fLocalCommit, fRemoteCommit;
    private BranchInfo fSelectedBranch;
    
    private Set<RevCommit> unmergedCommits;
    
    private boolean hasWorkingTree;
    
    private Color unmergedColor = new Color(0, 124, 250);
    private Color mergedColor = ThemeUtils.isDarkTheme() ? new Color(250, 250, 250) : new Color(0, 0, 0);
    
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
        column.getColumn().setText(Messages.HistoryTableViewer_1);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(50, false));

        column = new TableViewerColumn(this, SWT.NONE, 1);
        column.getColumn().setText(Messages.HistoryTableViewer_2);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));

        column = new TableViewerColumn(this, SWT.NONE, 2);
        column.getColumn().setText(Messages.HistoryTableViewer_3);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(20, false));
    
        column = new TableViewerColumn(this, SWT.NONE, 3);
        column.getColumn().setText(Messages.HistoryTableViewer_0);
        tableLayout.setColumnData(column.getColumn(), new ColumnWeightData(10, false));
    }
    
    void setRepository(IArchiRepository archiRepo) {
        // Get basic current LocalBranch Info
        try {
            fSelectedBranch = BranchInfo.currentLocalBranchInfo(archiRepo.getWorkingFolder(), false);
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Branch Status", ex); //$NON-NLS-1$
        }

        setInputAndSelect(archiRepo);
    }
    
    void setSelectedBranch(BranchInfo branchInfo) {
        if(Objects.equals(branchInfo, fSelectedBranch)) {
            return;
        }

        fSelectedBranch = branchInfo;
        setInputAndSelect((IArchiRepository)getInput());
    }
    
    void modelSaved() {
        // If we have working tree then update history view
        if(hasWorkingTree((IArchiRepository)getInput()) != hasWorkingTree) {
            setInput(getInput());
        }
    }
    
    private void setInputAndSelect(IArchiRepository archiRepo) {
        setInput(archiRepo);
        
        Display.getCurrent().asyncExec(() -> {
            if(!getTable().isDisposed()) {
                // Avoid bogus horizontal scrollbar cheese
                getTable().getParent().layout();
                
                // Select first row. This will ensure we only load the first few commits
                Object element = getElementAt(0);
                if(element != null) {
                    setSelection(new StructuredSelection(element), true);
                }
            }
        });
    }
    
    private boolean hasWorkingTree(IArchiRepository repo) {
        try {
            return repo != null && repo.hasChangesToCommit() && fSelectedBranch != null && fSelectedBranch.isCurrentBranch();
        }
        catch(IOException | GitAPIException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Has changes to commit", ex); //$NON-NLS-1$
            return false;
        }
    }
    
    // ===============================================================================================
    // ===================================== Table Model =============================================
    // ===============================================================================================
    
    private boolean isUnmerged(RevCommit commit) {
        return unmergedCommits.contains(commit);
    }
    
    private void addUnmerged(RevCommit commit, Repository repository) throws IOException {
        if(fRemoteCommit != null && fLocalCommit != null) { // There will only be unmerged (remote) commits if we have a tracked remote branch
            try(RevWalk walk = new RevWalk(repository)) {
                RevCommit c1 = walk.lookupCommit(commit.getId());
                RevCommit c2 = walk.lookupCommit(fLocalCommit.getId());
                if(!walk.isMergedInto(c1, c2)) {
                    unmergedCommits.add(commit);
                }
            }
        }
    }
    
    /**
     * The Model for the Table
     */
    private class HistoryContentProvider implements ILazyContentProvider {
        final int PRELOAD_SIZE = 50; // Number of commits to preload
        List<RevCommit> commits;
        
        @Override
        public void inputChanged(Viewer v, Object oldInput, Object newInput) {
            if(oldInput == null && newInput == null) { // nothing to do
                return;
            }
            
            dispose();
            
            if(!(newInput instanceof IArchiRepository repo // Must be non-null repo
                    && repo.getWorkingFolder().exists()    // Local Repo might have been deleted
                    && fSelectedBranch != null)) {         // Must have branch selected
                setItemCount(0);
                return;
            }
            
            commits = new ArrayList<>();
            unmergedCommits = new HashSet<>();
            
            // Get this now
            hasWorkingTree = hasWorkingTree(repo);
            
            loadCommits(repo, -1);
        }
        
        /**
         * Loads and counts the number of commits and keeps a reference to the current local and remote commits
         */
        void loadCommits(IArchiRepository repo, int index) {
            try(Repository repository = Git.open(repo.getWorkingFolder()).getRepository()) {
                try(RevWalk revWalk = new RevWalk(repository)) {
                    revWalk.setRetainBody(false); // Don't load the body of commits that are being counted

                    // Set the local branch commit start
                    ObjectId localCommitID = repository.resolve(fSelectedBranch.getLocalBranchNameFor());
                    if(localCommitID != null) {
                        fLocalCommit = revWalk.parseCommit(localCommitID);
                        revWalk.markStart(fLocalCommit);
                    }

                    // Set the remote branch commit start
                    ObjectId remoteCommitID = repository.resolve(fSelectedBranch.getRemoteBranchNameFor());
                    if(remoteCommitID != null) {
                        fRemoteCommit = revWalk.parseCommit(remoteCommitID);
                        revWalk.markStart(fRemoteCommit);
                    }

                    int count = 0;

                    // If index is -1 we'll count the total number of commits and preload the first block
                    // Note: don't use RevWalkUtils.count() because it won't count all local and remote commits
                    if(index == -1) {
                        for(RevCommit commit : revWalk) {
                            count++;
                            
                            // While we're counting the commits, add the first preload block
                            if(count <= PRELOAD_SIZE) {
                                revWalk.parseBody(commit);
                                commits.add(commit);
                                addUnmerged(commit, repository);
                            }
                        }

                        // Add an extra item if we are showing the working tree
                        setItemCount(hasWorkingTree ? count + 1 : count);
                    }
                    // If index is 0 or more we'll load PRELOAD_SIZE more commits from the given index point forwards
                    else {
                        for(RevCommit commit : revWalk) {
                            if(count >= commits.size()) {  // We've reached the current commit size so add the next one
                                revWalk.parseBody(commit);
                                commits.add(commit);
                                addUnmerged(commit, repository);
                            }
                            
                            if(++count > index + PRELOAD_SIZE - 1) { // Don't load more than PRELOAD_SIZE
                                break;
                            }
                        }
                    }

                    revWalk.dispose();
                }
            }
            catch(IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "RevWalk", ex); //$NON-NLS-1$
                setItemCount(0);
            }
        }
        
        @Override
        public void updateElement(int index) {
            // If this is the working tree row insert dummy object
            if(index == 0 && hasWorkingTree) {
                replace(new Object(), index);
                return;
            }
            
            // The real index of the RevCommit depends on whether we are showing the working tree row
            int realIndex = hasWorkingTree ? index - 1 : index;
            
            if(realIndex >= commits.size()) {
                loadCommits((IArchiRepository)getInput(), realIndex);
            }
            
            replace(commits.get(realIndex), index);
        }

        @Override
        public void dispose() {
            commits = null;
            unmergedCommits = null;
            fLocalCommit = null;
            fRemoteCommit = null;
        }
    }
    
    // ===============================================================================================
	// ===================================== Label Model =============================================
	// ===============================================================================================

    private class HistoryLabelProvider extends StyledCellLabelProvider {
        
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        
        private String getColumnText(RevCommit commit, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    return commit.getShortMessage();
                    
                case 1:
                    return commit.getAuthorIdent().getName();
                    
                case 2:
                    return dateFormat.format(new Date(commit.getCommitTime() * 1000L));
                
                case 3:
                    return commit.getName().substring(0, 8);
                    
                default:
                    return null;
            }
        }

        @Override
        public void update(ViewerCell cell) {
            if(!(cell.getElement() instanceof RevCommit)) {
                if(cell.getColumnIndex() == 0) {
                    cell.setForeground(null);
                    cell.setText(Messages.HistoryTableViewer_7);
                    cell.setFont(FontFactory.SystemFontBold);
                    Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_BLANK);
                    cell.setImage(image);
                }
                return;
            }
            
            RevCommit commit = (RevCommit)cell.getElement();
            
            cell.setForeground(null);
            cell.setText(getColumnText(commit, cell.getColumnIndex()));
            
            if(cell.getColumnIndex() == 0) {
                Image image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_BLANK); // Need a blank icon for indent on Mac/Linux

                // Local/Remote are same commit
                if(commit.equals(fLocalCommit) && commit.equals(fRemoteCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_HISTORY_VIEW);
                }
                // Local commit
                else if(commit.equals(fLocalCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_LOCAL);
                }
                // Remote commit
                else if(commit.equals(fRemoteCommit)) {
                    image = IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.ICON_REMOTE);
                }
                
                if(isUnmerged(commit)) {
                    cell.setForeground(unmergedColor);
                }

                cell.setImage(image);
            }
        }
        
        @Override
        protected void paint(Event event, Object element) {
            if(!(element instanceof RevCommit)) {
                super.paint(event, element);
                return;
            }
            
            // Draw a line denoting a branch for unmerged commits (i.e remote commits)
            RevCommit commit = (RevCommit)element;
            
            // Each OS has a different image indent
            final int imageGap = PlatformUtils.isWindows() ? 8 : PlatformUtils.isMac() ? 11 : 10;
            final int cWidth = 8;
            
            if(event.index == 0) {
                if(!(commit.equals(fRemoteCommit) || commit.equals(fLocalCommit))) {
                    Color oldForeground = event.gc.getForeground();
                    Color oldBackground = event.gc.getBackground();
                    
                    event.gc.setAntialias(SWT.ON);
                    event.gc.setLineWidth(2);
                    
                    event.gc.setForeground(isUnmerged(commit) ? unmergedColor : mergedColor);
                    event.gc.setBackground(isUnmerged(commit) ? unmergedColor : mergedColor);
                    
                    // circle
                    event.gc.drawOval(event.x + imageGap - (cWidth / 2), event.y + (event.height - cWidth) / 2, cWidth, cWidth);

                    // top line
                    event.gc.drawLine(event.x + imageGap, event.y, event.x + imageGap, event.y + (event.height - cWidth) / 2);
                    
                    // bottom line if not the oldest commit
                    List<RevCommit> commits = ((HistoryContentProvider)getContentProvider()).commits;
                    if(commits != null && commit != commits.get(commits.size() - 1)) {
                        event.gc.drawLine(event.x + imageGap, event.y + (event.height + cWidth) / 2, event.x + imageGap, event.y + event.height);
                    }
                    
                    // old way of doing it
                    //if(commit.getParentCount() > 0) {
                    //    event.gc.drawLine(event.x + imageGap, event.y + (event.height + cWidth) / 2, event.x + imageGap, event.y + event.height);
                    //}
                    
                    event.gc.setForeground(oldForeground);
                    event.gc.setBackground(oldBackground);
                }
            }
            
            super.paint(event, element);
        }
        
        @Override
        public String getToolTipText(Object element) {
            if(!(element instanceof RevCommit)) {
                return null;
            }
            
            RevCommit commit = (RevCommit)element;
            String s = ""; //$NON-NLS-1$
            
            // Local/Remote are same commit
            if(commit.equals(fLocalCommit) && commit.equals(fRemoteCommit)) {
                s += Messages.HistoryTableViewer_4 + " "; //$NON-NLS-1$
            }
            // Local commit
            else if(commit.equals(fLocalCommit)) {
                s += Messages.HistoryTableViewer_5 + " "; //$NON-NLS-1$
            }
            // Remote commit
            else if(commit.equals(fRemoteCommit)) {
                s += Messages.HistoryTableViewer_6 + " "; //$NON-NLS-1$
            }

            s += commit.getShortMessage().trim();

            return s;
        }
    }
}
