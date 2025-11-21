/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.repository;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.jdom.JDOMUtils;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProfile;
import com.archimatetool.modelrepository.merge.ModelComparison;

/**
 * Create Commit manifest to include in a commit message
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class CommitManifest {
    
    private static Logger logger = Logger.getLogger(CommitManifest.class.getName());
    
    public static final String VERSION = "1.0.0";

    private static final String ELEMENT_MANIFEST = "manifest";
    private static final String PRE_CR = "\n\n";
    private static final String MANIFEST_START = "<" + ELEMENT_MANIFEST;
    private static final String MANIFEST_END = "</" + ELEMENT_MANIFEST + ">";
    private static final String ELEMENT_CHANGES = "changes";
    private static final String ELEMENT_OBJECT = "object";
    private static final String ATTRIBUTE_ID = "id";
    private static final String ATTRIBUTE_VERSION = "version";
    private static final String ATTRIBUTE_CHANGE = "change";
    private static final String ATTRIBUTE_AMENDED = "amended";
    
    static final String ADDED = "added";
    static final String DELETED = "deleted";
    static final String MODIFIED = "modified";
    static final String MOVED = "moved";
    
    static record ObjectChange(String id, String type) {}
    
    /**
     * @return a manifest for the first commit.
     *         Include all model object IDs.
     */
    public static String createManifestForInitialCommit(IArchimateModel model) {
        Element rootElement = createRootElement();
        Element changesElement = new Element(ELEMENT_CHANGES);
        rootElement.addContent(changesElement);
        
        // Model Id
        if(model.getId() != null) {
            changesElement.addContent(createObjectElement(model.getId(), ADDED));
        }
        
        // Other Ids
        for(Iterator<EObject> iter = model.eAllContents(); iter.hasNext();) {
            EObject eObject = iter.next();
            if(isValidObject(eObject)) {
                changesElement.addContent(createObjectElement(((IIdentifier)eObject).getId(), ADDED));
            }
        }
        
        return getManifestAsString(rootElement);
    }

    /*
        Cases not working
    
        1. Add note to view
        2. Commit (view is modified)
        3. Delete note
        4. Commit amend
        view is still modified because previous change and current change is modified
    
    
        1. Have an element in a sub-folder (commited)
        2. Move element to parent folder
        3. Commit
        4. Move element back to sub-folder
        5. Commit amend
        element is still moved because previous change and current change is moved
        
        
        1. Change an element such as name, documentation or property
        2. Commit
        3. Undo the change
        4. Commit amend
        element is still modified because previous change and current change is modified
     */
    
    /**
     * @return a manifest for a commit (that is not the first commit).
     */
    public static String createManifestForCommit(GitUtils utils, boolean amend) throws IOException {
        RevCommit latestCommit = utils.getLatestCommit().orElse(null);
        if(latestCommit == null) {
            return "";
        }
        
        // Compare working tree with the latest commit
        ModelComparison modelComparison = new ModelComparison(new ArchiRepository(utils.getRepository().getWorkTree()), latestCommit).init();
        Set<ObjectChange> changes = getChangedObjects(modelComparison);
        
        // If amending also add the objects in the latest commit
        if(amend) {
            // Get the changes from the commit that we are amending and add them
            Set<ObjectChange> previousChanges = getObjectChangesFromCommitMessage(latestCommit.getFullMessage());
            changes.addAll(previousChanges);
        }
        
        /*
         * Remove redundant changes
         * 
         * added   + deleted  = (both entries removed)
         * added   + modified = added kept
         * added   + moved    = added kept
         * deleted + modified = deleted kept
         * deleted + moved    = deleted kept
         */
        for(ObjectChange change : new HashSet<>(changes)) {
            if(ADDED.equals(change.type())) {
                ObjectChange other = getObjectChange(changes, change.id(), DELETED);
                if(other != null) {
                    changes.remove(change);
                    changes.remove(other);
                }
            }

            if(ADDED.equals(change.type()) || DELETED.equals(change.type())) {
                ObjectChange other = getObjectChange(changes, change.id(), MODIFIED);
                if(other != null) {
                    changes.remove(other);
                }

                other = getObjectChange(changes, change.id(), MOVED);
                if(other != null) {
                    changes.remove(other);
                }
            }
        }
        
        if(changes.isEmpty()) {
            return "";
        }

        Element rootElement = createRootElement();
        
        // Amended commit
        if(amend) {
            rootElement.setAttribute(ATTRIBUTE_AMENDED, "true");
        }
        
        Element changesElement = new Element(ELEMENT_CHANGES);
        rootElement.addContent(changesElement);
        
        for(ObjectChange change : changes) {
            changesElement.addContent(createObjectElement(change.id(), change.type()));
        }
        
        return getManifestAsString(rootElement);
    }
    
    /**
     * @return an ObjectChange in the given set matching id and type.
     *         Because we use a Set there should be only one match.
     */
    private static ObjectChange getObjectChange(Set<ObjectChange> changes, String id, String type) {
        for(ObjectChange change : changes) {
            if(change.id().equals(id) && change.type().equals(type)) {
                return change;
            }
        }
        
        return null;
    }
    
    /**
     * @return a Set of ObjectChanges from the ModelComparison comparing the working tree with the latest commit
     */
    private static Set<ObjectChange> getChangedObjects(ModelComparison modelComparison) {
        boolean debug = false;
        
        Set<ObjectChange> changes = new LinkedHashSet<>();
        
        for(Diff diff : modelComparison.getComparison().getDifferences()) {
            Match match = diff.getMatch();
            
            if(debug) {
                System.out.println("diff: " + diff);
            }
            
            // Left is the most recent
            EObject left = match.getLeft();
            if(left != null) {
                if(debug) {
                    System.out.println("left: " + left);
                }
                if(getActualObject(left, diff) instanceof IIdentifier id) {
                    if(debug) {
                        System.out.println("actual: " + id);
                    }
                    changes.add(new ObjectChange(id.getId(), getChangeType(left, diff))); // must use left for change type
                }
            }
            
            // Right is previous
            EObject right = match.getRight();
            if(right != null) {
                if(debug) {
                    System.out.println("right: " + right);
                }
                if(getActualObject(right, diff) instanceof IIdentifier id) {
                    if(debug) {
                        System.out.println("actual: " + id);
                    }
                    changes.add(new ObjectChange(id.getId(), getChangeType(right, diff))); // must use right for change type
                }
            }
            
            if(debug) {
                System.out.println("-------------------------");
            }
        }
        
        return changes;
    }
    
    /**
     * @return The actual object that changed or null if not a valid object.
     * If it's a diagram object, get the diagram itself.
     * If it's a folder member object, get that rather than the folder.
     */
    private static EObject getActualObject(EObject eObject, Diff diff) {
        // Name change in IDiagramModelArchimateComponent, so ignore this change because it's actually a name change in the linked concept
        if(eObject instanceof IDiagramModelArchimateComponent && diff instanceof AttributeChange attChange
                                                              && attChange.getAttribute().equals(IArchimatePackage.eINSTANCE.getNameable_Name())) {
            return null;
        }
        
        // Get the parent eContainer if eObject is Bounds, Properties, Feature, Profile
        if((!(eObject instanceof IArchimateModelObject) || eObject instanceof IProfile)) {
            eObject = eObject.eContainer();
        }
        
        // DiagramModelComponent, so get parent DiagramModel
        if(eObject instanceof IDiagramModelComponent dmc) {
            eObject = dmc.getDiagramModel();
        }
        
        // Folder member added or deleted, so get member object
        if(eObject instanceof IFolder && diff instanceof ReferenceChange refChange
                                      && refChange.getValue() instanceof IArchimateModelObject member) {
            eObject = member;
        }
        
        return isValidObject(eObject) ? eObject : null;
    }
    
    /**
     * @return The change type for a Diff
     */
    private static String getChangeType(EObject eObject, Diff diff) {
        // Folders when a child member is added, deleted or moved
        if(eObject instanceof IFolder
                              && diff instanceof ReferenceChange refChange // ReferenceChange is a member object
                              && refChange.getValue() instanceof IArchimateModelObject) {
            return switch (diff.getKind()) {
                case ADD -> ADDED;
                case DELETE -> DELETED;
                case MOVE -> MOVED;
                default -> MODIFIED;
            };
        }
        
        return MODIFIED;
    }
    
    /**
     * @return The provided commit message with the manifest removed
     */
    public static String getCommitMessageWithoutManifest(String commitMessage) {
        if(commitMessage == null) {
            return "";
        }
        int index = commitMessage.lastIndexOf(PRE_CR + MANIFEST_START); // Get last index in case user was a smartarse and added it to their commit message
        return index == -1 ? commitMessage : commitMessage.substring(0, index);
    }
    
    /**
     * @return true if the manifest in the commit message contains *any* change entry given the object id
     */
    public static boolean containsChange(String commitMessage, String objectId) {
        String manifest = getManifestFromCommitMessage(commitMessage);
        return manifest != null ? manifest.contains("<object id=\"" + objectId) : false;
    }
    
    /**
     * @return ObjectChange set from the commit message
     */
    static Set<ObjectChange> getObjectChangesFromCommitMessage(String commitMessage) {
        Set<ObjectChange> objects = new LinkedHashSet<>();
        
        Element rootElement = getRootElementFromCommitMessage(commitMessage);
        
        if(rootElement != null) {
            Element changesElement = rootElement.getChild(ELEMENT_CHANGES);
            if(changesElement != null) {
                for(Element childElement : changesElement.getChildren(ELEMENT_OBJECT)) {
                    String id = childElement.getAttributeValue(ATTRIBUTE_ID);
                    String change = childElement.getAttributeValue(ATTRIBUTE_CHANGE);
                    if(StringUtils.isSet(id) && StringUtils.isSet(change)) {
                        objects.add(new ObjectChange(id, change));
                    }
                }
            }
        }
        
        return objects;
    }
    
    /**
     * @return JDOM root element from a commit message, or null if not found
     */
    private static Element getRootElementFromCommitMessage(String commitMessage) {
        String manifest = getManifestFromCommitMessage(commitMessage);
        
        if(manifest != null) {
            try {
                Document doc = JDOMUtils.readXMLString(manifest);
                return doc != null ? doc.getRootElement() : null;
            }
            catch(JDOMException | IOException ex) {
                ex.printStackTrace();
                logger.warning("Could not parse manifest"); // Don't log exception just return null
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * @return The manifest as Strig from the commit message, or null if not found
     */
    static String getManifestFromCommitMessage(String commitMessage) {
        if(commitMessage == null) {
            return null;
        }
        int start = commitMessage.lastIndexOf(MANIFEST_START); // Get last index in case user was a smartarse and added it to their commit message
        int end = commitMessage.lastIndexOf(MANIFEST_END);
        return start != -1 && end != -1 ? commitMessage.substring(start, end + MANIFEST_END.length()) : null;
    }
    
    /**
     * @return JDOM manifest root element
     */
    private static Element createRootElement() {
        return new Element(ELEMENT_MANIFEST)
                   .setAttribute(ATTRIBUTE_VERSION, VERSION);
    }
    
    /**
     * @return the manifest written from JDOM to a String
     */
    private static String getManifestAsString(Element element) {
        try(StringWriter out = new StringWriter()) {
            new XMLOutputter(Format.getPrettyFormat()).output(element, out);
            return PRE_CR + out.toString();
        }
        catch(IOException ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Could not output manifest", ex);
            return "";
        }
    }
    
    /**
     * @return a JDOM element for an object
     */
    private static Element createObjectElement(String id, String changeType) {
        return new Element(ELEMENT_OBJECT)
                   .setAttribute(ATTRIBUTE_ID, id)
                   .setAttribute(ATTRIBUTE_CHANGE, changeType);
    }
    
    /**
     * @return true if eObject is of a type that we will write to the manifest 
     */
    static boolean isValidObject(EObject eObject) {
        return (eObject instanceof IArchimateModel
                || eObject instanceof IFolder
                || eObject instanceof IArchimateConcept
                || eObject instanceof IDiagramModel)
                && ((IIdentifier)eObject).getId() != null;
    }
}
