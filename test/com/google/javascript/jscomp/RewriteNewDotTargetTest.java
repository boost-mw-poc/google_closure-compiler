/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.TranspilationUtil.CANNOT_CONVERT_YET;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RewriteNewDotTarget}. */
@RunWith(JUnit4.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class RewriteNewDotTargetTest extends CompilerTestCase {

  @Before
  public void enableTypeCheckBeforePass() {
    enableNormalize();
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return PeepholeTranspilationsPass.create(
        compiler, ImmutableList.of(new RewriteNewDotTarget(compiler)));
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    return options;
  }

  @Test
  public void testNewTargetInNonConstructorMethod() {
    testError(
        """
        /** @constructor */
        function Foo() { new.target; } // not an ES6 class constructor
        """,
        CANNOT_CONVERT_YET);

    testError(
        "function foo() { new.target; }", // not a constructor at all
        CANNOT_CONVERT_YET);

    testError(
        """
        class Foo {
          method() { // not a constructor
            new.target;
          }
        }
        """,
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testNewTargetInEs6Constructor() {
    test(
        """
        class Foo {
          constructor() {
            new.target;
            () => new.target; // works in arrow functions, too
          }
        }
        """,
        """
        class Foo {
          constructor() {
            this.constructor;
            () => { return this.constructor; }; // works in arrow functions, too
          }
        }
        """);
  }

  @Test
  public void testNewTargetInEs6Constructor_superCtorReturnsObject() {
    // TODO(b/157140030): This is an unexpected behaviour change.
    test(
        """
        /** @constructor */
        function Base() {
          return {};
        }

        class Child extends Base {
          constructor() {
            super();
            new.target; // Child
          }
        }
        """,
        """
        /** @constructor */
        function Base() {
          return {};
        }

        class Child extends Base {
          constructor() {
            super();
            this.constructor; // Object
          }
        }
        """);
  }

  @Test
  public void testNewTargetInEs6Constructor_beforeSuper() {
    test(
        """
        class Foo extends Object {
          constructor() {
            new.target;
            super();
          }
        }
        """,
        """
        class Foo extends Object {
          constructor() {
            this.constructor; // `this` before `super`
            super();
          }
        }
        """);
  }
}
