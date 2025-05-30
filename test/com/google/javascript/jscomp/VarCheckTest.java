/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.VarCheck.BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR;
import static com.google.javascript.jscomp.VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR;
import static com.google.javascript.jscomp.VarCheck.UNDEFINED_EXTERN_VAR_ERROR;
import static com.google.javascript.jscomp.VarCheck.UNDEFINED_VAR_ERROR;
import static com.google.javascript.jscomp.VarCheck.VAR_MULTIPLY_DECLARED_ERROR;
import static com.google.javascript.jscomp.testing.ScopeSubject.assertScope;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VarCheckTest extends CompilerTestCase {
  private static final String EXTERNS = "var window; function alert() {}";

  private CheckLevel strictModuleDepErrorLevel;
  private boolean validityCheck = false;

  private @Nullable CheckLevel externValidationErrorLevel;

  private boolean declarationCheck;

  public VarCheckTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // Setup value set by individual tests to the appropriate defaults.
    allowExternsChanges();
    strictModuleDepErrorLevel = CheckLevel.OFF;
    externValidationErrorLevel = null;
    validityCheck = false;
    declarationCheck = false;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setClosurePass(true);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK, strictModuleDepErrorLevel);
    if (externValidationErrorLevel != null) {
      options.setWarningLevel(DiagnosticGroups.EXTERNS_VALIDATION, externValidationErrorLevel);
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        new VarCheck(compiler, validityCheck).process(externs, root);
        if (!validityCheck && !compiler.hasErrors()) {
          // If the original test turned off sanity check, make sure our synthesized
          // code passes it.
          new VarCheck(compiler, true).process(externs, root);
        }
        if (declarationCheck) {
          new VariableTestCheck(compiler).process(externs, root);
        }
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    // Because we synthesize externs, the second pass won't emit a warning.
    return 1;
  }

  @Test
  public void testShorthandObjLit() {
    testError("var x = {y};", VarCheck.UNDEFINED_VAR_ERROR);
    testSame("var {x} = {x: 5}; let y = x;");

    testError("var {...x} = {...y};", VarCheck.UNDEFINED_VAR_ERROR);
    testSame("let y; var {...x} = {...y} = {};");
    testSame("var {...x} = {x: 5}; let y = x;");
  }

  @Test
  public void testBreak() {
    testSame("a: while(1) break a;");
  }

  @Test
  public void testContinue() {
    testSame("a: while(1) continue a;");
  }

  @Test
  public void testReferencedVarNotDefined() {
    testError("x = 0;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testReferencedVarNotDefined_typeof() {
    // typeof x !== 'undefined' is the strict mode compliant way to check for a variable's
    // existence.
    testSame("if (typeof undeclaredVariable !== 'undefined') {}");
    // Regression test: the exact pattern emitted by TypeScript to reference possibly declared
    // generic type variables in decorator emit.
    testSame("var _a; typeof (_a = typeof Value !== \"undefined\" && Value) === \"function\"");
  }

  @Test
  public void testReferencedVarNotDefined_arrowFunctionBody() {
    testError("() => y", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testReferencedLetNotDefined() {
    testError("{ let x = 1; } var y = x;", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testReferencedLetNotDefined_withES6Modules() {
    testError("export function f() { { let x = 1; } var y = x; }", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testReferencedLetDefined1() {
    testSame("let x; x = 1;");
  }

  @Test
  public void testNullishCoalesce() {
    testSame("let x; x = 0 ?? true");
    testError("let x; x = a ?? \"hi\"", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testLogicalAssignment() {
    testSame("let x; x ||= 2");
    testError("let x; x ||= a", VarCheck.UNDEFINED_VAR_ERROR);
    // References let, not defined
    testError("{ let x = 1; } var y; y ||= x;", VarCheck.UNDEFINED_VAR_ERROR);
    // Multiple declared vars
    testSame("try { var x = 1; x ||= 2; } catch (x) {}");
    // In externs
    test(externs("x ||= new Klass();"), srcs("class Klass{}"), error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testReferencedLetDefined1_withES6Modules() {
    testSame("export let x; x = 1;");
  }

  @Test
  public void testReferencedLetDefined2() {
    testSame("let x; function y() {x = 1;}");
  }

  @Test
  public void testReferencedConstDefined2() {
    testSame("const x = 1; var y = x + 1;");
  }

  @Test
  public void testReferencedVarDefined1() {
    testSame("var x, y; x=1;");
  }

  @Test
  public void testReferencedVarDefined2() {
    testSame("var x; function y() {x=1;}");
  }

  @Test
  public void testReferencedVarsExternallyDefined() {
    testSame("var x = window; alert(x);");
  }

  @Test
  public void testMultiplyDeclaredVars1() {
    testError("var x = 1; var x = 2;", VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMultiplyDeclaredVars2() {
    testSame("var y; try { y=1 } catch (x) {} try { y=1 } catch (x) {}");
  }

  @Test
  public void testMultiplyDeclaredVars3() {
    testSame("try { var x = 1; x *=2; } catch (x) {}");
  }

  @Test
  public void testMultiplyDeclaredVars4() {
    test(externs("x;"), srcs("var x = 1; var x = 2;"), error(VAR_MULTIPLY_DECLARED_ERROR));
  }

  @Test
  public void testMultiplyDeclaredLets() {
    testError("let x = 1; let x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("let x = 1; var x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("var x = 1; let x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("let {x} = 1; let {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("let {x} = 1; var {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("var {x} = 1; let {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMultiplyDeclaredConsts() {
    testError("const x = 1; const x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("const x = 1; var x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("var x = 1; const x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("const {x} = 1; const {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("const {x} = 1; var {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("var {x} = 1; const {x} = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMultiplyDeclaredConsts_withES6Modules() {
    testError(
        "export function f() { const x = 1; const x = 2; }",
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);

    testError("export const x = 1; export var x = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);

    testError("export const a = 1, a = 2;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMultiplyDeclareLetsInDifferentScope() {
    testSame("let x = 1; if (123) {let x = 2;}");
    testSame("try {let x = 1;} catch(x){}");
  }

  @Test
  public void testMultiplyDeclaredCatchAndVar() {
    // Note: This is technically valid code, but it's difficult to model the scoping rules in the
    // compiler and inconsistent across browsers. We forbid it in VariableReferenceCheck.
    testSame("try {} catch (x) { var x = 1; }");
  }

  @Test
  public void testMultiplyDeclaredCatchAndLet() {
    testError("try {} catch (x) { let x = 1; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMultiplyDeclaredCatchAndConst() {
    testError("try {} catch (x) { const x = 1; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testReferencedVarDefinedClass() {
    testError("var x; class x{ }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("let x; class x{ }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("const x = 1; class x{ }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("class x{ } let x;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("export default class x{ } let x;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testMessagesPointToFirstOccurance() {
    test(
        srcs("var x = 1; var x = 2;"),
        error(VarCheck.VAR_MULTIPLY_DECLARED_ERROR)
            .withMessageContaining("First occurrence: testcode:1:4"));
    test(
        srcs("var x; class x{ }"),
        error(BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR)
            .withMessageContaining("First occurrence: testcode:1:4"));
  }

  @Test
  public void testNamedClass() {
    testSame("class x {}");
    testSame("var x = class x {};");
    testSame("var y = class x {};");
    testSame("var y = class x { foo() { return new x; } };");
    testError("var Foo = class extends Bar {};", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testVarReferenceInExterns() {
    testSame(
        externs("asdf;"),
        srcs("var /** @suppress {duplicate} */ asdf;"),
        warning(VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR));
  }

  @Test
  public void testVarReferenceInExterns2() {
    testSame(
        externs("asdf;"),
        srcs(
            """
            (function() { var asdf; })()
            var /** @suppress {duplicate} */ asdf;
            """),
        warning(VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR));
  }

  @Test
  public void testNamespaceDeclarationInExterns() {
    testSame(externs("/** @const */ var $jscomp = $jscomp || {};"), srcs(""));
  }

  @Test
  public void testCallInExterns() {
    testSame(
        externs("yz();"),
        srcs("function /** @suppress {duplicate} */ yz() {}"),
        warning(VarCheck.NAME_REFERENCE_IN_EXTERNS_ERROR));
  }

  @Test
  public void testDestructuringInExterns() {
    testSame(externs("function externalFunction({x, y}) {}"), srcs(""));
    testSame(externs("function externalFunction({x, y:{z}}) {}"), srcs(""));
    testSame(externs("function externalFunction({x:localName}) {}"), srcs(""));
    testSame(externs("function externalFunction([a, b, c]) {}"), srcs(""));
    testSame(externs("function externalFunction([[...a], b, c = 5, ...d]) {}"), srcs(""));
  }

  @Test
  public void testVarReferenceInExterns_withEs6Modules() {
    // vars in ES6 modules are not in global scope, so foo is undefined.
    testError(externs("foo;"), srcs("export var foo;"), error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testVarDeclarationInExterns() {
    testSame(externs("var asdf;"), srcs("asdf;"));
  }

  @Test
  public void testVarReferenceInTypeSummary() {
    testSame(
        externs(
            """
            /** @typeSummary */
            var goog;
            goog.addSingletonGetter;
            class Foo {}
            goog.addSingletonGetter(Foo);
            """),
        srcs("Foo.getInstance();"));
  }

  @Test
  public void testFunctionDeclarationInExterns() {
    testSame(externs("function foo(x = 7) {}"), srcs("foo();"));
    testSame(externs("function foo(...rest) {}"), srcs("foo(1,2,3);"));
  }

  @Test
  public void testVarAssignmentInExterns() {
    testSame(externs("/** @type{{foo:string}} */ var foo; var asdf = foo;"), srcs("asdf.foo;"));
  }

  @Test
  public void testAliasesInExterns() {
    externValidationErrorLevel = CheckLevel.ERROR;

    testSame(externs("var foo; /** @const */ var asdf = foo;"), srcs(""));
    testSame(externs("var Foo; var ns = {}; /** @const */ ns.FooAlias = Foo;"), srcs(""));
    testSame(
        externs(
            """
            var ns = {}; /** @constructor */ ns.Foo = function() {};
            var ns2 = {}; /** @const */ ns2.Bar = ns.Foo;
            """),
        srcs(""));
  }

  @Test
  public void testDuplicateNamespaceInExterns() {
    // Compare as tree does not allow multiple externs, but VarCheck forcibly inserts them.
    disableCompareAsTree();
    testExternChanges(
        externs("/** @const */ var ns = {}; /** @const */ var ns = {};"),
        srcs(""),
        expected(VAR_CHECK_EXTERNS + "/** @const */ var ns = {};"));
  }

  @Test
  public void testLetDeclarationInExterns() {
    testSame(externs("let asdf;"), srcs("asdf;"));
  }

  @Test
  public void testConstDeclarationInExterns() {
    testSame(externs("const asdf = 1;"), srcs("asdf;"));
  }

  @Test
  public void testNewInExterns() {
    // Class is not hoisted.
    test(externs("x = new Klass();"), srcs("class Klass{}"), error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testPropReferenceInExterns1() {
    testSame(
        externs("asdf.foo;"),
        srcs("var /** @suppress {duplicate} */ asdf;"),
        warning(VarCheck.UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testPropReferenceInExterns2() {
    test(externs("asdf.foo;"), srcs(""), error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testPropReferenceInExterns3() {
    testSame(
        externs("asdf.foo;"),
        srcs("var /** @suppress {duplicate} */ asdf;"),
        warning(VarCheck.UNDEFINED_EXTERN_VAR_ERROR));

    externValidationErrorLevel = CheckLevel.ERROR;
    test(externs("asdf.foo;"), srcs("var asdf;"), error(VarCheck.UNDEFINED_EXTERN_VAR_ERROR));

    externValidationErrorLevel = CheckLevel.OFF;
    testSame(externs("asdf.foo;"), srcs("var asdf;"));
  }

  @Test
  public void testPropReferenceInExterns4() {
    testSame(externs("asdf.foo;"), srcs("let asdf;"), warning(VarCheck.UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testPropReferenceInExterns5() {
    testSame(
        externs("asdf.foo;"), srcs("class asdf {}"), warning(VarCheck.UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testVarInWithBlock() {
    testError("var a = {b:5}; with (a){b;}", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testFunctionDeclaredInBlock() {
    testError("if (true) {function foo() {}} foo();", VarCheck.UNDEFINED_VAR_ERROR);
    testError("foo(); if (true) {function foo() {}}", VarCheck.UNDEFINED_VAR_ERROR);

    testSame("if (true) {var foo = ()=>{}} foo();");
    testError("if (true) {let foo = ()=>{}} foo();", VarCheck.UNDEFINED_VAR_ERROR);
    testError("if (true) {const foo = ()=>{}} foo();", VarCheck.UNDEFINED_VAR_ERROR);

    testSame("foo(); if (true) {var foo = ()=>{}}");
    testError("foo(); if (true) {let foo = ()=>{}}", VarCheck.UNDEFINED_VAR_ERROR);
    testError("foo(); if (true) {const foo = ()=>{}}", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testValidFunctionExpr() {
    testSame("(function() {});");
  }

  @Test
  public void testRecursiveFunction() {
    testSame("(function a() { return a(); })();");
  }

  @Test
  public void testRecursiveFunction2() {
    testSame("var a = 3; (function a() { return a(); })();");
  }

  @Test
  public void testParam() {
    testSame("function fn(a){ var b = a; }");
    testSame("function fn(a){ var a = 2; }");
    testError("function fn(){ var b = a; }", VarCheck.UNDEFINED_VAR_ERROR);

    // Default parameters
    testError("function fn(a = b) { function g(a = 3) { var b; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=a) { let a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=a) { { let a; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=b) { function a(x=1) { var b; } }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=a) { var a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=a()) { function a() {} }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x=[a]) { var a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("function f(x = new foo.bar()) {}", VarCheck.UNDEFINED_VAR_ERROR);
    testSame("var foo = {}; foo.bar = class {}; function f(x = new foo.bar()) {}");

    testSame("function fn(a = 2){ var b = a; }");
    testSame("function fn(a = 2){ var a = 3; }");
    testSame("function fn({a, b}){ var c = a; }");
    testSame("function fn({a, b}){ var a = 3; }");
  }

  @Test
  public void testParamArrowFunction() {
    testSame("(a) => { var b = a; }");
    testError("() => { var b = a; }", VarCheck.UNDEFINED_VAR_ERROR);
    testError("(x=a) => { let a; }", VarCheck.UNDEFINED_VAR_ERROR);

    // Arrow function nested
    testError(
        """
        function FUNC() {
          {
            () => { var b = a; }
          }
        }
        """,
        VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testLegalVarReferenceBetweenModules() {
    testDependentModules("var x = 10;", "var y = x++;", null);
  }

  @Test
  public void testLegalLetReferenceBetweenModules() {
    testDependentModules("let x = 10;", "let y = x++;", null);
  }

  @Test
  public void testLegalConstReferenceBetweenModules() {
    testDependentModules("const x = 10;", "const y = x + 1;", null);
  }

  @Test
  public void testMissingModuleDependencyDefault() {
    testIndependentModules("var x = 10;", "var y = x++;", null, VarCheck.MISSING_MODULE_DEP_ERROR);
  }

  @Test
  public void testMissingModuleDependencyLetAndConst() {
    testIndependentModules("let x = 10;", "let y = x++;", null, VarCheck.MISSING_MODULE_DEP_ERROR);
    testIndependentModules(
        "const x = 10;", "const y = x + 1;", null, VarCheck.MISSING_MODULE_DEP_ERROR);
  }

  @Test
  public void testViolatedModuleDependencyDefault() {
    testDependentModules("var y = x++;", "var x = 10;", VarCheck.VIOLATED_MODULE_DEP_ERROR);
  }

  @Test
  public void testViolatedModuleDependencyLetAndConst() {
    testDependentModules("let y = x++;", "let x = 10;", VarCheck.VIOLATED_MODULE_DEP_ERROR);
    testDependentModules("const y = x + 1;", "const x = 10;", VarCheck.VIOLATED_MODULE_DEP_ERROR);
  }

  @Test
  public void testMissingModuleDependencySkipNonStrict() {
    validityCheck = true;
    testIndependentModules("var x = 10;", "var y = x++;", null, null);
  }

  @Test
  public void testViolatedModuleDependencySkipNonStrict() {
    validityCheck = true;
    testDependentModules("var y = x++;", "var x = 10;", null);
  }

  @Test
  public void testMissingModuleDependencySkipNonStrictNotPromoted() {
    validityCheck = true;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testIndependentModules("var x = 10;", "var y = x++;", null, null);
  }

  @Test
  public void testViolatedModuleDependencyNonStrictNotPromoted() {
    validityCheck = true;
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testDependentModules("var y = x++;", "var x = 10;", null);
  }

  @Test
  public void testDependentStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testDependentModules(
        "var f = function() {return new B();};",
        "var B = function() {}",
        VarCheck.STRICT_MODULE_DEP_ERROR);
  }

  @Test
  public void testIndependentStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.ERROR;
    testIndependentModules(
        "var f = function() {return new B();};",
        "var B = function() {}",
        VarCheck.STRICT_MODULE_DEP_ERROR,
        null);
  }

  @Test
  public void testStarStrictModuleDependencyCheck() {
    strictModuleDepErrorLevel = CheckLevel.WARNING;
    testSame(
        srcs(
            JSChunkGraphBuilder.forStar()
                .addChunk("function a() {}")
                .addChunk("function b() { a(); c(); }")
                .addChunk("function c() { a(); }")
                .build()),
        warning(VarCheck.STRICT_MODULE_DEP_ERROR));
  }

  @Test
  public void testForwardVarReferenceInLocalScope1() {
    testDependentModules("var x = 10; function a() {y++;}", "var y = 11; a();", null);
  }

  @Test
  public void testForwardVarReferenceInLocalScope2() {
    // It would be nice if this pass could use a call graph to flag this case
    // as an error, but it currently doesn't.
    testDependentModules("var x = 10; function a() {y++;} a();", "var y = 11;", null);
  }

  private void testDependentModules(String code1, String code2, @Nullable DiagnosticType error) {
    testDependentModules(code1, code2, error, null);
  }

  private void testDependentModules(
      String code1, String code2, DiagnosticType error, @Nullable DiagnosticType warning) {
    testTwoModules(code1, code2, true, error, warning);
  }

  private void testIndependentModules(
      String code1,
      String code2,
      @Nullable DiagnosticType error,
      @Nullable DiagnosticType warning) {
    testTwoModules(code1, code2, false, error, warning);
  }

  private void testTwoModules(
      String code1,
      String code2,
      boolean m2DependsOnm1,
      DiagnosticType error,
      DiagnosticType warning) {
    JSChunk m1 = new JSChunk("m1");
    m1.add(SourceFile.fromCode("input1", code1));
    JSChunk m2 = new JSChunk("m2");
    m2.add(SourceFile.fromCode("input2", code2));
    if (m2DependsOnm1) {
      m2.addDependency(m1);
    }
    if (error == null && warning == null) {
      test(srcs(m1, m2), expected(code1, code2));
    } else if (error == null) {
      test(srcs(m1, m2), expected(code1, code2), warning(warning));
    } else {
      testError(srcs(m1, m2), error(error));
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Test synthesis of externs

  @Test
  public void testSimple() {
    checkSynthesizedExtern(srcs("x"), "var x;" + VAR_CHECK_EXTERNS);
    checkSynthesizedExtern(srcs("var x"), VAR_CHECK_EXTERNS);
  }

  @Test
  public void testSimpleValidityCheck() {
    validityCheck = true;
    try {
      checkSynthesizedExtern(srcs("x"), "");
      assertWithMessage("Expected RuntimeException").fail();
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("Unexpected variable x");
    }
  }

  @Test
  public void testParameter() {
    checkSynthesizedExtern(srcs("function f(x){}"), VAR_CHECK_EXTERNS);
  }

  @Test
  public void testLocalVar() {
    checkSynthesizedExtern(srcs("function f(){x}"), "var x;" + VAR_CHECK_EXTERNS);
  }

  @Test
  public void testTwoLocalVars() {
    checkSynthesizedExtern(srcs("function f(){x}function g() {x}"), "var x;" + VAR_CHECK_EXTERNS);
  }

  @Test
  public void testInnerFunctionLocalVar() {
    checkSynthesizedExtern(srcs("function f(){function g() {x}}"), "var x;" + VAR_CHECK_EXTERNS);
  }

  @Test
  public void testNoCreateVarsForLabels() {
    checkSynthesizedExtern(srcs("x:var y"), VAR_CHECK_EXTERNS);
  }

  @Test
  public void testVariableInNormalCodeUsedInExterns1() {
    checkSynthesizedExtern(
        externs("x.foo;"),
        srcs("var x;"),
        "var x; " + VAR_CHECK_EXTERNS + " x.foo;",
        warning(UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testVariableInNormalCodeUsedInExterns2() {
    checkSynthesizedExtern(
        externs("x;"),
        srcs("var x;"),
        "var x; " + VAR_CHECK_EXTERNS + " x;",
        warning(NAME_REFERENCE_IN_EXTERNS_ERROR));
  }

  @Test
  public void testVariableInNormalCodeUsedInExterns3() {
    checkSynthesizedExtern(
        externs("x.foo;"),
        srcs("function x() {}"),
        "var x; " + VAR_CHECK_EXTERNS + " x.foo;",
        warning(UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testVariableInNormalCodeUsedInExterns4() {
    checkSynthesizedExtern(
        externs("x;"),
        srcs("function x() {}"),
        "var x; " + VAR_CHECK_EXTERNS + " x;",
        warning(NAME_REFERENCE_IN_EXTERNS_ERROR));
  }

  @Test
  public void testRedeclaration1() {
    String js = "var a; var a;";
    testError(js, VarCheck.VAR_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testRedeclaration2() {
    String js = "var a; /** @suppress {duplicate} */ var a;";
    testSame(js);
  }

  @Test
  public void testRedeclaration3() {
    String js = " /** @suppress {duplicate} */ var a; var a; ";
    testSame(js);
  }

  @Test
  public void testRedeclaration4() {
    String js =
        """
        /** @fileoverview @suppress {duplicate} */
        /** @type {string} */ var a;
        var a;\s
        """;
    testSame(js);
  }

  @Test
  public void testSuppressionWithInlineJsDoc() {
    testSame("/** @suppress {duplicate} */ var /** number */ a; var a;");
  }

  @Test
  public void testDuplicateVar() {
    testError(
        "/** @define {boolean} */ var DEF = false; var DEF = true;", VAR_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testDontAllowSuppressDupeOnLet() {
    testError(
        "let a; /** @suppress {duplicate} */ let a; ", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);

    testError(
        "function f() { let a; /** @suppress {duplicate} */ let a; }",
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testDuplicateBlockScopedDeclarationInSwitch() {
    testError(
        """
        function f(x) {
          switch (x) {
            case 'a':
              let z = 123;
              break;
            case 'b':
              let z = 234;
              break;
          }
        }
        """,
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);

    testError(
        """
        function f(x) {
          switch (x) {
            case 'a':
              class C {}
              break;
            case 'b':
              class C {}
              break;
          }
        }
        """,
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testLetConstRedeclareWithFunctions_withEs6Modules() {
    testError("function f() {} let f = 1; export {f};", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("let f = 1; function f() {}  export {f};", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "const f = 1; function f() {} export {f};", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "function f() {} const f = 1;  export {f};", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "export default function f() {}; let f = 5;", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testFunctionScopeArguments() {
    // A var declaration doesn't mask arguments
    testSame("function f() {var arguments}");

    testError("var f = function arguments() {}", VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    testError("var f = function (arguments) {}", VarCheck.VAR_ARGUMENTS_SHADOWED_ERROR);
    testSame("function f() {try {} catch(arguments) {}}");

    validityCheck = true;
    testSame("function f() {var arguments}");
  }

  @Test
  public void testFunctionRedeclared_global() {
    // Redeclaration in global scope is allowed.
    testSame("/** @fileoverview @suppress {duplicate} */ function f() {};function f() {};");
  }

  @Test
  public void testFunctionRedeclared1() {
    testError("{ function f() {}; function f() {}; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "if (0) { function f() {}; function f() {}; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "try { function f() {}; function f() {}; } catch (e) {}",
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testFunctionRedeclared2() {
    testError("{ let f = 0; function f() {}; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "if (0) { const f = 1; function f() {}; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "try { class f {}; function f() {}; } catch (e) {}",
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testFunctionRedeclared3() {
    testError("{ function f() {}; let f = 0; }", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError("if (0) { function f() {}; const f = 0;}", BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
    testError(
        "try { function f() {}; class f {}; } catch (e) {}",
        BLOCK_SCOPED_DECL_MULTIPLY_DECLARED_ERROR);
  }

  @Test
  public void testUndeclaredVarForGoog() {
    enableClosurePass();
    enableRewriteClosureCode();
    // We don't want to get goog as an undeclared var here.
    test(
        srcs("goog.require('namespace.Class1');\n"),
        error(ClosurePrimitiveErrors.MISSING_MODULE_OR_PROVIDE),
        error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testImportedNameCollision() {
    // TODO(tbreisacher): This should throw a duplicate declaration error.
    testSame("import foo from './foo'; foo('hello'); var foo = 5;");
  }

  @Test
  public void testImportStar() {
    testSame("import * as foo from './foo.js';");
  }

  @Test
  public void testExportAsAlias() {
    testSame("let a = 1; export {a as b};");
    testError("let a = 1; export {b as a};", VarCheck.UNDEFINED_VAR_ERROR);
    testError("export {a as a};", VarCheck.UNDEFINED_VAR_ERROR);

    // Make sure non-aliased exports still work correctly.
    testSame("let a = 1; export {a}");
    testError("let a = 1; export {b};", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testImportAsAlias() {
    testSame("import {b as a} from './foo.js'; let c = a;");
    testError("import {b as a} from './foo.js'; let c = b;", VarCheck.UNDEFINED_VAR_ERROR);
    testSame("import {a} from './foo.js'; let c = a;");
  }

  @Test
  public void testComputedPropertyWithNamedFunction() {
    testSame("({[0]: function f() {}})");
  }

  @Test
  public void testEsModule_withUndefinedExportsRef() {
    testError("exports = function() {}; export {exports};", VarCheck.UNDEFINED_VAR_ERROR);
  }

  @Test
  public void testGoogModule_withDefaultExports() {
    testSame(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            "goog.module('a.b'); exports = function() {};"));
  }

  @Test
  public void testGoogModule_withExportsRefInFunction() {
    testSame(
        srcs(
            // Referencing 'exports' inside a function is strange but allowed.
            TestExternsBuilder.getClosureExternsAsSource(),
            "goog.module('a.b'); exports.f = function() { exports.f(); };"));
  }

  @Test
  public void testGoogModule_withNamedExports() {
    testSame(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            "goog.module('a.b'); exports.f = function() {}; exports.x = 0;"));
  }

  @Test
  public void testGoogProvide_simpleName() {
    testSame(
        TestExternsBuilder.getClosureExternsAsSource() + "goog.provide('A'); var A = class {};");
    testSame(TestExternsBuilder.getClosureExternsAsSource() + "goog.provide('A'); A = class {};");
  }

  @Test
  public void testGoogProvide_simpleName_earlyReference() {
    testSame(TestExternsBuilder.getClosureExternsAsSource() + "A.B = class {}; goog.provide('A');");
    testSame(
        TestExternsBuilder.getClosureExternsAsSource()
            + "A.B = class {}; goog.provide('A'); var A = class {};");
  }

  @Test
  public void testGoogProvide_multipleRootsInSameFile() {
    testSame(
        TestExternsBuilder.getClosureExternsAsSource()
            + "goog.provide('A'); goog.provide('B'); var A = class {}; var B = class {};");
    testSame(
        TestExternsBuilder.getClosureExternsAsSource()
            + "goog.provide('A.a'); goog.provide('B.b'); A.a = 0; B.b = 1");
  }

  @Test
  public void testGoogProvide_complexName() {
    testSame(
        TestExternsBuilder.getClosureExternsAsSource()
            + "goog.provide('foo.A'); foo.A = class {};");
    testSame(
        TestExternsBuilder.getClosureExternsAsSource()
            + "goog.provide('foo.bar.A'); foo.bar.A = class {};");
  }

  @Test
  public void testGoogLegacyModule() {
    testSame(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            "goog.module('foo.A'); goog.module.declareLegacyNamespace(); exports = class {};",
            "new foo.A();"));
  }

  @Test
  public void testGoogNonLegacyModule() {
    testError(
        srcs(TestExternsBuilder.getClosureExternsAsSource(), "goog.module('foo.A');", "foo.A();"),
        error(UNDEFINED_VAR_ERROR));

    testError(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            "goog.module('foo.A'); exports = class {};",
            "new foo.A();"),
        error(UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testGoogLegacyModule_inLoadModule() {
    testSame(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            """
            goog.loadModule(function(exports) {
              goog.module('foo.A');
              goog.module.declareLegacyNamespace();
              exports = class {};
              return exports;
            });
            """,
            "new foo.A();"));
  }

  @Test
  public void testGoogNonLegacyModule_inLoadModule() {
    testError(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            """
            goog.loadModule(function(exports) {
              goog.module('foo.A');
              exports = class {};
              return exports;
            });
            """,
            "new foo.A();"),
        error(UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testGoogProvide_externs_addsDeclForNamespace() {
    checkSynthesizedExtern(
        externs("var goog; goog.provide('a.b'); a.b.C = class {};"),
        srcs(""),
        VAR_CHECK_EXTERNS + "var goog;goog.provide('a.b');a.b.C = class {};");
  }

  @Test
  public void testGoogForwardDeclare_canBeReferencedInExternsIfInCode() {
    testNoWarning(
        // Allow referencing `goog` in externs even though it is actually only defined in code.
        externs("goog.forwardDeclare('a.b.C');"), srcs("var /** !a.b.C */ c; var goog;"));
    checkSynthesizedExtern(
        // Don't synthesize an extern for 'goog'.
        externs("goog.forwardDeclare('a.b.C');"),
        srcs("var goog;"),
        VAR_CHECK_EXTERNS + "goog.forwardDeclare('a.b.C');");
  }

  @Test
  public void testArbitraryPropertyNamedForwardDeclare_cannotBeReferencedInExterns() {
    testWarning(
        externs("notGoog.forwardDeclare('a.b.C');"),
        srcs("var notGoog;"),
        warning(UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testGoogProvide_cannotBeReferencedInExterns() {
    testWarning(
        externs("goog.provide('a.b');"), srcs("var goog;"), warning(UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testGoogModule_cannotBeReferencedInExterns() {
    testWarning(
        externs("goog.module('a.b');"), srcs("var goog;"), warning(UNDEFINED_EXTERN_VAR_ERROR));
  }

  @Test
  public void testReferenceToWeakVar_fromStrongFile() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk(JSChunk.STRONG_CHUNK_NAME);
    weakChunk.addDependency(strongChunk);

    weakChunk.add(SourceFile.fromCode("weak.js", "var weakVar = 0;", SourceKind.WEAK));

    strongChunk.add(SourceFile.fromCode("strong.js", "weakVar();", SourceKind.STRONG));

    test(
        srcs(
            new JSChunk[] {
              strongChunk, weakChunk,
            }),
        error(VarCheck.VIOLATED_MODULE_DEP_ERROR),
        error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testReferenceToWeakVar_fromWeakFile() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    weakChunk.add(
        SourceFile.fromCode(
            "weak.js",
            """
            var weakVar = 0;
            weakVar();
            """,
            SourceKind.WEAK));

    testSame(srcs(weakChunk));
  }

  @Test
  public void testReferenceToWeakNamespaceRoot_fromStrongFile() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk(JSChunk.STRONG_CHUNK_NAME);
    weakChunk.addDependency(strongChunk);

    weakChunk.add(
        SourceFile.fromCode(
            "weak.js",
            TestExternsBuilder.getClosureExternsAsSource() + "goog.provide('foo.bar');",
            SourceKind.WEAK));

    strongChunk.add(SourceFile.fromCode("strong.js", "foo();", SourceKind.STRONG));

    test(
        srcs(
            new JSChunk[] {
              strongChunk, weakChunk,
            }),
        error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testReferenceToWeakNamespaceRoot_fromStrongFile_synthesizesExtern() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk(JSChunk.STRONG_CHUNK_NAME);
    weakChunk.addDependency(strongChunk);

    weakChunk.add(
        SourceFile.fromCode(
            "weak.js",
            TestExternsBuilder.getClosureExternsAsSource() + "goog.provide('foo.bar');",
            SourceKind.WEAK));

    strongChunk.add(
        SourceFile.fromCode(
            "strong.js",
            """
            /** @suppress {undefinedVars} */
            foo();
            """,
            SourceKind.STRONG));

    testExternChanges(
        externs(""), srcs(strongChunk, weakChunk), expected("var foo;" + VAR_CHECK_EXTERNS));
  }

  @Test
  public void testReferenceToweakChunkNamespaceRoot_fromStrongFile() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk(JSChunk.STRONG_CHUNK_NAME);
    weakChunk.addDependency(strongChunk);

    weakChunk.add(
        SourceFile.fromCode(
            "weak.js",
            TestExternsBuilder.getClosureExternsAsSource()
                + "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
            SourceKind.WEAK));

    strongChunk.add(SourceFile.fromCode("strong.js", "foo();", SourceKind.STRONG));

    test(srcs(strongChunk, weakChunk), error(VarCheck.UNDEFINED_VAR_ERROR));
  }

  @Test
  public void testReferenceToStrongNamespaceRoot_withAdditionalWeakProvide_fromStrongFile() {
    JSChunk weakChunk = new JSChunk(JSChunk.WEAK_CHUNK_NAME);
    JSChunk strongChunk = new JSChunk(JSChunk.STRONG_CHUNK_NAME);
    weakChunk.addDependency(strongChunk);

    weakChunk.add(SourceFile.fromCode("weak.js", "goog.provide('foo.bar');", SourceKind.WEAK));

    strongChunk.add(
        SourceFile.fromCode(
            "strong0.js",
            TestExternsBuilder.getClosureExternsAsSource() + "goog.provide('foo.qux');",
            SourceKind.STRONG));
    strongChunk.add(SourceFile.fromCode("strong1.js", "foo();", SourceKind.STRONG));

    testSame(
        srcs(
            new JSChunk[] {
              strongChunk, weakChunk,
            }));
  }

  private static final class VariableTestCheck implements CompilerPass {

    final AbstractCompiler compiler;

    VariableTestCheck(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(
          compiler,
          new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
              if (n.isName() && !parent.isFunction() && !parent.isLabel()) {
                assertScope(t.getScope()).declares(n.getString());
              }
            }
          },
          externs,
          root);
    }
  }

  public void checkSynthesizedExtern(Sources input, String expectedExtern) {
    checkSynthesizedExtern(externs(""), input, expectedExtern);
  }

  public void checkSynthesizedExtern(
      Externs extern, Sources input, String expectedExtern, Diagnostic... warnings) {
    declarationCheck = !validityCheck;
    disableCompareAsTree();
    testExternChanges(extern, input, expected(expectedExtern), warnings);
  }
}
