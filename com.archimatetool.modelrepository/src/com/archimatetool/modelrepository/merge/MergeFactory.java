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
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IBounds;
import com.archimatetool.model.IIdentifier;
import com.google.common.base.Function;

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
    
    // Matcher function for objects that don't have an identifier
    private static Function<EObject, String> idFunction = new Function<EObject, String>() {
        @Override
        public String apply(EObject input) {
            // EMFCompare can see these IBounds as the same object in different parents:
            // <bounds x="120" y="240" width="120" height="55"/>
            // <bounds x="596" y="240" width="120" height="55"/>
            if(input instanceof IBounds && input.eContainer() instanceof IIdentifier parent) {
                return parent.getId() + "#bounds";
            }
            
            // a null return here tells the match engine to fall back to the other matchers
            return null;
        }
    };
    
    /**
     * This is a replacement for the constructor MatchEngineFactoryImpl(IEObjectMatcher matcher, IComparisonFactory comparisonFactory)
     * but that constructor is deprecated, so this is the equivalent.
     */
    private static class ExtendedMatchEngineFactoryImpl extends MatchEngineFactoryImpl {
        ExtendedMatchEngineFactoryImpl() {
            // Initialise with defaults
            super();
            
            // Default matcher
            IEObjectMatcher defaultMatcher = DefaultMatchEngine.createDefaultEObjectMatcher(UseIdentifiers.WHEN_AVAILABLE);
            
            // Custom matcher using our matcher function
            IEObjectMatcher customIDMatcher = new IdentifierEObjectMatcher(defaultMatcher, idFunction);
            
            // Comparison Factory
            IComparisonFactory comparisonFactory = new DefaultComparisonFactory(new DefaultEqualityHelperFactory());
            
            // Match engine
            matchEngine = new DefaultMatchEngine(customIDMatcher, comparisonFactory);
            
            // The default engine ranking is 10, so this must be higher to override it
            setRanking(20);
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
     * Create a Comparison for left, right and options base origin using DefaultComparisonScopt and our MatchEngineFactoryRegistry
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
}
