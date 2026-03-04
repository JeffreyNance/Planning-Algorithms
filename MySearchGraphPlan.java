package edu.uky.ai.planning.ex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import edu.uky.ai.SearchBudget;
import edu.uky.ai.planning.pg.*;

/**
 * A simple subgraph search that demonstrates how to use important features but
 * is very inefficient because it always chooses exactly one step at each level
 * of the plan graph and does not reason intelligently about which step to
 * choose.
 * 
 * @author Jeffrey E. Nance
*/
public class MySearchGraphPlan extends SubgraphSearch {

	// Help Pair Mutex Relations
	class Pair<T1, T2> {
		public T1 x;
		public T2 y;
		public Pair(T1 x, T2 y) {
			if (x.hashCode() <= y.hashCode()) {
				this.x = x;
				this.y = y;
			}
			else {
				this.x = (T1) y;
				this.y = (T2) x;
			}
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {return true;}
			if (!(object instanceof Pair<?, ?> pair)) {return false;}

			return Objects.equals(x, pair.x) && Objects.equals(y, pair.y);
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y);
		}
	}

	/** The plan graph for which a subgraph is needed */
	protected final PlanGraph graph;
	Map<Integer, HashSet<Pair<StepNode, StepNode>>> CompetingNeedsMutexes = new HashMap<>();
	Map<Integer, HashSet<Pair<LiteralNode, LiteralNode>>> InconsistentSupportMutexes = new HashMap<>();
	HashSet<Pair<HashSet<StepNode>, Integer>> NoGoods = new HashSet<>();

	static Subgraph CreateInitialSubgraph(PlanGraph graph) {
		//Add Goals to Graph First (Might Need Function)
		Subgraph InitialSubGraph = new Subgraph();
		for (LiteralNode goal : graph.goals) {
			InitialSubGraph = InitialSubGraph.add(goal, graph.size() - 1);
		}

		return InitialSubGraph;
	}

	/**
	 * Constructs and initializes a new search for a given plan graph.
	 * 
	 * @param graph the plan graph for which a subgraph is needed
	 * @param budget the budget that limits this search
	 */
	public MySearchGraphPlan(PlanGraph graph, SearchBudget budget) {
		super(graph, budget, CreateInitialSubgraph(graph));
		this.graph = graph;

		/*Add Mutexes For Every Level*/
		boolean MutexPairs;
		CompetingNeedsMutexes.putIfAbsent(0, new HashSet<>());
		InconsistentSupportMutexes.putIfAbsent(0, new HashSet<>());
		for (int level = 1; level < graph.size(); level++) {
			CompetingNeedsMutexes.putIfAbsent(level, new HashSet<>());
			InconsistentSupportMutexes.putIfAbsent(level, new HashSet<>());
			Level LevelNode = graph.getLevel(level);
			ArrayList<StepNode> steps = new ArrayList<>();
			for (StepNode step1 : LevelNode.steps) {
				steps.add(step1);
			}
			ArrayList<LiteralNode> literals = new ArrayList<>();
			for (LiteralNode literal1 : LevelNode.literals) {
				literals.add(literal1);
			}

			// Competing Needs (Actions Have at Least One Mutually Exclusive Precondition Pairing)
			for (int Intstep1 = 0; Intstep1 < steps.size(); Intstep1++) {
				for (int Intstep2 = Intstep1 + 1; Intstep2 < steps.size(); Intstep2++) {
					StepNode step1 = steps.get(Intstep1);
					StepNode step2 = steps.get(Intstep2);

					// Check The Preconditions of All The Steps For Mutual Exclusion
					MutexPairs = false;
					for (LiteralNode literal1 : step1.getPreconditions(level)) {
						for (LiteralNode literal2 : step2.getPreconditions(level)) {
							//Mutex When at Least One Literal Precondition Pairing is Mutex
							if (literal1.mutex(literal2, level - 1) || InconsistentSupportMutexes.get(level - 1).contains(new Pair<>(literal1, literal2))) {
								MutexPairs = true;
							}
							if (MutexPairs) {break;}
						}
						if (MutexPairs) {break;}
					}

					// Add The Competing Needs Mutex
					if (MutexPairs) {
						CompetingNeedsMutexes.get(level).add(new Pair<>(step1, step2));
					}
				}
			}

			// Inconsistent Support (Every Pair of Actions Achieving The Literals is Mutually Exclusive)
			for (int Intliteral1 = 0; Intliteral1 < literals.size(); Intliteral1++) {
				for (int Intliteral2 = Intliteral1 + 1; Intliteral2 < literals.size(); Intliteral2++) {
					LiteralNode literal1 = literals.get(Intliteral1);
					LiteralNode literal2 = literals.get(Intliteral2);

					// Check The Producers of All The Literals For Mutual Exclusion
					MutexPairs = true;
					for (StepNode step1 : literal1.getProducers(level)) {
						for (StepNode step2 : literal2.getProducers(level)) {
							//Not Mutex When at Least One Action Pairing is Not Mutex
							if (!step1.mutex(step2, level) && !CompetingNeedsMutexes.get(level).contains(new Pair<>(step1, step2))) {
								MutexPairs = false;
							}
							if (!MutexPairs) {break;}
						}
						if (!MutexPairs) {break;}
					}

					// Add The Inconsistent Support Mutex
					if (MutexPairs) {
						InconsistentSupportMutexes.get(level).add(new Pair<>(literal1, literal2));
					}
				}
			}
		}
	}

	@Override
	protected SubgraphSpaceNode findSubgraph() {
		// Start the search with the root node (an empty subgraph) and at the
		// highest level of the plan graph (which is the number of levels minus
		// 1, since the first level is level 0).
		return solve(root, new ArrayList<>((Collection<LiteralNode>) root.graph.goals), root.graph.size() - 1);
	}

	// Get All The Possible Sets of Actions at a Level
	public SubgraphSpaceNode RecursiveCombinations(SubgraphSpaceNode searchNode, ArrayList<LiteralNode> Goals, HashSet<StepNode> ValidSteps, int level, int Goal) {
		
		//Ensure a Valid Pairing
		SubgraphSpaceNode Result = null;
		Pair<HashSet<StepNode>, Integer> Pairing = new Pair<>(new HashSet<>(ValidSteps), level);
		boolean Good = !NoGoods.contains(Pairing);
		
		//Check Whether ValidSteps is a Solution of The Current Level's Goals
		if (Good && Goal < Goals.size()) {
			//Loop Through All Possible Producers of This Goal
			int NoGoodChildrenCount = 0;
			ArrayList<StepNode> Producers = new ArrayList<>();
			for (StepNode step1 : Goals.get(Goal).getProducers(level)) {
				//Combine This Step With The Valid Steps
				Producers.add(step1);
				HashSet<StepNode> NewValidSteps = new HashSet<>(ValidSteps);
				NewValidSteps.add(step1);
				Pair<HashSet<StepNode>, Integer> Pairing2 = new Pair<>(new HashSet<>(NewValidSteps), level);
				
				//Avoid Checking NoGood Sets of Solution Steps
				if (NoGoods.contains(Pairing2)) {NoGoodChildrenCount++; continue;}
				
				//Check if This Step Has no Same-Level Mutexes or Competing Needs Mutexes With The List of Valid Steps
				boolean MutexPairs = false;
				for (StepNode step2 : ValidSteps) {
					if (step1.mutex(step2, level) || CompetingNeedsMutexes.get(level).contains(new Pair<>(step1, step2))) {
						MutexPairs = true;
						break;
					}
				}
				if (MutexPairs) {NoGoods.add(Pairing2); NoGoodChildrenCount++; continue;}

				//Recurse to Search For The Next Goal When The Step is a Valid Addition
				Result = RecursiveCombinations(searchNode, Goals, NewValidSteps, level, Goal + 1);
				
				//Stop Checking For This Goal if The Solution is Found
				if (Result != null) {return Result;}
			}
			
			//When This Goal is Not Attainable by Any Action, This Set of ValidSteps is NoGood
			if (NoGoodChildrenCount == Producers.size()) {NoGoods.add(Pairing);}
		}
		
		/*Every Goal is Satisfied by This Set of Steps*/
		else if (Good) {
			
			//Find The New Goals For The Next Level According to The Valid Steps -> The Preconditions of All The Actions
			ArrayList<LiteralNode> NewGoals = new ArrayList<>();
			for (StepNode step1 : ValidSteps) {
				for (LiteralNode literal1 : step1.getPreconditions(level)) {
					//The Input's Next-Level Goals Will Never be Mutex
					NewGoals.add(literal1);

					//Check if This Step's Preconditions Have no Same-Level Mutexes or Inconsistent Support Mutexes With The New Goals of The Next Level
					for (LiteralNode literal2 : NewGoals) {
						if (literal1.mutex(literal2, level - 1) || InconsistentSupportMutexes.get(level - 1).contains(new Pair<>(literal1, literal2))) {
							NoGoods.add(Pairing);
							return Result;
						}
					}
				}
			}

			//Ensure Not All Non-Operations
			boolean AllPersist = true;
			for (StepNode step1 : ValidSteps) {
				if (!step1.persistence) {
					AllPersist = false;
					break;
				}
			}
			//Check if This Solution is NoGood
			if (AllPersist) {NoGoods.add(Pairing); return Result;}
			
			//Create The New SearchNode
			Subgraph NewSubgraph = searchNode.subgraph;
			for (StepNode step1 : ValidSteps) {
				NewSubgraph = NewSubgraph.add(step1, level);
			}

			//Move to The Next Level Because This Assignment Satisfies This Level
			Result = solve(searchNode.expand(NewSubgraph), NewGoals, level - 1);
			if (Result == null) {NoGoods.add(Pairing);}
		}

		return Result;
	}

	/**
	 * Searches for a subgraph of a plan graph that represents a solution plan
	 * for a problem being solved. This search chooses exactly one step at each
	 * level of the plan graph, but it does not reason intelligently about which
	 * step to choose.
	 * 
	 * @param searchNode the current subgraph
	 * @param level the level of the graph at which a step will be chosen
	 * @return a subgraph which includes step nodes representing the steps of a
	 * plan that is a solution to the problem
	 */
	private final SubgraphSpaceNode solve(SubgraphSpaceNode searchNode, ArrayList<LiteralNode> Goals, int level) {

		// Graph Has to be Solvable
		if (!graph.goalAchieved()) {
			return null;
		}

		// Base Case: The search has reached level 0, which has no steps.
		if(level == 0) {
			return searchNode;
		}

		/*
		Recursive Case: Choose a non-mutex set of steps at the given level and
		move to the next highest level.
		*/
		return RecursiveCombinations(searchNode, Goals, new HashSet<StepNode>(), level, 0);
	}
}
