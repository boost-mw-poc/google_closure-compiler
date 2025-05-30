/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.javascript.jscomp.ExtractPrototypeMemberDeclarations.Pattern;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExtractPrototypeMemberDeclarations}. */
@RunWith(JUnit4.class)
public final class ExtractPrototypeMemberDeclarationsTest extends CompilerTestCase {
  private static final String TMP = "JSCompiler_prototypeAlias";
  private Pattern pattern = Pattern.USE_GLOBAL_TEMP;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    pattern = Pattern.USE_GLOBAL_TEMP;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ExtractPrototypeMemberDeclarations(compiler, pattern);
  }

  @Test
  public void testNotEnoughPrototypeToExtract() {
    // switch statement with stuff after "return"
    for (int i = 0; i < 7; i++) {
      testSame(generatePrototypeDeclarations("x", i));
    }
  }

  @Test
  public void testExtractingSingleClassPrototype() {
    extract(
        generatePrototypeDeclarations("x", 7),
        loadPrototype("x") + generateExtractedDeclarations(7));
  }

  @Test
  public void testNoOpOnEs6Class() {
    testSame("class Example { method1() {} method2() {} }");
    testSame("export class Example { method1() {} method2() {} }");
  }

  @Test
  public void testClassDefinedInBlock() {
    test(
        """
        {
        PROTYPE_DECLARATIONS
        }
        """
            .replace("PROTYPE_DECLARATIONS", generatePrototypeDeclarations("x", 7)),
        """
        var TMP;
        {
        TMP = x.prototype;
        EXTRACTED_DECLARATIONS
        }
        """
            .replace("TMP", TMP)
            .replace("EXTRACTED_DECLARATIONS", generateExtractedDeclarations(7)));
  }

  @Test
  public void testClassDefinedInFunction() {
    testSame(
        """
        function f() {
        PROTYPE_DECLARATIONS
        }
        """
            .replace("PROTYPE_DECLARATIONS", generatePrototypeDeclarations("x", 7)));
  }

  /** Currently, this does not run on classes defined in ES6 modules. */
  // TODO(tbreisacher): Make this work on modules. The initial 'var' needs to go into a non-module
  // script node.
  @Test
  public void testEs6Module() {
    ignoreWarnings(LOAD_WARNING);
    String importStatement = "import {someValue} from './another_module.js';";
    testSame(importStatement + generatePrototypeDeclarations("x", 17));
  }

  @Test
  public void testExtractingTwoClassPrototype() {
    extract(
        generatePrototypeDeclarations("x", 6) + generatePrototypeDeclarations("y", 6),
        loadPrototype("x")
            + generateExtractedDeclarations(6)
            + loadPrototype("y")
            + generateExtractedDeclarations(6));
  }

  @Test
  public void testExtractingTwoClassPrototypeInDifferentBlocks() {
    extract(
        generatePrototypeDeclarations("x", 6)
            + "if (foo()) {"
            + generatePrototypeDeclarations("y", 6)
            + "}",
        loadPrototype("x")
            + generateExtractedDeclarations(6)
            + "if (foo()) {"
            + loadPrototype("y")
            + generateExtractedDeclarations(6)
            + "}");
  }

  @Test
  public void testNoMemberDeclarations() {
    testSame(
        """
        x.prototype = {}; x.prototype = {}; x.prototype = {};
        x.prototype = {}; x.prototype = {}; x.prototype = {};
        x.prototype = {}; x.prototype = {}; x.prototype = {};
        """);
  }

  @Test
  public void testExtractingPrototypeWithQName() {
    extract(
        generatePrototypeDeclarations("com.google.javascript.jscomp.x", 7),
        loadPrototype("com.google.javascript.jscomp.x") + generateExtractedDeclarations(7));
  }

  @Test
  public void testInterweaved() {
    testSame(
        """
        x.prototype.a=1; y.prototype.a=1;
        x.prototype.b=1; y.prototype.b=1;
        x.prototype.c=1; y.prototype.c=1;
        x.prototype.d=1; y.prototype.d=1;
        x.prototype.e=1; y.prototype.e=1;
        x.prototype.f=1; y.prototype.f=1;
        """);
  }

  @Test
  public void testExtractingPrototypeWithNestedMembers() {
    extract(
        """
        x.prototype.y.a = 1;
        x.prototype.y.b = 1;
        x.prototype.y.c = 1;
        x.prototype.y.d = 1;
        x.prototype.y.e = 1;
        x.prototype.y.f = 1;
        x.prototype.y.g = 1;
        """,
        loadPrototype("x")
            + TMP
            + ".y.a = 1;"
            + TMP
            + ".y.b = 1;"
            + TMP
            + ".y.c = 1;"
            + TMP
            + ".y.d = 1;"
            + TMP
            + ".y.e = 1;"
            + TMP
            + ".y.f = 1;"
            + TMP
            + ".y.g = 1;");
  }

  @Test
  public void testWithDevirtualization() {
    extract(
        """
        x.prototype.a = 1;
        x.prototype.b = 1;
        function devirtualize1() { }
        x.prototype.c = 1;
        x.prototype.d = 1;
        x.prototype.e = 1;
        x.prototype.f = 1;
        x.prototype.g = 1;
        """,
        loadPrototype("x")
            + TMP
            + ".a = 1;"
            + TMP
            + ".b = 1;\n"
            + "function devirtualize1() { }"
            + TMP
            + ".c = 1;"
            + TMP
            + ".d = 1;"
            + TMP
            + ".e = 1;"
            + TMP
            + ".f = 1;"
            + TMP
            + ".g = 1;");

    extract(
        """
        x.prototype.a = 1;
        x.prototype.b = 1;
        function devirtualize1() { }
        x.prototype.c = 1;
        x.prototype.d = 1;
        function devirtualize2() { }
        x.prototype.e = 1;
        x.prototype.f = 1;
        function devirtualize3() { }
        x.prototype.g = 1;
        """,
        loadPrototype("x")
            + TMP
            + ".a = 1;"
            + TMP
            + ".b = 1;\n"
            + "function devirtualize1() { }"
            + TMP
            + ".c = 1;"
            + TMP
            + ".d = 1;\n"
            + "function devirtualize2() { }"
            + TMP
            + ".e = 1;"
            + TMP
            + ".f = 1;\n"
            + "function devirtualize3() { }"
            + TMP
            + ".g = 1;");
  }

  @Test
  public void testAnonSimple() {
    pattern = Pattern.USE_IIFE;

    extract(
        generatePrototypeDeclarations("x", 3),
        generateExtractedDeclarations(3) + loadPrototype("x"));

    testSame(generatePrototypeDeclarations("x", 1));
    testSame(generatePrototypeDeclarations("x", 2));

    extract(
        generatePrototypeDeclarations("x", 7),
        generateExtractedDeclarations(7) + loadPrototype("x"));
  }

  @Test
  public void testAnonWithDevirtualization() {
    pattern = Pattern.USE_IIFE;

    extract(
        """
        x.prototype.a = 1;
        x.prototype.b = 1;
        function devirtualize() { }
        x.prototype.c = 1;
        """,
        "(function("
            + TMP
            + ") {"
            + TMP
            + ".a = 1;"
            + TMP
            + ".b = 1;"
            + TMP
            + ".c = 1;"
            + loadPrototype("x")
            + "function devirtualize() { }");

    extract(
        """
        x.prototype.a = 1;
        function devirtualize1() { }
        x.prototype.b = 1;
        function devirtualize2() { }
        x.prototype.c = 1;
        function devirtualize3() { }
        """,
        "(function("
            + TMP
            + ") {"
            + TMP
            + ".a = 1;"
            + TMP
            + ".b = 1;"
            + TMP
            + ".c = 1;"
            + loadPrototype("x")
            + "function devirtualize1() { }\n"
            + "function devirtualize2() { }\n"
            + "function devirtualize3() { }");
  }

  @Test
  public void testAnonWithSideFx() {
    pattern = Pattern.USE_IIFE;
    testSame(
        """
        function foo() {};
        foo.prototype.a1 = 1;
        bar();;
        foo.prototype.a2 = 2;
        bar();;
        foo.prototype.a3 = 3;
        bar();;
        foo.prototype.a4 = 4;
        bar();;
        foo.prototype.a5 = 5;
        bar();;
        foo.prototype.a6 = 6;
        bar();;
        foo.prototype.a7 = 7;
        bar();
        """);
  }

  @Test
  public void testNotEnoughPrototypeToExtractInChunk() {
    pattern = Pattern.USE_CHUNK_TEMP;
    for (int i = 0; i < 7; i++) {
      JSChunk[] chunks =
          JSChunkGraphBuilder.forStar()
              .addChunk(generatePrototypeDeclarations("x", i))
              .addChunk(generatePrototypeDeclarations("y", i))
              .build();
      testSame(srcs(chunks));
    }
  }

  @Test
  public void testExtractingSingleClassPrototypeInChunk() {
    pattern = Pattern.USE_CHUNK_TEMP;
    JSChunk[] chunks =
        JSChunkGraphBuilder.forStar()
            .addChunk(generatePrototypeDeclarations("x", 7))
            .addChunk(generatePrototypeDeclarations("y", 7))
            .build();

    StringBuilder builderX = new StringBuilder();
    StringBuilder builderY = new StringBuilder();
    String xTmp = TMP + "0";
    String yTmp = TMP + "1";
    builderX
        .append(String.format("var %s; %s = x.prototype;", xTmp, xTmp))
        .append(generateExtractedDeclarations(7, 0));
    builderY
        .append(String.format("var %s; %s = y.prototype;", yTmp, yTmp))
        .append(generateExtractedDeclarations(7, 1));

    JSChunk[] expectedModules =
        JSChunkGraphBuilder.forStar()
            .addChunk(builderX.toString())
            .addChunk(builderY.toString())
            .build();

    test(srcs(chunks), expected(expectedModules));
  }

  private String loadPrototype(String qName) {
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      return TMP + " = " + qName + ".prototype;";
    } else {
      return "})(" + qName + ".prototype);";
    }
  }

  private void extract(String src, String expected) {
    if (pattern == Pattern.USE_GLOBAL_TEMP) {
      test(src, "var " + TMP + ";" + expected);
    } else {
      test(src, expected);
    }
  }

  private String generatePrototypeDeclarations(String className, int num) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < num; i++) {
      char member = (char) ('a' + i);
      builder.append(generatePrototypeDeclaration(className, "" + member, "" + member));
    }
    return builder.toString();
  }

  private String generatePrototypeDeclaration(String className, String member, String value) {
    return className + ".prototype." + member + " = " + value + ";";
  }

  private String generateExtractedDeclarations(int num) {
    return generateExtractedDeclarations(num, null);
  }

  private String generateExtractedDeclarations(int num, @Nullable Integer fileIndex) {
    StringBuilder builder = new StringBuilder();

    String alias = TMP;
    if (pattern == Pattern.USE_IIFE) {
      builder.append("(function(").append(TMP).append("){");
    } else if (pattern == Pattern.USE_CHUNK_TEMP && fileIndex != null) {
      alias += fileIndex.toString();
    }

    for (int i = 0; i < num; i++) {
      char member = (char) ('a' + i);
      builder.append(generateExtractedDeclaration(alias, "" + member, "" + member));
    }
    return builder.toString();
  }

  private static String generateExtractedDeclaration(String alias, String member, String value) {
    return alias + "." + member + " = " + value + ";";
  }
}
