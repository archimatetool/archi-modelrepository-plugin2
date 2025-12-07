/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.modelrepository.merge;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.match.DefaultComparisonFactory;
import org.eclipse.emf.compare.match.DefaultEqualityHelperFactory;
import org.eclipse.emf.compare.match.DefaultMatchEngine;
import org.eclipse.emf.compare.match.IComparisonFactory;
import org.eclipse.emf.compare.match.IMatchEngine;
import org.eclipse.emf.compare.match.eobject.IEObjectMatcher;
import org.eclipse.emf.compare.match.eobject.IdentifierEObjectMatcher;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.compare.utils.EqualityHelper;
import org.eclipse.emf.compare.utils.IEqualityHelper;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IFeature;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;


/**
 * Factory to create custom merge classes
 * 
 * See https://eclipse.dev/emf/compare/documentation/latest/developer/developer-guide.html
 * See https://www.eclipse.org/forums/index.php/t/1081892/
 * 
 * @author Phillip Beauvoir
 */
@SuppressWarnings("nls")
public class MergeFactory {
    
    /**
     * This is a replacement for the constructor MatchEngineFactoryImpl(IEObjectMatcher matcher, IComparisonFactory comparisonFactory)
     * but that constructor is deprecated, so this is the equivalent.
     */
    private static class ExtendedMatchEngineFactoryImpl extends MatchEngineFactoryImpl {
        private boolean USE_CUSTOM_ID_MATCHER = true;
        private boolean USE_CUSTOM_EQUALITY_HELPER = false; // Experimental, not used
      
        ExtendedMatchEngineFactoryImpl() {
            // Initialise with defaults
            super();
            
             // Default matcher
            IEObjectMatcher defaultMatcher = DefaultMatchEngine.createDefaultEObjectMatcher(UseIdentifiers.WHEN_AVAILABLE);
            
            // Custom matcher using a function that returns an identifier for an object
            IEObjectMatcher customIDMatcher = new IdentifierEObjectMatcher(defaultMatcher, eObject -> createIdentifier(eObject));
            
            // Comparison Factory with either custom or default equality helper
            IComparisonFactory comparisonFactory = new DefaultComparisonFactory(USE_CUSTOM_EQUALITY_HELPER ?
                    new CustomEqualityHelperFactory() : new DefaultEqualityHelperFactory());
            
            // Match engine
            matchEngine = new DefaultMatchEngine(USE_CUSTOM_ID_MATCHER ? customIDMatcher : defaultMatcher, comparisonFactory);
            
            // The default engine ranking is 10, so this must be higher to override it
            setRanking(20);
        }
    }
    
    /**
     * Experimental EqualityHelperFactory
     */
    private static class CustomEqualityHelperFactory extends DefaultEqualityHelperFactory {
        @Override
        public IEqualityHelper createEqualityHelper() {
            return new EqualityHelper(EqualityHelper.createDefaultCache(getCacheBuilder())) {
                
                @Override
                protected boolean matchingEObjects(EObject object1, EObject object2) {
                    // If objects have identifiers use default method
                    if(object1 instanceof IIdentifier && object2 instanceof IIdentifier) {
                        return super.matchingEObjects(object1, object2);
                    }
                    
                    // Else match on custom identifiers
                    String id1 = createIdentifier(object1);
                    String id2 = createIdentifier(object2);
                    return id1 != null && id2 != null && id1.equals(id2); // Check both are not null
                }
            };
        }
    }
    
    /**
     * Create a new MatchEngineFactoryRegistryImpl that uses our custom ID matcher
     */
    public static IMatchEngine.Factory.Registry createMatchEngineFactoryRegistry() {
        // Create the registry
        IMatchEngine.Factory.Registry registry = MatchEngineFactoryRegistryImpl.createStandaloneInstance();
        
        // Add our MatchEngineFactory to the registry
        registry.add(new ExtendedMatchEngineFactoryImpl());
        
        return registry;
    }
    
    /**
     * Create a Comparison for left, right and options base origin using DefaultComparisonScope and our MatchEngineFactoryRegistry
     * @param left Left root of this comparison.
     * @param right Right root of this comparison.
     * @param origin Common ancestor of <code>left</code> and <code>right</code>.
     */
    public static Comparison createComparison(Notifier left, Notifier right, Notifier origin) {
        // Use our MatchEngineFactoryRegistry
        IMatchEngine.Factory.Registry matchEngineFactoryRegistry = createMatchEngineFactoryRegistry();
        
        // Default ComparisonScope
        IComparisonScope scope = new DefaultComparisonScope(left, right, origin);
        
        // Build the Comparison
        return EMFCompare.builder().setMatchEngineFactoryRegistry(matchEngineFactoryRegistry).build().compare(scope);
    }
    
    /**
     * @return a unique identifier for an object or null
     */
    private static String createIdentifier(EObject eObject) {
        // Object has an identifier so use it
        if(eObject instanceof IIdentifier identifier) {
            return identifier.getId();
        }
        
        // The object does not have an identifier but the parent container has one
        if(eObject.eContainer() instanceof IIdentifier parent) {
            switch(eObject) {
                // EMFCompare can see these IBounds as the same object in different parents:
                // <bounds x="120" y="240" width="120" height="55"/>
                // <bounds x="596" y="240" width="120" height="55"/>
                case IBounds bounds -> {
                    return parent.getId() + "#bounds";
                }
                case IFeature feature -> {
                    return parent.getId() + "#feature#" + feature.getName();
                }
                case IProperty property -> {
                    return parent.getId() + "#property#" + property.getKey() + "#" + ((IProperties)parent).getProperties().indexOf(property);
                }
                case IDiagramModelBendpoint bendpoint -> {
                    return parent.getId() + "#bendpoint#" + ((IDiagramModelConnection)parent).getBendpoints().indexOf(bendpoint);
                }
                default -> {
                    // a null return tells the match engine to fall back to the other matchers
                    return null;
                }
            }
        }
        
        // a null return tells the match engine to fall back to the other matchers
        return null;
    }
}
