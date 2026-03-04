package edu.uky.ai.planning.ex;

import edu.uky.ai.SearchBudget;
import edu.uky.ai.planning.Problem;
import edu.uky.ai.planning.ps.PlanSpacePlanner;
import edu.uky.ai.planning.ps.PlanSpaceSearch;

/**
 * A simple plan-space planner
 * 
 * @author Jeffrey E. Nance
 */
public class MyPlanSpacePlanner extends PlanSpacePlanner {

	/**
	 * Constructs a new plan-space planner with my UKID.
	 */
	public MyPlanSpacePlanner() {
		super("jena227-pop");
	}

	@Override
	protected PlanSpaceSearch makeSearch(Problem problem, SearchBudget budget) {
		// Create and return a search object for the given problem using the
		// given search budget.
		return new MySearchPOP(problem, budget);
	}
}