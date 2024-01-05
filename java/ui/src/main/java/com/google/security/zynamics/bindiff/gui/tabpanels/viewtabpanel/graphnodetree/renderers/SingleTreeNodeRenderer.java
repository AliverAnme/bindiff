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

package com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.graphnodetree.renderers;

import com.google.security.zynamics.bindiff.enums.EFunctionType;
import com.google.security.zynamics.bindiff.enums.ESide;
import com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.graphnodetree.treenodes.AbstractTreeNode;
import com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.graphnodetree.treenodes.single.callgraph.SingleCallGraphFunctionTreeNode;
import com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.graphnodetree.treenodes.single.flowgraph.SingleFlowGraphBaseTreeNode;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawFlowGraph;
import com.google.security.zynamics.bindiff.project.userview.FlowGraphViewData;
import com.google.security.zynamics.bindiff.resources.Colors;
import com.google.security.zynamics.zylib.gui.GuiHelper;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public class SingleTreeNodeRenderer extends DefaultTreeCellRenderer {

  public SingleTreeNodeRenderer() {
    setBackgroundSelectionColor(Color.WHITE);
    setBorderSelectionColor(Color.GRAY);
  }

  @Override
  public Component getTreeCellRendererComponent(
      final JTree tree,
      final Object value,
      final boolean sel,
      final boolean expanded,
      final boolean leaf,
      final int row,
      final boolean hasFocus) {
    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

    final AbstractTreeNode node = (AbstractTreeNode) value;

    final Icon icon = node.getIcon();

    if (icon != null) {
      setIcon(icon);
    }

    setFont(
        node.isSelected()
            ? GuiHelper.getDefaultFont().deriveFont(Font.BOLD)
            : GuiHelper.getDefaultFont());
    setForeground(node.isVisible() ? Color.BLACK : Color.GRAY);

    Color background = Color.WHITE;

    EFunctionType functionType = null;

    if (node instanceof SingleCallGraphFunctionTreeNode) {
      final SingleCallGraphFunctionTreeNode functionTreeNode =
          (SingleCallGraphFunctionTreeNode) node;
      functionType = functionTreeNode.getFunction().getFunctionType();
    } else if (node instanceof SingleFlowGraphBaseTreeNode) {
      final FlowGraphViewData view = (FlowGraphViewData) node.getRootNode().getView();
      final ESide side = node.getRootNode().getSide();
      final RawFlowGraph flowgraph = view.getRawGraph(side);

      functionType = flowgraph.getFunctionType();
    }

    if (functionType != null) {
      switch (functionType) {
        case IMPORTED:
          background = Colors.FUNCTION_TYPE_IMPORTED;
          break;
        case LIBRARY:
          background = Colors.FUNCTION_TYPE_LIBRARY;
          break;
        case THUNK:
          background = Colors.FUNCTION_TYPE_THUNK;
          break;
        case UNKNOWN:
          background = Colors.FUNCTION_TYPE_UNKNOWN;
          break;
        case NORMAL:
        default:
          background = Colors.FUNCTION_TYPE_NORMAL;
      }
    }

    setBackgroundSelectionColor(background);
    setBackgroundNonSelectionColor(background);
    setBorderSelectionColor(background);

    setToolTipText(node.getTooltipText());

    return this;
  }
}
