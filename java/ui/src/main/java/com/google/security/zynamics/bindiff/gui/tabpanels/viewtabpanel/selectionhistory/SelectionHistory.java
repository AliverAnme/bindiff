// Copyright 2011-2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.selectionhistory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.FluentLogger;
import com.google.security.zynamics.bindiff.enums.EGraphType;
import com.google.security.zynamics.bindiff.enums.ESide;
import com.google.security.zynamics.bindiff.graph.BinDiffGraph;
import com.google.security.zynamics.bindiff.graph.CombinedGraph;
import com.google.security.zynamics.bindiff.graph.SingleGraph;
import com.google.security.zynamics.bindiff.graph.filter.GraphNodeFilter;
import com.google.security.zynamics.bindiff.graph.filter.GraphNodeFilter.Criterion;
import com.google.security.zynamics.bindiff.graph.nodes.CombinedDiffNode;
import com.google.security.zynamics.bindiff.graph.nodes.SingleDiffNode;
import com.google.security.zynamics.bindiff.project.matches.IMatchesChangeListener;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawCombinedBasicBlock;
import com.google.security.zynamics.zylib.disassembly.IAddress;
import com.google.security.zynamics.zylib.general.ListenerProvider;
import com.google.security.zynamics.zylib.yfileswrap.gui.zygraph.AbstractZyGraph;
import com.google.security.zynamics.zylib.yfileswrap.gui.zygraph.nodes.ZyGraphNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SelectionHistory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final List<SelectionSnapshot> snapshotList = new ArrayList<>();

  private final ListenerProvider<ISelectionHistoryListener> listeners = new ListenerProvider<>();

  private final InternalMatchedChangedListener matchChangeListener =
      new InternalMatchedChangedListener();

  private SingleGraph singleGraph;

  private CombinedGraph combinedGraph;

  private final int maxSnapshots;

  private int undoIndex = -1;

  private boolean freeze = false;

  public SelectionHistory(final AbstractZyGraph<?, ?> graph, final int maxUndoLevel) {
    checkNotNull(graph);
    checkArgument(maxUndoLevel > 0, "Invalid undo level");

    if (graph instanceof SingleGraph) {
      singleGraph = (SingleGraph) graph;
      combinedGraph = null;
    } else if (graph instanceof CombinedGraph) {
      combinedGraph = (CombinedGraph) graph;

      singleGraph = null;
    } else {
      throw new IllegalArgumentException(
          "Graph must be an instance of SingleGraph or CombinedGraph.");
    }

    maxSnapshots = maxUndoLevel;

    // add first no selection snapshot
    addSnapshot(new SelectionSnapshot(new ArrayList<>()));
  }

  public void addHistoryListener(final ISelectionHistoryListener listener) {
    listeners.addListener(listener);
  }

  public void addSnapshot(final SelectionSnapshot snapshot) {
    if (freeze) {
      return;
    }

    snapshotList.add(snapshot);

    if (snapshotList.size() > maxSnapshots) {
      snapshotList.remove(0);

      for (final ISelectionHistoryListener listener : listeners) {
        listener.snapshotRemoved();
      }
    }

    undoIndex = getNumberOfSnapshots() - 1;

    for (final ISelectionHistoryListener listener : listeners) {
      listener.snapshotAdded(snapshot);
    }
  }

  public boolean canRedo() {
    return undoIndex <= getNumberOfSnapshots() - 1;
  }

  public boolean canUndo() {
    return undoIndex >= 0;
  }

  public void dispose() {
    for (final ISelectionHistoryListener listener : listeners) {
      removeHistoryListener(listener);
    }

    snapshotList.clear();

    if (singleGraph != null) {
      singleGraph.getGraphs().getDiff().getMatches().removeListener(matchChangeListener);
    }
    if (combinedGraph != null) {
      combinedGraph.getGraphs().getDiff().getMatches().removeListener(matchChangeListener);
    }

    singleGraph = null;
    combinedGraph = null;
  }

  public int getNumberOfSnapshots() {
    return snapshotList.size();
  }

  public SelectionSnapshot getSnapshot(final boolean undo) {
    if (undo) {
      if (undoIndex != 0) {
        undoIndex--;
      }

      return getSnapshot(undoIndex);
    } else {
      if (undoIndex != getNumberOfSnapshots() - 1) {
        undoIndex++;
      }

      return getSnapshot(undoIndex);
    }
  }

  public SelectionSnapshot getSnapshot(final int index) {
    return snapshotList.get(index);
  }

  public void redo() {
    for (final ISelectionHistoryListener listener : listeners) {
      try {
        listener.startedRedo();
      } catch (final Exception e) {
        logger.atSevere().withCause(e).log("Selection history listener notification failed");
      }
    }

    if (!snapshotList.isEmpty() && canRedo()) {
      if (combinedGraph == null) {
        final List<SingleDiffNode> nodesToUnselect =
            GraphNodeFilter.filterNodes(singleGraph, Criterion.SELECTED);
        singleGraph.selectNodes(nodesToUnselect, false);

        final Collection<SingleDiffNode> nodesToSelect =
            getSnapshot(false).getSingleGraphSelection();
        singleGraph.selectNodes(nodesToSelect, true);
      } else {
        final List<CombinedDiffNode> nodesToUnselect =
            GraphNodeFilter.filterNodes(combinedGraph, Criterion.SELECTED);
        combinedGraph.selectNodes(nodesToUnselect, false);

        final Collection<CombinedDiffNode> nodesToSelect =
            getSnapshot(false).getCombinedGraphSelection();
        combinedGraph.selectNodes(nodesToSelect, true);
      }
    }

    for (final ISelectionHistoryListener listener : listeners) {
      try {
        listener.finishedRedo();
      } catch (final Exception e) {
        logger.atSevere().withCause(e).log("Selection history listener notification failed");
      }
    }
  }

  public void registerMatchListener() {
    if (singleGraph != null) {
      singleGraph.getGraphs().getDiff().getMatches().addListener(matchChangeListener);
    } else {
      combinedGraph.getGraphs().getDiff().getMatches().addListener(matchChangeListener);
    }
  }

  public void removeHistoryListener(final ISelectionHistoryListener listener) {
    try {
      listeners.removeListener(listener);
    } catch (final Exception e) {
      logger.atWarning().log("Listener was not listening.");
    }
  }

  public void setEnabled(final boolean enable) {
    freeze = !enable;
  }

  public void undo() {
    for (final ISelectionHistoryListener listener : listeners) {
      try {
        listener.startedUndo();
      } catch (final Exception e) {
        logger.atSevere().withCause(e).log("Selection history listener notification failed");
      }
    }

    if (!snapshotList.isEmpty() && canUndo()) {
      if (combinedGraph == null) {
        final List<SingleDiffNode> nodesToUnselect =
            GraphNodeFilter.filterNodes(singleGraph, Criterion.SELECTED);
        singleGraph.selectNodes(nodesToUnselect, false);

        final Collection<SingleDiffNode> nodesToSelect =
            getSnapshot(true).getSingleGraphSelection();
        singleGraph.selectNodes(nodesToSelect, true);
      } else {
        final List<CombinedDiffNode> nodesToUnselect =
            GraphNodeFilter.filterNodes(combinedGraph, Criterion.SELECTED);
        combinedGraph.selectNodes(nodesToUnselect, false);

        final Collection<CombinedDiffNode> nodesToSelect =
            getSnapshot(true).getCombinedGraphSelection();
        combinedGraph.selectNodes(nodesToSelect, true);
      }
    }

    for (final ISelectionHistoryListener listener : listeners) {
      try {
        listener.finishedUndo();
      } catch (final Exception e) {
        logger.atSevere().withCause(e).log("Selection history listener notification failed");
      }
    }
  }

  private class InternalMatchedChangedListener implements IMatchesChangeListener {
    private BinDiffGraph<? extends ZyGraphNode<?>, ?> getGraph() {
      return singleGraph != null ? singleGraph : combinedGraph;
    }

    private SingleDiffNode getNewDiffNode(final IAddress basicblockAddr) {
      for (final SingleDiffNode diffNode : singleGraph.getNodes()) {
        if (diffNode.getRawNode().getAddress().equals(basicblockAddr)) {
          return diffNode;
        }
      }

      return null;
    }

    private CombinedDiffNode getNewDiffNode(final IAddress basicblockAddr, final ESide side) {
      for (final CombinedDiffNode diffNode : combinedGraph.getNodes()) {
        final RawCombinedBasicBlock combinedBasicBlock =
            (RawCombinedBasicBlock) diffNode.getRawNode();

        if (basicblockAddr.equals(combinedBasicBlock.getAddress(side))) {
          return diffNode;
        }
      }

      return null;
    }

    private void refreshSnapshots(
        final CombinedDiffNode newPriUnmatchedDiffNode,
        final CombinedDiffNode newSecUnmatchedDiffNode) {
      checkNotNull(newPriUnmatchedDiffNode);
      checkNotNull(newSecUnmatchedDiffNode);

      final IAddress priAddress = newPriUnmatchedDiffNode.getRawNode().getAddress(ESide.PRIMARY);
      final IAddress secAddress = newSecUnmatchedDiffNode.getRawNode().getAddress(ESide.SECONDARY);

      for (final SelectionSnapshot snapshot : snapshotList) {
        for (final CombinedDiffNode diffNode : snapshot.getCombinedGraphSelection()) {
          if (priAddress.equals(diffNode.getRawNode().getAddress(ESide.PRIMARY))
              && secAddress.equals(diffNode.getRawNode().getAddress(ESide.SECONDARY))) {
            snapshot.remove(diffNode);
            snapshot.add(newPriUnmatchedDiffNode);
            snapshot.add(newSecUnmatchedDiffNode);
            snapshot.modicationFinished();
            break;
          }
        }
      }
    }

    private void refreshSnapshots(final SingleDiffNode newDiffNode) {
      checkNotNull(newDiffNode);

      final IAddress address = newDiffNode.getRawNode().getAddress();
      for (final SelectionSnapshot snapshot : snapshotList) {
        for (final SingleDiffNode diffNode : snapshot.getSingleGraphSelection()) {
          if (diffNode.getRawNode().getAddress().equals(address)) {
            snapshot.remove(diffNode);
            snapshot.add(newDiffNode);
            snapshot.modicationFinished();
            break;
          }
        }
      }
    }

    private void updateSnapshots(
        final IAddress priFunctionAddr,
        final IAddress secFunctionAddr,
        final IAddress priBasicblockAddr,
        final IAddress secBasicblockAddr) {
      final BinDiffGraph<? extends ZyGraphNode<?>, ?> graph = getGraph();
      if (graph.getGraphType() == EGraphType.CALL_GRAPH) {
        return;
      }

      if (priFunctionAddr.equals(graph.getPrimaryGraph().getFunctionAddress())
          && secFunctionAddr.equals(graph.getSecondaryGraph().getFunctionAddress())) {
        if (singleGraph != null) {
          refreshSnapshots(
              getNewDiffNode(
                  singleGraph.getSide() == ESide.PRIMARY ? priBasicblockAddr : secBasicblockAddr));
        } else {
          refreshSnapshots(
              getNewDiffNode(priBasicblockAddr, ESide.PRIMARY),
              getNewDiffNode(secBasicblockAddr, ESide.SECONDARY));
        }
      }
    }

    @Override
    public void addedBasicBlockMatch(
        final IAddress priFunctionAddr,
        final IAddress secFunctionAddr,
        final IAddress priBasicBlockAddr,
        final IAddress secBasicBlockAddr) {
      updateSnapshots(priFunctionAddr, secFunctionAddr, priBasicBlockAddr, secBasicBlockAddr);
    }

    @Override
    public void removedBasicBlockMatch(
        final IAddress priFunctionAddr,
        final IAddress secFunctionAddr,
        final IAddress priBasicBlockAddr,
        final IAddress secBasicBlockAddr) {
      updateSnapshots(priFunctionAddr, secFunctionAddr, priBasicBlockAddr, secBasicBlockAddr);
    }
  }
}
