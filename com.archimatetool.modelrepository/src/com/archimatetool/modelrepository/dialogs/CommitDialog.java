/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.dialogs;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.UIUtils;
import com.archimatetool.editor.ui.components.ExtendedTitleAreaDialog;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.modelrepository.IModelRepositoryImages;
import com.archimatetool.modelrepository.ModelRepositoryPlugin;
import com.archimatetool.modelrepository.repository.CommitManifest;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Commit Dialog
 * 
 * @author Phil Beauvoir
 */
public class CommitDialog extends ExtendedTitleAreaDialog {
    
    private static Logger logger = Logger.getLogger(CommitDialog.class.getName());
    
    private static String DIALOG_ID = "CommitDialog"; //$NON-NLS-1$
    
    private Label fRepoLabel;
    
    private Text fTextUserName, fTextUserEmail, fTextCommitMessage;
    private Button fAmendLastCommitCheckbox;
    
    private String fCommitMessage;
    private boolean fAmend;
    
    private IArchiRepository fRepository;
    
    public CommitDialog(Shell parentShell, IArchiRepository repo) {
        super(parentShell, DIALOG_ID);
        fRepository = repo;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.CommitDialog_0);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, ModelRepositoryPlugin.HELP_ID);

        setTitle(Messages.CommitDialog_0);
        setMessage(Messages.CommitDialog_1, IMessageProvider.INFORMATION);
        setTitleImage(IModelRepositoryImages.ImageFactory.getImage(IModelRepositoryImages.BANNER_COMMIT));

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        container.setLayout(layout);
        
        Label label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_2);
        
        fRepoLabel = new Label(container, SWT.NONE);
        fRepoLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_3);
        
        fTextUserName = UIUtils.createSingleTextControl(container, SWT.BORDER, false);
        fTextUserName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_4);
        
        fTextUserEmail = UIUtils.createSingleTextControl(container, SWT.BORDER, false);
        fTextUserEmail.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        label = new Label(container, SWT.NONE);
        label.setText(Messages.CommitDialog_5);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        label.setLayoutData(gd);
        
        fTextCommitMessage = new Text(container, SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.MULTI);
        gd = new GridData(GridData.FILL_BOTH);
        gd.horizontalSpan = 2;
        fTextCommitMessage.setLayoutData(gd);
        
        // Tab Traversal and Enter key
        UIUtils.applyTraverseListener(fTextCommitMessage, SWT.TRAVERSE_TAB_NEXT | SWT.TRAVERSE_TAB_PREVIOUS | SWT.TRAVERSE_RETURN);
        
        fAmendLastCommitCheckbox = new Button(container, SWT.CHECK);
        fAmendLastCommitCheckbox.setText(Messages.CommitDialog_6);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        fAmendLastCommitCheckbox.setLayoutData(gd);
        
        setValues();
        
        return area;
    }
    
    private void setValues() {
        try(GitUtils utils = GitUtils.open(fRepository.getWorkingFolder())) {
            fRepoLabel.setText(fRepository.getName() + " [" + utils.getCurrentLocalBranchName().orElse("null") + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            
            PersonIdent result = utils.getUserDetails();
            fTextUserName.setText(result.getName());
            fTextUserEmail.setText(result.getEmailAddress());
            
            // An amend of the last commit is allowed:
            // If HEAD and Remote Ref are not the same && the HEAD commit does not have more than one parent (i.e HEAD commit is not a merged commit)
            boolean isAmendable = !utils.isRemoteRefForCurrentBranchAtHead() && utils.getCommitParentCount(RepoConstants.HEAD) < 2;
            fAmendLastCommitCheckbox.setEnabled(isAmendable);
            
            // Set commit message to last commit message on checkbox click
            if(isAmendable) {
                // Get the latest commit message if there is one
                RevCommit latestCommit = utils.getLatestCommit().orElse(null);
                String previousCommitMessage = latestCommit != null ? latestCommit.getFullMessage() : null;
                
                if(previousCommitMessage != null) {
                    fAmendLastCommitCheckbox.addSelectionListener(widgetSelectedAdapter(event -> {
                        if(fAmendLastCommitCheckbox.getSelection()) {
                            // Get commit message stripped of manifest
                            String actualCommitMessage = CommitManifest.getCommitMessageWithoutManifest(previousCommitMessage);
                            
                            // If text box is empty or text box string doesn't equal commit string offer to replace
                            if(fTextCommitMessage.getText().isEmpty() || (!fTextCommitMessage.getText().equals(actualCommitMessage) && MessageDialog.openQuestion(getShell(),
                                    Messages.CommitDialog_0, Messages.CommitDialog_7))) {
                                fTextCommitMessage.setText(actualCommitMessage);
                            }
                        }
                    }));
                }
            }
            
            if(!StringUtils.isSet(result.getName())) {
                fTextUserName.setFocus();
            }
            else if(!StringUtils.isSet(result.getEmailAddress())) {
                fTextUserEmail.setFocus();
            }
            else {
                fTextCommitMessage.setFocus();
            }
        }
        catch(IOException ex) {
            logger.log(Level.WARNING, "Set Values", ex); //$NON-NLS-1$
            ex.printStackTrace();
        } 
    }

    @Override
    protected boolean isResizable() {
        return true;
    }
    
    @Override
    protected Point getDefaultDialogSize() {
        return new Point(600, 450);
    }
    
    public String getCommitMessage() {
        return fCommitMessage;
    }
    
    public boolean getAmend() {
        return fAmend;
    }

    @Override
    protected void okPressed() {
        fCommitMessage = fTextCommitMessage.getText();
        fAmend = fAmendLastCommitCheckbox.getSelection();
        
        // Store user name and email
        try {
            fRepository.saveUserDetails(fTextUserName.getText().trim(), fTextUserEmail.getText().trim());
        }
        catch(IOException ex) {
            logger.log(Level.WARNING, "Could not save user details", ex); //$NON-NLS-1$
            ex.printStackTrace();
        }
        
        super.okPressed();
    }
}