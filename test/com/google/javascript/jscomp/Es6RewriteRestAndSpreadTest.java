/*
 * Copyright 2014 The Closure Compiler Authors.
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
import static org.junit.Assert.assertThrows;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6RewriteRestAndSpreadTest extends CompilerTestCase {
  private static final String EXTERNS_BASE =
      new TestExternsBuilder()
          .addFunction()
          .addJSCompLibraries()
          .addExtra("$jscomp.getRestArguments = function(argument) {};")
          .build();

  public Es6RewriteRestAndSpreadTest() {
    super(EXTERNS_BASE);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteRestAndSpread(compiler);
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Before
  public void customSetUp() {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  // Spreading into array literals.

  @Test
  public void testSpreadArrayLiteralIntoArrayLiteral() {
    test("[...[1, 2], 3, ...[4], 5, 6, ...[], 7, 8]", "[1, 2, 3, 4, 5, 6, 7, 8]");
  }

  @Test
  public void testSpreadVariableIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid, 4, 5];",
        "var arr = [1, 2].concat((0, $jscomp.arrayFromIterable)(mid), [4, 5]);");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid(), 4, 5];",
        "var arr = [1, 2].concat((0, $jscomp.arrayFromIterable)(mid()), [4, 5]);");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionArgumentsIntoArrayLiteral() {
    test(
        "function f() { return [...arguments, 2]; };",
        """
        function f() {
          return [].concat((0, $jscomp.arrayFromIterable)(arguments), [2]);
        };
        """);
  }

  @Test
  public void testSpreadVariableAndFunctionReturnIntoArrayLiteral() {
    test(
        "var arr = [1, 2, ...mid, ...mid2(), 4, 5];",
        """
        var arr = [1,2].concat(
            (0, $jscomp.arrayFromIterable)(mid), (0, $jscomp.arrayFromIterable)(mid2()), [4, 5]);
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoEntireArrayLiteral() {
    test("var arr = [...mid()];", "var arr = [].concat((0, $jscomp.arrayFromIterable)(mid()));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionArgumentsIntoEntireArrayLiteral() {
    test(
        "function f() { return [...arguments]; };",
        """
        function f() {
          return [].concat((0, $jscomp.arrayFromIterable)(arguments));
        };
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadArrayLiteralIntoArrayLiteralWithinParameterList() {
    test("f(1, [2, ...[3], 4], 5);", "f(1, [2, 3, 4], 5);");
  }

  @Test
  public void testSpreadVariableIntoArrayLiteralWithinParameterList() {
    test(
        "f(1, [2, ...mid, 4], 5);",
        "f(1, [2].concat((0, $jscomp.arrayFromIterable)(mid), [4]), 5);");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoArrayLiteralWithinParameterList() {
    test(
        "f(1, [2, ...mid(), 4], 5);",
        "f(1, [2].concat((0, $jscomp.arrayFromIterable)(mid()), [4]), 5);");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  // Spreading into parameter lists.

  @Test
  public void testSpreadArrayLiteralIntoEntireParameterList() {
    test("f(...[0, 1, 2]);", "f.apply(null, [0, 1, 2]);");
  }

  @Test
  public void testSpreadArrayLiteralIntoParameterList() {
    test("f(...[0, 1, 2], 3);", "f.apply(null, [0, 1, 2, 3]);");
  }

  @Test
  public void testSpreadVariableIntoEntireParameterList() {
    test("f(...arr);", "f.apply(null, (0, $jscomp.arrayFromIterable)(arr));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoParameterList() {
    test(
        "f(0, ...arr, 2);", "f.apply(null, [0].concat((0, $jscomp.arrayFromIterable)(arr), [2]));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoEntireParameterList() {
    test("f(...g());", "f.apply(null, (0, $jscomp.arrayFromIterable)(g()));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadFunctionReturnIntoParameterList() {
    test(
        "f(0, ...g(), 2);", "f.apply(null, [0].concat((0, $jscomp.arrayFromIterable)(g()), [2]));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoIifeParameterList() {
    test(
        "(function() {})(...arr);",
        "(function() {}).apply(null, (0, $jscomp.arrayFromIterable)(arr))");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoAnonymousFunctionParameterList() {
    test("getF()(...args);", "getF().apply(null, (0, $jscomp.arrayFromIterable)(args));");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoMethodParameterList() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * Skipping at-struct here to allow for string access.
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }
                """),
        srcs(
            """
            var obj = new TestClass();
            obj.testMethod(...arr);
            obj['testMethod'](...arr);
            """),
        expected(
            """
            var obj = new TestClass();
            obj.testMethod.apply(obj, (0, $jscomp.arrayFromIterable)(arr));
            obj["testMethod"].apply(obj, (0, $jscomp.arrayFromIterable)(arr));
            """));
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListInCast() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * Skipping at-struct here to allow for string access.
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }
                """),
        srcs(
            """
            var obj = new TestClass();
            (/** @type {?} */ (obj.testMethod))(...arr);
            (/** @type {?} */ (/** @type {?} */ (obj.testMethod)))(...arr);
            (/** @type {?} */ (obj['testMethod']))(...arr);
            """),
        expected(
            """
            var obj = new TestClass();
            obj.testMethod.apply(obj, (0, $jscomp.arrayFromIterable)(arr));
            obj.testMethod.apply(obj, (0, $jscomp.arrayFromIterable)(arr));
            obj['testMethod'].apply(obj, (0, $jscomp.arrayFromIterable)(arr));
            """));
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoDeepMethodParameterList() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /** @param {...number} args */ function numberVarargFn(args) { }

                /** @type {!Iterable<number>} */ var numberIterable;
                """),
        srcs(
            """
            var x = {y: {z: {m: numberVarargFn}}};
            x.y.z.m(...numberIterable);
            """),
        expected(
            """
            var x = {y: {z: {m: numberVarargFn}}};
            x.y.z.m.apply(x.y.z, (0, $jscomp.arrayFromIterable)(numberIterable));
            """));
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoMethodParameterList_freeCall() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * Skipping at-struct here to allow for string access.
                 */
                function TestClass() { }

                // Adding @this {?} to allow calling testMethod without passing a TestClass as
                // `this`
                /** @param {...string} args @this {?} */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }
                """),
        srcs(
            """
            var obj = new TestClass();
            // The (0, obj.testMethod) tells the compiler that this is a 'free call'.
            (0, obj.testMethod)(...arr);
            (0, obj['testMethod'])(...arr);
            """),
        expected(
            """
            var obj = new TestClass();
            obj.testMethod.apply(null, (0, $jscomp.arrayFromIterable)(arr));
            obj["testMethod"].apply(null, (0, $jscomp.arrayFromIterable)(arr));
            """));
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadMultipleArrayLiteralsIntoParameterList() {
    test(
        externs(
            MINIMAL_EXTERNS + "/** @param {...number} args */ function numberVarargFn(args) { }"),
        srcs("numberVarargFn(...[1, 2], 3, ...[4, 5], 6, ...[7, 8])"),
        expected("numberVarargFn.apply(null, [1, 2, 3, 4, 5, 6, 7, 8])"));
  }

  @Test
  public void testSpreadMultipleVariablesIntoParameterList() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /** @param {...number} args */ function numberVarargFn(args) { }
                /** @type {!Iterable<number>} */ var numberIterable;
                """),
        srcs("numberVarargFn(0, ...numberIterable, 2, ...numberIterable, 4);"),
        expected(
            """
            numberVarargFn.apply(
                null,
                [0].concat(
                    (0, $jscomp.arrayFromIterable)(numberIterable),
                    [2],
                    (0, $jscomp.arrayFromIterable)(numberIterable),
                    [4]));
            """));
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListOnAnonymousRecieverWithSideEffects() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * @struct
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }

                /** @type {!Iterable<string>} */ var stringIterable;
                """),
        srcs("testClassFactory().testMethod(...stringIterable);"),
        expected(
            """
            var $jscomp$spread$args0;
            ($jscomp$spread$args0 = testClassFactory()).testMethod.apply(
                $jscomp$spread$args0, (0, $jscomp.arrayFromIterable)(stringIterable));
            """));
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListOnReceiverWithSideEffects_freeCall() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                // Skip @struct to allow for bracket access
                 */
                function TestClass() { }

                // Add @this {?} to allow calling testMethod without passing a TestClass as
                // `this`
                /** @param {...string} args @this {null} */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }

                /** @type {!Iterable<string>} */ var stringIterable;
                """),
        srcs(
            """
            (0, testClassFactory().testMethod)(...stringIterable);
            (0, testClassFactory()['testMethod'])(...stringIterable);
            """),
        expected(
            """
            testClassFactory().testMethod.apply(
                null, (0, $jscomp.arrayFromIterable)(stringIterable));
            testClassFactory()["testMethod"].apply(
                null, (0, $jscomp.arrayFromIterable)(stringIterable));
            """));
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoMethodParameterListOnConditionalRecieverWithSideEffects() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * @struct
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }

                /** @type {!Iterable<string>} */ var stringIterable;
                """),
        srcs("var x = b ? testClassFactory().testMethod(...stringIterable) : null;"),
        expected(
            """
            var $jscomp$spread$args0;
            var x = b ? ($jscomp$spread$args0 = testClassFactory()).testMethod.apply(
                $jscomp$spread$args0, (0, $jscomp.arrayFromIterable)(stringIterable))
                    : null;
            """));
  }

  @Test
  public void
      testSpreadVariableIntoMethodParameterListOnConditionalRecieverWithSideEffectsInCast() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * @struct
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }

                /** @type {!Iterable<string>} */ var stringIterable;
                """),
        srcs(
            """
            var x = b ?
                /** @type {?} */ (testClassFactory().testMethod)(...stringIterable) :
                null;
            """),
        expected(
            """
            var $jscomp$spread$args0;
            var x = b ? ($jscomp$spread$args0 = testClassFactory()).testMethod.apply(
                $jscomp$spread$args0, (0, $jscomp.arrayFromIterable)(stringIterable))
                    : null;
            """));
  }

  @Test
  public void
      testSpreadVariableIntoMethodParameterListOnRecieversWithSideEffectsMultipleTimesInOneScope() {
    test(
        externs(
            EXTERNS_BASE
                + """
                /**
                 * @constructor
                 * @struct
                 */
                function TestClass() { }

                /** @param {...string} args */
                TestClass.prototype.testMethod = function(args) { }

                /** @return {!TestClass} */
                function testClassFactory() { }

                /** @type {!Iterable<string>} */ var stringIterable;
                """),
        srcs(
            """
            testClassFactory().testMethod(...stringIterable);
            testClassFactory().testMethod(...stringIterable);
            """),
        expected(
            """
            var $jscomp$spread$args0;
            ($jscomp$spread$args0 = testClassFactory()).testMethod.apply(
                $jscomp$spread$args0, (0, $jscomp.arrayFromIterable)(stringIterable));
            var $jscomp$spread$args1;
            ($jscomp$spread$args1 = testClassFactory()).testMethod.apply(
                $jscomp$spread$args1, (0, $jscomp.arrayFromIterable)(stringIterable));
            """));
  }

  @Test
  public void testSpreadIntoSuperThrows() {
    // All ES6 classes must be transpiled away before this pass runs,
    // because we cannot spread into super() calls without creating invalid syntax.
    assertThrows(
        RuntimeException.class,
        () ->
            testSame(
                """
                class A {
                  constructor(...args) {
                      this.p = args;
                  }
                }

                class B extends A {
                   constructor(...args) {
                     super(0, ...args, 2);
                   }
                }
                """));
  }

  @Test
  public void testSpreadVariableIntoParameterListWithinArrayLiteral() {
    test(
        "[1, f(2, ...mid, 4), 5];",
        "[1, f.apply(null, [2].concat((0, $jscomp.arrayFromIterable)(mid), [4])), 5];");
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testSpreadVariableIntoNew() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        srcs("new F(...args);"),
        expected(
            """
            new (Function.prototype.bind.apply(F,\
             [null].concat((0, $jscomp.arrayFromIterable)(args))));\
            """));

    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/arrayfromiterable");
  }

  // Rest parameters

  @Test
  public void testUnusedRestParameterAtPositionZero() {
    test("function f(...zero) {}", "function f() {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionOne() {
    test("function f(zero, ...one) {}", "function f(zero) {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionTwo() {
    test("function f(zero, one, ...two) {}", "function f(zero, one) {}");
  }

  @Test
  public void testUsedRestParameterAtPositionZero() {
    test(
        "function f(...zero) { return zero; }",
        """
        function f() {
          let zero = $jscomp.getRestArguments.apply(0, arguments)
          return zero;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUsedRestParameterAtPositionTwo() {
    test(
        "function f(zero, one, ...two) { return two; }",
        """
        function f(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUsedRestParameterAtPositionTwo_maintainsNormalization() {
    test(
        "function f(zero, one, ...two) { function inner() {} return two; }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUsedRestParameterAtPositionTwo_maintainsNormalization_withoutReturn() {
    test(
        "function f(zero, one, ...two) { function inner() {} two; }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments);
          two;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUnusedRestParameterAtPositionTwo_noGoodInsertionPoint() {
    test(
        "function f(zero, one, ...two) { function inner() {} }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments); // declaration inserted
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithTypingOnFunction() {
    test("/** @param {...number} zero */ function f(...zero) {}", "function f() {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithInlineTyping() {
    test("function f(/** ...number */ ...zero) {}", "function f() {}");
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunction() {
    test(
        "/** @param {...number} two */ function f(zero, one, ...two) { return two; }",
        """
        function f(zero, one) {
         let two = $jscomp.getRestArguments.apply(2, arguments);
         return two;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionVariable() {
    test(
        "/** @param {...number} two */ var f = function(zero, one, ...two) { return two; }",
        """
        var f = function(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionProperty() {
    test(
        "/** @param {...number} two */ ns.f = function(zero, one, ...two) { return two; }",
        """
        ns.f = function(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
  }

  @Test
  public void testUnusedRestParameterAtPositionTwoWithUsedParameterAtPositionOne() {
    test(
        "function f(zero, one, ...two) {one = (one === undefined) ? 1 : one;}",
        """
        function f(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          one = (one === undefined) ? 1 : one;
        }
        """);
    assertThat(getLastCompiler().getInjected()).containsExactly("es6/util/restarguments");
  }
}
