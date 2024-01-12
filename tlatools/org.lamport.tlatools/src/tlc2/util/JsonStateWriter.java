package tlc2.util;

import tla2sany.explorer.ExploreNode;
import tla2sany.explorer.ExplorerVisitor;
import tla2sany.semantic.*;
import tla2sany.st.TreeNode;
import tlc2.TLCGlobals;
import tlc2.module.Json;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.Value;
import util.UniqueString;

import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Writes the given state in json format.
 */
public class JsonStateWriter extends StateWriter {
    // The Graphviz color scheme that is used for state transition edge colors. See
    // https://www.graphviz.org/doc/info/colors.html for more details on color schemes.
    private static final String dotColorScheme = "paired12";

    // A mapping of action names to their assigned color ids. Since states are fed
    // into a StateWriter incrementally, one at a time, this table is built up over
    // time, adding new actions as we find out about them.
    private final Map<String, Integer> actionToColors = new HashMap<>();

    // A mapping from ranks to nodes.
    private final Map<Integer, Set<Long>> rankToNodes = new HashMap<>();

    // Determines whether or not transition edges should be colorized in the state
    // graph.
    private final boolean colorize;

    // Determines whether or not transition edges should be labeled with their
    // action names.
    private final boolean actionLabels;

    // Used for assigning unique color identifiers to each action type. Incremented
    // by 1 every time a new color is assigned to an action.
    private Integer colorGen = 1;

    // Create a valid fname_snapshot.dot file after a state is written.
    private final boolean snapshot;

    // Include states in the dot file that are excluded from the model via a state or action constraint.
    private final boolean constrained;

    // Determines whether or not stuttering edges should be rendered.
    private final boolean stuttering;

    public JsonStateWriter() throws IOException {
        this("DotStateWriter.json", "", false, false, false, false, false);
    }

    public JsonStateWriter(final String fname, final String strict) throws IOException {
        this(fname, strict, false, false, false, false, false);
    }

    /**
     * @param fname
     * @param colorize     Colorize state transition edges in the DOT state graph.
     * @param actionLabels Label transition edges in the state graph with the name of the
     *                     associated action. Can potentially add a large amount of visual
     *                     clutter for large graphs with many actions.
     * @throws IOException
     */
    public JsonStateWriter(final String fname, final boolean colorize, final boolean actionLabels,
                           final boolean snapshot, final boolean constrained, final boolean stuttering) throws IOException {
        this(fname, "strict ", colorize, actionLabels, snapshot, constrained, stuttering);
    }

    public JsonStateWriter(final String fname, final String strict, final boolean colorize, final boolean actionLabels,
                           final boolean snapshot, final boolean constrained, final boolean stuttering) throws IOException {
        super(fname);
        // TODO here could be entry point for meta information at the beginning of file
        this.colorize = colorize;
        this.actionLabels = actionLabels;
        this.snapshot = snapshot;
        this.constrained = constrained;
        this.stuttering = stuttering;

        this.writer.append("{\"meta\": \"\", \"graph\": [\n");
    }

    /* (non-Javadoc)
     * @see tlc2.util.IStateWriter#isDot()
     */
    @Override
    public boolean isDot() {
        return true;
    }

    public boolean isConstrained() {
        return this.constrained;
    }

    /* initial state condition
     * (non-Javadoc)
     * @see tlc2.util.StateWriter#writeState(tlc2.tool.TLCState)
     */
    public synchronized void writeState(final TLCState state) {

        final String id = Long.toString(state.fingerPrint());
        final String vals = stateValsToJson(state);
        final String node = String.format("{\"id\": \"%s\", \"vars\":%s, \"$type\": \"init-node\"}", id, vals);
        this.writer.append(node);

        maintainRanks(state);

        if (snapshot) {
            try {
                this.snapshot();
            } catch (IOException e) {
                // Let\"s assume this never happens!
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        if (IdThread.getCurrentState() == null) return;
    }

    protected void maintainRanks(final TLCState state) {
        rankToNodes.computeIfAbsent(state.getLevel(), k -> new HashSet<Long>()).add(state.fingerPrint());
    }

    /* (non-Javadoc)
     * @see tlc2.util.StateWriter#writeState(tlc2.tool.TLCState, tlc2.tool.TLCState, boolean)
     */
    public void writeState(TLCState state, TLCState successor, short stateFlags) {
        writeState(state, successor, stateFlags, Visualization.DEFAULT);
    }

    public void writeState(final TLCState state, final TLCState successor, final short stateFlags, Action action) {
        writeState(state, successor, null, 0, 0, stateFlags, Visualization.DEFAULT, action);
    }

    /* (non-Javadoc)
     * @see tlc2.util.StateWriter#writeState(tlc2.tool.TLCState, tlc2.tool.TLCState, boolean, tlc2.util.IStateWriter.Visualization)
     */
    public void writeState(TLCState state, TLCState successor, short stateFlags, Visualization visualization) {
        writeState(state, successor, null, 0, 0, stateFlags, visualization, null);
    }

    /* (non-Javadoc)
     * @see tlc2.util.StateWriter#writeState(tlc2.tool.TLCState, tlc2.tool.TLCState, tlc2.util.BitVector, int, int, boolean)
     */
    public void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, short stateFlags) {
        writeState(state, successor, actionChecks, from, length, stateFlags, Visualization.DEFAULT, null);
    }

    /* (non-Javadoc)
     * @see tlc2.util.StateWriter#writeState(tlc2.tool.TLCState, tlc2.tool.TLCState, java.lang.String, boolean, tlc2.util.IStateWriter.Visualization)
     */
    private synchronized void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, short stateFlags,
                                         Visualization visualization, Action action) {
        if (!stuttering && visualization == Visualization.STUTTERING) {
            // Do not render stuttering transitions unless requested.
            return;
        }


        Map<String, IValue> writtenVars = new HashMap<>();

        String fromNode = Long.toString(state.fingerPrint());
        final String toNode = Long.toString(successor.fingerPrint());
        String label = action.getName().toString();

        String readsKeyValueJson = IdThread.getCurrentState().reads.toString();
        String writesKeyValueJson = IdThread.getCurrentState().writes.toString();
        /*
        System.out.println("++++++++++++++++++");
        System.out.println(label);
        System.out.println("READS:");
        System.out.println(readsKeyValueJson);
        System.out.println("WRITES:");
        System.out.println(writesKeyValueJson);
        System.out.println("++++++++++++++++++");
        */

        StringBuilder jsonBuilder = new StringBuilder("{");
        jsonBuilder.append("\"from\":\"").append(fromNode).append("\",");
        jsonBuilder.append("\"to\":\"").append(toNode).append("\",");
        jsonBuilder.append("\"label\":\"").append(label).append("\",");
        jsonBuilder.append("\"parameters\":\"").append(action.parameters).append("\",");
        jsonBuilder.append("\"$type\": \"edge").append("\",");
        jsonBuilder.append(readsKeyValueJson).append(",");
        jsonBuilder.append(writesKeyValueJson);

        jsonBuilder.append("}");

        this.writer.append(",\n").append(jsonBuilder);

        //String tree = action.getOpDef().toTreeString();
        //System.out.println(action.getOpDef().getName());
        //System.out.println(tree);

        // TODO can we obtain passed parameter?
        // TODO add diff of changed variables
        //final String edge = String.format("{\"from\":\"%s\", \"to\":\"%s\", \"label\":\"%s\", \"reads\":\"%s\", \"writes\":\"%s\", \"$type\": \"edge\"}", fromNode, toNode, label, reads.toString(), writes.toString());
        //this.writer.append(",\n").append(edge);


        if (!isSet(stateFlags, IStateWriter.IsSeen)) {
            // We save the successor state instead of the current node because the current node could
            // be an init state, which was already printed in the writeState(final TLCState state) call

            final String vals = stateValsToJson(successor);
            final String node = String.format("{\"id\": \"%s\", \"vars\":%s, \"$type\": \"node\"}", toNode, vals);
            this.writer.append(",\n").append(node);

            if (TLCGlobals.printDiffsOnly) {
                // TODO this could probably be used for var diffing
                // this.writer.append(states2dot(state.evalStateLevelAlias(), successor.evalStateLevelAlias()));
            }
            if (isSet(stateFlags, IStateWriter.IsNotInModel)) {
                // TODO when is this happening?
                // this.writer.append("\",style = filled, fillcolor=lightyellow]");
            }
        }


        maintainRanks(state);

        if (snapshot) {
            try {
                this.snapshot();
            } catch (IOException e) {
                // Let's assume this never happens!
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        IdThread.getCurrentState().clearReadsAndWrites();
    }

    private String varsMapToJson(Map<String, IValue> map) {
        StringBuilder keyValueBuilder = new StringBuilder();
        keyValueBuilder.append("{");
        for (Map.Entry<String, IValue> entry : map.entrySet()) {
            String value;
            try {
                if (entry.getValue() == null) {
                    value = "true";
                } else {
                    value = Json.toJson((Value) entry.getValue())
                            .toUnquotedString() // as string without \" at the beginning and end
                            .replace("\\", ""); // without escaped keys

                }
            } catch (IOException e) {
                System.out.println("WARNING: json couldn't be parsed for action");
                value = "\"\"";
            }

            keyValueBuilder.append("\"").append(entry.getKey()).append("\":")
                    .append(value).append(",");
        }
        keyValueBuilder.deleteCharAt(keyValueBuilder.length() - 1);
        keyValueBuilder.append("}");
        return keyValueBuilder.toString();
    }

    private String stateValsToJson(TLCState state) {
        final StringBuilder json = new StringBuilder();
        final Map<UniqueString, IValue> vals = state.evalStateLevelAlias().getVals();
        json.append("{");

        for (Map.Entry<UniqueString, IValue> entry : vals.entrySet()) {
            json.append("\"").append(entry.getKey().toString()).append("\"");
            json.append(":");

            try {

                final String escapedValue = Json.toJson(entry.getValue())
                        .toUnquotedString() // as string without \" at the beginning and end
                        .replace("\\", ""); // without escaped keys
                json.append(escapedValue);
            } catch (IOException e) {
                // this should never happen.
                System.out.println("Couldn't parse values to Json. Falling back to empty value");
                json.append("\"\"");
            }
            json.append(",");
        }
        // delete last \",\"
        json.deleteCharAt(json.length() - 1);
        json.append("}");

        return json.toString();
    }

    /**
     * Given an action, returns the associated color identifier for it. The color
     * identifier is just an integer suitable for use in a GraphViz color scheme. This
     * method updates the (action -> color) mapping if this action has not been seen
     * before for this DotStateWriter instance.
     *
     * @param action
     * @return the color identifier for the given action
     */
    protected Integer getActionColor(final Action action) {
        // Return a default color if the given action is null.
        if (action == null) {
            return 1;
        } else {
            String actionName = action.getName().toString();
            // If this action has been seen before, retrieve its color.
            if (actionToColors.containsKey(actionName)) {
                return actionToColors.get(actionName);
            }
            // If this action has not been seen yet, get the next available color
            // and assign it to this action.
            else {
                this.colorGen++;
                actionToColors.put(actionName, this.colorGen);
                return this.colorGen;
            }
        }
    }

    /**
     * Creates a DOT label for an edge representing a state transition.
     *
     * @param state     the current state of the transition
     * @param successor the next state of the transition
     * @param action    the action that induced the transition
     * @return the DOT label for the edge
     */
    protected String dotTransitionLabel(final TLCState state, final TLCState successor, final Action action) {
        // Only colorize edges if specified. Default to black otherwise.
        final String color = colorize ? this.getActionColor(action).toString() : "black";

        // Only add action label if specified.
        final String actionName = actionLabels ? action.getName().toString() : "";

        final String labelFmtStr = " [label=\"%s\",color=\"%s\",fontcolor=\"%s\"]";
        return String.format(labelFmtStr, actionName, color, color);
    }


    /**
     * Creates a DOT legend that maps actions to their corresponding edge color in the state graph.
     *
     * @param name    the title of the legend
     * @param actions the set of action names that will be included in the legend
     * @return
     */
    protected String dotLegend(final String name, final Set<String> actions) {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("subgraph %s {", "cluster_legend"));
        sb.append("graph[style=bold];");
        sb.append("label = \"Next State Actions\" style=\"solid\"\n");
        sb.append(String.format("node [ labeljust=\"l\",colorscheme=\"%s\",style=filled,shape=record ]\n",
                dotColorScheme));
        for (String action : actions) {
            String str = String.format("%s [label=\"%s\",fillcolor=%d]", action.replaceAll("!", ":"), action,
                    this.actionToColors.get(action));
            sb.append(str);
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Given a TLC state, generate a string representation suitable for a HTML DOT graph label.
     */
    //TODO This cannot handle states with variables such as "active = (0 :> TRUE @@ 1 :> FALSE)".
//	protected static String state2html(final TLCState state) {		
//		final StringBuilder sb = new StringBuilder();
//		final Map<UniqueString, Value> valMap = state.getVals();
//
//		// Generate a string representation of state.
//		for (UniqueString key : valMap.keySet()) {
//			final String valString = (key.toString() + " = " + valMap.get(key).toString());
//			sb.append(valString);
//			// New line between variables.
//			sb.append("<br/>");
//		}
//		return sb.toString();
//	}
    protected static String states2dot(final TLCState predecessor, final TLCState state) {
        // Replace "\" with "\\" and """ with "\"".
        return state.toString(predecessor).replace("\\", "\\\\").replace("\"", "\\\"").trim()
                .replace("\n", "\\n"); // Do not remove remaining (i.e. no danling/leading) "\n".
    }

    protected static String states2dot(final TLCState state) {
        // Replace "\" with "\\" and """ with "\"".
        return state.toString().replace("\\", "\\\\").replace("\"", "\\\"").trim()
                .replace("\n", "\\n"); // Do not remove remaining (i.e. no danling/leading) "\n".
    }

    /* (non-Javadoc)
     * @see tlc2.util.IStateWriter#close()
     */
    public void close() {
        this.writer.append("]}\n");
        super.close();
        return;
        // TODO do we need below?
		/*
		for (final Set<Long> entry : rankToNodes.values()) {
			this.writer.append("{rank = same; ");
			for (final Long l : entry) {
				this.writer.append(l + ";");
			}
			this.writer.append("}\n");
			this.writer.append("}\n");
		}
		this.writer.append("}\n"); // closes the main subgraph.
		// We only need the legend if the edges are colored by action and there is more
		// than a single action.
		if (colorize && this.actionToColors.size() > 1) {
			this.writer.append(dotLegend("DotLegend", this.actionToColors.keySet()));
		}
		this.writer.append("}");
		super.close();
		*/
    }

    /* (non-Javadoc)
     * @see tlc2.util.IStateWriter#snapshot()
     */
    @Override
    public void snapshot() throws IOException {
        return;
		/*
		this.writer.flush();
		
		final String snapshot = fname.replace(".dot", "_snapshot" + ".dot");
		FileUtil.copyFile(this.fname, snapshot);

		StringBuffer buf = new StringBuffer();
		for (final Set<Long> entry : rankToNodes.values()) {
			buf.append("{rank = same; ");
			for (final Long l : entry) {
				buf.append(l + ";");
			}
			buf.append("}\n");
		}
		buf.append("}\n");
		// We only need the legend if the edges are colored by action and there is more
		// than a single action.
		if (colorize && this.actionToColors.size() > 1) {
			buf.append(dotLegend("DotLegend", this.actionToColors.keySet()));
		}
		buf.append("}");

	    Files.write(Paths.get(snapshot), buf.toString().getBytes(), StandardOpenOption.APPEND);
		 */
    }
}
