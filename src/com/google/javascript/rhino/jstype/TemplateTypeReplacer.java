/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   John Lenz
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */


package com.google.javascript.rhino.jstype;

import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Specializes {@link TemplatizedType}s according to provided bindings.
 *
 * @author johnlenz@google.com (John Lenz)
 */
public final class TemplateTypeReplacer implements Visitor<JSType> {

  private final JSTypeRegistry registry;
  private final TemplateTypeMap bindings;

  private final boolean visitProperties;
  // TODO(nickreid): We should only need `useUnknownForMissingBinding`. Keeping two separate bits
  // was a quick fix for collapsing two different classes.
  private final boolean useUnknownForMissingKeys;
  private final boolean useUnknownForMissingValues;

  private boolean hasMadeReplacement = false;
  private TemplateType keyType;
  // The index in the corresponding bindings pointing to where the submap for the specified key
  // type ends, exclusive; or if keyType is null, defaults to -1.
  private int ownSubMapBoundary = -1;

  // Initialize data structures to `null` because these are unused in ~40% of TemplateTypeReplacers.
  private @Nullable Set<JSType> seenTypes = null;
  private @Nullable IdentityHashMap<JSType, JSType> visitedObjectTypes = null;

  /** Creates a replacer for use during {@code TypeInference}. */
  public static TemplateTypeReplacer forInference(
      JSTypeRegistry registry, Map<TemplateType, JSType> bindings) {
    ImmutableList<TemplateType> keys = ImmutableList.copyOf(bindings.keySet());
    ImmutableList.Builder<JSType> values = ImmutableList.builder();
    for (TemplateType key : keys) {
      JSType value = bindings.get(key);
      values.add(value != null ? value : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    }
    TemplateTypeMap map =
        registry.getEmptyTemplateTypeMap().copyWithExtension(keys, values.build());
    return new TemplateTypeReplacer(registry, map, true, true, true);
  }

  /**
   * Creates a replacer that will always totally eliminate {@link TemplateType}s from the
   * definitions of the types it performs replacement on.
   *
   * <p>If a binding for a {@link TemplateType} is required but not provided, `?` will be used.
   */
  public static TemplateTypeReplacer forTotalReplacement(
      JSTypeRegistry registry, TemplateTypeMap bindings) {
    return new TemplateTypeReplacer(registry, bindings, false, false, true);
  }

  /**
   * Creates a replacer that may not totally eliminate {@link TemplateType}s from the definitions of
   * the types it performs replacement on.
   *
   * <p>If a binding for a {@link TemplateType} is required but not provided, uses of that type will
   * not be replaced.
   */
  public static TemplateTypeReplacer forPartialReplacement(
      JSTypeRegistry registry, TemplateTypeMap bindings) {
    return new TemplateTypeReplacer(registry, bindings, false, false, false);
  }

  private TemplateTypeReplacer(
      JSTypeRegistry registry,
      TemplateTypeMap bindings,
      boolean visitProperties,
      boolean useUnknownForMissingKeys,
      boolean useUnknownForMissingValues) {
    this.registry = registry;
    this.bindings = bindings;
    this.visitProperties = visitProperties;
    this.useUnknownForMissingKeys = useUnknownForMissingKeys;
    this.useUnknownForMissingValues = useUnknownForMissingValues;
  }

  public boolean hasMadeReplacement() {
    return this.hasMadeReplacement;
  }

  // determinism is unnecessary for seenTypes, because we only call contains/add/remove.
  @SuppressWarnings("DeterministicDatastructure")
  private void initSeenTypes() {
    if (this.seenTypes == null) {
      this.seenTypes = Sets.newIdentityHashSet();
    }
  }

  @Override
  public JSType caseNoType(NoType type) {
    return type;
  }

  @Override
  public JSType caseEnumElementType(EnumElementType type) {
    return type;
  }

  @Override
  public JSType caseAllType() {
    return getNativeType(JSTypeNative.ALL_TYPE);
  }

  @Override
  public JSType caseBooleanType() {
    return getNativeType(JSTypeNative.BOOLEAN_TYPE);
  }

  @Override
  public JSType caseNoObjectType() {
    return getNativeType(JSTypeNative.NO_OBJECT_TYPE);
  }

  @Override
  public JSType caseFunctionType(FunctionType type) {
    return guardAgainstCycles(type, this::caseFunctionTypeUnguarded);
  }

  private JSType caseFunctionTypeUnguarded(FunctionType type) {
    if (isNativeFunctionType(type)) {
      return type;
    }

    if (!type.isOrdinaryFunction() && !type.isConstructor()) {
      return type;
    }

    boolean changed = false;

    JSType beforeThis = type.getTypeOfThis();
    JSType afterThis = coerseToThisType(beforeThis.visit(this));
    if (!identical(beforeThis, afterThis)) {
      changed = true;
    }

    JSType beforeReturn = type.getReturnType();
    JSType afterReturn = beforeReturn.visit(this);
    if (!identical(beforeReturn, afterReturn)) {
      changed = true;
    }

    boolean paramsChanged = false;
    int numParams = type.getParameters().size();
    FunctionParamBuilder paramBuilder =
        numParams == 0 ? null : new FunctionParamBuilder(registry, numParams);
    for (int i = 0; i < numParams; i++) {
      FunctionType.Parameter parameter = type.getParameters().get(i);
      JSType beforeParamType = parameter.getJSType();
      JSType afterParamType = beforeParamType.visit(this);

      if (!identical(beforeParamType, afterParamType)) {
        changed = true;
        paramsChanged = true;
        // TODO(lharker): we could also lazily create the FunctionParamBuilder here, but that would
        // require re-iterating over all previously seen params so unclear it's worth the complexity
        if (parameter.isOptional()) {
          paramBuilder.addOptionalParams(afterParamType);
        } else if (parameter.isVariadic()) {
          paramBuilder.addVarArgs(afterParamType);
        } else {
          paramBuilder.addRequiredParams(afterParamType);
        }
      } else {
        paramBuilder.newParameterFrom(parameter);
      }
    }

    if (changed) {
      return type.toBuilder()
          .withParameters(paramsChanged ? paramBuilder.build() : type.getParameters())
          .withReturnType(afterReturn)
          .withTypeOfThis(afterThis)
          .withIsAbstract(false) // TODO(b/187989034): Copy this from the source function.
          .build();
    }

    return type;
  }

  private JSType coerseToThisType(JSType type) {
    return type != null ? type : registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
  }

  @Override
  public JSType caseObjectType(ObjectType objType) {
    return guardAgainstCycles(objType, this::caseObjectTypeUnguarded);
  }

  private JSType caseObjectTypeUnguarded(ObjectType objType) {
    if (!visitProperties
        || objType.isNominalType()
        || objType instanceof ProxyObjectType
        || !objType.isRecordType()) {
      return objType;
    }

    // If we've visited this object type, we don't need to walk it again.
    JSType cached = getVisitedObjectTypeOrNull(objType);
    if (cached != null) {
      return cached;
    }

    boolean changed = false;
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    for (String prop : objType.getOwnPropertyNames()) {
      Node propertyNode = objType.getPropertyNode(prop);
      JSType beforeType = objType.getPropertyType(prop);
      JSType afterType = beforeType.visit(this);
      if (!identical(beforeType, afterType)) {
        changed = true;
      }
      builder.addProperty(prop, afterType, propertyNode);
    }

    // Use our new type if anything changed, and be sure to update the cache.
    JSType result = changed ? builder.build() : objType;
    visitedObjectTypes.put(objType, result);
    return result;
  }

  @Override
  public JSType caseTemplatizedType(TemplatizedType type) {
    return guardAgainstCycles(type, this::caseTemplatizedTypeUnguarded);
  }

  private JSType caseTemplatizedTypeUnguarded(TemplatizedType type) {
    boolean changed = false;
    ObjectType beforeBaseType = type.getReferencedType();
    ObjectType afterBaseType = ObjectType.cast(beforeBaseType.visit(this));
    if (!identical(beforeBaseType, afterBaseType)) {
      changed = true;
    }

    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (JSType beforeTemplateType : type.getTemplateTypes()) {
      JSType afterTemplateType = beforeTemplateType.visit(this);
      if (!identical(beforeTemplateType, afterTemplateType)) {
        changed = true;
      }
      builder.add(afterTemplateType);
    }

    if (changed) {
      type = registry.createTemplatizedType(afterBaseType, builder.build());
    }
    return type;
  }

  @Override
  public JSType caseUnknownType() {
    return getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  @Override
  public JSType caseNullType() {
    return getNativeType(JSTypeNative.NULL_TYPE);
  }

  @Override
  public JSType caseNumberType() {
    return getNativeType(JSTypeNative.NUMBER_TYPE);
  }

  @Override
  public JSType caseBigIntType() {
    return getNativeType(JSTypeNative.BIGINT_TYPE);
  }

  @Override
  public JSType caseStringType() {
    return getNativeType(JSTypeNative.STRING_TYPE);
  }

  @Override
  public JSType caseSymbolType() {
    return getNativeType(JSTypeNative.SYMBOL_TYPE);
  }

  @Override
  public JSType caseVoidType() {
    return getNativeType(JSTypeNative.VOID_TYPE);
  }

  @Override
  public JSType caseUnionType(UnionType type) {
    return guardAgainstCycles(type, this::caseUnionTypeUnguarded);
  }

  private JSType caseUnionTypeUnguarded(UnionType type) {
    boolean changed = false;
    List<JSType> results = new ArrayList<>();
    ImmutableList<JSType> alternates = type.getAlternates();
    int alternateCount = alternates.size();
    for (int i = 0; i < alternateCount; i++) {
      var alternative = alternates.get(i);
      JSType replacement = alternative.visit(this);
      if (!identical(replacement, alternative)) {
        changed = true;
      }
      results.add(replacement);
    }

    if (changed) {
      return registry.createUnionType(results); // maybe not a union
    }

    return type;
  }

  @Override
  public JSType caseTemplateType(TemplateType type) {
    this.hasMadeReplacement = true;

    if (!identical(type, this.keyType) && !bindings.hasTemplateKey(type, ownSubMapBoundary)) {
      return useUnknownForMissingKeys ? getNativeType(JSTypeNative.UNKNOWN_TYPE) : type;
    }

    this.initSeenTypes();
    if (seenTypes.contains(type)) {
      // If we have already encountered this TemplateType during replacement
      // (i.e. there is a reference loop) then return the TemplateType type itself.
      return type;
    } else if (!bindings.hasTemplateType(type)) {
      // If there is no JSType substitution for the TemplateType, return either the
      // UNKNOWN_TYPE or the TemplateType type itself, depending on configuration.
      return useUnknownForMissingValues ? getNativeType(JSTypeNative.UNKNOWN_TYPE) : type;
    }

    JSType replacement = bindings.getUnresolvedOriginalTemplateType(type);
    // Recursive templatized types, such as T => Foo<T>. Don't do any replacement - we should
    // preserve Foo<T> as it was before.
    // Note: this isn't perfect - if we had T => Foo<T, U>, this will skip replacing U - but
    // it's better than a stack overflow.
    // Return the 'type' instead of 'replacement' to notify callers that no changes were made.
    // If we didn't check this here, we'd still skip doing any work in replacement.visit(this)
    // because guardAgainstCycles would have already seen 'replacement'. But we'd return
    // 'visitedReplacement' instead of 'type'.
    if (identical(replacement, keyType) || seenTypes.contains(replacement)) {
      return type;
    }
    seenTypes.add(type);
    JSType visitedReplacement = replacement.visit(this);
    seenTypes.remove(type);

    Preconditions.checkState(
        !identical(visitedReplacement, keyType),
        "Trying to replace key %s with the same value",
        keyType);
    return visitedReplacement;
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    return registry.getNativeType(nativeType);
  }

  private boolean isNativeFunctionType(FunctionType type) {
    return type.isNativeObjectType();
  }

  @Override
  public JSType caseNamedType(NamedType type) {
    // The internals of a named type aren't interesting.
    return type;
  }

  @Override
  public JSType caseProxyObjectType(ProxyObjectType type) {
    return guardAgainstCycles(type, this::caseProxyObjectTypeUnguarded);
  }

  private JSType caseProxyObjectTypeUnguarded(ProxyObjectType type) {
    // Be careful not to unwrap a type unless it has changed.
    JSType beforeType = type.getReferencedTypeInternal();
    JSType replacement = beforeType.visit(this);
    if (!identical(replacement, beforeType)) {
      return replacement;
    }
    return type;
  }

  void setKeyType(TemplateType keyType, int ownSubMapBoundary) {
    this.keyType = keyType;
    this.ownSubMapBoundary = ownSubMapBoundary;
  }

  private <T extends JSType> JSType guardAgainstCycles(T type, Function<T, JSType> mapper) {
    this.initSeenTypes();
    if (!this.seenTypes.add(type)) {
      return type;
    }
    try {
      return mapper.apply(type);
    } finally {
      this.seenTypes.remove(type);
    }
  }

  // determinism is unnecessary for visitedObjectTypes, because we only call get/set
  @SuppressWarnings("DeterministicDatastructure")
  private @Nullable JSType getVisitedObjectTypeOrNull(JSType type) {
    // If we've visited this object type, we don't need to walk it again.
    if (visitedObjectTypes == null) {
      visitedObjectTypes = new IdentityHashMap<>();
      return null;
    }
    return visitedObjectTypes.get(type);
  }
}
