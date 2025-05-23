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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.javascript.jscomp.TypeCheck.BAD_IMPLEMENTED_TYPE;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;
import static com.google.javascript.rhino.jstype.JSTypeNative.ASYNC_GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GENERATOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.PROMISE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.ClosurePrimitive;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.TemplateType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A builder for FunctionTypes, because FunctionTypes are so ridiculously complex. All methods
 * return {@code this} for ease of use.
 *
 * <p>Right now, this mostly uses JSDocInfo to infer type information about functions. In the long
 * term, developers should extend it to use other signals by overloading the various "inferXXX"
 * methods. For example, we might want to use {@code goog.inherits} calls as a signal for
 * inheritance, or {@code return} statements as a signal for return type.
 *
 * <p>NOTE(nicksantos): Organizationally, this feels like it should be in Rhino. But it depends on
 * some coding convention stuff that's really part of JSCompiler.
 */
final class FunctionTypeBuilder {

  private final String fnName;
  private final AbstractCompiler compiler;
  private final CodingConvention codingConvention;
  private final JSTypeRegistry typeRegistry;
  private final Node errorRoot;

  private FunctionContents contents = UnknownFunctionContents.get();

  private String syntacticFnName;
  private @Nullable JSType returnType = null;
  private boolean returnTypeInferred = false;
  private @Nullable List<ObjectType> implementedInterfaces = null;
  private @Nullable List<ObjectType> extendedInterfaces = null;
  private @Nullable ObjectType baseType = null;
  private @Nullable JSType thisType = null;
  private boolean isClass = false;
  private boolean isConstructor = false;
  private boolean makesStructs = false;
  private boolean makesUnrestricted = false;
  private boolean makesDicts = false;
  private boolean isInterface = false;
  private boolean isRecord = false;
  private boolean isAbstract = false;
  private boolean isKnownAmbiguous = false;
  private @Nullable ImmutableList<Parameter> parameters = null;
  private @Nullable ClosurePrimitive closurePrimitiveId = null;
  private ImmutableList<TemplateType> templateTypeNames = ImmutableList.of();
  private ImmutableList<TemplateType> constructorTemplateTypeNames = ImmutableList.of();
  private @Nullable TypedScope declarationScope = null;
  private StaticTypedScope templateScope;

  static final DiagnosticType EXTENDS_WITHOUT_TYPEDEF =
      DiagnosticType.warning(
          "JSC_EXTENDS_WITHOUT_TYPEDEF",
          "@extends used without @constructor or @interface for {0}");

  static final DiagnosticType EXTENDS_NON_OBJECT =
      DiagnosticType.warning("JSC_EXTENDS_NON_OBJECT", "{0} @extends non-object type {1}");

  static final DiagnosticType RESOLVED_TAG_EMPTY =
      DiagnosticType.warning("JSC_RESOLVED_TAG_EMPTY", "Could not resolve type in {0} tag of {1}");

  static final DiagnosticType CONSTRUCTOR_REQUIRED =
      DiagnosticType.warning("JSC_CONSTRUCTOR_REQUIRED", "{0} used without @constructor for {1}");

  static final DiagnosticType VAR_ARGS_MUST_BE_LAST =
      DiagnosticType.warning("JSC_VAR_ARGS_MUST_BE_LAST", "variable length argument must be last");

  static final DiagnosticType OPTIONAL_ARG_AT_END =
      DiagnosticType.warning("JSC_OPTIONAL_ARG_AT_END", "optional arguments must be at the end");

  static final DiagnosticType INEXISTENT_PARAM =
      DiagnosticType.warning(
          "JSC_INEXISTENT_PARAM", "parameter {0} does not appear in {1}''s parameter list");

  static final DiagnosticType TYPE_REDEFINITION =
      DiagnosticType.warning(
          "JSC_TYPE_REDEFINITION",
          """
          attempted re-definition of type {0}
          found   : {1}
          expected: {2}\
          """);

  static final DiagnosticType TEMPLATE_TRANSFORMATION_ON_CLASS =
      DiagnosticType.warning(
          "JSC_TEMPLATE_TRANSFORMATION_ON_CLASS",
          "Template type transformation {0} not allowed on classes or interfaces");

  static final DiagnosticType TEMPLATE_TYPE_ILLEGAL_BOUND =
      DiagnosticType.error(
          "JSC_TEMPLATE_TYPE_ILLEGAL_BOUND",
          "Illegal upper bound ''{0}'' on template type parameter {1}");

  static final DiagnosticGroup ALL_DIAGNOSTICS =
      new DiagnosticGroup(
          EXTENDS_WITHOUT_TYPEDEF,
          EXTENDS_NON_OBJECT,
          RESOLVED_TAG_EMPTY,
          CONSTRUCTOR_REQUIRED,
          VAR_ARGS_MUST_BE_LAST,
          OPTIONAL_ARG_AT_END,
          INEXISTENT_PARAM,
          TYPE_REDEFINITION,
          TEMPLATE_TRANSFORMATION_ON_CLASS,
          TEMPLATE_TYPE_ILLEGAL_BOUND,
          TypeCheck.SAME_INTERFACE_MULTIPLE_IMPLEMENTS);

  private abstract static class ValidatorBase implements Predicate<JSType> {
    private final AbstractCompiler compiler;
    private final Node errorRoot;

    ValidatorBase(Node errorRoot, AbstractCompiler compiler) {
      this.errorRoot = errorRoot;
      this.compiler = compiler;
    }

    void reportWarning(DiagnosticType warning, String... args) {
      compiler.report(JSError.make(errorRoot, warning, args));
    }

    void reportError(DiagnosticType error, String... args) {
      compiler.report(JSError.make(errorRoot, error, args));
    }
  }

  private ExtendedTypeValidator createExtendedTypeValidator() {
    return new ExtendedTypeValidator(errorRoot, compiler, formatFnName());
  }

  private static class ExtendedTypeValidator extends ValidatorBase {
    private final String formattedFnName;

    ExtendedTypeValidator(Node errorRoot, AbstractCompiler compiler, String formattedFnName) {
      super(errorRoot, compiler);
      this.formattedFnName = formattedFnName;
    }

    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportWarning(EXTENDS_NON_OBJECT, formattedFnName, type.toString());
        return false;
      }
      if (objectType.isEmptyType()) {
        reportWarning(RESOLVED_TAG_EMPTY, "@extends", formattedFnName);
        return false;
      }
      if (objectType.isUnknownType()) {
        if (hasMoreTagsToResolve(objectType) || type.isTemplateType()) {
          return true;
        } else {
          reportWarning(RESOLVED_TAG_EMPTY, "@extends", formattedFnName);
          return false;
        }
      }
      return true;
    }
  }

  private ImplementedTypeValidator createImplementedTypeValidator() {
    return new ImplementedTypeValidator(errorRoot, compiler, formatFnName());
  }

  private static class ImplementedTypeValidator extends ValidatorBase {
    private final String formattedFnName;

    ImplementedTypeValidator(Node errorRoot, AbstractCompiler compiler, String formattedFnName) {
      super(errorRoot, compiler);
      this.formattedFnName = formattedFnName;
    }

    @Override
    public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if (objectType == null) {
        reportError(BAD_IMPLEMENTED_TYPE, formattedFnName);
        return false;
      } else if (objectType.isEmptyType()) {
        reportWarning(RESOLVED_TAG_EMPTY, "@implements", formattedFnName);
        return false;
      } else if (objectType.isUnknownType()) {
        if (hasMoreTagsToResolve(objectType)) {
          return true;
        } else {
          reportWarning(RESOLVED_TAG_EMPTY, "@implements", formattedFnName);
          return false;
        }
      } else {
        return true;
      }
    }
  }

  /**
   * @param fnName The function name to be used in error messages.
   * @param compiler The compiler.
   * @param errorRoot The node to associate with any warning generated by this builder.
   * @param scope The syntactic scope.
   */
  FunctionTypeBuilder(String fnName, AbstractCompiler compiler, Node errorRoot, TypedScope scope) {
    checkNotNull(errorRoot);
    this.fnName = nullToEmpty(fnName);
    this.codingConvention = compiler.getCodingConvention();
    this.typeRegistry = compiler.getTypeRegistry();
    this.errorRoot = errorRoot;
    this.compiler = compiler;
    this.templateScope = scope;
  }

  /** Format the function name for use in warnings. */
  String formatFnName() {
    return fnName.isEmpty() ? "<anonymous>" : fnName;
  }

  /** Sets the name with which this new type will be declared in the type registry. */
  @CanIgnoreReturnValue
  FunctionTypeBuilder setSyntacticFunctionName(String syntacticFnName) {
    this.syntacticFnName = nullToEmpty(syntacticFnName);
    return this;
  }

  /** Sets the contents of this function. */
  @CanIgnoreReturnValue
  FunctionTypeBuilder setContents(@Nullable FunctionContents contents) {
    if (contents != null) {
      this.contents = contents;
    }
    return this;
  }

  /**
   * Sets a declaration scope explicitly. This is important with block scopes because a function
   * declared in an inner scope with 'var' needs to use the inner scope to resolve names, but needs
   * to be declared in the outer scope.
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder setDeclarationScope(TypedScope declarationScope) {
    this.declarationScope = declarationScope;
    return this;
  }

  /**
   * Infer the parameter and return types of a function from the parameter and return types of the
   * function it is overriding.
   *
   * @param oldType The function being overridden. Does nothing if this is null.
   * @param paramsParent The PARAM_LIST node of the function that we're assigning to. If null, that
   *     just means we're not initializing this to a function literal.
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferFromOverriddenFunction(
      @Nullable FunctionType oldType, @Nullable Node paramsParent) {
    if (oldType == null) {
      return this;
    }

    // Propagate the template types, if they exist.
    this.templateTypeNames = oldType.getTemplateTypeMap().getTemplateKeys();

    returnType = oldType.getReturnType();
    returnTypeInferred = oldType.isReturnTypeInferred();
    if (paramsParent == null) {
      // Not a function literal.
      parameters = oldType.getParameters();
      if (parameters == null) {
        parameters = new FunctionParamBuilder(typeRegistry).build();
      }
    } else {
      // We're overriding with a function literal. Apply type information
      // to each parameter of the literal.
      FunctionParamBuilder paramBuilder = new FunctionParamBuilder(typeRegistry);
      Iterator<Parameter> oldParams = oldType.getParameters().iterator();
      boolean warnedAboutArgList = false;
      boolean oldParamsListHitOptArgs = false;
      for (Node currentParam = paramsParent.getFirstChild();
          currentParam != null;
          currentParam = currentParam.getNext()) {
        if (oldParams.hasNext()) {
          Parameter oldParam = oldParams.next();

          oldParamsListHitOptArgs =
              oldParamsListHitOptArgs || oldParam.isVariadic() || oldParam.isOptional();

          // The subclass method might write its var_args as individual arguments.

          boolean isOptionalArg = oldParam.isOptional();
          boolean isVarArgs = oldParam.isVariadic();
          if (currentParam.getNext() != null && isVarArgs) {
            isVarArgs = false;
            isOptionalArg = true;
          }
          // The subclass method might also make a required parameter into an optional parameter
          // with a default value
          if (currentParam.isDefaultValue()) {
            isOptionalArg = true;
          }
          paramBuilder.newParameterFrom(
              Parameter.create(oldParam.getJSType(), isOptionalArg, isVarArgs));
        } else {
          warnedAboutArgList |=
              addParameter(
                  paramBuilder,
                  typeRegistry.getNativeType(UNKNOWN_TYPE),
                  warnedAboutArgList,
                  codingConvention.isOptionalParameter(currentParam)
                      || oldParamsListHitOptArgs
                      || currentParam.isDefaultValue(),
                  codingConvention.isVarArgsParameter(currentParam));
        }
      }

      // Clone any remaining params that aren't in the function literal,
      // but make them optional.
      while (oldParams.hasNext()) {
        paramBuilder.newOptionalParameterFrom(oldParams.next());
      }

      parameters = paramBuilder.build();
    }
    return this;
  }

  /**
   * Infer the return type from JSDocInfo.
   *
   * @param fromInlineDoc Indicates whether return type is inferred from inline doc attached to
   *     function name
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferReturnType(@Nullable JSDocInfo info, boolean fromInlineDoc) {
    if (info != null) {
      JSTypeExpression returnTypeExpr = fromInlineDoc ? info.getType() : info.getReturnType();
      if (returnTypeExpr != null) {
        returnType = returnTypeExpr.evaluate(templateScope, typeRegistry);
        returnTypeInferred = false;
      }
    }

    return this;
  }

  @CanIgnoreReturnValue
  FunctionTypeBuilder usingClassSyntax() {
    this.isClass = true;
    return this;
  }

  /** Infer whether the function is a normal function, a constructor, or an interface. */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferKind(@Nullable JSDocInfo info) {
    if (info != null) {
      if (!NodeUtil.isMethodDeclaration(errorRoot)) {
        isConstructor = info.isConstructor();
        isInterface = info.isInterface();
        isRecord = info.usesImplicitMatch();
        makesStructs = info.makesStructs();
        makesUnrestricted = info.makesUnrestricted();
        makesDicts = info.makesDicts();
      }
      isAbstract = info.isAbstract();
    }
    if (isClass) {
      // If a CLASS literal has not been explicitly declared an interface, it's a constructor.
      // If it's not expicitly @dict or @unrestricted then it's @struct.
      isConstructor = !isInterface;
      makesStructs = info == null || (!makesDicts && !info.makesUnrestricted());
    }

    if (makesStructs && !(isConstructor || isInterface)) {
      reportWarning(CONSTRUCTOR_REQUIRED, "@struct", formatFnName());
    } else if (makesDicts && !isConstructor) {
      reportWarning(CONSTRUCTOR_REQUIRED, "@dict", formatFnName());
    }
    return this;
  }

  /** Clobber the templateTypeNames from the JSDoc with builtin ones for native types. */
  private boolean maybeUseNativeClassTemplateNames(JSDocInfo info) {
    ImmutableList<TemplateType> nativeKeys =
        typeRegistry.maybeGetTemplateTypesOfBuiltin(declarationScope, fnName);
    // TODO(b/73386087): Make infoTemplateTypeNames.size() == nativeKeys.size() a
    // Preconditions check. It currently fails for "var symbol" in the externs.
    if (nativeKeys != null && info.getTemplateTypeNames().size() == nativeKeys.size()) {
      this.templateTypeNames = nativeKeys;
      return true;
    }
    return false;
  }

  /**
   * Infer any supertypes from the JSDocInfo or the passed-in base type.
   *
   * @param info JSDoc info that is attached to the type declaration, if any
   * @param classExtendsType The type of the extends clause in `class C extends SuperClass {}`, if
   *     present.
   * @return this object
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferInheritance(
      @Nullable JSDocInfo info, @Nullable ObjectType classExtendsType) {

    if (info != null && info.hasBaseType()) {
      if (isConstructor || isInterface) {
        JSType infoBaseType = info.getBaseType().evaluate(templateScope, typeRegistry);
        if (!areCompatibleExtendsTypes(infoBaseType.toMaybeObjectType(), classExtendsType)) {
          this.isKnownAmbiguous = true;
        }
        if (infoBaseType.setValidator(createExtendedTypeValidator())) {
          baseType = infoBaseType.toObjectType();
        } else {
          this.isKnownAmbiguous = true;
        }
      } else {
        reportWarning(EXTENDS_WITHOUT_TYPEDEF, formatFnName());
        this.isKnownAmbiguous = true;
      }
    } else if (classExtendsType != null && (isConstructor || isInterface)) {
      // This case is:
      // // no JSDoc here
      // class extends astBaseType {...}
      //
      // It may well be that astBaseType is something dynamically created, like a value passed into
      // a function. A common pattern is:
      //
      // function mixinX(superClass) {
      //   return class extends superClass {
      //     ...
      //   };
      // }
      // The ExtendedTypeValidator() used in the JSDocInfo case above will report errors for these
      // cases, and we don't want that.
      // Since astBaseType is an actual value in code rather than an annotation, we can
      // rely on validation elsewhere to ensure it is actually defined.
      baseType = classExtendsType;
    }

    // Implemented interfaces (for constructors only).
    if (info != null && info.getImplementedInterfaceCount() > 0) {
      if (isConstructor) {
        implementedInterfaces = new ArrayList<>();
        for (JSTypeExpression t : info.getImplementedInterfaces()) {
          JSType maybeInterType = t.evaluate(templateScope, typeRegistry);

          if (maybeInterType.setValidator(createImplementedTypeValidator())) {
            implementedInterfaces.add((ObjectType) maybeInterType);
          }
        }
      } else if (isInterface) {
        reportWarning(TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, formatFnName());
      } else {
        reportWarning(CONSTRUCTOR_REQUIRED, "@implements", formatFnName());
      }
    }

    // extended interfaces (for interfaces only)
    // We've already emitted a warning if this is not an interface.
    if (isInterface) {
      extendedInterfaces = new ArrayList<>();
      if (info != null) {
        for (JSTypeExpression t : info.getExtendedInterfaces()) {
          JSType maybeInterfaceType = t.evaluate(templateScope, typeRegistry);
          if (maybeInterfaceType != null) {
            // setValidator runs validation and returns whether validation was successful (except
            // for not-yet resolved named types, where validation is delayed).
            // This code must run even for non-object types (which we know are invalid) to generate
            // and record the user error message.
            boolean isValid = maybeInterfaceType.setValidator(createExtendedTypeValidator());
            // ExtendedTypeValidator guarantees that maybeInterfaceType is an object type, but
            // setValidator might not (e.g. due to delayed execution).
            if (isValid && maybeInterfaceType.toMaybeObjectType() != null) {
              extendedInterfaces.add(maybeInterfaceType.toMaybeObjectType());
            }
          }
          // de-dupe baseType (from extends keyword) if it's also in @extends jsdoc.
          if (classExtendsType != null && maybeInterfaceType.isSubtypeOf(classExtendsType)) {
            classExtendsType = null;
          }
        }
      }
      if (classExtendsType != null
          && classExtendsType.setValidator(createExtendedTypeValidator())) {
        // case is:
        // /**
        //  * @interface
        //  * @extends {OtherInterface}
        //  */
        // class SomeInterface extends astBaseType {}
        // Add the explicit extends type to the extended interfaces listed in JSDoc.
        extendedInterfaces.add(classExtendsType);
      }
    }

    return this;
  }

  /**
   * Decide if the types in the @extends annotation and the extends clause of a class are a
   * "sensible" combination.
   *
   * <p>Sensible is vague here, depending on how dynamic we allow types to be. It's not just a
   * supertype/subtype check. Generally, we want to trust user annotations as declarations of
   * intent, but we also want to protect users from dangerous lies. UNKNOWN as one of the types is a
   * particularly uncertain case.
   */
  private static boolean areCompatibleExtendsTypes(
      ObjectType annotated, @Nullable ObjectType extendsClause) {
    if (extendsClause == null || identical(annotated, extendsClause)) {
      return true;
    }

    // Allow `/** @extends {Foo<T>} */ class Bar extends Foo`
    return annotated.isTemplatizedType()
        && identical(annotated.toMaybeTemplatizedType().getReferencedType(), extendsClause);
  }

  /**
   * Infers the type of {@code this}.
   *
   * @param type The type of this if the info is missing.
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferThisType(JSDocInfo info, JSType type) {
    // Look at the @this annotation first.
    inferThisType(info);

    if (thisType == null) {
      ObjectType objType = ObjectType.cast(type);
      if (objType != null && (info == null || !info.hasType())) {
        thisType = objType;
      }
    }

    return this;
  }

  /**
   * Infers the type of {@code this}.
   *
   * @param info The JSDocInfo for this function.
   */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferThisType(JSDocInfo info) {
    if (info != null && info.hasThisType()) {
      // TODO(johnlenz): In ES5 strict mode a function can have a null or
      // undefined "this" value, but all the existing "@this" annotations
      // don't declare restricted types.
      JSType maybeThisType =
          info.getThisType().evaluate(templateScope, typeRegistry).restrictByNotNullOrUndefined();
      if (maybeThisType != null) {
        thisType = maybeThisType;
      }
    }

    return this;
  }

  /** Infer the parameter types from the doc info alone. */
  FunctionTypeBuilder inferParameterTypes(JSDocInfo info) {
    // Create a fake args parent.
    Node lp = IR.paramList();
    for (String name : info.getParameterNames()) {
      lp.addChildToBack(IR.name(name));
    }

    return inferParameterTypes(lp, info);
  }

  /** Infer the parameter types from the list of parameter names and the JSDoc info. */
  FunctionTypeBuilder inferParameterTypes(@Nullable Node paramsParent, @Nullable JSDocInfo info) {
    if (paramsParent == null) {
      if (info == null) {
        return this;
      } else {
        return inferParameterTypes(info);
      }
    }

    // arguments
    final Iterator<Parameter> oldParameters;
    Parameter oldParameterType = null;
    if (parameters != null) {
      oldParameters = parameters.iterator();
      oldParameterType = oldParameters.hasNext() ? oldParameters.next() : null;
    } else {
      oldParameters = Collections.emptyIterator();
    }

    FunctionParamBuilder builder = new FunctionParamBuilder(typeRegistry);
    boolean warnedAboutArgList = false;
    Set<String> allJsDocParams =
        (info == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(info.getParameterNames());
    boolean isVarArgs = false;
    int paramIndex = 0;
    for (Node param = paramsParent.getFirstChild(); param != null; param = param.getNext()) {
      boolean isOptionalParam = false;
      final Node paramLhs;

      if (param.isRest()) {
        isVarArgs = true;
        paramLhs = param.getOnlyChild();
      } else if (param.isDefaultValue()) {
        // The first child is the actual positional parameter
        paramLhs = checkNotNull(param.getFirstChild(), param);
        isOptionalParam = true;
      } else {
        isVarArgs = isVarArgsParameterByConvention(param);
        isOptionalParam = isOptionalParameterByConvention(param);
        paramLhs = param;
      }

      String paramName = null;
      if (paramLhs.isName()) {
        paramName = paramLhs.getString();
      } else {
        checkState(paramLhs.isDestructuringPattern());
        // Right now, the only way to match a JSDoc param to a destructuring parameter is through
        // ordering the JSDoc parameters. So the third formal parameter will correspond to the
        // third JSDoc parameter.
        if (info != null) {
          paramName = info.getParameterNameAt(paramIndex);
        }
      }
      allJsDocParams.remove(paramName);

      // type from JSDocInfo
      JSType parameterType = null;
      if (info != null && info.hasParameterType(paramName)) {
        JSTypeExpression parameterTypeExpression = info.getParameterType(paramName);
        parameterType = parameterTypeExpression.evaluate(templateScope, typeRegistry);
        isOptionalParam = isOptionalParam || parameterTypeExpression.isOptionalArg();
        isVarArgs = isVarArgs || parameterTypeExpression.isVarArgs();
      } else if (paramLhs.getJSDocInfo() != null && paramLhs.getJSDocInfo().hasType()) {
        JSTypeExpression parameterTypeExpression = paramLhs.getJSDocInfo().getType();
        parameterType = parameterTypeExpression.evaluate(templateScope, typeRegistry);
        isOptionalParam = parameterTypeExpression.isOptionalArg();
        isVarArgs = parameterTypeExpression.isVarArgs();
      } else if (oldParameterType != null && oldParameterType.getJSType() != null) {
        parameterType = oldParameterType.getJSType();
        isOptionalParam = oldParameterType.isOptional();
        isVarArgs = oldParameterType.isVariadic();
      } else {
        parameterType = typeRegistry.getNativeType(UNKNOWN_TYPE);
      }

      warnedAboutArgList |=
          addParameter(builder, parameterType, warnedAboutArgList, isOptionalParam, isVarArgs);

      oldParameterType = oldParameters.hasNext() ? oldParameters.next() : null;
      paramIndex++;
    }
    // Copy over any old parameters that aren't in the param list.
    if (!isVarArgs) {
      while (oldParameterType != null && !isVarArgs) {
        builder.newParameterFrom(oldParameterType);
        oldParameterType = oldParameters.hasNext() ? oldParameters.next() : null;
      }
    }

    for (String inexistentName : allJsDocParams) {
      reportWarning(INEXISTENT_PARAM, inexistentName, formatFnName());
    }

    parameters = builder.build();
    return this;
  }

  /** Register the template keys in a template scope and on the function node. */
  private void registerTemplates(Iterable<TemplateType> templates, @Nullable Node scopeRoot) {
    if (!Iterables.isEmpty(templates)) {
      // Add any templates from JSDoc into our template scope.
      this.templateScope = typeRegistry.createScopeWithTemplates(templateScope, templates);
      // Register the template types on the scope root node, if there is one.
      if (scopeRoot != null) {
        typeRegistry.registerTemplateTypeNamesInScope(templates, scopeRoot);
      }
    }
  }

  /** Infer parameters from the params list and info. Also maybe add extra templates. */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferConstructorParameters(Node argsParent, @Nullable JSDocInfo info) {
    // Look for template parameters in 'info': these will be added to anything from the class.
    if (info != null) {
      setConstructorTemplateTypeNames(
          buildTemplateTypesFromJSDocInfo(info, true), argsParent.getParent());
    }

    inferParameterTypes(argsParent, info);

    return this;
  }

  @CanIgnoreReturnValue
  FunctionTypeBuilder inferImplicitConstructorParameters(ImmutableList<Parameter> parameters) {
    this.parameters = parameters;
    return this;
  }

  @CanIgnoreReturnValue
  FunctionTypeBuilder inferClosurePrimitive(@Nullable JSDocInfo info) {
    if (info != null && info.hasClosurePrimitiveId()) {
      this.closurePrimitiveId = ClosurePrimitive.fromStringId(info.getClosurePrimitiveId());
    }
    return this;
  }

  private void setConstructorTemplateTypeNames(List<TemplateType> templates, @Nullable Node ctor) {
    if (!templates.isEmpty()) {
      this.constructorTemplateTypeNames = ImmutableList.copyOf(templates);
      this.templateTypeNames =
          templateTypeNames.isEmpty()
              ? ImmutableList.copyOf(templates)
              : ImmutableList.<TemplateType>builder()
                  .addAll(templateTypeNames)
                  .addAll(constructorTemplateTypeNames)
                  .build();
      registerTemplates(templates, ctor);
    }
  }

  /**
   * @return Whether the given param is an optional param.
   */
  private boolean isOptionalParameterByConvention(Node param) {
    if (param.isDestructuringPattern()) {
      return false;
    }
    return codingConvention.isOptionalParameter(param);
  }

  /**
   * Determine whether this is a var args parameter.
   *
   * @return Whether the given param is a var args param.
   */
  private boolean isVarArgsParameterByConvention(Node param) {
    if (param.isDestructuringPattern()) {
      return false;
    }

    return codingConvention.isVarArgsParameter(param);
  }

  private ImmutableList<TemplateType> buildTemplateTypesFromJSDocInfo(
      JSDocInfo info, boolean allowTypeTransformations) {
    ImmutableMap<String, JSTypeExpression> infoTypeKeys = info.getTemplateTypes();
    ImmutableMap<String, Node> infoTypeTransformations = info.getTypeTransformations();
    if (infoTypeKeys.isEmpty() && infoTypeTransformations.isEmpty()) {
      return ImmutableList.of();
    }

    // Temporarily bootstrap the template environment with unbound (unknown bound) template types
    List<TemplateType> unboundedTemplates = new ArrayList<>();
    for (String templateKey : infoTypeKeys.keySet()) {
      unboundedTemplates.add(typeRegistry.createTemplateType(templateKey));
    }
    this.templateScope = typeRegistry.createScopeWithTemplates(templateScope, unboundedTemplates);

    // Evaluate template type bounds with bootstrapped environment and reroute the bounds to these
    ImmutableList.Builder<TemplateType> templates = ImmutableList.builder();
    Map<TemplateType, JSType> templatesToBounds = new LinkedHashMap<>();
    for (Map.Entry<String, JSTypeExpression> entry : infoTypeKeys.entrySet()) {
      JSTypeExpression expr = entry.getValue();
      JSType typeBound = typeRegistry.evaluateTypeExpression(entry.getValue(), templateScope);
      // It's an error to mark a template bound explicitly {?}. Unbounded templates have an implicit
      // unknown bound. Allowing explicit unknowns would make it more difficult to stricten their
      // treatment in the future, since "unknown" is currently used as a proxy for "implicit".
      if (expr.isExplicitUnknownTemplateBound()) {
        reportError(TEMPLATE_TYPE_ILLEGAL_BOUND, String.valueOf(typeBound), entry.getKey());
      }
      TemplateType template =
          typeRegistry.getType(templateScope, entry.getKey()).toMaybeTemplateType();
      if (template != null) {
        templatesToBounds.put(template, typeBound);
      } else {
        templatesToBounds.put(
            typeRegistry.createTemplateType(entry.getKey(), typeBound), typeBound);
      }
    }

    for (Map.Entry<TemplateType, JSType> entry : templatesToBounds.entrySet()) {
      TemplateType template = entry.getKey();
      JSType bound = entry.getValue();
      template.setBound(bound);
      templates.add(template);
    }

    for (Map.Entry<String, Node> entry : infoTypeTransformations.entrySet()) {
      if (allowTypeTransformations) {
        templates.add(
            typeRegistry.createTemplateTypeWithTransformation(entry.getKey(), entry.getValue()));
      } else {
        reportWarning(TEMPLATE_TRANSFORMATION_ON_CLASS, entry.getKey());
      }
    }

    ImmutableList<TemplateType> builtTemplates = templates.build();
    for (TemplateType template : builtTemplates) {
      if (template.containsCycle()) {
        reportError(
            RhinoErrorReporter.CYCLIC_INHERITANCE_ERROR,
            "Cycle detected in inheritance chain of type " + template.getReferenceName());
      }
    }

    return builtTemplates;
  }

  /** Infer the template type from the doc info. */
  @CanIgnoreReturnValue
  FunctionTypeBuilder inferTemplateTypeName(@Nullable JSDocInfo info, @Nullable JSType ownerType) {
    // NOTE: these template type names may override a list
    // of inherited ones from an overridden function.

    if (info != null && !maybeUseNativeClassTemplateNames(info)) {
      ImmutableList<TemplateType> templates =
          buildTemplateTypesFromJSDocInfo(info, !(isConstructor || isInterface));
      if (!templates.isEmpty()) {
        this.templateTypeNames = templates;
      }
    }

    ImmutableList<TemplateType> ownerTypeKeys =
        ownerType != null ? ownerType.getTemplateTypeMap().getTemplateKeys() : ImmutableList.of();

    if (!templateTypeNames.isEmpty() || !ownerTypeKeys.isEmpty()) {
      // TODO(sdh): The order of these should be switched to avoid class templates shadowing
      // method templates, but this currently loosens type checking of arrays more than we'd like.
      // See http://github.com/google/closure-compiler/issues/2973
      registerTemplates(
          Iterables.concat(templateTypeNames, ownerTypeKeys), contents.getSourceNode());
    }

    return this;
  }

  /**
   * Add a parameter to the param list.
   *
   * @param builder A builder.
   * @param paramType The parameter type.
   * @param warnedAboutArgList Whether we've already warned about arg ordering issues (like if
   *     optional args appeared before required ones).
   * @param isOptional Is this an optional parameter?
   * @param isVarArgs Is this a var args parameter?
   * @return Whether a warning was emitted.
   */
  private boolean addParameter(
      FunctionParamBuilder builder,
      JSType paramType,
      boolean warnedAboutArgList,
      boolean isOptional,
      boolean isVarArgs) {
    boolean emittedWarning = false;
    if (isOptional) {
      // Remembering that an optional parameter has been encountered
      // so that if a non optional param is encountered later, an
      // error can be reported.
      if (!builder.addOptionalParams(paramType) && !warnedAboutArgList) {
        reportWarning(VAR_ARGS_MUST_BE_LAST);
        emittedWarning = true;
      }
    } else if (isVarArgs) {
      if (!builder.addVarArgs(paramType) && !warnedAboutArgList) {
        reportWarning(VAR_ARGS_MUST_BE_LAST);
        emittedWarning = true;
      }
    } else {
      if (!builder.addRequiredParams(paramType) && !warnedAboutArgList) {
        // An optional parameter was seen and this argument is not an optional
        // or var arg so it is an error.
        if (builder.hasVarArgs()) {
          reportWarning(VAR_ARGS_MUST_BE_LAST);
        } else {
          reportWarning(OPTIONAL_ARG_AT_END);
        }
        emittedWarning = true;
      }
    }
    return emittedWarning;
  }

  /** Sets the returnType for this function using very basic type inference. */
  private void provideDefaultReturnType() {
    if (contents.getSourceNode() != null && contents.getSourceNode().isAsyncGeneratorFunction()) {
      // Set the return type of a generator function to:
      //   @return {!AsyncGenerator<?>}
      ObjectType generatorType = typeRegistry.getNativeObjectType(ASYNC_GENERATOR_TYPE);
      returnType =
          typeRegistry.createTemplatizedType(
              generatorType, typeRegistry.getNativeType(UNKNOWN_TYPE));
      return;
    } else if (contents.getSourceNode() != null && contents.getSourceNode().isGeneratorFunction()) {
      // Set the return type of a generator function to:
      //   @return {!Generator<?>}
      ObjectType generatorType = typeRegistry.getNativeObjectType(GENERATOR_TYPE);
      returnType =
          typeRegistry.createTemplatizedType(
              generatorType, typeRegistry.getNativeType(UNKNOWN_TYPE));
      return;
    }

    JSType inferredReturnType = typeRegistry.getNativeType(UNKNOWN_TYPE);
    if (!contents.mayHaveNonEmptyReturns()
        && !contents.mayHaveSingleThrow()
        && !contents.mayBeFromExterns()) {
      // Infer return types for non-generator functions.
      // We need to be extremely conservative about this, because of two
      // competing needs.
      // 1) If we infer the return type of f too widely, then we won't be able
      //    to assign f to other functions.
      // 2) If we infer the return type of f too narrowly, then we won't be
      //    able to override f in subclasses.
      // So we only infer in cases where the user doesn't expect to write
      // @return annotations--when it's very obvious that the function returns
      // nothing.
      inferredReturnType = typeRegistry.getNativeType(VOID_TYPE);
      returnTypeInferred = true;
    }

    if (contents.getSourceNode() != null && contents.getSourceNode().isAsyncFunction()) {
      // Set the return type of an async function:
      //   @return {!Promise<?>} or @return {!Promise<undefined>}
      ObjectType promiseType = typeRegistry.getNativeObjectType(PROMISE_TYPE);
      returnType = typeRegistry.createTemplatizedType(promiseType, inferredReturnType);
    } else {
      returnType = inferredReturnType;
    }
  }

  /** Builds the function type, and puts it in the registry. */
  FunctionType buildAndRegister() {
    if (returnType == null) {
      provideDefaultReturnType();
      checkNotNull(returnType);
    }

    if (parameters == null) {
      throw new IllegalStateException("All Function types must have params and a return type");
    }

    final FunctionType fnType;
    if (isConstructor) {
      fnType = getOrCreateConstructor();
    } else if (isInterface) {
      fnType = getOrCreateInterface();
    } else {
      fnType =
          this.createDefaultBuilder()
              .withParameters(parameters)
              .withReturnType(returnType, returnTypeInferred)
              .withTypeOfThis(thisType)
              .withIsAbstract(isAbstract)
              .withClosurePrimitiveId(closurePrimitiveId)
              .build();
      maybeSetBaseType(fnType);
    }

    if (implementedInterfaces != null && fnType.isConstructor()) {
      fnType.setImplementedInterfaces(implementedInterfaces);
    }

    if (extendedInterfaces != null) {
      fnType.setExtendedInterfaces(extendedInterfaces);
    }

    if (isRecord) {
      fnType.setImplicitMatch(true);
    }

    return fnType;
  }

  private void maybeSetBaseType(FunctionType fnType) {
    if (!fnType.hasInstanceType() || baseType == null) {
      return;
    }

    fnType.setPrototypeBasedOn(baseType);
    fnType.getInstanceType().mergeSupertypeTemplateTypes(baseType);
  }

  private FunctionType.Builder createDefaultBuilder() {
    return FunctionType.builder(this.typeRegistry)
        .withName(this.fnName)
        .withSourceNode(this.contents.getSourceNode())
        .withTemplateKeys(this.templateTypeNames)
        .setIsKnownAmbiguous(this.isKnownAmbiguous)
        .setGoogModuleId(TypedScopeCreator.containingGoogModuleIdOf(this.declarationScope));
  }

  /**
   * Returns a constructor function either by returning it from the registry if it exists or
   * creating and registering a new type. If there is already a type, then warn if the existing type
   * is different than the one we are creating, though still return the existing function if
   * possible. The primary purpose of this is that registering a constructor will fail for all
   * built-in types that are initialized in {@link JSTypeRegistry}. We a) want to make sure that the
   * type information specified in the externs file matches what is in the registry and b) annotate
   * the externs with the {@link JSType} from the registry so that there are not two separate JSType
   * objects for one type.
   */
  private FunctionType getOrCreateConstructor() {
    FunctionType fnType =
        this.createDefaultBuilder()
            .forConstructor()
            .withParameters(parameters)
            .withReturnType(returnType)
            .withConstructorTemplateKeys(constructorTemplateTypeNames)
            .withIsAbstract(isAbstract)
            .build();

    if (makesStructs) {
      fnType.setStruct();
    } else if (makesDicts) {
      fnType.setDict();
    } else if (makesUnrestricted) {
      fnType.setExplicitUnrestricted();
    }

    // There are two cases where this type already exists in the current scope:
    //   1. The type is a built-in that we initalized in JSTypeRegistry and is also defined in
    //  externs.
    //   2. Cases like "class C {} C = class {}"
    // See https://github.com/google/closure-compiler/issues/2928 for some related bugs.
    JSType existingType = typeRegistry.getType(declarationScope, fnName);
    if (existingType != null) {
      boolean isInstanceObject = existingType.isInstanceType();
      if (isInstanceObject || fnName.equals("Function")) {
        FunctionType existingFn =
            isInstanceObject
                ? existingType.toObjectType().getConstructor()
                : typeRegistry.getNativeFunctionType(FUNCTION_FUNCTION_TYPE);

        if (existingFn.getSource() == null) {
          existingFn.setSource(contents.getSourceNode());
        }

        if (!existingFn.hasEqualCallType(fnType)) {
          reportWarning(
              TYPE_REDEFINITION, formatFnName(), fnType.toString(), existingFn.toString());
        }

        // If the existing function is a built-in type, set its base type in case it @extends
        // another function (since we don't set its prototype in JSTypeRegistry)
        if (existingFn.isNativeObjectType()) {
          maybeSetBaseType(existingFn);
        }

        return existingFn;
      } else {
        // We fall through and return the created type, even though it will fail
        // to register. We have no choice as we have to return a function. We
        // issue an error elsewhere though, so the user should fix it.
      }
    }

    maybeSetBaseType(fnType);

    // TODO(johnlenz): determine what we are supposed to do for:
    //   @constructor
    //   this.Foo = ...
    //
    if (!syntacticFnName.isEmpty() && !syntacticFnName.startsWith("this.")) {
      typeRegistry.declareTypeForExactScope(
          declarationScope, syntacticFnName, fnType.getInstanceType());
    }
    return fnType;
  }

  private FunctionType getOrCreateInterface() {
    FunctionType fnType = null;

    JSType type = typeRegistry.getType(declarationScope, syntacticFnName);
    if (type != null && type.isInstanceType()) {
      FunctionType ctor = type.toMaybeObjectType().getConstructor();
      if (ctor.isInterface()) {
        fnType = ctor;
        fnType.setSource(contents.getSourceNode());
      }
    }

    if (fnType == null) {
      fnType = this.createDefaultBuilder().forInterface().withParameters().build();
      if (makesStructs) {
        fnType.setStruct();
      }
      if (!fnName.isEmpty()) {
        typeRegistry.declareTypeForExactScope(
            declarationScope, syntacticFnName, fnType.getInstanceType());
      }
      maybeSetBaseType(fnType);
    }
    return fnType;
  }

  private void reportWarning(DiagnosticType warning, String... args) {
    compiler.report(JSError.make(errorRoot, warning, args));
  }

  private void reportError(DiagnosticType error, String... args) {
    compiler.report(JSError.make(errorRoot, error, args));
  }

  /** Determines whether the given JsDoc info declares a function type. */
  static boolean isFunctionTypeDeclaration(JSDocInfo info) {
    return info.getParameterCount() > 0
        || info.hasReturnType()
        || info.hasThisType()
        || info.isConstructor()
        || info.isInterface()
        || info.isAbstract();
  }

  /**
   * Check whether a type is resolvable in the future If this has a supertype that hasn't been
   * resolved yet, then we can assume this type will be OK once the super type resolves.
   *
   * @return true if objectType is resolvable in the future
   */
  private static boolean hasMoreTagsToResolve(ObjectType objectType) {
    checkArgument(objectType.isUnknownType());
    FunctionType ctor = objectType.getConstructor();
    if (ctor != null) {
      // interface extends interfaces
      for (ObjectType interfaceType : ctor.getExtendedInterfaces()) {
        if (!interfaceType.isResolved()) {
          return true;
        }
      }
    }
    if (objectType.getImplicitPrototype() != null) {
      // constructor extends class
      return !objectType.getImplicitPrototype().isResolved();
    }
    return false;
  }

  /** Holds data dynamically inferred about functions. */
  static interface FunctionContents {
    /** Returns the source node of this function. May be null. */
    Node getSourceNode();

    /** Returns if the function may be in externs. */
    boolean mayBeFromExterns();

    /** Returns if a return of a real value (not undefined) appears. */
    boolean mayHaveNonEmptyReturns();

    /** Returns if this consists of a single throw. */
    boolean mayHaveSingleThrow();
  }

  static class UnknownFunctionContents implements FunctionContents {
    private static final UnknownFunctionContents singleton = new UnknownFunctionContents();

    static FunctionContents get() {
      return singleton;
    }

    @Override
    public Node getSourceNode() {
      return null;
    }

    @Override
    public boolean mayBeFromExterns() {
      return true;
    }

    @Override
    public boolean mayHaveNonEmptyReturns() {
      return true;
    }

    @Override
    public boolean mayHaveSingleThrow() {
      return true;
    }
  }

  static class AstFunctionContents implements FunctionContents {
    private final Node n;
    private boolean hasNonEmptyReturns = false;

    AstFunctionContents(Node n) {
      this.n = n;
    }

    @Override
    public Node getSourceNode() {
      return n;
    }

    @Override
    public boolean mayBeFromExterns() {
      return n.isFromExterns();
    }

    @Override
    public boolean mayHaveNonEmptyReturns() {
      return hasNonEmptyReturns;
    }

    void recordNonEmptyReturn() {
      hasNonEmptyReturns = true;
    }

    @Override
    public boolean mayHaveSingleThrow() {
      Node block = n.getLastChild();
      return block.hasOneChild() && block.getFirstChild().isThrow();
    }
  }
}
