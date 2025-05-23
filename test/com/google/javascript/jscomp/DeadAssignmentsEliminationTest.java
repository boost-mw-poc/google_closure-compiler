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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DeadAssignmentsElimination}. */
@RunWith(JUnit4.class)
public final class DeadAssignmentsEliminationTest extends CompilerTestCase {

  public DeadAssignmentsEliminationTest() {
    super("var extern;");
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return (externs, js) ->
        NodeTraversal.traverse(compiler, js, new DeadAssignmentsElimination(compiler));
  }

  @Test
  public void testSimple() {
    inFunction("var a; a=1", "var a; 1");
    inFunction("var a; a=1+1", "var a; 1+1");
    inFunction("var a; a=foo();", "var a; foo()");
    inFunction("var a; a=foo?.();", "var a; foo?.()");
    inFunction("a=1; var a; a=foo();", "1; var a; foo();");
    inFunction("a=1; var a; a=foo?.();", "1; var a; foo?.();");
    // This should be: "var a; (function f(){})", but we don't mess with
    // functions with inner functions.
    inFunction("var a; a=function f(){}");
  }

  @Test
  public void testPropAssignmentNotRemoved() {
    // We only remove dead assignments when lhs is a name node.
    inFunction("var a = {b:1}; a.b=1+1");
    inFunction("var a = {b:1}; a.b=foo();");
  }

  @Test
  public void testArguments() {
    test("function f(a){ a=1; }", "function f(a){ 1; }");
    test("function f(a){ a=1+1; }", "function f(a){ 1+1; }");
    test("function f(a){ a=foo(); }", "function f(a){ foo(); }");
    test("function f(a){ a=foo?.(); }", "function f(a){ foo?.(); }");
    test("function f(a){ a=1; a=foo(); }", "function f(a){ 1; foo(); }");
    test("function f(a){ a=1; a=foo?.(); }", "function f(a){ 1; foo?.(); }");
  }

  @Test
  public void testLoops() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("for(var a=0; a<10; a++) {}");
    inFunction("var x; for(var a=0; a<10; a++) {x=a}; a(x)");
    inFunction("var x; for(var a=0; x=a<10; a++) {}", "var x; for(var a=0; a<10; a++) {}");
    inFunction("var x; for(var a=0; a<10; x=a) {}", "var x; for(var a=0; a<10; a) {}");
    inFunction("var x; for(var a=0; a<10; x=a,a++) {}", "var x; for(var a=0; a<10; a,a++) {}");
    inFunction("var x; for(var a=0; a<10; a++,x=a) {}", "var x; for(var a=0; a<10; a++,a) {}");
    inFunction("var x;for(var a=0; a<10; a++) {x=1}", "var x;for(var a=0; a<10; a++) {1}");
    inFunction("var x; x=1; do{x=2}while(0); x", "var x; 1; do{x=2}while(0); x");
    inFunction("var x; x=1; while(1){x=2}; x");
  }

  @Test
  public void testMultiPaths() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x,y; if(x)y=1;", "var x,y; if(x)1;");
    inFunction("var x,y; if(x)y=1; y=2; x(y)", "var x,y; if(x)1; y=2; x(y)");
    inFunction("var x; switch(x) { case(1): x=1; break; } x");
    inFunction(
        "var x; switch(x) { case(1): x=1; break; }", "var x; switch(x) { case(1): 1; break; }");
  }

  @Test
  public void testUsedAsConditions() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; while(x=1){}", "var x; while(1){}");
    inFunction("var x; if(x=1){}", "var x; if(1){}");
    inFunction("var x; do{}while(x=1)", "var x; do{}while(1)");
    inFunction("var x; if(x=1==4&&1){}", "var x; if(1==4&&1) {}");
    inFunction("var x; if(0&&(x=1)){}", "var x; if(0&&1){}");
    inFunction("var x; if((x=2)&&(x=1)){}", "var x; if(2&&1){}");
    inFunction("var x; x=2; if(0&&(x=1)){}; x");

    inFunction("var x,y; if( (x=1)+(y=2) > 3){}", "var x,y; if( 1+2 > 3){}");
  }

  @Test
  public void nullishCoalesce() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; if(x=1==4??1){}", "var x; if(1==4??1) {}");
    inFunction("var x; if(0??(x=1)){}", "var x; if(0??1){}");
    inFunction("var x; if((x=2)??(x=1)){}", "var x; if(2??1){}");
    inFunction("var x; x=2; if(0??(x=1)){}; x");
    inFunction("var a, b; if ((a = 1) ?? (b = a)) {b}");
    inFunction("var a, b; if ((b = a) ?? (a = 1)) {b}", "var a, b; if ((b = a) ?? (1)) {b}");
    inFunction("var a; (a = 1) ?? (a = 2)", "var a; 1 ?? 2");
  }

  @Test
  public void testUsedAsConditionsInSwitchStatements() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; switch(x=1){}", "var x; switch(1){}");
    inFunction("var x; switch(x){case(x=1):break;}", "var x; switch(x){case(1):break;}");

    inFunction("var x,y; switch(y) { case (x += 1): break; case (x): break;}");

    inFunction(
        "var x,y; switch(y) { case (x = 1): break; case (2): break;}",
        "var x,y; switch(y) { case (1): break; case (2): break;}");
    inFunction(
        "var x,y; switch(y) { case (x+=1): break; case (x=2): break;}",
        "var x,y; switch(y) { case (x+1): break; case (2): break;}");
  }

  @Test
  public void testAssignmentInReturn() {
    inFunction("var x; return x = 1;", "var x; return 1");
    inFunction("var x; return");
  }

  @Test
  public void testAssignmentSamples() {
    // We want this to be "var x" in these cases.
    inFunction("var x = 2;");
    inFunction("var x = 2; x++;", "var x=2; void 0");
    inFunction("var x; x=x++;", "var x;x++");
    inFunction("var x; x+=1;", "var x;x+1");
  }

  @Test
  public void testAssignmentInArgs() {
    inFunction("var x; foo(x = 1);", "var x; foo(1);");
    inFunction("var x; return foo(x = 1);", "var x; return foo(1);");
  }

  /** BUG #1358904 */
  @Test
  public void testAssignAndReadInCondition() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var a, b; if ((a = 1) && (b = a)) {b}");
    inFunction("var a, b; if ((b = a) && (a = 1)) {b}", "var a, b; if ((b = a) && (1)) {b}");
  }

  @Test
  public void testParameters() {
    inFunction("param1=1; param1=2; param2(param1)", "1; param1=2; param2(param1)");
    inFunction("param1=param2()", "param2()");
  }

  @Test
  public void testErrorHandling() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; try{ x=1 } catch(e){ x=2 }; x");
    inFunction("var x; try{ x=1 } catch(e){ x=2 }", "var x;try{ 1 } catch(e) { 2 }");
    inFunction("var x; try{ x=1 } finally { x=2 }; x", "var x;try{ 1 } finally{ x=2 }; x");
    inFunction("var x; while(1) { try{x=1;break}finally{x} }");
    inFunction("var x; try{throw 1} catch(e){x=2} finally{x}");
    inFunction(
        "var x; try{x=1;throw 1;x} finally{x=2}; x", "var x; try{1;throw 1;x} finally{x=2}; x");
  }

  @Test
  public void testErrorHandling2() {
    inFunction(
        """
        try {
        } catch (e) {
          e = 1;
          let g = e;
          print(g)
        }
        """);

    inFunction(
        """
        try {
        } catch (e) {
            e = 1;
            {
              let g = e;
              print(g)
            }
        }
        """);
  }

  @Test
  public void testDeadVarDeclarations1() {
    inFunction("var x=1; x=2; x", "var x; 1; x=2; x");
  }

  @Test
  public void testDeadVarDeclarations2() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x=1;");
    inFunction("var x=1; x=2; x", "var x; 1; x=2; x");
    inFunction("var x=1, y=10; x=2; x", "var x; 1; var y; 10; x=2; x");
    inFunction("var x=1, y=x; y");
    inFunction("var x=1, y=x; x=2; x", "var x = 1; var y; x; x=2; x;");
  }

  @Test
  public void testDeadVarDeclarations_forLoop() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("for(var x=1;;);");
    inFunction("for(var x=1,y=x;;);");
    inFunction("for(var x=1;10;);");
  }

  @Test
  public void testGlobal() {
    // Doesn't do any work on global scope yet.
    test("var x; x=1; x=2; x=3;", "var x; x=1; x=2; x=3;");
  }

  @Test
  public void testInnerFunctions() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x = function() { var x; x=1; }", "var x = function() { var x; 1; }");
  }

  @Test
  public void testInnerFunctions2() {
    // Give up DCE if there is a inner function.
    inFunction("var x = 0; print(x); x = 1; var y = function(){}; y()");
  }

  @Test
  public void testSelfReAssignment() {
    inFunction("var x; x = x;", "var x; x");
  }

  @Test
  public void testSelfIncrement() {
    inFunction("var x; x = x + 1;", "var x; x + 1");
  }

  @Test
  public void testAssignmentOp() {
    // We have remove constant expressions that cleans this one up.
    inFunction("var x; x += foo()", "var x; x + foo()");
  }

  @Test
  public void testAssignmentOpUsedAsLhs() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x,y; y = x += foo(); print(y)", "var x,y; y = x +  foo(); print(y)");
    inFunction("var x,y; y = x += foo?.(); print(y)", "var x,y; y = x +  foo?.(); print(y)");
  }

  @Test
  public void testAssignmentOpUsedAsCondition() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; if(x += foo()) {}", "var x; if(x +  foo()) {}");
    inFunction("var x; if(x += foo?.()) {}", "var x; if(x +  foo?.()) {}");

    inFunction("var x; if((x += foo()) > 1) {}", "var x; if((x +  foo()) > 1) {}");

    // Not in a while because this happens every loop.
    inFunction("var x; while((x += foo()) > 1) {}");

    inFunction("var x; for(;--x;){}");
    inFunction("var x; for(;x--;){}");
    inFunction("var x; for(;x -= 1;){}");
    inFunction("var x; for(;x = 0;){}", "var x; for(;0;){}");

    inFunction("var x; for(;;--x){}");
    inFunction("var x; for(;;x--){}");
    inFunction("var x; for(;;x -= 1){}");
    inFunction("var x; for(;;x = 0){}", "var x; for(;;0){}");

    inFunction("var x; for(--x;;){}", "var x; void 0; for(;;){}");
    inFunction("var x; for(x--;;){}", "var x; void 0; for(;;){}");
    inFunction("var x; for(x -= 1;;){}", "var x; for(x - 1;;){}");
    inFunction("var x; for(x = 0;;){}", "var x; for(0;;){}");
  }

  @Test
  public void testDeadIncrement() {
    // TODO(user): Optimize this.
    inFunction("var x; x ++", "var x; void 0");
    inFunction("var x; x --", "var x; void 0");
  }

  @Test
  public void testDeadButAlivePartiallyWithinTheExpression() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x; x = 100, print(x), x = 101;", "var x; x = 100, print(x),     101;");
    inFunction(
        "var x; x = 100, print(x), print(x), x = 101;",
        "var x; x = 100, print(x), print(x),     101;");
    inFunction(
        "var x; x = 100, print(x), x = 0, print(x), x = 101;",
        "var x; x = 100, print(x), x = 0, print(x),     101;");

    // Here, `a=C` is removed as it is dead. `X=a` is removed as it is dead.
    inFunction(
        "var a, C, X, S; if ((X = a) && (a = C)) {}; a = S;", //
        "var a, C, X, S; if (a&&C) {}; S;");

    // Here, `a=C` is preserved as it is NOT dead. `X=a` is removed as it is dead.
    inFunction(
        "var a, C, X, S; if ((a = C) && (X = a)) {}; a = S;",
        "var a, C, X, S; if ((a = C) && a) {}; S;");
  }

  @Test
  public void testMutipleDeadAssignmentsButAlivePartiallyWithinTheExpression() {
    inFunction(
        """
        var x; x = 1, x = 2, x = 3, x = 4, x = 5,
          print(x), x = 0, print(x), x = 101;
        """,
        "var x; 1, 2, 3, 4, x = 5, print(x), x = 0, print(x), 101;");
  }

  @Test
  public void testDeadPartiallyWithinTheExpression() {
    // Sadly, this is not covered. We don't suspect this would happen too
    // often.
    inFunction("var x; x = 100, x = 101; print(x);");
  }

  @Test
  public void testAssignmentChain() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var a,b,c,d,e; a = b = c = d = e = 1", "var a,b,c,d,e; 1");
    inFunction(
        "var a,b,c,d,e; a = b = c = d = e = 1; print(c)",
        "var a,b,c,d,e;         c = 1        ; print(c)");
    inFunction(
        "var a,b,c,d,e; a = b = c = d = e = 1; print(a + e)",
        "var a,b,c,d,e; a =             e = 1; print(a + e)");
    inFunction(
        "var a,b,c,d,e; a = b = c = d = e = 1; print(b + d)",
        "var a,b,c,d,e;     b =     d     = 1; print(b + d)");
    inFunction(
        "var a,b,c,d,e; a = b = c = d = e = 1; print(a + b + d + e)",
        "var a,b,c,d,e; a = b =     d = e = 1; print(a + b + d + e)");
    inFunction("var a,b,c,d,e; a = b = c = d = e = 1; print(a+b+c+d+e)");
  }

  @Test
  public void testAssignmentOpChain() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var a,b,c,d,e; a = b = c += d = e = 1", "var a,b,c,d,e;         c + 1");
    inFunction(
        "var a,b,c,d,e; a = b = c += d = e = 1;  print(e)",
        "var a,b,c,d,e;         c +     (e = 1); print(e)");
    inFunction(
        "var a,b,c,d,e; a = b = c += d = e = 1;  print(d)",
        "var a,b,c,d,e;         c + (d = 1)  ;   print(d)");
    inFunction(
        "var a,b,c,d,e; a = b = c += d = e = 1;  print(a)",
        "var a,b,c,d,e; a =     c +          1;  print(a)");
  }

  @Test
  public void testIncDecInSubExpressions() {
    inFunction("var a; a = 1, a++; a");
    inFunction("var a; a = 1, ++a; a");
    inFunction("var a; a = 1, a--; a");
    inFunction("var a; a = 1, --a; a");

    inFunction("var a; a = 1, a++, print(a)");
    inFunction("var a; a = 1, ++a, print(a)");
    inFunction("var a; a = 1, a--, print(a)");
    inFunction("var a; a = 1, --a, print(a)");

    inFunction("var a; a = 1, print(a++)");
    inFunction("var a; a = 1, print(++a)");

    inFunction("var a; a = 1, print(a++)");
    inFunction("var a; a = 1, print(++a)");

    inFunction("var a; a = 1, print(a--)");
    inFunction("var a; a = 1, print(--a)");
  }

  @Test
  public void testNestedReassignments() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var a; a = (a = 1)", "var a; 1");
    inFunction("var a; a = (a *= 2)", "var a; a*2");

    // Note a = (a++) is not same as a++. Only if 'a' is dead.
    inFunction("var a; a = (a++)", "var a; a++"); // Preferred: "var a"
    inFunction("var a; a = (++a)", "var a; ++a"); // Preferred: "var a"

    inFunction("var a; a = (b = (a = 1))", "var a; b = 1");
    inFunction("var a; a = (b = (a *= 2))", "var a; b = a * 2");
    inFunction("var a; a = (b = (a++))", "var a; b=a++");
    inFunction("var a; a = (b = (++a))", "var a; b=++a");

    // Include b as local.
    inFunction("var a,b; a = (b = (a = 1))", "var a,b; 1");
    inFunction("var a,b; a = (b = (a *= 2))", "var a,b; a * 2");
    inFunction("var a,b; a = (b = (a++))", "var a,b; a++"); // Preferred: "var a,b"
    inFunction("var a,b; a = (b = (++a))", "var a,b; ++a"); // Preferred: "var a,b"

    inFunction("var a; a += (a++)", "var a; a + a++");
    inFunction("var a; a += (++a)", "var a; a+ (++a)");

    // Include b as local.
    inFunction("var a,b; a += (b = (a = 1))", "var a,b; a + 1");
    inFunction("var a,b; a += (b = (a *= 2))", "var a,b; a + (a * 2)");
    inFunction("var a,b; a += (b = (a++))", "var a,b; a + a++");
    inFunction("var a,b; a += (b = (++a))", "var a,b; a+(++a)");
  }

  @Test
  public void testIncrementalReassignmentInForLoops() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("for(;x+=1;x+=1) {}");
    inFunction("for(;x;x+=1){}");
    inFunction("for(;x+=1;){foo(x)}");
    inFunction("for(;1;x+=1){foo(x)}");
  }

  @Test
  public void testIdentityAssignments() {
    inFunction("var x; x=x", "var x; x");
    inFunction("var x; x.y=x.y");
  }

  @Test
  public void testBug8730257() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        try {
           var sortIndices = {};
           sortIndices = bar();
           for (var i = 0; i < 100; i++) {
             var sortIndex = sortIndices[i];
             bar(sortIndex);
           }
         } finally {
           bar();
         }
        """);
  }

  @Test
  public void testAssignToExtern() {
    inFunction("extern = true;");
  }

  @Test
  public void testIssue297a() {
    testSame(
        """
        function f(p) {
         var x;
         return ((x=p.id) && (x=parseInt(x.substr(1))) && x>0);
        }; f('');
        """);
  }

  @Test
  public void testIssue297b() {
    test(
        """
        function f() {
         var x;
         return (x='') && (x = x.substr(1));
        };
        """,
        """
        function f() {
         var x;
         return (x='') && (x.substr(1));
        };
        """);
  }

  @Test
  public void testIssue297c() {
    test(
        """
        function f() {
         var x;
         return (x=1) && (x = f(x));
        };
        """,
        """
        function f() {
         var x;
         return (x=1) && f(x);
        };
        """);
  }

  @Test
  public void testIssue297d() {
    test(
        """
        function f(a) {
         return (a=1) && (a = f(a));
        };
        """,
        """
        function f(a) {
         return (a=1) && (f(a));
        };
        """);
  }

  @Test
  public void testIssue297e() {
    test(
        """
        function f(a) {
         return (a=1) - (a = g(a));
        };
        """,
        """
        function f(a) {
         return (a=1) - (g(a));
        };
        """);
  }

  @Test
  public void testIssue297f() {
    test(
        """
        function f(a) {
         h((a=1) - (a = g(a)));
        };
        """,
        """
        function f(a) {
         h((a=1) - (g(a)));
        };
        """);
  }

  @Test
  public void testIssue297g() {
    test(
        """
        function f(a) {
         var b = h((b=1) - (b = g(b)));
         return b;
        };
        """,
        // The last assignment in the initializer should be eliminated
        """
        function f(a) {
         var b = h((b=1) - (b = g(b)));
         return b;
        };
        """);
  }

  @Test
  public void testIssue297h() {
    test(
        """
        function f(a) {
         var b = b=1;
         return b;
        };
        """,
        // The assignment in the initializer should be eliminated
        """
        function f(a) {
         var b = b = 1;
         return b;
        };
        """);
  }

  @Test
  public void testInExpression0() {
    inFunction("var a; return a=(a=(a=a));", "var a; return a;");
  }

  @Test
  public void testInExpression1() {
    inFunction("var a; return a=(a=(a=3));", "var a; return 3;");
    inFunction("var a; return a=(a=(a=a));", "var a; return a;");
    inFunction("var a; return a=(a=(a=a+1)+1);", "var a; return a+1+1;");
    inFunction("var a; return a=(a=(a=f(a)+1)+1);", "var a; return f(a)+1+1;");
    inFunction("var a; return a=f(a=f(a=f(a)));", "var a; return f(f(f(a)));");
  }

  @Test
  public void testInExpression2() {
    inFunction("var a; (a = 1) || (a = 2)", "var a; 1 || 2");

    inFunction("var a; (a = 1) || (a = 2); return a");

    inFunction("var a; a = 1; a ? a = 2 : a;", "var a; a = 1; a ?     2 : a;");

    inFunction("var a; a = 1; a ? a = 2 : a; return a");

    inFunction("var a; a = 1; a ? a : a = 2;", "var a; a = 1; a ? a : 2;");

    inFunction("var a; a = 1; a ? a : a =2; return a");

    inFunction("var a; (a = 1) ? a = 2 : a = 3;", "var a;      1  ?     2 :     3;");

    // This can be improved.  "a = 1" is dead but "a" is read in the following
    // expression.
    inFunction("var a; (a = 1) ? a = 2 : a = 3; return a");
  }

  @Test
  public void testIssue384a() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        var a, b;
        if (f(b = true) || f(b = false))
          a = b;
        else
          a = null;
        return a;
        """);
  }

  @Test
  public void testIssue384b() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        var a, b;
        (f(b = true) || f(b = false)) ? (a = b) : (a = null);
        return a;
        """);
  }

  @Test
  public void testIssue384c() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        var a, b;
        (a ? f(b = true) : f(b = false)) && (a = b);
        return a;
        """);
  }

  @Test
  public void testIssue384d() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        var a, b;
        (f(b = true) || f(b = false)) && (a = b);
        return a;
        """);
  }

  @Test
  public void testForIn() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x = {}; for (var y in x) { y() }");
    inFunction(
        "var x, y, z; x = {}; z = {}; for (y in x = z) { y() }",
        "var x, y, z;   ({}); z = {}; for (y in z)     { y() }");
    inFunction(
        "var x, y, z; x = {}; z = {}; for (y[z=1] in z) { y() }",
        "var x, y, z;   ({}); z = {}; for (y[z=1] in z) { y() }");

    // "x in z" doesn't overwrite x if z is empty.
    // TODO(user): If you look outside of just liveness, x = {} is dead.
    // That probably requires value numbering or SSA to detect that case.
    inFunction("var x, y, z; x = {}; z = {}; for (x in z) { x() }");
  }

  @Test
  public void testArrowFunction() {
    test("() => {var x; x = 1}", "() => {var x; 1}");

    test("(a) => {a = foo()}", "(a) => {foo()}");
  }

  @Test
  public void testClassMethods() {
    test(
        """
        class C{
          func() {
            var x;
            x = 1;
          }
        }
        """,
        """
        class C{
          func() {
            var x;
            1;
          }
        }
        """);

    test(
        """
        class C{
          constructor(x, y) {
            this.x = x;
            this.y = y;
          }
          func() {
            var z;
            z = 1;
            this.x = 3
          }
        }
        """,
        """
        class C{
          constructor(x, y) {
            this.x = x;
            this.y = y;
          }
          func() {
            var z;
            1;
            this.x = 3
          }
        }
        """);
  }

  @Test
  public void testClassStaticBlocks() {
    // TODO(b/240443227): Improve ClassStaticBlock optimization, dead code is not removed in
    // expression.
    testSame(
        """
        class C{
          static{
            var x;
            x = 1;
          }
        }
        """);

    testSame(
        """
         var x = 0;
         print(x);
          x = 1;
          class C {
           static {
            print(x);
            }
          }
        """);
  }

  @Test
  public void testGenerators() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    test(
        """
        function* f() {
          var x, y;
          x = 1; y = 2;
          yield y;
        }
        """,
        """
        function* f() {
          var x, y;
          1; y = 2;
          yield y;
        }
        """);
  }

  @Test
  public void testForOf() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var x = {}; for (var y of x) { y() }");

    inFunction(
        "var x, y, z; x = {}; z = {}; for (y of x = z) {}",
        "var x, y, z;   ({}); z = {}; for (y of z)     {}");
  }

  @Test
  public void testForAwaitOf() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inAsyncFunction("var x = {}; for await (var y of x) { y() }");

    inAsyncFunction(
        "var x, y, z; x = {}; z = {}; for await (y of x = z) {}",
        "var x, y, z;   ({}); z = {}; for await (y of z)     {}");
  }

  @Test
  public void testTemplateStrings() {
    inFunction("var name; name = 'Foo'; `Hello ${name}`");

    inFunction(
        "var name; name = 'Foo'; name = 'Bar'; `Hello ${name}`",
        "var name; 'Foo'; name = 'Bar'; `Hello ${name}`");
  }

  @Test
  public void testDestructuring() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("var a, b, c; [a, b, c] = [1, 2, 3];");

    inFunction("var a, b, c; [a, b, c] = [1, 2, 3]; return a + c;");

    inFunction(
        "var a, b; a = 1; b = 2; [a, b] = [3, 4]; return a + b;",
        "var a, b; 1; 2; [a, b] = [3, 4]; return a + b;");

    inFunction("var x; x = {}; [x.a] = [3];");
  }

  @Test
  public void testDestructuringDeclarationRvalue() {
    // Test array destructuring
    inFunction(
        """
        let arr = []
        if (CONDITION) {
          arr = [3];
        }
        let [foo] = arr;
        use(foo);
        """);

    // Test object destructuring
    inFunction(
        """
        let obj = {}
        if (CONDITION) {
          obj = {foo: 3};
        }
        let {foo} = obj;
        use(foo);
        """);
  }

  @Test
  public void testDestructuringAssignmentRValue() {
    // Test array destructuring
    inFunction(
        """
        let arr = []
        if (CONDITION) {
          arr = [3];
        }
        let foo;
        [foo] = arr;
        use(foo);
        """);

    // Test object destructuring
    inFunction(
        """
        let obj = {}
        if (CONDITION) {
          obj = {foo: 3};
        }
        let foo;
        ({foo} = obj);
        use(foo);
        """);
  }

  @Test
  public void testForOfWithDestructuring() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        let x;
        x = [];
        var y = 5; // Don't eliminate because if arr is empty, y will remain 5.
        for ([y = x] of arr) { y; }
        y;
        """);

    inFunction(
        """
        let x;
        x = [];
        for (let [y = x] of arr) { y; }
        """);

    inFunction("for (let [key, value] of arr) {}");
    inFunction("for (let [key, value] of arr) { key; value; }");
    inFunction(
        "var a; a = 3; for (let [a] of arr) { a; }", "var a; 3; for (let [a] of arr) { a; }");
  }

  @Test
  public void testReferenceInDestructuringPatternDefaultValue() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction(
        """
        let bar = [];
        const {foo = bar} = obj;
        foo;
        """);

    inFunction(
        """
        let bar;
        bar = [];
        const {foo = bar} = obj;
        foo;
        """);

    inFunction("let bar; bar = 3; const [foo = bar] = arr; foo;");
    inFunction("let foo, bar; bar = 3; [foo = bar] = arr; foo;");
  }

  @Test
  public void testReferenceInDestructuringPatternComputedProperty() {
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    inFunction("let str; str = 'bar'; const {[str + 'baz']: foo} = obj; foo;");

    inFunction(
        """
        let obj = {};
        let str, foo;
        str = 'bar';
        ({[str + 'baz']: foo} = obj);
        foo;
        """);
  }

  @Test
  public void testDefaultParameter() {
    test(
        """
        function f(x, y = 12) {
          var z;
          z = y;
        }
        """,
        """
        function f(x, y = 12) {
          var z;
          y;
        }
        """);
  }

  @Test
  public void testObjectLiterals() {
    test(
        """
        var obj = {
          f() {
          var x;
          x = 2;
          }
        }
        """,
        """
        var obj = {
          f() {
          var x;
          2;
          }
        }
        """);
  }

  @Test
  public void testObjectLiteralsComputedProperties() {
    inFunction("let a; a = 2; let obj = {[a]: 3}; obj");
  }

  @Test
  public void testSpread_consideredRead() {
    inFunction(
        """
        var a;
        a = [];
        [...a];
        """);

    inFunction(
        """
        var a;
        a = {};
        ({...a});
        """);
  }

  @Test
  public void testRest_notConsideredWrite() {
    // TODO(b/126441776): The initial writes are dead. The pass should rewrite to the commented
    // code.

    inFunction(
        """
        var a = 9;
        [...a] = itr;
        return a;
        """
        // ,
        // """
        // var a;
        // [...a] = itr;
        // return a;
        // """
        );

    inFunction(
        """
        var a = 9;
        ({...a} = obj);
        return a;
        """
        // ,
        // """
        // var a;
        // ({...a} = obj);
        // return a;
        // """
        );
  }

  @Test
  public void testDestructuring_notConsideredWrite() {
    // TODO(b/126441776): The initial writes are dead. The pass should rewrite to the commented
    // code.

    inFunction(
        """
        var a = 9;
        [a] = itr;
        return a;
        """
        // ,
        // """
        // var a;
        // [a] = itr;
        // return a;
        // """
        );

    inFunction(
        """
        var a = 9;
        ({a} = obj);
        return a;
        """
        // ,
        // """
        // var a;
        // ({a} = obj);
        // return a;
        // """
        );
  }

  @Test
  public void testRest_isNotRemovable() {
    // TODO(b/126441776): Elimination is possible here under getter/setter assumptions. Determine if
    // this is the correct behaviour.

    inFunction(
        """
        var a;
        [...a] = itr;
        """);

    inFunction(
        """
        var a;
        ({...a} = obj);
        """);
  }

  @Test
  public void testDestructuring_isNotRemovable() {
    // TODO(b/126441776): Elimination is possible here under getter/setter assumptions. Determine if
    // this is the correct behaviour.

    inFunction(
        """
        var a;
        [a] = itr;
        """);

    inFunction(
        """
        var a;
        ({a} = obj);
        """);
  }

  @Test
  public void testLet() {
    inFunction("let a; a = 2;", "let a; 2;");

    inFunction(
        "let a; let b; a = foo(); b = 2; return b;", "let a; let b; foo(); b = 2; return b;");

    inFunction(
        "let a; let b; a = foo?.(); b = 2; return b;", //
        "let a; let b; foo?.(); b = 2; return b;");
  }

  @Test
  public void testConst1() {
    inFunction("const a = 1;");
  }

  @Test
  public void testConst2() {
    test(
        "async function f(d) { if (d) { d = 5; } const a = 1; const b = 2; const [x, y] = b; }",
        "async function f(d) { if (d) {     5; } const a = 1; const b = 2; const [x, y] = b; }");
  }

  @Test
  public void testBlockScoping() {
    inFunction(
        """
        let x;
        {
          let x;
          x = 1;
        }
        x = 2;
        return x;
        """,
        """
        let x;
        {
          let x$jscomp$1;
          1;
        }
        x = 2;
        return x;
        """);

    inFunction(
        """
        let x;
        x = 2
        {
          let x;
          x = 1;
        }
        print(x);
        """,
        """
        let x;
        x = 2;
        {
          let x$jscomp$1;
          1;
        }
        print(x);
        """);
  }

  @Test
  public void testComputedClassField() {
    inFunction(
        """
        let x;
        class C {
          static [x = 'field1'] = (x = 5);
          [x = 'field2'] = 7;
        }
        """,
        """
        let x;
        class C {
          static ['field1'] = 5;
          ['field2'] = 7;
        }
        """);

    inFunction(
        """
        let x;
        class C {
          static [x = 'field1'] = (x = 5);
          [x = 'field2'] = 7;
        }
        use(x);
        """);

    inFunction(
        """
        let x;
        class C {
          static field1 = x;
          [x = 'field2'] = 7;
        }
        use(C.field1);
        """,
        """
        let x;
        class C {
          static field1 = x;
        // TODO(b/189993301): don't remove 'x = field2' because it's read by 'field1 = x'
          ['field2'] = 7;
        }
        use(C.field1);
        """);
  }

  @Test
  public void testComputedClassMethod() {
    inFunction(
        """
        let x;
        class C {
        // NOTE: it would be correct to eliminate the following two assignments
          static [x = 'field1']() {}
          [x = 'field2']() {}
        }
        """);

    inFunction(
        """
        let x;
        class C {
          static [x = 'field1']() {};
          [x = 'field2']() {}
        }
        use(x);
        """);

    inFunction(
        """
        let x;
        class C {
          static field1 = x;
          [x = 'field2']() {}
        }
        use(C.field1);
        """);
  }

  private void inFunction(String src) {
    inFunction(src, src);
  }

  private void inFunction(String src, String expected) {
    test(
        "function FUNC(param1, param2){" + src + "}",
        "function FUNC(param1, param2){" + expected + "}");
  }

  private void inAsyncFunction(String src) {
    inAsyncFunction(src, src);
  }

  private void inAsyncFunction(String src, String expected) {
    test(
        "async function FUNC(param1, param2){" + src + "}",
        "async function FUNC(param1, param2){" + expected + "}");
  }
}
