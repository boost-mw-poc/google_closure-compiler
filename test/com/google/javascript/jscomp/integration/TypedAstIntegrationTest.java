/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.IncrementalCheckMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.SegmentOfCompilationToRun;
import com.google.javascript.jscomp.CrossChunkMethodMotion;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSChunk;
import com.google.javascript.jscomp.ModuleIdentifier;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that run the optimizer over individual library TypedAST shards */
@RunWith(JUnit4.class)
public final class TypedAstIntegrationTest extends IntegrationTestCase {

  private ArrayList<Path> shards;
  private ArrayList<SourceFile> stubExternFiles;
  private ArrayList<SourceFile> stubSourceFiles;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    this.shards = new ArrayList<>();
    this.stubExternFiles = new ArrayList<>();
    this.stubSourceFiles = new ArrayList<>();
  }

  @Test
  public void compilerGeneratesErrorReportWithoutCrashing() throws IOException {
    SourceFile lib1 =
        code("\n\n class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 =
        code("\n\n class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("\n\n alert(new Lib1().m()); \n\n alert(new Lib2().n());"));
    // assigning an instance of Lib1 to a variable of type 'string' causes the disambiguator to
    // 'invalidate' the type of Lib1 and any associated properties.
    SourceFile invalidating =
        code("/** @suppress {checkTypes} @type {string} */ \n\n\n const str = new Lib1();");
    precompileLibrary(typeSummary(lib1), invalidating);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);
    options.setPropertiesThatMustDisambiguate(ImmutableSet.of("m"));

    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    assertThat(compiler.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            "Property 'm' was required to be disambiguated but was invalidated."
            );

    // This code path pipes through the {@link ErrorManager} which extends the
    // {@link BasicErrorManager}. The {@link BasicErrorManager} uses a {@link
    // LightweightMessageFormatter} to format the error messages seen by the compiler. When doing
    // so, it tries to format the error message using the compiler which extends the {@link
    // SourceExceptProvider} to attach the relevant snippet of the source files. It invokes
    // {@link SourceExceptProvider}'s methods such as {@link getSourceLine()}, etc to get the source
    // code. For stage 2 and stage 3 passes, the compiler (and these tests) does not receive the
    // source files. So, the {@link SourceExceptProvider} backs off from reading the source files.
    // TODO(b/379868495): The `JSC_DISAMBIGUATE2_PROPERTY_INVALIDATION` error in this test is not a
    // good example to test the crashing behavior when it is reported by the compiler. It is not a
    // good example because it is a `JSError` that is created in the disambiguator pass without
    // source details (line number, column number, etc) -
    // https://source.corp.google.com/piper///depot/google3/third_party/java_src/jscomp/java/com/google/javascript/jscomp/disambiguate/DisambiguateProperties.java;rcl=665086807;l=235. When such an error is formatted, the {@link
    // LightweightMessageFormatter}
    // anyway backs off from extracting the source snippet and formatting it -
    // https://source.corp.google.com/piper///depot/google3/third_party/java_src/jscomp/java/com/google/javascript/jscomp/LightweightMessageFormatter.java;rcl=645071015;l=175.
    // A better example would be an error that is reported in the stage 2 or stage 3 pass and
    // includes the relevant source code details.
    compiler.generateReport();
  }

  @Test
  public void simpleAlertCall() throws IOException {
    precompileLibrary(extern(new TestExternsBuilder().addAlert().build()), code("alert(10);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "alert(10);");
  }

  @Test
  public void alertCallWithCrossLibraryVarReference() throws IOException {
    SourceFile lib1 = code("const lib1Global = 10;");
    precompileLibrary(lib1);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        code("alert(lib1Global);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "", "alert(10);");
  }

  @Test
  public void disambiguatesGoogScopeAcrossLibraries() throws IOException {

    SourceFile lib1 = code("goog.scope(function () { var x = 3; });");
    SourceFile lib2 = code("goog.scope(function () { var x = 4; });");
    SourceFile externs = extern(new TestExternsBuilder().addClosureExterns().build());

    precompileLibrary(externs);
    precompileLibrary(typeSummary(externs), lib1);
    precompileLibrary(typeSummary(externs), lib2);

    CompilerOptions options = new CompilerOptions();
    options.setClosurePass(true);

    Compiler compiler = compileTypedAstShards(options);
    assertCompiledCodeEquals(
        compiler, "var $jscomp$scope$1954846972$0$x=3;", "var $jscomp$scope$1954846973$0$x=4");
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries() throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "", "", "alert('lib1'); alert('lib2')");
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries_withTranspilation() throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "", "", "alert('lib1'); alert('lib2')");
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries_skippedIfInvalidatingTypeError()
      throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));
    // assigning an instance of Lib1 to a variable of type 'string' causes the disambiguator to
    // 'invalidate' the type of Lib1 and any associated properties.
    SourceFile invalidating =
        code("/** @suppress {checkTypes} @type {string} */ const str = new Lib1();");
    precompileLibrary(typeSummary(lib1), invalidating);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);
    options.setPropertiesThatMustDisambiguate(ImmutableSet.of("m"));

    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    assertThat(compiler.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.DESCRIPTION_EQUALITY)
        .containsExactly(
            "Property 'm' was required to be disambiguated but was invalidated."
            );
  }

  @Test
  public void disambiguatesAndDeletesMethodsAcrossLibraries_ignoresInvalidationsInUnusedShards()
      throws IOException {
    SourceFile lib1 = code("class Lib1 { m() { return 'lib1'; } n() { return 'delete me'; } }");
    SourceFile lib2 = code("class Lib2 { m() { return 'delete me'; } n() { return 'lib2'; } }");
    precompileLibrary(lib1);
    precompileLibrary(lib2);
    precompileLibrary(
        extern(new TestExternsBuilder().addAlert().build()),
        typeSummary(lib1),
        typeSummary(lib2),
        code("alert(new Lib1().m()); alert(new Lib2().n());"));
    // assigning an instance of Lib1 to a variable of type 'string' causes the disambiguator to
    // 'invalidate' the type of Lib1 and any associated properties.
    SourceFile invalidating =
        code("/** @suppress {checkTypes} @type {string} */ const str = new Lib1();");
    precompileLibrary(typeSummary(lib1), invalidating);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);
    options.setPropertiesThatMustDisambiguate(ImmutableSet.of("m"));

    // Drop the invalidating source from the list of SourceFile inputs to JSCompiler.
    // However, leave in the associated TypedAST in this.shards.
    // We want to verify that JSCompiler is able to disambiguate properties on Lib1 despite the
    // invalidation in the unused TypedAST shard.
    Preconditions.checkState(
        Objects.equals(this.stubSourceFiles.get(3).getName(), invalidating.getName()),
        this.stubSourceFiles);
    this.stubSourceFiles.remove(3);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "", "", "alert('lib1'); alert('lib2')");
  }

  @Test
  public void exportAnnotationOnPropertyPreventsRenaming() throws IOException {
    SourceFile externs = extern(new TestExternsBuilder().addAlert().build());
    SourceFile lib1 =
        code(
            """
            class C {
              constructor(foo, bar) {
                this.foo = foo;
                this.bar = bar;
              }
            }
            alert(new C(1, 2))
            """);
    SourceFile lib2 = code("const obj = { /** @export */ foo: 0, bar: 1};");
    precompileLibrary(externs, lib1);
    precompileLibrary(lib2);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(
        compiler,
        """
        class a {
        constructor() { this.foo = 1; }
        }
        alert(new a());
        """,
        "");
  }

  @Test
  public void exportAnnotationOnProperty_ignoredInUnusedTypedAstShard() throws IOException {
    SourceFile externs = extern(new TestExternsBuilder().addAlert().build());
    SourceFile lib1 =
        code(
            """
            class C {
              constructor(foo, bar) {
                this.foo = foo;
                this.bar = bar;
              }
            }
            alert(new C(1, 2))
            """);
    SourceFile unusedLib = code("const obj = { /** @export */ foo: 0, bar: 1};");
    precompileLibrary(externs, lib1);
    precompileLibrary(unusedLib);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    options.setDisambiguateProperties(true);

    // Drop the unusedLib source from the list of SourceFile inputs to JSCompiler.
    // However, leave in the associated TypedAST in this.shards.
    // We want to verify that JSCompiler does /not/ pay attention to the @export in
    // the unusedLib file, as it's not part of the compilation.
    Preconditions.checkState(this.stubSourceFiles.size() == 2, this.stubSourceFiles);
    Preconditions.checkState(this.shards.size() == 2, this.shards);
    this.stubSourceFiles.remove(1);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(
        compiler,
        """
        class a {}
        alert(new a());
        """);
  }

  @Test
  public void exportSymbol_preventsVariableRenamingCollision() throws IOException {
    SourceFile lib1 =
        code("/** @fileoverview @suppress {checkTypes} */ var goog, x; goog.exportSymbol('a', x);");
    precompileLibrary(lib1);

    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(false);
    options.setClosurePass(true);
    options.setVariableRenaming(VariableRenamingPolicy.ALL);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(
        compiler,
        """
        var b;
        var c;
        b.exportSymbol('a', c);
        """);
  }

  @Test
  public void lateFulfilledGlobalVariableIsRenamed() throws IOException {
    SourceFile lib1 =
        code(
            """
            function lib1() {
              if (typeof lib2Var !== 'undefined') {
                alert(lib2Var);
              }
            }
            """);
    precompileLibrary(extern(new TestExternsBuilder().addAlert().build()), lib1);
    precompileLibrary(typeSummary(lib1), code("var lib2Var = 10; lib1();"));

    CompilerOptions options = new CompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGeneratePseudoNames(true);

    Compiler compiler = compileTypedAstShards(options);

    String[] expected =
        new String[] {
          """
          function $lib1$$() {
            if (typeof $lib2Var$$ !== 'undefined') {
              alert($lib2Var$$);
            }
          }
          """,
          "var $lib2Var$$ = 10; $lib1$$();"
        };
    assertCompiledCodeEquals(compiler, expected);
  }

  @Test
  public void lateFulfilledNameReferencedInExternsAndCode_notRenamed() throws IOException {
    // tests an edge case with the "RemoveUnnecessarySyntheticExterns" pass
    // it can't remove the synthetic externs definition of "lateDefinedVar" or the compiler will
    // crash after variable renaming.

    // both externs and code have bad references to the same lateDefinedVar
    precompileLibrary(
        extern(
            """
            /** @fileoverview @suppress {externsValidation,checkVars} */
            lateDefinedVar;
            """),
        code(
            """
            /** @fileoverview @suppress {checkVars,uselessCode} */
            lateDefinedVar;
            """));
    // and another, entirely separate library defines it.
    precompileLibrary(code("var lateDefinedVar; var normalVar;"));

    CompilerOptions options = new CompilerOptions();
    options.setVariableRenaming(VariableRenamingPolicy.ALL);
    options.setGeneratePseudoNames(true);
    options.setProtectHiddenSideEffects(true);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "lateDefinedVar;", "var lateDefinedVar; var $normalVar$$;");
  }

  @Test
  public void externJSDocPropertiesNotRenamed() throws IOException {
    precompileLibrary(
        extern(
            new TestExternsBuilder()
                .addExtra(
                    """
                    /** @typedef {{x: number, y: number}} */
                    let Coord;
                    /** @param {!Coord} coord */
                    function takeCoord(coord) {}
                    """)
                .build()),
        code("const coord = {x: 1, y: 2}; takeCoord(coord);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "takeCoord({x: 1, y: 2});");
  }

  @Test
  public void gatherExternProperties() throws IOException {
    precompileLibrary(
        extern(
            new TestExternsBuilder()
                .addExtra(
                    """
                    /** @fileoverview @externs */
                    var ns = {};
                    ns.x;
                    """)
                .addConsole()
                .build()),
        code("ns.nonExternProperty = 2; console.log(ns.x); console.log(ns.nonExternProperty);"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "ns.a = 2; console.log(ns.x);console.log(ns.a);");
  }

  @Test
  public void testDefineCheck() throws IOException {
    precompileLibrary(code(""));

    CompilerOptions options = new CompilerOptions();
    options.setDefineReplacements(ImmutableMap.of("FOOBAR", 1));

    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    assertThat(compiler.getWarnings())
        .comparingElementsUsing(JSCompCorrespondences.OWNING_DIAGNOSTIC_GROUP)
        .containsExactly(DiagnosticGroups.UNKNOWN_DEFINES);
    assertThat(compiler.getErrors()).isEmpty();
  }

  @Test
  public void protectsHiddenSideEffects() throws IOException {
    precompileLibrary(
        extern("const foo = {}; foo.bar;"),
        code("/** @fileoverview @suppress {uselessCode} */ foo.bar;"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "foo.bar");
  }

  @Test
  public void protectsHiddenSideEffects_ignoringProtectSideEffectOption() throws IOException {
    precompileLibrary(
        extern("const foo = {}; foo.bar;"),
        code("/** @fileoverview @suppress {uselessCode} */ foo.bar;"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());
    // Setting this option is, in precompiled_libraries mode, a no-op: we always protect the
    // side effects. This is because at the library-level we always protect side effects.
    // This means that any "hidden side effects" at the library-level are wrapped in a
    // JSCOMPILER_PRESERVE call:
    //   JSCOMPILER_PRESERVE(foo.bar)
    // So optimizations/finalizations must always remove that "JSCOMPILER_PRESERVE" call added
    // during library compilation.
    options.setProtectHiddenSideEffects(false);

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "foo.bar");
  }

  @Test
  public void removesRegExpCallsIfSafe() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addRegExp().build()), code("(/abc/gi).exec('')"));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "");
  }

  @Test
  public void removesRegExpCallsIfUnsafelyReferenced() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addRegExp().addConsole().build()),
        code(
            """
            (/abc/gi).exec('');
            console.log(RegExp.$1);
            """));

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "(/abc/gi).exec(''); console.log(RegExp.$1);");
  }

  @Test
  public void runsJ2clOptimizations() throws IOException {
    SourceFile f =
        SourceFile.fromCode(
            "f.java.js",
            """
            function InternalWidget(){}
            InternalWidget.$clinit = function () {
              InternalWidget.$clinit = function() {};
              InternalWidget.$clinit();
            };
            InternalWidget.$clinit();
            """);
    stubSourceFiles.add(f);
    precompileLibrary(f);

    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDependencyOptions(DependencyOptions.none());

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(compiler, "");
  }

  @Test
  public void testAngularPass() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().build()),
        code(
            """
            /** @ngInject */ function f() {}
            /** @ngInject */ function g(a){}
            /** @ngInject */ var b = function f(a, b, c) {}
            """));

    CompilerOptions options = new CompilerOptions();

    Compiler compiler = compileTypedAstShards(options);

    assertCompiledCodeEquals(
        compiler,
        """
        function f() {}
        function g(a) {} g['$inject']=['a'];
        var b = function f(a, b, c) {}; b['$inject']=['a', 'b', 'c']
        """);
  }

  @Test
  public void testCrossChunkMethodMotion() throws IOException {
    // run checks & serialize .typedasts
    SourceFile f1 =
        SourceFile.fromCode(
            "f1.js",
            """
            /** @constructor */
            var Foo = function() {};
            Foo.prototype.bar = function() {};
            /** @type {!Foo} */
            var x = new Foo();
            """);
    SourceFile f2 = SourceFile.fromCode("f2.js", "x.bar();");
    precompileLibrary(f1);
    precompileLibrary(typeSummary(f1), f2);

    Compiler compiler = new Compiler();
    // create two chunks, where chunk 2 depends on chunk 1, and they contain f1 and f2
    CompilerOptions options = new CompilerOptions();
    options.setCrossChunkMethodMotion(true);
    compiler.initOptions(options);
    JSChunk chunk1 = new JSChunk("chunk1");
    chunk1.add(f1);
    JSChunk chunk2 = new JSChunk("chunk2");
    chunk2.add(f2);
    chunk2.addDependency(chunk1);

    // run compilation
    try (InputStream inputStream = toInputStream(this.shards)) {
      compiler.initChunksWithTypedAstFilesystem(
          ImmutableList.copyOf(this.stubExternFiles),
          ImmutableList.of(chunk1, chunk2),
          options,
          inputStream);
    }
    compiler.stage2Passes(SegmentOfCompilationToRun.OPTIMIZATIONS);
    compiler.stage3Passes();

    String[] expected =
        new String[] {
          CrossChunkMethodMotion.STUB_DECLARATIONS
              + """
              var Foo = function() {};
              Foo.prototype.bar=JSCompiler_stubMethod(0);
              var x=new Foo;
              """,
          "Foo.prototype.bar=JSCompiler_unstubMethod(0,function(){}); x.bar()",
        };
    assertCompiledCodeEquals(compiler, expected);
  }

  @Test
  public void dependencyModePruningForGoogModules_banned() throws IOException {
    precompileLibrary(
        extern(new TestExternsBuilder().addClosureExterns().build()),
        code("goog.module('keepMe'); const x = 1;"), // input_1
        code("goog.module('entryPoint'); goog.require('keepMe');"), // input_2
        code("goog.module('dropMe'); const x = 3;")); // input_3

    CompilerOptions options = new CompilerOptions();
    options.setDependencyOptions(
        DependencyOptions.pruneForEntryPoints(
            ImmutableList.of(ModuleIdentifier.forClosure("entryPoint"))));

    // TODO(b/219588952): if we decide to support this, verify that JSCompiler no longer incorrectly
    // prunes the 'keepMe' module.
    // This might be fixable by just removing module rewriting from the 'checks' phase.
    Exception ex =
        assertThrows(IllegalArgumentException.class, () -> compileTypedAstShards(options));
    assertThat(ex).hasMessageThat().contains("mode=PRUNE");
  }

  private static final String EXPORT_PROPERTY_DEF =
      """
      goog.exportProperty = function(object, publicName, symbol) {
        object[publicName] = symbol;
      };
      """;

  @Test
  public void testPolymerExportPolicyExportAllClassBased() throws IOException {
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.none());

    SourceFile closureBase =
        SourceFile.fromCode("base.js", "/** @const */ var goog = {};" + EXPORT_PROPERTY_DEF);
    precompileLibrary(closureBase);
    precompileLibrary(
        typeSummary(closureBase),
        extern(new TestExternsBuilder().addString().addPolymer().build()),
        code(
            """
            class FooElement extends PolymerElement {
              static get properties() {
                return {
                  longUnusedProperty: String,
                }
              }
              longUnusedMethod() {
                return this.longUnusedProperty;
              }
            }
            """));

    Compiler compiler = compileTypedAstShards(options);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected it against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");
  }

  @Test
  public void testPolymerExportPolicyExportAllClassBased_inGoogModule() throws IOException {
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.none());

    SourceFile closureBase =
        SourceFile.fromCode(
            "base.js",
            "/** @const */ var goog = {}; goog.module = function(s) {};" + EXPORT_PROPERTY_DEF);
    precompileLibrary(closureBase);
    precompileLibrary(
        typeSummary(closureBase),
        extern(new TestExternsBuilder().addString().addPolymer().build()),
        code(
            """
            goog.module('fooElement');
            class FooElement extends PolymerElement {
              static get properties() {
                return {
                  longUnusedProperty: String,
                }
              }
              longUnusedMethod() {
                return this.longUnusedProperty;
              }
            }
            """));

    Compiler compiler = compileTypedAstShards(options);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected it against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");
  }

  @Test
  public void testPolymer_propertiesOnLegacyElementNotRenamed() throws IOException {
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.none());

    SourceFile closureBase =
        SourceFile.fromCode("base.js", "/** @const */ var goog = {};" + EXPORT_PROPERTY_DEF);
    precompileLibrary(closureBase);
    precompileLibrary(
        typeSummary(closureBase),
        extern(new TestExternsBuilder().addString().addPolymer().build()),
        code(
            """
            Polymer({
              is: "foo-element",
              properties: {
                longUnusedProperty: String,
              },
              longUnusedMethod: function() {
                return this.longUnusedProperty;
              },
            });
            """));

    Compiler compiler = compileTypedAstShards(options);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected them against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");
  }

  @Test
  public void testPolymer_propertiesOnLegacyElementNotRenamed_inGoogModule() throws IOException {
    CompilerOptions options = new CompilerOptions();
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.none());

    SourceFile closureBase =
        SourceFile.fromCode(
            "base.js",
            "/** @const */ var goog = {}; goog.module = function(s) {};" + EXPORT_PROPERTY_DEF);
    precompileLibrary(closureBase);
    precompileLibrary(
        typeSummary(closureBase),
        extern(new TestExternsBuilder().addString().addPolymer().build()),
        code(
            """
            goog.module('fooElement');
            Polymer({
              is: "foo-element",
              properties: {
                longUnusedProperty: String,
              },
              longUnusedMethod: function() {
                return this.longUnusedProperty;
              },
            });
            """));

    Compiler compiler = compileTypedAstShards(options);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected them against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");
  }

  @Test
  public void testDisambiguationForPolymerElementProperties() throws IOException {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDependencyOptions(DependencyOptions.none());
    options.setGeneratePseudoNames(true);
    options.setDisambiguateProperties(true);

    SourceFile closureBase = code("/** @const */ var goog = {};" + EXPORT_PROPERTY_DEF);
    SourceFile polymerExterns =
        extern(
            new TestExternsBuilder()
                .addString()
                .addConsole()
                .addPolymer()
                .addExtra("/** @type {!Global} */ var globalThis;")
                .build());
    SourceFile fooElement =
        code(
            """
            const FooElement = Polymer({
              is: "foo-element",
              properties: {
                longProperty: String,
              },
              longUnusedMethod: function() {
                return this.longProperty;
              },
            });
            class Other { longProperty() {} }
            console.log(new Other().longProperty());
            """);
    precompileLibrary(closureBase); // base library
    precompileLibrary( // polymer dependency library
        typeSummary(closureBase), polymerExterns, fooElement);
    precompileLibrary(
        typeSummary(polymerExterns),
        typeSummary(fooElement),
        code(
            """
            function unused() { console.log(FooElement); }
            /** @param {!FooElement} fooElement */
            globalThis['test'] = function(fooElement) {
              console.log(fooElement.longProperty);
            }
            """));

    Compiler compiler = compileTypedAstShards(options);
    assertCompiledCodeEquals(
        compiler,
        "",
        // Verify that the references to 'longProperty' off the FooElement are never renamed or
        // disambiguated, even when referenced in a differet file, although longProperty on
        // `class Other {` can be renamed/inlined.
        """
        Polymer({
          $is$: 'foo-element',
          $properties$: {longProperty: String},
          longUnusedMethod: function(){return this.longProperty}
        });
        console.log(void 0);
        """,
        """
        globalThis.test = function($fooElement$$) {
          console.log($fooElement$$.longProperty);
        }
        """);
  }

  // use over 'compileTypedAstShards' if you want to validate reported errors or warnings in your
  // @Test case.
  private Compiler compileTypedAstShardsWithoutErrorChecks(CompilerOptions options)
      throws IOException {
    Compiler compiler = new Compiler();
    compiler.initOptions(options);
    try (InputStream inputStream = toInputStream(this.shards)) {
      compiler.initWithTypedAstFilesystem(
          ImmutableList.copyOf(this.stubExternFiles),
          ImmutableList.copyOf(this.stubSourceFiles),
          options,
          inputStream);
    }
    compiler.stage2Passes(SegmentOfCompilationToRun.OPTIMIZATIONS);
    if (!compiler.hasErrors()) {
      compiler.stage3Passes();
    }

    compiler.generateReport();

    return compiler;
  }

  private Compiler compileTypedAstShards(CompilerOptions options) throws IOException {
    Compiler compiler = compileTypedAstShardsWithoutErrorChecks(options);

    checkUnexpectedErrorsOrWarnings(compiler, 0);
    return compiler;
  }

  private SourceFile code(String code) {
    SourceFile sourceFile =
        SourceFile.fromCode("input_" + (stubSourceFiles.size() + 1), code, SourceKind.STRONG);
    SourceFile stubFile = SourceFile.stubSourceFile(sourceFile.getName(), SourceKind.STRONG);
    this.stubSourceFiles.add(stubFile);
    return sourceFile;
  }

  private SourceFile extern(String code) {
    SourceFile sourceFile =
        SourceFile.fromCode("extern_" + (stubExternFiles.size() + 1), code, SourceKind.EXTERN);
    SourceFile stubFile = SourceFile.stubSourceFile(sourceFile.getName(), SourceKind.STRONG);
    this.stubExternFiles.add(stubFile);
    return sourceFile;
  }

  /** Runs the type summary generator on the given source code, and returns a summary file */
  private SourceFile typeSummary(SourceFile original) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setIncrementalChecks(IncrementalCheckMode.GENERATE_IJS);

    compiler.init(ImmutableList.of(), ImmutableList.of(original), options);
    compiler.parse();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    compiler.stage1Passes();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    return SourceFile.fromCode(original.getName(), compiler.toSource());
  }

  private void precompileLibrary(SourceFile... files) throws IOException {
    Path typedAstPath = Files.createTempFile("", ".typedast");

    CompilerOptions options = new CompilerOptions();
    options.setChecksOnly(true);
    options.setAngularPass(true);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProtectHiddenSideEffects(true);
    options.setTypedAstOutputFile(typedAstPath);
    options.setClosurePass(true);
    options.setPolymerVersion(2);

    ImmutableList.Builder<SourceFile> externs = ImmutableList.builder();
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();
    for (SourceFile file : files) {
      if (file.isExtern()) {
        externs.add(file);
      } else {
        sources.add(file);
      }
    }

    Compiler compiler = new Compiler();
    compiler.init(externs.build(), sources.build(), options);
    compiler.parse();
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    compiler.stage1Passes(); // serializes a TypedAST into typedAstPath
    checkUnexpectedErrorsOrWarnings(compiler, 0);

    this.shards.add(typedAstPath);
  }

  /** Converts the list of paths into an input stream sequentially reading all the given files */
  private static InputStream toInputStream(ArrayList<Path> typedAsts) throws IOException {
    ArrayList<InputStream> inputShards = new ArrayList<>();
    for (Path typedAst : typedAsts) {
      // Each shard is a gzipped TypedAst proto.
      // NOTE: while it's generally possible to concatenate individually gzipped files
      // (https://stackoverflow.com/questions/8005114/fast-concatenation-of-multiple-gzip-files)
      // it doesn't seem to work to wrap a SequenceInputStream in a GZIPInputStream directly.
      inputShards.add(new GZIPInputStream(new FileInputStream(typedAst.toFile())));
    }
    return new SequenceInputStream(Collections.enumeration(inputShards));
  }

  private void assertCompiledCodeEquals(Compiler compiler, String... expected) {
    // Passing the default CompilerOptions to parse the expected code is OK, since it configures the
    // parser to support all input languages.
    Node expectedRoot = super.parseExpectedCode(expected, new CompilerOptions());
    assertNode(compiler.getRoot().getSecondChild())
        .usingSerializer(compiler::toSource)
        .isEqualTo(expectedRoot);
  }
}
