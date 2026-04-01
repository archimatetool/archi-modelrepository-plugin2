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
 * Builds the EMF Compare stack for repository merges: ID-first matching, synthetic IDs for bounds/features/etc.,
 * then runs the comparison for the given left, right, and origin model roots.
 */
@SuppressWarnings("nls")
public class MergeFactory {

	/** Match-engine factory that registers ID-first matching and a custom {@link EqualityHelper}. */
	private static class ExtendedMatchEngineFactoryImpl extends MatchEngineFactoryImpl {
		/** Builds the default matcher, wraps it with {@link IdentifierEObjectMatcher}, and sets a high ranking. */
		ExtendedMatchEngineFactoryImpl() {
			super();
			IEObjectMatcher defaultMatcher = DefaultMatchEngine
					.createDefaultEObjectMatcher(UseIdentifiers.WHEN_AVAILABLE);
			
			// Use a more aggressive ID matcher that prioritizes IDs over everything else
			IEObjectMatcher customIDMatcher = new IdentifierEObjectMatcher(defaultMatcher,
					eObject -> {
						String id = createIdentifier(eObject);
						// Log for debug if needed
						// System.out.println("ID for " + eObject.eClass().getName() + ": " + id);
						return id;
					});
			
			IComparisonFactory comparisonFactory = new DefaultComparisonFactory(new CustomEqualityHelperFactory());
			matchEngine = new DefaultMatchEngine(customIDMatcher, comparisonFactory);
			setRanking(100); // Increase ranking to ensure it's used
		}
	}

	/** Supplies an {@link EqualityHelper} that treats synthetic {@link MergeFactory#createIdentifier(EObject)} strings as equality for non-{@link IIdentifier} objects. */
	private static class CustomEqualityHelperFactory extends DefaultEqualityHelperFactory {
		/** {@inheritDoc} */
		@Override
		public IEqualityHelper createEqualityHelper() {
			return new EqualityHelper(EqualityHelper.createDefaultCache(getCacheBuilder())) {
				/**
				 * Two {@link IIdentifier}s use default EMF equality; otherwise compare {@link MergeFactory#createIdentifier(EObject)} when both non-null.
				 */
				@Override
				protected boolean matchingEObjects(EObject object1, EObject object2) {
					if (object1 instanceof IIdentifier && object2 instanceof IIdentifier) {
						return super.matchingEObjects(object1, object2);
					}
					String id1 = createIdentifier(object1);
					String id2 = createIdentifier(object2);
					if (id1 != null && id2 != null) {
						return id1.equals(id2);
					}
					return super.matchingEObjects(object1, object2);
				}
			};
		}
	}

	/**
	 * Standalone registry containing only our extended factory (ranking 100).
	 *
	 * @return registry suitable for {@link EMFCompare.Builder#setMatchEngineFactoryRegistry(org.eclipse.emf.compare.match.IMatchEngine.Factory.Registry)}
	 */
	public static IMatchEngine.Factory.Registry createMatchEngineFactoryRegistry() {
		IMatchEngine.Factory.Registry registry = MatchEngineFactoryRegistryImpl.createStandaloneInstance();
		registry.add(new ExtendedMatchEngineFactoryImpl());
		return registry;
	}

	/**
	 * Runs EMF Compare on three model roots. In repository merge, {@code left} is typically “theirs”, {@code right} is “ours”.
	 *
	 * @param left   left side of the comparison scope (REMOTE in merge)
	 * @param right  right side (LOCAL / ours)
	 * @param origin common ancestor model, or {@code null} if unavailable
	 * @return populated {@link Comparison} with matches, diffs, and conflicts
	 */
	public static Comparison createComparison(Notifier left, Notifier right, Notifier origin) {
		IMatchEngine.Factory.Registry registry = createMatchEngineFactoryRegistry();
		IComparisonScope scope = new DefaultComparisonScope(left, right, origin);

		// Default EMF Compare conflict detection; stricter object matching comes from the custom registry above.
		return EMFCompare.builder().setMatchEngineFactoryRegistry(registry).build().compare(scope);
	}

	/**
	 * Stable string id for matching “anonymous” diagram parts (bounds, bendpoints, features, properties) under an {@link IIdentifier} parent.
	 *
	 * @param eObject any model object
	 * @return Archi id for {@link IIdentifier}, synthetic key for attached value objects, or {@code null}
	 */
	private static String createIdentifier(EObject eObject) {
		if (eObject == null)
			return null;
		if (eObject instanceof IIdentifier identifier)
			return identifier.getId();

		EObject container = eObject.eContainer();
		if (container instanceof IIdentifier parent) {
			String parentId = parent.getId();
			if (eObject instanceof IBounds)
				return parentId + "#bounds";
			if (eObject instanceof IProperty prop)
				return parentId + "#prop#" + prop.getKey();
			if (eObject instanceof IDiagramModelBendpoint) {
				if (container instanceof IDiagramModelConnection conn) {
					return parentId + "#bendpoint#" + conn.getBendpoints().indexOf(eObject);
				}
			}
			if (eObject instanceof IFeature feature)
				return parentId + "#feature#" + feature.getName();
		}
		return null;
	}
}