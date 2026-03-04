package edu.uky.ai.planning.ex;

import edu.uky.ai.SearchBudget;
import edu.uky.ai.logic.Bindings;
import edu.uky.ai.logic.Literal;
import edu.uky.ai.planning.ForwardPlan;
import edu.uky.ai.planning.Operator;
import edu.uky.ai.planning.Plan;
import edu.uky.ai.planning.Problem;
import edu.uky.ai.logic.Negation;
import edu.uky.ai.planning.ps.*;
import edu.uky.ai.util.DirectedAcyclicGraph;
import edu.uky.ai.util.ImmutableList;
import edu.uky.ai.util.MinPriorityQueue;

/**
 * A simple plan-space search
 * 
 * @author Jeffrey E. Nance
 */
public class MySearchPOP extends PlanSpaceSearch {

	/** The minimum priority queue where search nodes will be stored */
	public MinPriorityQueue<PlanSpaceNode> minqueue = new MinPriorityQueue();

	/**
	 * Constructs and initializes a new search for a given problem.
	 * 
	 * @param problem the problem this search will solve
	 * @param budget the budget that limits this search
	 */
	public MySearchPOP(Problem problem, SearchBudget budget) {
		super(problem, budget);
		// Add the root node of the search to the queue.
		minqueue.push(root, 0);
	}

	@Override
	public Plan solve() {
		// Run until there are no more nodes left in the queue.
		while(!minqueue.isEmpty()) {
			// Pop the first node off the queue.
			PlanSpaceNode node = minqueue.pop();
			// If the plan has no flaws, it might be a solution, but that is not
			// guaranteed.
			if(node.flaws.size() == 0) {
				// Translate this partial plan into a fully-ground,
				// totally-ordered plan and check if it is a solution.
				return getGroundPlan(node);
			}
			// Otherwise, try to fix a single flaw in the list of flaws.
			else {
				//Find The Best Open Precondition Flaw to Fix
				OpenPreconditionFlaw openflaw = null;
				OpenPreconditionFlaw keepopenflaw = null;
				Bindings newBindings = null;
				PartialStep step = null;
				int MinPotentialBinds = Integer.MAX_VALUE;
				int PotentialBinds = 0;
				for (Flaw flaw : node.flaws) {
					if (flaw instanceof OpenPreconditionFlaw) {
						openflaw = (OpenPreconditionFlaw) flaw;

						PotentialBinds = 0;
						//Find All Current Steps Which Could Satisfy The Flaw
						for(PartialStep step1 : node.steps) {
							// Check whether this step has an effect which can unity with the
							// open precondition we are trying to fix.
							newBindings = findEffect(step1, openflaw.precondition, node.bindings);
							if(newBindings != null) {
								PotentialBinds += 1;
							}
						}
						//Find All Corresponding Operators Which Could Satisfy The Flaw
						for(Operator operator : problem.domain.operators) {
							// Make a new step out of the operator.
							step = new PartialStep(operator);
							// Check whether this step has an effect which can unity with the
							// open precondition we are trying to fix.
							newBindings = findEffect(step, openflaw.precondition, node.bindings);
							if(newBindings != null) {
								PotentialBinds += 1;
							}
						}

						//Keep The Open Flaw With Least Potential Binds
						if (PotentialBinds < MinPotentialBinds) {
							MinPotentialBinds = PotentialBinds;
							keepopenflaw = openflaw;
							if (MinPotentialBinds == 0) {break;} //Checking More is Useless
						}
					}
				}

				//Fix Open Precondition Flaws Which Can be Resolved in One Way
				boolean flawfound = false;
				if (MinPotentialBinds == 1) {
					fix(node, keepopenflaw);
				}

				//Fix Definite Threatened Causal Link Flaws, LIFO
				else if (MinPotentialBinds != 0) {
					for (Flaw flaw2 : node.flaws) {
						if (flaw2 instanceof ThreatenedCausalLinkFlaw) {
							ThreatenedCausalLinkFlaw threatflaw = (ThreatenedCausalLinkFlaw) flaw2;
							for (Literal effect : threatflaw.threat.effects) {
								if (keepopenflaw == null || effect.unify(threatflaw.link.label.negate(), node.bindings) == node.bindings) {
									flawfound = true;
									fixthreat(node, threatflaw);
									break;
								}
							}
							if (flawfound == true) {break;}
						}
					}
				}

				//Ignore Open Precondition Flaws Which Can be Resolved in no Ways
				//Fix Open Precondition Flaws, LIFO
				if (!flawfound && MinPotentialBinds > 1) {
					fix(node, keepopenflaw);
				}
			}
		}
		// If the queue is empty, fail.
		return null;
	}

	/**
	 * A {@link PlanSpaceNode} represents a partial plan (its steps, bindings,
	 * orderings, causal links, and flaws); this method converts that partial
	 * plan into a fully-ground, totally-ordered plan that can be tested as a
	 * solution to the problem.
	 * 
	 * @param node the search node representing a partial plan
	 * @return a fully-ground, totally-ordered plan
	 */
	private Plan getGroundPlan(PlanSpaceNode node) {
		ForwardPlan plan = new ForwardPlan();
		for(PartialStep step : node.orderings)
			if(step.operator != null)
				plan = plan.addStep(step.makeStep(node.bindings));
		return plan;
	}

	/**
	 * Fixes a given {@link ThreatenedCausalLinkFlaw} in a partial plan.
	 * 
	 * @param node the partial plan whose flaw will be fixed
	 * @param flaw the threatened causal link flaw to fix
	 */
	private void fixthreat(PlanSpaceNode node, ThreatenedCausalLinkFlaw flaw) {
		// Option 1: Promote the threatening step before the tail
		DirectedAcyclicGraph<PartialStep> promotedOrderings = node.orderings.add(flaw.threat, flaw.link.tail);

		// Option 2: Demote the threatening step after the head
		DirectedAcyclicGraph<PartialStep> demotedOrderings = node.orderings.add(flaw.link.head, flaw.threat);

		// Remove This Flaw
		ImmutableList<Flaw> newFlaws = node.flaws.remove(flaw);

		// Get The Heuristic Number of Open Conditions & Threat Conditions
		int OpenConditions = 0;
		double ThreatConditions = 0;
		for (Flaw flaw2 : newFlaws) {
			if (flaw2 instanceof ThreatenedCausalLinkFlaw) {
				ThreatConditions += 1;
			}
			else {
				OpenConditions += 1;
			}
		}
		ThreatConditions /= 10; //Division For Heuristic

		// Expand the child plan and push it onto the search queue.
		if (promotedOrderings != null) {
			minqueue.push(node.expand(node.steps, node.bindings, promotedOrderings, node.causalLinks, newFlaws), node.steps.size() + OpenConditions + ThreatConditions);
		}
		if (demotedOrderings != null) {
			minqueue.push(node.expand(node.steps, node.bindings, demotedOrderings, node.causalLinks, newFlaws), node.steps.size() + OpenConditions + ThreatConditions);
		}
	}

	/**
	 * Fixes a given {@link OpenPreconditionFlaw} in a partial plan.
	 * 
	 * @param node the partial plan whose flaw will be fixed
	 * @param flaw the open precondition flaw to fix
	 */
	private void fix(PlanSpaceNode node, OpenPreconditionFlaw flaw) {
		// Option 1: Fix this flaw using a causal link from an existing step
		Bindings newBindings = null;
		for (PartialStep step1 : node.steps) {
			// Check whether this step has an effect which can unity with the
			// open precondition we are trying to fix.
			newBindings = findEffect(step1, flaw.precondition, node.bindings);
			if(newBindings != null) {
				fix(node, flaw, step1, newBindings);
			}
		}

		// Option 2: Fix this flaw using a causal link from a new step that will
		// be added to the plan.
		// Loop through every operator in the problem...
		for(Operator operator : problem.domain.operators) {
			// Make a new step out of the operator.
			PartialStep step = new PartialStep(operator);
			// Check whether this step has an effect which can unity with the
			// open precondition we are trying to fix.
			newBindings = findEffect(step, flaw.precondition, node.bindings);
			if(newBindings != null) {
				fix(node, flaw, step, newBindings);
			}
		}
	}

	/**
	 * Check whether a given step has an effect which can unity with a given
	 * precondition. If it does, return the bindings that make them unify.
	 * 
	 * @param step the step which may have the needed effect
	 * @param precondition the precondition the step's effect must unity with
	 * @param bindings the partial plan's current bindings
	 * @return the new bindings needed to make the step's effect unify with the
	 * precondition, or null if the step does not have an effect which can unify
	 * with the precondition
	 */
	private Bindings findEffect(PartialStep step, Literal precondition, Bindings bindings) {

		// Check every effect of the step.
		Bindings newBindings = null;
		for(Literal effect : step.effects) {
			// Unify the effect and the precondition and add the new bindings to
			// the current bindings.
			newBindings = precondition.unify(effect, bindings);
			
			// The unify method returns null if there is no way to make the
			// precondition and effect unify. If there way a way, it returned
			// the new bindings, so return those to indicate success.
			if(newBindings != null) {
				return newBindings;
			}
		}

		//When This is The Start Step, Try to Unify Negated Literals Which Are Not in The Start's Effects to a New Binding
		if (step.operator == null && step.preconditions.size() == 0 && precondition instanceof Negation && !step.effects.contains(precondition.negate())) {
			return bindings;
		}

		// If no effect will work, return null.
		return null;
	}

	/**
	 * Generate the child of a partial plan that fixes a given open precondition
	 * flaw using a given step. This method can be called from two different
	 * contexts (see {@link #fix(PlanSpaceNode, OpenPreconditionFlaw)}). If the
	 * given step is the dummy start step, the step will not need to be added to
	 * the plan because it is already in the list of steps. If the given step is
	 * a new step, it will be added to the plan. In either case, the step will
	 * be ordered before the step whose flaw is being fixed and a causal link
	 * will be drawn from the step to the step whose flaw is being fixed.
	 * 
	 * @param node the partial plan for whom a child will be expanded
	 * @param flaw the open precondition flaw being fixed by the given step
	 * @param step the step being used to fix the flaw
	 * @param bindings the new bindings to use in the child plan
	 */
	private void fix(PlanSpaceNode node, OpenPreconditionFlaw flaw, PartialStep step, Bindings bindings) {
		// Add the step to the list of steps. If the dummy start step is being
		// used, it will already be in the list, so don't add it again.
		ImmutableList<PartialStep> newSteps = node.steps;
		if(!newSteps.contains(step))
			newSteps = newSteps.add(step);

		// Order the step so that it comes before the step whose flaw is being
		// fixed. If the dummy start step is being used, this ordering will
		// already be in the ordering graph, but it doesn't hurt to re-add it.
		DirectedAcyclicGraph<PartialStep> newOrderings = null;
		if (node.orderings != null) {
			newOrderings = node.orderings.add(step, flaw.step);
		}

		if (newOrderings != null) {
			// Draw a causal link from the step to the step whose flaw is being
			// fixed. The label of the causal link will be the open precondition
			// being fixed (which will be the same as one of the effects of the
			// step).
			CausalLink newLink = new CausalLink(step, flaw.precondition, flaw.step);
			ImmutableList<CausalLink> newCausalLinks = node.causalLinks.add(newLink);

			// Remove the flaw being fixed from the list of flaws
			ImmutableList<Flaw> newFlaws = node.flaws.remove(flaw);
			if (!node.steps.contains(step)) {
				// Add a new open precondition flaw for every precondition of the new
				// step. If the dummy start step is being used, it has no preconditions,
				// so no new flaws will be added.
				for(Literal precondition : step.preconditions) {
					newFlaws = newFlaws.add(new OpenPreconditionFlaw(step, precondition));
				}

				// Add potential threat flaws created by this new step
				for (CausalLink link: newCausalLinks) {
					if (newOrderings.path(step, link.tail) || newOrderings.path(link.head, step)) {
						continue;
					}
					for (Literal effect : step.effects) {
						if (effect.unify(link.label.negate(), bindings) != null) {
							newFlaws = newFlaws.add(new ThreatenedCausalLinkFlaw(link, step));
						}
					}
				}
			}

			// Add potential threat flaws to this causal link created by any existing step
			for (PartialStep step1 : node.steps) {
				if (newOrderings.path(step1, newLink.tail) || newOrderings.path(newLink.head, step1)) {
					continue;
				}
				for (Literal effect1 : step1.effects) {
					if (effect1.unify(newLink.label.negate(), bindings) != null) {
						newFlaws = newFlaws.add(new ThreatenedCausalLinkFlaw(newLink, step1));
					}
				}
			}

			//Get The Heuristic Number of Open Conditions & Threat Conditions
			int OpenConditions = 0;
			double ThreatConditions = 0;
			for (Flaw flaw2 : newFlaws) {
				if (flaw2 instanceof ThreatenedCausalLinkFlaw) {
					ThreatConditions += 1;
				}
				else {
					OpenConditions += 1;
				}
			}
			ThreatConditions /= 10; //Division For Heuristic

			// Expand the child plan and push it onto the search queue.
			minqueue.push(node.expand(newSteps, bindings, newOrderings, newCausalLinks, newFlaws), newSteps.size() + OpenConditions + ThreatConditions);
		}
	}
}