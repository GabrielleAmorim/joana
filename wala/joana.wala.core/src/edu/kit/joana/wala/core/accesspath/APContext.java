/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.wala.core.accesspath;

import edu.kit.joana.ifc.sdg.graph.SDGEdge;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.wala.core.accesspath.APContextManager.NoAlias;
import edu.kit.joana.wala.core.accesspath.APIntraProcV2.MergeOp;
import gnu.trove.map.TIntObjectMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a single calling context configuration. This class can be asked if certain heap data dependencies are
 * present in the alias configuration it represents.
 * 
 * @author Juergen Graf <juergen.graf@gmail.com>
 */
public class APContext implements Cloneable {
	
	private final int pdgId;
	private final TIntObjectMap<Set<AP>> n2ap;
	private final Set<EQClass> eqClasses = new HashSet<>();
	private Set<NoAlias> initialNoAlias = new HashSet<>();

	public APContext(final int pdgId, final TIntObjectMap<Set<AP>> n2ap) {
		this.pdgId = pdgId;
		this.n2ap = n2ap;
	}
	
	public void setInitialNoAlias(final Set<NoAlias> noAlias) {
		this.initialNoAlias = noAlias;
	}
	
	public APContext clone() {
		final APContext ctx = new APContext(pdgId, n2ap);
		ctx.eqClasses.addAll(eqClasses);
		ctx.setInitialNoAlias(initialNoAlias);
		return ctx;
	}
	
	private int eqID = 0;
	
	private int getNextID() {
		return eqID++;
	}
	
	private static class EQClass {
		private final int id;
		private final Set<AP> paths;
		
		private EQClass(final int id, final Set<AP> paths) {
			this.id = id;
			this.paths = Collections.unmodifiableSet(paths);
		}
		
		public boolean needsMerge(final EQClass other) {
			if (other == this) {
				return false;
			}
			
			for (final AP ap : paths) {
				if (other.paths.contains(ap)) {
					return true;
				}
			}
			
			return false;
		}
		
		public boolean contains(final AP ap) {
			return paths.contains(ap);	
		}
		
		public String toString() {
			return "eq" + id;
		}
		
		public static EQClass merge(final EQClass c1, final EQClass c2, final int newID) {
			final Set<AP> paths = new HashSet<>();
			paths.addAll(c1.paths);
			paths.addAll(c2.paths);
			return new EQClass(newID, paths);
		}
	}
	
	private void addToEqClasses(final MergeOp op) {
		final int id = getNextID();
		final Set<AP> paths = new HashSet<>();
		paths.addAll(op.from);
		paths.addAll(op.to);
		final EQClass cls = new EQClass(id, paths);
		addToEqClasses(cls);
	}

	private void addToEqClasses(final EQClass cls) {
		EQClass toMerge = null;
		for (final EQClass eqc : eqClasses) {
			if (eqc.needsMerge(cls)) {
				toMerge = eqc;
				break;
			}
		}
		
		if (toMerge != null) {
			eqClasses.remove(toMerge);
			final EQClass mergedEQ = EQClass.merge(toMerge, cls, cls.id);
			eqClasses.add(mergedEQ);
		} else {
			eqClasses.add(cls);
		}
	}

	public boolean mayBeActive(final SDGEdge e) {
		if (e.getKind() != SDGEdge.Kind.DATA_ALIAS) {
			return true;
		}
		
		return mayBeAliased(e.getSource(), e.getTarget());
	}

	public boolean mayBeAliased(final SDGNode n1, final SDGNode n2) {
		return mayBeAliased(n1.getId(), n2.getId());
	}
	
	public boolean mayBeAliased(final int n1, final int n2) {
		final Set<AP> ap1 = n2ap.get(n1);
		final Set<String> equiv1 = extractEquiv(ap1);
		final Set<AP> ap2 = n2ap.get(n2);
		final Set<String> equiv2 = extractEquiv(ap2);

		// if equiv1 and equiv 2 share a common element, they may be aliased.
		return equiv1.retainAll(equiv2); 
	}

	public void addMerge(final MergeOp mo) {
		addToEqClasses(mo);
	}

	private Set<String> extractEquiv(final Set<AP> paths) {
		final Set<String> equiv = new HashSet<>();
		
		for (final AP ap : paths) {
			final String eq = equivalenceClassAP(ap);
			equiv.add(eq);
		}
		
		return equiv;
	}
	
	private EQClass findEQ(final AP ap) {
		for (final EQClass cls : eqClasses) {
			if (cls.contains(ap)) {
				return cls;
			}
		}
		
		return null;
	}
	
	private String searchForEQ(final AP ap, final String suffix) {
		if (ap == null) {
			return suffix;
		}
		
		final EQClass eq = findEQ(ap);
		if (eq != null) {
			return eq + "." + suffix; 
		} else {
			final AP apParent = ap.getParentPath();
			final AP.Node end = ap.getEnd();
			final String newSuffix = (suffix == null || suffix.isEmpty() ? end.toString() : end + "." + suffix);
			
			return searchForEQ(apParent, newSuffix);
		}
	}
	
	private String equivalenceClassAP(final AP ap) {
		return searchForEQ(ap, "");
	}
	
	public boolean isAliased(final AP a1, final AP a2) {
		if (a1.equals(a2)) {
			return true;
		}
		
		final String eqAP1 = equivalenceClassAP(a1);
		final String eqAP2 = equivalenceClassAP(a2);
		
		if (eqAP1.equals(eqAP2)) { 
			return true;
		} else if (compatibleType(a1, a2)) {
			for (final NoAlias na : initialNoAlias) {
				if (na.captures(a1, a2)) {
					return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	public boolean compatibleType(final AP a1, final AP a2) {
		return true;
	}
	
}