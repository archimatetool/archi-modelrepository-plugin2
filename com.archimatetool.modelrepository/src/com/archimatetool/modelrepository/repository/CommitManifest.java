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

import com.archimatetool.jdom.JDOMUtils;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateComponent;
import com.archimatetool.model.IDiagramModelComponent;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;

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
    private static final String ELEMENT_CHANGES = "changes";
    private static final String ELEMENT_OBJECT = "object";
    private static final String ATTRIBUTE_ID = "id";
    private static final String ATTRIBUTE_VERSION = "version";
    
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
            changesElement.addContent(createObjectElement(model.getId()));
        }
        
        // Other Ids
        for(Iterator<EObject> iter = model.eAllContents(); iter.hasNext();) {
            EObject eObject = iter.next();
            if(isValidObject(eObject)) {
                changesElement.addContent(createObjectElement(((IIdentifier)eObject).getId()));
            }
        }
        
        return getManifestAsString(rootElement);
    }

    /**
     * @return a manifest for a commit that is not the first commit.
     */
    public static String createManifestForCommit(GitUtils utils, boolean amend) throws IOException {
        RevCommit latestCommit = utils.getLatestCommit();
        if(latestCommit == null) {
            return "";
        }
        
        ModelComparison modelComparison = new ModelComparison(new ArchiRepository(utils.getRepository().getWorkTree()), latestCommit).init();
        Set<String> objects = getChangedObjectIds(modelComparison);
        
        // If amending also add the object Ids in the previous commit
        if(amend) {
            objects.addAll(getObjectIdsFromCommitMessage(latestCommit.getFullMessage()));
        }

        if(objects.isEmpty()) {
            return "";
        }

        Element rootElement = createRootElement();
        Element changesElement = new Element(ELEMENT_CHANGES);
        rootElement.addContent(changesElement);
        
        for(String id : objects) {
            changesElement.addContent(createObjectElement(id));
        }
        
        return getManifestAsString(rootElement);
    }
    
    private static Set<String> getChangedObjectIds(ModelComparison modelComparison) {
        boolean debug = false;
        Set<EObject> objectsChanged = new HashSet<>(); // for debugging
        
        Set<String> objectIds = new HashSet<>();
        
        for(Diff diff : modelComparison.getComparison().getDifferences()) {
            Match match = diff.getMatch();
            
            if(match.getLeft() instanceof IIdentifier left) { // Left is the most recent, can be null
                if(debug) {
                    System.out.println("left: " + left);
                }
                
                IIdentifier object = getActualObject(left, diff);
                if(object != null) {
                    objectIds.add(object.getId());
                    
                    if(debug) {
                        objectsChanged.add(object);
                    }
                }
            }
            
            if(match.getRight() instanceof IIdentifier right) { // Right is previous, can be null
                if(debug) {
                    System.out.println("right: " + right);
                }
                
                IIdentifier object = getActualObject(right, diff);
                if(object != null) {
                    objectIds.add(object.getId());
                    
                    if(debug) {
                        objectsChanged.add(object);
                    }
                }
            }
        }
        
        if(debug) {
            System.out.println();
            System.out.println("--------- added ------------");
            System.out.println();
            
            for(EObject eObject : objectsChanged) {
                System.out.println(eObject);
            }
        }
        
        return objectIds;
    }
    
    /**
     * @return The given commit message with the manifest removed
     */
    public static String getCommitMessageWithoutManifest(String commitMessage) {
        int index = commitMessage.lastIndexOf(PRE_CR + MANIFEST_START); // Get last index in case user was a smartarse and added it to their commit message
        return index == -1 ? commitMessage : commitMessage.substring(0, index);
    }
    
    /**
     * @return Object Ids from commit message
     */
    public static Set<String> getObjectIdsFromCommitMessage(String commitMessage) {
        Set<String> objects = new HashSet<>();
        
        Element rootElement = getRootElementFromCommitMessage(commitMessage);
        
        if(rootElement != null) {
            Element changesElement = rootElement.getChild(ELEMENT_CHANGES);
            if(changesElement != null) {
                for(Element childElement : changesElement.getChildren(ELEMENT_OBJECT)) {
                    String id = childElement.getAttributeValue(ATTRIBUTE_ID);
                    if(id != null && id.length() != 0) {
                        objects.add(id);
                    }
                }
            }
        }
        
        return objects;
    }
    
    /**
     * @return true if the manifest in the commit message contains a change of id
     */
    public static boolean containsChange(String commitMessage, String id) {
        String manifest = getManifestFromCommitMessage(commitMessage);
        return manifest != null ? manifest.contains("<object id=\"" + id) : false;
    }
    
    /**
     * @return The actual object that changed or null if not a valid object.
     * If it's a diagram object, get the diagram itself.
     * If it's a folder member object, get that rather than the folder.
     */
    private static IIdentifier getActualObject(IIdentifier object, Diff diff) {
        // Name change in IDiagramModelArchimateComponent, so ignore this change because it's actually a name change in the linked concept
        if(object instanceof IDiagramModelArchimateComponent && diff instanceof AttributeChange attChange
                                                             && attChange.getAttribute().equals(IArchimatePackage.eINSTANCE.getNameable_Name())) {
            return null;
        }
        
        // DiagramModelComponent, so get parent DiagramModel
        if(object instanceof IDiagramModelComponent dmc) {
            object = dmc.getDiagramModel();
        }
        
        // Folder member added or deleted, so get member object
        if(object instanceof IFolder && diff instanceof ReferenceChange refChange
                                     && refChange.getValue() instanceof IIdentifier member) {
            object = member;
        }
        
        return isValidObject(object) ? object : null;
    }
    
    /**
     * @return The Manifest from the commit message, or null
     */
    private static String getManifestFromCommitMessage(String commitMessage) {
        int index = commitMessage.lastIndexOf(MANIFEST_START); // Get last index in case user was a smartarse and added it to their commit message
        return index != -1 ? commitMessage.substring(index) : null;
    }
    
    private static Element getRootElementFromCommitMessage(String commitMessage) {
        String manifest = getManifestFromCommitMessage(commitMessage);
        
        if(manifest != null) {
            try {
                Document doc = JDOMUtils.readXMLString(manifest);
                return doc != null ? doc.getRootElement() : null;
            }
            catch(JDOMException | IOException ex) {
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Could not get manifest", ex);
                return null;
            }
        }
        
        return null;
    }
    
    private static Element createRootElement() {
        return new Element(ELEMENT_MANIFEST)
                   .setAttribute(ATTRIBUTE_VERSION, VERSION);
    }
    
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
    
    private static Element createObjectElement(String id) {
        return new Element(ELEMENT_OBJECT)
                   .setAttribute(ATTRIBUTE_ID, id);
    }
    
    private static boolean isValidObject(EObject eObject) {
        return (eObject instanceof IArchimateModel
                || eObject instanceof IFolder
                || eObject instanceof IArchimateConcept
                || eObject instanceof IDiagramModel)
                && ((IIdentifier)eObject).getId() != null;
    }
}
