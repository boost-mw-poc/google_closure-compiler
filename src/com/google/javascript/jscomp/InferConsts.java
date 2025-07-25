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

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.jspecify.annotations.Nullable;

/**
 * Attaches the CONST_VAR annotation to any variable that's
 * 1) Provably well-defined and assigned once in its lifetime.
 * 2) Annotated 'const'
 * 3) Declared with the 'const' keyword.
 *
 * These 3 are considered semantically equivalent. Notice that a variable
 * in a loop is never considered const.
 *
 * Note that criteria (1) is only used for normal code, not externs.
 */
class InferConsts implements CompilerPass {
  private final AbstractCompiler compiler;

  InferConsts(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node js) {
    ReferenceCollector collector =
        new ReferenceCollector(
            compiler, ReferenceCollector.DO_NOTHING_BEHAVIOR, new SyntacticScopeCreator(compiler));
    collector.process(js);

    for (Var v : collector.getAllSymbols()) {
      considerVar(v, collector.getReferences(v));
    }

    Scope globalExternsScope = new SyntacticScopeCreator(compiler).createScope(externs, null);
    for (Var v : globalExternsScope.getAllSymbols()) {
      considerVar(v, null);
    }
  }

  private void considerVar(Var v, @Nullable ReferenceCollection refCollection) {
    if (v.isImplicitGoogNamespace()) {
      return; // no name node for provided variables.
    }
    Node nameNode = v.getNameNode();
    JSDocInfo docInfo = v.getJSDocInfo();
    if (docInfo != null && docInfo.isConstant()) {
      nameNode.setDeclaredConstantVar(true);
    } else if (nameNode != null && nameNode.getParent().isConst()) {
      nameNode.setDeclaredConstantVar(true);
    }
    if (isInferredConst(v, refCollection)) {
      nameNode.setInferredConstantVar(true);
    }
  }

  private static boolean isInferredConst(Var v, ReferenceCollection refCollection) {
    Node nameNode = v.getNameNode();
    if (nameNode == null || refCollection == null || !refCollection.isAssignedOnceInLifetime()) {
      return false;
    }
    if (v.isImplicitGoogNamespace()) {
      return false;
    }
    Token declarationType = v.declarationType();

    return switch (declarationType) {
      case LET ->
          // Check that non-destructuring let names are actually assigned at declaration.
          !nameNode.getParent().isLet() || nameNode.hasChildren();
      case CONST,
          CATCH,
          CLASS,
          PARAM_LIST, // Parameters cannot be referenced before declaration.
          FUNCTION -> // Function hoisting means no references before declaration.
          true;
      case IMPORT ->
          // ES module exports are mutable.
          // TODO(lharker): make this check smarter if we start optimizing unrewritten modules.
          false;
      case VAR ->
          // var hoisting requires this extra work to make sure the 'declaration' is also the first
          // reference.
          refCollection.firstReferenceIsAssigningDeclaration() && refCollection.isWellDefined();
      default ->
          throw new IllegalStateException("Unrecognized declaration type " + declarationType);
    };
  }
}
