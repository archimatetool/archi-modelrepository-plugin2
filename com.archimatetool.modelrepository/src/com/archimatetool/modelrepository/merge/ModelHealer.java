package com.archimatetool.modelrepository.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;

/**
 * Service for "healing" ArchiMate models after merging.
 * Fixes broken references, orphans, and synchronization issues.
 */
public class ModelHealer {

    private final IArchimateModel targetModel;

    /**
     * @param targetModel merged LOCAL model that will be saved (healing mutates it in place)
     */
    public ModelHealer(IArchimateModel targetModel) {
        this.targetModel = targetModel;
    }

    /**
     * Full post-merge pipeline: conflict {@code eSet}, folder repair, connection list sync, visual line repair, dedupe, validation prep.
     *
     * @param comparison  EMF Compare result used for conflict application and context
     * @param theirModel  REMOTE-side model (donor for missing pieces); may be {@code null} in tests
     * @param resolutions per-diff map from {@link MergeConflictDialog}: {@code true} means user chose REMOTE for that conflict’s diffs
     */
    public void heal(Comparison comparison, IArchimateModel theirModel, Map<Diff, Boolean> resolutions) {
        logAllDiagramConnections("BEFORE-HEAL");
        if (theirModel != null) {
            forceHealMissingRelations(theirModel);
        }

        EcoreUtil.resolveAll(targetModel);
        fixCrossResourceReferences();

        // Apply conflict resolutions directly via eSet, bypassing BatchMerger entirely.
        // This is the authoritative step for applying REMOTE-resolved conflicts.
        if (resolutions != null) {
            applyConflictResolutionsDirect(comparison, resolutions);
        }

        // Safety net: rebind relationships and connections from REMOTE model.
        if (resolutions != null && theirModel != null) {
            forceRebindSelectedFromRemote(theirModel, resolutions);
        }

        fixCrossResourceReferences();
        ensureRelationshipsAreInFolders();
        reconnectConnections(comparison);
        logTrackedConnections("AFTER-reconnect");
        performFinalHealing();
        logTrackedConnections("AFTER-finalHealing");
        syncVisualConnections(comparison);
        logTrackedConnections("AFTER-syncVisual");
        // BatchMerger can create a second IDiagramModelArchimateConnection for the same
        // logical relationship; syncVisualConnections then aligns both to identical
        // endpoints → double lines on the diagram.
        deduplicateRedundantArchimateConnectionsInEachDiagram();
        logTrackedConnections("AFTER-dedupe");
        // EMF merge can drop diagram lines while keeping the logical relationship + nodes.
        // Recreate any ArchiMate connection that exists on the same view in REMOTE but is missing locally.
        if (theirModel != null) {
            ensureVisualConnectionsFromTheirDiagrams(theirModel);
        }
        logTrackedConnections("AFTER-ensureVisual");
        // Drop refs to objects not in the model tree (e.g. deleted by merge but still referenced).
        // Without this, save produces XMI that fails on reload: "Unresolved reference 'id-…'".
        removeReferencesToObjectsOutsideContainmentTree();
        fixCrossResourceReferences();
        logAllDiagramConnections("AFTER-HEAL");
    }

    /**
     * Same as {@link #heal(Comparison, IArchimateModel, Map)} with no manual conflict resolutions (automatic merge path).
     */
    public void heal(Comparison comparison, IArchimateModel theirModel) {
        heal(comparison, theirModel, null);
    }

    /**
     * Logs the presence/absence of any IDiagramModelConnection whose eContainer is non-null
     * (i.e. still alive in the model) for the two diagrams affected by the PROEXP-1468 case.
     * Add more diagram/connection IDs here as needed when investigating new regressions.
     */
    private void logTrackedConnections(String phase) {
        // Track: relId=id-39300a52 on PROEXP-1468 main and "Feature" branch diagrams
        String[] trackedRelIds = { "id-39300a52dd9d4794a582635c94922bd3" };
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModelArchimateConnection conn) {
                IArchimateRelationship rel = conn.getArchimateRelationship();
                if (rel == null) return;
                for (String rid : trackedRelIds) {
                    if (rid.equals(rel.getId())) {
                        EObject container = conn.eContainer();
                        String diagName = "?";
                        for (EObject cur = container; cur != null; cur = cur.eContainer()) {
                            if (cur instanceof IDiagramModel dm) { diagName = dm.getName(); break; }
                        }
                        System.out.println("[TRACK " + phase + "] conn=" + conn.getId()
                            + " rel=" + rel.getId()
                            + " diagram=" + diagName
                            + " alive=" + (container != null));
                    }
                }
            }
        });
    }

    /**
     * Debug: dumps every connection in every diagram plus per-node {@code sourceConnections} ids.
     *
     * @param phase label prefix for log lines
     */
    private void logAllDiagramConnections(String phase) {
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm) {
                List<IDiagramModelConnection> conns = new ArrayList<>();
                dm.eAllContents().forEachRemaining(c -> {
                    if (c instanceof IDiagramModelConnection dmc) conns.add(dmc);
                });
                for (IDiagramModelConnection c : conns) {
                    String srcId = MergeUtils.getObjectId(c.getSource());
                    String tgtId = MergeUtils.getObjectId(c.getTarget());
                    System.out.println("[" + phase + "] diagram=" + dm.getName()
                        + " conn=" + c.getId()
                        + " src=" + srcId + " tgt=" + tgtId);
                }
                // Also log sourceConnections lists to detect duplicates
                dm.eAllContents().forEachRemaining(n -> {
                    if (n instanceof IDiagramModelObject dmo) {
                        if (!dmo.getSourceConnections().isEmpty()) {
                            String ids = dmo.getSourceConnections().stream()
                                .map(c -> c.getId())
                                .reduce((a, b) -> a + ", " + b).orElse("");
                            System.out.println("[" + phase + "] diagram=" + dm.getName()
                                + " node=" + MergeUtils.getObjectId(dmo)
                                + " sourceConnections=[" + ids + "]");
                        }
                    }
                });
            }
        });
    }

    /**
     * Rewrites proxy or foreign-resource references to in-model objects by id; clears dangling refs.
     */
    public void fixCrossResourceReferences() {
        targetModel.eAllContents().forEachRemaining(eObject -> {
            for (org.eclipse.emf.ecore.EReference ref : eObject.eClass().getEAllReferences()) {
                if (!ref.isChangeable() || ref.isDerived() || ref.isContainer() || ref.isContainment())
                    continue;
                Object value = eObject.eGet(ref);
                if (value instanceof EObject ro)
                    checkAndFixReference(eObject, ref, ro);
                else if (value instanceof List<?> list) {
                    new ArrayList<>(list).forEach(item -> {
                        if (item instanceof EObject ro)
                            checkAndFixReference(eObject, ref, ro);
                    });
                }
            }
        });
    }

    /**
     * If {@code ro} is a proxy or from another resource, replace with {@link MergeUtils#findObjectById} result or remove.
     */
    private void checkAndFixReference(EObject owner, org.eclipse.emf.ecore.EReference ref, EObject ro) {
        boolean proxyOrForeign = ro.eIsProxy()
                || (ro.eResource() != null && ro.eResource() != targetModel.eResource());
        if (proxyOrForeign) {
            String id = MergeUtils.getObjectId(ro);
            EObject local = id.isEmpty() ? null : MergeUtils.findObjectById(targetModel, id);
            if (local != null) {
                if (ref.isMany()) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) owner.eGet(ref);
                    int i = list.indexOf(ro);
                    if (i != -1)
                        list.set(i, local);
                } else {
                    owner.eSet(ref, local);
                }
            } else {
                // Proxy / foreign pointer to an id that does not exist locally → must not be serialized
                clearReferenceSlot(owner, ref, ro);
            }
            return;
        }
    }

    /** Removes {@code ro} from a many-ref list or nulls a single ref. */
    private void clearReferenceSlot(EObject owner, EReference ref, EObject ro) {
        if (ref.isMany()) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) owner.eGet(ref);
            list.remove(ro);
        } else {
            owner.eSet(ref, null);
        }
    }

    /**
     * Any reference to an EObject that is not under {@link IArchimateModel} containment will break
     * XMI load ("Unresolved reference") after save. Removes such refs (and non-proxy stragglers).
     */
    public void removeReferencesToObjectsOutsideContainmentTree() {
        Set<EObject> reachable = new HashSet<>();
        reachable.add(targetModel);
        targetModel.eAllContents().forEachRemaining(reachable::add);

        // Build an id-to-EObject index for stale-ref replacement (resolves identity mismatches
        // that occur when BatchMerger creates a new instance for the same logical object).
        Map<String, EObject> reachableById = new HashMap<>();
        for (EObject e : reachable) {
            String eid = MergeUtils.getObjectId(e);
            if (eid != null)
                reachableById.putIfAbsent(eid, e);
        }

        List<EObject> owners = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(owners::add);

        for (EObject owner : owners) {
            for (EReference ref : owner.eClass().getEAllReferences()) {
                if (!ref.isChangeable() || ref.isDerived() || ref.isContainer() || ref.isContainment())
                    continue;
                Object val = owner.eGet(ref, false);
                if (val instanceof EObject eo) {
                    if (!reachable.contains(eo)) {
                        // Try to replace with the reachable instance that has the same id.
                        String sid = MergeUtils.getObjectId(eo);
                        EObject replacement = sid != null ? reachableById.get(sid) : null;
                        if (replacement != null) {
                            System.out.println("[HEAL outsideContainment] Replacing stale singleton ref "
                                    + ref.getName() + " on " + MergeUtils.getObjectId(owner)
                                    + " id=" + sid);
                            owner.eSet(ref, replacement);
                        } else {
                            System.out.println("[HEAL outsideContainment] Removing unresolvable singleton ref "
                                    + ref.getName() + " on " + MergeUtils.getObjectId(owner)
                                    + " id=" + sid);
                            owner.eSet(ref, null);
                        }
                    }
                } else if (val instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> elist = (List<Object>) owner.eGet(ref);
                    for (int i = elist.size() - 1; i >= 0; i--) {
                        Object item = elist.get(i);
                        if (item instanceof EObject e && !reachable.contains(e)) {
                            String sid = MergeUtils.getObjectId(e);
                            EObject replacement = sid != null ? reachableById.get(sid) : null;
                            if (replacement != null && !elist.contains(replacement)) {
                                System.out.println("[HEAL outsideContainment] Replacing stale list ref "
                                        + ref.getName() + "[" + i + "] on " + MergeUtils.getObjectId(owner)
                                        + " id=" + sid);
                                elist.set(i, replacement);
                            } else {
                                System.out.println("[HEAL outsideContainment] Removing stale list ref "
                                        + ref.getName() + "[" + i + "] on " + MergeUtils.getObjectId(owner)
                                        + " id=" + sid + " (replacement=" + (replacement != null ? "dup" : "not found") + ")");
                                elist.remove(i);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Moves relationships with no container into the default Relations folder. */
    public void ensureRelationshipsAreInFolders() {
        List<IArchimateRelationship> orphans = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IArchimateRelationship rel && rel.eContainer() == null)
                orphans.add(rel);
        });
        IFolder folder = targetModel.getFolder(FolderType.RELATIONS);
        if (folder != null)
            orphans.forEach(rel -> folder.getElements().add(rel));
    }

    /**
     * Rebuilds {@code sourceConnections}/{@code targetConnections} on every diagram and strips stale entries (id-based).
     *
     * @param comparison unused today but kept for API symmetry with other heal steps
     */
    public void reconnectConnections(Comparison comparison) {
        // Fix the bidirectional sourceConnections / targetConnections lists across ALL
        // diagrams. Running only on "affected" diagrams is insufficient because a
        // relationship endpoint change (e.g. target moved from A1 to A3) modifies the
        // logical relationship but leaves stale list entries in diagrams that were never
        // touched by the comparison. This causes duplicate visual connections.
        List<IDiagramModel> allDiagrams = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm) allDiagrams.add(dm);
        });

        for (IDiagramModel diagram : allDiagrams) {
            List<IDiagramModelObject> nodes = new ArrayList<>();
            List<IDiagramModelConnection> connections = new ArrayList<>();
            
            diagram.eAllContents().forEachRemaining(obj -> {
                if (obj instanceof IDiagramModelObject dmo) nodes.add(dmo);
                if (obj instanceof IDiagramModelConnection dmc) connections.add(dmc);
            });

            // First: rebuild the forward registry (conn → its declared src/tgt nodes)
            for (IDiagramModelConnection conn : connections) {
                IDiagramModelObject src = conn.getSource() instanceof IDiagramModelObject s ? s : null;
                IDiagramModelObject tgt = conn.getTarget() instanceof IDiagramModelObject t ? t : null;

                if (src != null && !src.getSourceConnections().contains(conn)) {
                    src.getSourceConnections().add(conn);
                }
                if (tgt != null && !tgt.getTargetConnections().contains(conn)) {
                    tgt.getTargetConnections().add(conn);
                }
            }
            
            // Second: remove stale entries where the node no longer owns the connection.
            // This cleans up duplicates that arise when a connection was reconnected to a
            // different node but the old node's list was not cleared.
            for (IDiagramModelObject dmo : nodes) {
                String dmoId = MergeUtils.getObjectId(dmo);
                int beforeSrc = dmo.getSourceConnections().size();
                int beforeTgt = dmo.getTargetConnections().size();
                dmo.getSourceConnections().removeIf(c -> {
                    // Compare by ID because after EMF merge conn.getSource() may be a different
                    // in-memory instance (proxy or copy) even though it represents the same element.
                    String connSrcId = MergeUtils.getObjectId(c.getSource());
                    boolean stale = connSrcId == null || !connSrcId.equals(dmoId);
                    if (stale) System.out.println("[HEAL reconnect] Removing stale sourceConnection "
                        + c.getId() + " from node " + dmoId
                        + " in diagram " + diagram.getName()
                        + " (conn.source=" + connSrcId + ")");
                    return stale;
                });
                dmo.getTargetConnections().removeIf(c -> {
                    String connTgtId = MergeUtils.getObjectId(c.getTarget());
                    boolean stale = connTgtId == null || !connTgtId.equals(dmoId);
                    if (stale) System.out.println("[HEAL reconnect] Removing stale targetConnection "
                        + c.getId() + " from node " + dmoId
                        + " in diagram " + diagram.getName()
                        + " (conn.target=" + connTgtId + ")");
                    return stale;
                });
                int afterSrc = dmo.getSourceConnections().size();
                int afterTgt = dmo.getTargetConnections().size();
                if (beforeSrc != afterSrc || beforeTgt != afterTgt) {
                    System.out.println("[HEAL reconnect] Node " + MergeUtils.getObjectId(dmo)
                        + " in " + diagram.getName()
                        + " src: " + beforeSrc + "->" + afterSrc
                        + " tgt: " + beforeTgt + "->" + afterTgt);
                }
            }
        }
    }

    /**
     * Deletes hopeless relationships (missing ends), empty zombie diagram objects, and fixes diagram↔concept back-pointers.
     */
    public void performFinalHealing() {
        List<EObject> allElements = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(allElements::add);

        List<EObject> toDelete = new ArrayList<>();
        for (EObject obj : allElements) {
            // BE CONSERVATIVE: Only delete if it's truly broken and cannot be fixed.
            // For Relationships, if source or target is null, it's really broken.
            if (obj instanceof IArchimateRelationship rel && (rel.getSource() == null || rel.getTarget() == null)) {
                toDelete.add(rel);
            }
            
            // NEW: Heal "Zombie" Diagram Elements that have lost their ArchiMate element reference
            if (obj instanceof IDiagramModelArchimateObject dmo && dmo.getArchimateElement() == null) {
                // If it has no connections and no referenced element, it's a "zombie" - delete it.
                if (dmo.getSourceConnections().isEmpty() && dmo.getTargetConnections().isEmpty()) {
                    toDelete.add(dmo);
                }
            }
            
            // For Connections, we try to fix them in syncVisualConnections instead of deleting here.
            // if (obj instanceof IDiagramModelConnection conn && (conn.getSource() == null || conn.getTarget() == null)) {
            //    toDelete.add(conn);
            // }
            
            if (obj instanceof IDiagramModelArchimateComponent dmc) {
                IArchimateConcept concept = dmc.getArchimateConcept();
                if (concept != null && !concept.getReferencingDiagramComponents().contains(dmc)) {
                    dmc.setArchimateConcept(null);
                    dmc.setArchimateConcept(concept);
                }
            }
        }
        
        for (EObject obj : allElements) {
            if (obj instanceof IArchimateConcept concept) {
                List<?> referencing = new ArrayList<>(concept.getReferencingDiagramComponents());
                for (Object dmcObj : referencing) {
                    if (dmcObj instanceof IDiagramModelArchimateComponent dmc) {
                        if (dmc.eContainer() == null) {
                            dmc.setArchimateConcept(null);
                        }
                    }
                }
            }
        }

        toDelete.forEach(obj -> {
            if (obj.eContainer() != null)
                EcoreUtil.delete(obj);
        });
    }

    /**
     * For every {@link IDiagramModelArchimateConnection}, ensures visual ends match the logical {@link IArchimateRelationship}
     * (reconnect or delete when impossible; skips connection-on-connection edge cases).
     *
     * @param comparison retained for future filtering; currently all diagrams are scanned
     */
    public void syncVisualConnections(Comparison comparison) {
        // Scan ALL diagrams in the model, not only those that appear in the comparison.
        //
        // Rationale: a conflict resolution changes the logical source/target of a
        // relationship, which can invalidate visual connections in ANY view – including
        // views that exist only on one side and were never part of the comparison diff set.
        //
        // For each connection that is out-of-sync with its logical relationship:
        //   • If the correct node views exist in the diagram → reconnect.
        //   • If a required node is missing from the diagram → delete the connection;
        //     it is impossible to display it correctly.
        List<IDiagramModel> allDiagrams = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm) {
                allDiagrams.add(dm);
            }
        });

        for (IDiagramModel diagram : allDiagrams) {
            // Snapshot the connections before iterating (some may be deleted mid-loop).
            List<IDiagramModelArchimateConnection> connectionsToCheck = new ArrayList<>();
            diagram.eAllContents().forEachRemaining(obj -> {
                if (obj instanceof IDiagramModelArchimateConnection conn) {
                    connectionsToCheck.add(conn);
                }
            });

            for (IDiagramModelArchimateConnection conn : connectionsToCheck) {
                // Skip if already removed by a previous iteration (nested connections).
                if (conn.eContainer() == null) {
                    continue;
                }

                IArchimateRelationship rel = conn.getArchimateRelationship();
                if (rel == null || rel.getSource() == null || rel.getTarget() == null) {
                    continue;
                }

                // Resolve the ArchiMate concept that each visual endpoint represents.
                // Two legitimate cases:
                //   (a) Normal:               endpoint is IDiagramModelArchimateObject  → archimateElement
                //   (b) Connection-on-connection: endpoint is IDiagramModelArchimateConnection → archimateRelationship
                // Both IArchimateRelationship and the element types implement IArchimateConcept,
                // so the result is directly comparable to rel.getSource()/getTarget().
                // Match logical ends to visual ends. Nested diagram shapes (child box inside parent)
                // may represent a component while the Flow is logically attached to the parent
                // ApplicationComponent — strict == would mis-detect and break the line.
                if (visualConnectionEndpointsMatchRelationship(diagram, conn, rel)) {
                    continue;
                }

                // Try to find the correct node views (works for element endpoints only).
                IDiagramModelArchimateObject newSrcView = MergeUtils.findViewForConcept(targetModel, diagram,
                        rel.getSource());
                IDiagramModelArchimateObject newTgtView = MergeUtils.findViewForConcept(targetModel, diagram,
                        rel.getTarget());

                if (newSrcView != null && newTgtView != null) {
                    System.out.println("[HEAL syncVisual] Reconnecting conn=" + conn.getId()
                        + " in diagram=" + diagram.getName()
                        + " | oldSrc=" + MergeUtils.getObjectId(conn.getSource())
                        + " oldTgt=" + MergeUtils.getObjectId(conn.getTarget())
                        + " -> newSrc=" + MergeUtils.getObjectId(newSrcView)
                        + " newTgt=" + MergeUtils.getObjectId(newTgtView));
                    // Disconnect from old endpoints first to avoid duplication in
                    // sourceConnections / targetConnections lists.
                    if (conn.getSource() instanceof IDiagramModelObject oldSrc) {
                        oldSrc.getSourceConnections().remove(conn);
                    }
                    if (conn.getTarget() instanceof IDiagramModelObject oldTgt) {
                        oldTgt.getTargetConnections().remove(conn);
                    }
                    conn.connect(newSrcView, newTgtView);
                } else {
                    // The required node view is absent. Two sub-cases:
                    //
                    // (b) connection-on-connection: rel.getTarget() is a relationship that must be
                    //     visualised as a connection, not a box. findViewFor() can never return a
                    //     node for it, so we must NOT delete the connection. Leave it as-is.
                    //
                    // (a) genuine missing node: delete the connection.
                    boolean isConnectionOnConnection =
                            conn.getSource() instanceof IDiagramModelConnection
                            || conn.getTarget() instanceof IDiagramModelConnection;

                    if (!isConnectionOnConnection) {
                        System.out.println("[HEAL syncVisual] DELETING conn=" + conn.getId()
                            + " rel=" + rel.getId()
                            + " in diagram=" + diagram.getName()
                            + " | srcView=" + (newSrcView != null ? newSrcView.getId() : "null")
                            + " tgtView=" + (newTgtView != null ? newTgtView.getId() : "null")
                            + " | conn.src=" + MergeUtils.getObjectId(conn.getSource())
                            + " conn.tgt=" + MergeUtils.getObjectId(conn.getTarget()));
                        EcoreUtil.delete(conn);
                    } else {
                        System.out.println("[HEAL syncVisual] SKIPPING conn-on-conn=" + conn.getId()
                            + " in diagram=" + diagram.getName());
                    }
                }
            }
        }
    }

    /**
     * EMF Compare's {@code copyAllLeftToRight} may leave two diagram connections that
     * reference the same {@link IArchimateRelationship} (one from LOCAL, one merged
     * from REMOTE). After {@link #syncVisualConnections} they often share the same
     * source/target views — the canvas shows one relationship twice. Collapse to a
     * single visual per (relationship, source endpoint, target endpoint) tuple.
     */
    private void deduplicateRedundantArchimateConnectionsInEachDiagram() {
        List<IDiagramModel> allDiagrams = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm)
                allDiagrams.add(dm);
        });

        for (IDiagramModel diagram : allDiagrams) {
            List<IDiagramModelArchimateConnection> snapshot = new ArrayList<>();
            diagram.eAllContents().forEachRemaining(obj -> {
                if (obj instanceof IDiagramModelArchimateConnection c)
                    snapshot.add(c);
            });

            // Group by (relId, sourceId, targetId, labelExpression, lineColor, bendpoints).
            // Two visual connections for the same logical relationship are intentional when
            // they differ in label, color, OR routing (bendpoints). Only byte-identical
            // copies introduced by BatchMerger are removed.
            Map<String, List<IDiagramModelArchimateConnection>> groups = new LinkedHashMap<>();
            for (IDiagramModelArchimateConnection conn : snapshot) {
                if (conn.eContainer() == null)
                    continue;
                IArchimateRelationship rel = conn.getArchimateRelationship();
                if (rel == null)
                    continue;
                var labelFeature = conn.getFeatures().getFeature("labelExpression");
                String label = (labelFeature != null && labelFeature.getValue() != null)
                        ? labelFeature.getValue() : "";
                String lineColor = conn.getLineColor() != null ? conn.getLineColor() : "";
                // Include bendpoints so that two copies of the same relation placed with
                // different routing are treated as distinct, not as duplicates.
                StringBuilder bpKey = new StringBuilder();
                for (var bp : conn.getBendpoints()) {
                    bpKey.append(bp.getStartX()).append(',')
                         .append(bp.getStartY()).append(',')
                         .append(bp.getEndX()).append(',')
                         .append(bp.getEndY()).append(';');
                }
                String key = rel.getId() + "|" + MergeUtils.getObjectId(conn.getSource()) + "|"
                        + MergeUtils.getObjectId(conn.getTarget())
                        + "|" + label + "|" + lineColor + "|" + bpKey;
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(conn);
            }

            for (List<IDiagramModelArchimateConnection> group : groups.values()) {
                if (group.size() <= 1)
                    continue;
                IDiagramModelArchimateConnection keeper = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    IDiagramModelArchimateConnection dup = group.get(i);
                    if (dup.eContainer() == null || dup == keeper)
                        continue;
                    System.out.println("[HEAL dedupe] diagram=" + diagram.getName() + " remove duplicate conn="
                            + dup.getId() + " keep=" + keeper.getId() + " rel="
                            + keeper.getArchimateRelationship().getId());
                    disconnectArchimateConnectionFromEndpoints(dup);
                    EcoreUtil.delete(dup);
                }
            }
        }
    }

    /**
     * For each diagram, compare with the same diagram (by id) in {@code theirModel}. If REMOTE has an
     * element-to-element {@link IDiagramModelArchimateConnection} for a relationship that also exists in
     * the merged model, but our diagram has no line for that relationship, create the missing visual.
     * This covers cases where the batch merge updated the canvas but dropped individual connections.
     */
    public void ensureVisualConnectionsFromTheirDiagrams(IArchimateModel theirModel) {
        if (theirModel == null)
            return;

        EcoreUtil.resolveAll(targetModel);

        Map<String, IDiagramModel> theirDiagramById = new HashMap<>();
        Map<String, List<IDiagramModel>> theirDiagramsByName = new LinkedHashMap<>();
        theirModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm) {
                String did = dm.getId();
                if (did != null)
                    theirDiagramById.putIfAbsent(did, dm);
                String n = dm.getName();
                if (n != null) {
                    String key = n.trim();
                    if (!key.isEmpty())
                        theirDiagramsByName.computeIfAbsent(key, k -> new ArrayList<>()).add(dm);
                }
            }
        });

        List<IDiagramModel> ourDiagrams = new ArrayList<>();
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModel dm)
                ourDiagrams.add(dm);
        });

        for (IDiagramModel ourDm : ourDiagrams) {
            IDiagramModel theirDm = null;
            String diagramId = ourDm.getId();
            if (diagramId != null)
                theirDm = theirDiagramById.get(diagramId);
            if (theirDm == null && ourDm.getName() != null) {
                String nk = ourDm.getName().trim();
                if (!nk.isEmpty()) {
                    List<IDiagramModel> cands = theirDiagramsByName.get(nk);
                    if (cands != null && cands.size() == 1)
                        theirDm = cands.get(0);
                }
            }
            if (theirDm == null)
                continue;

            List<IDiagramModelArchimateConnection> theirConns = new ArrayList<>();
            theirDm.eAllContents().forEachRemaining(o -> {
                if (o instanceof IDiagramModelArchimateConnection c)
                    theirConns.add(c);
            });

            for (IDiagramModelArchimateConnection theirConn : theirConns) {
                if (!isSimpleElementToElementArchimateConnection(theirConn))
                    continue;
                IArchimateRelationship theirRel = theirConn.getArchimateRelationship();
                if (theirRel == null)
                    continue;

                String relId = theirRel.getId();
                String relName = theirRel.getName();

                IArchimateRelationship ourRel = (IArchimateRelationship) MergeUtils.findObjectById(targetModel,
                        relId);
                if (ourRel == null) {
                    System.out.println("[HEAL ensureVisual] SKIP rel=" + relId + " name='" + relName
                            + "': not found in merged model");
                    continue;
                }
                if (ourRel.getSource() == null || ourRel.getTarget() == null) {
                    System.out.println("[HEAL ensureVisual] SKIP rel=" + relId + " name='" + relName
                            + "': source/target null in merged model");
                    continue;
                }
                if (diagramHasArchimateConnectionForRelationship(ourDm, ourRel)) {
                    continue;
                }

                IDiagramModelArchimateObject ourSrc = MergeUtils.findViewForConcept(targetModel, ourDm,
                        ourRel.getSource());
                IDiagramModelArchimateObject ourTgt = MergeUtils.findViewForConcept(targetModel, ourDm,
                        ourRel.getTarget());
                if (ourSrc == null || ourTgt == null) {
                    System.out.println("[HEAL ensureVisual] SKIP rel=" + relId + " name='" + relName
                            + "' in diagram='" + ourDm.getName()
                            + "': ourSrc=" + (ourSrc != null ? ourSrc.getId() : "null")
                            + " ourTgt=" + (ourTgt != null ? ourTgt.getId() : "null")
                            + " | relSrc=" + MergeUtils.getObjectId(ourRel.getSource())
                            + " relTgt=" + MergeUtils.getObjectId(ourRel.getTarget()));
                    continue;
                }

                createArchimateVisualFromTheir(theirConn, ourRel, ourSrc, ourTgt, ourDm.getName());
            }
        }
    }

    /** @return {@code true} if both ends are {@link IDiagramModelArchimateObject} (not nested connection routing). */
    private static boolean isSimpleElementToElementArchimateConnection(IDiagramModelArchimateConnection c) {
        return c.getSource() instanceof IDiagramModelArchimateObject
                && c.getTarget() instanceof IDiagramModelArchimateObject;
    }

    /** @return whether {@code diagram} already contains a line for {@code rel} by relationship id */
    private static boolean diagramHasArchimateConnectionForRelationship(IDiagramModel diagram,
            IArchimateRelationship rel) {
        String relId = rel.getId();
        if (relId == null)
            return false;
        for (Iterator<EObject> it = diagram.eAllContents(); it.hasNext();) {
            EObject o = it.next();
            if (o instanceof IDiagramModelArchimateConnection conn) {
                IArchimateRelationship r = conn.getArchimateRelationship();
                if (r != null && relId.equals(r.getId()))
                    return true;
            }
        }
        return false;
    }

    /**
     * Clones a REMOTE diagram connection onto LOCAL {@code ourSrc}/{@code ourTgt} for {@code ourRel}.
     *
     * @param diagramName used only in logs
     */
    private void createArchimateVisualFromTheir(IDiagramModelArchimateConnection theirConn,
            IArchimateRelationship ourRel, IDiagramModelArchimateObject ourSrc, IDiagramModelArchimateObject ourTgt,
            String diagramName) {
        IDiagramModelArchimateConnection nc = IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
        String tid = theirConn.getId();
        if (tid != null && MergeUtils.findObjectById(targetModel, tid) == null)
            nc.setId(tid);
        nc.setType(theirConn.getType());
        nc.setArchimateRelationship(ourRel);
        nc.connect(ourSrc, ourTgt);
        System.out.println("[HEAL ensureVisualFromTheir] diagram=\"" + diagramName + "\" created connection for rel="
                + ourRel.getId() + " connId=" + nc.getId());
    }

    /** Removes {@code conn} from its endpoints’ connection lists before delete. */
    private static void disconnectArchimateConnectionFromEndpoints(IDiagramModelArchimateConnection conn) {
        Object src = conn.getSource();
        Object tgt = conn.getTarget();
        if (src instanceof IConnectable cs)
            cs.getSourceConnections().remove(conn);
        if (tgt instanceof IConnectable ct)
            ct.getTargetConnections().remove(conn);
    }

    /**
     * Copies missing {@link IArchimateRelationship} data from {@code theirModel} onto LOCAL diagram connections that lost it.
     *
     * @param theirModel REMOTE model loaded for the same merge
     */
    public void forceHealMissingRelations(IArchimateModel theirModel) {
        targetModel.eAllContents().forEachRemaining(obj -> {
            if (obj instanceof IDiagramModelArchimateConnection ourConn && ourConn.getArchimateRelationship() == null) {
                EObject donor = MergeUtils.findObjectById(theirModel, ourConn.getId());
                if (donor instanceof IDiagramModelArchimateConnection theirConn) {
                    IArchimateRelationship theirRel = theirConn.getArchimateRelationship();
                    if (theirRel != null) {
                        IArchimateRelationship ourRel = (IArchimateRelationship) MergeUtils.findObjectById(targetModel,
                                theirRel.getId());
                        if (ourRel == null) {
                            ourRel = (IArchimateRelationship) EcoreUtil.copy(theirRel);
                            IFolder relationsFolder = targetModel.getFolder(FolderType.RELATIONS);
                            if (relationsFolder != null)
                                relationsFolder.getElements().add(ourRel);
                        }
                        ourConn.setArchimateRelationship(ourRel);
                        IArchimateConcept src = (IArchimateConcept) MergeUtils.findObjectById(targetModel,
                                MergeUtils.getObjectId(theirRel.getSource()));
                        IArchimateConcept tgt = (IArchimateConcept) MergeUtils.findObjectById(targetModel,
                                MergeUtils.getObjectId(theirRel.getTarget()));
                        if (src != null)
                            ourRel.setSource(src);
                        if (tgt != null)
                            ourRel.setTarget(tgt);
                    }
                }
            }
        });
    }

    /**
     * Directly applies conflict resolutions using EMF eSet, completely bypassing
     * BatchMerger. For each REAL conflict resolved as REMOTE (any diff in the conflict
     * has resolutions value = true), the LEFT (REMOTE) diff's value is written into
     * the LOCAL object via eSet.
     *
     * This handles all diff types:
     * - ReferenceChange (source, target, any reference)
     * - AttributeChange (name, etc.)
     * Including connection-on-connection scenarios where the target is an
     * IDiagramModelConnection, not an IDiagramModelObject.
     */
    public void applyConflictResolutionsDirect(Comparison comparison, Map<Diff, Boolean> resolutions) {
        for (Conflict conflict : comparison.getConflicts()) {
            // Find the LEFT (REMOTE) diff for this conflict.
            Diff leftDiff = null;
            for (Diff d : conflict.getDifferences()) {
                if (d.getSource() == DifferenceSource.LEFT) {
                    leftDiff = d;
                    break;
                }
            }
            if (leftDiff == null) continue;

            // Determine if this conflict should be resolved as REMOTE.
            // The dialog sets resolutions=true for ALL diffs (LEFT and RIGHT) in a
            // REMOTE-resolved conflict, so we check any diff in the conflict.
            boolean resolveAsRemote = false;
            for (Diff d : conflict.getDifferences()) {
                if (Boolean.TRUE.equals(resolutions.get(d))) {
                    resolveAsRemote = true;
                    break;
                }
            }
            if (!resolveAsRemote) continue;

            EObject ourObj = leftDiff.getMatch().getRight();
            if (ourObj == null) continue;

            if (leftDiff instanceof ReferenceChange rc) {
                EReference ref = rc.getReference();
                if (!ref.isChangeable() || ref.isDerived() || ref.isContainment())
                    continue;

                EObject leftValue = rc.getValue();
                EObject localValue = findLocalEquivalent(leftValue, comparison);
                if (localValue == null) continue;

                if (ref.isMany()) {
                    // For list references, apply based on diff kind.
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> list = (java.util.List<Object>) ourObj.eGet(ref);
                    switch (rc.getKind()) {
                        case ADD -> { if (!list.contains(localValue)) list.add(localValue); }
                        case DELETE -> list.remove(localValue);
                        default -> ourObj.eSet(ref, localValue); // CHANGE or MOVE
                    }
                } else {
                    ourObj.eSet(ref, localValue);
                }

            } else if (leftDiff instanceof AttributeChange ac) {
                var feat = ac.getAttribute();
                if (!feat.isChangeable() || feat.isDerived()) continue;
                EObject leftObj = leftDiff.getMatch().getLeft();
                if (leftObj != null) {
                    ourObj.eSet(feat, leftObj.eGet(feat));
                }
            }
        }
    }

    /**
     * Finds the LOCAL (RIGHT) equivalent of a LEFT (REMOTE) value via the comparison
     * match tree first, falling back to ID-based search in targetModel.
     */
    private EObject findLocalEquivalent(EObject leftValue, Comparison comparison) {
        if (leftValue == null) return null;
        Match match = comparison.getMatch(leftValue);
        if (match != null && match.getRight() != null)
            return match.getRight();
        String id = MergeUtils.getObjectId(leftValue);
        return MergeUtils.findObjectById(targetModel, id);
    }

    /**
     * For every diff the user resolved as REMOTE, rebinds matching relationships/connections using ids from {@code theirModel}.
     */
    public void forceRebindSelectedFromRemote(IArchimateModel theirModel, Map<Diff, Boolean> resolutions) {
        Set<EObject> processed = new HashSet<>();
        for (Map.Entry<Diff, Boolean> entry : resolutions.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                EObject ourObj = MergeUtils.getAnyObjectFromMatch(entry.getKey().getMatch(), DifferenceSource.RIGHT);
                if (ourObj != null && !processed.contains(ourObj)) {
                    if (ourObj instanceof IArchimateRelationship ourRel)
                        rebindRelationship(ourRel, theirModel);
                    else if (ourObj instanceof IDiagramModelConnection ourConn)
                        rebindConnection(ourConn, theirModel);
                    processed.add(ourObj);
                }
            }
        }
    }

    /** Sets {@code ourRel} source/target from the same-id relationship in {@code theirModel} resolved into {@link #targetModel}. */
    private void rebindRelationship(IArchimateRelationship ourRel, IArchimateModel theirModel) {
        EObject theirObj = MergeUtils.findObjectById(theirModel, ourRel.getId());
        if (theirObj instanceof IArchimateRelationship theirRel) {
            EObject localTarget = MergeUtils.findObjectById(targetModel, MergeUtils.getObjectId(theirRel.getTarget()));
            EObject localSource = MergeUtils.findObjectById(targetModel, MergeUtils.getObjectId(theirRel.getSource()));
            if (localTarget instanceof IArchimateConcept c)
                ourRel.setTarget(c);
            if (localSource instanceof IArchimateConcept c)
                ourRel.setSource(c);
        }
    }

    /**
     * True if diagram connection ends match logical relationship ends, including the case where a line
     * starts on a <em>nested</em> diagram object while the Flow is logically bound to a parent element
     * (see {@link MergeUtils#diagramArchimateObjectVisualMatchesRelationshipEndpoint}).
     */
    private boolean visualConnectionEndpointsMatchRelationship(IDiagramModel diagram,
            IDiagramModelArchimateConnection conn, IArchimateRelationship rel) {
        return endpointVisualMatchesRelEnd(conn.getSource(), diagram, rel.getSource())
                && endpointVisualMatchesRelEnd(conn.getTarget(), diagram, rel.getTarget());
    }

    /**
     * @param endpoint diagram object or nested connection
     * @param relEnd   logical relationship end
     */
    private boolean endpointVisualMatchesRelEnd(Object endpoint, IDiagramModel diagram, IArchimateConcept relEnd) {
        if (relEnd == null)
            return false;
        if (endpoint instanceof IDiagramModelArchimateObject v)
            return MergeUtils.diagramArchimateObjectVisualMatchesRelationshipEndpoint(diagram, v, relEnd,
                    targetModel);
        // Connection-on-connection: endpoint is a visual connection; compare by ID because
        // BatchMerger may create new instances for existing elements.
        IArchimateConcept ep = getEndpointConcept(endpoint);
        String relEndId = MergeUtils.getObjectId(relEnd);
        String epId = MergeUtils.getObjectId(ep);
        return relEndId != null && relEndId.equals(epId);
    }

    /**
     * Returns the ArchiMate concept that a visual connection endpoint represents.
     * <ul>
     *   <li>If the endpoint is an {@link IDiagramModelArchimateObject} (a box/node)
     *       → returns its {@code archimateElement}.</li>
     *   <li>If the endpoint is an {@link IDiagramModelArchimateConnection} (connection-on-connection)
     *       → returns its {@code archimateRelationship}.</li>
     *   <li>Otherwise → {@code null}.</li>
     * </ul>
     */
    private static IArchimateConcept getEndpointConcept(Object endpoint) {
        if (endpoint instanceof IDiagramModelArchimateObject dmo) {
            return dmo.getArchimateElement();
        }
        if (endpoint instanceof IDiagramModelArchimateConnection dmc) {
            return dmc.getArchimateRelationship();
        }
        return null;
    }

    /** Rewires {@code ourConn}’s source/target diagram objects using the same-id connection from {@code theirModel}. */
    private void rebindConnection(IDiagramModelConnection ourConn, IArchimateModel theirModel) {
        EObject theirObj = MergeUtils.findObjectById(theirModel, ourConn.getId());
        if (theirObj instanceof IDiagramModelConnection theirConn) {
            EObject localTarget = MergeUtils.findObjectById(targetModel, MergeUtils.getObjectId(theirConn.getTarget()));
            EObject localSource = MergeUtils.findObjectById(targetModel, MergeUtils.getObjectId(theirConn.getSource()));
            if (localTarget instanceof IDiagramModelObject dmo)
                ourConn.setTarget(dmo);
            if (localSource instanceof IDiagramModelObject dmo)
                ourConn.setSource(dmo);
        }
    }
}
