package com.archimatetool.modelrepository.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProperty;
import com.archimatetool.modelrepository.dialogs.ViewComparisonComposite;

/**
 * Modal UI for reviewing REAL EMF Compare conflicts before merge: per-property rows, LOCAL/REMOTE choice,
 * styled explanation panel, and optional diagram comparison.
 */
public class MergeConflictDialog extends TitleAreaDialog {

	/** Mine/Theirs columns: max display width after pack (details in info panel). */
	private static final int TABLE_MINE_THEIRS_MAX_WIDTH_PX = 300;
	/** Max characters in Mine/Theirs cells before «…» (avoids huge strings + layout glitches). */
	private static final int TABLE_MINE_THEIRS_MAX_CHARS = 96;
	/** Location column (view / folder path): cap width; full path in info panel. */
	private static final int TABLE_LOCATION_MAX_WIDTH_PX = 280;
	private static final int TABLE_LOCATION_MAX_CHARS = 72;

	private Comparison comparison;
	private List<Conflict> visibleConflicts;
	private TableViewer masterViewer;
	private StyledText infoText;
	private Button btnVisualCompare;

	private Map<Diff, Boolean> diffResolutions = new HashMap<>();
	private Color diffColor;
	private Color localBg;
	private Color remoteBg;

	/**
	 * @param parentShell parent shell
	 * @param comparison  EMF Compare result for the merge in progress (mutated only for debug copy)
	 */
	public MergeConflictDialog(Shell parentShell, Comparison comparison) {
		super(parentShell);
		this.comparison = comparison;

		for (Diff diff : comparison.getDifferences()) {
			diffResolutions.put(diff, false);
		}

		diffColor = new Color(parentShell.getDisplay(), 255, 230, 230);
		localBg = new Color(parentShell.getDisplay(), 240, 255, 240); // Very light green
		remoteBg = new Color(parentShell.getDisplay(), 240, 240, 255); // Very light blue
		setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Control createContents(Composite parent) {
		Control control = super.createContents(parent);
		getShell().setText("Merge Conflict Auditor");
		return control;
	}

	/**
	 * Builds sash with conflict table, styled info panel, toolbar, and context menu.
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		setTitle("Merge Conflict Auditor");
		setMessage("Double-click row to toggle LOCAL/REMOTE. Resolution column + row color: green = LOCAL, blue = REMOTE. Right-click to Copy ID.");

		createToolbar(container);

		SashForm mainSash = new SashForm(container, SWT.VERTICAL);
		mainSash.setLayoutData(new GridData(GridData.FILL_BOTH));

		createUnifiedTablePane(mainSash);
		infoText = new StyledText(mainSash, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.WRAP | SWT.READ_ONLY);
		infoText.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		infoText.setFont(JFaceResources.getTextFont());
		mainSash.setWeights(new int[] { 40, 60 });

		// Context menu (e.g. copy object ID)
		createContextMenu();

		masterViewer.addSelectionChangedListener(event -> {
			IStructuredSelection sel = (IStructuredSelection) event.getSelection();
			if (!sel.isEmpty() && sel.getFirstElement() instanceof UnifiedConflictRow u) {
				updateInfoTextForUnifiedRow(u);
				btnVisualCompare.setEnabled(getDiagramModel(u.anchorObject) != null);
			} else {
				infoText.setText("");
				btnVisualCompare.setEnabled(false);
			}
			updateConflictCountMessage();
		});

		masterViewer.addDoubleClickListener(event -> {
			Object el = ((IStructuredSelection) event.getSelection()).getFirstElement();
			if (el instanceof UnifiedConflictRow u) {
				toggleConflictResolution(u.conflict, !getConflictResolution(u.conflict));
			}
		});

		visibleConflicts = comparison.getConflicts().stream()
				.filter(c -> c.getKind() == ConflictKind.REAL)
				.collect(Collectors.toList());

		if (visibleConflicts.isEmpty()) {
			setMessage("No REAL conflicts detected. You can safely press OK to auto-merge.", IMessageProvider.WARNING);
		}

		List<UnifiedConflictRow> unifiedRows = buildAllUnifiedRows(visibleConflicts);
		masterViewer.setInput(unifiedRows);
		if (!unifiedRows.isEmpty()) {
			masterViewer.setSelection(new StructuredSelection(unifiedRows.get(0)), true);
			updateInfoTextForUnifiedRow(unifiedRows.get(0));
			EObject firstObj = unifiedRows.get(0).anchorObject;
			btnVisualCompare.setEnabled(getDiagramModel(firstObj) != null);
		}
		packTableColumns(masterViewer.getTable());
		updateConflictCountMessage();
		
		// Add a summary label at the bottom
		org.eclipse.swt.widgets.Label summaryLabel = new org.eclipse.swt.widgets.Label(container, SWT.NONE);
		summaryLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		summaryLabel.setText(String.format("Found %d REAL conflicts that require your attention.", visibleConflicts.size()));

		return area;
	}

	/** Flattens all conflicts into table rows (one row per object × property). */
	private List<UnifiedConflictRow> buildAllUnifiedRows(List<Conflict> conflicts) {
		List<UnifiedConflictRow> all = new ArrayList<>();
		for (Conflict c : conflicts) {
			all.addAll(buildRowsForConflict(c));
		}
		return all;
	}

	/**
	 * One table row per (object, property) within a conflict — same grouping as the old detail table.
	 *
	 * @param conflict REAL conflict from {@link #comparison}
	 * @return de-duplicated rows keyed by object id and feature name
	 */
	private List<UnifiedConflictRow> buildRowsForConflict(Conflict conflict) {
		Map<String, UnifiedConflictRow> rows = new LinkedHashMap<>();
		for (Diff d : conflict.getDifferences()) {
			EStructuralFeature f = getFeature(d);
			if (f == null)
				continue;
			Match m = d.getMatch();
			EObject obj = getAnyObject(d);
			if (obj == null)
				continue;
			String objId = getObjectId(obj);
			String key = objId + "|" + f.getName();
			if (rows.containsKey(key))
				continue;
			String hName = getHumanName(f.getName());
			String ownerDesc = getObjectShortDescription(obj);
			InspectionRow ir = new InspectionRow(hName, getValForDetailTable(m.getLeft(), f),
					getValForDetailTable(m.getRight(), f), true, ownerDesc, f.getName());
			rows.put(key, new UnifiedConflictRow(conflict, ir, obj));
		}
		return new ArrayList<>(rows.values());
	}

	/** Table context menu: copy selected row’s object id. */
	private void createContextMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(manager -> {
			IStructuredSelection selection = (IStructuredSelection) masterViewer.getSelection();
			if (!selection.isEmpty() && selection.getFirstElement() instanceof UnifiedConflictRow u) {
				String id = getObjectId(u.anchorObject);

				Action copyIdAction = new Action("Copy ID: " + id) {
					@Override
					public void run() {
						Clipboard cb = new Clipboard(getShell().getDisplay());
						cb.setContents(new Object[] { id }, new Transfer[] { TextTransfer.getInstance() });
						cb.dispose();
					}
				};
				manager.add(copyIdAction);
			}
		});

		Menu menu = menuMgr.createContextMenu(masterViewer.getControl());
		masterViewer.getControl().setMenu(menu);
	}

	/** Opens a window with filtered comparison XMI, search, and copy. */
	private void showDebugXML() {
		Comparison copy = org.eclipse.emf.ecore.util.EcoreUtil.copy(comparison);
		copy.getConflicts().removeIf(c -> c.getKind() != ConflictKind.REAL);
		Set<Diff> diffsInRealConflicts = new HashSet<>();
		for(Conflict c : copy.getConflicts()) {
			diffsInRealConflicts.addAll(c.getDifferences());
		}
		copy.getDifferences().removeIf(d -> !diffsInRealConflicts.contains(d));
		cleanMatches(copy, diffsInRealConflicts);

		String xml = getComparisonDump(copy);

		Shell shell = new Shell(getShell(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
		shell.setText("EMF Compare Debug XML & Integrity Checker");
		shell.setLayout(new GridLayout(1, false));
		shell.setSize(1000, 800);

		Composite controls = new Composite(shell, SWT.NONE);
		controls.setLayout(new GridLayout(3, false));
		controls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Text searchText = new Text(controls, SWT.BORDER | SWT.SEARCH);
		searchText.setMessage("Search ID (e.g. id-a2cd9504...)");
		searchText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Button btnCheck = new Button(controls, SWT.PUSH);
		btnCheck.setText("Validate Choices");

		Button btnCopy = new Button(controls, SWT.PUSH);
		btnCopy.setText("Copy XML");

		Text text = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		text.setLayoutData(new GridData(GridData.FILL_BOTH));
		text.setFont(JFaceResources.getTextFont());
		text.setText(xml);
		text.setEditable(false);

		searchText.addListener(SWT.DefaultSelection, e -> {
			String searchString = searchText.getText();
			String content = text.getText();
			int start = text.getSelection().x + text.getSelectionCount();
			int index = content.indexOf(searchString, start);
			if (index == -1)
				index = content.indexOf(searchString);
			if (index != -1) {
				text.setSelection(index, index + searchString.length());
				text.showSelection();
			}
		});

		btnCheck.addListener(SWT.Selection, e -> {
			if (validateIntegrity()) {
				MessageDialog.openInformation(shell, "Integrity OK", "All Relationship/Connection pairs match.");
			} else {
				MessageDialog.openError(shell, "Integrity Failure", "Mismatch! Check: " + findFirstMismatchID());
			}
		});

		btnCopy.addListener(SWT.Selection, e -> {
			Clipboard cb = new Clipboard(shell.getDisplay());
			cb.setContents(new Object[] { xml }, new Transfer[] { TextTransfer.getInstance() });
			cb.dispose();
		});

		shell.open();
	}

	/** Removes match subtrees that do not contain any {@code interestingDiffs}. */
	private void cleanMatches(Comparison comparison, Set<Diff> interestingDiffs) {
		List<Match> matches = new ArrayList<>(comparison.getMatches());
		for (Match match : matches) {
			if (!isMatchInteresting(match, interestingDiffs)) {
				comparison.getMatches().remove(match);
			}
		}
	}

	/** Recursively checks whether a match or any submatch carries an interesting diff. */
	private boolean isMatchInteresting(Match match, Set<Diff> interestingDiffs) {
		for (Diff diff : match.getDifferences()) {
			if (interestingDiffs.contains(diff)) {
				return true;
			}
		}
		List<Match> subMatches = new ArrayList<>(match.getSubmatches());
		boolean anyChildInteresting = false;
		for (Match subMatch : subMatches) {
			if (isMatchInteresting(subMatch, interestingDiffs)) {
				anyChildInteresting = true;
			} else {
				match.getSubmatches().remove(subMatch);
			}
		}
		return anyChildInteresting;
	}

	/** Serializes {@code comp} to formatted XMI in memory for debugging. */
	private String getComparisonDump(Comparison comp) {
		try {
			org.eclipse.emf.ecore.resource.impl.ResourceSetImpl resSet = new org.eclipse.emf.ecore.resource.impl.ResourceSetImpl();
			resSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*",
					new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
			org.eclipse.emf.ecore.resource.Resource res = resSet
					.createResource(org.eclipse.emf.common.util.URI.createURI("debug.xmi"));
			res.getContents().add(org.eclipse.emf.ecore.util.EcoreUtil.copy(comp));
			Map<Object, Object> options = new HashMap<>();
			options.put(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_FORMATTED, Boolean.TRUE);
			java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			res.save(baos, options);
			return baos.toString("UTF-8");
		} catch (Exception e) {
			return "Dump failed: " + e.getMessage();
		}
	}

	/** {@link TableColumn#pack()} then caps Location and Mine/Theirs widths. */
	private void packTableColumns(Table table) {
		TableColumn[] cols = table.getColumns();
		for (int i = 0; i < cols.length; i++) {
			TableColumn column = cols[i];
			column.pack();
			int w = column.getWidth() + 20;
			// 0:Resolution, 1:Object, 2:Property, 3:Location, 4:Mine, 5:Theirs, 6:Type
			if (i == 3) {
				w = Math.min(w, TABLE_LOCATION_MAX_WIDTH_PX);
			} else if (i == 4 || i == 5) {
				w = Math.min(w, TABLE_MINE_THEIRS_MAX_WIDTH_PX);
			}
			column.setWidth(w);
		}
	}

	/** Sets REMOTE ({@code true}) or LOCAL ({@code false}) for a conflict and all related diffs. */
	private void toggleConflictResolution(Conflict conflict, boolean newValue) {
		for (Diff d : conflict.getDifferences()) {
			syncDiffAndDependencies(d, newValue);
		}
		masterViewer.refresh();
		updateIntegrityStatus();
		refreshInfoAfterToggle();
	}

	/** Refreshes info text and title message after a resolution toggle. */
	private void refreshInfoAfterToggle() {
		IStructuredSelection sel = (IStructuredSelection) masterViewer.getSelection();
		if (!sel.isEmpty() && sel.getFirstElement() instanceof UnifiedConflictRow u) {
			updateInfoTextForUnifiedRow(u);
		}
		updateConflictCountMessage();
	}

	/** Updates banner message from {@link #validateIntegrity()} (placeholder: always OK). */
	private void updateIntegrityStatus() {
		if (validateIntegrity()) {
			setMessage("All choices are consistent. You can safely press OK.", IMessageProvider.INFORMATION);
		} else {
			setMessage("INTEGRITY ERROR: Some Connection/Relationship pairs are mismatched!", IMessageProvider.ERROR);
		}
	}

	/** Propagates resolution to {@code diff}, related objects, and EMF requires/requiredBy links. */
	private void syncDiffAndDependencies(Diff diff, boolean newValue) {
		Set<Diff> toUpdate = new HashSet<>();
		collectAllRelatedDiffs(diff, toUpdate);
		for (Diff d : toUpdate) {
			diffResolutions.put(d, newValue);
		}
	}

	/**
	 * Depth-first closure of diffs that touch the same relationship/connection/concept as {@code diff}.
	 */
	private void collectAllRelatedDiffs(Diff diff, Set<Diff> visited) {
		if (visited.contains(diff))
			return;
		visited.add(diff);

		EObject obj = getAnyObject(diff);

		for (Diff other : comparison.getDifferences()) {
			EObject otherObj = getAnyObject(other);
			if (otherObj == null)
				continue;

			boolean isSameOrPartner = false;
			if (otherObj.equals(obj)) {
				isSameOrPartner = true;
			} else if (obj instanceof IArchimateRelationship r
					&& otherObj instanceof IDiagramModelArchimateConnection dc) {
				if (dc.getArchimateRelationship() == r)
					isSameOrPartner = true;
			} else if (obj instanceof IDiagramModelArchimateConnection dc
					&& otherObj instanceof IArchimateRelationship r) {
				if (dc.getArchimateRelationship() == r)
					isSameOrPartner = true;
			} else if (obj instanceof com.archimatetool.model.IArchimateConcept c
					&& otherObj instanceof com.archimatetool.model.IDiagramModelArchimateComponent dmc) {
				if (dmc.getArchimateConcept() == c)
					isSameOrPartner = true;
			} else if (obj instanceof com.archimatetool.model.IDiagramModelArchimateComponent dmc
					&& otherObj instanceof com.archimatetool.model.IArchimateConcept c) {
				if (dmc.getArchimateConcept() == c)
					isSameOrPartner = true;
			}

			if (isSameOrPartner) {
				collectAllRelatedDiffs(other, visited);
			}
		}

		for (Diff req : diff.getRequires()) {
			collectAllRelatedDiffs(req, visited);
		}
		for (Diff reqBy : diff.getRequiredBy()) {
			collectAllRelatedDiffs(reqBy, visited);
		}
	}

	/** Bulk LOCAL/REMOTE buttons and debug/compare actions above the table. */
	private void createToolbar(Composite parent) {
		Composite t = new Composite(parent, SWT.NONE);
		t.setLayout(new GridLayout(5, false));

		Button b1 = new Button(t, SWT.PUSH);
		b1.setText("ALL TO LOCAL");
		b1.setForeground(getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
		b1.setToolTipText("Discard all remote changes for conflicting objects");
		b1.addListener(SWT.Selection, e -> {
			for (Diff d : comparison.getDifferences())
				diffResolutions.put(d, false);
			masterViewer.refresh();
			updateIntegrityStatus();
			refreshInfoAfterToggle();
		});

		Button b2 = new Button(t, SWT.PUSH);
		b2.setText("ALL TO REMOTE");
		b2.setForeground(getShell().getDisplay().getSystemColor(SWT.COLOR_BLUE));
		b2.setToolTipText("Accept all remote changes for the current conflicts");
		b2.addListener(SWT.Selection, e -> {
			for (Conflict c : visibleConflicts) {
				for (Diff d : c.getDifferences()) {
					syncDiffAndDependencies(d, true);
				}
			}
			masterViewer.refresh();
			updateIntegrityStatus();
			refreshInfoAfterToggle();
		});

		Button bDebugSimple = new Button(t, SWT.PUSH);
		bDebugSimple.setText("Show Debug Simple");
		bDebugSimple.setToolTipText("Show human-readable conflict summary");
		bDebugSimple.addListener(SWT.Selection, e -> showDebugSimple());

		Button bDebug = new Button(t, SWT.PUSH);
		bDebug.setText("Show Debug XML");
		bDebug.addListener(SWT.Selection, e -> showDebugXML());

		btnVisualCompare = new Button(t, SWT.PUSH);
		btnVisualCompare.setText("Visual Compare View");
		btnVisualCompare.setToolTipText("Show visual comparison of the diagram");
		btnVisualCompare.setEnabled(false);
		btnVisualCompare.addListener(SWT.Selection, e -> showVisualCompare());
	}

	/** Plain-text audit report in a popup (copy-friendly). */
	private void showDebugSimple() {
		String report = generateHumanReadableReport();
		Shell shell = new Shell(getShell(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
		shell.setText("Merge Conflict Simple Report");
		shell.setLayout(new GridLayout(1, false));
		shell.setSize(800, 600);
		Composite controls = new Composite(shell, SWT.NONE);
		controls.setLayout(new GridLayout(1, false));
		controls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Button btnCopy = new Button(controls, SWT.PUSH);
		btnCopy.setText("Copy to Clipboard");
		Text text = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		text.setLayoutData(new GridData(GridData.FILL_BOTH));
		text.setFont(JFaceResources.getTextFont());
		text.setText(report);
		text.setEditable(false);
		btnCopy.addListener(SWT.Selection, e -> {
			Clipboard cb = new Clipboard(shell.getDisplay());
			cb.setContents(new Object[] { report }, new Transfer[] { TextTransfer.getInstance() });
			cb.dispose();
		});
		shell.open();
	}

	/** Side-by-side {@link ViewComparisonComposite} for REMOTE vs LOCAL diagram of the selected row. */
	private void showVisualCompare() {
		IStructuredSelection sel = (IStructuredSelection) masterViewer.getSelection();
		if (sel.isEmpty()) return;
		
		EObject obj = getAnyObjectFromSelection(sel.getFirstElement());
		IDiagramModel localDiagram = getDiagramModel(obj);
		if (localDiagram == null) return;
		
		// Find the match for this diagram
		Match match = comparison.getMatch(localDiagram);
		if (match == null) return;
		
		IDiagramModel remoteDiagram = (IDiagramModel) match.getLeft();
		if (remoteDiagram == null) {
			MessageDialog.openInformation(getShell(), "Visual Compare", "This diagram does not exist in the REMOTE version.");
			return;
		}
		
		// Show comparison dialog
		Shell shell = new Shell(getShell(), SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
		shell.setText("Visual Compare: " + localDiagram.getName());
		shell.setLayout(new GridLayout(1, false));
		shell.setSize(1200, 800);
		
		Composite labelsComp = new Composite(shell, SWT.NONE);
		labelsComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		labelsComp.setLayout(new GridLayout(2, true));
		
		org.eclipse.swt.widgets.Label remoteLabel = new org.eclipse.swt.widgets.Label(labelsComp, SWT.CENTER);
		remoteLabel.setText("REMOTE VERSION");
		remoteLabel.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
		remoteLabel.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_BLUE));
		remoteLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		org.eclipse.swt.widgets.Label localLabel = new org.eclipse.swt.widgets.Label(labelsComp, SWT.CENTER);
		localLabel.setText("LOCAL VERSION");
		localLabel.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
		localLabel.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
		localLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		ViewComparisonComposite comp = new ViewComparisonComposite(shell, SWT.NONE);
		comp.setDiagramModels(remoteDiagram, localDiagram);
		
		// Center the shell on the screen
		shell.pack();
		shell.setSize(1200, 800);
		org.eclipse.swt.graphics.Rectangle displayRect = shell.getDisplay().getPrimaryMonitor().getBounds();
		int x = displayRect.x + (displayRect.width - 1200) / 2;
		int y = displayRect.y + (displayRect.height - 800) / 2;
		shell.setLocation(x, y);
		
		shell.open();
	}

	/** Resolves table selection to an anchor {@link EObject}. */
	private EObject getAnyObjectFromSelection(Object selected) {
		if (selected instanceof UnifiedConflictRow u) {
			return u.anchorObject;
		}
		if (selected instanceof Conflict c) {
			return getAnyObject(c.getDifferences().get(0));
		}
		if (selected instanceof Diff d) {
			return getAnyObject(d);
		}
		return null;
	}

	/** Walks ancestors to find the containing {@link IDiagramModel}. */
	private IDiagramModel getDiagramModel(EObject obj) {
		if (obj == null) return null;
		if (obj instanceof IDiagramModel dm) return dm;
		EObject p = obj.eContainer();
		while (p != null) {
			if (p instanceof IDiagramModel dm) return dm;
			p = p.eContainer();
		}
		return null;
	}

	/** Builds the three-section text report (REAL / PSEUDO / automatic diffs). */
	private String generateHumanReadableReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== ARCHIMATE FULL MERGE AUDIT REPORT ===\n");
		sb.append("Generated on: ").append(new java.util.Date()).append("\n\n");

		List<Conflict> realConflicts = comparison.getConflicts().stream().filter(c -> c.getKind() == ConflictKind.REAL)
				.toList();

		sb.append(">>> SECTION 1: REAL CONFLICTS (Require your decision) <<<\n");
		if (realConflicts.isEmpty())
			sb.append("None\n");
		for (Conflict c : realConflicts) {
			appendConflictToReport(sb, c, "REAL");
		}

		List<Conflict> pseudoConflicts = comparison.getConflicts().stream()
				.filter(c -> c.getKind() == ConflictKind.PSEUDO).toList();

		sb.append("\n>>> SECTION 2: PSEUDO CONFLICTS (Changes are identical) <<<\n");
		if (pseudoConflicts.isEmpty())
			sb.append("None\n");
		for (Conflict c : pseudoConflicts) {
			appendConflictToReport(sb, c, "PSEUDO");
		}

		List<Diff> simpleDiffs = comparison.getDifferences().stream().filter(d -> d.getConflict() == null).toList();

		sb.append("\n>>> SECTION 3: AUTOMATIC CHANGES (No direct conflicts) <<<\n");
		if (simpleDiffs.isEmpty())
			sb.append("None\n");
		for (Diff d : simpleDiffs) {
			appendDiffToReport(sb, d);
		}

		sb.append("\n=== END OF REPORT ===");
		return sb.toString();
	}

	/** Appends one conflict block with base/local/remote property values. */
	private void appendConflictToReport(StringBuilder sb, Conflict c, String kind) {
		Diff first = c.getDifferences().get(0);
		Match m = first.getMatch();
		EObject obj = getAnyObject(first);
		
		sb.append(String.format("[%s] Object: %s (ID: %s)\n", kind, ArchiLabelProvider.INSTANCE.getLabel(obj),
				getObjectId(obj)));

		Set<EStructuralFeature> features = new HashSet<>();
		for (Diff d : c.getDifferences()) {
			EStructuralFeature f = getFeature(d);
			if (f != null)
				features.add(f);
		}

		for (EStructuralFeature f : features) {
			// EMF Compare scope: LEFT = remote branch, RIGHT = local (ours)
			sb.append("  • Property: ").append(getHumanName(f.getName())).append("\n");
			sb.append("    - BASE  : ").append(getVal(m.getOrigin(), f)).append("\n");
			sb.append("    - LOCAL : ").append(getVal(m.getRight(), f)).append("\n");
			sb.append("    - REMOTE: ").append(getVal(m.getLeft(), f)).append("\n");
		}
		sb.append("  RESOLUTION: ").append(getConflictResolution(c) ? "REMOTE" : "LOCAL").append("\n\n");
	}

	/** Appends a single non-conflicting diff line to the report. */
	private void appendDiffToReport(StringBuilder sb, Diff d) {
		EObject obj = getAnyObject(d);
		EStructuralFeature f = getFeature(d);
		if (f == null)
			return;

		String side = (d.getSource() == DifferenceSource.RIGHT) ? "LOCAL" : "REMOTE";
		sb.append(String.format("[%s CHANGE] Object: %s (%s)\n", side, ArchiLabelProvider.INSTANCE.getLabel(obj),
				obj.eClass().getName()));
		sb.append("  • Property: ").append(getHumanName(f.getName())).append("\n");
		sb.append("    - FROM: ").append(getVal(d.getMatch().getOrigin(), f)).append("\n");
		sb.append("    - TO  : ")
				.append(getVal(
						d.getSource() == DifferenceSource.RIGHT ? d.getMatch().getRight() : d.getMatch().getLeft(), f))
				.append("\n\n");
	}

	/** Attribute or reference feature touched by {@code d}, if any. */
	private EStructuralFeature getFeature(Diff d) {
		if (d instanceof AttributeChange ac)
			return ac.getAttribute();
		if (d instanceof ReferenceChange rc)
			return rc.getReference();
		return null;
	}

	/** Creates {@link #masterViewer} and column headers for the unified conflict grid. */
	private void createUnifiedTablePane(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, false));
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));

		masterViewer = new TableViewer(comp, SWT.BORDER | SWT.FULL_SELECTION);
		masterViewer.getTable().setHeaderVisible(true);
		masterViewer.getTable().setLinesVisible(true);
		masterViewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));

		createUnifiedCol("Resolution", 1, 150);
		createUnifiedCol("Object Name", 2, 160);
		createUnifiedCol("Property", 3, 130);
		createUnifiedCol("Location", 4, 180);
		createUnifiedCol("Local Value (Mine)", 5, 160);
		createUnifiedCol("Remote Value (Theirs)", 6, 160);
		createUnifiedCol("Object Type", 7, 140);
		masterViewer.setContentProvider(ArrayContentProvider.getInstance());
	}

	/**
	 * One table column with {@link ColumnLabelProvider} that reads {@link UnifiedConflictRow}.
	 *
	 * @param colIndex 1-based index matching {@code switch} in label provider
	 */
	private void createUnifiedCol(String title, final int colIndex, int width) {
		TableViewerColumn col = new TableViewerColumn(masterViewer, SWT.NONE);
		col.getColumn().setText(title);
		col.getColumn().setWidth(width);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				if (!(cell.getElement() instanceof UnifiedConflictRow u)) {
					return;
				}
				EObject obj = u.anchorObject;
				boolean choice = getConflictResolution(u.conflict);
				InspectionRow row = u.inspection;

				cell.setText(switch (colIndex) {
				case 1 -> choice ? ">> ACCEPT REMOTE" : "KEEP LOCAL <<";
				case 2 -> {
					String label = ArchiLabelProvider.INSTANCE.getLabel(obj);
					if (label == null || label.isEmpty()) {
						EObject container = obj != null ? obj.eContainer() : null;
						if (container != null) {
							label = ArchiLabelProvider.INSTANCE.getLabel(container);
						}
					}
					yield (label == null || label.isEmpty()) ? "(Unnamed)" : label;
				}
				case 3 -> row.name;
				case 4 -> ellipsizeTableCell(getObjectLocation(obj), TABLE_LOCATION_MAX_CHARS);
				case 5 -> ellipsizeTableCell(row.local, TABLE_MINE_THEIRS_MAX_CHARS);
				case 6 -> ellipsizeTableCell(row.remote, TABLE_MINE_THEIRS_MAX_CHARS);
				case 7 -> obj == null ? "" : obj.eClass().getName();
				default -> "";
				});

				if (colIndex == 2) {
					org.eclipse.swt.graphics.Image image = ArchiLabelProvider.INSTANCE.getImage(obj);
					if (image == null && obj != null && obj.eContainer() != null) {
						image = ArchiLabelProvider.INSTANCE.getImage(obj.eContainer());
					}
					cell.setImage(image);
				}

				if (colIndex == 1) {
					cell.setForeground(choice ? getShell().getDisplay().getSystemColor(SWT.COLOR_BLUE)
							: getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
					cell.setBackground(choice ? remoteBg : localBg);
				} else if (colIndex == 5) {
					cell.setForeground(null);
					cell.setBackground(localBg);
				} else if (colIndex == 6) {
					cell.setForeground(null);
					cell.setBackground(remoteBg);
				} else {
					cell.setForeground(null);
					cell.setBackground(choice ? remoteBg : localBg);
				}
			}
		});
	}

	/** Title-area message: unresolved REAL conflict count vs total. */
	private void updateConflictCountMessage() {
		if (visibleConflicts == null)
			return;
		long remaining = visibleConflicts.stream().filter(c -> !diffResolutions.containsKey(c.getDifferences().get(0)))
				.count();

		if (remaining > 0) {
			setMessage(String.format("There are %d unresolved REAL conflicts. Total: %d.", remaining,
					visibleConflicts.size()), IMessageProvider.WARNING);
		} else {
			setMessage(String.format("All %d REAL conflicts have been resolved. You can safely press OK.",
					visibleConflicts.size()), IMessageProvider.INFORMATION);
		}
	}

	/** Fills {@link #infoText} for one unified row: property, location, explanation, values, object id. */
	private void updateInfoTextForUnifiedRow(UnifiedConflictRow u) {
		StringBuilder sb = new StringBuilder();
		List<StyleRange> styles = new ArrayList<>();
		appendStyled(sb, styles, "SELECTED ROW (PROPERTY)\n", true);
		sb.append("\n");
		InspectionRow row = u.inspection;
		appendStyled(sb, styles, "  Property : ", true);
		sb.append(row.name).append("\n");
		if (!row.technicalPropertyName.isEmpty() && !row.technicalPropertyName.equals(row.name)) {
			appendStyled(sb, styles, "  Technical property : ", true);
			sb.append(row.technicalPropertyName).append("\n");
		}
		EObject infoObj = u.anchorObject;
		String locationFull = infoObj != null ? getObjectLocation(infoObj) : "";
		appendStyled(sb, styles, "  Location : ", true);
		sb.append(locationFull == null || locationFull.isEmpty() ? "(N/A)" : locationFull).append("\n");
		appendStyled(sb, styles, "  Context  : ", true);
		sb.append(row.ownerName).append("\n\n");
		appendStyled(sb, styles, "EXPLANATION\n", true);
		sb.append(generateHumanExplanation(row, u.conflict)).append("\n\n");
		appendStyled(sb, styles, "--- [ LOCAL VALUE (Mine) ] ---\n", true, localBg);
		sb.append(row.local).append("\n\n");
		appendStyled(sb, styles, "--- [ REMOTE VALUE (Theirs) ] ---\n", true, remoteBg);
		sb.append(row.remote).append("\n\n");
		if (infoObj != null) {
			appendStyled(sb, styles, "ROW OBJECT (this line)\n", true);
			sb.append("  Name : ").append(ArchiLabelProvider.INSTANCE.getLabel(infoObj)).append("\n");
			sb.append("  Type : ").append(humanType(infoObj)).append("\n");
			sb.append("  ID   : ").append(getObjectId(infoObj)).append("\n");
		}
		infoText.setText(sb.toString());
		infoText.setStyleRanges(styles.toArray(new StyleRange[0]));
	}

	/** Long formatted description of one {@link Conflict} with styled LOCAL/REMOTE hints. */
	private void appendFullConflictDescription(StringBuilder sb, List<StyleRange> styles, Conflict c) {
		if (c.getDifferences().isEmpty()) {
			appendStyled(sb, styles, "(Empty conflict)\n", true);
			return;
		}
		Diff firstDiff = c.getDifferences().get(0);
		EObject obj = getAnyObject(firstDiff);

		boolean resolveAsRemote = getConflictResolution(c);
		String kindLabel = c.getKind() == ConflictKind.REAL ? "⚠  REAL CONFLICT" : "ℹ  PSEUDO CONFLICT";
		appendStyled(sb, styles, kindLabel + "\n", true);
		sb.append("\n");

		appendStyled(sb, styles, "OBJECT\n", true);
		sb.append("  Name  : ").append(ArchiLabelProvider.INSTANCE.getLabel(obj)).append("\n");
		sb.append("  Type  : ").append(humanType(obj)).append("\n");
		sb.append("  ID    : ").append(getObjectId(obj)).append("\n");
		sb.append("  Path  : ").append(getObjectLocation(obj)).append("\n");
		sb.append("\n");

		appendStyled(sb, styles, "WHAT CHANGED\n", true);
		Set<EStructuralFeature> features = new LinkedHashSet<>();
		for (Diff d : c.getDifferences()) {
			EStructuralFeature f = getFeature(d);
			if (f != null) features.add(f);
		}
		Match m = firstDiff.getMatch();
		for (EStructuralFeature f : features) {
			sb.append("  Property : ").append(getHumanName(f.getName())).append("\n");
			sb.append("  ├─ Base  : ").append(getValRich(m.getOrigin(), f)).append("\n");
			String localVal  = getValRich(m.getRight(), f);
			String remoteVal = getValRich(m.getLeft(), f);
			if (resolveAsRemote) {
				appendStyled(sb, styles, "  ├─ Local  : ", true);
				sb.append(localVal).append("  (will be replaced)\n");
				appendStyled(sb, styles, "  └─ Remote : ", true);
				int remStart = sb.length();
				sb.append(remoteVal).append("  ✓ WILL BE USED\n");
				styles.add(new StyleRange(remStart, remoteVal.length() + 14,
						getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE), null, SWT.BOLD));
			} else {
				appendStyled(sb, styles, "  ├─ Local  : ", true);
				int locStart = sb.length();
				sb.append(localVal).append("  ✓ WILL BE KEPT\n");
				styles.add(new StyleRange(locStart, localVal.length() + 14,
						getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN), null, SWT.BOLD));
				appendStyled(sb, styles, "  └─ Remote : ", true);
				sb.append(remoteVal).append("  (will be discarded)\n");
			}
			sb.append("\n");
		}

		appendStyled(sb, styles, "RESOLUTION\n", true);
		int resStart = sb.length();
		String resLabel = resolveAsRemote ? "  → REMOTE ACCEPTED (remote changes will be applied)" 
				: "  → LOCAL KEPT (remote changes will be discarded)";
		sb.append(resLabel).append("\n");
		styles.add(new StyleRange(resStart, resLabel.length(),
				resolveAsRemote
					? getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE)
					: getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN),
					null, SWT.BOLD));
		sb.append("\n");

		appendStyled(sb, styles, "ALL DIFFS IN THIS CONFLICT\n", true);
		for (Diff d : c.getDifferences()) {
			String side = d.getSource() == DifferenceSource.LEFT ? "[REMOTE]" : "[LOCAL] ";
			EStructuralFeature f = getFeature(d);
			String feat = f != null ? getHumanName(f.getName()) : d.getClass().getSimpleName();
			String kind = d.getKind().name();
			sb.append("  ").append(side).append(" ").append(kind).append(" on «").append(feat).append("»\n");
		}
	}

	/** Legacy path: show {@link #appendFullConflictDescription} for a whole conflict (unused if only unified rows). */
	private void updateInfoTextForConflict(Conflict c) {
		StringBuilder sb = new StringBuilder();
		List<StyleRange> styles = new ArrayList<>();
		appendFullConflictDescription(sb, styles, c);
		infoText.setText(sb.toString());
		infoText.setStyleRanges(styles.toArray(new StyleRange[0]));
	}

	/** Returns a human-readable type name for an EObject. */
	private String humanType(EObject obj) {
		if (obj == null) return "null";
		String raw = obj.eClass().getName();
		return switch (raw) {
			case "AssociationRelationship" -> "Association Relationship";
			case "TriggeringRelationship"  -> "Triggering Relationship";
			case "FlowRelationship"        -> "Flow Relationship";
			case "CompositionRelationship" -> "Composition Relationship";
			case "AggregationRelationship" -> "Aggregation Relationship";
			case "RealizationRelationship" -> "Realization Relationship";
			case "ServingRelationship"     -> "Serving Relationship";
			case "AccessRelationship"      -> "Access Relationship";
			case "AssignmentRelationship"  -> "Assignment Relationship";
			case "InfluenceRelationship"   -> "Influence Relationship";
			case "SpecializationRelationship" -> "Specialization Relationship";
			case "ApplicationComponent"    -> "Application Component";
			case "ApplicationService"      -> "Application Service";
			case "ApplicationInterface"    -> "Application Interface";
			case "ArchimateDiagramModel"   -> "Diagram (View)";
			case "DiagramObject"           -> "Diagram Box";
			case "Connection"              -> "Diagram Connection";
			case "BusinessActor"           -> "Business Actor";
			case "BusinessRole"            -> "Business Role";
			case "BusinessProcess"         -> "Business Process";
			case "TechnologyService"       -> "Technology Service";
			case "TechnologyInteraction"   -> "Technology Interaction";
			default -> raw;
		};
	}

	/** One line for list cells / conflict text; ArchiLabelProvider is empty for IFeature, IProperty, etc. */
	private String mergeListItemLabel(EObject eo) {
		if (eo instanceof IFeature f) {
			String name = f.getName() != null ? f.getName() : "";
			String val = singleLineForDisplay(f.getValue());
			if (name.isEmpty() && val.isEmpty())
				return "Feature";
			if (name.isEmpty())
				return val;
			if (val.isEmpty())
				return name;
			return name + ": " + val;
		}
		if (eo instanceof IProperty p) {
			String key = p.getKey() != null ? p.getKey() : "";
			String val = singleLineForDisplay(p.getValue());
			if (key.isEmpty() && val.isEmpty())
				return "Property";
			if (key.isEmpty())
				return val;
			if (val.isEmpty())
				return key;
			return key + ": " + val;
		}
		return ArchiLabelProvider.INSTANCE.getLabel(eo);
	}

	/** Collapses whitespace/newlines for single-line UI strings. */
	private static String singleLineForDisplay(Object o) {
		if (o == null)
			return "";
		String s = String.valueOf(o);
		return s.replace('\n', ' ').replace('\r', ' ').trim();
	}

	/**
	 * Short preview for table cells; full text for Mine/Theirs stays in {@link InspectionRow};
	 * full location path is shown in the info panel.
	 */
	private static String ellipsizeTableCell(String s, int maxChars) {
		if (s == null)
			return "";
		String oneLine = s.replace('\n', ' ').replace('\r', ' ').replaceAll(" {2,}", " ").trim();
		if (oneLine.length() <= maxChars)
			return oneLine;
		if (maxChars <= 1)
			return "…";
		return oneLine.substring(0, maxChars - 1) + "…";
	}

	/**
	 * Like {@link #getVal(EObject, EStructuralFeature)} but for a single EObject with richer output:
	 * shows both the label and the ID for EObject values.
	 */
	private String getValRich(EObject obj, EStructuralFeature f) {
		if (obj == null) return "(None)";
		if (!obj.eClass().getEAllStructuralFeatures().contains(f)) return "(N/A)";
		Object v = obj.eGet(f);
		if (v instanceof List<?> list) {
			if (list.isEmpty()) return "(Empty)";
			return list.stream().map(item -> {
				if (item instanceof EObject eo) {
					String lbl = mergeListItemLabel(eo);
					String id  = getObjectId(eo);
					return lbl + (id.isEmpty() ? "" : " [" + id.substring(0, Math.min(8, id.length())) + "…]");
				}
				return String.valueOf(item);
			}).collect(Collectors.joining(", "));
		}
		if (v instanceof EObject eo) {
			String lbl = ArchiLabelProvider.INSTANCE.getLabel(eo);
			String id  = getObjectId(eo);
			String type = humanType(eo);
			return lbl + "  (" + type + ")"
					+ (id.isEmpty() ? "" : "\n             id: " + id);
		}
		return String.valueOf(v);
	}

	/** Appends {@code text} and registers a bold {@link StyleRange} starting at current length. */
	private void appendStyled(StringBuilder sb, List<StyleRange> styles, String text, boolean bold) {
		appendStyled(sb, styles, text, bold, null);
	}

	/** Appends {@code text} with optional bold and background highlight for {@link StyledText}. */
	private void appendStyled(StringBuilder sb, List<StyleRange> styles, String text, boolean bold, Color bg) {
		int start = sb.length();
		sb.append(text);
		StyleRange sr = new StyleRange(start, text.length(), null, bg);
		if (bold)
			sr.fontStyle = SWT.BOLD;
		styles.add(sr);
	}

	/** Short label for the Object Context column: element name plus owning view name (type is in the info panel). */
	private String getObjectShortDescription(EObject obj) {
		if (obj == null) return "";
		String name = ArchiLabelProvider.INSTANCE.getLabel(obj);
		
		// Walk up to the nearest diagram to show which view contains this object
		String viewName = "";
		EObject p = obj.eContainer();
		while (p != null) {
			if (p instanceof IDiagramModel dm) {
				viewName = " in " + dm.getName();
				break;
			}
			p = p.eContainer();
		}
		
		return name + viewName;
	}

	/** Maps EMF feature name to a short UI label for the Property column. */
	private String getHumanName(String tech) {
		return switch (tech) {
		case "sourceConnections" -> "Outgoing Connections";
		case "targetConnections" -> "Incoming Connections";
		case "archimateElement" -> "Model Link";
		case "bounds" -> "Geometry/Position";
		case "name" -> "Name";
		case "documentation" -> "Documentation";
		case "elements" -> "Elements";
		case "source" -> "Source (Start)";
		case "target" -> "Target (End)";
		case "bendpoints" -> "Line Bends (Bendpoints)";
		case "children" -> "Nested elements";
		case "features" -> "Visual style";
		default -> tech;
		};
	}

	/** Verbose string representation of {@code f} on {@code obj} for reports (includes EMF types in lists). */
	private String getVal(EObject obj, EStructuralFeature f) {
		if (obj == null) return "(None)";
		
		// Feature may be absent on this EClass (e.g. after partial load)
		if (!obj.eClass().getEAllStructuralFeatures().contains(f)) {
			return "(Not applicable)";
		}
		
		Object v = obj.eGet(f);
		if (v instanceof List<?> list) {
			if (list.isEmpty()) return "(Empty)";
			return list.stream()
					.map(item -> {
						if (item instanceof EObject eo) {
							String label = mergeListItemLabel(eo);
							String type = humanType(eo);
							return label + " (" + type + ")";
						}
						return String.valueOf(item);
					})
					.collect(Collectors.joining(", "));
		}
		if (v instanceof EObject eo) {
			String label = ArchiLabelProvider.INSTANCE.getLabel(eo);
			String type = humanType(eo);
			return label + " (" + type + ")";
		}
		return String.valueOf(v);
	}

	/**
	 * Table cell values: labels only (no EMF type suffix); full detail lives in the info panel.
	 */
	private String getValForDetailTable(EObject obj, EStructuralFeature f) {
		if (obj == null)
			return "(None)";
		if (!obj.eClass().getEAllStructuralFeatures().contains(f))
			return "(Not applicable)";

		Object v = obj.eGet(f);
		if (v instanceof List<?> list) {
			if (list.isEmpty())
				return "(Empty)";
			return list.stream().map(item -> {
				if (item instanceof EObject eo)
					return mergeListItemLabel(eo);
				return String.valueOf(item);
			}).collect(Collectors.joining(", "));
		}
		if (v instanceof EObject eo)
			return ArchiLabelProvider.INSTANCE.getLabel(eo);
		return String.valueOf(v);
	}

	/** Folder path from model root up to the owning view, e.g. {@code Strategy / View: Main}. */
	private String getObjectLocation(EObject obj) {
		if (obj == null) return "";
		EObject p = obj.eContainer();
		List<String> path = new ArrayList<>();
		while (p != null) {
			if (p instanceof IDiagramModel dm) {
				path.add(0, "View: " + dm.getName());
				break;
			}
			if (p instanceof IFolder f) path.add(0, f.getName());
			p = p.eContainer();
		}
		return String.join(" / ", path);
	}

	/** Delegates to {@link #generateHumanExplanation(InspectionRow, Conflict)} using current table selection’s conflict if any. */
	private String generateHumanExplanation(InspectionRow row) {
		IStructuredSelection masterSel = (IStructuredSelection) masterViewer.getSelection();
		if (!masterSel.isEmpty() && masterSel.getFirstElement() instanceof UnifiedConflictRow u) {
			return generateHumanExplanation(row, u.conflict);
		}
		return generateHumanExplanation(row, (Conflict) null);
	}

	/**
	 * User-facing paragraph for the info panel from property name / technical feature and chosen side.
	 *
	 * @param conflict may be {@code null} when no row is selected (defaults to LOCAL wording)
	 */
	private String generateHumanExplanation(InspectionRow row, Conflict conflict) {
		String prop = row.name;
		boolean isRemote = conflict != null && getConflictResolution(conflict);

		String action = isRemote ? "REMOTE version will be applied" : "LOCAL version will be kept";
		
		if (prop.contains("Target")) {
			return "Conflict in the relationship endpoint. LOCAL points to «" + row.local + "», while REMOTE points to «" + row.remote + "». " + action + ".";
		}
		if (prop.contains("Source")) {
			return "Conflict in the relationship starting point. LOCAL starts from «" + row.local + "», while REMOTE starts from «" + row.remote + "». " + action + ".";
		}
		if (prop.contains("Name")) {
			return "The object has different names. LOCAL: «" + row.local + "», REMOTE: «" + row.remote + "». " + action + ".";
		}
		if (prop.contains("Geometry")) {
			return "The position or size of this element on the diagram has been changed on both sides. " + action + ".";
		}
		if (prop.contains("Line Bends")) {
			return "The visual path (bendpoints) of the connection line has been modified differently. " + action + ".";
		}
		if (prop.contains("Documentation")) {
			return "The documentation/description text is different. " + action + ".";
		}
		if (prop.contains("Connections")) {
			return "The list of visual connections for this element has changed. This often happens when relationships are reconnected. " + action + ".";
		}
		if (prop.contains("Model Link")) {
			return "The link between this visual element and the underlying logical model element is different. " + action + ".";
		}
		if ("children".equals(row.technicalPropertyName)) {
			return "This conflict concerns the list of nested shapes inside this container on the diagram (child diagram objects). "
					+ "It reflects how elements are grouped or stacked inside a parent box on the view—not the logical ArchiMate "
					+ "elements in the model tree. Differences often come from adding or removing inner shapes, or from EMF Compare "
					+ "detecting a different order of the same children (reordering may appear as a conflict even when the canvas looks similar). "
					+ "Choosing a side decides which nesting and child set is kept on this view. " + action + ".";
		}
		if ("features".equals(row.technicalPropertyName)) {
			return "This conflict concerns diagram-only appearance settings for this object: things like fill colour, border, line style, "
					+ "whether an icon is shown, and the label expression template (how the name is built from properties). "
					+ "These settings affect how the element is drawn on the view; they do not replace or rename the underlying "
					+ "ArchiMate element linked from the model. If both sides changed styling independently, you must pick which "
					+ "presentation to keep. " + action + ".";
		}

		return "Property «" + prop + "» has conflicting values. " + action + ".";
	}

	/** @return Archi id or empty string */
	private String getObjectId(EObject obj) {
		return (obj instanceof IIdentifier id) ? id.getId() : "";
	}

	/** Prefers RIGHT (local) object from a diff’s match. */
	private EObject getAnyObject(Diff d) {
		return d.getMatch().getRight() != null ? d.getMatch().getRight() : d.getMatch().getLeft();
	}

	/**
	 * @return {@code true} if user chose REMOTE for this conflict (stored on the first diff in the conflict)
	 */
	private boolean getConflictResolution(Conflict c) {
		if (c == null || c.getDifferences().isEmpty()) return false;
		return diffResolutions.getOrDefault(c.getDifferences().get(0), false);
	}

	/** Placeholder for future integrity drill-down from debug XML validator. */
	private String findFirstMismatchID() {
		return "Unknown";
	}

	/** Placeholder: always {@code true} (would cross-check relationship vs connection wiring). */
	private boolean validateIntegrity() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	protected void okPressed() {
		super.okPressed();
	}

	/**
	 * @return map copied by {@link MergeHandler}: {@code true} means user accepted REMOTE for that diff’s conflict side
	 */
	public Map<Diff, Boolean> getDiffResolutions() {
		return diffResolutions;
	}

	/** {@inheritDoc} */
	@Override
	protected Point getInitialSize() {
		return new Point(1100, 760);
	}

	/** Disposes SWT colors allocated in the constructor. */
	@Override
	public boolean close() {
		diffColor.dispose();
		return super.close();
	}

	private static class InspectionRow {
		String name, remote, local, ownerName;
		/** EMF structural feature name (e.g. children, features) for tailored explanations. */
		final String technicalPropertyName;
		boolean isDifferent;
		/**
		 * @param n                    display name ({@link MergeConflictDialog#getHumanName(String)})
		 * @param r                    remote (left) cell text
		 * @param l                    local (right) cell text
		 * @param d                    reserved for future “changed” highlighting
		 * @param o                    short owner/context string
		 * @param technicalPropertyName EMF {@link EStructuralFeature#getName()}
		 */
		InspectionRow(String n, String r, String l, boolean d, String o, String technicalPropertyName) {
			name = n;
			remote = r;
			local = l;
			isDifferent = d;
			ownerName = o;
			this.technicalPropertyName = technicalPropertyName != null ? technicalPropertyName : "";
		}
	}

	/** One row in the unified table: a property line belonging to a {@link Conflict}. */
	private static final class UnifiedConflictRow {
		final Conflict conflict;
		final InspectionRow inspection;
		final EObject anchorObject;

		/**
		 * @param conflict     owning REAL conflict
		 * @param inspection   property-level row data
		 * @param anchorObject object shown in Object/Type columns (usually RIGHT side)
		 */
		UnifiedConflictRow(Conflict conflict, InspectionRow inspection, EObject anchorObject) {
			this.conflict = conflict;
			this.inspection = inspection;
			this.anchorObject = anchorObject;
		}
	}

	/** {@link MessageDialog} with an extra read-only text area (technical detail). */
	private static class CopyableErrorDialog extends MessageDialog {
		private String techText;
		/** Shows error dialog with selectable {@code tech} body. */
		public static void open(Shell parent, String title, String msg, String tech) {
			new CopyableErrorDialog(parent, title, msg, tech).open();
		}

		/** @param tech extra detail shown below the main message */
		protected CopyableErrorDialog(Shell parent, String title, String msg, String tech) {
			super(parent, title, null, msg, MessageDialog.ERROR, new String[] { "OK" }, 0);
			this.techText = tech;
		}
		/** {@inheritDoc} */
		@Override
		protected Control createCustomArea(Composite parent) {
			org.eclipse.swt.widgets.Text text = new org.eclipse.swt.widgets.Text(parent, SWT.BORDER | SWT.READ_ONLY);
			text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			text.setText(techText);
			text.setSelection(0, techText.length());
			return text;
		}
	}
}
