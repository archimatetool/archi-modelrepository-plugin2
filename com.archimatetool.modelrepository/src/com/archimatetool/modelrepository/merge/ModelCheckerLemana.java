package com.archimatetool.modelrepository.merge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

import com.archimatetool.editor.Logger;
import com.archimatetool.editor.model.Messages;
import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProfile;
import com.archimatetool.model.IProfiles;

/**
 * Check Model for integrity
 * 
 * @author Phillip Beauvoir
 */
public class ModelCheckerLemana {

	// If this is set in Program arguments then don't model check
	private static boolean NO_MODELCHECK = Arrays.asList(Platform.getApplicationArgs()).contains("-noModelCheck"); //$NON-NLS-1$

	protected IArchimateModel model;
	protected List<String> errorMessages;

	/**
	 * @param model model to validate (typically right after merge)
	 */
	public ModelCheckerLemana(IArchimateModel model) {
		this.model = model;
	}

	/**
	 * Traverses the whole model and accumulates integrity messages.
	 *
	 * @return {@code true} if no errors (or {@code -noModelCheck} is set), {@code false} otherwise
	 */
	public boolean checkAll() {
		errorMessages = new ArrayList<>();

		// Don't model check
		if (NO_MODELCHECK) {
			return true;
		}

		// Instance count map
		Map<IArchimateConcept, List<IDiagramModelArchimateComponent>> dmcMap = new HashMap<>();

		// Model ID
		errorMessages.addAll(checkHasIdentifier(model));

		// not that important
		// addErrorMessages(checkFolderStructure());

		// Iterate through all child objects in the model...
		for (Iterator<EObject> iter = model.eAllContents(); iter.hasNext();) {
			EObject eObject = iter.next();

			// Identifier
			if (eObject instanceof IIdentifier identifier) {
				errorMessages.addAll(checkHasIdentifier(identifier));
			}

			// Relation
			if (eObject instanceof IArchimateRelationship relationship) {
				errorMessages.addAll(checkRelationship(relationship));
			}

			// Diagram Model ArchiMate Object
			if (eObject instanceof IDiagramModelArchimateObject dmo) {
				errorMessages.addAll(checkDiagramModelArchimateObject(dmo));
				incrementInstanceCount(dmo, dmcMap);
			}

			// Diagram Model ArchiMate Connection
			if (eObject instanceof IDiagramModelArchimateConnection dmc) {
				errorMessages.addAll(checkDiagramModelArchimateConnection(dmc));
				incrementInstanceCount(dmc, dmcMap);
			}

			// Concept or Diagram is in correct Folder
			if (eObject instanceof IArchimateConcept || eObject instanceof IDiagramModel) {
				errorMessages.addAll(checkObjectInCorrectFolder((IArchimateModelObject) eObject));
			}

			// Folders contain correct objects
			if (eObject instanceof IFolder folder) {
				errorMessages.addAll(checkFolderContainsCorrectObjects(folder));
			}

			// Profiles
			if (eObject instanceof IProfiles profiles) {
				errorMessages.addAll(checkProfiles(profiles));
			}

			// Extension
			errorMessages.addAll(checkObject(eObject));
		}

		// Now check Diagram Model Object reference count
		errorMessages.addAll(checkDiagramComponentInstanceCount(dmcMap));

		return errorMessages.isEmpty();
	}

	/** @return messages collected by the last {@link #checkAll()} (may be {@code null} if not run) */
	public List<String> getErrorMessages() {
		return errorMessages;
	}

	/**
	 * Placeholder hook: sorts and logs errors when a summary exists.
	 *
	 * @param shell parent shell (unused in current implementation)
	 */
	public void showErrorDialog(Shell shell) {
		String summary = createMessageSummary();
		if (summary != null) {
			// Log these messages to log file
			logErrorMesssages();

		}
	}

	/**
	 * @return newline-joined sorted messages, or {@code null} if none
	 */
	public String createMessageSummary() {
		if (errorMessages == null || errorMessages.isEmpty()) {
			return null;
		}

		// Sort messages
		errorMessages.sort(null);

		StringBuilder sb = new StringBuilder();

		for (String message : errorMessages) {
			sb.append(message);
			sb.append('\n');
		}

		return sb.toString();
	}

	/** Writes {@link #errorMessages} to the Archi error log with model context. */
	public void logErrorMesssages() {
		if (errorMessages == null || errorMessages.isEmpty()) {
			return;
		}

		StringBuilder sb = new StringBuilder();

		sb.append("Model Error: "); //$NON-NLS-1$
		sb.append(model.getName());

		if (model.getFile() != null) {
			sb.append(" ["); //$NON-NLS-1$
			sb.append(model.getFile().getAbsolutePath());
			sb.append("]"); //$NON-NLS-1$
		}

		sb.append(':');
		sb.append('\n');

		for (String message : errorMessages) {
			sb.append("   "); //$NON-NLS-1$
			sb.append(message);
			sb.append('\n');
		}

		Logger.logError(sb.toString());
	}

	/**
	 * Verifies default top-level folders exist (optional / currently unused from {@link #checkAll()}).
	 *
	 * @return localized messages for each missing {@link FolderType}
	 */
	protected List<String> checkFolderStructure() {
		List<String> messages = new ArrayList<>();

		if (model.getFolder(FolderType.STRATEGY) == null) {
			messages.add(Messages.ModelChecker_23);
		}
		if (model.getFolder(FolderType.BUSINESS) == null) {
			messages.add(Messages.ModelChecker_2);
		}
		if (model.getFolder(FolderType.APPLICATION) == null) {
			messages.add(Messages.ModelChecker_3);
		}
		if (model.getFolder(FolderType.TECHNOLOGY) == null) {
			messages.add(Messages.ModelChecker_4);
		}
		if (model.getFolder(FolderType.OTHER) == null) {
			messages.add(Messages.ModelChecker_5);
		}
		if (model.getFolder(FolderType.IMPLEMENTATION_MIGRATION) == null) {
			messages.add(Messages.ModelChecker_6);
		}
		if (model.getFolder(FolderType.MOTIVATION) == null) {
			messages.add(Messages.ModelChecker_7);
		}
		if (model.getFolder(FolderType.RELATIONS) == null) {
			messages.add(Messages.ModelChecker_8);
		}
		if (model.getFolder(FolderType.DIAGRAMS) == null) {
			messages.add(Messages.ModelChecker_9);
		}

		return messages;
	}

	/**
	 * @param eObject any identifiable model object
	 * @return empty list if id is set, else a single bound message
	 */
	protected List<String> checkHasIdentifier(IIdentifier eObject) {
		return StringUtils.isSet(eObject.getId()) ? List.of()
				: List.of(NLS.bind(Messages.ModelChecker_10, ArchiLabelProvider.INSTANCE.getLabel(eObject)));
	}

	/**
	 * Ensures relationship ends exist and are attached to the model.
	 *
	 * @param relation logical ArchiMate relationship
	 */
	protected List<String> checkRelationship(IArchimateRelationship relation) {
		List<String> messages = new ArrayList<>();

		String name = "(" + relation.getId() + ")"; //$NON-NLS-1$ //$NON-NLS-2$

		// Source missing
		if (relation.getSource() == null) {
			messages.add(NLS.bind(Messages.ModelChecker_19, name));
		}
		// Source orphaned from model
		else if (relation.getSource().getArchimateModel() == null) {
			messages.add(NLS.bind(Messages.ModelChecker_20, name));
		}

		// Target missing
		if (relation.getTarget() == null) {
			messages.add(NLS.bind(Messages.ModelChecker_21, name));
		}
		// Target orphaned from model
		else if (relation.getTarget().getArchimateModel() == null) {
			messages.add(NLS.bind(Messages.ModelChecker_22, name));
		}

		return messages;
	}

	/**
	 * Validates diagram node ↔ logical element link and gathers rich diagnostics when the link is broken.
	 *
	 * @param dmo shape on a view
	 */
	protected List<String> checkDiagramModelArchimateObject(IDiagramModelArchimateObject dmo) {
		String diagramName = dmo.getDiagramModel() == null ? "Unknown View" : dmo.getDiagramModel().getName();
		String dmoInfo = String.format("Diagram Element '%s' in View '%s'", dmo.getId(), diagramName);

		IArchimateElement element = dmo.getArchimateElement();

		// No referenced element
		if (element == null) {
			StringBuilder sb = new StringBuilder();
			sb.append(dmoInfo).append(": Missing referenced ArchiMate element.\n");
			
			// Try to get more info from the DMO itself
			String dmoName = dmo.getName();
			if (StringUtils.isSet(dmoName)) {
				sb.append(String.format("  - Label on Diagram: '%s'\n", dmoName));
			}
			
			// Graphic Type
			sb.append(String.format("  - Graphic Type: %s\n", dmo.eClass().getName()));

			// Bounds (Location)
			com.archimatetool.model.IBounds b = dmo.getBounds();
			if (b != null) {
				sb.append(String.format("  - Location on View: x=%d, y=%d, width=%d, height=%d\n", 
						b.getX(), b.getY(), b.getWidth(), b.getHeight()));
			}

			// NEW: Appearance info to help find it visually
			sb.append(String.format("  - Fill Color: %s\n", dmo.getFillColor()));
			if (dmo instanceof com.archimatetool.model.ITextContent tc && StringUtils.isSet(tc.getContent())) {
				sb.append(String.format("  - Text Content: '%s'\n", tc.getContent()));
			}

			// Try to see if it's a proxy and what its URI was
			try {
				if (dmo instanceof org.eclipse.emf.ecore.InternalEObject internalEObject) {
					// Use eGet with resolve=false to get the proxy itself
					Object proxy = internalEObject.eGet(com.archimatetool.model.IArchimatePackage.Literals.DIAGRAM_MODEL_ARCHIMATE_OBJECT__ARCHIMATE_ELEMENT, false);
					if (proxy instanceof org.eclipse.emf.ecore.InternalEObject internalProxy && internalProxy.eIsProxy()) {
						sb.append(String.format("  - Intended Element URI: %s\n", internalProxy.eProxyURI()));
					} else if (proxy == null) {
						sb.append("  - Reference Status: Link is completely empty (null).\n");
						
						// NEW: Try to find what this was by looking at connections
						List<IDiagramModelArchimateConnection> sourceConns = dmo.getSourceConnections().stream()
								.filter(IDiagramModelArchimateConnection.class::isInstance)
								.map(IDiagramModelArchimateConnection.class::cast)
								.toList();
						List<IDiagramModelArchimateConnection> targetConns = dmo.getTargetConnections().stream()
								.filter(IDiagramModelArchimateConnection.class::isInstance)
								.map(IDiagramModelArchimateConnection.class::cast)
								.toList();
						
						if (!sourceConns.isEmpty() || !targetConns.isEmpty()) {
							sb.append("  - Context from Connections:\n");
							for (IDiagramModelArchimateConnection conn : sourceConns) {
								String relLabel = conn.getArchimateRelationship() != null ? ArchiLabelProvider.INSTANCE.getLabel(conn.getArchimateRelationship()) : "null relationship";
								String targetLabel = conn.getTarget() != null ? ArchiLabelProvider.INSTANCE.getLabel(conn.getTarget()) : "null target";
								sb.append(String.format("    * Outgoing: --[%s]--> to '%s'\n", relLabel, targetLabel));
							}
							for (IDiagramModelArchimateConnection conn : targetConns) {
								String relLabel = conn.getArchimateRelationship() != null ? ArchiLabelProvider.INSTANCE.getLabel(conn.getArchimateRelationship()) : "null relationship";
								String sourceLabel = conn.getSource() != null ? ArchiLabelProvider.INSTANCE.getLabel(conn.getSource()) : "null source";
								sb.append(String.format("    * Incoming: from '%s' --[%s]-->\n", sourceLabel, relLabel));
							}
						} else {
							sb.append("  - No connections found for this element.\n");
						}
					}
				}
			} catch (Exception ex) {
				sb.append(String.format("  - Error retrieving proxy info: %s\n", ex.getMessage()));
			}
			
			// Add parent info
			EObject parent = dmo.eContainer();
			if (parent instanceof IIdentifier idParent) {
				sb.append(String.format("  - Contained in: '%s' (%s) [%s]\n", 
						ArchiLabelProvider.INSTANCE.getLabel(parent), 
						parent.eClass().getName(),
						idParent.getId()));
			}

			return List.of(sb.toString());
		}

		// Orphaned element
		if (element.getArchimateModel() == null) {
			String elementName = ArchiLabelProvider.INSTANCE.getLabel(element);
			return List.of(dmoInfo + String.format(": Referenced ArchiMate element '%s' (%s) is orphaned from model.", elementName, element.getId()));
		}

		return List.of();
	}

	/**
	 * Checks ArchiMate connection ↔ relationship consistency (orphans, wrong ends).
	 *
	 * @param connection diagram line referencing a relationship
	 */
	protected List<String> checkDiagramModelArchimateConnection(IDiagramModelArchimateConnection connection) {
		List<String> messages = new ArrayList<>();

		String diagramName = connection.getDiagramModel() == null ? "Unknown View" : connection.getDiagramModel().getName();
		String connectionInfo = String.format("Connection '%s' in View '%s'", connection.getId(), diagramName);

		IArchimateRelationship relation = connection.getArchimateRelationship();

		// No referenced relation
		if (relation == null) {
			messages.add(connectionInfo + ": No referenced relationship found.");
		} else {
			// Orphaned relation
			if (relation.getArchimateModel() == null) {
				messages.add(connectionInfo + String.format(": Referenced relationship '%s' is orphaned from model.", relation.getId()));
			}
			// Orphaned relation source
			if (relation.getSource() != null && relation.getSource().getArchimateModel() == null) {
				messages.add(connectionInfo + String.format(": Relationship source '%s' is orphaned from model.", relation.getSource().getId()));
			}
			// Orphaned relation target
			if (relation.getTarget() != null && relation.getTarget().getArchimateModel() == null) {
				messages.add(connectionInfo + String.format(": Relationship target '%s' is orphaned from model.", relation.getTarget().getId()));
			}
			
			// Relationship ends != connection ends
			IArchimateConcept relSource = relation.getSource();
			IArchimateConcept relTarget = relation.getTarget();
			
			IArchimateConcept viewSource = ((IDiagramModelArchimateComponent) connection.getSource()).getArchimateConcept();
			IArchimateConcept viewTarget = ((IDiagramModelArchimateComponent) connection.getTarget()).getArchimateConcept();

			if (viewSource != relSource) {
				StringBuilder sb = new StringBuilder();
				sb.append(connectionInfo).append(": Relationship has wrong source end component.\n");
				sb.append(String.format("  Relationship: '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(relation), relation.getId()));
				sb.append(String.format("  Expected Source (from Rel): '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(relSource), relSource != null ? relSource.getId() : "null"));
				sb.append(String.format("  Actual Source (in View):    '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(viewSource), viewSource != null ? viewSource.getId() : "null"));
				messages.add(sb.toString());
			}
			
			if (viewTarget != relTarget) {
				StringBuilder sb = new StringBuilder();
				sb.append(connectionInfo).append(": Relationship has wrong target end component.\n");
				sb.append(String.format("  Relationship: '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(relation), relation.getId()));
				sb.append(String.format("  Expected Target (from Rel): '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(relTarget), relTarget != null ? relTarget.getId() : "null"));
				sb.append(String.format("  Actual Target (in View):    '%s' [%s]\n", ArchiLabelProvider.INSTANCE.getLabel(viewTarget), viewTarget != null ? viewTarget.getId() : "null"));
				messages.add(sb.toString());
			}
		}

		return messages;
	}

	/**
	 * Ensures {@code object} lives under its default folder type subtree.
	 *
	 * @param object concept or diagram
	 */
	protected List<String> checkObjectInCorrectFolder(IArchimateModelObject object) {
		if (!(object.eContainer() instanceof IFolder folder)) {
			return List.of();
		}

		IFolder topFolder = folder.getArchimateModel().getDefaultFolderForObject(object);

		if (folder == topFolder) {
			return List.of();
		}

		EObject e = folder;
		while ((e = e.eContainer()) != null) {
			if (e == topFolder) {
				return List.of();
			}
		}

		return List.of(NLS.bind(Messages.ModelChecker_26,
				new String[] { object.getName(), object.getId(), folder.getName() }));
	}

	/**
	 * Folder elements should only be {@link IArchimateConcept} or {@link IDiagramModel}.
	 *
	 * @param folder folder to scan
	 */
	protected List<String> checkFolderContainsCorrectObjects(IFolder folder) {
		List<String> messages = new ArrayList<>();

		// Only allowed these types in folder's elements list
		for (EObject eObject : folder.getElements()) {
			if (!(eObject instanceof IArchimateConcept || eObject instanceof IDiagramModel)) {
				String name = "(Folder: " + folder.getId() + " Object: " + ((IIdentifier) eObject).getId() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				messages.add(NLS.bind(Messages.ModelChecker_25, name));
			}
		}

		return messages;
	}

	/**
	 * Each profile must belong to the same model and match the host object’s concept class.
	 *
	 * @param profilesObject element or relationship carrying {@link IProfile}s
	 */
	protected List<String> checkProfiles(IProfiles profilesObject) {
		List<String> messages = new ArrayList<>();

		for (IProfile profile : profilesObject.getProfiles()) {
			// Profile must exist in this model
			if (profile.getArchimateModel() != ((IArchimateModelObject) profilesObject).getArchimateModel()) {
				messages.add(NLS.bind(Messages.ModelChecker_28, profile.getId()));
			}

			// Profile must have matching concept type
			EClass eClass = profile.getConceptClass();
			if (eClass == null || eClass != profilesObject.eClass()) {
				messages.add(NLS.bind(Messages.ModelChecker_29, profile.getId()));
			}
		}

		return messages;
	}

	/**
	 * Sub-classes can add a check by over-riding this
	 * 
	 * @param eObject The object in the model to check
	 * @return an array of error messages which can be empty
	 */
	protected List<String> checkObject(EObject eObject) {
		return List.of();
	}

	/**
	 * For each IDiagramModelArchimateComponent encountered increment the instance
	 * count
	 */
	private void incrementInstanceCount(IDiagramModelArchimateComponent dmc, Map<IArchimateConcept, List<IDiagramModelArchimateComponent>> map) {
		IArchimateConcept concept = dmc.getArchimateConcept();
		if (concept != null) { // don't want an NPE while checking
			map.computeIfAbsent(concept, k -> new ArrayList<>()).add(dmc);
		}
	}

	/**
	 * Check the actual IDiagramModelArchimateComponent instance count against the
	 * concept's reported instance count
	 */
	private List<String> checkDiagramComponentInstanceCount(Map<IArchimateConcept, List<IDiagramModelArchimateComponent>> map) {
		List<String> messages = new ArrayList<>();

		// Now check the total count against the reported count of the concept
		for (Entry<IArchimateConcept, List<IDiagramModelArchimateComponent>> entry : map.entrySet()) {
			IArchimateConcept concept = entry.getKey();
			List<IDiagramModelArchimateComponent> foundComponents = entry.getValue();
			List<IDiagramModelArchimateComponent> expectedComponents = new ArrayList<>(concept.getReferencingDiagramComponents());
			
			int count = foundComponents.size();
			int expectedCount = expectedComponents.size();
			
			if (expectedCount != count) {
				String conceptName = ArchiLabelProvider.INSTANCE.getLabel(concept);
				StringBuilder sb = new StringBuilder();
				sb.append(String.format("Wrong number of diagram component instances for '%s' (%s):\n", conceptName, concept.getId()));
				sb.append(String.format("  Expected %d (from concept's internal list):\n", expectedCount));
				for(IDiagramModelArchimateComponent dmc : expectedComponents) {
					String viewName = dmc.getDiagramModel() != null ? dmc.getDiagramModel().getName() : "Unknown View";
					sb.append(String.format("    - View: '%s', Component ID: %s\n", viewName, dmc.getId()));
				}
				sb.append(String.format("  Found %d (during model traversal):\n", count));
				for(IDiagramModelArchimateComponent dmc : foundComponents) {
					String viewName = dmc.getDiagramModel() != null ? dmc.getDiagramModel().getName() : "Unknown View";
					sb.append(String.format("    - View: '%s', Component ID: %s\n", viewName, dmc.getId()));
				}
				messages.add(sb.toString());
			}
		}

		return messages;
	}
}
