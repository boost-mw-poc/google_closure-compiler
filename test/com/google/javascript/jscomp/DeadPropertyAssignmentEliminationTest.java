/*
 * Copyright 2016 The Closure Compiler Authors.
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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeadPropertyAssignmentEliminationTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
  }

  @Test
  public void testBasic() {
    testSame(
        """
        var foo = function() {
          this.a = 20;
        }
        """);

    test(
        """
        var foo = function() {
          this.a = 10;
          this.a = 20;
        }
        """,
        """
        var foo = function() {
          10;
          this.a = 20;
        }
        """);

    testSame(
        """
        var foo = function() {
          this.a = 20;
          this.a = this.a + 20;
        }
        """);
  }

  @Test
  public void testMultipleProperties() {
    test(
        """
        var foo = function() {
          this.a = 10;
          this.b = 15;
          this.a = 20;
        }
        """,
        """
        var foo = function() {
          10;
          this.b = 15;
          this.a = 20;
        }
        """);
  }

  @Test
  public void testNonStandardAssign() {
    test(
        """
        var foo = function() {
          this.a = 10;
          this.a += 15;
          this.a = 20;
        }
        """,
        """
        var foo = function() {
          this.a = 10;
          this.a + 15;
          this.a = 20;
        }
        """);
  }

  @Test
  public void testChainingPropertiesAssignments() {
    test(
        """
        var foo = function() {
          this.a = this.b = this.c = 10;
          this.b = 15;
        }
        """,
        """
        var foo = function() {
          this.a = this.c = 10;
          this.b = 15;
        }
        """);
  }

  @Test
  public void testConditionalProperties() {
    // We don't handle conditionals at all.
    testSame(
        """
        var foo = function() {
          this.a = 10;
          if (true) { this.a = 20; } else { this.a = 30; }
        }
        """);

    // We don't handle conditionals at all.
    testSame(
        """
        var bar = function(x)  {};
        var foo = function() {
          this.a = 10; // must preserve this assignment
          bar?.(this.a = 20);
        }
        """);

    // However, we do handle everything up until the conditional.
    test(
        """
        var foo = function() {
          this.a = 10;
          this.a = 20;
          if (true) { this.a = 20; } else { this.a = 30; }
        }
        """,
        """
        var foo = function() {
          10;
          this.a = 20;
          if (true) { this.a = 20; } else { this.a = 30; }
        }
        """);

    // However, we do handle everything up until the conditional.
    test(
        """
        var bar = function(x)  {};
        var foo = function() {
          this.a = 10; // this is a dead assignment and must be eliminated
          this.a = 20;
          bar?.(this.a = 30);
        }
        """,
        """
        var bar = function(x)  {};
        var foo = function() {
          10;
          this.a = 20;
          bar?.(this.a = 30);
        }
        """);
  }

  @Test
  public void testQualifiedNamePrefixAssignment() {
    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          a.b = other;
          a.b.c = 30;
        }
        """);

    testSame(
        """
        var foo = function() {
          a.b = 20;
          a = other;
          a.b = 30;
        }
        """);
  }

  @Test
  public void testCall() {
    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          doSomething();
          a.b.c = 30;
        }
        """);

    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          doSomething().b.c = 25;
          a.b.c = 30;
        }
        """);

    testSame(
        """
        var foo = function() { // to preserve newlines
          a.b.c = 20;
          doSomething?.();
          a.b.c = 30;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function() {
          this.c = 20;
          doSomething(this);
          this.c = 30;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function() {
          this.c = 20;
          this.doSomething();
          this.c = 30;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function() {
          this.c = 20;
          doSomething(this.c);
          this.c = 30;
        }
        """);

    test(
        """
        var foo = function() {
          a.b.c = 20;
          doSomething(a.b.c = 25); // unconditional assignment in arg renders above
        // assignment dead.
          a.b.c = 30;
        }
        """,
        """
        var foo = function() {
          20;
          doSomething(a.b.c = 25);
          a.b.c = 30;
        }
        """);

    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          doSomething?.(a.b.c = 25); // conditional assignment in optional chain arg keeps
        // above assignment alive.
          a.b.c = 30;
        }
        """);
  }

  @Test
  public void testYield() {
    // Assume that properties may be read during a yield
    testSame(
        """
        var foo = function*() {
          a.b.c = 20;
          yield;
          a.b.c = 30;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function*() {
          this.c = 20;
          yield;
          this.c = 30;
        }
        """);

    testSame(
        """
        var obj = {
          *gen() {
            this.c = 20;
            yield;
            this.c = 30;
          }
        }
        """);

    test(
        """
        var foo = function*() {
          a.b.c = 20;
          yield a.b.c = 25;
          a.b.c = 30;
        }
        """,
        """
        var foo = function*() {
          20;
          yield a.b.c = 25;
          a.b.c = 30;
        }
        """);
  }

  @Test
  public void testNew() {
    // Assume that properties may be read during a constructor call
    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          new C;
          a.b.c = 30;
        }
        """);
  }

  @Test
  public void testTaggedTemplateLit() {
    // Assume that properties may be read during a tagged template lit invocation
    testSame(
        """
        var foo = function() {
          a.b.c = 20;
          doSomething`foo`;
          a.b.c = 30;
        }
        """);
  }

  @Test
  public void testAwait() {
    // Assume that properties may be read while waiting for "await"
    testSame(
        """
        async function foo() {
          a.b.c = 20;
          await bar;
          a.b.c = 30;
        }
        """);
  }

  @Test
  public void testUnknownLookup() {
    testSame(
        """
        /** @constructor */
        var foo = function(str) {
          this.x = 5;
          var y = this[str];
          this.x = 10;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function(str) {
          this.x = 5;
          var y = this?.[str];
          this.x = 10;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function(x, str) {
          x.y = 5;
          var y = x[str];
          x.y = 10;
        }
        """);

    testSame(
        """
        /** @constructor */
        var foo = function(x, str) {
          x.y = 5;
          var y = x?.[str];
          x.y = 10;
        }
        """);
  }

  @Test
  public void testName() {
    testSame(
        """
        function f(x) {
          var y = { a: 0 };
          x.a = 123;
          y = x;
          x.a = 234;
          return x.a + y.a;
        }
        """);
  }

  @Test
  public void testName2() {
    testSame(
        """
        function f(x) {
          var y = x;
          x.a = 123;
          x = {};
          x.a = 234;
          return x.a + y.a;
        }
        """);
  }

  @Test
  public void testAliasing() {
    testSame(
        """
        function f(x) {
          x.b.c = 1;
          var y = x.a.c; // x.b.c is read here
          x.b.c = 2;
          return x.b.c + y;
        }
        var obj = { c: 123 };
        f({a: obj, b: obj});
        """);
  }

  @Test
  public void testHook() {
    testSame(
        """
        function f(x, pred) {
          var y;
          x.p = 234;
          y = pred ? (x.p = 123) : x.p;
        }
        """);

    testSame(
        """
        function f(x, pred) {
          var y;
          x.p = 234;
          y = pred ? (x.p = 123) : 123;
          return x.p;
        }
        """);
  }

  @Test
  public void testConditionalExpression() {
    testSame(
        """
        function f(x) {
          return (x.p = 2) || (x.p = 3); // Second assignment will never execute.
        }
        """);

    testSame(
        """
        function f(x) {
          return (x.p = 0) && (x.p = 3); // Second assignment will never execute.
        }
        """);
  }

  @Test
  public void nullishCoalesce() {
    testSame(
        """
        function f(x) {
          return (x.p = 2) ?? (x.p = 3); // Second assignment will never execute.
        }
        """);
  }

  @Test
  public void optChain() {
    testSame(
        """
        function f(x) {
          return bar(x.p = 2)?.(x.p = 3); // second assignment conditionally executes, hence
        // marks property `p` as read.
        }
        """);

    testSame(
        """
        function foo() {
            this.p = 123;
            var z = this?.p; // marks property `p` as read
            this.p = 234;
        }
        """);
  }

  @Test
  public void testBrackets() {
    testSame(
        """
        function f(x, p) {
          x.prop = 123;
          x[p] = 234;
          return x.prop;
        }
        """);
  }

  // This pass does not currently use the control-flow graph. Hence, dead assignments in some of the
  // following tests get preserved even when they are safe to delete.
  @Test
  public void testFor() {
    testSame(
        """
        function f(x) {
          x.p = 1;
          for(;x;) {}
          x.p = 2;
        }
        """);

    testSame(
        """
        function f(x) {
          for(;x;) {
            x.p = 1;
          }
          x.p = 2;
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          for(;;) {
            x.p = 2;
          }
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          for(x.p = 2;;) {
          }
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          for(x.p = 2;;x.p=3) {
            return x.p; // Reads the "x.p = 2" assignment.
          }
        }
        """);

    testSame(
        """
        function f(x) {
          for(;;) {
            x.p = 1;
            x.p = 2;
          }
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          for(;;) {
          }
          x.p = 2;
        }
        """);
  }

  // This pass does not currently use the control-flow graph. Hence, dead assignments in some of the
  // following tests get preserved even when they are safe to delete.
  @Test
  public void testWhile() {
    testSame(
        """
        function f(x) {
          x.p = 1;
          while(x);
          x.p = 2;
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          while(1) {
            x.p = 2;
          }
        }
        """);

    testSame(
        """
        function f(x) {
          while(true) {
            x.p = 1;
            if (random()) continue;
            x.p = 2;
          }
        }
        """);

    testSame(
        """
        function f(x) {
          while(true) {
            x.p = 1;
            if (random()) break;
            x.p = 2;
          }
        }
        """);

    test(
        """
        function f(x) {
          x.p = 1;
          while(x.p = 2) {
          }
        }
        """,
        """
        function f(x) {
          1;
          while(x.p = 2) {
          }
        }
        """);

    test(
        """
        function f(x) {
          while(true) {
            x.p = 1;
            x.p = 2;
          }
        }
        """,
        """
        function f(x) {
          while(true) {
            1;
            x.p = 2;
          }
        }
        """);

    test(
        """
        function f(x) {
          x.p = 1;
          while(1) {}
          x.p = 2;
        }
        """,
        """
        function f(x) {
          1;
          while(1) {}
          x.p = 2;
        }
        """);
  }

  @Test
  public void testTry() {
    testSame(
        """
        function f(x) {
          x.p = 1;
          try {
            x.p = 2;
          } catch (e) {}
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 1;
          try {
          } catch (e) {
            x.p = 2;
          }
        }
        """);

    testSame(
        """
        function f(x) {
          try {
            x.p = 1;
          } catch (e) {
            x.p = 2;
          }
        }
        """);

    test(
        """
        function f(x) {
          try {
            x.p = 1;
            x.p = 2;
          } catch (e) {
          }
        }
        """,
        """
        function f(x) {
          try {
            1;
            x.p = 2;
          } catch (e) {
          }
        }
        """);

    testSame(
        """
        function f(x) {
          try {
            x.p = 1;
            maybeThrow();
            x.p = 2;
          } catch (e) {
          }
        }
        """);

    testSame(
        """
        function f(x) {
          try {
            x.p = 1;
            maybeThrow?.();
            x.p = 2;
          } catch (e) {
          }
        }
        """);

    testSame(
        """
        /** @constructor */
        function f() {
          try {
            this.p = 1;
            maybeThrow();
            this.p = 2;
          } catch (e) {
          }
        }
        """);
  }

  @Test
  public void testThrow() {
    testSame(
        """
        function f(x) {
          x.p = 10
          if (random) throw err;
          x.p = 20;
        }
        """);

    testSame(
        """
        function f(x) {
          x.p = 10
          throw err;
          x.p = 20;
        }
        """);
  }

  @Test
  public void testSwitch() {
    testSame(
        """
        function f(x, pred) {
          x.p = 1;
          switch (pred) {
            case 1:
              x.p = 2;
            case 2:
              x.p = 3;
              break;
            default:
              return x.p;
          }
        }
        """);

    testSame(
        """
        function f(x, pred) {
          x.p = 1;
          switch (pred) {
            default:
              x.p = 2;
          }
          x.p = 3;
        }
        """);

    testSame(
        """
        function f(x, pred) {
          x.p = 1;
          switch (pred) {
            default:
              return;
          }
          x.p = 2;
        }
        """);

    // For now we don't enter switch statements.
    testSame(
        """
        function f(x, pred) {
          switch (pred) {
            default:
              x.p = 2;
              x.p = 3;
          }
        }
        """);
  }

  @Test
  public void testIf() {
    test(
        """
        function f(x, pred) {
          if (pred) {
            x.p = 1;
            x.p = 2;
            return x.p;
          }
        }
        """,
        """
        function f(x, pred) {
          if (pred) {
            1;
            x.p = 2;
            return x.p;
          }
        }
        """);

    test(
        """
        function f(x, pred) {
          x.p = 1;
          if (pred) {}
          x.p = 2;
          return x.p;
        }
        """,
        """
        function f(x, pred) {
          1;
          if (pred) {}
          x.p = 2;
          return x.p;
        }
        """);

    testSame(
        """
        function f(x, pred) {
          if (pred) {
            x.p = 1;
          }
          x.p = 2;
        }
        """);
  }

  @Test
  public void testCircularPropChain() {
    testSame(
        """
        function f(x, y) {
          x.p = {};
          x.p.y.p.z = 10;
          x.p = {};
        }
        """);
  }

  @Test
  public void testDifferentQualifiedNames() {
    testSame(
        """
        function f(x, y) {
          x.p = 10;
          y.p = 11;
        }
        """);
  }

  @Test
  public void testGetPropContainsNonQualifiedNames() {
    testSame(
        """
        function f(x) {
          foo(x).p = 10;
          foo(x).p = 11;
        }
        """);

    testSame(
        """
        function f(x) {
          (x = 10).p = 10;
          (x = 10).p = 11;
        }
        """);
  }

  @Test
  public void testEs6Constructor() {
    testSame(
        """
        class Foo {
          constructor() {
            this.p = 123;
            var z = this.p;
            this.p = 234;
          }
        }
        """);

    testSame(
        """
        class Foo {
          constructor() {
            this.p = 123;
            var z = this?.p;
            this.p = 234;
          }
        }
        """);

    test(
        """
        class Foo {
          constructor() {
            this.p = 123;
            this.p = 234;
          }
        }
        """,
        """
        class Foo {
          constructor() {
            123;
            this.p = 234;
          }
        }
        """);
  }

  @Test
  public void testES6ClassExtends() {
    testSame(
        """
        class C {
          constructor() {
            this.x = 20;
          }
        }
        class D extends C {
          constructor() {
            super();
            this.x = 40;
          }
        }
        """);
  }

  @Test
  public void testStaticBlock() {
    testSame(
        """
        class Foo {
          static {
            this.p = 123;
            var z = this.p;
            this.p = 234;
          }
        }
        """);

    testSame(
        """
        class C {
          static {
            this.x = 20;
          }
        }
        class D extends C {
          static {
            this.x = 40;
          }
        }
        """);

    // TODO(b/235871861): Ideally the first assignment would be removed
    testSame(
        """
        class Foo {
          static {
            this.p = 123; // should get removed
            this.p = 234;
          }
        }
        """);

    // TODO(b/235871861): Ideally the first assignment would be removed
    testSame(
        """
        class Foo {
          static {
            let o = { prop : 2};
            o.prop = 123; // should get removed
            o.prop = 234;
          }
        }
        """);

    testSame(
        """
        let o = { prop : 2};
        class C {
          static {
            o.prop = 20; // should not get removed
          }
        }
        o.prop;
        """);
  }

  @Test
  public void testGetter() {
    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; };
        Object.defineProperties(Foo.prototype, {bar: {
          get: function () { return this.enabled ? 'enabled' : 'disabled'; }
        }});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          var f = foo.bar;
          foo.enabled = false;
        }
        """);

    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; };
        Object.defineProperties(Foo.prototype, {bar: {
          get: function () { return this.enabled ? 'enabled' : 'disabled'; }
        }});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          var f = foo?.bar;
          foo.enabled = false;
        }
        """);

    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; };
        Object.defineProperty(Foo, 'bar', {
          get: function () { return this.enabled ? 'enabled' : 'disabled'; }
        });
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          var f = foo.bar;
          foo.enabled = false;
        }
        """);
  }

  @Test
  public void testGetter_afterDeadAssignment() {
    testSame(
        """
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          var f = foo.bar;
          foo.enabled = false;
        }
        /** @constructor */ function Foo() { this.enabled = false; };
        Object.defineProperties(Foo.prototype, {bar: {
          get: function () { return this.enabled ? 'enabled' : 'disabled'; }
        }});
        """);
  }

  @Test
  public void testGetter_onDifferentType() {
    testSame(
        """
        /** @constructor */
        function Foo() {
          this.enabled = false;
        };
        Object.defineProperties(
            Foo.prototype, {
              baz: {
                get: function () { return this.enabled ? 'enabled' : 'disabled'; }
              }
            });
        /** @constructor */
        function Bar() {
          this.enabled = false;
          this.baz = 123;
        };
        function f() {
          var bar = new Bar();
          bar.enabled = true;
          var ret = bar.baz;
          bar.enabled = false;
          return ret;
        };
        """);
  }

  @Test
  public void testSetter() {
    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        Object.defineProperties(Foo.prototype, {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        """);

    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        Object.defineProperty(Foo, 'bar', {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        });
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        """);
  }

  @Test
  public void testEs5Getter() {
    testSame(
        """
        var bar = {
          enabled: false,
          get baz() {
            return this.enabled ? 'enabled' : 'disabled';
          }
        };
        function f() {
          bar.enabled = true;
          var ret = bar.baz;
          bar.enabled = false;
          return ret;
        };
        """);

    testSame(
        """
        var bar = {
          enabled: false,
          get baz() {
            return this.enabled ? 'enabled' : 'disabled';
          }
        };
        function f() {
          bar.enabled = true;
          var ret = bar?.baz;
          bar.enabled = false;
          return ret;
        };
        """);
  }

  @Test
  public void testEs5Getter_computed() {
    test(
        """
        var bar = {
          enabled: false,
          get ['baz']() {
            return this.enabled ? 'enabled' : 'disabled';
          }
        };
        function f() {
          bar.enabled = true;
          var ret = bar.baz;
          bar.enabled = false;
          return ret;
        };
        """,
        """
        var bar = {
          enabled: false,
          get ['baz']() {
            return this.enabled ? 'enabled' : 'disabled';
          }
        };
        function f() {
          true;
          var ret = bar.baz;
          bar.enabled = false;
          return ret;
        };
        """);
  }

  @Test
  public void testEs5Setter() {
    testSame(
        """
        var bar = {
          enabled: false,
          set baz(x) {
            this.x = this.enabled ? x * 2 : x;
          }
        };
        function f() {
          bar.enabled = true;
          bar.baz = 10;
          bar.enabled = false;
        };
        """);
  }

  @Test
  public void testEs5Setter_computed() {
    test(
        """
        var bar = {
          enabled: false,
          set ['baz'](x) {
            this.x = this.enabled ? x * 2 : x;
          }
        };
        function f() {
          bar.enabled = true;
          bar.baz = 10;
          bar.enabled = false;
        };
        """,
        """
        var bar = {
          enabled: false,
          set ['baz'](x) {
            this.x = this.enabled ? x * 2 : x;
          }
        };
        function f() {
          true;
          bar.baz = 10;
          bar.enabled = false;
        };
        """);
  }

  @Test
  public void testObjectDefineProperty_aliasedParams() {
    test(
        """
        function addGetter(obj, propName) {
          Object.defineProperty(obj, propName, {
            get: function() { return this[propName]; }
          });
        };
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        addGetter(Foo.prototype, 'x');
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        function addGetter(obj, propName) {
          Object.defineProperty(obj, propName, {
            get: function() { return this[propName]; }
          });
        };
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        addGetter(Foo.prototype, 'x');
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);

    test(
        """
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function addGetter(obj, propName) {
          Object.defineProperty(obj, propName, {
            get: function() { return this[propName]; }
          });
        };
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        addGetter(Foo.prototype, 'x');
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function addGetter(obj, propName) {
          Object.defineProperty(obj, propName, {
            get: function() { return this[propName]; }
          });
        };
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        addGetter(Foo.prototype, 'x');
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testObjectDefineProperty_aliasedObject() {
    testSame(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = Foo.prototype;
        Object.defineProperty(x, 'bar', {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        });
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testObjectDefineProperty_aliasedPropName() {
    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = 'bar';
        Object.defineProperty(Foo.prototype, x, {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        });
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = 'bar';
        Object.defineProperty(Foo.prototype, x, {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        });
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);

    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = 'bar';
        Object.defineProperty(Foo.prototype, x, {
          value: 10
        });
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = 'bar';
        Object.defineProperty(Foo.prototype, x, {
          value: 10
        });
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testObjectDefineProperty_aliasedPropSet() {
    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        };
        Object.defineProperty(Foo.prototype, 'bar', x);
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var x = {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        };
        Object.defineProperty(Foo.prototype, 'bar', x);
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testObjectDefineProperties_aliasedPropertyMap() {
    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }};
        Object.defineProperties(Foo.prototype, properties);
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }};
        Object.defineProperties(Foo.prototype, properties);
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);

    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        };
        Object.defineProperties(Foo.prototype, {bar: properties});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        };
        Object.defineProperties(Foo.prototype, {bar: properties});
        function f() {
          var foo = new Foo()
          true;
          foo.bar = 10;
          foo.enabled = false;
        }
        function z() {
          var x = {};
          10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testObjectDefineProperties_aliasedObject() {
    test(
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }};
        var x = Foo.prototype;
        Object.defineProperties(x, {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          foo.enabled = false;
          foo.enabled = true;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.enabled = false; this.x = null; };
        var properties = {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }};
        var x = Foo.prototype;
        Object.defineProperties(x, {bar: {
          set: function (x) { this.x = this.enabled ? x * 2 : x; }
        }});
        function f() {
          var foo = new Foo()
          foo.enabled = true;
          foo.bar = 10;
          false;
          foo.enabled = true;
        }
        function z() {
          var x = {};
          x.bar = 10;
          x.bar = 20;
        }
        """);
  }

  @Test
  public void testPropertyDefinedInExterns() {
    String externs =
        """
        var window = {};
        /** @type {number} */ window.innerWidth
        /** @constructor */
        var Image = function() {};
        /** @type {string} */ Image.prototype.src;
        """;

    testSame(
        externs(externs),
        srcs(
            """
            function z() {
              window.innerWidth = 10;
              window.innerWidth = 20;
            }
            """));

    testSame(
        externs(externs),
        srcs(
            """
            function z() {
              var img = new Image();
              img.src = '';
              img.src = 'foo.bar';
            }
            """));

    testSame(
        externs(externs),
        srcs(
            """
            function z(x) {
              x.src = '';
              x.src = 'foo.bar';
            }
            """));
  }

  @Test
  public void testJscompInherits() {
    test(
        """
        /** @constructor */ function Foo() { this.bar = null; };
        var $jscomp = {};
        $jscomp.inherits = function(x) {
          Object.defineProperty(x, x, x);
        };
        function f() {
          var foo = new Foo()
          foo.bar = 10;
          foo.bar = 20;
        }
        """,
        """
        /** @constructor */ function Foo() { this.bar = null; };
        var $jscomp = {};
        $jscomp.inherits = function(x) {
          Object.defineProperty(x, x, x);
        };
        function f() {
          var foo = new Foo()
          10;
          foo.bar = 20;
        }
        """);
  }

  @Test
  public void testGithubIssue2874() {
    testSame(
        """
        var globalObj = {i0:0};

        function func(b) {
          var g = globalObj;
          var f = b;
          g.i0 = f.i0;
          g = b;
          g.i0 = 0;
        }
        func({i0:2});
        alert(globalObj);
        """);
  }

  @Test
  public void testReplaceShorthandAssignmentOpWithRegularOp() {
    // See https://github.com/google/closure-compiler/issues/3017
    test(
        """
        function f(obj) { // preserve newlines
          obj.a = (obj.a |= 2) | 8;
          obj.a = (obj.a |= 16) | 32;
        }
        """,
        """
        function f(obj) { // preserve newlines
          obj.a = (obj.a | 2) | 8;
          obj.a = (obj.a | 16) | 32;
        }
        """);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeadPropertyAssignmentElimination(compiler);
  }
}
