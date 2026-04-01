/**
 * Orchestrates Git merge of the ArchiMate model in a repository.
 * <ol>
 * <li>Fetches ours / theirs / base model revisions via Git.</li>
 * <li>Runs EMF Compare for semantic, ID-aware diffing (not plain text).</li>
 * <li>Applies merge results and runs {@link ModelHealer} so in-memory references and diagram
 * connections stay consistent after XML-level merge.</li>
 * </ol>
 */
package com.archimatetool.modelrepository.merge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.merge.IMerger.RegistryImpl;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.modelrepository.IRunnable;
import com.archimatetool.modelrepository.repository.BranchInfo;
import com.archimatetool.modelrepository.repository.GitUtils;
import com.archimatetool.modelrepository.repository.IArchiRepository;
import com.archimatetool.modelrepository.repository.RepoConstants;

/**
 * Workbench entry point for merging another Git branch into the checked-out model: three-way EMF Compare,
 * optional conflict UI, {@link ModelHealer}, validation, and commit.
 */
@SuppressWarnings("nls")
public class MergeHandler {

	private IWorkbenchWindow workbenchWindow;

	/**
	 * @param workbenchWindow used for shells, progress dialogs, and display-thread validation
	 */
	public MergeHandler(IWorkbenchWindow workbenchWindow) {
		this.workbenchWindow = workbenchWindow;
	}

	/**
	 * Merges {@code branchToMerge} into the repository’s current HEAD model file.
	 * May open {@link MergeConflictDialog} when REAL conflicts exist; rolls back the Git merge on failure.
	 *
	 * @param repo            open Archi repository
	 * @param branchToMerge   branch or ref to merge into HEAD
	 * @return merge outcome enum
	 * @throws IOException    Git or I/O errors
	 * @throws GitAPIException JGit API errors from merge/commit
	 */
	public MergeHandlerResult merge(IArchiRepository repo, BranchInfo branchToMerge)
			throws IOException, GitAPIException {
		System.out.println("[MergeHandler] Merging " + branchToMerge.getFullName());

		if (branchToMerge.isRefAtHead()) {
			return MergeHandlerResult.ALREADY_UP_TO_DATE;
		}

		try (GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
			if (utils.isMergedInto(RepoConstants.HEAD, branchToMerge.getFullName())) {
				utils.resetToRef(branchToMerge.getFullName());
				return MergeHandlerResult.MERGED_OK;
			}

			MergeResult mergeResult = utils.merge().include(branchToMerge.getRef()).setCommit(false)
					.setFastForward(FastForwardMode.NO_FF).setStrategy(MergeStrategy.RECURSIVE).call();

			if (mergeResult.getMergeStatus() == MergeStatus.ALREADY_UP_TO_DATE) {
				return MergeHandlerResult.ALREADY_UP_TO_DATE;
			}

			return handle3WayMerge(utils, branchToMerge, repo);
		} catch (Exception ex) {
			try (GitUtils utils = GitUtils.open(repo.getWorkingFolder())) {
				utils.resetToRef(RepoConstants.HEAD);
			}
			throw new IOException(ex);
		}
	}

	/**
	 * Loads ours/theirs/base, runs {@link MergeFactory#createComparison}, then either automatic merge or conflict UI.
	 */
	private MergeHandlerResult handle3WayMerge(GitUtils utils, BranchInfo branchToMerge, IArchiRepository repo)
			throws IOException, GitAPIException {
		System.out.println("[MergeHandler] Handling 3Way merge...");
		ProgressMonitorDialog progressDialog = new ProgressMonitorDialog(workbenchWindow.getShell());

		AtomicReference<IArchimateModel> ourModelRef = new AtomicReference<>();
		AtomicReference<IArchimateModel> theirModelRef = new AtomicReference<>();
		AtomicReference<IArchimateModel> baseModelRef = new AtomicReference<>();

		try {
			IRunnable.run(progressDialog, true, false, monitor -> {
				monitor.beginTask("Extracting models...", IProgressMonitor.UNKNOWN);
				ourModelRef.set(ModelLoader.loadModel(utils, RepoConstants.HEAD));
				theirModelRef.set(ModelLoader.loadModel(utils, branchToMerge.getFullName()));
				baseModelRef.set(ModelLoader.loadBaseModel(utils, branchToMerge.getFullName()));
			});
		} catch (Exception ex) {
			throw new IOException(ex);
		}

		IArchimateModel ourModel = ourModelRef.get();
		IArchimateModel theirModel = theirModelRef.get();
		IArchimateModel baseModel = baseModelRef.get();

		Comparison comparison = MergeFactory.createComparison(theirModel, ourModel, baseModel);

		long realConflictsCount = comparison.getConflicts().stream().filter(c -> c.getKind() == ConflictKind.REAL)
				.count();

		if (realConflictsCount > 0) {
			return handleConflictingMerge(progressDialog, utils, branchToMerge, comparison, ourModel, theirModel, repo);
		} else {
			return applyAutomaticMerge(progressDialog, utils, branchToMerge, comparison, ourModel, theirModel, repo);
		}
	}

	/**
	 * Applies all non-conflicting REMOTE ({@code LEFT}) diffs via {@link BatchMerger}, heals, validates, commits.
	 */
	private MergeHandlerResult applyAutomaticMerge(ProgressMonitorDialog progressDialog, GitUtils utils,
			BranchInfo branchToMerge, Comparison comparison, IArchimateModel ourModel, IArchimateModel theirModel, IArchiRepository repo)
			throws IOException, GitAPIException {

		IMerger.Registry mergerRegistry = RegistryImpl.createStandaloneInstance();

		try {
			IRunnable.run(progressDialog, true, false, monitor -> {
				monitor.beginTask("Merging and Validating...", IProgressMonitor.UNKNOWN);

				logDiagramConnections(ourModel, "BEFORE-BATCH");

				new BatchMerger(mergerRegistry).copyAllLeftToRight(comparison.getDifferences().stream()
						.filter(org.eclipse.emf.compare.utils.EMFComparePredicates
								.fromSide(org.eclipse.emf.compare.DifferenceSource.LEFT))
						.collect(Collectors.toList()), new BasicMonitor());

				logDiagramConnections(ourModel, "AFTER-BATCH");

				fixMissingImages(ourModel, theirModel);

				// Healing
				new ModelHealer(ourModel).heal(comparison, theirModel);

				logDiagramConnections(ourModel, "AFTER-HEAL");

				// Clean orphans
				ourModel.eResource().getContents().removeIf(obj -> obj != ourModel && obj.eContainer() == null);

				// Validation
				validateModel(ourModel);
			});

			saveAndCommit(progressDialog, utils, ourModel, "Merge branch ''{1}''", branchToMerge, repo, false);
			return MergeHandlerResult.MERGED_OK;

		} catch (Exception ex) {
			utils.resetToRef("HEAD");
			if (ex.getMessage() != null && ex.getMessage().contains("VALIDATION_FAILED")) {
				return MergeHandlerResult.CANCELLED;
			}
			throw new IOException("Merge failed: " + ex.getMessage(), ex);
		}
	}

	/** Debug: prints every diagram ArchiMate connection’s declared source/target ids. */
	private void logDiagramConnections(IArchimateModel model, String phase) {
		model.eAllContents().forEachRemaining(obj -> {
			if (obj instanceof IDiagramModel dm) {
				dm.eAllContents().forEachRemaining(n -> {
					if (n instanceof IDiagramModelArchimateObject dmo) {
						for (var c : dmo.getSourceConnections()) {
							System.out.println("[" + phase + "] diagram=" + dm.getName()
								+ " node=" + dmo.getId()
								+ " srcConn=" + c.getId()
								+ " conn.src=" + (c.getSource() != null ? c.getSource().getId() : "null")
								+ " conn.tgt=" + (c.getTarget() != null ? c.getTarget().getId() : "null"));
						}
					}
				});
			}
		});
	}

	/**
	 * Copies missing image bytes from {@code otherModel}’s archive into {@code model}’s archive for shared paths.
	 */
	private void fixMissingImages(IArchimateModel model, IArchimateModel otherModel) {
		Set<String> missingPaths = MergeUtils.getMissingImagePaths(model);
		if (missingPaths.isEmpty())
			return;

		IArchiveManager archiveManager = (IArchiveManager) model.getAdapter(IArchiveManager.class);
		IArchiveManager otherArchiveManager = (IArchiveManager) otherModel.getAdapter(IArchiveManager.class);

		for (String imagePath : missingPaths) {
			byte[] bytes = otherArchiveManager.getBytesFromEntry(imagePath);
			if (bytes != null) {
				try {
					archiveManager.addByteContentEntry(imagePath, bytes);
				} catch (IOException ex) {
					System.out.println("[MergeHandler] Could not load image: " + imagePath);
					ex.printStackTrace();
				}
			}
		}
	}

	/**
	 * Opens {@link MergeConflictDialog}, applies filtered remote diffs and {@link ModelHealer} with user resolutions, then commits.
	 */
	private MergeHandlerResult handleConflictingMerge(ProgressMonitorDialog progressDialog, GitUtils utils,
			BranchInfo branchToMerge, Comparison comparison, IArchimateModel ourModel, IArchimateModel theirModel, IArchiRepository repo) throws IOException, GitAPIException {

		AtomicReference<Map<Diff, Boolean>> resolutionsRef = new AtomicReference<>();

		workbenchWindow.getShell().getDisplay().syncExec(() -> {
			MergeConflictDialog dialog = new MergeConflictDialog(workbenchWindow.getShell(), comparison);
			if (dialog.open() == org.eclipse.jface.window.Window.OK) {
				resolutionsRef.set(dialog.getDiffResolutions());
			}
		});

		if (resolutionsRef.get() == null) {
			try {
				utils.resetToRef(RepoConstants.HEAD);
			} catch (Exception e) {
			}
			return MergeHandlerResult.CANCELLED;
		}

		Map<Diff, Boolean> resolutions = resolutionsRef.get();
		IMerger.Registry mergerRegistry = IMerger.RegistryImpl.createStandaloneInstance();

		try {
			IRunnable.run(progressDialog, true, false, monitor -> {
				monitor.beginTask("Merging models...", IProgressMonitor.UNKNOWN);

				List<Diff> toApply = filterDiffsToApply(comparison, resolutions);

				if (!toApply.isEmpty()) {
					new BatchMerger(mergerRegistry).copyAllLeftToRight(toApply, new BasicMonitor());
				}

				// Healing
				new ModelHealer(ourModel).heal(comparison, theirModel, resolutions);

				ourModel.eAdapters().clear();
				
				// Validation
				validateModel(ourModel);
			});

			saveAndCommit(progressDialog, utils, ourModel, "Merge branch ''{1}'' with resolved conflicts", branchToMerge, repo, false);
			return MergeHandlerResult.MERGED_WITH_CONFLICTS_RESOLVED;

		} catch (Exception ex) {
			utils.resetToRef("HEAD");
			if (ex.getMessage() != null && ex.getMessage().contains("VALIDATION_FAILED")) {
				return MergeHandlerResult.CANCELLED;
			}
			throw new IOException("Merge failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * LEFT (remote) diffs safe to merge with {@link BatchMerger}: skips conflict members, local-only objects, and destructive list ops when keeping LOCAL.
	 */
	private List<Diff> filterDiffsToApply(Comparison comparison, Map<Diff, Boolean> resolutions) {
		List<Diff> toApply = new ArrayList<>();
		Set<EObject> protectedObjects = new HashSet<>();

		for (Conflict conflict : comparison.getConflicts()) {
			for (Diff d : conflict.getDifferences()) {
				EObject obj = MergeUtils.getObjectFromDiff(d);
				if (obj != null) protectedObjects.add(obj);
			}
		}
		for (Diff diff : comparison.getDifferences()) {
			if (diff.getSource() == DifferenceSource.RIGHT) {
				EObject obj = MergeUtils.getObjectFromDiff(diff);
				if (obj != null) protectedObjects.add(obj);
			}
		}

		for (Diff diff : comparison.getDifferences()) {
			Conflict conflict = diff.getConflict();
			if (conflict != null) {
				// Conflict resolutions are applied directly in ModelHealer via eSet,
				// not through BatchMerger. BatchMerger behaves unpredictably for conflict
				// diffs (it may call removeFromRight on RIGHT diffs, reverting them to BASE).
				continue;
			}
			if (diff.getSource() == DifferenceSource.LEFT) {
				EObject remoteObj = MergeUtils.getObjectFromDiff(diff);
				if (remoteObj != null && protectedObjects.contains(remoteObj)) continue;
				
				// Protection: Don't allow REMOTE to delete or move objects in LOCAL collections (folders, diagrams)
				// if we are resolving conflicts in favor of LOCAL.
				if (diff instanceof ReferenceChange rc && rc.getReference().isMany()) {
					boolean isRemoteResolution = Boolean.TRUE.equals(resolutions.get(diff));
					if (!isRemoteResolution) {
						// If REMOTE wants to REMOVE something from a list, but that something exists in LOCAL,
						// we skip this list-change to preserve LOCAL structure.
						if (rc.getKind() == DifferenceKind.DELETE || rc.getKind() == DifferenceKind.MOVE) {
							continue; 
						}
					}
				}

				if (!(diff.getKind() == DifferenceKind.DELETE && diff.getMatch().getLeft() == null)) {
					toApply.add(diff);
				}
			}
		}
		return toApply;
	}

	/**
	 * Runs {@link ModelCheckerLemana} on the UI thread; throws {@code VALIDATION_FAILED} if errors were reported.
	 */
	private void validateModel(IArchimateModel model) {
		AtomicReference<Boolean> isValid = new AtomicReference<>(true);
		workbenchWindow.getShell().getDisplay().syncExec(() -> {
			ModelCheckerLemana checker = new ModelCheckerLemana(model);
			if (!checker.checkAll()) {
				isValid.set(false);
				MergeUIUtils.showModelErrorsDialog(checker.getErrorMessages());
			}
		});

		if (!isValid.get()) {
			throw new RuntimeException("VALIDATION_FAILED");
		}
	}

	/**
	 * Strips proxy cross-references, saves model + archive, commits, optionally opens post-merge compare.
	 */
	private void saveAndCommit(ProgressMonitorDialog progressDialog, GitUtils utils, IArchimateModel model, String message, BranchInfo branchToMerge, IArchiRepository repo, boolean showCompare) throws IOException {
		model.setFile(new File(utils.getRepository().getWorkTree(), RepoConstants.MODEL_FILENAME));
		
		// Final proxy cleanup
		model.eAllContents().forEachRemaining(obj -> {
			for (Iterator<EObject> it = obj.eCrossReferences().iterator(); it.hasNext();) {
				if (it.next().eIsProxy()) it.remove();
			}
		});

		saveModel(model);

		if (model.eResource() != null) {
			model.eResource().unload();
		}

		commitChanges(progressDialog, utils, message, branchToMerge);

		// Show comparison dialog if requested (after commit)
		if (showCompare) {
			try (org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(utils.getRepository())) {
				org.eclipse.jgit.revwalk.RevCommit remoteCommit = revWalk.parseCommit(branchToMerge.getRef().getObjectId());
				utils.getLatestCommit().ifPresent(newHeadCommit -> {
					MergeUIUtils.showCompareDialog(repo, remoteCommit, newHeadCommit);
				});
			}
		}
	}

	/** Persists the model file and bundled archive through Archi APIs. */
	private void saveModel(IArchimateModel model) throws IOException {
		IArchiveManager archiveManager = (IArchiveManager) model.getAdapter(IArchiveManager.class);
		if (archiveManager != null) {
			archiveManager.saveModel();
		}
		IEditorModelManager.INSTANCE.saveModel(model);
	}

	/** Binds {@code message} with branch names and commits the working tree via {@link GitUtils#commitChangesWithManifest}. */
	private void commitChanges(ProgressMonitorDialog progressDialog, GitUtils utils, String message,
			BranchInfo branchToMerge) throws IOException {
		String fullMessage = NLS.bind(message, new Object[] { branchToMerge.isRemote() ? " remote " : " ",
				branchToMerge.getShortName(), utils.getCurrentLocalBranchName().orElse("null") });
		try {
			IRunnable.run(progressDialog, true, false, monitor -> {
				monitor.beginTask("Committing...", IProgressMonitor.UNKNOWN);
				utils.commitChangesWithManifest(fullMessage, false);
			});
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/** Outcome of {@link #merge(IArchiRepository, BranchInfo)}. */
	public enum MergeHandlerResult {
		/** Merge completed and committed (no or auto-resolved conflicts). */
		MERGED_OK,
		/** Nothing to merge. */
		ALREADY_UP_TO_DATE,
		/** User resolved REAL conflicts in the dialog; result committed. */
		MERGED_WITH_CONFLICTS_RESOLVED,
		/** User cancelled or validation failed after dialog. */
		CANCELLED
	}
}
