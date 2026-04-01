package com.archimatetool.modelrepository.merge;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.modelrepository.repository.GitUtils;

/**
 * Low-level utility methods for ArchiMate and EMF Compare operations.
 */
public class MergeUtils {

    /**
     * Resolves the merge-base commit (best common ancestor) for two revisions.
     *
     * @param utils open repository helper
     * @param rev1  first revision spec
     * @param rev2  second revision spec
     * @return merge-base SHA, or {@code null} if none
     */
    public static String getMergeBase(GitUtils utils, String rev1, String rev2) throws IOException {
        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(utils.getRepository())) {
            org.eclipse.jgit.revwalk.RevCommit c1 = walk.parseCommit(utils.getRepository().resolve(rev1));
            org.eclipse.jgit.revwalk.RevCommit c2 = walk.parseCommit(utils.getRepository().resolve(rev2));
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(c1);
            walk.markStart(c2);
            org.eclipse.jgit.revwalk.RevCommit base = walk.next();
            return base != null ? base.name() : null;
        }
    }

    /**
     * Extracts a single file at {@code path} from commit {@code rev} into {@code dest}.
     *
     * @param utils open Git helper
     * @param rev   commit id
     * @param path  path inside the commit tree
     * @param dest  output file (overwritten if present)
     */
    public static void extractFileFromGit(GitUtils utils, String rev, String path, File dest) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = utils.getRepository();
                org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            org.eclipse.jgit.lib.ObjectId commitId = repository.resolve(rev);
            org.eclipse.jgit.revwalk.RevCommit commit = revWalk.parseCommit(commitId);
            org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repository, path,
                    commit.getTree());
            if (treeWalk != null) {
                org.eclipse.jgit.lib.ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                    loader.copyTo(fos);
                }
            }
        }
    }

    /**
     * Returns the Archi persistent identifier string for an object, or {@code null}.
     *
     * @param obj any EMF object
     */
    public static String getObjectId(EObject obj) {
        return (obj instanceof com.archimatetool.model.IIdentifier id) ? id.getId() : null;
    }

    /**
     * Finds any model element with the given Archi ID anywhere under {@code model}.
     *
     * @param model root ArchiMate model
     * @param id    element id to resolve
     */
    public static EObject findObjectById(com.archimatetool.model.IArchimateModel model, String id) {
        if (id == null || model == null)
            return null;
        for (EObject eObject : (Iterable<EObject>) () -> model.eAllContents()) {
            if (eObject instanceof com.archimatetool.model.IIdentifier identifier && id.equals(identifier.getId()))
                return eObject;
        }
        return null;
    }

    /**
     * Returns the EObject on the given comparison side for a {@link Match} (LEFT or RIGHT).
     *
     * @param match EMF Compare match
     * @param side  {@link DifferenceSource#LEFT} or {@link DifferenceSource#RIGHT}
     */
    public static EObject getAnyObjectFromMatch(Match match, DifferenceSource side) {
        return (side == DifferenceSource.RIGHT) ? match.getRight() : match.getLeft();
    }

	/**
	 * Collects every {@link IDiagramModel} that contains at least one changed object from REMOTE or involved in any conflict.
	 *
	 * @param comparison finished EMF Compare result
	 * @return distinct diagram roots (views) touched by those diffs
	 */
	public static Set<IDiagramModel> getAffectedDiagrams(Comparison comparison) {
		Set<IDiagramModel> affected = new HashSet<>();
		for (Diff diff : comparison.getDifferences()) {
			// Only consider diagrams affected if there is a change from REMOTE
			// or a conflict that was resolved.
			if (diff.getSource() == DifferenceSource.LEFT || diff.getConflict() != null) {
				EObject obj = getObjectFromDiff(diff);
				if (obj != null) {
					EObject container = obj;
					while (container != null && !(container instanceof IDiagramModel)) {
						container = container.eContainer();
					}
					if (container instanceof IDiagramModel dm) {
						affected.add(dm);
					}
				}
			}
		}
		return affected;
	}

    /**
     * Picks a representative EObject from a diff’s match (prefers RIGHT, then LEFT, then origin).
     *
     * @param diff any difference in a comparison
     */
    public static EObject getObjectFromDiff(Diff diff) {
        if (diff.getMatch() != null) {
            if (diff.getMatch().getRight() != null)
                return diff.getMatch().getRight();
            if (diff.getMatch().getLeft() != null)
                return diff.getMatch().getLeft();
            if (diff.getMatch().getOrigin() != null)
                return diff.getMatch().getOrigin();
        }
        return null;
    }

    /**
     * Finds the diagram shape ({@link IDiagramModelArchimateObject}) on {@code diagram} that represents {@code element}.
     *
     * @param diagram view to search
     * @param element logical ArchiMate element (reference equality to {@code archimateElement})
     */
    public static IDiagramModelArchimateObject findViewFor(IDiagramModel diagram, EObject element) {
        if (element == null)
            return null;
        for (Iterator<EObject> it = diagram.eAllContents(); it.hasNext();) {
            EObject obj = it.next();
            if (obj instanceof IDiagramModelArchimateObject view) {
                if (element.equals(view.getArchimateElement())) {
                    return view;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a concept reference inside {@code model} (proxy → real object in this resource).
     *
     * @param model   merged or local model resource
     * @param concept element or relationship reference (may be proxy)
     */
    public static IArchimateConcept resolveConceptInModel(IArchimateModel model, IArchimateConcept concept) {
        if (concept == null || model == null)
            return null;
        if (concept.eIsProxy()) {
            String id = getObjectId(concept);
            if (id == null || id.isEmpty())
                return null;
            EObject o = findObjectById(model, id);
            return o instanceof IArchimateConcept c ? c : null;
        }
        return concept;
    }

    /**
     * Like {@link #findViewFor} but also matches diagram nodes by {@link IArchimateElement#getId()} when
     * object identity differs (common right after EMF merge / unresolved proxies).
     */
    public static IDiagramModelArchimateObject findViewForConcept(IArchimateModel model, IDiagramModel diagram,
            IArchimateConcept concept) {
        IArchimateConcept resolved = resolveConceptInModel(model, concept);
        if (resolved == null || diagram == null)
            return null;
        IDiagramModelArchimateObject byRef = findViewFor(diagram, resolved);
        if (byRef != null)
            return byRef;
        String cid = getObjectId(resolved);
        if (cid == null || cid.isEmpty())
            return null;
        for (Iterator<EObject> it = diagram.eAllContents(); it.hasNext();) {
            EObject obj = it.next();
            if (obj instanceof IDiagramModelArchimateObject view) {
                IArchimateElement el = view.getArchimateElement();
                if (el != null && cid.equals(el.getId()))
                    return view;
            }
        }
        return null;
    }

    /**
     * Archi often draws a Flow from a <em>nested</em> diagram shape whose {@code archimateElement} is a
     * child component, while the logical {@link IArchimateRelationship} is attached to a parent
     * {@link IArchimateElement} (e.g. whole application). Strict {@code viewElement == relEnd} then fails
     * and {@link com.archimatetool.modelrepository.merge.ModelHealer#syncVisualConnections} may delete or
     * rewire the line incorrectly.
     * <p>
     * This returns true if {@code view} represents {@code relEndpoint}, or if {@code view} is contained
     * (in the diagram tree) inside another {@link IDiagramModelArchimateObject} that represents
     * {@code relEndpoint}.
     *
     * @param diagram    view containing {@code view}
     * @param view       diagram object under test
     * @param relEndpoint logical endpoint of a relationship
     * @param model      merged model (for ID resolution)
     */
    public static boolean diagramArchimateObjectVisualMatchesRelationshipEndpoint(IDiagramModel diagram,
            IDiagramModelArchimateObject view, IArchimateConcept relEndpoint, IArchimateModel model) {
        if (view == null || relEndpoint == null || diagram == null || model == null)
            return false;
        // Use ID-based comparison throughout: after BatchMerger the "resolved" object and the
        // archimateElement of a diagram node may be different in-memory instances for the same
        // logical element. Reference equality (Object.equals default) would return false and
        // cause syncVisualConnections to wrongly rewire or delete a valid connection.
        String endpointId = getObjectId(relEndpoint);
        if (endpointId == null || endpointId.isEmpty())
            return false;
        IArchimateElement ve = view.getArchimateElement();
        if (ve != null && endpointId.equals(ve.getId()))
            return true;
        for (EObject cur = view.eContainer(); cur != null && cur != diagram; cur = cur.eContainer()) {
            if (cur instanceof IDiagramModelArchimateObject ancestor) {
                IArchimateElement ae = ancestor.getArchimateElement();
                if (ae != null && endpointId.equals(ae.getId()))
                    return true;
            }
        }
        return false;
    }

    /**
     * Collects image paths referenced by the model that have no bytes in the project archive.
     *
     * @param model ArchiMate model with {@link com.archimatetool.editor.model.IArchiveManager} adapter
     */
    public static Set<String> getMissingImagePaths(com.archimatetool.model.IArchimateModel model) {
        Set<String> missingPaths = new HashSet<>();
        com.archimatetool.editor.model.IArchiveManager archiveManager = (com.archimatetool.editor.model.IArchiveManager) model.getAdapter(com.archimatetool.editor.model.IArchiveManager.class);
        for (String imagePath : archiveManager.getImagePaths()) {
            if (archiveManager.getBytesFromEntry(imagePath) == null) {
                missingPaths.add(imagePath);
            }
        }
        return missingPaths;
    }
}
