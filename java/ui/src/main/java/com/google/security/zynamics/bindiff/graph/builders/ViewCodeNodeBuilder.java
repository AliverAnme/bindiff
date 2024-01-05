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

package com.google.security.zynamics.bindiff.graph.builders;

import com.google.common.base.Strings;
import com.google.security.zynamics.bindiff.enums.EInstructionHighlighting;
import com.google.security.zynamics.bindiff.enums.EPlaceholderType;
import com.google.security.zynamics.bindiff.enums.ESide;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.BasicBlockCommentDelimiterLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.BasicBlockCommentLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.BasicBlockHeadLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.BasicBlockLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.CInstructionCommentLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.InstructionCommentDelimiterLineObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.InstructionObject;
import com.google.security.zynamics.bindiff.graph.labelcontent.editableline.PlaceholderObject;
import com.google.security.zynamics.bindiff.project.matches.BasicBlockMatchData;
import com.google.security.zynamics.bindiff.project.matches.FunctionMatchData;
import com.google.security.zynamics.bindiff.project.matches.InstructionMatchData;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawBasicBlock;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawCombinedBasicBlock;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawCombinedFlowGraph;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawCombinedJump;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawFlowGraph;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawInstruction;
import com.google.security.zynamics.bindiff.project.rawflowgraph.RawInstructionComment;
import com.google.security.zynamics.bindiff.resources.Colors;
import com.google.security.zynamics.bindiff.resources.Fonts;
import com.google.security.zynamics.bindiff.types.AddressPairComparator;
import com.google.security.zynamics.zylib.disassembly.IAddress;
import com.google.security.zynamics.zylib.general.Pair;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.CStyleRunData;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.ECommentPlacement;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.IZyEditableObject;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.KeyBehaviours.Tokenizer.CMultiCommentLineTokenizer;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.ZyLabelContent;
import com.google.security.zynamics.zylib.gui.zygraph.realizers.ZyLineContent;
import com.google.security.zynamics.zylib.strings.StringHelper;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class ViewCodeNodeBuilder {
  private static final String ABOVE_INSTRUCTION_COMMENT_ESCAPE_STRING = "// ";
  private static final String BEHIND_INSTRUCTION_COMMENT_ESCAPE_STRING = " // ";
  private static final String BASICBLOCK_COMMENT_HEADLINE_STRING = "// Basic Block Comment: ";
  private static final String BASICBLOCK_COMMENT_ESCAPE_STRING = "// ";

  public static final int MAX_MNEMONIC_SIZE = 12;

  private static final String PADDING_AFTER_ADDRESS = "   ";

  private static final PlaceholderObject PLACEHOLDER_MATCHED_ABOVE_INSTRUCTION_COMMENT =
      new PlaceholderObject(EPlaceholderType.MATCHED_ABOVE_INSTRUCTION_COMMENT);
  private static final PlaceholderObject PLACEHOLDER_MATCHED_BEHIND_INSTRUCTION_COMMENT =
      new PlaceholderObject(EPlaceholderType.MATCHED_BEHIND_INSTRUCTION_COMMENT);
  private static final PlaceholderObject PLACEHOLDER_UNMATCHED_ABOVE_INSTRUCTION_COMMENT =
      new PlaceholderObject(EPlaceholderType.UNMATCHED_ABOVE_INSTRUCTION_COMMENT);
  private static final PlaceholderObject PLACEHOLDER_UNMATCHED_INSTRUCTION =
      new PlaceholderObject(EPlaceholderType.UNMATCHED_INSTRUCTION);
  private static final PlaceholderObject PLACEHOLDER_UNMATCHED_BEHIND_INSTRUCTION_COMMENT =
      new PlaceholderObject(EPlaceholderType.UNMATCHED_BEHIND_INSTRUCTION_COMMENT);
  private static final PlaceholderObject PLACEHOLDER_BASIC_BLOCK_COMMENT =
      new PlaceholderObject(EPlaceholderType.BASIC_BLOCK_COMMENT);

  private static void buildAddress(
      final IAddress address, final StringBuffer line, final List<CStyleRunData> styleRun) {
    final String text = address.toHexString() + PADDING_AFTER_ADDRESS;
    styleRun.add(
        new CStyleRunData(
            line.length(), text.length(), EInstructionHighlighting.TYPE_ADDRESS.getColor()));
    line.append(text);
  }

  private static ZyLineContent buildHeadline(final RawBasicBlock basicBlock) {
    final StringBuffer line = new StringBuffer();
    final List<CStyleRunData> styleRun = new ArrayList<>();
    buildAddress(basicBlock.getFunctionAddr(), line, styleRun);

    styleRun.add(
        new CStyleRunData(line.length(), -1, EInstructionHighlighting.TYPE_DEFAULT.getColor()));
    line.append(basicBlock.getFunctionName());

    final IZyEditableObject headlineObject = new BasicBlockHeadLineObject(basicBlock);
    return new ZyLineContent(line.toString(), Fonts.BOLD_FONT, styleRun, headlineObject);
  }

  private static void buildBasicblockComments(
      final RawBasicBlock priBasicblock,
      final RawBasicBlock secBasicblock,
      final ZyLabelContent labelContent,
      final ESide side) {
    int maxInsertLinesCounter = 0;

    if (priBasicblock != null) {
      maxInsertLinesCounter = StringHelper.count(priBasicblock.getComment(), '\n');
    }

    if (secBasicblock != null) {
      maxInsertLinesCounter =
          Math.max(maxInsertLinesCounter, StringHelper.count(secBasicblock.getComment(), '\n'));
    }

    final RawBasicBlock basicblock = side == ESide.PRIMARY ? priBasicblock : secBasicblock;

    for (final ZyLineContent line : buildBasicblockComment(basicblock, maxInsertLinesCounter)) {
      labelContent.addLineContent(line);
    }
  }

  private static void buildMatchedCodeNodeContent(
      final RawBasicBlock priBasicblock,
      final RawBasicBlock secBasicblock,
      final List<Pair<IAddress, IAddress>> instructionAddrPairs,
      final ZyLabelContent content,
      final ESide side) {
    final List<ZyLineContent> lines = new ArrayList<>();

    if (side == ESide.PRIMARY) {
      content.addLineContent(buildHeadline(priBasicblock));
      final int maxOperandLength = priBasicblock.getMaxOperandLength();

      for (final Pair<IAddress, IAddress> instrPair : instructionAddrPairs) {
        RawInstruction priInstruction = null;
        if (instrPair.first() != null) {
          priInstruction = priBasicblock.getInstruction(instrPair.first());
        }

        RawInstruction secInstruction = null;
        if (secBasicblock != null && instrPair.second() != null) {
          secInstruction = secBasicblock.getInstruction(instrPair.second());
        }

        final int topCommentLineCount =
            precalcMaxCommentLineCount(
                priInstruction, secInstruction, ECommentPlacement.ABOVE_LINE);
        final int rightCommentLineCount =
            precalcMaxCommentLineCount(
                priInstruction, secInstruction, ECommentPlacement.BEHIND_LINE);

        lines.addAll(
            buildInstruction(
                priInstruction,
                maxOperandLength,
                instrPair.second() == null,
                side,
                topCommentLineCount,
                rightCommentLineCount));
      }
    } else {
      content.addLineContent(buildHeadline(secBasicblock));
      final int maxOperandLength = secBasicblock.getMaxOperandLength();

      for (final Pair<IAddress, IAddress> instrPair : instructionAddrPairs) {
        RawInstruction priInstruction = null;
        if (priBasicblock != null && instrPair.first() != null) {
          priInstruction = priBasicblock.getInstruction(instrPair.first());
        }

        RawInstruction secInstruction = null;
        if (instrPair.second() != null) {
          secInstruction = secBasicblock.getInstruction(instrPair.second());
        }

        final int topCommentLineCount =
            precalcMaxCommentLineCount(
                priInstruction, secInstruction, ECommentPlacement.ABOVE_LINE);
        final int rightCommentLineCount =
            precalcMaxCommentLineCount(
                priInstruction, secInstruction, ECommentPlacement.BEHIND_LINE);

        lines.addAll(
            buildInstruction(
                secInstruction,
                maxOperandLength,
                instrPair.first() == null,
                side,
                topCommentLineCount,
                rightCommentLineCount));
      }
    }

    for (final ZyLineContent line : lines) {
      content.addLineContent(line);
    }

    buildBasicblockComments(priBasicblock, secBasicblock, content, side);
  }

  private static void buildMnemonic(
      final RawInstruction instruction,
      final StringBuffer line,
      final List<CStyleRunData> styleRun) {
    if (instruction.getMnemonic().length() == 0) {
      return;
    }

    String mnemonic = instruction.getMnemonic();
    if (instruction.getOperands().length > 0) {
      mnemonic = Strings.padEnd(mnemonic, instruction.getMaxMnemonicLen(), ' ');
    }

    styleRun.add(
        new CStyleRunData(
            line.length(), mnemonic.length(), EInstructionHighlighting.TYPE_MNEMONIC.getColor()));
    line.append(mnemonic);
  }

  private static boolean appendStyleRunData(
      ByteArrayOutputStream current,
      Color color,
      final StringBuffer line,
      final List<CStyleRunData> styleRun) {
    if (current == null || color == null) {
      return true; // Tell caller to reset stream and set color.
    }
    if (current.size() == 0) {
      return false; // Stream buffer empty, tell caller to reuse and not set a new color
    }

    final String part = current.toString();
    styleRun.add(new CStyleRunData(line.length(), part.length(), color));
    line.append(part);

    try {
      current.close();
    } catch (final IOException e) {
      // Ignore error on close
    }
    return true; // Caller should reset stream and set new color
  }

  private static void buildOperands(
      final RawInstruction instruction,
      final StringBuffer line,
      final List<CStyleRunData> styleRun) {
    final byte[] rawOperandBytes = instruction.getOperands();
    final ByteArrayOutputStream current = new ByteArrayOutputStream();
    Color color = null;
    for (final byte b : rawOperandBytes) {
      if (b > 0 && EInstructionHighlighting.validOrdinal(b)) {
        if (appendStyleRunData(current, color, line, styleRun)) {
          color = EInstructionHighlighting.getColor(b);
          current.reset();
        }
      } else {
        current.write(b);
      }
    }
    appendStyleRunData(current, color, line, styleRun);
  }

  private static List<ZyLineContent> buildTrailingComment(
      final RawInstruction instruction,
      final InstructionObject instructionObject,
      final RawInstructionComment comment,
      final StringBuffer line,
      final List<CStyleRunData> styleRun,
      final int maxOperandLength,
      final boolean isUnmatchedInstruction,
      final ESide side,
      final int maxRightCommentLineCount) {
    final List<ZyLineContent> lines = new ArrayList<>();
    String commentText = "";

    if (comment != null) {
      final int lineCommentStart = line.length();
      final int maxLineLength =
          instruction.getAddress().toHexString().length()
              + PADDING_AFTER_ADDRESS.length()
              + MAX_MNEMONIC_SIZE
              + maxOperandLength;

      final Color commentColor = EInstructionHighlighting.TYPE_COMMENT.getColor();

      commentText = comment.getText();
      final CMultiCommentLineTokenizer tokenizer =
          new CMultiCommentLineTokenizer(commentText, "\n");

      boolean isFirst = true;
      while (tokenizer.hasMoreTokens()) {
        final String token = tokenizer.nextToken();

        if (isFirst) {
          isFirst = false;

          String commentDelimiter =
              Strings.repeat(" ", Math.max(0, maxLineLength - lineCommentStart));
          commentDelimiter += BEHIND_INSTRUCTION_COMMENT_ESCAPE_STRING;

          final int commentDelimiterLength = commentDelimiter.length();
          final InstructionCommentDelimiterLineObject escapeStringLineObject =
              new InstructionCommentDelimiterLineObject(
                  instruction, comment.getPlacement(), lineCommentStart, commentDelimiterLength);

          final CStyleRunData escapeStringEntry =
              new CStyleRunData(
                  lineCommentStart, commentDelimiterLength, commentColor, escapeStringLineObject);
          styleRun.add(escapeStringEntry);

          line.append(commentDelimiter);

          if (!token.isEmpty()) {
            final CInstructionCommentLineObject commentLineObject;
            commentLineObject =
                new CInstructionCommentLineObject(
                    instruction, comment.getPlacement(), line.length(), token.length());
            final CStyleRunData commentEntry =
                new CStyleRunData(line.length(), token.length(), commentColor, commentLineObject);
            styleRun.add(commentEntry);

            line.append(token);
          }

          final ZyLineContent lineContent =
              new ZyLineContent(line.toString(), Fonts.NORMAL_FONT, styleRun, instructionObject);
          if (isUnmatchedInstruction) {
            lineContent.setBackgroundColor(
                0,
                lineCommentStart,
                side == ESide.PRIMARY ? Colors.PRIMARY_BASE : Colors.SECONDARY_BASE);
          }

          lines.add(lineContent);
        } else {
          final List<CStyleRunData> newStyleRun = new ArrayList<>();

          String commentLine =
              String.format(
                  "%s%s",
                  Strings.repeat(" ", maxLineLength), BEHIND_INSTRUCTION_COMMENT_ESCAPE_STRING);

          final InstructionCommentDelimiterLineObject escapeStringLineObject =
              new InstructionCommentDelimiterLineObject(
                  instruction, comment.getPlacement(), 0, commentLine.length());

          final CStyleRunData escapeStringEntry =
              new CStyleRunData(0, commentLine.length(), commentColor, escapeStringLineObject);
          newStyleRun.add(escapeStringEntry);

          if (!token.equals("")) {
            final CInstructionCommentLineObject commentLineObject;
            commentLineObject =
                new CInstructionCommentLineObject(
                    instruction, comment.getPlacement(), commentLine.length(), token.length());

            final CStyleRunData commentEntry =
                new CStyleRunData(
                    commentLine.length(), token.length(), commentColor, commentLineObject);
            newStyleRun.add(commentEntry);

            commentLine += token;
          }

          final ZyLineContent lineContent =
              new ZyLineContent(commentLine, Fonts.NORMAL_FONT, newStyleRun, instructionObject);

          lines.add(lineContent);
        }
      }
    }

    if (commentText.isEmpty()) {
      final ZyLineContent lineContent =
          new ZyLineContent(line.toString(), Fonts.NORMAL_FONT, styleRun, instructionObject);

      if (isUnmatchedInstruction) {
        lineContent.setBackgroundColor(
            0, line.length(), side == ESide.PRIMARY ? Colors.PRIMARY_BASE : Colors.SECONDARY_BASE);
      }

      lines.add(lineContent);
    }

    if (lines.size() < maxRightCommentLineCount) {
      lines.addAll(
          buildPlaceholderLines(
              maxRightCommentLineCount - lines.size(),
              EPlaceholderType.MATCHED_BEHIND_INSTRUCTION_COMMENT));
    }

    return lines;
  }

  private static List<ZyLineContent> buildTopComment(
      final RawInstruction instruction,
      final InstructionObject instructionObject,
      final RawInstructionComment comment,
      final int maxTopCommentLineCount) {
    if (comment == null) {
      return buildPlaceholderLines(
          maxTopCommentLineCount, EPlaceholderType.MATCHED_ABOVE_INSTRUCTION_COMMENT);
    }

    final List<ZyLineContent> lines = new ArrayList<>();

    final String commentText = comment.getText();

    final CMultiCommentLineTokenizer tokenizer = new CMultiCommentLineTokenizer(commentText, "\n");

    final Color commentColor = EInstructionHighlighting.TYPE_COMMENT.getColor();

    while (tokenizer.hasMoreTokens()) {
      final List<CStyleRunData> styleRun = new ArrayList<>();

      String commentLine = ABOVE_INSTRUCTION_COMMENT_ESCAPE_STRING;
      final InstructionCommentDelimiterLineObject escapeStringLineObject =
          new InstructionCommentDelimiterLineObject(
              instruction, comment.getPlacement(), 0, commentLine.length());

      final CStyleRunData escapeEntry =
          new CStyleRunData(0, commentLine.length(), commentColor, escapeStringLineObject);
      styleRun.add(escapeEntry);

      final String token = tokenizer.nextToken();

      final CInstructionCommentLineObject commentLineObject =
          new CInstructionCommentLineObject(
              instruction, comment.getPlacement(), commentLine.length(), token.length());
      commentLine += token;

      final CStyleRunData commentEntry =
          new CStyleRunData(0, commentLine.length(), commentColor, commentLineObject);
      styleRun.add(commentEntry);

      final ZyLineContent lineContent =
          new ZyLineContent(commentLine, Fonts.NORMAL_FONT, styleRun, instructionObject);

      lines.add(lineContent);
    }

    if (lines.size() < maxTopCommentLineCount) {
      final List<ZyLineContent> emptyLines = new ArrayList<>();

      emptyLines.addAll(
          buildPlaceholderLines(
              maxTopCommentLineCount - lines.size(),
              EPlaceholderType.MATCHED_ABOVE_INSTRUCTION_COMMENT));

      emptyLines.addAll(lines);

      return emptyLines;
    }

    return lines;
  }

  private static void buildUnmatchedCodeNodeContent(
      final RawBasicBlock basicblock, final ZyLabelContent content) {
    content.addLineContent(buildHeadline(basicblock));

    final List<ZyLineContent> lines = new ArrayList<>();

    final int maxOperandLength = basicblock.getMaxOperandLength();

    for (final RawInstruction instruction : basicblock) {
      lines.addAll(buildInstruction(instruction, maxOperandLength, false, null, 0, 0));
    }

    for (final ZyLineContent line : lines) {
      content.addLineContent(line);
    }

    buildBasicblockComments(
        basicblock.getSide() == ESide.PRIMARY ? basicblock : null,
        basicblock.getSide() == ESide.SECONDARY ? basicblock : null,
        content,
        basicblock.getSide());
  }

  private static List<Pair<IAddress, IAddress>>
      getMatchedFunctionsBasicBlockInstructionAddressPairs(
          final FunctionMatchData functionMatch,
          final RawCombinedFlowGraph<RawCombinedBasicBlock, RawCombinedJump<RawCombinedBasicBlock>>
              combinedFlowGraph,
          final IAddress primaryBasicBlockAddr,
          final IAddress secondaryBasicBlockAddr) {
    final List<Pair<IAddress, IAddress>> instructionAddrPairs = new ArrayList<>();

    final RawFlowGraph priFlowgraph = combinedFlowGraph.getPrimaryFlowgraph();
    final RawFlowGraph secFlowgraph = combinedFlowGraph.getSecondaryFlowgraph();

    if (primaryBasicBlockAddr != null && secondaryBasicBlockAddr != null) {
      final BasicBlockMatchData basicBlockMatch =
          functionMatch.getBasicBlockMatch(primaryBasicBlockAddr, ESide.PRIMARY);
      for (final InstructionMatchData instructionMatch : basicBlockMatch.getInstructionMatches()) {
        instructionAddrPairs.add(
            new Pair<>(
                instructionMatch.getIAddress(ESide.PRIMARY),
                instructionMatch.getIAddress(ESide.SECONDARY)));
      }

      final RawBasicBlock primaryBasicblock = priFlowgraph.getBasicBlock(primaryBasicBlockAddr);
      for (final Entry<IAddress, RawInstruction> e :
          primaryBasicblock.getInstructions().entrySet()) {
        if (basicBlockMatch.getInstructionMatch(e.getKey(), ESide.PRIMARY) == null) {
          instructionAddrPairs.add(new Pair<>(e.getKey(), null));
        }
      }

      final RawBasicBlock secondaryBasicblock = secFlowgraph.getBasicBlock(secondaryBasicBlockAddr);
      for (final Entry<IAddress, RawInstruction> e :
          secondaryBasicblock.getInstructions().entrySet()) {
        if (basicBlockMatch.getInstructionMatch(e.getKey(), ESide.SECONDARY) == null) {
          instructionAddrPairs.add(new Pair<>(null, e.getKey()));
        }
      }
    } else if (primaryBasicBlockAddr != null) {
      final RawBasicBlock primaryBasicblock = priFlowgraph.getBasicBlock(primaryBasicBlockAddr);
      for (final Entry<IAddress, RawInstruction> e :
          primaryBasicblock.getInstructions().entrySet()) {
        instructionAddrPairs.add(new Pair<>(e.getKey(), null));
      }
    } else if (secondaryBasicBlockAddr != null) {
      final RawBasicBlock secondaryBasicblock = secFlowgraph.getBasicBlock(secondaryBasicBlockAddr);
      for (final Entry<IAddress, RawInstruction> e :
          secondaryBasicblock.getInstructions().entrySet()) {
        instructionAddrPairs.add(new Pair<>(null, e.getKey()));
      }
    }

    return instructionAddrPairs;
  }

  private static List<Pair<IAddress, IAddress>>
      getMatchedFunctionsBasicBlockInstructionAddressPairs(
          final FunctionMatchData functionMatch,
          final RawCombinedFlowGraph<RawCombinedBasicBlock, RawCombinedJump<RawCombinedBasicBlock>>
              combinedFlowGraph,
          final IAddress primaryBasicBlockAddr,
          final IAddress secondaryBasicBlockAddr,
          final ESide sortBy) {
    List<Pair<IAddress, IAddress>> instructionAddrPairs =
        new ArrayList<>(
            getMatchedFunctionsBasicBlockInstructionAddressPairs(
                functionMatch, combinedFlowGraph, primaryBasicBlockAddr, secondaryBasicBlockAddr));

    final List<Pair<IAddress, IAddress>> firstNotNullAddrPairs = new ArrayList<>();
    final List<Pair<IAddress, IAddress>> secondNotNullAddrPairs = new ArrayList<>();

    for (Pair<IAddress, IAddress> addrPair : instructionAddrPairs) {
      if (ESide.SECONDARY == sortBy) {
        addrPair = new Pair<>(addrPair.second(), addrPair.first());
      }

      if (addrPair.first() != null) {
        firstNotNullAddrPairs.add(addrPair);
      }

      if (addrPair.second() != null) {
        secondNotNullAddrPairs.add(addrPair);
      }
    }

    firstNotNullAddrPairs.sort(new AddressPairComparator(ESide.PRIMARY));
    secondNotNullAddrPairs.sort(new AddressPairComparator(ESide.SECONDARY));

    instructionAddrPairs.clear();

    if (firstNotNullAddrPairs.size() == 0) {
      instructionAddrPairs = secondNotNullAddrPairs;
    }

    if (secondNotNullAddrPairs.size() == 0) {
      instructionAddrPairs = firstNotNullAddrPairs;
    }

    if (firstNotNullAddrPairs.size() != 0 && secondNotNullAddrPairs.size() != 0) {

      int index = 0;
      for (final Pair<IAddress, IAddress> firstNotNull : firstNotNullAddrPairs) {
        if (firstNotNull.second() == null) {
          instructionAddrPairs.add(firstNotNull); // add first
        } else if (index < secondNotNullAddrPairs.size()) {
          // is MatchPair<IAddress, IAddress>
          boolean loop = true;

          while (loop) {
            final Pair<IAddress, IAddress> secondNotNull = secondNotNullAddrPairs.get(index);

            if (secondNotNull.first() == null) {
              // no Match
              instructionAddrPairs.add(secondNotNull); // add second
            } else {
              loop = false;
            }

            index++;

            if (index >= secondNotNullAddrPairs.size()) {
              loop = false;
            }
          }

          instructionAddrPairs.add(firstNotNull); // add Match
        }
      }
    }

    // adds secondary unmatched tail instructions
    final List<Pair<IAddress, IAddress>> secondTail = new ArrayList<>();
    int i = secondNotNullAddrPairs.size();
    while (i > 0) {
      i--;

      if (secondNotNullAddrPairs.get(i).first() == null) {
        secondTail.add(secondNotNullAddrPairs.get(i));
      } else {
        break;
      }
    }

    secondTail.sort(new AddressPairComparator(ESide.SECONDARY));
    instructionAddrPairs.addAll(secondTail);

    // switch secondary and first (if order by secondary)
    final List<Pair<IAddress, IAddress>> returnPairs = instructionAddrPairs;

    if (ESide.SECONDARY == sortBy) {
      returnPairs.clear();

      for (final Pair<IAddress, IAddress> addrPair : instructionAddrPairs) {
        returnPairs.add(new Pair<>(addrPair.second(), addrPair.first()));
      }
    }

    return returnPairs;
  }

  public static List<ZyLineContent> buildBasicblockComment(
      final RawBasicBlock basicblock, final int insertLineCount) {
    final Color commentColor = EInstructionHighlighting.TYPE_COMMENT.getColor();

    final BasicBlockLineObject lineObject = new BasicBlockLineObject(basicblock);
    final List<ZyLineContent> commentLines = new ArrayList<>();
    final String commentText = basicblock.getComment();

    boolean isFirst = true;

    final CMultiCommentLineTokenizer tokenizer = new CMultiCommentLineTokenizer(commentText, "\n");

    int lineCounter = 0;
    while (tokenizer.hasMoreTokens()) {
      lineCounter++;

      final String token = tokenizer.nextToken();

      String lineText = BASICBLOCK_COMMENT_ESCAPE_STRING + token;
      int commentDelimiterLength = BASICBLOCK_COMMENT_ESCAPE_STRING.length();

      if (isFirst) {
        lineText = BASICBLOCK_COMMENT_HEADLINE_STRING + token;

        commentDelimiterLength = BASICBLOCK_COMMENT_HEADLINE_STRING.length();

        isFirst = false;
      }

      final IZyEditableObject delimiterObject;
      delimiterObject =
          new BasicBlockCommentDelimiterLineObject(basicblock, 0, commentDelimiterLength);

      final IZyEditableObject commentObject;
      commentObject =
          new BasicBlockCommentLineObject(basicblock, commentDelimiterLength, token.length());

      final List<CStyleRunData> styleRun = new ArrayList<>();
      styleRun.add(new CStyleRunData(0, commentDelimiterLength, commentColor, delimiterObject));
      styleRun.add(
          new CStyleRunData(commentDelimiterLength, token.length(), commentColor, commentObject));

      commentLines.add(new ZyLineContent(lineText, Fonts.NORMAL_FONT, styleRun, lineObject));
    }

    if (lineCounter < insertLineCount) {
      commentLines.addAll(
          buildPlaceholderLines(
              insertLineCount - lineCounter, EPlaceholderType.BASIC_BLOCK_COMMENT));
    }

    return commentLines;
  }

  public static List<ZyLineContent> buildInstruction(
      final RawInstruction instruction,
      final int maxOperandLength,
      final boolean isUnmatchedInstruction,
      final ESide side,
      final int topCommentLineCountMax,
      final int rightCommentLineCountMax) {
    final List<ZyLineContent> lines = new ArrayList<>();

    final StringBuffer line = new StringBuffer();
    final List<CStyleRunData> styleRun = new ArrayList<>();

    if (instruction != null) {
      RawInstructionComment aboveComment = null;
      RawInstructionComment trailingComment = null;

      if (instruction.hasComments()) {
        for (final RawInstructionComment comment : instruction.getComments()) {
          if (comment.getPlacement() == ECommentPlacement.ABOVE_LINE) {
            aboveComment = comment;
          } else if (comment.getPlacement() == ECommentPlacement.BEHIND_LINE) {
            trailingComment = comment;
          }
        }
      }

      buildAddress(instruction.getAddress(), line, styleRun);
      buildMnemonic(instruction, line, styleRun);
      buildOperands(instruction, line, styleRun);

      final InstructionObject instructionObject =
          new InstructionObject(instruction, 0, line.length());

      lines.addAll(
          buildTopComment(instruction, instructionObject, aboveComment, topCommentLineCountMax));

      lines.addAll(
          buildTrailingComment(
              instruction,
              instructionObject,
              trailingComment,
              line,
              styleRun,
              maxOperandLength,
              isUnmatchedInstruction,
              side,
              rightCommentLineCountMax));
    } else {
      // Build empty lines if instruction is not matched.
      // Note: Null should be allowed here because there is no instruction from which an instruction
      // object could be created.
      lines.addAll(
          buildPlaceholderLines(
              topCommentLineCountMax, EPlaceholderType.UNMATCHED_ABOVE_INSTRUCTION_COMMENT));
      lines.addAll(buildPlaceholderLines(1, EPlaceholderType.UNMATCHED_INSTRUCTION));
      lines.addAll(
          buildPlaceholderLines(
              rightCommentLineCountMax - 1, EPlaceholderType.UNMATCHED_BEHIND_INSTRUCTION_COMMENT));
    }
    return lines;
  }

  public static List<ZyLineContent> buildPlaceholderLines(
      final int nTimes, final EPlaceholderType placeholderType) {
    final List<ZyLineContent> emptyLines = new ArrayList<>();

    PlaceholderObject placeholderObject = null;

    switch (placeholderType) {
      case MATCHED_ABOVE_INSTRUCTION_COMMENT:
        placeholderObject = PLACEHOLDER_MATCHED_ABOVE_INSTRUCTION_COMMENT;
        break;
      case MATCHED_BEHIND_INSTRUCTION_COMMENT:
        placeholderObject = PLACEHOLDER_MATCHED_BEHIND_INSTRUCTION_COMMENT;
        break;
      case UNMATCHED_ABOVE_INSTRUCTION_COMMENT:
        placeholderObject = PLACEHOLDER_UNMATCHED_ABOVE_INSTRUCTION_COMMENT;
        break;
      case UNMATCHED_INSTRUCTION:
        placeholderObject = PLACEHOLDER_UNMATCHED_INSTRUCTION;
        break;
      case UNMATCHED_BEHIND_INSTRUCTION_COMMENT:
        placeholderObject = PLACEHOLDER_UNMATCHED_BEHIND_INSTRUCTION_COMMENT;
        break;
      case BASIC_BLOCK_COMMENT:
        placeholderObject = PLACEHOLDER_BASIC_BLOCK_COMMENT;
        break;
    }

    for (int index = 0; index < nTimes; ++index) {
      final ZyLineContent emptyLineContent =
          new ZyLineContent("\t", Fonts.NORMAL_FONT, placeholderObject);
      emptyLines.add(emptyLineContent);
    }

    return emptyLines;
  }

  public static void buildSingleCodeNodeContent(
      final FunctionMatchData functionMatch,
      final RawCombinedFlowGraph<RawCombinedBasicBlock, RawCombinedJump<RawCombinedBasicBlock>>
          combinedFlowgraph,
      final RawCombinedBasicBlock combinedBasicblock,
      final ZyLabelContent basicblockContent,
      final ESide side) {
    final RawBasicBlock primaryBasicblock = combinedBasicblock.getRawNode(ESide.PRIMARY);
    final RawBasicBlock secondaryBasicblock = combinedBasicblock.getRawNode(ESide.SECONDARY);

    if (side == ESide.PRIMARY) {
      // unmatched basic block
      if (secondaryBasicblock == null) {
        buildUnmatchedCodeNodeContent(primaryBasicblock, basicblockContent);
        return;
      }
    } else {
      // unmatched basic block
      if (primaryBasicblock == null) {
        buildUnmatchedCodeNodeContent(secondaryBasicblock, basicblockContent);
        return;
      }
    }

    // matched basic block
    List<Pair<IAddress, IAddress>> instructionAddrPairs;

    final IAddress priBasicBlockAddr = combinedBasicblock.getAddress(ESide.PRIMARY);
    final IAddress secBasicBlockAddr = combinedBasicblock.getAddress(ESide.SECONDARY);

    instructionAddrPairs =
        getMatchedFunctionsBasicBlockInstructionAddressPairs(
            functionMatch, combinedFlowgraph, priBasicBlockAddr, secBasicBlockAddr, ESide.PRIMARY);

    final RawBasicBlock priBasicBlock =
        combinedFlowgraph.getPrimaryFlowgraph().getBasicBlock(priBasicBlockAddr);
    final RawBasicBlock secBasicBlock =
        combinedFlowgraph.getSecondaryFlowgraph().getBasicBlock(secBasicBlockAddr);

    buildMatchedCodeNodeContent(
        priBasicBlock, secBasicBlock, instructionAddrPairs, basicblockContent, side);
  }

  public static int precalcMaxCommentLineCount(
      final RawInstruction primary,
      final RawInstruction secondary,
      final ECommentPlacement placement) {
    int primaryLines = placement == ECommentPlacement.BEHIND_LINE ? 1 : 0;
    int secondaryLines = placement == ECommentPlacement.BEHIND_LINE ? 1 : 0;

    if (primary != null && primary.hasComments()) {
      for (final RawInstructionComment comment : primary.getComments()) {
        if (placement == comment.getPlacement() && !comment.getText().isEmpty()) {
          primaryLines = StringHelper.count(comment.getText(), '\n') + 1;
        }
      }
    }

    if (secondary != null && secondary.getComments() != null) {
      for (final RawInstructionComment comment : secondary.getComments()) {
        if (placement == comment.getPlacement() && !comment.getText().isEmpty()) {
          secondaryLines = StringHelper.count(comment.getText(), '\n') + 1;
        }
      }
    }
    return Math.max(primaryLines, secondaryLines);
  }
}
