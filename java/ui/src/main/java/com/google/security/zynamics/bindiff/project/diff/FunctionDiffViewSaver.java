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

package com.google.security.zynamics.bindiff.project.diff;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.security.zynamics.bindiff.database.MatchesDatabase;
import com.google.security.zynamics.bindiff.enums.ESide;
import com.google.security.zynamics.bindiff.gui.components.MessageBox;
import com.google.security.zynamics.bindiff.gui.tabpanels.viewtabpanel.ViewTabPanelFunctions;
import com.google.security.zynamics.bindiff.project.userview.FlowGraphViewData;
import com.google.security.zynamics.bindiff.resources.Constants;
import com.google.security.zynamics.bindiff.utils.BinDiffFileUtils;
import com.google.security.zynamics.zylib.gui.ProgressDialogs.CEndlessHelperThread;
import java.awt.Window;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;

public class FunctionDiffViewSaver extends CEndlessHelperThread {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ViewTabPanelFunctions controller;

  private final Window parent;

  private final File primaryExportFileTarget;
  private final File secondaryExportFileTarget;
  private final File binDiffFileTarget;
  private final boolean overridePrimaryExport;
  private final boolean overrideSecondaryExport;

  private boolean cleanupPrimaryExportFile = false;
  private boolean cleanupSecondaryExportFile = false;
  private boolean cleanupBinDiffFile = false;

  private final Diff diffToSave;
  private Diff savedDiff;

  private boolean addSavedDiffToWorkspace = false;

  public FunctionDiffViewSaver(
      final ViewTabPanelFunctions controller,
      final File primaryExportFileTarget,
      final File secondaryExportFileTarget,
      final File binDiffFileTarget,
      final boolean overridePrimaryExport,
      final boolean overrideSecondaryExport) {
    this.controller = checkNotNull(controller);
    this.parent = controller.getMainWindow();
    this.diffToSave = controller.getGraphs().getDiff();
    this.primaryExportFileTarget = checkNotNull(primaryExportFileTarget);
    this.secondaryExportFileTarget = checkNotNull(secondaryExportFileTarget);
    this.binDiffFileTarget = checkNotNull(binDiffFileTarget);

    this.overridePrimaryExport = overridePrimaryExport;
    this.overrideSecondaryExport = overrideSecondaryExport;
  }

  private void cleanUp() {
    // TODO(cblichmann): Do not perform error handling by relying on error
    // message strings for checking whether an error
    // occurred
    String errorMsg = "";
    if (primaryExportFileTarget != null
        && primaryExportFileTarget.exists()
        && cleanupPrimaryExportFile) {
      if (!primaryExportFileTarget.delete()) {
        errorMsg = String.format("Failed to delete '%s'.\n", primaryExportFileTarget.getPath());
      }
    }
    if (secondaryExportFileTarget != null
        && secondaryExportFileTarget.exists()
        && cleanupSecondaryExportFile) {
      if (!secondaryExportFileTarget.delete()) {
        errorMsg = String.format("Failed to delete '%s'.\n", secondaryExportFileTarget.getPath());
      }
    }
    if (binDiffFileTarget != null && binDiffFileTarget.exists() && cleanupBinDiffFile) {
      if (!binDiffFileTarget.delete()) {
        errorMsg = String.format("Failed to delete '%s'.\n", binDiffFileTarget.getPath());
      }
    }

    final File destinationDir = binDiffFileTarget.getParentFile();
    if (destinationDir.list().length == 0) {
      if (!destinationDir.delete()) {
        errorMsg = String.format("Failed to delete '%s'.\n", destinationDir.getPath());
      }
    }

    if (!"".equals(errorMsg)) {
      errorMsg = errorMsg.substring(0, errorMsg.length() - 2);

      logger.atWarning().log("%s", errorMsg);
      MessageBox.showWarning(parent, errorMsg);
    }
  }

  private void cloneDiffObjectOnSaveAs() {
    final FlowGraphViewData view = diffToSave.getViewManager().getFlowGraphViewsData().get(0);

    if (binDiffFileTarget.exists()
        && !binDiffFileTarget.equals(diffToSave.getMatchesDatabase())
        && diffToSave.getMatchesDatabase().getParent().equals(binDiffFileTarget.getParent())) {
      // Diff view should overwrite another existing view (*.BinDiff file)
      diffToSave.willOverwriteDiff(binDiffFileTarget.getPath());
      savedDiff =
          diffToSave.cloneDiffObjectOnSaveAs(
              binDiffFileTarget, primaryExportFileTarget, secondaryExportFileTarget, view);
    } else if (!binDiffFileTarget.exists()
        && diffToSave.getMatchesDatabase().getParent().equals(binDiffFileTarget.getParent())) {
      // A function diff loaded directly from the workspace and afterwards saved with different
      // non-existing name
      savedDiff =
          diffToSave.cloneDiffObjectOnSaveAs(
              binDiffFileTarget, primaryExportFileTarget, secondaryExportFileTarget, view);
    } else {
      // Diff itself should be overwritten or the diff was loaded directly from IDA
      addSavedDiffToWorkspace = !binDiffFileTarget.equals(diffToSave.getMatchesDatabase());
      savedDiff = diffToSave;
    }

    this.addSavedDiffToWorkspace = addSavedDiffToWorkspace || savedDiff != diffToSave;
  }

  private boolean copyBinDiffFile() {
    final File sourceFile = diffToSave.getMatchesDatabase();

    if (sourceFile.getPath().equals(binDiffFileTarget.getPath())) {
      savedDiff = diffToSave;

      return true;
    }

    cleanupBinDiffFile = !binDiffFileTarget.exists();

    FileInputStream fis = null;
    FileOutputStream fos = null;
    try {
      cloneDiffObjectOnSaveAs();

      fis = new FileInputStream(sourceFile);
      fos = new FileOutputStream(binDiffFileTarget);

      ByteStreams.copy(fis, fos);
    } catch (final FileNotFoundException e) {
      logger.atSevere().withCause(e).log(
          "Save function diff view failed. Couldn't copy BinDiff file into workspace.");
      MessageBox.showError(
          parent, "Save function diff view failed. Couldn't copy BinDiff file into workspace.");

      return false;
    } catch (final IOException e) {
      logger.atSevere().withCause(e).log(
          "Save function diff view failed. Couldn't copy BinExport files into workspace.");
      MessageBox.showError(
          parent, "Save function diff view failed. Couldn't copy BinExport files into workspace.");

      return false;
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (final IOException e) {
          logger.atWarning().withCause(e).log("Couldn't close file input stream");
        }
      }
      if (fos != null) {
        try {
          fos.close();
        } catch (final IOException e) {
          logger.atWarning().withCause(e).log("Couldn't close file output stream");
        }
      }
    }

    return true;
  }

  private boolean copyExportFile(final ESide side) {
    final File sourceFile = diffToSave.getExportFile(side);
    final File targetFile =
        side == ESide.PRIMARY ? primaryExportFileTarget : secondaryExportFileTarget;

    if (targetFile.exists()
        && (side == ESide.PRIMARY && !overridePrimaryExport
            || side == ESide.SECONDARY && !overrideSecondaryExport)) {
      return true;
    }

    if (sourceFile.getPath().equals(targetFile.getPath())) {
      return true;
    }

    if (side == ESide.PRIMARY) {
      cleanupPrimaryExportFile = !targetFile.exists();
    } else {
      cleanupSecondaryExportFile = !targetFile.exists();
    }

    try {
      FileInputStream fis = null;
      FileOutputStream fos = null;
      try {
        fis = new FileInputStream(sourceFile);
        fos = new FileOutputStream(targetFile);
        ByteStreams.copy(fis, fos);
      } finally {
        if (fis != null) {
          try {
            fis.close();
          } catch (final IOException e) {
            logger.atWarning().withCause(e).log("Couldn't close file input stream");
          }
        }
        if (fos != null) {
          try {
            fos.close();
          } catch (final IOException e) {
            logger.atWarning().withCause(e).log("Couldn't close file output stream");
          }
        }
      }
    } catch (final IOException e) {
      logger.atSevere().withCause(e).log(
          "Save function diff view failed. Couldn't copy BinExport files.");
      MessageBox.showError(
          parent, "Save function diff view failed. Couldn't copy BinExport files.");

      return false;
    }

    return true;
  }

  private void saveView() {
    controller.writeComments();
    controller.writeFlowgraphMatches();
  }

  private boolean updateDiff() {
    try {
      savedDiff.setMatchesDatabase(binDiffFileTarget);
      savedDiff.setExportFile(primaryExportFileTarget, ESide.PRIMARY);
      savedDiff.setExportFile(secondaryExportFileTarget, ESide.SECONDARY);
    } catch (final UnsupportedOperationException e) {
      logger.atSevere().withCause(e).log(
          "Save function diff view failed. Couldn't update diff object.");
      MessageBox.showError(parent, "Save function diff view failed. Couldn't update diff object.");

      return false;
    }

    return true;
  }

  private boolean updateMatchesDatabase() {
    final String priExportFileName =
        BinDiffFileUtils.forceFilenameEndsNotWithExtension(
            primaryExportFileTarget.getName(), Constants.BINDIFF_BINEXPORT_EXTENSION);
    final String secExportFileName =
        BinDiffFileUtils.forceFilenameEndsNotWithExtension(
            secondaryExportFileTarget.getName(), Constants.BINDIFF_BINEXPORT_EXTENSION);
    if (!binDiffFileTarget.exists()) {
      return false;
    }

    try (final MatchesDatabase matchesDb = new MatchesDatabase(binDiffFileTarget)) {
      matchesDb.changeExportFilename(priExportFileName, ESide.PRIMARY);
      matchesDb.changeExportFilename(secExportFileName, ESide.SECONDARY);

      matchesDb.changeFileTable(savedDiff);
    } catch (final SQLException e) {
      final String msg =
          "Save function diff view failed. Couldn't update export file name in matches database.";
      logger.atSevere().withCause(e).log(msg);
      MessageBox.showError(parent, msg + " " + e.getMessage());
      return false;
    }
    return true;
  }

  private void updateWorkspace() throws SQLException {
    if (addSavedDiffToWorkspace) {
      controller.getWorkspace().addDiff(savedDiff);
    }
  }

  @Override
  protected void runExpensiveCommand() throws SQLException {
    setDescription("Copying primary BinExport file...");
    if (!copyExportFile(ESide.PRIMARY)) {
      cleanUp();
      return;
    }

    setDescription("Copying secondary BinExport file...");
    if (!copyExportFile(ESide.SECONDARY)) {
      cleanUp();
      return;
    }

    setDescription("Copying BinDiff file...");
    if (!copyBinDiffFile()) {
      cleanUp();
      return;
    }

    setDescription("Updating BinDiff database...");
    if (!updateMatchesDatabase()) {
      cleanUp();
      return;
    }

    setDescription("Updating Diff...");
    if (!updateDiff()) {
      cleanUp();
      return;
    }

    setDescription("Saving comments and changed matches...");
    saveView();

    setDescription("Updating Workspace...");
    updateWorkspace();

    setDescription("Completed successfully.");
    try {
      Thread.sleep(300);
    } catch (final InterruptedException e) {
      // Ignore
    }
  }
}
