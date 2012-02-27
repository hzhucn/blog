/*
 * Copyright (c) 2005, 2006, Regents of the University of California
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.  
 *
 * * Neither the name of the University of California, Berkeley nor
 *   the names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior 
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package blog;

import java.util.*;
import common.Util;
import common.DGraph;
import common.AbstractDGraph;
import common.TupleIterator;

/**
 * An object generation graph contains nodes that represent sets of
 * objects with certain generation histories.  A node in an object
 * generation graph is like a formula with a free variable: given a
 * particular world and a particular assignment of values to other
 * variables, we can say whether any given object <em>satisfies</em>
 * the node.  An object <i>o</i> satisfies a node <i>v</i> given 
 * a world <i>w</i> and a variable assignment <i>a</i> if:
 * <ul> 
 * <li> <i>v</i> is a <code>POPNode</code> for a POP <i>(tau, g1, ..., gk)</i> 
 *      and <i>o</i> satisfies a POP application 
 *      <i>(tau, (g1, o1), ..., (gk, ok))</i> such that <i>o1, ..., ok</i> 
 *      satisfy the parents of <i>v</i> given <i>(w, a)</i>;
 *
 * <li> <i>v</i> is an <code>OrNode</code> and <i>o</i> satisfies one of the 
 *      parents of <i>v</i> given <i>(w, a)</i> (we ensure that the parents 
 *      of an <code>OrNode</code> are satisfied by disjoint sets);
 *
 * <li> <i>v</i> is a <code>TermNode</code> for a term <i>t</i> and 
 *      <i>o</i> is the denotation of <i>t</i> given <i>(w, a)</i>;
 *
 * <li> <i>v</i> is an <code>IntegerNode</code> with lower bounds 
 *      <i>s1, ..., sj</i> and upper bounds <i>t1, ..., tk</i>, and <i>o</i> 
 *      is an integer (or timestep) that is &gt;= the denotations of 
 *      <i>s1, ..., sj</i> and &lt;= the denotations of <i>t1, ..., tk</i> 
 *      given <i>(w, a)</i>;
 *
 * <li> <i>v</i> is a <code>GuaranteedNode</code> for type <i>tau</i> and 
 *      <i>o</i> is a guaranteed object of type <i>tau</i>.
 * </ul>
 *
 * <p>The arguments to the ObjGenGraph constructor describe a certain
 * set of objects.  The ObjGenGraph contains a distinguished
 * <em>target node</em> that is satisfied by all the described objects
 * (and possibly some other objects that exist).  The
 * <code>iterator</code> method returns an iterator over 
 * the objects that satisfy the target node.  This set may be
 * infinite, but we can still iterate over it.
 *
 * TODO: Fix case where term in TermNode evaluates to Model.NULL.
 */

public class ObjGenGraph extends AbstractDGraph {
    /**
     * Creates an object generation graph where the target node is satisfied 
     * by all objects of the given type.
     */
    public ObjGenGraph(Type type) {
	this.var = null;
	targetNode = getTypeNode(type, null, Collections.EMPTY_LIST);
	recordAncestorGraph(targetNode);
    }

    /**
     * Creates an object generation graph where the target node is satisfied 
     * by all objects of the given type that satisfy all the given constraints 
     * when bound to <code>subjectVar</code>.
     *
     * @param constraints List of Formula objects.  Only literals are 
     *                    processed.  
     *
     * @param freeVars    Set of LogicalVar objects representing variables 
     *                    that should not be used in the graph because 
     *                    they will not be assigned values when the graph 
     *                    is used.
     */
    public ObjGenGraph(Type type, LogicalVar subjectVar, List constraints, 
		       Set freeVars) {
	this.var = subjectVar;
	this.freeVars = freeVars;

	//System.out.println("Creating ObjGenGraph for {" + type + " "
	//		   + subjectVar + " : " + constraints + "}");

	targetNode = getTypeNode(type, subjectVar, constraints);
	recordAncestorGraph(targetNode);

	//print(System.out);
    }

    /**
     * Creates an object generation graph where the set of free variables 
     * consists of just the subject variable.
     */
    public ObjGenGraph(Type type, LogicalVar subjectVar, List constraints) {
	this(type, subjectVar, constraints, 
	     Collections.singleton(subjectVar));
    }

    /**
     * Returns true if this ObjGenGraph exactly represents the set specified 
     * in its constructor: that is, all the objects returned by 
     * <code>elementSet</code> satisfy the constraints passed to the 
     * constructor.
     */
    public boolean isExact() {
	return false;
    }

    /**
     * Returns an iterator over the set of objects that satisfy the 
     * target node in the given context.
     */
    public ObjectIterator iterator(EvalContext context) {
	return iterator(context, Collections.EMPTY_SET, false);
    }

    /**
     * Returns an iterator over the set of objects that satisfy the
     * target node in the given context.  If
     * <code>returnPOPApps</code> is true, returns NumberVars to
     * represent entire sets of POP application satisfiers, rather
     * than returning the satifiers themselves.
     */
    public ObjectIterator iterator(EvalContext context, 
				   Set externallyDistinguished, 
				   boolean returnPOPApps) {
	return new SatisfierIterator(context, externallyDistinguished, 
				     returnPOPApps);
    }

    /**
     * Returns true if the iteration order for objects that satisfy
     * the target node is affected by the iteration order for object
     * identifiers in the given context.  This is true if any ancestor
     * of the target node is a POPNode for a type that is represented
     * with object identifiers in the given context.
     */
    public boolean dependsOnIdOrder(EvalContext context) {
	return targetNode.dependsOnIdOrder(context); 
    }

    public Set nodes() {
	return Collections.unmodifiableSet(nodes);
    }

    public Set getParents(Object v) {
	if (nodes.contains(v)) {
	    return ((Node) v).getParents();
	}
	throw new IllegalArgumentException
	    ("Tried to get parents of non-node " + v + ".");
    }

    public Set getChildren(Object v) {
	if (nodes.contains(v)) {
	    return ((Node) v).getChildren();
	}
	throw new IllegalArgumentException
	    ("Tried to get children of non-node " + v + ".");
    }

    public static abstract class Node {	

	abstract Set getParents();

	Set getChildren() {
	    return Collections.unmodifiableSet(children);
	}

	/**
	 * Ensures that the given node is in this node's child set.
	 */
	void addChild(Node child) {
	    children.add(child);
	}

	/**
	 * Returns an iterator over the objects that satisfy this node 
	 * in the given context.    
	 *
	 * <p>The obvious algorithm for enumerating objects that satisfy 
	 * a node is by recursion in the object generation graph.  But 
	 * that algorithm would get stuck in cycles.  Thus, we terminate 
	 * the recursion at the parents of POPNodes.  A non-root node's 
	 * <code>iterator</code> method will be called repeatedly, with 
	 * larger and larger lists of satisfying objects for the parents 
	 * of POP nodes.  
	 *
	 * <p>Since we're calling <code>iterator</code> repeatedly, we
	 * need a way to get distinct objects each time.  Thus, we
	 * specify a list of <em>desired</em> objects for each POP
	 * node parent.  All the returned objects must satisfy a POP
	 * application involving at least one of these desired objects
	 * and possibly some of the additional objects specified in
	 * <code>otherPOPParentObjs</code>.  The desired parent
	 * objects will always be objects that were not included in
	 * the previous calls to <code>iterator</code>, so the objects
	 * returned will be distinct from those returned previously.
	 *
	 * <p>As an exception, if <code>includeGuaranteed</code> is true, 
	 * then the returned objects may also be guaranteed objects,  
	 * objects that satisfy a POP application with an empty tuple of 
	 * generating objects, or the satisfiers of TermNodes.  
	 *
	 * @param desiredPOPParentObjs     Map from Node to List
	 * @param otherPOPParentObjs  Map from Node to List
	 */
	public abstract ObjectIterator iterator(EvalContext context, 
						Set externallyDistinguished, 
						boolean returnPOPApps, 
						Map desiredPOPParentObjs, 
						Map otherPOPParentObjs, 
						boolean includeGuaranteed);

	/**
	 * Returns true if every call to this node's <code>iterator</code>
	 * method returns an iterator over a finite set.  
	 */
	public abstract boolean isFinite();

	/**
	 * Returns true if the iteration order for satisfiers of this
	 * node depends on the iteration order for object identifiers
	 * in the given context.
	 */
	public abstract boolean dependsOnIdOrder(EvalContext context);

	Set children = new HashSet();
    }

    public static class OrNode extends Node {
	/**
	 * Creates a new OrNode with no parents.
	 */
	private OrNode() {
	    this.parents = new HashSet();
	}

	/**
	 * Creates a new OrNode with the given nodes as parents.
	 *
	 * @param parents Set of Node objects
	 */
	private OrNode(Set parents) {
	    this.parents = new HashSet(parents);
	}

	Set getParents() {
	    return Collections.unmodifiableSet(parents);
	}

	/**
	 * Adds the given node as a parent of this node.
	 */
	void addParent(Node parent) {
	    parents.add(parent);
	}

	public ObjectIterator iterator(EvalContext context, 
				       Set externallyDistinguished, 
				       boolean returnPOPApps, 
				       Map desiredPOPParentObjs, 
				       Map otherPOPParentObjs, 
				       boolean includeGuaranteed) {
	    return new OrNodeIterator(context, externallyDistinguished, 
				      returnPOPApps, 
				      desiredPOPParentObjs, 
				      otherPOPParentObjs,
				      includeGuaranteed);
	}

	public boolean isFinite() {
	    return true; // assumes no IntegerNodes as parents
	}

	public boolean dependsOnIdOrder(EvalContext context) {
	    for (Iterator iter = parents.iterator(); iter.hasNext(); ) {
		Node parent = (Node) iter.next();
		if (parent.dependsOnIdOrder(context)) {
		    return true;
		}
	    }
	    return false;
	}

	public String toString() {
	    return ("OrNode" + parents);
	}

	private class OrNodeIterator extends AbstractObjectIterator {
	    OrNodeIterator(EvalContext context, 
			   Set externallyDistinguished, 
			   boolean returnPOPApps, 
			   Map desiredPOPParentObjs, 
			   Map otherPOPParentObjs, 
			   boolean includeGuaranteed) {
		this.context = context;
		this.externallyDistinguished = externallyDistinguished;
		this.returnPOPApps = returnPOPApps;
		this.desiredPOPParentObjs = desiredPOPParentObjs;
		this.otherPOPParentObjs = otherPOPParentObjs;
		this.includeGuaranteed = includeGuaranteed;

		parentsIter = parents.iterator();
	    }

	    protected int skipAfterNext() {
		return curParentIter.skipIndistinguishable();
	    }
	    
	    protected Object findNext() {
		while ((curParentIter == null) || !curParentIter.hasNext()) {
		    if ((curParentIter != null) 
			&& !curParentIter.canDetermineNext()) {
			canDetermineNext = false;
			return null;
		    }

		    // Move on to next parent
		    if (parentsIter.hasNext()) {
			Node parent = (Node) parentsIter.next();
			if (!parent.isFinite()) {
			    throw new IllegalStateException
				("Parent of OrNode returned iterator over "
				 + "infinite set.");
			}
			curParentIter = parent.iterator
			    (context, externallyDistinguished, returnPOPApps, 
			     desiredPOPParentObjs, otherPOPParentObjs, 
			     includeGuaranteed);
		    } else {
			return null;
		    }
		}

		return curParentIter.next();
	    }

	    EvalContext context;
	    Set externallyDistinguished;
	    boolean returnPOPApps;
	    Map desiredPOPParentObjs;
	    Map otherPOPParentObjs;
	    boolean includeGuaranteed;

	    Iterator parentsIter;
	    ObjectIterator curParentIter = null;
	}

	Set parents;
    }

    /**
     * Node satisfied by the set of objects that satisfy a given potential 
     * object pattern (POP).
     */
    public static class POPNode extends Node {
	private POPNode(POP pop, List parents) {
	    this.pop = pop;
	    this.parents = new ArrayList(parents);
	}

	public Set getParents() {
	    return new HashSet(parents);
	}

	public ObjectIterator iterator(EvalContext context, 
				       Set externallyDistinguished, 
				       boolean returnPOPApps, 
				       Map desiredPOPParentObjs, 
				       Map otherPOPParentObjs, 
				       boolean includeGuaranteed) {
	    return new POPNodeIterator(context, externallyDistinguished, 
				       returnPOPApps, 
				       desiredPOPParentObjs, 
				       otherPOPParentObjs, includeGuaranteed);
	}

	public boolean isFinite() {
	    return true;
	}

	public boolean dependsOnIdOrder(EvalContext context) {
	    return context.usesIdentifiers(pop.type());
	}

	public String toString() {
	    return ("POP[" + pop + "]");
	}

	/**
	 * To return objects generated by POP applications 
	 * that involve at least one desired parent object, we iterate 
	 * over tuples of generating objects where the first position 
	 * with a desired object is position 1, then likewise for position 
	 * 2, and so on.  
	 */
	private class POPNodeIterator extends AbstractObjectIterator {
	    POPNodeIterator(EvalContext context, 
			    Set externallyDistinguished, 
			    boolean returnPOPApps, 
			    Map desiredPOPParentObjs, 
			    Map otherPOPParentObjs, 
			    boolean includeGuaranteed) {
		this.context = context;
		this.externallyDistinguished = externallyDistinguished;
		this.returnPOPApps = returnPOPApps;
		this.desiredPOPParentObjs = desiredPOPParentObjs;
		this.otherPOPParentObjs = otherPOPParentObjs;
		this.includeGuaranteed = includeGuaranteed;
	    }

	    protected int skipAfterNext() {
		if (satisfyingObjIter == null) {
		    return 0;
		}
		return satisfyingObjIter.skipIndistinguishable();
	    }

	    protected Object findNext() {
		while ((satisfyingObjIter == null) 
		       || !satisfyingObjIter.hasNext()) {

		    // Need to move on to next parent tuple -- that is, 
		    // next tuple of generating objects
		    while ((parentTupleIter == null) 
			   || !parentTupleIter.hasNext()) {

			// Need to move on to next first-desired-object index
			if (parents.isEmpty() && includeGuaranteed
			        && !doneEmptyTuple) {
			    parentTupleIter = new TupleIterator
				(Collections.EMPTY_LIST);
			    doneEmptyTuple = true;
			} else if (firstDesiredObjIndex < parents.size()) {
			    parentTupleIter = new TupleIterator
				(getParentObjLists());
			    ++firstDesiredObjIndex;
			} else {
			    return null;
			}
		    }

		    // Have tuple of generating objects; 
		    // see what they generate 
		    List genObjs = (List) parentTupleIter.next();
		    NumberVar nv = new NumberVar(pop, genObjs);
		    if (returnPOPApps) {
			if (context.getValue(nv) == null) {
			    canDetermineNext = false;
			    return null;
			}
			return nv;
//		    } else {
//			satisfyingObjIter = context.getSatisfiers(nv)
//			    .iterator(externallyDistinguished);
//			if (satisfyingObjIter == null) {
//			    canDetermineNext = false;
//			    return null;
//			}
//		    }
		    } else {
			ObjectSet satisfyingObjs = context.getSatisfiers(nv);
			if (satisfyingObjs != null)
			    satisfyingObjIter = context.getSatisfiers(nv)
			    .iterator(externallyDistinguished);
			if (satisfyingObjs == null || satisfyingObjIter == null) {
			    canDetermineNext = false;
			    return null;
			}
		    }
		}

		return satisfyingObjIter.next();
	    }

	    private List getParentObjLists() {
		List objLists = new ArrayList(); // List of Lists
		for (int i = 0; i < parents.size(); ++i) {
		    Node parent = (Node) parents.get(i);
		    if (i < firstDesiredObjIndex) {
			objLists.add(otherPOPParentObjs.get(parent));
		    } else if (i == firstDesiredObjIndex) {
			objLists.add(desiredPOPParentObjs.get(parent));
		    } else {
			objLists.add(Util.concat
				     ((List) desiredPOPParentObjs.get(parent), 
				      (List) otherPOPParentObjs.get(parent)));
		    }
		}
		return objLists;
	    }

	    EvalContext context;
	    Set externallyDistinguished;
	    boolean returnPOPApps;
	    Map desiredPOPParentObjs;
	    Map otherPOPParentObjs;
	    boolean includeGuaranteed;

	    boolean doneEmptyTuple = false;
	    int firstDesiredObjIndex = 0;
	    Iterator parentTupleIter = null;
	    ObjectIterator satisfyingObjIter = null;
	}

	POP pop;
	List parents;
    }

    /**
     * Node satisfied only by the denotation of the given term.
     */
    public static class TermNode extends Node {
	private TermNode(Term term) {
	    this.term = term;
	}

	public Set getParents() {
	    return Collections.EMPTY_SET;
	}

	public ObjectIterator iterator(EvalContext context, 
				       Set externallyDistinguished, 
				       boolean returnPOPApps, 
				       Map desiredPOPParentObjs, 
				       Map otherPOPParentObjs, 
				       boolean includeGuaranteed) {
	    if (includeGuaranteed) {
		Object value = term.evaluate(context);
		if (value == null) {
		    return (ObjectIterator) ObjectSet.UNDETERMINED_SET
			.iterator();
		} 
		return (ObjectIterator) AbstractObjectSet.singleton(value)
		    .iterator();
	    }
	    return (ObjectIterator) ObjectSet.EMPTY_OBJECT_SET.iterator();
	}

	public boolean isFinite() {
	    return true;
	}

	public boolean dependsOnIdOrder(EvalContext context) {
	    return false;
	}

	public String toString() {
	    return term.toString();
	}

	Term term;
    }

    public static class IntegerNode extends Node {
	void addLowerBound(Term term, boolean strict) {
	    // Store weak lower bound, which is 1 greater than strict 
	    // lower bound.
	    lowerBounds.add(new Bound(term, strict ? 1 : 0));
	}

	void addUpperBound(Term term, boolean strict) {
	    // Store weak upper bound, which is 1 less than strict 
	    // upper bound.  
	    upperBounds.add(new Bound(term, strict ? -1 : 0));
	}

	boolean isConstrained() {
	    return (!lowerBounds.isEmpty()) || (!upperBounds.isEmpty());
	}

	public Set getParents() {
	    return Collections.EMPTY_SET;
	}

	public ObjectIterator iterator(EvalContext context, 
				       Set externallyDistinguished, 
				       boolean returnPOPApps, 
				       Map desiredPOPParentObjs, 
				       Map otherPOPParentObjs, 
				       boolean includeGuaranteed) {
	    if (!includeGuaranteed) {
		return (ObjectIterator) ObjectSet.EMPTY_OBJECT_SET.iterator();
	    }

	    if (!lowerBounds.isEmpty()) {
		Integer l = getLowerBound(context);
		if (l == null) {
		    return (ObjectIterator) ObjectSet.UNDETERMINED_SET
			.iterator();
		}

		if (!upperBounds.isEmpty()) {
		    Integer u = getUpperBound(context);
		    if (u == null) {
			return (ObjectIterator) ObjectSet.UNDETERMINED_SET
			    .iterator();
		    }

		    Iterator iter = Util.getIntegerRangeIterator
			(l.intValue(), u.intValue());
		    return new IntNodeIterator(iter);
		}

		Iterator iter = Util.getAscendingIntegerIterator(l.intValue());
		return new IntNodeIterator(iter);
	    }
	    if (!upperBounds.isEmpty()) {
		Integer u = getUpperBound(context);
		if (u == null) {
		    return (ObjectIterator) ObjectSet.UNDETERMINED_SET
			.iterator();
		}

		Iterator iter = Util.getDescendingIntegerIterator
		    (u.intValue());
		return new IntNodeIterator(iter);
	    }

	    return new IntNodeIterator(Util.getIntegerIterator());
	}

	public boolean isFinite() {
	    return ((!lowerBounds.isEmpty()) && (!upperBounds.isEmpty()));
	}

	public boolean dependsOnIdOrder(EvalContext context) {
	    return false;
	}

	/**
	 * Method that can be overridden by subclasses so that the 
	 * iterator for this node returns objects corresponding to integers, 
	 * not the integers themselves.
	 */
	protected Object correspondingObj(Integer i) {
	    return i;
	}

	public String toString() {
	    StringBuffer buf = new StringBuffer();
	    buf.append("[(");
	    for (Iterator iter = lowerBounds.iterator(); iter.hasNext(); ) {
		buf.append(iter.next());
	    }
	    buf.append("), (");
	    for (Iterator iter = upperBounds.iterator(); iter.hasNext(); ) {
		buf.append(iter.next());
	    }
	    buf.append(")]");
	    return buf.toString();
	}

	Integer getLowerBound(EvalContext context) {
	    Integer bestSoFar = null;
	    for (Iterator iter = lowerBounds.iterator(); iter.hasNext(); ) {
		Integer value = ((Bound) iter.next()).getValue(context);
		if (value == null) {
		    return null;
		}
		if ((bestSoFar == null) 
		        || (value.intValue() > bestSoFar.intValue())) {
		    bestSoFar = value;
		}
	    }
	    return bestSoFar;
	}

	Integer getUpperBound(EvalContext context) {
	    Integer bestSoFar = null;
	    for (Iterator iter = upperBounds.iterator(); iter.hasNext(); ) {
		Integer value = ((Bound) iter.next()).getValue(context);
		if (value == null) {
		    return null;
		}
		if ((bestSoFar == null) 
		        || (value.intValue() < bestSoFar.intValue())) {
		    bestSoFar = value;
		}
	    }
	    return bestSoFar;
	}

	private class IntNodeIterator extends AbstractObjectIterator {
	    IntNodeIterator(Iterator underlying) {
		this.underlying = underlying;
	    }

	    protected Object findNext() {
		if (!underlying.hasNext()) {
		    return null;
		}
		return correspondingObj((Integer) underlying.next());
	    }

	    private Iterator underlying;
	}

	private static class Bound {
	    Bound(Term term, int offset) {
		this.term = term;
		this.offset = offset;
	    }

	    Integer getValue(EvalContext context) {
		Number value = (Number) term.evaluate(context);
		if (value == null) {
		    return null;
		}
		return new Integer(value.intValue() + offset);
	    }

	    public boolean equals(Object o) {
		if (o instanceof Bound) {
		    Bound other = (Bound) o;
		    return (term.equals(other.term) 
			    && (offset == other.offset));
		}
		return false;
	    }

	    public int hashCode() {
		return (term.hashCode() + offset);
	    }

	    public String toString() {
		if (offset > 0) {
		    return (term + "+" + offset);
		}
		if (offset > 0) {
		    return (term + "-" + Math.abs(offset));
		}
		return term.toString();
	    }

	    private Term term;
	    private int offset;
	}

	private List lowerBounds = new ArrayList(); // of Bound
	private List upperBounds = new ArrayList(); // of Bound
    }

    public static class TimestepNode extends IntegerNode {
	protected Object correspondingObj(Integer i) {
	    return Timestep.at(i.intValue());
	}
    }

    public static class GuaranteedNode extends Node {
	private GuaranteedNode(Type type) {
	    this.type = type;

	    if (type == BuiltInTypes.BOOLEAN) {
		satisfiers = new ArrayList();
		satisfiers.add(Boolean.FALSE);
		satisfiers.add(Boolean.TRUE);
	    } else if (type.isBuiltIn()) {
		satisfiers = null; // set that we won't enumerate
	    } else {
		// user-defined type
		satisfiers = type.getGuaranteedObjects();
	    }
	}

	public Set getParents() {
	    return Collections.EMPTY_SET;
	}

	public boolean dependsOnIdOrder(EvalContext context) {
	    return false;
	}

	public ObjectIterator iterator(EvalContext context, 
				       Set externallyDistinguished, 
				       boolean returnPOPApps, 
				       Map desiredPOPParentObjs, 
				       Map otherPOPParentObjs, 
				       boolean includeGuaranteed) {
	    if (includeGuaranteed) {
		if (satisfiers == null) {
		    throw new UnsupportedOperationException
			("Can't iterate over objects of type " + type);
		}

		return new DefaultObjectIterator(satisfiers.iterator());
	    }
	    return (ObjectIterator) ObjectSet.EMPTY_OBJECT_SET.iterator();
	}

	public boolean isFinite() {
	    return (satisfiers != null);
	}

	public String toString() {
	    return ("Guaranteed " + type);
	}

	List satisfiers;
	Type type;
    }

    private Node getTypeNode(Type type, Term subject, List constraints) {
	List relevantCons = new ArrayList();

	// First do processing that applies regardless of type
	for (Iterator iter = constraints.iterator(); iter.hasNext(); ) {
	    Formula constraint = (Formula) iter.next();
	    
	    if (constraint instanceof EqualityFormula) {
		EqualityFormula equality = (EqualityFormula) constraint;

		// If constraint says subject is null, return null
		if (equality.assertsNull(subject)) {
		    return null;
		}

		// If constraint says subject equals a term that does not 
		// contain any free vars, then return TermNode for that term
		Term equalTerm = equality.getEqualTerm(subject);
		if ((equalTerm != null) && !containsFreeVar(equalTerm)) {
		    return new TermNode(equalTerm);
		}
	    }
	    
	    // If constraint mentions subject, add it to relevant 
	    // constraints list
	    if (constraint.containsTerm(subject)) {
		relevantCons.add(constraint);
	    }
	}
	
	// Now do type-specific processing

	if (type.isSubtypeOf(BuiltInTypes.INTEGER)
	        || (type == BuiltInTypes.TIMESTEP)) {
	    return getIntegerNode(subject, constraints, type);
	} 
	else if (type.isBuiltIn()) {
	    // Built-in type for which we don't recognize any constraints.
	    // None of the unprocessed constraints could be relevant.
	    Node typeNode = new GuaranteedNode(type);
	    internNode(typeNode, type);
	    return typeNode;
	}

	return getUserDefTypeNode(type, subject, constraints);
    }

    private Node getIntegerNode(Term subject, List constraints, 
				Type integerType) {
	IntegerNode node = (integerType == BuiltInTypes.TIMESTEP ? 
			    new TimestepNode() : new IntegerNode());

	for (Iterator iter = constraints.iterator(); iter.hasNext(); ) {
	    addBound((Formula) iter.next(), subject, node);
	}

	if (!node.isConstrained()) {
	    Node existing = (Node) unconstrainedNodes.get(integerType);
	    if (existing != null) {
		return existing;
	    }
	    internNode(node, integerType);
	}

	if (integerType == BuiltInTypes.NATURAL_NUM) {
	    node.addLowerBound(new FuncAppTerm(BuiltInFunctions.ZERO), false);
	} else if (integerType == BuiltInTypes.TIMESTEP) {
	    node.addLowerBound(new FuncAppTerm(BuiltInFunctions.EPOCH), false);
	}
			
	return node;
    }

    private Node getUserDefTypeNode(Type type, Term subject, 
				    List constraints) {
	// See what constraints are asserted about the values of generating 
	// functions on subject.  Here we find generating functions that 
	// are constrained to be non-null; generating functions that are 
	// constrained to be null will be discovered by getPOPNode 
	// (when it calls getTypeNode with the relevant gen func application 
	// as the subject).  
	Set nonNullGenFuncs = new HashSet();
	List originFuncConstraints = new ArrayList();
	for (Iterator iter = constraints.iterator(); iter.hasNext(); ) {
	    Formula constraint = (Formula) iter.next();
	    Set originFuncsApplied = constraint.getGenFuncsApplied(subject);
	    if (!originFuncsApplied.isEmpty()) {
		originFuncConstraints.add(constraint);
	    }
		
	    for (Iterator originFuncIter = originFuncsApplied.iterator(); 
		 originFuncIter.hasNext(); ) {
		OriginFunction g = (OriginFunction) originFuncIter.next();
		FuncAppTerm t = new FuncAppTerm
		    (g, Collections.singletonList(subject));
		if (impliesNonNull(constraint, t)) {
		    nonNullGenFuncs.add(g);
		}
	    }
	}

	// Before creating node, check to see if we're creating an 
	// unconstrained type node, and if so, whether one already exists 
	// for this type.  Even if the object generation graph is cyclic, 
	// the type nodes that we need will eventually be unconstrained, 
	// since constraints can only be finitely deep.  
	Type unconstrainedType = null;
	if (originFuncConstraints.isEmpty()) {
	    Node existing = (Node) unconstrainedNodes.get(type);
	    if (existing != null) {
		return existing;
	    }
	    unconstrainedType = type; // first visit to unconstrained type node
	} 

	// Create node
	OrNode typeNode = new OrNode();
	internNode(typeNode, unconstrainedType);
	
	// Create and add parents
	if (nonNullGenFuncs.isEmpty()) {
	    // Guaranteed objects of this type might satisfy constraints
	    typeNode.addParent(new GuaranteedNode(type));
	}

	for (Iterator iter = type.getPOPs().iterator(); iter.hasNext(); ) {
	    POP pop = (POP) iter.next();
	    OriginFunction[] popGenFuncs = pop.originFuncs();
	    if (Arrays.asList(popGenFuncs).containsAll(nonNullGenFuncs)) {
		// this POP uses all the non-null gen funcs
		Node popNode = getPOPNode(pop, subject, originFuncConstraints);

		// popNode could be null, e.g., if there is a
		// constraint saying one of its gen funcs is null
		if (popNode != null) {
		    typeNode.addParent(popNode);
		}
	    }
	}

	return typeNode;
    }

    private Node getPOPNode(POP pop, Term subject, List constraints) {
	List parents = new ArrayList();
	for (int i = 0; i < pop.originFuncs().length; ++i) {
	    OriginFunction g = pop.originFuncs()[i];
	    Term newSubject 
		= new FuncAppTerm(g, Collections.singletonList(subject));
	    Node typeNode = getTypeNode(g.getRetType(), newSubject, 
					constraints);
	    if (typeNode == null) {
		// constraints say newSubject couldn't be any object
		return null;
	    }
	    parents.add(typeNode);
	}
	return new POPNode(pop, parents);
    }

    /**
     * Adds a lower or upper bound on <code>subject</code> to the
     * given IntegerNode.
     */
    private void addBound(Formula phi, Term subject, IntegerNode node) {
	// See if phi is an atomic formula or the negation thereof
	AtomicFormula psi = null;
	boolean positive = true;
	if (phi instanceof AtomicFormula) {
	    psi = (AtomicFormula) phi;
	} else if ((phi instanceof NegFormula)
		   && (((NegFormula) phi).getNeg() 
		       instanceof AtomicFormula)) {
	    psi = (AtomicFormula) ((NegFormula) phi).getNeg();
	    positive = false;
	}
	if (psi == null) {
	    return; // not an atomic formula or negation thereof
	}
	
	// See if it uses one of the functions we recognize, and if one 
	// of the arguments is <code>subject</code> and the other does not 
	// contain <code>var</code>.
	ArgSpec psiTerm = psi.getTerm();
	Function f = (psiTerm instanceof FuncAppTerm) ?
	    ((FuncAppTerm) psiTerm).getFunction() : null;
	boolean isFirstArg = true;
	Term bound = null;
	if ((f == BuiltInFunctions.LT) 
	    || (f == BuiltInFunctions.LEQ)
	    || (f == BuiltInFunctions.GT) 
	    || (f == BuiltInFunctions.GEQ)) {
	    Term[] args = ((FuncAppTerm) psiTerm).getArgs();
	    if (subject.equals(args[0])) {
		bound = args[1];
	    } else if (subject.equals(args[1])) {
		isFirstArg = false;
		bound = args[0];
	    }
	}
	if ((bound == null) || containsFreeVar(bound)) {
	    return; // not a formula we can interpret
	}
	
	// Go through the logic of what's being asserted
	boolean strict = ((f == BuiltInFunctions.LT) 
			  || (f == BuiltInFunctions.GT));
	boolean upper = ((f == BuiltInFunctions.LT)
			 || (f == BuiltInFunctions.LEQ));
	if (!positive) {
	    upper = !upper;
	}
	if (!isFirstArg) {
	    upper = !upper;
	}
	
	if (upper) {
	    node.addUpperBound(bound, strict);
	} else {
	    node.addLowerBound(bound, strict);
	}
    }

    private boolean containsFreeVar(Term term) {
	return !Util.intersection(term.getFreeVars(), freeVars).isEmpty();
    }

    private void internNode(Node node, Type unconstrainedType) {
	if (unconstrainedType != null) {
	    unconstrainedNodes.put(unconstrainedType, node);
	}
    }
	
    private void recordAncestorGraph(Node v) {
	if (!nodes.contains(v)) {
	    nodes.add(v);

	    for (Iterator iter = v.getParents().iterator(); iter.hasNext(); ) {
		Node parent = (Node) iter.next();
		parent.addChild(v);
		recordAncestorGraph(parent);
	    }
	}
    }

    /**
     * Returns true if phi being true implies that t is not null.  Assumes 
     * phi is a literal.  
     */
    private static boolean impliesNonNull(Formula phi, Term t) {
	if (!phi.containsTerm(t)) {
	    return false;
	}

	if (phi instanceof AtomicFormula) {
	    // If phi is true then its top-level function returns true, 
	    // which means all its arguments are non-null
	    return true;
	}

	if (phi instanceof EqualityFormula) {
	    // Return true if one of the terms in the equality contains t, 
	    // and the other term is always non-null.  This assumes that 
	    // any function returns null when applied to a null argument --
	    // there's no special case that says Boolean functions return 
	    // false.  
	    EqualityFormula equality = (EqualityFormula) phi;
	    return ((equality.getTerm1().containsTerm(t) && 
		     isNonNullTerm(equality.getTerm2()))
		    || (equality.getTerm2().containsTerm(t) &&
			isNonNullTerm(equality.getTerm1())));
	}

	if (phi instanceof NegFormula) {
	    // Return true if phi is the negation of an equality formula 
	    // that asserts t is null
	    NegFormula negation = (NegFormula) phi;
	    return ((negation.getNeg() instanceof EqualityFormula) 
		    && ((EqualityFormula) negation.getNeg()).assertsNull(t));
	}

	return false;
    }

    /**
     * Returns true if t is a term that cannot evaluate to null,
     * namely a LogicalVar or a non-random ground term whose value
     * is not null.
     */
    private static boolean isNonNullTerm(Term t) {
	if (t instanceof LogicalVar) {
	    return true;
	}
	if (t.getFreeVars().isEmpty()) {
	    Object value = t.getValueIfNonRandom();
	    return ((value != null) && (value != Model.NULL));
	}
	return false;
    }

    private class SatisfierIterator extends AbstractObjectIterator {
	SatisfierIterator(EvalContext context, Set externallyDistinguished, 
			  boolean returnPOPApps) {
	    this.context = context;
	    this.externallyDistinguished = externallyDistinguished;
	    this.returnPOPAppsForTarget = returnPOPApps;

	    // For each POPNode, add its parents to the POP parent maps, 
	    // associated with empty lists
	    for (Iterator nodeIter = nodes.iterator(); nodeIter.hasNext(); ) {
		Node node = (Node) nodeIter.next();
		if (node instanceof POPNode) {
		    for (Iterator parentIter = node.getParents().iterator(); 
			 parentIter.hasNext(); ) {
			Node parent = (Node) parentIter.next();
			popParentCurRoundObjs.put(parent, new ArrayList());
			popParentPrevRoundObjs.put(parent, new ArrayList());
			popParentEarlierRoundObjs.put(parent, new ArrayList());
		    }
		}
	    }

	    targetIter = getNodeIterator(targetNode, returnPOPAppsForTarget);
	}

	protected int skipAfterNext() {
	    return targetIter.skipIndistinguishable();
	}

	protected Object findNext() {
	    // If the target iterator has no more elements, we need to
	    // finish this round and start a new one.  Do this
	    // repeatedly until the target node iterator has a next
	    // element, or until we complete a round where no objects
	    // are added for any POP parents.
	    while (!targetIter.hasNext()) {
		if (!targetIter.canDetermineNext()) {
		    canDetermineNext = false;
//		    System.out.println("Iter for target node " + targetNode 
//				       + " can't determine next.");
		    return null;
		}

		// Process all the POP parents in this round -- except 
		// the target node, whose curRoundObjs were collected over 
		// the course of this round.  
		// 
		// Note that within a round, the order in which nodes 
		// are processed doesn't matter.  
		for (Iterator iter = popParentCurRoundObjs.keySet()
			 .iterator(); iter.hasNext(); ) {
		    Node popParent = (Node) iter.next();
		    if (popParent != targetNode) {
			if (!processPOPParent(popParent)) {
			    return null;
			}
		    }
		}

		// Try to start new round.  
		if (!startNewRound()) {
		    return null;
		}

		// Don't include guaranteed objects in new iterators after 
		// the first round
		includeGuaranteed = false;

		// We will process the target node first in the new round
		targetIter = getNodeIterator(targetNode, 
					     returnPOPAppsForTarget);
	    }

	    // Continue processing the target node in the current round
	    Object newObj = targetIter.next();
	    if (popParentCurRoundObjs.containsKey(targetNode)) {
		((List) popParentCurRoundObjs.get(targetNode)).add(newObj);
	    }
	    return newObj;
	}

	private boolean startNewRound() {
	    // TODO: limit ourselves to parents of POPs that are still 
	    // active, i.e., POPs that don't have a permanently empty 
	    // parent.  A node is permanently empty if it has no 
	    // objects and none of its ancestors generated any objects 
	    // in the current round.  
	    
	    boolean haveNewObjs = false;
	    for (Iterator iter = popParentCurRoundObjs.entrySet().iterator(); 
		 iter.hasNext(); ) {
		Map.Entry entry = (Map.Entry) iter.next();
		Node popParent = (Node) entry.getKey();
		List curRoundObjs = (List) entry.getValue();
		if (!curRoundObjs.isEmpty()) {
		    haveNewObjs = true;
		}
		
		List prevRoundObjs 
		    = (List) popParentPrevRoundObjs.get(popParent);
		List earlierRoundObjs 
		    = (List) popParentEarlierRoundObjs.get(popParent);
		earlierRoundObjs.addAll(prevRoundObjs);
		
		prevRoundObjs.clear();
		prevRoundObjs.addAll(curRoundObjs);
		curRoundObjs.clear();
	    }

	    return haveNewObjs;
	}

	private boolean processPOPParent(Node node) {
	    List curRoundObjs = (List) popParentCurRoundObjs.get(node);
	    ObjectIterator iter = getNodeIterator(node, false);
	    if (node.isFinite()) {
		// Get all objects from iterator
		while (iter.hasNext()) {
		    curRoundObjs.add(iter.next());
		}
	    } else {
		// Get only one object from iterator
		if (iter.hasNext()) {
		    curRoundObjs.add(iter.next());
		}
	    }

	    if (!iter.canDetermineNext()) {
		canDetermineNext = false;
//		System.out.println("POP parent node " + node 
//				   + " can't determine next.");
		return false;
	    }
	    return true;
	}

	private ObjectIterator getNodeIterator(Node node, 
					       boolean returnPOPApps) {
	    if (rootIters.containsKey(node)) {
		return (ObjectIterator) rootIters.get(node);
	    }

	    ObjectIterator iter = node.iterator(context, 
						externallyDistinguished, 
						returnPOPApps, 
						popParentPrevRoundObjs, 
						popParentEarlierRoundObjs,
						includeGuaranteed);
	    if (node.getParents().isEmpty()) {
		rootIters.put(node, iter);
	    }
	    return iter;
	}

	EvalContext context;
	Set externallyDistinguished;
	boolean returnPOPAppsForTarget;

	boolean includeGuaranteed = true;
	Map popParentCurRoundObjs = new HashMap();     // from Node to List
	Map popParentPrevRoundObjs = new HashMap();    // from Node to List
	Map popParentEarlierRoundObjs = new HashMap(); // from Node to List
	Map rootIters = new HashMap(); // from Node to ObjectIterator

	ObjectIterator targetIter;
    }
    
    private Set nodes = new HashSet(); // of Node
    private Map unconstrainedNodes = new HashMap(); // from Type to Node
    
    private Node targetNode;

    private LogicalVar var;
    private Set freeVars;
}
