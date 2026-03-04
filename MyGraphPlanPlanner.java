package edu.uky.ai.planning.ex;

import edu.uky.ai.SearchBudget;
import edu.uky.ai.planning.pg.PlanGraph;
import edu.uky.ai.planning.pg.PlanGraphPlanner;
import edu.uky.ai.planning.pg.SubgraphSearch;

/**
 * A simple subgraph planner that demonstrates how to use important features but
 * is not very efficient.
 * 
 * @author Jeffrey E. Nance
 */
public class MyGraphPlanPlanner extends PlanGraphPlanner {

	/**
	 * Constructs a new example subgraph planner with the default name.
	 */
	public MyGraphPlanPlanner() {
		super("jena227-graph");
	}

	@Override
	protected SubgraphSearch makeSearch(PlanGraph graph, SearchBudget budget) {
		// Create and return a search object for the given plan graph using the
		// given search budget. Note that this method may be called multiple
		// times for the same problem. The first time this method is called, the
		// plan graph will have the minimum number of levels needed before the
		// problem's goal appears. If that search fails, this method will be
		// called again, but with 1 new level added to the plan graph. If that
		// fails, this method will be called again with 1 new level added to the
		// graph, and so on.
		return new MySearchGraphPlan(graph, budget);
	}
}