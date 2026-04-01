package com.archimatetool.modelrepository.merge;

import java.util.List;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.modelrepository.dialogs.CompareDialog;
import com.archimatetool.modelrepository.merge.ModelComparison;
import com.archimatetool.modelrepository.repository.IArchiRepository;

/**
 * Utility for showing merge-related UI dialogs.
 */
public class MergeUIUtils {

    /**
     * Opens {@link CompareDialog} on the UI thread with a {@link ModelComparison} between two commits.
     *
     * @param repo     repository whose working folder is used to load models
     * @param commit1  first commit (order is handled inside {@link ModelComparison})
     * @param commit2  second commit
     */
    public static void showCompareDialog(IArchiRepository repo, org.eclipse.jgit.revwalk.RevCommit commit1, org.eclipse.jgit.revwalk.RevCommit commit2) {
        Display.getDefault().syncExec(() -> {
            try {
                ModelComparison mc = new ModelComparison(repo, commit1, commit2);
                mc.init();
                CompareDialog dialog = new CompareDialog(Display.getDefault().getActiveShell(), mc);
                dialog.open();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    /**
     * Shows a read-only multi-line dialog listing validation errors (used after {@link ModelCheckerLemana} fails).
     *
     * @param messages human-readable problem lines; never {@code null}
     */
    public static void showModelErrorsDialog(List<String> messages) {
        StringBuilder errorText = new StringBuilder();
        for (String err : messages) {
            errorText.append(err).append("\n\n");
        }

        Display.getDefault().syncExec(() -> {
            Shell shell = Display.getDefault().getActiveShell();
            
            TitleAreaDialog dlg = new TitleAreaDialog(shell) {
                @Override
                protected Control createDialogArea(Composite parent) {
                    Composite composite = (Composite) super.createDialogArea(parent);
                    setTitle("Model Integrity Errors");
                    setMessage("Errors were found in the model during validation. You can copy the text below.", IMessageProvider.ERROR);

                    Text errorTextBox = new Text(
                            composite,
                            SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.WRAP | SWT.READ_ONLY
                    );
                    errorTextBox.setText(errorText.toString());
                    errorTextBox.setBackground(composite.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

                    GridData gridData = new GridData(GridData.FILL_BOTH);
                    gridData.grabExcessHorizontalSpace = true;
                    gridData.grabExcessVerticalSpace = true;
                    gridData.heightHint = 400;
                    gridData.widthHint = 800;
                    errorTextBox.setLayoutData(gridData);

                    return composite;
                }

                @Override
                protected Point getInitialSize() {
                    return new Point(1000, 700);
                }

                @Override
                protected boolean isResizable() {
                    return true;
                }
            };
            dlg.open();
        });
    }
}
