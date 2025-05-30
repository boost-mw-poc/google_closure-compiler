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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.PolymerPass.MemberDefinition;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Polymer class (element) definition. Includes convenient fields for rewriting the class.
 */
final class PolymerClassDefinition {
  static enum DefinitionType {
    ObjectLiteral,
    ES6Class
  }

  /** The declaration style used for the Polymer definition */
  final DefinitionType defType;

  /** The Polymer call or class node which defines the Element. */
  final Node definition;

  final CompilerInput input;

  /** The target node (LHS) for the Polymer element definition. */
  final Node target;

  /** Whether the target of this element is a generated node */
  final boolean hasGeneratedLhs;

  /** The object literal passed to the call to the Polymer() function. */
  final Node descriptor;

  /** The constructor function for the element. */
  final MemberDefinition constructor;

  /** The name of the native HTML element which this element extends. */
  final @Nullable String nativeBaseElement;

  /** Properties declared in the Polymer "properties" block. */
  final List<MemberDefinition> props;

  /** Properties declared in the Polymer behavior's "properties" block. */
  final Map<MemberDefinition, BehaviorDefinition> behaviorProps;

  /** Methods on the element. */
  final @Nullable List<MemberDefinition> methods;

  /** Flattened list of behavior definitions used by this element. */
  final @Nullable ImmutableList<BehaviorDefinition> behaviors;

  /** Language features that should be carried over to the extraction destination. */
  final @Nullable FeatureSet features;

  private @Nullable String interfaceName = null;

  PolymerClassDefinition(
      DefinitionType defType,
      Node definition,
      Node target,
      boolean hasGeneratedLhs,
      Node descriptor,
      JSDocInfo classInfo,
      MemberDefinition constructor,
      @Nullable String nativeBaseElement,
      List<MemberDefinition> props,
      @Nullable Map<MemberDefinition, BehaviorDefinition> behaviorProps,
      List<MemberDefinition> methods,
      @Nullable ImmutableList<BehaviorDefinition> behaviors,
      @Nullable FeatureSet features,
      CompilerInput input) {
    this.defType = defType;
    this.definition = definition;
    this.target = target;
    this.hasGeneratedLhs = hasGeneratedLhs;
    checkState(descriptor == null || descriptor.isObjectLit());
    this.descriptor = descriptor;
    this.constructor = constructor;
    this.nativeBaseElement = nativeBaseElement;
    this.props = props;
    this.behaviorProps = behaviorProps;
    this.methods = methods;
    this.behaviors = behaviors;
    this.features = features;
    this.input = input;
  }

  /**
   * Validates the class definition and if valid, destructively extracts the class definition from
   * the AST.
   */
  static @Nullable PolymerClassDefinition extractFromCallNode(
      Node callNode,
      AbstractCompiler compiler,
      ModuleMetadata moduleMetadata,
      PolymerBehaviorExtractor behaviorExtractor) {
    Node descriptor = NodeUtil.getArgumentForCallOrNew(callNode, 0);
    if (descriptor == null || !descriptor.isObjectLit()) {
      // report bad class definition
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID));
      return null;
    }

    int paramCount = callNode.getChildCount() - 1;
    if (paramCount != 1) {
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_UNEXPECTED_PARAMS));
      return null;
    }

    Node elName = NodeUtil.getFirstPropMatchingKey(descriptor, "is");
    if (elName == null) {
      compiler.report(JSError.make(callNode, PolymerPassErrors.POLYMER_MISSING_IS));
      return null;
    }
    Node enclosingModule = NodeUtil.getEnclosingModuleIfPresent(callNode);

    boolean hasGeneratedLhs = false;
    Node target;
    if (NodeUtil.isNameDeclaration(callNode.getGrandparent())) {
      target = IR.name(callNode.getParent().getString());
    } else if (callNode.getParent().isAssign()) {
      if (isGoogModuleExports(callNode.getParent())) {
        // `exports = Polymer({` in a goog.module requires special handling, as adding a
        // duplicate assignment to exports just confuses the compiler. Create a dummy declaration
        // var exportsForPolymer$jscomp0 = Polymer({ // ...
        target = createDummyGoogModuleExportsTarget(compiler, callNode);
      } else {
        target = callNode.getParent().getFirstChild().cloneTree();
      }
    } else {
      String elNameStringBase =
          elName.isQualifiedName()
              ? elName.getQualifiedName().replace('.', '$')
              : elName.getString();
      String elNameString = CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, elNameStringBase);
      elNameString += "Element";
      target = IR.name(elNameString);
      hasGeneratedLhs = true;
    }

    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(target);

    JSDocInfo ctorInfo = null;
    Node constructor = NodeUtil.getFirstPropMatchingKey(descriptor, "factoryImpl");
    if (constructor == null) {
      constructor = NodeUtil.emptyFunction();
      compiler.reportChangeToChangeScope(constructor);
      constructor.srcrefTree(callNode);
    } else {
      ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
    }

    Node baseClass = NodeUtil.getFirstPropMatchingKey(descriptor, "extends");
    String nativeBaseElement = baseClass == null ? null : baseClass.getString();

    Node behaviorArray = NodeUtil.getFirstPropMatchingKey(descriptor, "behaviors");
    ImmutableList<BehaviorDefinition> behaviors =
        behaviorExtractor.extractBehaviors(behaviorArray, moduleMetadata);
    List<MemberDefinition> properties = new ArrayList<>();
    Map<MemberDefinition, BehaviorDefinition> behaviorProps = new LinkedHashMap<>();
    for (BehaviorDefinition behavior : behaviors) {
      for (MemberDefinition prop : behavior.props) {
        behaviorProps.put(prop, behavior);
      }
    }
    overwriteMembersIfPresent(
        properties,
        PolymerPassStaticUtils.extractProperties(
            descriptor, DefinitionType.ObjectLiteral, compiler, /* constructor= */ null));

    // Behaviors might get included multiple times for the same element. See test case
    // testDuplicatedBehaviorsAreCopiedOnce
    // In those cases, behaviorProps will have repeated names. We must remove those duplicates.
    removeDuplicateBehaviorProps(behaviorProps);

    // Remove behavior properties which are already present in element properties
    removeBehaviorPropsOverlappingWithElementProps(behaviorProps, properties);

    FeatureSet newFeatures = null;
    if (!behaviors.isEmpty()) {
      newFeatures = behaviors.get(0).features;
      for (int i = 1; i < behaviors.size(); i++) {
        newFeatures = newFeatures.union(behaviors.get(i).features);
      }
    }

    List<MemberDefinition> methods = new ArrayList<>();
    for (Node keyNode = descriptor.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
      boolean isFunctionDefinition =
          keyNode.isMemberFunctionDef()
              || (keyNode.isStringKey() && keyNode.getFirstChild().isFunction());
      if (isFunctionDefinition) {
        methods.add(
            new MemberDefinition(
                NodeUtil.getBestJSDocInfo(keyNode),
                keyNode,
                keyNode.getFirstChild(),
                enclosingModule));
      }
    }
    CompilerInput input = compiler.getInput(NodeUtil.getEnclosingScript(callNode).getInputId());

    return new PolymerClassDefinition(
        DefinitionType.ObjectLiteral,
        callNode,
        target,
        hasGeneratedLhs,
        descriptor,
        classInfo,
        new MemberDefinition(ctorInfo, null, constructor, enclosingModule),
        nativeBaseElement,
        properties,
        behaviorProps,
        methods,
        behaviors,
        newFeatures,
        input);
  }

  private static boolean isGoogModuleExports(Node assign) {
    // Verify this is an assignment to a name `exports`
    if (!assign.getParent().isExprResult() || !assign.getFirstChild().matchesName("exports")) {
      return false;
    }
    // Verify the assignment is within either a goog.module or goog.loadModule
    Node containingBlock = assign.getGrandparent();
    return (containingBlock.isModuleBody()
            && containingBlock.getParent().getBooleanProp(Node.GOOG_MODULE))
        || NodeUtil.isBundledGoogModuleScopeRoot(containingBlock);
  }

  /**
   * Adding our usual multiple assignments <code>
   *
   *   /** @constructor @extends {PolymerElement} * /
   *   var FooElement = function() {};
   *   FooElement = Polymer() {
   *
   *   }
   *   </code> confuses goog.module rewriting when the Polymer call is assigned to `exports. Instead
   * create a new module local, and export that name.
   *
   * @param callNode a Polymer({}) call
   * @return The new target node for the Polymer call
   */
  private static Node createDummyGoogModuleExportsTarget(AbstractCompiler compiler, Node callNode) {
    String madeUpName = "exportsForPolymer$jscomp" + compiler.getUniqueNameIdSupplier().get();
    Node assignExpr = callNode.getGrandparent();

    Node exportName = callNode.getParent().getFirstChild();
    Node target = IR.name(madeUpName).clonePropsFrom(exportName).srcref(exportName);
    callNode.replaceWith(target);
    Node newDecl = IR.var(target.cloneNode(), callNode).srcref(assignExpr);
    newDecl.insertBefore(assignExpr);
    newDecl.setJSDocInfo(assignExpr.getJSDocInfo());
    assignExpr.setJSDocInfo(null);
    return target;
  }

  /**
   * Validates the class definition and if valid, extracts the class definition from the AST. As
   * opposed to the Polymer 1 extraction, this operation is non-destructive.
   */
  static @Nullable PolymerClassDefinition extractFromClassNode(
      Node classNode, AbstractCompiler compiler) {
    checkState(classNode != null && classNode.isClass());

    // The supported case is for the config getter to return an object literal descriptor.
    Node propertiesDescriptor = null;
    Node propertiesGetter =
        NodeUtil.getFirstGetterMatchingKey(NodeUtil.getClassMembers(classNode), "properties");
    if (propertiesGetter != null) {
      if (!propertiesGetter.isStaticMember()) {
        // report bad class definition
        compiler.report(
            JSError.make(classNode, PolymerPassErrors.POLYMER_CLASS_PROPERTIES_NOT_STATIC));
      } else {
        for (Node child =
                NodeUtil.getFunctionBody(propertiesGetter.getFirstChild()).getFirstChild();
            child != null;
            child = child.getNext()) {
          if (child.isReturn()) {
            if (child.hasChildren() && child.getFirstChild().isObjectLit()) {
              propertiesDescriptor = child.getFirstChild();
              break;
            } else {
              compiler.report(
                  JSError.make(
                      propertiesGetter, PolymerPassErrors.POLYMER_CLASS_PROPERTIES_INVALID));
            }
          }
        }
      }
    }

    Node target;
    if (NodeUtil.isNameDeclaration(classNode.getGrandparent())) {
      target = IR.name(classNode.getParent().getString());
    } else if (classNode.getParent().isAssign()
        && classNode.getParent().getFirstChild().isQualifiedName()) {
      target = classNode.getParent().getFirstChild();
    } else if (!classNode.getFirstChild().isEmpty()) {
      target = classNode.getFirstChild();
    } else {
      // issue error - no name found
      compiler.report(JSError.make(classNode, PolymerPassErrors.POLYMER_CLASS_UNNAMED));
      return null;
    }
    Node enclosingModule = NodeUtil.getEnclosingModuleIfPresent(classNode);

    JSDocInfo classInfo = NodeUtil.getBestJSDocInfo(classNode);

    JSDocInfo ctorInfo = null;
    Node constructor = NodeUtil.getEs6ClassConstructorMemberFunctionDef(classNode);
    if (constructor != null) {
      ctorInfo = NodeUtil.getBestJSDocInfo(constructor);
    }

    ImmutableList<MemberDefinition> properties =
        PolymerPassStaticUtils.extractProperties(
            propertiesDescriptor, DefinitionType.ES6Class, compiler, constructor);

    List<MemberDefinition> methods = new ArrayList<>();
    for (Node keyNode = NodeUtil.getClassMembers(classNode).getFirstChild();
        keyNode != null;
        keyNode = keyNode.getNext()) {
      if (!keyNode.isMemberFunctionDef()) {
        continue;
      }
      methods.add(
          new MemberDefinition(
              NodeUtil.getBestJSDocInfo(keyNode),
              keyNode,
              keyNode.getFirstChild(),
              enclosingModule));
    }
    CompilerInput input = compiler.getInput(NodeUtil.getEnclosingScript(classNode).getInputId());

    return new PolymerClassDefinition(
        DefinitionType.ES6Class,
        classNode,
        target,
        /* hasGeneratedLhs= */ false,
        propertiesDescriptor,
        classInfo,
        new MemberDefinition(ctorInfo, null, constructor, enclosingModule),
        null,
        properties,
        null,
        methods,
        null,
        null,
        input);
  }

  /**
   * Appends a list of new MemberDefinitions to the end of a list and removes any previous
   * MemberDefinition in the list which has the same name as the new member.
   */
  private static void overwriteMembersIfPresent(
      List<MemberDefinition> list, List<MemberDefinition> newMembers) {
    for (MemberDefinition newMember : newMembers) {
      for (MemberDefinition member : list) {
        if (member.name.getString().equals(newMember.name.getString())) {
          list.remove(member);
          break;
        }
      }
      list.add(newMember);
    }
  }

  /**
   * Removes duplicate properties from the given map, keeping only the first property
   *
   * <p>Duplicates occur when either the same behavior is transitively added multiple times to
   * Polymer element, or when two unique added behaviors share a property with the same name.
   */
  private static void removeDuplicateBehaviorProps(
      Map<MemberDefinition, BehaviorDefinition> behaviorProps) {
    if (behaviorProps == null) {
      return;
    }
    Iterator<Map.Entry<MemberDefinition, BehaviorDefinition>> behaviorsItr =
        behaviorProps.entrySet().iterator();
    Set<String> seen = new LinkedHashSet<>();
    while (behaviorsItr.hasNext()) {
      MemberDefinition memberDefinition = behaviorsItr.next().getKey();
      String propertyName = memberDefinition.name.getString();
      if (!seen.add(propertyName)) {
        behaviorsItr.remove();
      }
    }
  }

  /**
   * Removes any behavior properties from the given map that have the same name as a property in the
   * given list.
   *
   * <p>For example, if a Polymer element with a property "name" depends on a behavior with a
   * property "name", then this method removes the behavior property in favor of the element
   * property.
   */
  private static void removeBehaviorPropsOverlappingWithElementProps(
      Map<MemberDefinition, BehaviorDefinition> behaviorProps,
      List<MemberDefinition> polymerElementProps) {
    if (behaviorProps == null) {
      return;
    }
    Set<String> elementPropNames =
        polymerElementProps.stream().map(x -> x.name.getString()).collect(Collectors.toSet());
    Iterator<Map.Entry<MemberDefinition, BehaviorDefinition>> behaviorsItr =
        behaviorProps.entrySet().iterator();
    while (behaviorsItr.hasNext()) {
      MemberDefinition memberDefinition = behaviorsItr.next().getKey();
      if (elementPropNames.contains(memberDefinition.name.getString())) {
        behaviorsItr.remove();
      }
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("defType", defType)
        .add("definition", definition)
        .add("target", target)
        .add("nativeBaseElement", nativeBaseElement)
        .omitNullValues()
        .toString();
  }

  String getInterfaceName(UniqueIdSupplier uniqueIdSupplier) {
    if (interfaceName == null) {
      interfaceName =
          "Polymer"
              + target.getQualifiedName().replace('.', '_')
              + "Interface$"
              + uniqueIdSupplier.getUniqueId(input);
    }
    return interfaceName;
  }
}
