package org.tzi.use.kodkod.transform.ocl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import kodkod.ast.Node;
import kodkod.ast.Variable;

import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IAssociationEnd;
import org.tzi.kodkod.model.iface.IAttribute;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.TypeLiterals;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MNavigableElement;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.expr.ExpAny;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpNavigation;
import org.tzi.use.uml.ocl.expr.ExpObjOp;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;

/**
 * Extension of DefaultExpressionVisitor to visit the variable operations of an
 * expression.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class VariableOperationVisitor extends DefaultExpressionVisitor {

	private IClass attributeClass;
	
	private Object object;
	private boolean set;
	private boolean object_type_nav;

	public VariableOperationVisitor(IModel model, Map<String, Node> variables, Map<String, IClass> variableClasses,
			Map<String, Variable> replaceVariables, List<String> collectionVariables, Stack<OclTransformationContext> contextStack) {
		super(model, variables, variableClasses, replaceVariables, collectionVariables, contextStack);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * <b>Note:</b> This method is only reachable from inside the
	 * {@code VariableOperationVisitor}. Other {@code any} expressions are
	 * handled by {@link QueryExpressionVisitor#visitAny(ExpAny)}.
	 */
	@Override
	public void visitAny(ExpAny exp) {
		super.visitAny(exp);
		Type type = exp.getVariableDeclarations().varDecl(0).type();
		if(type.isTypeOfClass()){
			attributeClass = model.getClass(((MClass)type).name());
		}
	}
	
	@Override
	public void visitAttrOp(ExpAttrOp exp) {
		subExpression(exp.objExp());
		
		IAttribute attribute = attributeClass.getAttribute(exp.attr().name());
		
		if (attribute != null) {
			boolean set = attribute.type().isSet();

			List<Object> arguments = new ArrayList<Object>();
			arguments.add(object);
			arguments.add(attribute.relation());
			arguments.add(set);
			
			// update current type
			if(attribute.type().isObjectType()){
				org.tzi.kodkod.model.type.ObjectType t = (org.tzi.kodkod.model.type.ObjectType) attribute.type();
				attributeClass = t.clazz();
			}
			else {
				attributeClass = null;
			}
			
			invokeMethod("access", arguments, false, object_type_nav);
		} else {
			throw new TransformationException("Cannot find attribute " + exp.attr().name() + ".");
		}
	}

	@Override
	public void visitNavigation(ExpNavigation exp) {
		subExpression(exp.getObjectExpression());
		
		MNavigableElement source = exp.getSource();
		MNavigableElement destination = exp.getDestination();
		MAssociation mAssociation = source.association();

		IAssociation association = model.getAssociation(mAssociation.name());

		if (association != null) {
			boolean isAssociationClass = association.associationClass() != null;

			int fromRole = findAssociationEndIndex(source, association, false);
			int toRole = findAssociationEndIndex(destination, association, true);

			if (fromRole == -1) {
				fromRole = associationClassEnd(source, association, fromRole, true);
			} else if (toRole == -1) {
				toRole = associationClassEnd(destination, association, fromRole, false);
			}

			object_type_nav = !set;

			if (fromRole == -1 || toRole == -1 || fromRole == toRole) {
				throw new TransformationException("Cannot find correct associationEnd indexes for navigation from " + source.nameAsRolename()
						+ " to " + destination.nameAsRolename() + ".");
			}

			List<Object> arguments = new ArrayList<Object>();
			arguments.add(object);
			arguments.add(association.relation());
			arguments.add(fromRole);
			arguments.add(toRole);
			arguments.add(isAssociationClass);
			arguments.add(object_type_nav);

			invokeMethod("navigation", arguments, false, object_type_nav);
		} else {
			throw new TransformationException("Cannot find association " + mAssociation.name() + ".");
		}
	}

	/**
	 * Handle the navigation to an association class end.
	 * 
	 * @param source
	 * @param association
	 * @param fromIndex
	 * @param fromRole
	 * @return
	 */
	private int associationClassEnd(MNavigableElement source, IAssociation association, int fromIndex, boolean fromRole) {
		if (source.nameAsRolename().equalsIgnoreCase(association.name())) {
			if (fromRole) {
				set = false;
			} else {
				if (!association.isBinaryAssociation()) {
					set = true;
				} else {
					int toIndex = fromIndex == 1 ? 1 : 0;
					IAssociationEnd associationEnd = association.associationEnds().get(toIndex);
					set = !associationEnd.multiplicity().isObjectTypeEnd();
				}
				attributeClass = association.associationClass();
			}
			return 0;
		}
		return -1;
	}

	/**
	 * Search the index of the association end.
	 * 
	 * @param source
	 * @param association
	 * @param toRole
	 * @return
	 */
	private int findAssociationEndIndex(MNavigableElement source, IAssociation association, boolean toRole) {
		List<IAssociationEnd> associationEnds = association.associationEnds();
		IAssociationEnd associationEnd;
		for (int i = 0; i < associationEnds.size(); i++) {
			associationEnd = associationEnds.get(i);
			if (associationEnd.name().equals(source.nameAsRolename())) {
				if (toRole) {
					if (!association.isBinaryAssociation()) {
						set = true;
					} else {
						set = !associationEnd.multiplicity().isObjectTypeEnd();
					}
					attributeClass = associationEnd.associatedClass();
				}

				return i + 1;
			}
		}
		return -1;
	}

	@Override
	public void visitVariable(ExpVariable exp) {
		
		/*
		 * approach due to the description in the method createVariables in the
		 * class QueryExpressionVisitor. For this replacement it is necessary to
		 * use check the replaceVariables before the normal variables.
		 */
		if (replaceVariables.containsKey(exp.getVarname())) {
			object = replaceVariables.get(exp.getVarname());
			getAttributeClass(replaceVariables.get(exp.getVarname()).name());

		} else if (variables.containsKey(exp.getVarname())) {
			object = variables.get(exp.getVarname());
			if (collectionVariables.contains(exp.getVarname())) {
				set = true;
			}
			getAttributeClass(exp.getVarname());
		} else if (exp.type().isTypeOfClass()) {
			IClass clazz = model.getClass(exp.type().shortName());
			TypeLiterals type = clazz.objectType();
			type.addTypeLiteral(exp.getVarname());
			object = type.getTypeLiteral(exp.getVarname());
			attributeClass = clazz;
		} else {
			throw new TransformationException("No variable " + exp.getVarname() + ".");
		}
	}

	@Override
	public void visitObjOp(ExpObjOp exp) {
		super.visitObjOp(exp);
		MOperation operation = exp.getOperation();
		if (operation.hasResultType() && operation.resultType().isTypeOfClass()) {
			attributeClass = model.getClass(((MClass) operation.resultType()).name());
		}
	}

	/**
	 * Translates the stack usage of the {@link DefaultExpressionVisitor} to the
	 * field usage of this visitor.
	 * 
	 * FIXME change this visitor to use the stack properly
	 * a problem are the many methods that use class attributes to interact with on another
	 */
	private void subExpression(Expression e){
		int stackSize = stack.size();
		e.processWithVisitor(this);
		if(stack.size() > stackSize){
			OclTransformationContext ctx = stack.pop();
			object = ctx.object;
			set = ctx.set;
			object_type_nav = ctx.object_type_nav;
		}
	}
	
	private void getAttributeClass(String varName) {
		if (variableClasses.containsKey(varName))
			attributeClass = variableClasses.get(varName);
	}

	public IClass getAttributeClass() {
		return attributeClass;
	}
}
