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

package com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.menubar;

import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.WorkspaceTabPanelFunctions;
import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.actions.InitialCallGraphSettingsAction;
import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.actions.InitialFlowGraphSettingsAction;
import com.google.security.zynamics.bindiff.gui.tabpanels.projecttabpanel.actions.MainSettingsAction;
import com.google.security.zynamics.bindiff.utils.GuiUtils;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

public class SettingsMenu extends JMenu {

  public SettingsMenu(final WorkspaceTabPanelFunctions controller) {
    super("Settings");
    setMnemonic('S');

    JMenuItem mainSettings =
        GuiUtils.buildMenuItem(
            "Main Settings...", 'M', KeyEvent.VK_F2, new MainSettingsAction(controller));

    JMenuItem initialCallGraphSettings =
        GuiUtils.buildMenuItem(
            "Initial Call Graph Settings...",
            'C',
            KeyEvent.VK_F2,
            InputEvent.SHIFT_DOWN_MASK,
            new InitialCallGraphSettingsAction(controller));

    JMenuItem initialFlowGraphSettings =
        GuiUtils.buildMenuItem(
            "Initial Flow Graph Settings...",
            'F',
            KeyEvent.VK_F2,
            InputEvent.CTRL_DOWN_MASK,
            new InitialFlowGraphSettingsAction(controller));

    add(mainSettings);

    add(new JSeparator());

    add(initialCallGraphSettings);
    add(initialFlowGraphSettings);
  }
}
