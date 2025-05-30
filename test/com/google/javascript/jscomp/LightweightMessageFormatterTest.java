/*
 * Copyright 2007 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.LightweightMessageFormatter.LineNumberingFormatter;
import com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LightweightMessageFormatterTest {
  private static final DiagnosticType FOO_TYPE =
      DiagnosticType.error("TEST_FOO", "error description here");
  private static final String ORIGINAL_SOURCE_FILE = "original/source.html";
  private static final OriginalMapping ORIGINAL_SOURCE =
      OriginalMapping.newBuilder()
          .setOriginalFile(ORIGINAL_SOURCE_FILE)
          .setLineNumber(3)
          .setColumnPosition(15)
          .build();

  @Test
  public void testNull() {
    assertThat(format(null)).isNull();
  }

  @Test
  public void testOneLineRegion() {
    assertThat(format(region(5, 5, "hello world"))).isEqualTo("  5| hello world");
  }

  @Test
  public void testTwoLineRegion() {
    assertThat(format(region(5, 6, "hello world\nfoo bar")))
        .isEqualTo(
            """
              5| hello world
              6| foo bar\
            """);
  }

  @Test
  public void testThreeLineRegionAcrossNumberRange() {
    String region = format(region(9, 11, "hello world\nfoo bar\nanother one"));
    assertThat(region)
        .isEqualTo(
            """
               9| hello world
              10| foo bar
              11| another one\
            """);
  }

  @Test
  public void testThreeLineRegionEmptyLine() {
    String region = format(region(7, 9, "hello world\n\nanother one"));
    assertThat(region)
        .isEqualTo(
            """
              7| hello world
              8|\s
              9| another one\
            """);
  }

  @Test
  public void testOnlyOneEmptyLine() {
    assertThat(format(region(7, 7, ""))).isNull();
  }

  @Test
  public void testTwoEmptyLines() {
    assertThat(format(region(7, 8, "\n"))).isEqualTo("  7| ");
  }

  @Test
  public void testThreeLineRemoveLastEmptyLine() {
    String region = format(region(7, 9, "hello world\nfoobar\n"));
    assertThat(region)
        .isEqualTo(
            """
              7| hello world
              8| foobar\
            """);
  }

  @Test
  public void testFormatErrorSpaces() {
    Node n = Node.newString("foobar").setLinenoCharno(5, 8);
    n.setLength("foobar".length());
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {");
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:8: ERROR - [TEST_FOO] error description here
                if (foobar) {
                    ^^^^^^
            """);
  }

  @Test
  public void testFormatErrorTabs() {
    Node n = Node.newString("foobar").setLinenoCharno(5, 6);
    n.setLength("foobar".length());
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("\t\tif (foobar) {");
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:6: ERROR - [TEST_FOO] error description here
            \t\tif (foobar) {
            \t\t    ^^^^^^
            """);
  }

  @Test
  public void testFormatErrorSpaceEndOfLine1() {
    JSError error = JSError.make("javascript/complex.js", 1, 10, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("assert (1;");
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:1:10: ERROR - [TEST_FOO] error description here
            assert (1;
                      ^
            """);
  }

  @Test
  public void testFormatErrorSpaceEndOfLine2() {
    JSError error = JSError.make("javascript/complex.js", 6, 7, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("if (foo");
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:6:7: ERROR - [TEST_FOO] error description here
            if (foo
                   ^
            """);
  }

  @Test
  public void testFormatErrorOriginalSource() {
    Node n = Node.newString("foobar").setLinenoCharno(5, 8);
    n.setLength("foobar".length());
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {", "<div ng-show='(foo'>");
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:8:\s
            Originally at:
            original/source.html:3:15: ERROR - [TEST_FOO] error description here
            <div ng-show='(foo'>
                           ^^^^^
            """);
  }

  @Test
  public void testMultiline_oneLineErrorMessage() {
    Node n = Node.newString("foobar").setLinenoCharno(5, 8);
    n.setLength("foobar".length());
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {", SourceExcerpt.FULL, 5);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:8: ERROR - [TEST_FOO] error description here
              5|     if (foobar) {
                         ^^^^^^
            """);
  }

  @Test
  public void testMultiline_twoLineErrorMessage() {
    Node foobar = IR.string("foobar");
    Node baz = IR.string("baz");
    Node orNode = IR.or(foobar, baz);
    orNode.setLinenoCharno(5, 4);
    orNode.setLength("foobar\n      || baz".length());
    orNode.setSourceFileForTesting("javascript/complex.js");

    JSError error = JSError.make(orNode, FOO_TYPE);
    LightweightMessageFormatter formatter =
        formatter("if (foobar\n      || baz) {", SourceExcerpt.FULL, 6);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:4: ERROR - [TEST_FOO] error description here
              5| if (foobar
                     ^^^^^^
              6|       || baz) {
                 ^^^^^^^^^^^^
            """);
  }

  @Test
  public void testMultiline_charNoOutOfBoundsCrashes() {
    Node n = Node.newString("foobar").setLinenoCharno(5, 800);
    n.setLength("foobar".length());
    n.setSourceFileForTesting("javascript/complex.js");
    JSError error = JSError.make(n, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("    if (foobar) {", SourceExcerpt.FULL, 5);

    assertThrows(Exception.class, () -> formatter.formatError(error));
  }

  @Test
  public void testMultiline_longTruncatedErrorMessage() {
    Node qname = IR.getprop(IR.name("a"), "b", "c", "d", "e");
    qname.setLinenoCharno(8, 0);
    qname.setLength("a\n .b\n .c\n .d\n .e".length());
    qname.setSourceFileForTesting("javascript/complex.js");

    JSError error = JSError.make(qname, FOO_TYPE);
    LightweightMessageFormatter formatter =
        formatter("a\n .b\n .c\n .d\n .e + 1;", SourceExcerpt.FULL, 12);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:8:0: ERROR - [TEST_FOO] error description here
               8| a
                  ^
               9|  .b
                  ^^^
            ...
              11|  .d
                  ^^^
              12|  .e + 1;
                  ^^^
            """);
  }

  @Test
  public void testMultiline_nodeLengthOutOfBounds() {
    Node foobar = Node.newString("foobar").setLinenoCharno(5, 2);
    foobar.setLength("foobar".length());
    Node baz = Node.newString("baz").setLinenoCharno(6, 6);
    baz.setLength("baz".length());
    Node orNode = IR.or(foobar, baz);
    orNode.setLinenoCharno(5, 4);
    orNode.setLength(1000); // intentionally too long
    orNode.setSourceFileForTesting("javascript/complex.js");

    JSError error = JSError.make(orNode, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("if (foobar", SourceExcerpt.FULL, 5);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:4: ERROR - [TEST_FOO] error description here
              5| if (foobar
                     ^^^^^^
            """);
  }

  @Test
  public void testMultiline_errorWithoutAssociatedNode() {
    JSError error = JSError.make("javascript/complex.js", 5, 4, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("if (foobar", SourceExcerpt.FULL, 5);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5:4: ERROR - [TEST_FOO] error description here
              5| if (foobar
                     ^
            """);
  }

  @Test
  public void testMultiline_errorWithoutAssociatedNodeAndNegativeChar() {
    JSError error = JSError.make("javascript/complex.js", 5, -1, FOO_TYPE);
    LightweightMessageFormatter formatter = formatter("if (foobar", SourceExcerpt.FULL, 5);
    assertThat(formatter.formatError(error))
        .isEqualTo(
            """
            javascript/complex.js:5: ERROR - [TEST_FOO] error description here
              5| if (foobar
            """);
  }

  private LightweightMessageFormatter formatter(String string, SourceExcerpt excerpt, int endLine) {
    return new LightweightMessageFormatter(source(string, null, endLine), excerpt);
  }

  private LightweightMessageFormatter formatter(String string) {
    return new LightweightMessageFormatter(source(string, null));
  }

  private LightweightMessageFormatter formatter(String string, String originalSource) {
    return new LightweightMessageFormatter(source(string, originalSource));
  }

  private SourceExcerptProvider source(final String source, final @Nullable String originalSource) {
    return source(source, originalSource, -1);
  }

  private SourceExcerptProvider source(
      final String source, final @Nullable String originalSource, final int endLineNumber) {
    return new SourceExcerptProvider() {
      @Override
      public String getSourceLine(String sourceName, int lineNumber) {
        if (sourceName.equals(ORIGINAL_SOURCE_FILE)) {
          return originalSource;
        }
        return source;
      }

      @Override
      public Region getSourceLines(String sourceName, int lineNumber, int length) {
        checkState(endLineNumber != -1, "Must provide the end of the source lines");
        if (sourceName.equals(ORIGINAL_SOURCE_FILE)) {
          return new SimpleRegion(lineNumber, endLineNumber, originalSource);
        }
        return new SimpleRegion(lineNumber, endLineNumber, source);
      }

      @Override
      public Region getSourceRegion(String sourceName, int lineNumber) {
        throw new UnsupportedOperationException();
      }

      @Override
      public OriginalMapping getSourceMapping(String sourceName, int lineNumber, int columnNumber) {
        if (originalSource != null) {
          return ORIGINAL_SOURCE;
        }
        return null;
      }
    };
  }

  private String format(@Nullable Region region) {
    return new LineNumberingFormatter().formatRegion(region);
  }

  private Region region(final int startLine, final int endLine, final String source) {
    return new SimpleRegion(startLine, endLine, source);
  }
}
