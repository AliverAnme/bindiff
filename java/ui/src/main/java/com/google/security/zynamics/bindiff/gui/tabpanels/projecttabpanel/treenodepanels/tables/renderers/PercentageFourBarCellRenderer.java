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

package com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.treenodepanels.tables.renderers;

import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.treenodepanels.renderers.PercentageThreeBarExtendedBarIcon;
import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.treenodepanels.renderers.PercentageThreeBarExtendedCellData;
import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;

public class PercentageFourBarCellRenderer extends AbstractTableCellRenderer {
  private final Color leftBarColor;
  private final Color centerLeftBarColor;
  private final Color centerRightBarColor;
  private final Color rightBarColor;
  private final Color emptyBarColor;
  private final Color textColor;

  public PercentageFourBarCellRenderer(
      final Color leftBarColor,
      final Color centerLeftBarColor,
      final Color centerRightBarColor,
      final Color rightBarColor,
      final Color emptyBarColor,
      final Color textColor) {
    this.leftBarColor = leftBarColor;
    this.centerLeftBarColor = centerLeftBarColor;
    this.centerRightBarColor = centerRightBarColor;
    this.rightBarColor = rightBarColor;
    this.emptyBarColor = emptyBarColor;
    this.textColor = textColor;
  }

  @Override
  public Component getTableCellRendererComponent(
      final JTable table,
      final Object value,
      final boolean selected,
      final boolean focused,
      final int row,
      final int column) {
    buildAndSetToolTip(table, row);

    setFont(!isBoldFont(table, row) ? NORMAL_FONT : BOLD_FONT);

    if (value instanceof PercentageThreeBarExtendedCellData) {
      setIcon(
          new PercentageThreeBarExtendedBarIcon(
              (PercentageThreeBarExtendedCellData) value,
              leftBarColor,
              centerLeftBarColor,
              centerRightBarColor,
              rightBarColor,
              emptyBarColor,
              textColor,
              table.getSelectionBackground(),
              selected,
              0 - 1,
              0,
              table.getColumnModel().getColumn(column).getWidth() - 2,
              table.getRowHeight() - 2));
    }

    return this;
  }
}
