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
 *   Nick Santos
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.base.Tri.FALSE;
import static com.google.javascript.jscomp.base.Tri.TRUE;
import static com.google.javascript.jscomp.base.Tri.UNKNOWN;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Outcome;
import com.google.javascript.rhino.jstype.JSType.TypePair;
import com.google.javascript.rhino.jstype.NamedType.ResolutionKind;
import com.google.javascript.rhino.testing.Asserts;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;
import com.google.javascript.rhino.testing.MapBasedScope;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// TODO(nicksantos): Split some of this up into per-class unit tests.
@RunWith(JUnit4.class)
public class JSTypeTest extends BaseJSTypeTestCase {
  private FunctionType dateMethod;
  private FunctionType functionType;
  private NamedType unresolvedNamedType;
  private FunctionType googBar;
  private FunctionType googSubBar;
  private FunctionType googSubSubBar;
  private ObjectType googBarInst;
  private ObjectType googSubBarInst;
  private ObjectType googSubSubBarInst;
  private NamedType namedGoogBar;
  private ObjectType subclassOfUnresolvedNamedType;
  private FunctionType subclassCtor;
  private FunctionType interfaceType;
  private ObjectType interfaceInstType;
  private FunctionType subInterfaceType;
  private ObjectType subInterfaceInstType;
  private JSType recordType;
  private EnumType enumType;
  private EnumElementType elementsType;
  private NamedType forwardDeclaredNamedType;

  private static final StaticTypedScope EMPTY_SCOPE = MapBasedScope.emptyScope();

  /**
   * A non exhaustive list of representative types used to test simple properties that should hold
   * for all types (such as the reflexivity of subtyping).
   */
  private ImmutableList<JSType> types;

  @Before
  @SuppressWarnings({"MustBeClosedChecker"})
  public void setUp() throws Exception {
    resetRegistryWithForwardDeclaredName("forwardDeclared");
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      final ObjectType googObject = registry.createAnonymousObjectType(null);
      MapBasedScope scope = new MapBasedScope(ImmutableMap.of("goog", googObject));

      RecordTypeBuilder builder = new RecordTypeBuilder(registry);
      builder.addProperty("a", NUMBER_TYPE, null);
      builder.addProperty("b", STRING_TYPE, null);
      recordType = builder.build();

      enumType = EnumType.builder(registry).setName("Enum").setElementType(NUMBER_TYPE).build();
      elementsType = enumType.getElementsType();
      functionType = FunctionType.builder(registry).withReturnType(NUMBER_TYPE).build();
      dateMethod =
          FunctionType.builder(registry)
              .withParameters(registry.createParameters())
              .withReturnType(NUMBER_TYPE)
              .withTypeOfThis(DATE_TYPE)
              .build();

      errorReporter.expectAllWarnings("Bad type annotation. Unknown type not.resolved.named.type");
      unresolvedNamedType = registry.createNamedType(scope, "not.resolved.named.type", "", -1, -1);
      namedGoogBar = registry.createNamedType(scope, "goog.Bar", null, -1, -1);

      subclassCtor = FunctionType.builder(registry).forConstructor().build();
      subclassCtor.setPrototypeBasedOn(unresolvedNamedType);
      subclassOfUnresolvedNamedType = subclassCtor.getInstanceType();

      interfaceType = FunctionType.builder(registry).forInterface().withName("Interface").build();
      interfaceInstType = interfaceType.getInstanceType();

      subInterfaceType =
          FunctionType.builder(registry).forInterface().withName("SubInterfacce").build();
      subInterfaceType.setExtendedInterfaces(Lists.<ObjectType>newArrayList(interfaceInstType));
      subInterfaceInstType = subInterfaceType.getInstanceType();

      googBar = registry.createConstructorType("goog.Bar", null, null, null, null, false);
      googBar.getPrototype().defineDeclaredProperty("date", DATE_TYPE, null);
      googBar.setImplementedInterfaces(Lists.<ObjectType>newArrayList(interfaceInstType));
      googBarInst = googBar.getInstanceType();

      googSubBar = registry.createConstructorType("googSubBar", null, null, null, null, false);
      googSubBar.setPrototypeBasedOn(googBar.getInstanceType());
      googSubBarInst = googSubBar.getInstanceType();

      googSubSubBar =
          registry.createConstructorType("googSubSubBar", null, null, null, null, false);
      googSubSubBar.setPrototypeBasedOn(googSubBar.getInstanceType());
      googSubSubBarInst = googSubSubBar.getInstanceType();

      googObject.defineDeclaredProperty("Bar", googBar, null);

      forwardDeclaredNamedType = registry.createNamedType(scope, "forwardDeclared", "source", 1, 0);
    }

    assertThat(namedGoogBar.getImplicitPrototype()).isNotNull();

    types =
        ImmutableList.of(
            NO_OBJECT_TYPE,
            new NoResolvedType(registry, "Foo", null),
            NO_TYPE,
            BOOLEAN_OBJECT_TYPE,
            BOOLEAN_TYPE,
            STRING_OBJECT_TYPE,
            STRING_TYPE,
            SYMBOL_OBJECT_TYPE,
            SYMBOL_TYPE,
            VOID_TYPE,
            UNKNOWN_TYPE,
            NULL_TYPE,
            NUMBER_OBJECT_TYPE,
            NUMBER_TYPE,
            DATE_TYPE,
            dateMethod,
            functionType,
            unresolvedNamedType,
            googBar,
            googSubBar,
            googSubSubBar,
            namedGoogBar,
            googBar.getInstanceType(),
            subclassOfUnresolvedNamedType,
            subclassCtor,
            recordType,
            enumType,
            elementsType,
            googBar,
            googSubBar,
            forwardDeclaredNamedType);
  }

  /** Tests the behavior of the top constructor type. */
  @Test
  public void testUniversalConstructorType() {
    // isXxx
    assertThat(FUNCTION_TYPE.isNoObjectType()).isFalse();
    assertThat(FUNCTION_TYPE.isNoType()).isFalse();
    assertThat(FUNCTION_TYPE.isArrayType()).isFalse();
    assertThat(FUNCTION_TYPE.isBooleanValueType()).isFalse();
    assertThat(FUNCTION_TYPE.isDateType()).isFalse();
    assertThat(FUNCTION_TYPE.isEnumElementType()).isFalse();
    assertThat(FUNCTION_TYPE.isNullType()).isFalse();
    assertThat(FUNCTION_TYPE.isNamedType()).isFalse();
    assertThat(FUNCTION_TYPE.isNullType()).isFalse();
    assertThat(FUNCTION_TYPE.isNumber()).isFalse();
    assertThat(FUNCTION_TYPE.isNumberObjectType()).isFalse();
    assertThat(FUNCTION_TYPE.isNumberValueType()).isFalse();
    assertThat(FUNCTION_TYPE.isObject()).isTrue();
    assertThat(FUNCTION_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(FUNCTION_TYPE.isRegexpType()).isFalse();
    assertThat(FUNCTION_TYPE.isString()).isFalse();
    assertThat(FUNCTION_TYPE.isStringObjectType()).isFalse();
    assertThat(FUNCTION_TYPE.isStringValueType()).isFalse();
    assertThat(FUNCTION_TYPE.isSymbol()).isFalse();
    assertThat(FUNCTION_TYPE.isSymbolObjectType()).isFalse();
    assertThat(FUNCTION_TYPE.isSymbolValueType()).isFalse();
    assertThat(FUNCTION_TYPE.isEnumType()).isFalse();
    assertThat(FUNCTION_TYPE.isUnionType()).isFalse();
    assertThat(FUNCTION_TYPE.isStruct()).isFalse();
    assertThat(FUNCTION_TYPE.isDict()).isFalse();
    assertThat(FUNCTION_TYPE.isAllType()).isFalse();
    assertThat(FUNCTION_TYPE.isVoidType()).isFalse();
    assertThat(FUNCTION_TYPE.isConstructor()).isTrue();
    assertThat(FUNCTION_TYPE.isInstanceType()).isTrue();

    // isSubtype
    assertThat(FUNCTION_TYPE.isSubtype(NO_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(NO_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(ARRAY_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(BOOLEAN_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(DATE_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(functionType)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(recordType)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(NULL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(REGEXP_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(STRING_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(SYMBOL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.isSubtype(ALL_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(VOID_TYPE)).isFalse();

    // canTestForEqualityWith
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(NO_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(ALL_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(DATE_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(functionType)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(recordType)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(NULL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(STRING_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForEqualityWith(VOID_TYPE)).isFalse();

    // canTestForShallowEqualityWith
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(recordType)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(FUNCTION_TYPE.isNullable()).isFalse();
    assertThat(FUNCTION_TYPE.isVoidable()).isFalse();

    // isObject
    assertThat(FUNCTION_TYPE.isObject()).isTrue();

    // matchesXxx
    assertThat(FUNCTION_TYPE.matchesNumberContext()).isFalse();
    assertThat(FUNCTION_TYPE.matchesObjectContext()).isTrue();
    assertThat(FUNCTION_TYPE.matchesStringContext()).isFalse();
    assertThat(FUNCTION_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(FUNCTION_TYPE.toString()).isEqualTo("Function");
    assertThat(FUNCTION_TYPE.hasDisplayName()).isTrue();
    assertThat(FUNCTION_TYPE.getDisplayName()).isEqualTo("Function");

    // getPropertyType
    assertTypeEquals(UNKNOWN_TYPE, FUNCTION_TYPE.getPropertyType("anyProperty"));

    assertThat(FUNCTION_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(FUNCTION_TYPE);

    assertThat(FUNCTION_TYPE.isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the Bottom Object type. */
  @Test
  public void testNoObjectType() {
    // isXxx
    assertThat(NO_OBJECT_TYPE.isNoObjectType()).isTrue();
    assertThat(NO_OBJECT_TYPE.isNoType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isNumber()).isTrue();
    assertThat(NO_OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isObject()).isTrue();
    assertThat(NO_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isString()).isTrue();
    assertThat(NO_OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isSymbol()).isTrue();
    assertThat(NO_OBJECT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isSymbolValueType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(NO_OBJECT_TYPE.isDict()).isFalse();
    assertThat(NO_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(NO_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(NO_OBJECT_TYPE.isInstanceType()).isFalse();

    // isSubtype
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(functionType)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(recordType)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, recordType);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_OBJECT_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(recordType)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(NO_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(NO_OBJECT_TYPE.isVoidable()).isFalse();

    // isObject
    assertThat(NO_OBJECT_TYPE.isObject()).isTrue();

    // matchesXxx
    assertThat(NO_OBJECT_TYPE.matchesNumberContext()).isTrue();
    assertThat(NO_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(NO_OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(NO_OBJECT_TYPE.matchesSymbolContext()).isTrue();

    // toString
    assertThat(NO_OBJECT_TYPE.toString()).isEqualTo("NoObject");
    assertThat(NO_OBJECT_TYPE.hasDisplayName()).isFalse();
    assertThat(NO_OBJECT_TYPE.getDisplayName()).isNull();

    // getPropertyType
    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getPropertyType("anyProperty"));

    Asserts.assertResolvesToSame(NO_OBJECT_TYPE);

    assertThat(NO_OBJECT_TYPE.isNominalConstructorOrInterface()).isFalse();
  }

  /** Tests the behavior of the Bottom type. */
  @Test
  public void testNoType() {
    // isXxx
    assertThat(NO_TYPE.isNoObjectType()).isFalse();
    assertThat(NO_TYPE.isNoType()).isTrue();
    assertThat(NO_TYPE.isArrayType()).isFalse();
    assertThat(NO_TYPE.isBooleanValueType()).isFalse();
    assertThat(NO_TYPE.isDateType()).isFalse();
    assertThat(NO_TYPE.isEnumElementType()).isFalse();
    assertThat(NO_TYPE.isNullType()).isFalse();
    assertThat(NO_TYPE.isNamedType()).isFalse();
    assertThat(NO_TYPE.isNullType()).isFalse();
    assertThat(NO_TYPE.isNumber()).isTrue();
    assertThat(NO_TYPE.isNumberObjectType()).isFalse();
    assertThat(NO_TYPE.isNumberValueType()).isFalse();
    assertThat(NO_TYPE.isObject()).isTrue();
    assertThat(NO_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(NO_TYPE.isRegexpType()).isFalse();
    assertThat(NO_TYPE.isString()).isTrue();
    assertThat(NO_TYPE.isStringObjectType()).isFalse();
    assertThat(NO_TYPE.isStringValueType()).isFalse();
    assertThat(NO_TYPE.isSymbol()).isTrue();
    assertThat(NO_TYPE.isSymbolObjectType()).isFalse();
    assertThat(NO_TYPE.isSymbolValueType()).isFalse();
    assertThat(NO_TYPE.isEnumType()).isFalse();
    assertThat(NO_TYPE.isUnionType()).isFalse();
    assertThat(NO_TYPE.isStruct()).isFalse();
    assertThat(NO_TYPE.isDict()).isFalse();
    assertThat(NO_TYPE.isAllType()).isFalse();
    assertThat(NO_TYPE.isVoidType()).isFalse();
    assertThat(NO_TYPE.isConstructor()).isFalse();
    assertThat(NO_TYPE.isInstanceType()).isFalse();

    // isSubtype
    assertThat(NO_TYPE.isSubtypeOf(NO_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(functionType)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NULL_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NUMBER_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(STRING_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(SYMBOL_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(VOID_TYPE)).isTrue();

    // canTestForEqualityWith
    assertCannotTestForEqualityWith(NO_TYPE, NO_TYPE);
    assertCannotTestForEqualityWith(NO_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, functionType);
    assertCanTestForEqualityWith(NO_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NO_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertThat(NO_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(NO_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isTrue();

    // isNullable
    assertThat(NO_TYPE.isNullable()).isTrue();
    assertThat(NO_TYPE.isVoidable()).isTrue();

    // isObject
    assertThat(NO_TYPE.isObject()).isTrue();

    // matchesXxx
    assertThat(NO_TYPE.matchesNumberContext()).isTrue();
    assertThat(NO_TYPE.matchesObjectContext()).isTrue();
    assertThat(NO_TYPE.matchesStringContext()).isTrue();
    assertThat(NO_TYPE.matchesSymbolContext()).isTrue();

    // toString
    assertThat(NO_TYPE.toString()).isEqualTo("None");
    assertThat(NO_TYPE.getDisplayName()).isNull();
    assertThat(NO_TYPE.hasDisplayName()).isFalse();

    // getPropertyType
    assertTypeEquals(NO_TYPE,
        NO_TYPE.getPropertyType("anyProperty"));

    Asserts.assertResolvesToSame(NO_TYPE);

    assertThat(NO_TYPE.isNominalConstructorOrInterface()).isFalse();
  }

  /** Tests the behavior of the unresolved Bottom type. */
  @Test
  public void testNoResolvedType() {
    // isXxx
    NoResolvedType noResolvedFooType = new NoResolvedType(registry, "Foo", null);
    assertThat(noResolvedFooType.isNoObjectType()).isFalse();
    assertThat(noResolvedFooType.isNoType()).isFalse();
    assertThat(noResolvedFooType.isNoResolvedType()).isTrue();
    assertThat(noResolvedFooType.isArrayType()).isFalse();
    assertThat(noResolvedFooType.isBooleanValueType()).isFalse();
    assertThat(noResolvedFooType.isDateType()).isFalse();
    assertThat(noResolvedFooType.isEnumElementType()).isFalse();
    assertThat(noResolvedFooType.isNullType()).isFalse();
    assertThat(noResolvedFooType.isNamedType()).isFalse();
    assertThat(noResolvedFooType.isNumber()).isFalse();
    assertThat(noResolvedFooType.isNumberObjectType()).isFalse();
    assertThat(noResolvedFooType.isNumberValueType()).isFalse();
    assertThat(noResolvedFooType.isObject()).isTrue();
    assertThat(noResolvedFooType.isFunctionPrototypeType()).isFalse();
    assertThat(noResolvedFooType.isRegexpType()).isFalse();
    assertThat(noResolvedFooType.isString()).isFalse();
    assertThat(noResolvedFooType.isStringObjectType()).isFalse();
    assertThat(noResolvedFooType.isStringValueType()).isFalse();
    assertThat(noResolvedFooType.isSymbol()).isFalse();
    assertThat(noResolvedFooType.isSymbolObjectType()).isFalse();
    assertThat(noResolvedFooType.isSymbolValueType()).isFalse();
    assertThat(noResolvedFooType.isEnumType()).isFalse();
    assertThat(noResolvedFooType.isUnionType()).isFalse();
    assertThat(noResolvedFooType.isStruct()).isFalse();
    assertThat(noResolvedFooType.isDict()).isFalse();
    assertThat(noResolvedFooType.isAllType()).isFalse();
    assertThat(noResolvedFooType.isVoidType()).isFalse();
    assertThat(noResolvedFooType.isConstructor()).isFalse();
    assertThat(noResolvedFooType.isInstanceType()).isFalse();

    // isSubtype
    assertType(noResolvedFooType).isSubtypeOf(noResolvedFooType);
    assertType(noResolvedFooType).isSubtypeOf(ALL_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(NO_OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(ARRAY_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(BOOLEAN_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(BOOLEAN_OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(DATE_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(functionType);
    assertType(noResolvedFooType).isNotSubtypeOf(NULL_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(NUMBER_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(NUMBER_OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(REGEXP_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(STRING_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(STRING_OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(SYMBOL_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(SYMBOL_OBJECT_TYPE);
    assertType(noResolvedFooType).isNotSubtypeOf(VOID_TYPE);

    // canTestForEqualityWith
    assertCanTestForEqualityWith(noResolvedFooType, noResolvedFooType);
    assertCanTestForEqualityWith(noResolvedFooType, NO_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, ARRAY_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, DATE_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, functionType);
    assertCanTestForEqualityWith(noResolvedFooType, NULL_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, NUMBER_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, REGEXP_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, STRING_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, SYMBOL_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, ALL_TYPE);
    assertCanTestForEqualityWith(noResolvedFooType, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertType(noResolvedFooType).canTestForShallowEqualityWith(noResolvedFooType);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(NO_OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(ARRAY_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(BOOLEAN_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(DATE_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(functionType);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(NULL_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(NUMBER_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(REGEXP_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(STRING_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(STRING_OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(SYMBOL_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(ALL_TYPE);
    assertType(noResolvedFooType).canTestForShallowEqualityWith(VOID_TYPE);

    // isNullable
    assertThat(noResolvedFooType.isNullable()).isFalse();
    assertThat(noResolvedFooType.isVoidable()).isFalse();

    // isObject
    assertThat(noResolvedFooType.isObject()).isTrue();

    // matchesXxx
    assertThat(noResolvedFooType.matchesNumberContext()).isFalse();
    assertThat(noResolvedFooType.matchesObjectContext()).isFalse();
    assertThat(noResolvedFooType.matchesStringContext()).isFalse();
    assertThat(noResolvedFooType.matchesSymbolContext()).isFalse();

    // toString
    assertThat(noResolvedFooType.toString()).isEqualTo("NoResolvedType<Foo>");
    assertThat(noResolvedFooType.getDisplayName()).isEqualTo("Foo");
    assertThat(noResolvedFooType.hasDisplayName()).isTrue();

    Asserts.assertResolvesToSame(noResolvedFooType);

    assertThat(forwardDeclaredNamedType.isEmptyType()).isTrue();
    assertThat(forwardDeclaredNamedType.isNoResolvedType()).isTrue();

    UnionType nullable = (UnionType) registry.createNullableType(noResolvedFooType);
    assertType(nullable.getGreatestSubtype(NULL_TYPE)).isEqualTo(NULL_TYPE);
    assertType(nullable.getRestrictedUnion(NULL_TYPE)).isEqualTo(noResolvedFooType);

    UnionType testIsVoidable = (UnionType) registry.createUnionType(noResolvedFooType, VOID_TYPE);
    assertThat(testIsVoidable.getPossibleToBooleanOutcomes()).isEqualTo(BooleanLiteralSet.BOTH);
  }

  /** Tests the behavior of the Array type. */
  @Test
  public void testArrayType() {
    // isXxx
    assertThat(ARRAY_TYPE.isArrayType()).isTrue();
    assertThat(ARRAY_TYPE.isBooleanValueType()).isFalse();
    assertThat(ARRAY_TYPE.isDateType()).isFalse();
    assertThat(ARRAY_TYPE.isEnumElementType()).isFalse();
    assertThat(ARRAY_TYPE.isNamedType()).isFalse();
    assertThat(ARRAY_TYPE.isNullType()).isFalse();
    assertThat(ARRAY_TYPE.isNumber()).isFalse();
    assertThat(ARRAY_TYPE.isNumberObjectType()).isFalse();
    assertThat(ARRAY_TYPE.isNumberValueType()).isFalse();
    assertThat(ARRAY_TYPE.isObject()).isTrue();
    assertThat(ARRAY_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(ARRAY_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(ARRAY_TYPE.isRegexpType()).isFalse();
    assertThat(ARRAY_TYPE.isString()).isFalse();
    assertThat(ARRAY_TYPE.isStringObjectType()).isFalse();
    assertThat(ARRAY_TYPE.isStringValueType()).isFalse();
    assertThat(ARRAY_TYPE.isSymbol()).isFalse();
    assertThat(ARRAY_TYPE.isSymbolObjectType()).isFalse();
    assertThat(ARRAY_TYPE.isSymbolValueType()).isFalse();
    assertThat(ARRAY_TYPE.isEnumType()).isFalse();
    assertThat(ARRAY_TYPE.isUnionType()).isFalse();
    assertThat(ARRAY_TYPE.isStruct()).isFalse();
    assertThat(ARRAY_TYPE.isDict()).isFalse();
    assertThat(ARRAY_TYPE.isAllType()).isFalse();
    assertThat(ARRAY_TYPE.isVoidType()).isFalse();
    assertThat(ARRAY_TYPE.isConstructor()).isFalse();
    assertThat(ARRAY_TYPE.isInstanceType()).isTrue();

    // isSubtype
    assertThat(ARRAY_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(recordType)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();

    // canBeCalled
    assertThat(ARRAY_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(ARRAY_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(ARRAY_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, functionType);
    assertCanTestForEqualityWith(ARRAY_TYPE, recordType);
    assertCannotTestForEqualityWith(ARRAY_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(ARRAY_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(recordType)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(ARRAY_TYPE.isNullable()).isFalse();
    assertThat(ARRAY_TYPE.isVoidable()).isFalse();
    assertThat(createUnionType(ARRAY_TYPE, NULL_TYPE).isNullable()).isTrue();
    assertThat(createUnionType(ARRAY_TYPE, VOID_TYPE).isVoidable()).isTrue();

    // isObject
    assertThat(ARRAY_TYPE.isObject()).isTrue();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        ARRAY_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(STRING_OBJECT_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(SYMBOL_OBJECT_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(ARRAY_TYPE, functionType),
        ARRAY_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, ARRAY_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, ARRAY_TYPE),
        ARRAY_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertThat(ARRAY_TYPE.getImplicitPrototype().getPropertiesCount()).isEqualTo(17);
    assertThat(ARRAY_TYPE.getPropertiesCount()).isEqualTo(18);
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("concat"));
    assertReturnTypeEquals(STRING_TYPE,
        ARRAY_TYPE.getPropertyType("join"));
    assertReturnTypeEquals(UNKNOWN_TYPE, ARRAY_TYPE.getPropertyType("pop"));
    assertReturnTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("push"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("reverse"));
    assertReturnTypeEquals(UNKNOWN_TYPE, ARRAY_TYPE.getPropertyType("shift"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("slice"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("sort"));
    assertReturnTypeEquals(ARRAY_TYPE, ARRAY_TYPE.getPropertyType("splice"));
    assertReturnTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("unshift"));
    assertTypeEquals(NUMBER_TYPE, ARRAY_TYPE.getPropertyType("length"));

    // isPropertyType*
    assertPropertyTypeDeclared(ARRAY_TYPE, "pop");

    // matchesXxx
    assertThat(ARRAY_TYPE.matchesNumberContext()).isFalse();
    assertThat(ARRAY_TYPE.matchesObjectContext()).isTrue();
    assertThat(ARRAY_TYPE.matchesStringContext()).isTrue();
    assertThat(ARRAY_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(ARRAY_TYPE.toString()).isEqualTo("Array");
    assertThat(ARRAY_TYPE.hasDisplayName()).isTrue();
    assertThat(ARRAY_TYPE.getDisplayName()).isEqualTo("Array");

    assertThat(ARRAY_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(ARRAY_TYPE);

    assertThat(ARRAY_TYPE.isNominalConstructorOrInterface()).isFalse();
    assertThat(ARRAY_TYPE.getConstructor().isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the unknown type. */
  @Test
  public void testUnknownType() {
    // isXxx
    assertThat(UNKNOWN_TYPE.isArrayType()).isFalse();
    assertThat(UNKNOWN_TYPE.isBooleanObjectType()).isFalse();
    assertThat(UNKNOWN_TYPE.isBooleanValueType()).isFalse();
    assertThat(UNKNOWN_TYPE.isDateType()).isFalse();
    assertThat(UNKNOWN_TYPE.isEnumElementType()).isFalse();
    assertThat(UNKNOWN_TYPE.isNamedType()).isFalse();
    assertThat(UNKNOWN_TYPE.isNullType()).isFalse();
    assertThat(UNKNOWN_TYPE.isNumberObjectType()).isFalse();
    assertThat(UNKNOWN_TYPE.isNumberValueType()).isFalse();
    assertThat(UNKNOWN_TYPE.isObject()).isTrue();
    assertThat(UNKNOWN_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(UNKNOWN_TYPE.isRegexpType()).isFalse();
    assertThat(UNKNOWN_TYPE.isStringObjectType()).isFalse();
    assertThat(UNKNOWN_TYPE.isStringValueType()).isFalse();
    assertThat(UNKNOWN_TYPE.isSymbolObjectType()).isFalse();
    assertThat(UNKNOWN_TYPE.isSymbolValueType()).isFalse();
    assertThat(UNKNOWN_TYPE.isEnumType()).isFalse();
    assertThat(UNKNOWN_TYPE.isUnionType()).isFalse();
    assertThat(UNKNOWN_TYPE.isStruct()).isFalse();
    assertThat(UNKNOWN_TYPE.isDict()).isFalse();
    assertThat(UNKNOWN_TYPE.isUnknownType()).isTrue();
    assertThat(UNKNOWN_TYPE.isVoidType()).isFalse();
    assertThat(UNKNOWN_TYPE.isConstructor()).isFalse();
    assertThat(UNKNOWN_TYPE.isInstanceType()).isFalse();

    // autoboxesTo
    assertThat(UNKNOWN_TYPE.autoboxesTo()).isNull();

    // isSubtype
    assertThat(UNKNOWN_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(STRING_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(SYMBOL_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(NUMBER_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(functionType)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(recordType)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(NULL_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(namedGoogBar)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isSubtypeOf(VOID_TYPE)).isTrue();

    // canBeCalled
    assertThat(UNKNOWN_TYPE.canBeCalled()).isTrue();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(UNKNOWN_TYPE, UNKNOWN_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, functionType);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, recordType);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(UNKNOWN_TYPE, BOOLEAN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(recordType)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();

    // canHaveNullValue
    assertThat(UNKNOWN_TYPE.isNullable()).isTrue();
    assertThat(UNKNOWN_TYPE.isVoidable()).isTrue();

    // getGreatestCommonType
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(STRING_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(UNKNOWN_TYPE, UNKNOWN_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertThat(UNKNOWN_TYPE.matchesNumberContext()).isTrue();
    assertThat(UNKNOWN_TYPE.matchesObjectContext()).isTrue();
    assertThat(UNKNOWN_TYPE.matchesStringContext()).isTrue();
    assertThat(UNKNOWN_TYPE.matchesSymbolContext()).isTrue();

    // isPropertyType*
    assertPropertyTypeUnknown(UNKNOWN_TYPE, "XXX");

    // toString
    assertThat(UNKNOWN_TYPE.toString()).isEqualTo("?");
    assertThat(UNKNOWN_TYPE.hasDisplayName()).isTrue();
    assertThat(UNKNOWN_TYPE.getDisplayName()).isEqualTo("Unknown");

    Asserts.assertResolvesToSame(UNKNOWN_TYPE);
    assertThat(UNKNOWN_TYPE.isNominalConstructorOrInterface()).isFalse();

    assertThat(UNKNOWN_TYPE.getPropertyType("abc")).isEqualTo(UNKNOWN_TYPE);
  }

  /** Tests the behavior of the checked unknown type. */
  @Test
  public void testCheckedUnknownType() {
    // isPropertyType*
    assertPropertyTypeUnknown(CHECKED_UNKNOWN_TYPE, "XXX");

    // toString
    assertThat(CHECKED_UNKNOWN_TYPE.toString()).isEqualTo("??");
    assertThat(CHECKED_UNKNOWN_TYPE.hasDisplayName()).isTrue();
    assertThat(CHECKED_UNKNOWN_TYPE.getDisplayName()).isEqualTo("Unknown");

    Asserts.assertResolvesToSame(CHECKED_UNKNOWN_TYPE);
    assertThat(CHECKED_UNKNOWN_TYPE.isNominalConstructorOrInterface()).isFalse();

    assertThat(CHECKED_UNKNOWN_TYPE.getPropertyType("abc")).isEqualTo(CHECKED_UNKNOWN_TYPE);
  }

  /** Tests the behavior of the unknown type. */
  @Test
  public void testAllType() {
    // isXxx
    assertThat(ALL_TYPE.isArrayType()).isFalse();
    assertThat(ALL_TYPE.isBooleanValueType()).isFalse();
    assertThat(ALL_TYPE.isDateType()).isFalse();
    assertThat(ALL_TYPE.isEnumElementType()).isFalse();
    assertThat(ALL_TYPE.isNamedType()).isFalse();
    assertThat(ALL_TYPE.isNullType()).isFalse();
    assertThat(ALL_TYPE.isNumber()).isFalse();
    assertThat(ALL_TYPE.isNumberObjectType()).isFalse();
    assertThat(ALL_TYPE.isNumberValueType()).isFalse();
    assertThat(ALL_TYPE.isObject()).isFalse();
    assertThat(ALL_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(ALL_TYPE.isRegexpType()).isFalse();
    assertThat(ALL_TYPE.isString()).isFalse();
    assertThat(ALL_TYPE.isStringObjectType()).isFalse();
    assertThat(ALL_TYPE.isStringValueType()).isFalse();
    assertThat(ALL_TYPE.isSymbol()).isFalse();
    assertThat(ALL_TYPE.isSymbolObjectType()).isFalse();
    assertThat(ALL_TYPE.isSymbolValueType()).isFalse();
    assertThat(ALL_TYPE.isEnumType()).isFalse();
    assertThat(ALL_TYPE.isUnionType()).isFalse();
    assertThat(ALL_TYPE.isStruct()).isFalse();
    assertThat(ALL_TYPE.isDict()).isFalse();
    assertThat(ALL_TYPE.isAllType()).isTrue();
    assertThat(ALL_TYPE.isVoidType()).isFalse();
    assertThat(ALL_TYPE.isConstructor()).isFalse();
    assertThat(ALL_TYPE.isInstanceType()).isFalse();

    // isSubtype
    assertThat(ALL_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(ALL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(recordType)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(ALL_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();

    // canBeCalled
    assertThat(ALL_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(ALL_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, functionType);
    assertCanTestForEqualityWith(ALL_TYPE, recordType);
    assertCanTestForEqualityWith(ALL_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(ALL_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(recordType)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(ALL_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isTrue();

    // isNullable
    assertThat(ALL_TYPE.isNullable()).isTrue();
    assertThat(ALL_TYPE.isVoidable()).isTrue();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE, ALL_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertThat(ALL_TYPE.matchesNumberContext()).isFalse(); // ?
    assertThat(ALL_TYPE.matchesObjectContext()).isTrue();
    assertThat(ALL_TYPE.matchesStringContext()).isTrue();
    assertThat(ALL_TYPE.matchesSymbolContext()).isFalse(); // ?

    // toString
    assertThat(ALL_TYPE.toString()).isEqualTo("*");

    assertThat(ALL_TYPE.hasDisplayName()).isTrue();
    assertThat(ALL_TYPE.getDisplayName()).isEqualTo("<Any Type>");

    Asserts.assertResolvesToSame(ALL_TYPE);
    assertThat(ALL_TYPE.isNominalConstructorOrInterface()).isFalse();
  }

  /** Tests the behavior of the Object type (the object at the top of the JavaScript hierarchy). */
  @Test
  public void testTheObjectType() {
    // implicit prototype
    assertTypeEquals(OBJECT_PROTOTYPE, OBJECT_TYPE.getImplicitPrototype());

    // isXxx
    assertThat(OBJECT_TYPE.isNoObjectType()).isFalse();
    assertThat(OBJECT_TYPE.isNoType()).isFalse();
    assertThat(OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(OBJECT_TYPE.isDateType()).isFalse();
    assertThat(OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(OBJECT_TYPE.isNullType()).isFalse();
    assertThat(OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(OBJECT_TYPE.isNullType()).isFalse();
    assertThat(OBJECT_TYPE.isNumber()).isFalse();
    assertThat(OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(OBJECT_TYPE.isObject()).isTrue();
    assertThat(OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(OBJECT_TYPE.isString()).isFalse();
    assertThat(OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(OBJECT_TYPE.isSymbol()).isFalse();
    assertThat(OBJECT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(OBJECT_TYPE.isSymbolValueType()).isFalse();
    assertThat(OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(OBJECT_TYPE.isStruct()).isFalse();
    assertThat(OBJECT_TYPE.isDict()).isFalse();
    assertThat(OBJECT_TYPE.isAllType()).isFalse();
    assertThat(OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(OBJECT_TYPE.isInstanceType()).isTrue();

    // isSubtype
    assertThat(OBJECT_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(recordType)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();

    // canBeCalled
    assertThat(OBJECT_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, STRING_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(OBJECT_TYPE, recordType);
    assertCannotTestForEqualityWith(OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(OBJECT_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(recordType)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // isNullable
    assertThat(OBJECT_TYPE.isNullable()).isFalse();
    assertThat(OBJECT_TYPE.isVoidable()).isFalse();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        OBJECT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(OBJECT_TYPE, OBJECT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(OBJECT_TYPE, SYMBOL_TYPE), OBJECT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createUnionType(OBJECT_TYPE, NUMBER_TYPE),
        OBJECT_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(OBJECT_TYPE,
        OBJECT_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertThat(OBJECT_TYPE.getPropertiesCount()).isEqualTo(7);
    assertReturnTypeEquals(OBJECT_TYPE, OBJECT_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        OBJECT_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        OBJECT_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(UNKNOWN_TYPE,
        OBJECT_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("hasOwnProperty"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("isPrototypeOf"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        OBJECT_TYPE.getPropertyType("propertyIsEnumerable"));

    // matchesXxx
    assertThat(OBJECT_TYPE.matchesNumberContext()).isFalse();
    assertThat(OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(OBJECT_TYPE.matchesSymbolContext()).isFalse();

    // implicit prototype
    assertTypeEquals(OBJECT_PROTOTYPE, OBJECT_TYPE.getImplicitPrototype());

    // toString
    assertThat(OBJECT_TYPE.toString()).isEqualTo("Object");

    assertThat(OBJECT_TYPE.isNativeObjectType()).isTrue();
    assertThat(OBJECT_TYPE.getImplicitPrototype().isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(OBJECT_TYPE);
    assertThat(OBJECT_TYPE.isNominalConstructorOrInterface()).isFalse();
    assertThat(OBJECT_TYPE.getConstructor().isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the number value type. */
  @Test
  public void testNumberObjectType() {
    // isXxx
    assertThat(NUMBER_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isBooleanObjectType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isNumber()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isNumberObjectType()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isObject()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isString()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSymbol()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSymbolValueType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isDict()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isInstanceType()).isTrue();

    // autoboxesTo
    assertTypeEquals(NUMBER_OBJECT_TYPE, NUMBER_TYPE.autoboxesTo());

    // isSubtype
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(createUnionType(NUMBER_OBJECT_TYPE, NULL_TYPE)))
        .isTrue();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(createUnionType(NUMBER_TYPE, NULL_TYPE))).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();

    // canBeCalled
    assertThat(NUMBER_OBJECT_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, elementsType);
    assertCannotTestForEqualityWith(NUMBER_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NUMBER_OBJECT_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(NUMBER_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(NUMBER_OBJECT_TYPE.isVoidable()).isFalse();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        NUMBER_OBJECT_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, STRING_OBJECT_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_OBJECT_TYPE, SYMBOL_OBJECT_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_OBJECT_TYPE, SYMBOL_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, NUMBER_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, functionType),
        NUMBER_OBJECT_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE,
        NUMBER_OBJECT_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, DATE_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(NUMBER_OBJECT_TYPE, REGEXP_TYPE),
        NUMBER_OBJECT_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertThat(NUMBER_OBJECT_TYPE.matchesNumberContext()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(NUMBER_OBJECT_TYPE.toString()).isEqualTo("Number");
    assertThat(NUMBER_OBJECT_TYPE.hasDisplayName()).isTrue();
    assertThat(NUMBER_OBJECT_TYPE.getDisplayName()).isEqualTo("Number");

    assertThat(NUMBER_OBJECT_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(NUMBER_OBJECT_TYPE);
  }

  /** Tests the behavior of the number value type. */
  @Test
  public void testNumberValueType() {
    // isXxx
    assertThat(NUMBER_TYPE.isArrayType()).isFalse();
    assertThat(NUMBER_TYPE.isBooleanObjectType()).isFalse();
    assertThat(NUMBER_TYPE.isBooleanValueType()).isFalse();
    assertThat(NUMBER_TYPE.isDateType()).isFalse();
    assertThat(NUMBER_TYPE.isEnumElementType()).isFalse();
    assertThat(NUMBER_TYPE.isNamedType()).isFalse();
    assertThat(NUMBER_TYPE.isNullType()).isFalse();
    assertThat(NUMBER_TYPE.isNumber()).isTrue();
    assertThat(NUMBER_TYPE.isNumberObjectType()).isFalse();
    assertThat(NUMBER_TYPE.isNumberValueType()).isTrue();
    assertThat(NUMBER_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(NUMBER_TYPE.isRegexpType()).isFalse();
    assertThat(NUMBER_TYPE.isString()).isFalse();
    assertThat(NUMBER_TYPE.isStringObjectType()).isFalse();
    assertThat(NUMBER_TYPE.isStringValueType()).isFalse();
    assertThat(NUMBER_TYPE.isSymbol()).isFalse();
    assertThat(NUMBER_TYPE.isSymbolObjectType()).isFalse();
    assertThat(NUMBER_TYPE.isSymbolValueType()).isFalse();
    assertThat(NUMBER_TYPE.isEnumType()).isFalse();
    assertThat(NUMBER_TYPE.isUnionType()).isFalse();
    assertThat(NUMBER_TYPE.isStruct()).isFalse();
    assertThat(NUMBER_TYPE.isDict()).isFalse();
    assertThat(NUMBER_TYPE.isAllType()).isFalse();
    assertThat(NUMBER_TYPE.isVoidType()).isFalse();
    assertThat(NUMBER_TYPE.isConstructor()).isFalse();
    assertThat(NUMBER_TYPE.isInstanceType()).isFalse();

    // autoboxesTo
    assertTypeEquals(NUMBER_OBJECT_TYPE, NUMBER_TYPE.autoboxesTo());

    // isSubtype
    assertThat(NUMBER_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(NUMBER_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(NUMBER_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(NUMBER_TYPE.isSubtypeOf(createUnionType(NUMBER_TYPE, NULL_TYPE))).isTrue();
    assertThat(NUMBER_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();

    // canBeCalled
    assertThat(NUMBER_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, BIGINT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NUMBER_TYPE, functionType);
    assertCannotTestForEqualityWith(NUMBER_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(NUMBER_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // isNullable
    assertThat(NUMBER_TYPE.isNullable()).isFalse();
    assertThat(NUMBER_TYPE.isVoidable()).isFalse();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        NUMBER_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, STRING_OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, SYMBOL_OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, SYMBOL_TYPE), NUMBER_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, functionType),
        NUMBER_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(createUnionType(NUMBER_TYPE, OBJECT_TYPE),
        NUMBER_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, DATE_TYPE),
        NUMBER_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(NUMBER_TYPE, REGEXP_TYPE),
        NUMBER_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertThat(NUMBER_TYPE.matchesNumberContext()).isTrue();
    assertThat(NUMBER_TYPE.matchesObjectContext()).isTrue();
    assertThat(NUMBER_TYPE.matchesStringContext()).isTrue();
    assertThat(NUMBER_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(NUMBER_TYPE.toString()).isEqualTo("number");
    assertThat(NUMBER_TYPE.hasDisplayName()).isTrue();
    assertThat(NUMBER_TYPE.getDisplayName()).isEqualTo("number");

    Asserts.assertResolvesToSame(NUMBER_TYPE);
    assertThat(NUMBER_TYPE.isNominalConstructorOrInterface()).isFalse();
  }

  /** Tests the behavior of the null type. */
  @Test
  public void testNullType() {

    // isXxx
    assertThat(NULL_TYPE.isArrayType()).isFalse();
    assertThat(NULL_TYPE.isBooleanValueType()).isFalse();
    assertThat(NULL_TYPE.isDateType()).isFalse();
    assertThat(NULL_TYPE.isEnumElementType()).isFalse();
    assertThat(NULL_TYPE.isNamedType()).isFalse();
    assertThat(NULL_TYPE.isNullType()).isTrue();
    assertThat(NULL_TYPE.isNumber()).isFalse();
    assertThat(NULL_TYPE.isNumberObjectType()).isFalse();
    assertThat(NULL_TYPE.isNumberValueType()).isFalse();
    assertThat(NULL_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(NULL_TYPE.isRegexpType()).isFalse();
    assertThat(NULL_TYPE.isString()).isFalse();
    assertThat(NULL_TYPE.isStringObjectType()).isFalse();
    assertThat(NULL_TYPE.isStringValueType()).isFalse();
    assertThat(NULL_TYPE.isSymbol()).isFalse();
    assertThat(NULL_TYPE.isSymbolObjectType()).isFalse();
    assertThat(NULL_TYPE.isSymbolValueType()).isFalse();
    assertThat(NULL_TYPE.isEnumType()).isFalse();
    assertThat(NULL_TYPE.isUnionType()).isFalse();
    assertThat(NULL_TYPE.isStruct()).isFalse();
    assertThat(NULL_TYPE.isDict()).isFalse();
    assertThat(NULL_TYPE.isAllType()).isFalse();
    assertThat(NULL_TYPE.isVoidType()).isFalse();
    assertThat(NULL_TYPE.isConstructor()).isFalse();
    assertThat(NULL_TYPE.isInstanceType()).isFalse();

    // autoboxesTo
    assertThat(NULL_TYPE.autoboxesTo()).isNull();

    // isSubtype
    assertThat(NULL_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(NULL_TYPE)).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(NULL_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();
    assertType(NULL_TYPE).isNotSubtypeOf(forwardDeclaredNamedType);

    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(NO_OBJECT_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(NO_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(NULL_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(ALL_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(STRING_OBJECT_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(SYMBOL_OBJECT_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(SYMBOL_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(NUMBER_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(functionType))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(OBJECT_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(DATE_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(REGEXP_TYPE))).isTrue();
    assertThat(NULL_TYPE.isSubtypeOf(createNullableType(ARRAY_TYPE))).isTrue();

    // canBeCalled
    assertThat(NULL_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(NULL_TYPE, NO_TYPE);
    assertCanTestForEqualityWith(NULL_TYPE, NO_OBJECT_TYPE);
    assertCanTestForEqualityWith(NULL_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, ARRAY_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, BOOLEAN_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, functionType);
    assertCannotTestForEqualityWith(NULL_TYPE, NULL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, NUMBER_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, REGEXP_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // canTestForShallowEqualityWith
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isTrue();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(NULL_TYPE.canTestForShallowEqualityWith(createNullableType(STRING_OBJECT_TYPE)))
        .isTrue();

    // getLeastSupertype
    assertTypeEquals(NULL_TYPE, NULL_TYPE.getLeastSupertype(NULL_TYPE));
    assertTypeEquals(ALL_TYPE, NULL_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createNullableType(STRING_OBJECT_TYPE),
        NULL_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(
        createNullableType(SYMBOL_OBJECT_TYPE), NULL_TYPE.getLeastSupertype(SYMBOL_OBJECT_TYPE));
    assertTypeEquals(createNullableType(SYMBOL_TYPE), NULL_TYPE.getLeastSupertype(SYMBOL_TYPE));
    assertTypeEquals(createNullableType(NUMBER_TYPE),
        NULL_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createNullableType(functionType),
        NULL_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(createNullableType(OBJECT_TYPE),
        NULL_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createNullableType(DATE_TYPE),
        NULL_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createNullableType(REGEXP_TYPE),
        NULL_TYPE.getLeastSupertype(REGEXP_TYPE));

    // matchesXxx
    assertThat(NULL_TYPE.matchesNumberContext()).isTrue();
    assertThat(NULL_TYPE.matchesObjectContext()).isFalse();
    assertThat(NULL_TYPE.matchesStringContext()).isTrue();
    assertThat(NULL_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(NULL_TYPE.toString()).isEqualTo("null");
    assertThat(NULL_TYPE.hasDisplayName()).isTrue();
    assertThat(NULL_TYPE.getDisplayName()).isEqualTo("null");

    Asserts.assertResolvesToSame(NULL_TYPE);

    // getGreatestSubtype
    assertThat(NULL_TYPE.isSubtypeOf(createUnionType(forwardDeclaredNamedType, NULL_TYPE)))
        .isTrue();
    JSType fwdDeclaredNullUnion = createUnionType(forwardDeclaredNamedType, NULL_TYPE);
    assertType(NULL_TYPE.getGreatestSubtype(fwdDeclaredNullUnion)).isEqualTo(NULL_TYPE);
    assertThat(NULL_TYPE.isNominalConstructorOrInterface()).isFalse();

    assertThat(NULL_TYPE.differsFrom(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testDateTypeX() {
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, functionType);
  }

  /** Tests the behavior of the Date type. */
  @Test
  public void testDateType() {
    // isXxx
    assertThat(DATE_TYPE.isArrayType()).isFalse();
    assertThat(DATE_TYPE.isBooleanValueType()).isFalse();
    assertThat(DATE_TYPE.isDateType()).isTrue();
    assertThat(DATE_TYPE.isEnumElementType()).isFalse();
    assertThat(DATE_TYPE.isNamedType()).isFalse();
    assertThat(DATE_TYPE.isNullType()).isFalse();
    assertThat(DATE_TYPE.isNumberValueType()).isFalse();
    assertThat(DATE_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(DATE_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(DATE_TYPE.isRegexpType()).isFalse();
    assertThat(DATE_TYPE.isStringValueType()).isFalse();
    assertThat(DATE_TYPE.isSymbolValueType()).isFalse();
    assertThat(DATE_TYPE.isEnumType()).isFalse();
    assertThat(DATE_TYPE.isUnionType()).isFalse();
    assertThat(DATE_TYPE.isStruct()).isFalse();
    assertThat(DATE_TYPE.isDict()).isFalse();
    assertThat(DATE_TYPE.isAllType()).isFalse();
    assertThat(DATE_TYPE.isVoidType()).isFalse();
    assertThat(DATE_TYPE.isConstructor()).isFalse();
    assertThat(DATE_TYPE.isInstanceType()).isTrue();

    // autoboxesTo
    assertThat(DATE_TYPE.autoboxesTo()).isNull();

    // isSubtype
    assertThat(DATE_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // canBeCalled
    assertThat(DATE_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(DATE_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(DATE_TYPE, SYMBOL_OBJECT_TYPE); // fix this
    assertCannotTestForEqualityWith(DATE_TYPE, SYMBOL_TYPE); // fix this
    assertCanTestForEqualityWith(DATE_TYPE, functionType);
    assertCannotTestForEqualityWith(DATE_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(DATE_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isTrue();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(DATE_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(DATE_TYPE.isNullable()).isFalse();
    assertThat(DATE_TYPE.isVoidable()).isFalse();
    assertThat(createNullableType(DATE_TYPE).isNullable()).isTrue();
    assertThat(createUnionType(DATE_TYPE, VOID_TYPE).isVoidable()).isTrue();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        DATE_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, STRING_OBJECT_TYPE),
        DATE_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, NUMBER_TYPE),
        DATE_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, functionType),
        DATE_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, DATE_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(DATE_TYPE, DATE_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, REGEXP_TYPE),
        DATE_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertThat(DATE_TYPE.getImplicitPrototype().getPropertiesCount()).isEqualTo(46);
    assertThat(DATE_TYPE.getPropertiesCount()).isEqualTo(46);
    assertReturnTypeEquals(DATE_TYPE, DATE_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toDateString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toTimeString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleDateString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toLocaleTimeString"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getTime"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMonth"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getDate"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCDate"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getDay"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getUTCDay"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("getHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getUTCMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("getTimezoneOffset"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setTime"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMilliseconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCSeconds"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMinutes"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setHours"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCHours"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setDate"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCDate"));
    assertReturnTypeEquals(NUMBER_TYPE, DATE_TYPE.getPropertyType("setMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCMonth"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setFullYear"));
    assertReturnTypeEquals(NUMBER_TYPE,
        DATE_TYPE.getPropertyType("setUTCFullYear"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toUTCString"));
    assertReturnTypeEquals(STRING_TYPE,
        DATE_TYPE.getPropertyType("toGMTString"));

    // matchesXxx
    assertThat(DATE_TYPE.matchesNumberContext()).isTrue();
    assertThat(DATE_TYPE.matchesObjectContext()).isTrue();
    assertThat(DATE_TYPE.matchesStringContext()).isTrue();
    assertThat(DATE_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(DATE_TYPE.toString()).isEqualTo("Date");
    assertThat(DATE_TYPE.hasDisplayName()).isTrue();
    assertThat(DATE_TYPE.getDisplayName()).isEqualTo("Date");

    assertThat(DATE_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(DATE_TYPE);
    assertThat(DATE_TYPE.isNominalConstructorOrInterface()).isFalse();
    assertThat(DATE_TYPE.getConstructor().isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the RegExp type. */
  @Test
  public void testRegExpType() {
    // isXxx
    assertThat(REGEXP_TYPE.isNoType()).isFalse();
    assertThat(REGEXP_TYPE.isNoObjectType()).isFalse();
    assertThat(REGEXP_TYPE.isArrayType()).isFalse();
    assertThat(REGEXP_TYPE.isBooleanValueType()).isFalse();
    assertThat(REGEXP_TYPE.isDateType()).isFalse();
    assertThat(REGEXP_TYPE.isEnumElementType()).isFalse();
    assertThat(REGEXP_TYPE.isNamedType()).isFalse();
    assertThat(REGEXP_TYPE.isNullType()).isFalse();
    assertThat(REGEXP_TYPE.isNumberValueType()).isFalse();
    assertThat(REGEXP_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(REGEXP_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(REGEXP_TYPE.isRegexpType()).isTrue();
    assertThat(REGEXP_TYPE.isStringValueType()).isFalse();
    assertThat(REGEXP_TYPE.isSymbolValueType()).isFalse();
    assertThat(REGEXP_TYPE.isEnumType()).isFalse();
    assertThat(REGEXP_TYPE.isUnionType()).isFalse();
    assertThat(REGEXP_TYPE.isStruct()).isFalse();
    assertThat(REGEXP_TYPE.isDict()).isFalse();
    assertThat(REGEXP_TYPE.isAllType()).isFalse();
    assertThat(REGEXP_TYPE.isVoidType()).isFalse();

    // autoboxesTo
    assertThat(REGEXP_TYPE.autoboxesTo()).isNull();

    // isSubtype
    assertThat(REGEXP_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // canBeCalled
    assertThat(REGEXP_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(REGEXP_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(REGEXP_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(REGEXP_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, functionType);
    assertCannotTestForEqualityWith(REGEXP_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(REGEXP_TYPE, ARRAY_TYPE);

    // canTestForShallowEqualityWith
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(REGEXP_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(REGEXP_TYPE.isNullable()).isFalse();
    assertThat(REGEXP_TYPE.isVoidable()).isFalse();
    assertThat(createNullableType(REGEXP_TYPE).isNullable()).isTrue();
    assertThat(createUnionType(REGEXP_TYPE, VOID_TYPE).isVoidable()).isTrue();

    // getLeastSupertype
    assertTypeEquals(ALL_TYPE,
        REGEXP_TYPE.getLeastSupertype(ALL_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, STRING_OBJECT_TYPE),
        REGEXP_TYPE.getLeastSupertype(STRING_OBJECT_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, NUMBER_TYPE),
        REGEXP_TYPE.getLeastSupertype(NUMBER_TYPE));
    assertTypeEquals(createUnionType(REGEXP_TYPE, functionType),
        REGEXP_TYPE.getLeastSupertype(functionType));
    assertTypeEquals(OBJECT_TYPE, REGEXP_TYPE.getLeastSupertype(OBJECT_TYPE));
    assertTypeEquals(createUnionType(DATE_TYPE, REGEXP_TYPE),
        REGEXP_TYPE.getLeastSupertype(DATE_TYPE));
    assertTypeEquals(REGEXP_TYPE,
        REGEXP_TYPE.getLeastSupertype(REGEXP_TYPE));

    // getPropertyType
    assertThat(REGEXP_TYPE.getImplicitPrototype().getPropertiesCount()).isEqualTo(9);
    assertThat(REGEXP_TYPE.getPropertiesCount()).isEqualTo(14);
    assertReturnTypeEquals(REGEXP_TYPE, REGEXP_TYPE.getPropertyType("constructor"));
    assertReturnTypeEquals(createNullableType(ARRAY_TYPE),
        REGEXP_TYPE.getPropertyType("exec"));
    assertReturnTypeEquals(BOOLEAN_TYPE,
        REGEXP_TYPE.getPropertyType("test"));
    assertReturnTypeEquals(STRING_TYPE,
        REGEXP_TYPE.getPropertyType("toString"));
    assertTypeEquals(STRING_TYPE, REGEXP_TYPE.getPropertyType("source"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("global"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("ignoreCase"));
    assertTypeEquals(BOOLEAN_TYPE, REGEXP_TYPE.getPropertyType("multiline"));
    assertTypeEquals(NUMBER_TYPE, REGEXP_TYPE.getPropertyType("lastIndex"));

    // matchesXxx
    assertThat(REGEXP_TYPE.matchesNumberContext()).isFalse();
    assertThat(REGEXP_TYPE.matchesObjectContext()).isTrue();
    assertThat(REGEXP_TYPE.matchesStringContext()).isTrue();
    assertThat(REGEXP_TYPE.matchesSymbolContext()).isFalse();

    // toString
    assertThat(REGEXP_TYPE.toString()).isEqualTo("RegExp");
    assertThat(REGEXP_TYPE.hasDisplayName()).isTrue();
    assertThat(REGEXP_TYPE.getDisplayName()).isEqualTo("RegExp");

    assertThat(REGEXP_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(REGEXP_TYPE);
    assertThat(REGEXP_TYPE.isNominalConstructorOrInterface()).isFalse();
    assertThat(REGEXP_TYPE.getConstructor().isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the string object type. */
  @Test
  public void testStringObjectType() {
    // isXxx
    assertThat(STRING_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isBooleanObjectType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isNumber()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(STRING_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isString()).isTrue();
    assertThat(STRING_OBJECT_TYPE.isStringObjectType()).isTrue();
    assertThat(STRING_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSymbol()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSymbolObjectType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSymbolValueType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isDict()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isInstanceType()).isTrue();

    // autoboxesTo
    assertTypeEquals(STRING_OBJECT_TYPE, STRING_TYPE.autoboxesTo());

    // isSubtype
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();

    // canBeCalled
    assertThat(STRING_OBJECT_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, STRING_TYPE);
    assertCannotTestForEqualityWith(STRING_OBJECT_TYPE, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_OBJECT_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, functionType);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(STRING_OBJECT_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(STRING_OBJECT_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // properties (ECMA-262 page 98 - 106)
    assertThat(STRING_OBJECT_TYPE.getImplicitPrototype().getPropertiesCount()).isEqualTo(24);
    assertThat(STRING_OBJECT_TYPE.getPropertiesCount()).isEqualTo(25);

    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toString"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("valueOf"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("charAt"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("charCodeAt"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("concat"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("indexOf"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("lastIndexOf"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("localeCompare"));
    assertReturnTypeEquals(createNullableType(ARRAY_TYPE),
        STRING_OBJECT_TYPE.getPropertyType("match"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("replace"));
    assertReturnTypeEquals(NUMBER_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("search"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("slice"));
    assertReturnTypeEquals(ARRAY_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("split"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("substr"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("substring"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLowerCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLocaleLowerCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toUpperCase"));
    assertReturnTypeEquals(STRING_TYPE,
        STRING_OBJECT_TYPE.getPropertyType("toLocaleUpperCase"));
    assertTypeEquals(NUMBER_TYPE, STRING_OBJECT_TYPE.getPropertyType("length"));

    // matchesXxx
    assertThat(STRING_OBJECT_TYPE.matchesNumberContext()).isTrue();
    assertThat(STRING_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(STRING_OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(STRING_OBJECT_TYPE.matchesSymbolContext()).isFalse();

    // isNullable
    assertThat(STRING_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(STRING_OBJECT_TYPE.isVoidable()).isFalse();
    assertThat(createNullableType(STRING_OBJECT_TYPE).isNullable()).isTrue();
    assertThat(createUnionType(STRING_OBJECT_TYPE, VOID_TYPE).isVoidable()).isTrue();

    assertThat(STRING_OBJECT_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(STRING_OBJECT_TYPE);

    assertThat(STRING_OBJECT_TYPE.hasDisplayName()).isTrue();
    assertThat(STRING_OBJECT_TYPE.getDisplayName()).isEqualTo("String");
    assertThat(STRING_OBJECT_TYPE.isNominalConstructorOrInterface()).isFalse();
    assertThat(STRING_OBJECT_TYPE.getConstructor().isNominalConstructorOrInterface()).isTrue();
  }

  /** Tests the behavior of the string value type. */
  @Test
  public void testStringValueType() {
    // isXxx
    assertThat(STRING_TYPE.isArrayType()).isFalse();
    assertThat(STRING_TYPE.isBooleanObjectType()).isFalse();
    assertThat(STRING_TYPE.isBooleanValueType()).isFalse();
    assertThat(STRING_TYPE.isDateType()).isFalse();
    assertThat(STRING_TYPE.isEnumElementType()).isFalse();
    assertThat(STRING_TYPE.isNamedType()).isFalse();
    assertThat(STRING_TYPE.isNullType()).isFalse();
    assertThat(STRING_TYPE.isNumber()).isFalse();
    assertThat(STRING_TYPE.isNumberObjectType()).isFalse();
    assertThat(STRING_TYPE.isNumberValueType()).isFalse();
    assertThat(STRING_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(STRING_TYPE.isRegexpType()).isFalse();
    assertThat(STRING_TYPE.isString()).isTrue();
    assertThat(STRING_TYPE.isStringObjectType()).isFalse();
    assertThat(STRING_TYPE.isStringValueType()).isTrue();
    assertThat(STRING_TYPE.isSymbol()).isFalse();
    assertThat(STRING_TYPE.isSymbolObjectType()).isFalse();
    assertThat(STRING_TYPE.isSymbolValueType()).isFalse();
    assertThat(STRING_TYPE.isEnumType()).isFalse();
    assertThat(STRING_TYPE.isUnionType()).isFalse();
    assertThat(STRING_TYPE.isStruct()).isFalse();
    assertThat(STRING_TYPE.isDict()).isFalse();
    assertThat(STRING_TYPE.isAllType()).isFalse();
    assertThat(STRING_TYPE.isVoidType()).isFalse();
    assertThat(STRING_TYPE.isConstructor()).isFalse();
    assertThat(STRING_TYPE.isInstanceType()).isFalse();

    // autoboxesTo
    assertTypeEquals(STRING_OBJECT_TYPE, STRING_TYPE.autoboxesTo());

    // isSubtype
    assertThat(STRING_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(STRING_TYPE.isSubtypeOf(STRING_TYPE)).isTrue();
    assertThat(STRING_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(STRING_TYPE.isSubtypeOf(UNKNOWN_TYPE)).isTrue();

    // canBeCalled
    assertThat(STRING_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(STRING_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, functionType);
    assertCanTestForEqualityWith(STRING_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, BIGINT_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, BOOLEAN_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, BOOLEAN_OBJECT_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, SYMBOL_TYPE);
    assertCannotTestForEqualityWith(STRING_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, ARRAY_TYPE);
    assertCanTestForEqualityWith(STRING_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isTrue();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(STRING_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // matchesXxx
    assertThat(STRING_TYPE.matchesNumberContext()).isTrue();
    assertThat(STRING_TYPE.matchesObjectContext()).isTrue();
    assertThat(STRING_TYPE.matchesStringContext()).isTrue();
    assertThat(STRING_TYPE.matchesSymbolContext()).isFalse();

    // isNullable
    assertThat(STRING_TYPE.isNullable()).isFalse();
    assertThat(STRING_TYPE.isVoidable()).isFalse();
    assertThat(createNullableType(STRING_TYPE).isNullable()).isTrue();
    assertThat(createUnionType(STRING_TYPE, VOID_TYPE).isVoidable()).isTrue();

    // toString
    assertThat(STRING_TYPE.toString()).isEqualTo("string");
    assertThat(STRING_TYPE.hasDisplayName()).isTrue();
    assertThat(STRING_TYPE.getDisplayName()).isEqualTo("string");

    // findPropertyType
    assertTypeEquals(NUMBER_TYPE, STRING_TYPE.findPropertyType("length"));
    assertThat(STRING_TYPE.findPropertyType("unknownProperty")).isNull();

    Asserts.assertResolvesToSame(STRING_TYPE);
    assertThat(STRING_TYPE.isNominalConstructorOrInterface()).isFalse();
  }

  /** Tests the behavior of the symbol type. */
  @Test
  public void testSymbolValueType() {
    // isXxx
    assertThat(SYMBOL_TYPE.isArrayType()).isFalse();
    assertThat(SYMBOL_TYPE.isBooleanObjectType()).isFalse();
    assertThat(SYMBOL_TYPE.isBooleanValueType()).isFalse();
    assertThat(SYMBOL_TYPE.isDateType()).isFalse();
    assertThat(SYMBOL_TYPE.isEnumElementType()).isFalse();
    assertThat(SYMBOL_TYPE.isNamedType()).isFalse();
    assertThat(SYMBOL_TYPE.isNullType()).isFalse();
    assertThat(SYMBOL_TYPE.isNumberObjectType()).isFalse();
    assertThat(SYMBOL_TYPE.isNumberValueType()).isFalse();
    assertThat(SYMBOL_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(SYMBOL_TYPE.isRegexpType()).isFalse();
    assertThat(SYMBOL_TYPE.isStringObjectType()).isFalse();
    assertThat(SYMBOL_TYPE.isStringValueType()).isFalse();
    assertThat(SYMBOL_TYPE.isEnumType()).isFalse();
    assertThat(SYMBOL_TYPE.isUnionType()).isFalse();
    assertThat(SYMBOL_TYPE.isStruct()).isFalse();
    assertThat(SYMBOL_TYPE.isDict()).isFalse();
    assertThat(SYMBOL_TYPE.isAllType()).isFalse();
    assertThat(SYMBOL_TYPE.isVoidType()).isFalse();
    assertThat(SYMBOL_TYPE.isConstructor()).isFalse();
    assertThat(SYMBOL_TYPE.isInstanceType()).isFalse();
    assertThat(SYMBOL_TYPE.isSymbol()).isTrue();
    assertThat(SYMBOL_TYPE.isSymbolObjectType()).isFalse();
    assertThat(SYMBOL_TYPE.isSymbolValueType()).isTrue();

    // autoboxesTo
    assertTypeEquals(SYMBOL_OBJECT_TYPE, SYMBOL_TYPE.autoboxesTo());

    // isSubtype
    assertThat(SYMBOL_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(SYMBOL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(SYMBOL_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(SYMBOL_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();

    // canBeCalled
    assertThat(SYMBOL_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(SYMBOL_TYPE, SYMBOL_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, functionType);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(SYMBOL_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isTrue();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse(); // ?
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isTrue();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(SYMBOL_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // isNullable
    assertThat(SYMBOL_TYPE.isNullable()).isFalse();
    assertThat(SYMBOL_TYPE.isVoidable()).isFalse();

    // matchesXxx
    assertThat(SYMBOL_TYPE.matchesNumberContext()).isFalse();
    assertThat(SYMBOL_TYPE.matchesObjectContext()).isTrue();
    assertThat(SYMBOL_TYPE.matchesStringContext()).isFalse();
    assertThat(SYMBOL_TYPE.matchesSymbolContext()).isTrue();

    // toString
    assertThat(SYMBOL_TYPE.toString()).isEqualTo("symbol");
    assertThat(SYMBOL_TYPE.hasDisplayName()).isTrue();
    assertThat(SYMBOL_TYPE.getDisplayName()).isEqualTo("symbol");

    Asserts.assertResolvesToSame(SYMBOL_TYPE);
  }

  /** Tests the behavior of the Symbol type. */
  @Test
  public void testSymbolObjectType() {
    // isXxx
    assertThat(SYMBOL_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isBooleanObjectType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isDict()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isInstanceType()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSymbol()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSymbolObjectType()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSymbolValueType()).isFalse();

    // isSubtype
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    // canBeCalled
    assertThat(SYMBOL_OBJECT_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(SYMBOL_OBJECT_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, functionType);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(SYMBOL_OBJECT_TYPE, OBJECT_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, DATE_TYPE);
    assertCannotTestForEqualityWith(SYMBOL_OBJECT_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(SYMBOL_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.isVoidable()).isFalse();

    // matchesXxx
    assertThat(SYMBOL_OBJECT_TYPE.matchesNumberContext()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.matchesStringContext()).isFalse();
    assertThat(SYMBOL_OBJECT_TYPE.matchesSymbolContext()).isTrue();

    // toString
    assertThat(SYMBOL_OBJECT_TYPE.toString()).isEqualTo("Symbol");
    assertThat(SYMBOL_OBJECT_TYPE.hasDisplayName()).isTrue();
    assertThat(SYMBOL_OBJECT_TYPE.getDisplayName()).isEqualTo("Symbol");

    assertThat(SYMBOL_OBJECT_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(SYMBOL_OBJECT_TYPE);
  }

  private void assertPropertyTypeDeclared(ObjectType ownerType, String prop) {
    assertThat(ownerType.isPropertyTypeDeclared(prop)).isTrue();
    assertThat(ownerType.isPropertyTypeInferred(prop)).isFalse();
  }

  private void assertPropertyTypeInferred(ObjectType ownerType, String prop) {
    assertThat(ownerType.isPropertyTypeDeclared(prop)).isFalse();
    assertThat(ownerType.isPropertyTypeInferred(prop)).isTrue();
  }

  private void assertPropertyTypeUnknown(ObjectType ownerType, String prop) {
    assertThat(ownerType.isPropertyTypeDeclared(prop)).isFalse();
    assertThat(ownerType.isPropertyTypeInferred(prop)).isFalse();
    assertThat(ownerType.getPropertyType(prop).isUnknownType()).isTrue();
  }

  private void assertReturnTypeEquals(JSType expectedReturnType,
      JSType function) {
    assertThat(function).isInstanceOf(FunctionType.class);
    assertTypeEquals(expectedReturnType,
        ((FunctionType) function).getReturnType());
  }

  /** Tests the behavior of record types. */
  @Test
  public void testRecordType() {
    // isXxx
    assertThat(recordType.isObject()).isTrue();
    assertThat(recordType.isFunctionPrototypeType()).isFalse();

    // isSubtype
    assertThat(recordType.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(recordType.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(SYMBOL_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(recordType.isSubtypeOf(UNKNOWN_TYPE)).isTrue();
    assertThat(recordType.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(recordType.isSubtypeOf(FUNCTION_TYPE)).isFalse();

    // autoboxesTo
    assertThat(recordType.autoboxesTo()).isNull();

    // canBeCalled
    assertThat(recordType.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(recordType, ALL_TYPE);
    assertCanTestForEqualityWith(recordType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, recordType);
    assertCanTestForEqualityWith(recordType, functionType);
    assertCanTestForEqualityWith(recordType, OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, NUMBER_TYPE);
    assertCanTestForEqualityWith(recordType, DATE_TYPE);
    assertCanTestForEqualityWith(recordType, REGEXP_TYPE);
    assertCanTestForEqualityWith(recordType, SYMBOL_OBJECT_TYPE);
    assertCanTestForEqualityWith(recordType, SYMBOL_TYPE);

    // canTestForShallowEqualityWith
    assertThat(recordType.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(recordType.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(recordType.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(recordType)).isTrue();
    assertThat(recordType.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(recordType.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(recordType.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(recordType.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // matchesXxx
    assertThat(recordType.matchesNumberContext()).isFalse();
    assertThat(recordType.matchesObjectContext()).isTrue();
    assertThat(recordType.matchesStringContext()).isFalse();
    assertThat(recordType.matchesSymbolContext()).isFalse();

    Asserts.assertResolvesToSame(recordType);
  }

  /** Tests the behavior of the instance of Function. */
  @Test
  public void testFunctionInstanceType() {
    FunctionType functionInst = FUNCTION_TYPE;

    // isXxx
    assertThat(functionInst.isObject()).isTrue();
    assertThat(functionInst.isFunctionPrototypeType()).isFalse();
    assertThat(functionInst.getImplicitPrototype().isFunctionPrototypeType()).isTrue();

    // isSubtype
    assertThat(functionInst.isSubtype(ALL_TYPE)).isTrue();
    assertThat(functionInst.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(SYMBOL_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(DATE_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(REGEXP_TYPE)).isFalse();
    assertThat(functionInst.isSubtype(UNKNOWN_TYPE)).isTrue();
    assertThat(functionInst.isSubtype(FUNCTION_TYPE)).isTrue();

    // autoboxesTo
    assertThat(functionInst.autoboxesTo()).isNull();

    // canBeCalled
    assertThat(functionInst.canBeCalled()).isTrue();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(functionInst, ALL_TYPE);
    assertCanTestForEqualityWith(functionInst, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(functionInst, functionInst);
    assertCanTestForEqualityWith(functionInst, OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionInst, NUMBER_TYPE);
    assertCannotTestForEqualityWith(functionInst, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionInst, SYMBOL_TYPE);
    assertCanTestForEqualityWith(functionInst, DATE_TYPE);
    assertCanTestForEqualityWith(functionInst, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(functionInst.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(functionInst.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(functionInst.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(functionInst)).isTrue();
    assertThat(functionInst.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(functionInst.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(functionInst.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(functionInst.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // matchesXxx
    assertThat(functionInst.matchesNumberContext()).isFalse();
    assertThat(functionInst.matchesObjectContext()).isTrue();
    assertThat(functionInst.matchesStringContext()).isFalse();
    assertThat(functionInst.matchesSymbolContext()).isFalse();

    // hasProperty
    assertThat(functionInst.hasProperty("prototype")).isTrue();
    assertPropertyTypeInferred(functionInst, "prototype");

    // misc
    assertTypeEquals(FUNCTION_FUNCTION_TYPE, functionInst.getConstructor());
    assertTypeEquals(FUNCTION_PROTOTYPE, functionInst.getImplicitPrototype());
    assertTypeEquals(functionInst, FUNCTION_FUNCTION_TYPE.getInstanceType());

    Asserts.assertResolvesToSame(functionInst);
  }

  /** Tests the behavior of functional types. */
  @Test
  public void testFunctionType() {
    // isXxx
    assertThat(functionType.isObject()).isTrue();
    assertThat(functionType.isFunctionPrototypeType()).isFalse();
    assertThat(functionType.getImplicitPrototype().isFunctionPrototypeType()).isTrue();

    // isSubtype
    assertThat(functionType.isSubtype(ALL_TYPE)).isTrue();
    assertThat(functionType.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(functionType.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(functionType.isSubtype(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(functionType.isSubtype(SYMBOL_TYPE)).isFalse();
    assertThat(functionType.isSubtype(DATE_TYPE)).isFalse();
    assertThat(functionType.isSubtype(REGEXP_TYPE)).isFalse();
    assertThat(functionType.isSubtype(UNKNOWN_TYPE)).isTrue();
    assertThat(functionType.isSubtype(FUNCTION_TYPE)).isTrue();

    // autoboxesTo
    assertThat(functionType.autoboxesTo()).isNull();

    // canBeCalled
    assertThat(functionType.canBeCalled()).isTrue();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(functionType, ALL_TYPE);
    assertCanTestForEqualityWith(functionType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(functionType, functionType);
    assertCanTestForEqualityWith(functionType, OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionType, NUMBER_TYPE);
    assertCanTestForEqualityWith(functionType, DATE_TYPE);
    assertCanTestForEqualityWith(functionType, REGEXP_TYPE);
    assertCannotTestForEqualityWith(functionType, SYMBOL_OBJECT_TYPE);
    assertCannotTestForEqualityWith(functionType, SYMBOL_TYPE);

    // canTestForShallowEqualityWith
    assertThat(functionType.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(functionType.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(functionType.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(functionType)).isTrue();
    assertThat(functionType.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(functionType.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(SYMBOL_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(SYMBOL_OBJECT_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(functionType.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(functionType.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // matchesXxx
    assertThat(functionType.matchesNumberContext()).isFalse();
    assertThat(functionType.matchesObjectContext()).isTrue();
    assertThat(functionType.matchesStringContext()).isFalse();
    assertThat(functionType.matchesSymbolContext()).isFalse();

    // hasProperty
    assertThat(functionType.hasProperty("prototype")).isTrue();
    assertPropertyTypeInferred(functionType, "prototype");

    Asserts.assertResolvesToSame(functionType);

    assertThat(FunctionType.builder(registry).withName("aFunctionName").build().getDisplayName())
        .isEqualTo("aFunctionName");
  }

  /** Tests the subtyping relation of record types. */
  @Test
  public void testRecordTypeSubtyping() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    assertThat(subRecordType.isSubtypeOf(recordType)).isTrue();
    assertThat(recordType.isSubtypeOf(subRecordType)).isFalse();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", OBJECT_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    JSType differentRecordType = builder.build();

    assertThat(differentRecordType.isSubtypeOf(recordType)).isFalse();
    assertThat(recordType.isSubtypeOf(differentRecordType)).isFalse();
  }

  /** Tests the subtyping relation of record types when an object has an inferred property.. */
  @Test
  public void testRecordTypeSubtypingWithInferredProperties() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", googSubBarInst, null);
    JSType record = builder.build();

    ObjectType subtypeProp = registry.createAnonymousObjectType(null);
    subtypeProp.defineInferredProperty("a", googSubSubBarInst, null);
    assertThat(subtypeProp.isSubtypeOf(record)).isTrue();
    assertThat(record.isSubtypeOf(subtypeProp)).isFalse();

    ObjectType supertypeProp = registry.createAnonymousObjectType(null);
    supertypeProp.defineInferredProperty("a", googBarInst, null);
    assertThat(supertypeProp.isSubtypeOf(record)).isFalse();
    assertThat(record.isSubtypeOf(supertypeProp)).isFalse();

    ObjectType declaredSubtypeProp = registry.createAnonymousObjectType(null);
    declaredSubtypeProp.defineDeclaredProperty("a", googSubSubBarInst, null);
    assertThat(declaredSubtypeProp.isSubtypeOf(record)).isTrue();
    assertThat(record.isSubtypeOf(declaredSubtypeProp)).isFalse();
  }

  /** Tests the getLeastSupertype method for record types. */
  @Test
  public void testRecordTypeLeastSuperType1() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType subRecordType = builder.build();

    JSType leastSupertype = recordType.getLeastSupertype(subRecordType);
    assertTypeEquals(leastSupertype, registry.createUnionType(recordType, subRecordType));
  }

  @Test
  public void testRecordTypeLeastSuperType2() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("e", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType otherRecordType = builder.build();

    assertTypeEquals(
        registry.createUnionType(recordType, otherRecordType),
        recordType.getLeastSupertype(otherRecordType));
  }

  @Test
  public void testRecordTypeLeastSuperType3() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);
    JSType otherRecordType = builder.build();

    assertTypeEquals(
        registry.createUnionType(recordType, otherRecordType),
        recordType.getLeastSupertype(otherRecordType));
  }

  @Test
  public void testRecordTypeLeastSuperType4() {
    JSType leastSupertype = recordType.getLeastSupertype(OBJECT_TYPE);
    assertTypeEquals(leastSupertype, OBJECT_TYPE);
  }

  /** Tests the getGreatestSubtype method for record types. */
  @Test
  public void testRecordTypeGreatestSubType1() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType2() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType3() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", NUMBER_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType4() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);

    JSType subRecordType = builder.build();

    JSType subtype = recordType.getGreatestSubtype(subRecordType);
    assertTypeEquals(subtype, NO_TYPE);
  }

  @Test
  public void testRecordTypeGreatestSubType5() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));

    // if Function is given a property "a" of type "string", then it's
    // a subtype of the record type {a: string}.
    FUNCTION_TYPE.defineDeclaredProperty("a", STRING_TYPE, null);
    assertTypeEquals(FUNCTION_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));
    assertTypeEquals(FUNCTION_TYPE, FUNCTION_TYPE.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType6() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("x", UNKNOWN_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));

    // if Function is given a property "x" of type "string", then it's
    // also a subtype of the record type {x: ?}.
    FUNCTION_TYPE.defineDeclaredProperty("x", STRING_TYPE, null);
    assertTypeEquals(FUNCTION_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));
    assertTypeEquals(FUNCTION_TYPE, FUNCTION_TYPE.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType7() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("x", NUMBER_TYPE, null);

    JSType recordType = builder.build();

    // if Function is given a property "x" of type "string", then it's
    // not a subtype of the record type {x: number}.
    FUNCTION_TYPE.defineDeclaredProperty("x", STRING_TYPE, null);
    assertTypeEquals(NO_OBJECT_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));
  }

  @Test
  public void testRecordTypeGreatestSubType8() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("xyz", UNKNOWN_TYPE, null);

    JSType recordType = builder.build();

    assertTypeEquals(NO_OBJECT_TYPE, recordType.getGreatestSubtype(FUNCTION_TYPE));

    // if goog.Bar is given a property "xyz" of type "string", then it's
    // also a subtype of the record type {x: ?}.
    googBar.defineDeclaredProperty("xyz", STRING_TYPE, null);

    assertTypeEquals(googBar, recordType.getGreatestSubtype(FUNCTION_TYPE));
    assertTypeEquals(googBar, FUNCTION_TYPE.getGreatestSubtype(recordType));

    ObjectType googBarInst = googBar.getInstanceType();
    assertTypeEquals(NO_OBJECT_TYPE,
                 recordType.getGreatestSubtype(googBarInst));
    assertTypeEquals(NO_OBJECT_TYPE,
                 googBarInst.getGreatestSubtype(recordType));
  }

  @Test
  public void testRecordTypeGreatestSubType9() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType1.getGreatestSubtype(recordType2);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType10() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();


    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getInstanceType(), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType11() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", createUnionType(NUMBER_TYPE, STRING_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", createUnionType(NUMBER_TYPE, BOOLEAN_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", NUMBER_TYPE, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  @Test
  public void testRecordTypeGreatestSubType12() {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBar.getPrototype(), null);
    builder.addProperty("e", STRING_TYPE, null);

    JSType recordType1 = builder.build();

    FunctionType googBarArgConstructor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "barArg", null, registry.createParameters(googBar), null, null, false));

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", googBarArgConstructor, null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    JSType recordType2 = builder.build();

    JSType subtype = recordType2.getGreatestSubtype(recordType1);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("d", registry.getNativeObjectType(
        JSTypeNative.NO_OBJECT_TYPE), null);
    builder.addProperty("e", STRING_TYPE, null);
    builder.addProperty("f", STRING_TYPE, null);

    assertTypeEquals(subtype, builder.build());
  }

  /** Tests the "apply" method on the function type. */
  @Test
  public void testApplyOfDateMethod() {
    JSType applyType = dateMethod.getPropertyType("apply");
    assertWithMessage("apply should be a function")
        .that(applyType instanceof FunctionType)
        .isTrue();

    FunctionType applyFn = (FunctionType) applyType;
    assertTypeEquals("apply should have the same return type as its function",
        NUMBER_TYPE, applyFn.getReturnType());

    ImmutableList<FunctionType.Parameter> params = applyFn.getParameters();
    assertWithMessage("apply takes two args").that(params).hasSize(2);
    assertTypeEquals(
        "apply's first arg is the @this type",
        registry.createOptionalNullableType(DATE_TYPE),
        params.get(0).getJSType());
    assertTypeEquals(
        "apply's second arg is an Array",
        registry.createOptionalNullableType(OBJECT_TYPE),
        params.get(1).getJSType());
    assertWithMessage("apply's args must be optional").that(params.get(0).isOptional()).isTrue();
    assertWithMessage("apply's args must be optional").that(params.get(1).isOptional()).isTrue();
  }

  /** Tests the "call" method on the function type. */
  @Test
  public void testCallOfDateMethod() {
    JSType callType = dateMethod.getPropertyType("call");
    assertWithMessage("call should be a function").that(callType instanceof FunctionType).isTrue();

    FunctionType callFn = (FunctionType) callType;
    assertTypeEquals("call should have the same return type as its function",
        NUMBER_TYPE, callFn.getReturnType());

    ImmutableList<FunctionType.Parameter> params = callFn.getParameters();
    assertWithMessage("call takes one argument in this case").that(params).hasSize(1);
    assertTypeEquals(
        "call's first arg is the @this type",
        registry.createOptionalNullableType(DATE_TYPE),
        params.get(0).getJSType());
    assertWithMessage("call's args must be optional").that(params.get(0).isOptional()).isTrue();
  }

  /** Tests the representation of function types. */
  @Test
  public void testFunctionTypeRepresentation() {
    assertThat(registry.createFunctionType(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE).toString())
        .isEqualTo("function(number, string): boolean");

    assertThat(ARRAY_FUNCTION_TYPE.toString()).isEqualTo("function(new:Array, ...*): Array");

    assertThat(BOOLEAN_OBJECT_FUNCTION_TYPE.toString())
        .isEqualTo("function(new:Boolean, *=): boolean");

    assertThat(NUMBER_OBJECT_FUNCTION_TYPE.toString())
        .isEqualTo("function(new:Number, *=): number");

    assertThat(STRING_OBJECT_FUNCTION_TYPE.toString())
        .isEqualTo("function(new:String, *=): string");

    assertThat(registry.createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE).toString())
        .isEqualTo("function(...number): boolean");

    assertThat(
            registry
                .createFunctionTypeWithVarArgs(BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE)
                .toString())
        .isEqualTo("function(number, ...string): boolean");

    assertThat(
            FunctionType.builder(registry)
                .withParameters(registry.createParameters(NUMBER_TYPE))
                .withReturnType(NUMBER_STRING_BOOLEAN)
                .withTypeOfThis(DATE_TYPE)
                .build()
                .toString())
        .isEqualTo("function(this:Date, number): (boolean|number|string)");
  }

  /** Tests relationships between structural function types. */
  @Test
  public void testFunctionTypeRelationships() {
    FunctionType dateMethodEmpty =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withTypeOfThis(DATE_TYPE)
            .build();
    FunctionType dateMethodWithParam =
        FunctionType.builder(registry)
            .withParameters(registry.createOptionalParameters(NUMBER_TYPE))
            .withTypeOfThis(DATE_TYPE)
            .build();
    FunctionType dateMethodWithReturn =
        FunctionType.builder(registry)
            .withReturnType(NUMBER_TYPE)
            .withTypeOfThis(DATE_TYPE)
            .build();
    FunctionType stringMethodEmpty =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withTypeOfThis(STRING_OBJECT_TYPE)
            .build();
    FunctionType stringMethodWithParam =
        FunctionType.builder(registry)
            .withParameters(registry.createOptionalParameters(NUMBER_TYPE))
            .withTypeOfThis(STRING_OBJECT_TYPE)
            .build();
    FunctionType stringMethodWithReturn =
        FunctionType.builder(registry)
            .withReturnType(NUMBER_TYPE)
            .withTypeOfThis(STRING_OBJECT_TYPE)
            .build();

    // One-off tests.
    assertThat(stringMethodEmpty.isSubtype(dateMethodEmpty)).isFalse();

    // Systemic tests.
    ImmutableList<FunctionType> allFunctions =
        ImmutableList.of(
            dateMethodEmpty,
            dateMethodWithParam,
            dateMethodWithReturn,
            stringMethodEmpty,
            stringMethodWithParam,
            stringMethodWithReturn);
    for (int i = 0; i < allFunctions.size(); i++) {
      for (int j = 0; j < allFunctions.size(); j++) {
        FunctionType typeA = allFunctions.get(i);
        FunctionType typeB = allFunctions.get(j);
        assertWithMessage("equals(%s, %s)", typeA, typeB)
            .that(typeA.equals(typeB))
            .isEqualTo(i == j);

        // For this particular set of functions, the functions are subtypes
        // of each other iff they have the same "this" type.
        assertWithMessage("isSubtype(%s, %s)", typeA, typeB)
            .that(typeA.isSubtype(typeB))
            .isEqualTo(typeA.getTypeOfThis().equals(typeB.getTypeOfThis()));

        if (i == j) {
          assertTypeEquals(typeA, typeA.getLeastSupertype(typeB));
          assertTypeEquals(typeA, typeA.getGreatestSubtype(typeB));
        } else {
          assertTypeEquals(
              String.format("sup(%s, %s)", typeA, typeB),
              FUNCTION_TYPE,
              typeA.getLeastSupertype(typeB));
          assertTypeEquals(String.format("inf(%s, %s)", typeA, typeB),
              LEAST_FUNCTION_TYPE, typeA.getGreatestSubtype(typeB));
        }
      }
    }
  }

  @Test
  public void testProxiedFunctionTypeRelationships() {
    FunctionType dateMethodEmpty =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withTypeOfThis(DATE_TYPE)
            .build()
            .toMaybeFunctionType();
    FunctionType dateMethodWithParam =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters(NUMBER_TYPE))
            .withTypeOfThis(DATE_TYPE)
            .build()
            .toMaybeFunctionType();
    ProxyObjectType proxyDateMethodEmpty =
        new ProxyObjectType(registry, dateMethodEmpty);
    ProxyObjectType proxyDateMethodWithParam =
        new ProxyObjectType(registry, dateMethodWithParam);

    assertTypeEquals(
        FUNCTION_TYPE, proxyDateMethodEmpty.getLeastSupertype(proxyDateMethodWithParam));
    assertTypeEquals(LEAST_FUNCTION_TYPE,
        proxyDateMethodEmpty.getGreatestSubtype(proxyDateMethodWithParam));
  }

  /** Tests relationships between structural function types. */
  @Test
  public void testFunctionSubTypeRelationships() {
    FunctionType googBarMethod = FunctionType.builder(registry).withTypeOfThis(googBar).build();
    FunctionType googBarParamFn =
        FunctionType.builder(registry).withParameters(registry.createParameters(googBar)).build();
    FunctionType googBarReturnFn =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withReturnType(googBar)
            .build();
    FunctionType googSubBarMethod =
        FunctionType.builder(registry).withTypeOfThis(googSubBar).build();
    FunctionType googSubBarParamFn =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters(googSubBar))
            .build();
    FunctionType googSubBarReturnFn =
        FunctionType.builder(registry).withReturnType(googSubBar).build();

    assertThat(googBarMethod.isSubtype(googSubBarMethod)).isTrue();
    assertThat(googBarReturnFn.isSubtype(googSubBarReturnFn)).isTrue();

    ImmutableList<FunctionType> allFunctions =
        ImmutableList.of(
            googBarMethod,
            googBarParamFn,
            googBarReturnFn,
            googSubBarMethod,
            googSubBarParamFn,
            googSubBarReturnFn);
    for (int i = 0; i < allFunctions.size(); i++) {
      for (int j = 0; j < allFunctions.size(); j++) {
        FunctionType typeA = allFunctions.get(i);
        FunctionType typeB = allFunctions.get(j);
        assertWithMessage("equals(%s, %s)", typeA, typeB)
            .that(typeA.equals(typeB))
            .isEqualTo(i == j);

        // TODO(nicksantos): This formulation of least subtype and greatest
        // supertype is a bit loose. We might want to tighten it up later.
        if (i == j) {
          assertTypeEquals(typeA, typeA.getLeastSupertype(typeB));
          assertTypeEquals(typeA, typeA.getGreatestSubtype(typeB));
        } else {
          assertTypeEquals(
              String.format("sup(%s, %s)", typeA, typeB),
              FUNCTION_TYPE,
              typeA.getLeastSupertype(typeB));
          assertTypeEquals(String.format("inf(%s, %s)", typeA, typeB),
              LEAST_FUNCTION_TYPE, typeA.getGreatestSubtype(typeB));
        }
      }
    }
  }

  /**
   * Tests that defining a property of a function's {@code prototype} adds the property to it
   * instance type.
   */
  @Test
  public void testFunctionPrototypeAndImplicitPrototype1() {

    FunctionType constructor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "Foo", /* source= */ null, null, null, null, /* isAbstract= */ false));
    ObjectType instance = constructor.getInstanceType();

    // adding one property on the prototype
    ObjectType prototype =
        (ObjectType) constructor.getPropertyType("prototype");
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);

    assertThat(instance.getPropertiesCount()).isEqualTo(NATIVE_PROPERTIES_COUNT + 1);
  }

  /**
   * Tests that replacing a function's {@code prototype} changes the visible properties of its
   * instance type.
   */
  @Test
  public void testFunctionPrototypeAndImplicitPrototype2() {

    FunctionType constructor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "Bar",
                    /* source= */ null,
                    registry.createParameters(ALL_TYPE, ALL_TYPE, ALL_TYPE),
                    null,
                    null,
                    /* isAbstract= */ false));
    ObjectType instance = constructor.getInstanceType();

    // replacing the prototype
    ObjectType prototype = registry.createAnonymousObjectType(null);
    prototype.defineDeclaredProperty("foo", DATE_TYPE, null);
    constructor.defineDeclaredProperty("prototype", prototype, null);

    assertThat(instance.getPropertiesCount()).isEqualTo(NATIVE_PROPERTIES_COUNT + 1);
  }

  /** Tests assigning JsDoc on a prototype property. */
  @Test
  public void testJSDocOnPrototypeProperty() {
    subclassCtor.setPropertyJSDocInfo("prototype", JSDocInfo.builder().build());
    assertThat(subclassCtor.getOwnPropertyJSDocInfo("prototype")).isNull();
  }

  /** Tests operation of {@code isVoidable}. */
  @Test
  public void testIsVoidable() {
    assertThat(VOID_TYPE.isVoidable()).isTrue();
    assertThat(NULL_TYPE.isVoidable()).isFalse();
    assertThat(createUnionType(NUMBER_TYPE, VOID_TYPE).isVoidable()).isTrue();
  }

  /** Tests operation of {@code isBigIntOrNumber}. */
  @Test
  public void testIsBigIntOrNumber() {
    assertThat(BIGINT_TYPE.isBigIntOrNumber()).isTrue();
    assertThat(BIGINT_OBJECT_TYPE.isBigIntOrNumber()).isTrue();
    // testing {bigint,number}
    assertThat(BIGINT_NUMBER.isBigIntOrNumber()).isTrue();
    // testing {BigInt,Number}
    assertThat(BIGINT_NUMBER_OBJECT.isBigIntOrNumber()).isTrue();
    assertThat(BIGINT_NUMBER_STRING.isBigIntOrNumber()).isFalse();
    // This check is designed to return false when mixing object and primitive types, e.g.
    // (bigint|BigInt). Even though it should technically be true, these situation are usually a
    // result of user error, so we want to report them when encountered.
    assertThat(createUnionType(BIGINT_TYPE, BIGINT_OBJECT_TYPE).isBigIntOrNumber()).isFalse();
  }

  /** Tests the behavior of the void type. */
  @Test
  public void testVoidType() {
    // isSubtype
    assertThat(VOID_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(VOID_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();

    // autoboxesTo
    assertThat(VOID_TYPE.autoboxesTo()).isNull();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(VOID_TYPE, ALL_TYPE);
    assertCannotTestForEqualityWith(VOID_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isTrue();
    assertThat(VOID_TYPE.canTestForShallowEqualityWith(createUnionType(NUMBER_TYPE, VOID_TYPE)))
        .isTrue();

    // matchesXxx
    assertThat(VOID_TYPE.matchesNumberContext()).isFalse();
    assertThat(VOID_TYPE.matchesNumberContext()).isFalse();
    assertThat(VOID_TYPE.matchesObjectContext()).isFalse();
    assertThat(VOID_TYPE.matchesStringContext()).isTrue();
    assertThat(VOID_TYPE.matchesNumberContext()).isFalse();

    Asserts.assertResolvesToSame(VOID_TYPE);
  }

  /** Tests the behavior of the boolean type. */
  @Test
  public void testBooleanValueType() {
    // isXxx
    assertThat(BOOLEAN_TYPE.isArrayType()).isFalse();
    assertThat(BOOLEAN_TYPE.isBooleanObjectType()).isFalse();
    assertThat(BOOLEAN_TYPE.isBooleanValueType()).isTrue();
    assertThat(BOOLEAN_TYPE.isDateType()).isFalse();
    assertThat(BOOLEAN_TYPE.isEnumElementType()).isFalse();
    assertThat(BOOLEAN_TYPE.isNamedType()).isFalse();
    assertThat(BOOLEAN_TYPE.isNullType()).isFalse();
    assertThat(BOOLEAN_TYPE.isNumberObjectType()).isFalse();
    assertThat(BOOLEAN_TYPE.isNumberValueType()).isFalse();
    assertThat(BOOLEAN_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(BOOLEAN_TYPE.isRegexpType()).isFalse();
    assertThat(BOOLEAN_TYPE.isStringObjectType()).isFalse();
    assertThat(BOOLEAN_TYPE.isStringValueType()).isFalse();
    assertThat(BOOLEAN_TYPE.isEnumType()).isFalse();
    assertThat(BOOLEAN_TYPE.isUnionType()).isFalse();
    assertThat(BOOLEAN_TYPE.isStruct()).isFalse();
    assertThat(BOOLEAN_TYPE.isDict()).isFalse();
    assertThat(BOOLEAN_TYPE.isAllType()).isFalse();
    assertThat(BOOLEAN_TYPE.isVoidType()).isFalse();
    assertThat(BOOLEAN_TYPE.isConstructor()).isFalse();
    assertThat(BOOLEAN_TYPE.isInstanceType()).isFalse();

    // autoboxesTo
    assertTypeEquals(BOOLEAN_OBJECT_TYPE, BOOLEAN_TYPE.autoboxesTo());

    // isSubtype
    assertThat(BOOLEAN_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();

    // canBeCalled
    assertThat(BOOLEAN_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(BOOLEAN_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, NUMBER_TYPE);
    assertCannotTestForEqualityWith(BOOLEAN_TYPE, functionType);
    assertCannotTestForEqualityWith(BOOLEAN_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, REGEXP_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_TYPE, UNKNOWN_TYPE);

    // canTestForShallowEqualityWith
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canTestForShallowEqualityWith(UNKNOWN_TYPE)).isTrue();

    // isNullable
    assertThat(BOOLEAN_TYPE.isNullable()).isFalse();
    assertThat(BOOLEAN_TYPE.isVoidable()).isFalse();

    // matchesXxx
    assertThat(BOOLEAN_TYPE.matchesNumberContext()).isTrue();
    assertThat(BOOLEAN_TYPE.matchesNumberContext()).isTrue();
    assertThat(BOOLEAN_TYPE.matchesObjectContext()).isTrue();
    assertThat(BOOLEAN_TYPE.matchesStringContext()).isTrue();
    assertThat(BOOLEAN_TYPE.matchesNumberContext()).isTrue();

    // toString
    assertThat(BOOLEAN_TYPE.toString()).isEqualTo("boolean");
    assertThat(BOOLEAN_TYPE.hasDisplayName()).isTrue();
    assertThat(BOOLEAN_TYPE.getDisplayName()).isEqualTo("boolean");

    Asserts.assertResolvesToSame(BOOLEAN_TYPE);
  }

  /** Tests the behavior of the Boolean type. */
  @Test
  public void testBooleanObjectType() {
    // isXxx
    assertThat(BOOLEAN_OBJECT_TYPE.isArrayType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isBooleanObjectType()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isBooleanValueType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isDateType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isEnumElementType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isNamedType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isNullType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isNumberObjectType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isNumberValueType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isFunctionPrototypeType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.getImplicitPrototype().isFunctionPrototypeType()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isRegexpType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isStringObjectType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isStringValueType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isEnumType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isUnionType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isStruct()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isDict()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isAllType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isVoidType()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isConstructor()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isInstanceType()).isTrue();

    // isSubtype
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    // canBeCalled
    assertThat(BOOLEAN_OBJECT_TYPE.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, ALL_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, NUMBER_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, functionType);
    assertCannotTestForEqualityWith(BOOLEAN_OBJECT_TYPE, VOID_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, OBJECT_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, DATE_TYPE);
    assertCanTestForEqualityWith(BOOLEAN_OBJECT_TYPE, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(BOOLEAN_OBJECT_TYPE.isNullable()).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isVoidable()).isFalse();

    // matchesXxx
    assertThat(BOOLEAN_OBJECT_TYPE.matchesNumberContext()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.matchesNumberContext()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.matchesObjectContext()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.matchesStringContext()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.matchesNumberContext()).isTrue();

    // toString
    assertThat(BOOLEAN_OBJECT_TYPE.toString()).isEqualTo("Boolean");
    assertThat(BOOLEAN_OBJECT_TYPE.hasDisplayName()).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.getDisplayName()).isEqualTo("Boolean");

    assertThat(BOOLEAN_OBJECT_TYPE.isNativeObjectType()).isTrue();

    Asserts.assertResolvesToSame(BOOLEAN_OBJECT_TYPE);
  }

  /** Tests the behavior of the enum type. */
  @Test
  public void testEnumType() {
    EnumType enumType =
        EnumType.builder(registry).setName("Enum").setElementType(NUMBER_TYPE).build();

    // isXxx
    assertThat(enumType.isArrayType()).isFalse();
    assertThat(enumType.isBooleanObjectType()).isFalse();
    assertThat(enumType.isBooleanValueType()).isFalse();
    assertThat(enumType.isDateType()).isFalse();
    assertThat(enumType.isEnumElementType()).isFalse();
    assertThat(enumType.isNamedType()).isFalse();
    assertThat(enumType.isNullType()).isFalse();
    assertThat(enumType.isNumberObjectType()).isFalse();
    assertThat(enumType.isNumberValueType()).isFalse();
    assertThat(enumType.isFunctionPrototypeType()).isFalse();
    assertThat(enumType.isRegexpType()).isFalse();
    assertThat(enumType.isStringObjectType()).isFalse();
    assertThat(enumType.isStringValueType()).isFalse();
    assertThat(enumType.isEnumType()).isTrue();
    assertThat(enumType.isUnionType()).isFalse();
    assertThat(enumType.isStruct()).isFalse();
    assertThat(enumType.isDict()).isFalse();
    assertThat(enumType.isAllType()).isFalse();
    assertThat(enumType.isVoidType()).isFalse();
    assertThat(enumType.isConstructor()).isFalse();
    assertThat(enumType.isInstanceType()).isFalse();

    // isSubtype
    assertThat(enumType.isSubtype(ALL_TYPE)).isTrue();
    assertThat(enumType.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(enumType.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(enumType.isSubtype(functionType)).isFalse();
    assertThat(enumType.isSubtype(NULL_TYPE)).isFalse();
    assertThat(enumType.isSubtype(OBJECT_TYPE)).isTrue();
    assertThat(enumType.isSubtype(DATE_TYPE)).isFalse();
    assertThat(enumType.isSubtype(unresolvedNamedType)).isTrue();
    assertThat(enumType.isSubtype(namedGoogBar)).isFalse();
    assertThat(enumType.isSubtype(REGEXP_TYPE)).isFalse();

    // canBeCalled
    assertThat(enumType.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(enumType, ALL_TYPE);
    assertCanTestForEqualityWith(enumType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(enumType, NUMBER_TYPE);
    assertCanTestForEqualityWith(enumType, functionType);
    assertCannotTestForEqualityWith(enumType, VOID_TYPE);
    assertCanTestForEqualityWith(enumType, OBJECT_TYPE);
    assertCanTestForEqualityWith(enumType, DATE_TYPE);
    assertCanTestForEqualityWith(enumType, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(enumType.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(enumType.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isTrue();
    assertThat(enumType.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(enumType)).isTrue();
    assertThat(enumType.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(NUMBER_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(OBJECT_TYPE)).isTrue();
    assertThat(enumType.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(enumType.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(enumType.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(enumType.isNullable()).isFalse();
    assertThat(enumType.isVoidable()).isFalse();

    // matchesXxx
    assertThat(enumType.matchesNumberContext()).isFalse();
    assertThat(enumType.matchesNumberContext()).isFalse();
    assertThat(enumType.matchesObjectContext()).isTrue();
    assertThat(enumType.matchesStringContext()).isTrue();
    assertThat(enumType.matchesNumberContext()).isFalse();

    // toString
    assertThat(enumType.toString()).isEqualTo("enum{Enum}");
    assertThat(enumType.hasDisplayName()).isTrue();
    assertThat(enumType.getDisplayName()).isEqualTo("Enum");

    assertThat(
            EnumType.builder(registry)
                .setName("AnotherEnum")
                .setElementType(NUMBER_TYPE)
                .build()
                .getDisplayName())
        .isEqualTo("AnotherEnum");
    assertThat(EnumType.builder(registry).setElementType(NUMBER_TYPE).build().hasDisplayName())
        .isFalse();

    Asserts.assertResolvesToSame(enumType);
  }

  /** Tests the behavior of the enum element type. */
  @Test
  public void testEnumElementType() {
    // isXxx
    assertThat(elementsType.isArrayType()).isFalse();
    assertThat(elementsType.isBooleanObjectType()).isFalse();
    assertThat(elementsType.isBooleanValueType()).isFalse();
    assertThat(elementsType.isDateType()).isFalse();
    assertThat(elementsType.isEnumElementType()).isTrue();
    assertThat(elementsType.isNamedType()).isFalse();
    assertThat(elementsType.isNullType()).isFalse();
    assertThat(elementsType.isNumberObjectType()).isFalse();
    assertThat(elementsType.isNumberValueType()).isFalse();
    assertThat(elementsType.isFunctionPrototypeType()).isFalse();
    assertThat(elementsType.isRegexpType()).isFalse();
    assertThat(elementsType.isStringObjectType()).isFalse();
    assertThat(elementsType.isStringValueType()).isFalse();
    assertThat(elementsType.isEnumType()).isFalse();
    assertThat(elementsType.isUnionType()).isFalse();
    assertThat(elementsType.isStruct()).isFalse();
    assertThat(elementsType.isDict()).isFalse();
    assertThat(elementsType.isAllType()).isFalse();
    assertThat(elementsType.isVoidType()).isFalse();
    assertThat(elementsType.isConstructor()).isFalse();
    assertThat(elementsType.isInstanceType()).isFalse();

    // isSubtype
    assertThat(elementsType.isSubtype(ALL_TYPE)).isTrue();
    assertThat(elementsType.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(elementsType.isSubtype(NUMBER_TYPE)).isTrue();
    assertThat(elementsType.isSubtype(functionType)).isFalse();
    assertThat(elementsType.isSubtype(NULL_TYPE)).isFalse();
    assertThat(elementsType.isSubtype(OBJECT_TYPE)).isFalse(); // no more autoboxing
    assertThat(elementsType.isSubtype(DATE_TYPE)).isFalse();
    assertThat(elementsType.isSubtype(unresolvedNamedType)).isTrue();
    assertThat(elementsType.isSubtype(namedGoogBar)).isFalse();
    assertThat(elementsType.isSubtype(REGEXP_TYPE)).isFalse();

    // canBeCalled
    assertThat(elementsType.canBeCalled()).isFalse();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(elementsType, ALL_TYPE);
    assertCanTestForEqualityWith(elementsType, STRING_OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, NUMBER_TYPE);
    assertCanTestForEqualityWith(elementsType, NUMBER_OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, elementsType);
    assertCannotTestForEqualityWith(elementsType, functionType);
    assertCannotTestForEqualityWith(elementsType, VOID_TYPE);
    assertCanTestForEqualityWith(elementsType, OBJECT_TYPE);
    assertCanTestForEqualityWith(elementsType, DATE_TYPE);
    assertCanTestForEqualityWith(elementsType, REGEXP_TYPE);

    // canTestForShallowEqualityWith
    assertThat(elementsType.canTestForShallowEqualityWith(NO_TYPE)).isTrue();
    assertThat(elementsType.canTestForShallowEqualityWith(NO_OBJECT_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(ARRAY_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(BOOLEAN_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(elementsType)).isTrue();
    assertThat(elementsType.canTestForShallowEqualityWith(DATE_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(functionType)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(NULL_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(NUMBER_TYPE)).isTrue();
    assertThat(elementsType.canTestForShallowEqualityWith(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(OBJECT_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(REGEXP_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(STRING_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(STRING_OBJECT_TYPE)).isFalse();
    assertThat(elementsType.canTestForShallowEqualityWith(ALL_TYPE)).isTrue();
    assertThat(elementsType.canTestForShallowEqualityWith(VOID_TYPE)).isFalse();

    // isNullable
    assertThat(elementsType.isNullable()).isFalse();
    assertThat(elementsType.isVoidable()).isFalse();

    // matchesXxx
    assertThat(elementsType.matchesNumberContext()).isTrue();
    assertThat(elementsType.matchesNumberContext()).isTrue();
    assertThat(elementsType.matchesObjectContext()).isTrue();
    assertThat(elementsType.matchesStringContext()).isTrue();
    assertThat(elementsType.matchesNumberContext()).isTrue();

    // toString
    assertThat(elementsType.toString()).isEqualTo("Enum<number>");
    assertThat(elementsType.hasDisplayName()).isTrue();
    assertThat(elementsType.getDisplayName()).isEqualTo("Enum");

    Asserts.assertResolvesToSame(elementsType);
  }

  @Test
  public void testStringEnumType() {
    EnumElementType stringEnum =
        EnumType.builder(registry)
            .setName("Enum")
            .setElementType(STRING_TYPE)
            .build()
            .getElementsType();

    assertTypeEquals(UNKNOWN_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertThat(stringEnum.hasProperty("length")).isFalse();
    assertTypeEquals(STRING_OBJECT_TYPE, stringEnum.autoboxesTo());
    assertThat(stringEnum.getConstructor()).isNull();

    Asserts.assertResolvesToSame(stringEnum);
  }

  @Test
  public void testStringObjectEnumType() {
    EnumElementType stringEnum =
        EnumType.builder(registry)
            .setName("Enum")
            .setElementType(STRING_OBJECT_TYPE)
            .build()
            .getElementsType();

    assertTypeEquals(NUMBER_TYPE, stringEnum.getPropertyType("length"));
    assertTypeEquals(NUMBER_TYPE, stringEnum.findPropertyType("length"));
    assertThat(stringEnum.hasProperty("length")).isTrue();
    assertTypeEquals(STRING_OBJECT_FUNCTION_TYPE, stringEnum.getConstructor());
  }

  /** Tests object types. */
  @Test
  public void testObjectType() {
    PrototypeObjectType objectType = PrototypeObjectType.builder(registry).build();

    // isXxx
    assertThat(objectType.isAllType()).isFalse();
    assertThat(objectType.isArrayType()).isFalse();
    assertThat(objectType.isDateType()).isFalse();
    assertThat(objectType.isFunctionPrototypeType()).isFalse();
    assertThat(OBJECT_TYPE).isSameInstanceAs(objectType.getImplicitPrototype());

    // isSubtype
    assertThat(objectType.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(objectType.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(objectType.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(objectType.isSubtypeOf(functionType)).isFalse();
    assertThat(objectType.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(objectType.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(objectType.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(objectType.isSubtypeOf(unresolvedNamedType)).isTrue();
    assertThat(objectType.isSubtypeOf(namedGoogBar)).isFalse();
    assertThat(objectType.isSubtypeOf(REGEXP_TYPE)).isFalse();

    // autoboxesTo
    assertThat(objectType.autoboxesTo()).isNull();

    // canTestForEqualityWith
    assertCanTestForEqualityWith(objectType, NUMBER_TYPE);
    assertCanTestForEqualityWith(objectType, BIGINT_TYPE);

    // matchesXxxContext
    assertThat(objectType.matchesNumberContext()).isFalse();
    assertThat(objectType.matchesNumberContext()).isFalse();
    assertThat(objectType.matchesObjectContext()).isTrue();
    assertThat(objectType.matchesStringContext()).isFalse();
    assertThat(objectType.matchesNumberContext()).isFalse();

    // isNullable
    assertThat(objectType.isNullable()).isFalse();
    assertThat(objectType.isVoidable()).isFalse();
    assertThat(createNullableType(objectType).isNullable()).isTrue();
    assertThat(createUnionType(objectType, VOID_TYPE).isVoidable()).isTrue();

    // toString
    assertThat(objectType.toString()).isEqualTo("{...}");
    assertThat(objectType.getDisplayName()).isNull();
    assertType(objectType).getReferenceNameIsNull();
    assertThat(PrototypeObjectType.builder(registry).setName("anObject").build().getDisplayName())
        .isEqualTo("anObject");

    Asserts.assertResolvesToSame(objectType);
  }

  /** Tests the goog.Bar type. */
  @Test
  public void testGoogBar() {
    assertThat(namedGoogBar.isInstanceType()).isTrue();
    assertThat(googBar.isInstanceType()).isFalse();
    assertThat(namedGoogBar.isConstructor()).isFalse();
    assertThat(googBar.isConstructor()).isTrue();
    assertThat(googBar.getInstanceType().isInstanceType()).isTrue();
    assertThat(namedGoogBar.getConstructor().isConstructor()).isTrue();
    assertThat(namedGoogBar.getImplicitPrototype().isFunctionPrototypeType()).isTrue();

    // isSubtype
    assertTypeCanAssignToItself(googBar);
    assertTypeCanAssignToItself(namedGoogBar);
    googBar.isSubtype(namedGoogBar);
    namedGoogBar.isSubtype(googBar);
    assertTypeEquals(googBar, googBar);
    assertTypeNotEquals(googBar, googSubBar);

    Asserts.assertResolvesToSame(googBar);
    Asserts.assertResolvesToSame(googSubBar);
  }

  /** Tests how properties are counted for object types. */
  @Test
  public void testObjectTypePropertiesCount() {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", DATE_TYPE, null);
    assertThat(sup.getPropertiesCount()).isEqualTo(nativeProperties + 1);

    sup.defineDeclaredProperty("b", DATE_TYPE, null);
    assertThat(sup.getPropertiesCount()).isEqualTo(nativeProperties + 2);

    ObjectType sub = registry.createObjectType(null, sup);
    assertThat(sub.getPropertiesCount()).isEqualTo(nativeProperties + 2);
  }

  /** Tests how properties are defined. */
  @Test
  public void testDefineProperties() {
    ObjectType prototype = googBar.getPrototype();
    ObjectType instance = googBar.getInstanceType();

    assertTypeEquals(instance.getImplicitPrototype(), prototype);

    // Test declarations.
    assertThat(prototype.defineDeclaredProperty("declared", NUMBER_TYPE, null)).isTrue();
    assertThat(prototype.defineDeclaredProperty("declared", NUMBER_TYPE, null)).isFalse();
    assertThat(instance.defineDeclaredProperty("declared", NUMBER_TYPE, null)).isFalse();
    assertTypeEquals(NUMBER_TYPE, instance.getPropertyType("declared"));

    // Test inferring different types.
    assertThat(prototype.defineInferredProperty("inferred1", STRING_TYPE, null)).isTrue();
    assertThat(prototype.defineInferredProperty("inferred1", NUMBER_TYPE, null)).isTrue();
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        instance.getPropertyType("inferred1"));

    // Test inferring different types on different objects.
    assertThat(prototype.defineInferredProperty("inferred2", STRING_TYPE, null)).isTrue();
    assertThat(instance.defineInferredProperty("inferred2", NUMBER_TYPE, null)).isTrue();
    assertTypeEquals(
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        instance.getPropertyType("inferred2"));

    // Test inferring on the supertype and declaring on the subtype.
    assertThat(prototype.defineInferredProperty("prop", STRING_TYPE, null)).isTrue();
    assertThat(instance.defineDeclaredProperty("prop", NUMBER_TYPE, null)).isTrue();
    assertTypeEquals(NUMBER_TYPE, instance.getPropertyType("prop"));
    assertTypeEquals(STRING_TYPE, prototype.getPropertyType("prop"));
  }

  /** Tests that properties are correctly counted even when shadowing occurs. */
  @Test
  public void testObjectTypePropertiesCountWithShadowing() {
    ObjectType sup = registry.createAnonymousObjectType(null);
    int nativeProperties = sup.getPropertiesCount();

    sup.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertThat(sup.getPropertiesCount()).isEqualTo(nativeProperties + 1);

    ObjectType sub = registry.createObjectType(null, sup);
    sub.defineDeclaredProperty("a", OBJECT_TYPE, null);
    assertThat(sub.getPropertiesCount()).isEqualTo(nativeProperties + 1);
  }

  /** Tests the named type goog.Bar. */
  @Test
  public void testNamedGoogBar() {
    // isXxx
    assertThat(namedGoogBar.isFunctionPrototypeType()).isFalse();
    assertThat(namedGoogBar.getImplicitPrototype().isFunctionPrototypeType()).isTrue();

    // isSubtype
    assertThat(namedGoogBar.isSubtype(ALL_TYPE)).isTrue();
    assertThat(namedGoogBar.isSubtype(STRING_OBJECT_TYPE)).isFalse();
    assertThat(namedGoogBar.isSubtype(NUMBER_TYPE)).isFalse();
    assertThat(namedGoogBar.isSubtype(functionType)).isFalse();
    assertThat(namedGoogBar.isSubtype(NULL_TYPE)).isFalse();
    assertThat(namedGoogBar.isSubtype(OBJECT_TYPE)).isTrue();
    assertThat(namedGoogBar.isSubtype(DATE_TYPE)).isFalse();
    assertThat(namedGoogBar.isSubtype(namedGoogBar)).isTrue();
    assertThat(namedGoogBar.isSubtype(unresolvedNamedType)).isTrue();
    assertThat(namedGoogBar.isSubtype(REGEXP_TYPE)).isFalse();
    assertThat(namedGoogBar.isSubtype(ARRAY_TYPE)).isFalse();

    // autoboxesTo
    assertThat(namedGoogBar.autoboxesTo()).isNull();

    // properties
    assertTypeEquals(DATE_TYPE, namedGoogBar.getPropertyType("date"));

    assertThat(namedGoogBar.isNativeObjectType()).isFalse();
    assertThat(namedGoogBar.getImplicitPrototype().isNativeObjectType()).isFalse();

    JSType resolvedNamedGoogBar = Asserts.assertValidResolve(namedGoogBar);
    assertThat(namedGoogBar).isNotSameInstanceAs(resolvedNamedGoogBar);
    assertThat(googBar.getInstanceType()).isSameInstanceAs(resolvedNamedGoogBar);
  }

  /** Tests the prototype chaining of native objects. */
  @Test
  public void testPrototypeChaining() {
    // equals
    assertTypeEquals(
        ARRAY_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        BOOLEAN_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        DATE_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
    assertTypeEquals(
        NUMBER_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        STRING_OBJECT_TYPE.getImplicitPrototype().
        getImplicitPrototype(), OBJECT_TYPE);
    assertTypeEquals(
        REGEXP_TYPE.getImplicitPrototype().getImplicitPrototype(),
        OBJECT_TYPE);
  }

  /**
   * Tests that function instances have their constructor pointer back at the function that created
   * them.
   */
  @Test
  public void testInstanceFunctionChaining() {
    // Array
    assertTypeEquals(
        ARRAY_FUNCTION_TYPE, ARRAY_TYPE.getConstructor());

    // Boolean
    assertTypeEquals(
        BOOLEAN_OBJECT_FUNCTION_TYPE,
        BOOLEAN_OBJECT_TYPE.getConstructor());

    // Date
    assertTypeEquals(
        DATE_FUNCTION_TYPE, DATE_TYPE.getConstructor());

    // Number
    assertTypeEquals(
        NUMBER_OBJECT_FUNCTION_TYPE,
        NUMBER_OBJECT_TYPE.getConstructor());

    // Object
    assertTypeEquals(
        OBJECT_FUNCTION_TYPE, OBJECT_TYPE.getConstructor());

    // RegExp
    assertTypeEquals(REGEXP_FUNCTION_TYPE, REGEXP_TYPE.getConstructor());

    // String
    assertTypeEquals(
        STRING_OBJECT_FUNCTION_TYPE,
        STRING_OBJECT_TYPE.getConstructor());
  }

  /**
   * Tests that the method {@link JSType#canTestForEqualityWith(JSType)} handles special corner
   * cases.
   */
  @SuppressWarnings("checked")
  @Test
  public void testCanTestForEqualityWithCornerCases() {
    // null == undefined is always true
    assertCannotTestForEqualityWith(NULL_TYPE, VOID_TYPE);

    // (Object,null) == undefined could be true or false
    UnionType nullableObject =
        (UnionType) createUnionType(OBJECT_TYPE, NULL_TYPE);
    assertCanTestForEqualityWith(nullableObject, VOID_TYPE);
    assertCanTestForEqualityWith(VOID_TYPE, nullableObject);
  }

  /** Tests the {@link JSType#testForEquality(JSType)} method. */
  @Test
  public void testTestForEquality() {
    compare(TRUE, NO_OBJECT_TYPE, NO_OBJECT_TYPE);
    compare(UNKNOWN, ALL_TYPE, ALL_TYPE);
    compare(TRUE, NO_TYPE, NO_TYPE);
    JSType fooNoResolvedType = new NoResolvedType(registry, "Foo", null);
    compare(UNKNOWN, fooNoResolvedType, fooNoResolvedType);
    compare(UNKNOWN, NO_OBJECT_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, ALL_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, NO_TYPE, NUMBER_TYPE);

    compare(FALSE, NULL_TYPE, BOOLEAN_TYPE);
    compare(TRUE, NULL_TYPE, NULL_TYPE);
    compare(FALSE, NULL_TYPE, NUMBER_TYPE);
    compare(FALSE, NULL_TYPE, OBJECT_TYPE);
    compare(FALSE, NULL_TYPE, STRING_TYPE);
    compare(TRUE, NULL_TYPE, VOID_TYPE);
    compare(UNKNOWN, NULL_TYPE, createUnionType(UNKNOWN_TYPE, VOID_TYPE));
    compare(UNKNOWN, NULL_TYPE, createUnionType(OBJECT_TYPE, VOID_TYPE));
    compare(UNKNOWN, NULL_TYPE, unresolvedNamedType);
    compare(UNKNOWN,
        NULL_TYPE, createUnionType(unresolvedNamedType, DATE_TYPE));

    compare(FALSE, VOID_TYPE, REGEXP_TYPE);
    compare(TRUE, VOID_TYPE, VOID_TYPE);
    compare(UNKNOWN, VOID_TYPE, createUnionType(REGEXP_TYPE, VOID_TYPE));

    compare(UNKNOWN, NUMBER_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, NUMBER_TYPE, NUMBER_TYPE);
    compare(UNKNOWN, NUMBER_TYPE, OBJECT_TYPE);

    compare(UNKNOWN, ARRAY_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, OBJECT_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, OBJECT_TYPE, STRING_TYPE);

    compare(UNKNOWN, STRING_TYPE, STRING_TYPE);

    compare(UNKNOWN, STRING_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, STRING_TYPE, NUMBER_TYPE);
    compare(FALSE, STRING_TYPE, VOID_TYPE);
    compare(FALSE, STRING_TYPE, NULL_TYPE);
    compare(FALSE, STRING_TYPE, createUnionType(NULL_TYPE, VOID_TYPE));

    compare(UNKNOWN, UNKNOWN_TYPE, BOOLEAN_TYPE);
    compare(UNKNOWN, UNKNOWN_TYPE, NULL_TYPE);
    compare(UNKNOWN, UNKNOWN_TYPE, VOID_TYPE);

    compare(FALSE, FUNCTION_TYPE, BOOLEAN_TYPE);
    compare(FALSE, FUNCTION_TYPE, NUMBER_TYPE);
    compare(FALSE, FUNCTION_TYPE, STRING_TYPE);
    compare(FALSE, FUNCTION_TYPE, VOID_TYPE);
    compare(FALSE, FUNCTION_TYPE, NULL_TYPE);
    compare(UNKNOWN, FUNCTION_TYPE, OBJECT_TYPE);
    compare(UNKNOWN, FUNCTION_TYPE, ALL_TYPE);

    compare(UNKNOWN, NULL_TYPE, subclassOfUnresolvedNamedType);

    JSType functionAndNull = createUnionType(NULL_TYPE, dateMethod);
    compare(UNKNOWN, functionAndNull, dateMethod);

    compare(UNKNOWN, NULL_TYPE, NO_TYPE);
    compare(UNKNOWN, VOID_TYPE, NO_TYPE);
    compare(UNKNOWN, NULL_TYPE, unresolvedNamedType);
    compare(UNKNOWN, VOID_TYPE, unresolvedNamedType);
    compare(TRUE, NO_TYPE, NO_TYPE);
  }

  private void compare(Tri r, JSType t1, JSType t2) {
    assertThat(t1.testForEquality(t2)).isEqualTo(r);
    assertThat(t2.testForEquality(t1)).isEqualTo(r);
  }

  /** Tests the subtyping relationships among simple types. */
  @Test
  public void testSubtypingSimpleTypes() {
    // Any
    assertThat(NO_TYPE.isSubtypeOf(NO_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(functionType)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NULL_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NUMBER_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(STRING_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NO_TYPE.isSubtypeOf(VOID_TYPE)).isTrue();

    // AnyObject
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(functionType)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(NO_OBJECT_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // Array
    assertThat(ARRAY_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(ARRAY_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // boolean
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // Boolean
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(DATE_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(BOOLEAN_OBJECT_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // Date
    assertThat(DATE_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(DATE_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(DATE_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(DATE_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();

    // Unknown
    assertThat(ALL_TYPE.isSubtypeOf(NO_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NO_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(ARRAY_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(BOOLEAN_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(BOOLEAN_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(functionType)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NULL_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NUMBER_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(NUMBER_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(REGEXP_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(STRING_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(STRING_OBJECT_TYPE)).isFalse();
    assertThat(ALL_TYPE.isSubtypeOf(ALL_TYPE)).isTrue();
    assertThat(ALL_TYPE.isSubtypeOf(VOID_TYPE)).isFalse();
  }

  /** Tests that the Object type is the greatest element (top) of the object hierarchy. */
  @Test
  public void testSubtypingObjectTopOfObjects() {
    assertThat(OBJECT_TYPE.isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(createUnionType(DATE_TYPE, REGEXP_TYPE).isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(createUnionType(OBJECT_TYPE, NO_OBJECT_TYPE).isSubtypeOf(OBJECT_TYPE)).isTrue();
    assertThat(functionType.isSubtype(OBJECT_TYPE)).isTrue();
  }

  @Test
  public void testSubtypingFunctionPrototypeType() {

    FunctionType sub1 =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "Foo1",
                    /* source= */ null,
                    registry.createParameters(ALL_TYPE),
                    null,
                    null,
                    /* isAbstract= */ false));
    sub1.setPrototypeBasedOn(googBar);

    FunctionType sub2 =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "Foo2",
                    /* source= */ null,
                    registry.createParameters(ALL_TYPE),
                    null,
                    null,
                    /* isAbstract= */ false));
    sub2.setPrototypeBasedOn(googBar);

    ObjectType o1 = sub1.getInstanceType();
    ObjectType o2 = sub2.getInstanceType();

    assertThat(o1.isSubtypeOf(o2)).isFalse();
    assertThat(o1.getImplicitPrototype().isSubtypeOf(o2.getImplicitPrototype())).isFalse();
    assertThat(o1.getImplicitPrototype().isSubtypeOf(googBar)).isTrue();
    assertThat(o2.getImplicitPrototype().isSubtypeOf(googBar)).isTrue();
  }

  @Test
  public void testSubtypingFunctionFixedArgs() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, BOOLEAN_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, BOOLEAN_TYPE);

    assertThat(f1.isSubtype(f1)).isTrue();
    assertThat(f1.isSubtype(f2)).isFalse();
    assertThat(f2.isSubtype(f1)).isTrue();
    assertThat(f2.isSubtype(f2)).isTrue();

    assertThat(f1.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f1)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f2)).isTrue();
  }

  @Test
  public void testSubtypingFunctionMultipleFixedArgs() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, ALL_TYPE);

    assertThat(f1.isSubtype(f1)).isTrue();
    assertThat(f1.isSubtype(f2)).isFalse();
    assertThat(f2.isSubtype(f1)).isTrue();
    assertThat(f2.isSubtype(f2)).isTrue();

    assertThat(f1.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f1)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f2)).isTrue();
  }

  @Test
  public void testSubtypingFunctionFixedArgsNotMatching() {
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, STRING_TYPE, UNKNOWN_TYPE);
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, ALL_TYPE);

    assertThat(f1.isSubtype(f1)).isTrue();
    assertThat(f1.isSubtype(f2)).isFalse();
    assertThat(f2.isSubtype(f1)).isTrue();
    assertThat(f2.isSubtype(f2)).isTrue();

    assertThat(f1.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f1)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f2)).isTrue();
  }

  @Test
  public void testSubtypingFunctionVariableArgsOneOnly() {
    // f1 = (string...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object) -> String
    FunctionType f2 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE);

    assertThat(f1.isSubtype(f1)).isTrue();
    assertThat(f1.isSubtype(f2)).isFalse();
    assertThat(f2.isSubtype(f1)).isFalse();
    assertThat(f2.isSubtype(f2)).isTrue();

    assertThat(f1.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f1)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f2)).isTrue();
  }

  @Test
  public void testSubtypingFunctionVariableArgsBoth() {
    // f1 = (number, String, string...) -> Object
    FunctionType f1 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, NUMBER_TYPE, STRING_OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object, string...) -> String
    FunctionType f2 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE, STRING_TYPE);

    assertThat(f1.isSubtype(f1)).isTrue();
    assertThat(f1.isSubtype(f2)).isFalse();
    assertThat(f2.isSubtype(f1)).isTrue();
    assertThat(f2.isSubtype(f2)).isTrue();

    assertThat(f1.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f1)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(f2)).isTrue();
  }

  @Test
  public void testSubtypingMostGeneralFunction() {
    // (string, string) -> Object
    FunctionType f1 = registry.createFunctionType(OBJECT_TYPE, STRING_TYPE, STRING_TYPE);
    // (string, void) -> number
    FunctionType f2 = registry.createFunctionType(NUMBER_TYPE, STRING_TYPE, VOID_TYPE);
    // (Date, string, number) -> AnyObject
    FunctionType f3 = registry.createFunctionType(
        NO_OBJECT_TYPE, DATE_TYPE, STRING_TYPE, NUMBER_TYPE);
    // (Number) -> Any
    FunctionType f4 = registry.createFunctionType(NO_TYPE, NUMBER_OBJECT_TYPE);
    // f1 = (string...) -> Object
    FunctionType f5 = registry.createFunctionTypeWithVarArgs(OBJECT_TYPE, STRING_TYPE);
    // f2 = (string|number, Object) -> String
    FunctionType f6 = registry.createFunctionType(STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE);
    // f1 = (number, string...) -> Object
    FunctionType f7 = registry.createFunctionTypeWithVarArgs(
        OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
    // f2 = (string|number, Object, string...) -> String
    FunctionType f8 = registry.createFunctionTypeWithVarArgs(
        STRING_OBJECT_TYPE, NUMBER_STRING, OBJECT_TYPE, STRING_TYPE);

    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(LEAST_FUNCTION_TYPE)).isTrue();

    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(FUNCTION_TYPE)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();

    assertThat(f1.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f2.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f3.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f4.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f5.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f6.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f7.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();
    assertThat(f8.isSubtype(GREATEST_FUNCTION_TYPE)).isTrue();

    assertThat(f1.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f2.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f3.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f4.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f5.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f6.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f7.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();
    assertThat(f8.isSubtype(LEAST_FUNCTION_TYPE)).isFalse();

    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f1)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f2)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f3)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f4)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f5)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f6)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f7)).isTrue();
    assertThat(LEAST_FUNCTION_TYPE.isSubtypeOf(f8)).isTrue();

    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f1)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f2)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f3)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f4)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f5)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f6)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f7)).isFalse();
    assertThat(GREATEST_FUNCTION_TYPE.isSubtypeOf(f8)).isFalse();
  }

  /** Types to test for symmetrical relationships. */
  private ImmutableList<JSType> getTypesToTestForSymmetry() {
    return ImmutableList.of(
        UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE,
        NULL_TYPE,
        VOID_TYPE,
        NUMBER_TYPE,
        STRING_TYPE,
        BOOLEAN_TYPE,
        OBJECT_TYPE,
        FUNCTION_TYPE,
        LEAST_FUNCTION_TYPE,
        GREATEST_FUNCTION_TYPE,
        ALL_TYPE,
        NO_TYPE,
        NO_OBJECT_TYPE,
        new NoResolvedType(registry, "Foo", null),
        new NoResolvedType(registry, "Foo", ImmutableList.of(STRING_TYPE)),
        new NoResolvedType(registry, "Bar", null),
        createUnionType(new NoResolvedType(registry, "Foo", null), NULL_TYPE),
        createUnionType(BOOLEAN_TYPE, STRING_TYPE),
        createUnionType(NUMBER_TYPE, STRING_TYPE),
        createUnionType(NULL_TYPE, dateMethod),
        createUnionType(UNKNOWN_TYPE, dateMethod),
        createUnionType(namedGoogBar, dateMethod),
        createUnionType(NULL_TYPE, unresolvedNamedType),
        enumType,
        elementsType,
        dateMethod,
        functionType,
        unresolvedNamedType,
        googBar,
        namedGoogBar,
        googBar.getInstanceType(),
        namedGoogBar,
        subclassOfUnresolvedNamedType,
        subclassCtor,
        recordType,
        forwardDeclaredNamedType,
        createUnionType(forwardDeclaredNamedType, NULL_TYPE),
        createTemplatizedType(OBJECT_TYPE, STRING_TYPE),
        createTemplatizedType(OBJECT_TYPE, NUMBER_TYPE),
        createTemplatizedType(ARRAY_TYPE, STRING_TYPE),
        createTemplatizedType(ARRAY_TYPE, NUMBER_TYPE),
        createUnionType(createTemplatizedType(ARRAY_TYPE, BOOLEAN_TYPE), NULL_TYPE),
        createUnionType(createTemplatizedType(OBJECT_TYPE, BOOLEAN_TYPE), NULL_TYPE));
  }

  @Test
  public void testSymmetryOfTestForEquality() {
    ImmutableList<JSType> listA = getTypesToTestForSymmetry();
    ImmutableList<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        Tri aOnB = typeA.testForEquality(typeB);
        Tri bOnA = typeB.testForEquality(typeA);
        assertWithMessage(
                lines(
                    "testForEquality not symmetrical:",
                    "typeA: %s\ntypeB: %s",
                    "a.testForEquality(b): %s",
                    "b.testForEquality(a): %s"),
                typeA,
                typeB,
                aOnB,
                bOnA)
            .that(aOnB == bOnA)
            .isTrue();
      }
    }
  }

  /** Tests that getLeastSupertype is a symmetric relation. */
  @Test
  public void testSymmetryOfLeastSupertype() {
    ImmutableList<JSType> listA = getTypesToTestForSymmetry();
    ImmutableList<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getLeastSupertype(typeB);
        JSType bOnA = typeB.getLeastSupertype(typeA);

        // TODO(b/110226422): This should use `assertTypeEquals` but at least one of the cases
        // doesn't have matching `hashCode()` values.
        assertWithMessage(
                lines(
                    "getLeastSupertype not symmetrical:",
                    "typeA: %s",
                    "typeB: %s",
                    "a.getLeastSupertype(b): %s",
                    "b.getLeastSupertype(a): %s"),
                typeA,
                typeB,
                aOnB,
                bOnA)
            .that(aOnB.equals(bOnA))
            .isTrue();
      }
    }
  }

  @Test
  public void testWeirdBug() {
    assertTypeNotEquals(googBar, googBar.getInstanceType());
    assertThat(googBar.isSubtype(googBar.getInstanceType())).isFalse();
    assertThat(googBar.getInstanceType().isSubtypeOf(googBar)).isFalse();
  }

  /** Tests that getGreatestSubtype is a symmetric relation. */
  @Test
  public void testSymmetryOfGreatestSubtype() {
    ImmutableList<JSType> listA = getTypesToTestForSymmetry();
    ImmutableList<JSType> listB = getTypesToTestForSymmetry();
    for (JSType typeA : listA) {
      for (JSType typeB : listB) {
        JSType aOnB = typeA.getGreatestSubtype(typeB);
        JSType bOnA = typeB.getGreatestSubtype(typeA);

        // TODO(b/110226422): This should use `assertTypeEquals` but at least one of the cases
        // doesn't have matching `hashCode()` values.
        assertWithMessage(
                lines(
                    "getGreatestSubtype not symmetrical:",
                    "typeA: %s",
                    "typeB: %s",
                    "a.getGreatestSubtype(b): %s",
                    "b.getGreatestSubtype(a): %s"),
                typeA,
                typeB,
                aOnB,
                bOnA)
            .that(aOnB.equals(bOnA))
            .isTrue();
      }
    }
  }

  /** Tests that getLeastSupertype is a reflexive relation. */
  @Test
  public void testReflexivityOfLeastSupertype() {
    ImmutableList<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getLeastSupertype not reflexive",
          type, type.getLeastSupertype(type));
    }
  }

  /** Tests that getGreatestSubtype is a reflexive relation. */
  @Test
  public void testReflexivityOfGreatestSubtype() {
    ImmutableList<JSType> list = getTypesToTestForSymmetry();
    for (JSType type : list) {
      assertTypeEquals("getGreatestSubtype not reflexive",
          type, type.getGreatestSubtype(type));
    }
  }

  /** Tests {@link JSType#getLeastSupertype(JSType)} for unresolved named types. */
  @Test
  public void testLeastSupertypeUnresolvedNamedType() {
    // (undefined,function(?):?) and ? unresolved named type
    JSType expected = registry.createUnionType(unresolvedNamedType, FUNCTION_TYPE);
    assertTypeEquals(expected, unresolvedNamedType.getLeastSupertype(FUNCTION_TYPE));
    assertTypeEquals(expected, FUNCTION_TYPE.getLeastSupertype(unresolvedNamedType));
    assertThat(expected.toString()).isEqualTo("(?|Function)");
  }

  @Test
  public void testLeastSupertypeUnresolvedNamedType2() {
    JSType expected = registry.createUnionType(
        unresolvedNamedType, UNKNOWN_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(UNKNOWN_TYPE));
    assertTypeEquals(expected,
        UNKNOWN_TYPE.getLeastSupertype(unresolvedNamedType));
    assertTypeEquals(UNKNOWN_TYPE, expected);
  }

  @Test
  public void testLeastSupertypeUnresolvedNamedType3() {
    JSType expected = registry.createUnionType(
        unresolvedNamedType, CHECKED_UNKNOWN_TYPE);
    assertTypeEquals(expected,
        unresolvedNamedType.getLeastSupertype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(expected,
        CHECKED_UNKNOWN_TYPE.getLeastSupertype(unresolvedNamedType));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE, expected);
  }

  /** Tests the subclass of an unresolved named type */
  @Test
  public void testSubclassOfUnresolvedNamedType() {
    assertThat(subclassOfUnresolvedNamedType.isUnknownType()).isTrue();
  }

  /**
   * Tests that Proxied FunctionTypes behave the same over getLeastSupertype and getGreatestSubtype
   * as non proxied FunctionTypes
   */
  @Test
  public void testSupertypeOfProxiedFunctionTypes() {
    ObjectType fn1 =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withReturnType(NUMBER_TYPE)
            .build();
    ObjectType fn2 =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withReturnType(STRING_TYPE)
            .build();
    ObjectType p1 = new ProxyObjectType(registry, fn1);
    ObjectType p2 = new ProxyObjectType(registry, fn2);
    ObjectType supremum =
        FunctionType.builder(registry)
            .withParameters(registry.createParameters())
            .withReturnType(registry.createUnionType(STRING_TYPE, NUMBER_TYPE))
            .build();

    assertTypeEquals(fn1.getLeastSupertype(fn2), p1.getLeastSupertype(p2));
    assertTypeEquals(supremum, fn1.getLeastSupertype(fn2));
    assertTypeEquals(supremum, fn1.getLeastSupertype(p2));
    assertTypeEquals(supremum, p1.getLeastSupertype(fn2));
    assertTypeEquals(supremum, p1.getLeastSupertype(p2));
  }

  @Test
  public void testTypeOfThisIsProxied() {
    ObjectType fnType =
        FunctionType.builder(registry)
            .withReturnType(NUMBER_TYPE)
            .withTypeOfThis(OBJECT_TYPE)
            .build();
    ObjectType proxyType = new ProxyObjectType(registry, fnType);
    assertTypeEquals(fnType.getTypeOfThis(), proxyType.getTypeOfThis());
  }

  /** Tests the {@link NamedType#equals} function, which had a bug in it. */
  @Test
  public void testNamedTypeEquals() {
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      // test == if references are equal
      errorReporter.expectAllWarnings("Bad type annotation. Unknown type type1");
      NamedType a = registry.createNamedType(EMPTY_SCOPE, "type1", "source", 1, 0);
      NamedType b = registry.createNamedType(EMPTY_SCOPE, "type1", "source", 1, 0);
      assertThat(a.equals(b)).isTrue();

      // test == instance of referenced type
      assertThat(namedGoogBar.equals(googBar.getInstanceType())).isTrue();
      assertThat(googBar.getInstanceType().equals(namedGoogBar)).isTrue();
    }
  }

  /** Tests the {@link NamedType#equals} function against other types. */
  @Test
  @SuppressWarnings({"MustBeClosedChecker"})
  public void testNamedTypeEquals2() {
    // test == if references are equal
    JSTypeResolver.Closer closer = registry.getResolver().openForDefinition();
    NamedType a = registry.createNamedType(EMPTY_SCOPE, "typeA", "source", 1, 0);
    NamedType b = registry.createNamedType(EMPTY_SCOPE, "typeB", "source", 1, 0);

    ObjectType realA =
        registry.createConstructorType("typeA", null, null, null, null, false).getInstanceType();
    ObjectType realB =
        EnumType.builder(registry)
            .setName("typeB")
            .setElementType(NUMBER_TYPE)
            .build()
            .getElementsType();
    registry.declareType(null, "typeA", realA);
    registry.declareType(null, "typeB", realB);
    closer.close();

    assertThat(a.isResolved()).isTrue();
    assertThat(b.isResolved()).isTrue();
    assertTypeEquals(a, realA);
    assertTypeEquals(b, realB);

    JSType resolvedA = Asserts.assertValidResolve(a);
    assertThat(a).isNotSameInstanceAs(resolvedA);
    assertThat(realA).isSameInstanceAs(resolvedA);

    JSType resolvedB = Asserts.assertValidResolve(b);
    assertThat(b).isNotSameInstanceAs(resolvedB);
    assertThat(realB).isSameInstanceAs(resolvedB);
  }

  @Test
  @SuppressWarnings("MustBeClosedChecker")
  public void testMeaningOfUnresolved() {
    registry.getResolver().openForDefinition();
    // Given
    JSType underTest = new UnitTestingJSType(registry);

    // When
    // No resolution.

    // Then
    assertThat(underTest.isResolved()).isFalse();
    assertThat(underTest.isSuccessfullyResolved()).isFalse();
    assertThat(underTest.isUnsuccessfullyResolved()).isFalse();
  }

  @Test
  public void testMeaningOfSuccessfullyResolved() {
    // Given
    JSType underTest =
        new UnitTestingJSType(registry) {
          @Override
          public boolean isNoResolvedType() {
            return false;
          }

          @Override
          public JSTypeClass getTypeClass() {
            return JSTypeClass.NO;
          }
        };

    // When
    underTest.eagerlyResolveToSelf();

    // Then
    assertThat(underTest.isResolved()).isTrue();
    assertThat(underTest.isSuccessfullyResolved()).isTrue();
    assertThat(underTest.isUnsuccessfullyResolved()).isFalse();
  }

  @Test
  public void testMeaningOfUnsuccessfullyResolved() {
    // Given
    JSType underTest =
        new UnitTestingJSType(registry) {
          @Override
          public boolean isNoResolvedType() {
            return true;
          }

          @Override
          public JSTypeClass getTypeClass() {
            return JSTypeClass.NO;
          }
        };
    underTest.eagerlyResolveToSelf();

    // When


    // Then
    assertThat(underTest.isResolved()).isTrue();
    assertThat(underTest.isSuccessfullyResolved()).isFalse();
    assertThat(underTest.isUnsuccessfullyResolved()).isTrue();
  }

  /** Tests {@link JSType#getGreatestSubtype(JSType)} on simple types. */
  @Test
  public void testGreatestSubtypeSimpleTypes() {
    assertTypeEquals(ARRAY_TYPE,
        ARRAY_TYPE.getGreatestSubtype(ALL_TYPE));
    assertTypeEquals(ARRAY_TYPE,
        ALL_TYPE.getGreatestSubtype(ARRAY_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        REGEXP_TYPE.getGreatestSubtype(NO_OBJECT_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        NO_OBJECT_TYPE.getGreatestSubtype(REGEXP_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(STRING_OBJECT_TYPE));
    assertTypeEquals(NO_TYPE, ARRAY_TYPE.getGreatestSubtype(NUMBER_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(functionType));
    assertTypeEquals(STRING_OBJECT_TYPE,
        STRING_OBJECT_TYPE.getGreatestSubtype(OBJECT_TYPE));
    assertTypeEquals(STRING_OBJECT_TYPE,
        OBJECT_TYPE.getGreatestSubtype(STRING_OBJECT_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(DATE_TYPE));
    assertTypeEquals(NO_OBJECT_TYPE,
        ARRAY_TYPE.getGreatestSubtype(REGEXP_TYPE));
    assertTypeEquals(NO_TYPE,
        NULL_TYPE.getGreatestSubtype(ARRAY_TYPE));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getGreatestSubtype(UNKNOWN_TYPE));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getGreatestSubtype(CHECKED_UNKNOWN_TYPE));

    assertTypeEquals(NO_OBJECT_TYPE, NO_OBJECT_TYPE.getGreatestSubtype(forwardDeclaredNamedType));
    assertTypeEquals(NO_OBJECT_TYPE, forwardDeclaredNamedType.getGreatestSubtype(NO_OBJECT_TYPE));

    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE.getGreatestSubtype(CHECKED_UNKNOWN_TYPE));
    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        CHECKED_UNKNOWN_TYPE.getGreatestSubtype(UNKNOWN_TYPE));

  }

  /** Tests that a derived class extending a type via a named type is a subtype of it. */
  @Test
  public void testSubtypingDerivedExtendsNamedBaseType() {
    ObjectType derived =
        registry.createObjectType(null, registry.createObjectType(null, namedGoogBar));

    assertThat(derived.isSubtypeOf(googBar.getInstanceType())).isTrue();
  }

  @Test
  public void testNamedSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            googBar.getPrototype(),
            googBar.getInstanceType(),
            googSubBar.getPrototype(),
            googSubBar.getInstanceType(),
            googSubSubBar.getPrototype(),
            googSubSubBar.getInstanceType(),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordSubtypeChain() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    JSType aType = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    JSType abType = builder.build();

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("c", STRING_TYPE, null);
    JSType acType = builder.build();
    JSType abOrAcType = registry.createUnionType(abType, acType);

    builder = new RecordTypeBuilder(registry);
    builder.addProperty("a", STRING_TYPE, null);
    builder.addProperty("b", STRING_TYPE, null);
    builder.addProperty("c", NUMBER_TYPE, null);
    JSType abcType = builder.build();

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            aType,
            abOrAcType,
            abType,
            abcType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordAndObjectChain2() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("date", DATE_TYPE, null);
    JSType hasDateProperty = builder.build();

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            hasDateProperty,
            googBar.getInstanceType(),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testRecordAndObjectChain3() throws Exception {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty("date", UNKNOWN_TYPE, null);
    JSType hasUnknownDateProperty = builder.build();

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            hasUnknownDateProperty,
            googBar.getInstanceType(),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testNullableNamedTypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.createOptionalNullableType(registry.getNativeType(JSTypeNative.ALL_TYPE)),
            registry.createOptionalNullableType(
                registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE)),
            registry.createOptionalNullableType(registry.getNativeType(JSTypeNative.OBJECT_TYPE)),
            registry.createOptionalNullableType(googBar.getPrototype()),
            registry.createOptionalNullableType(googBar.getInstanceType()),
            registry.createNullableType(googSubBar.getPrototype()),
            registry.createNullableType(googSubBar.getInstanceType()),
            googSubSubBar.getPrototype(),
            googSubSubBar.getInstanceType(),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testEnumTypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            enumType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testFunctionSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
            registry.getNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE),
            dateMethod,
            registry.getNativeType(JSTypeNative.LEAST_FUNCTION_TYPE),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testFunctionUnionSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            createUnionType(OBJECT_TYPE, STRING_TYPE),
            createUnionType(GREATEST_FUNCTION_TYPE, googBarInst, STRING_TYPE),
            createUnionType(
                STRING_TYPE,
                registry.createFunctionType(createUnionType(STRING_TYPE, NUMBER_TYPE)),
                googBarInst),
            createUnionType(registry.createFunctionType(NUMBER_TYPE), googSubBarInst),
            LEAST_FUNCTION_TYPE,
            NO_OBJECT_TYPE,
            NO_TYPE);
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testConstructorSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE),
            registry.getNativeType(JSTypeNative.FUNCTION_TYPE),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testGoogBarSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.FUNCTION_TYPE),
            googBar,
            googSubBar,
            googSubSubBar,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testConstructorWithArgSubtypeChain() throws Exception {
    FunctionType googBarArgConstructor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "barArg",
                    /* source= */ null,
                    registry.createParameters(googBar),
                    null,
                    null,
                    /* isAbstract= */ false));

    FunctionType googSubBarArgConstructor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "subBarArg",
                    /* source= */ null,
                    registry.createParameters(googSubBar),
                    null,
                    null,
                    /* isAbstract= */ false));

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.FUNCTION_TYPE),
            googBarArgConstructor,
            googSubBarArgConstructor,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testInterfaceInstanceSubtypeChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            ALL_TYPE,
            OBJECT_TYPE,
            interfaceInstType,
            googBar.getPrototype(),
            googBarInst,
            googSubBar.getPrototype(),
            googSubBarInst,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testInterfaceInheritanceSubtypeChain() throws Exception {
    FunctionType tempType =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "goog.TempType",
                    /* source= */ null,
                    null,
                    null,
                    null,
                    /* isAbstract= */ false));
    tempType.setImplementedInterfaces(
        Lists.<ObjectType>newArrayList(subInterfaceInstType));
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            ALL_TYPE,
            OBJECT_TYPE,
            interfaceInstType,
            subInterfaceInstType,
            tempType.getPrototype(),
            tempType.getInstanceType(),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testAnonymousObjectChain() throws Exception {
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            ALL_TYPE,
            createNullableType(OBJECT_TYPE),
            OBJECT_TYPE,
            registry.createAnonymousObjectType(null),
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testAnonymousEnumElementChain() throws Exception {
    ObjectType enumElemType =
        EnumType.builder(registry)
            .setName("typeB")
            .setElementType(registry.createAnonymousObjectType(null))
            .build()
            .getElementsType();
    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            ALL_TYPE,
            createNullableType(OBJECT_TYPE),
            OBJECT_TYPE,
            enumElemType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain);
  }

  @Test
  public void testTemplatizedArrayChain() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfStringOrNumber = createTemplatizedType(
        ARRAY_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType arrayOfAllType = createTemplatizedType(
        ARRAY_TYPE, ALL_TYPE);

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            arrayOfAllType,
            arrayOfStringOrNumber,
            arrayOfString,
            arrayOfNoType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedArrayChain2() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfNoObjectType = createTemplatizedType(
        ARRAY_TYPE, NO_OBJECT_TYPE);
    JSType arrayOfArray = createTemplatizedType(
        ARRAY_TYPE, ARRAY_TYPE);
    JSType arrayOfObject = createTemplatizedType(
        ARRAY_TYPE, OBJECT_TYPE);
    JSType arrayOfAllType = createTemplatizedType(
        ARRAY_TYPE, ALL_TYPE);

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            arrayOfAllType,
            arrayOfObject,
            arrayOfArray,
            arrayOfNoObjectType,
            arrayOfNoType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedObjectChain() throws Exception {
    JSType objectOfNoType = createTemplatizedType(
        OBJECT_TYPE, NO_TYPE);
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType objectOfStringOrNumber = createTemplatizedType(
        OBJECT_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType objectOfAllType = createTemplatizedType(
        OBJECT_TYPE, ALL_TYPE);

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            objectOfAllType,
            objectOfStringOrNumber,
            objectOfString,
            objectOfNoType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testMixedTemplatizedTypeChain() throws Exception {
    JSType arrayOfNoType = createTemplatizedType(
        ARRAY_TYPE, NO_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType objectOfStringOrNumber = createTemplatizedType(
        OBJECT_TYPE, createUnionType(STRING_TYPE, NUMBER_TYPE));
    JSType objectOfAllType = createTemplatizedType(
        OBJECT_TYPE, ALL_TYPE);

    ImmutableList<JSType> typeChain =
        ImmutableList.of(
            registry.getNativeType(JSTypeNative.ALL_TYPE),
            registry.getNativeType(JSTypeNative.OBJECT_TYPE),
            objectOfAllType,
            objectOfStringOrNumber,
            objectOfString,
            arrayOfString,
            arrayOfNoType,
            registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE),
            registry.getNativeType(JSTypeNative.NO_TYPE));
    verifySubtypeChain(typeChain, false);
  }

  @Test
  public void testTemplatizedTypeSubtypes() {
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);
    JSType arrayOfUnknown = createTemplatizedType(
        ARRAY_TYPE, UNKNOWN_TYPE);

    assertThat(objectOfString.isSubtypeOf(ARRAY_TYPE)).isFalse();
    // TODO(johnlenz): should this be false?
    assertThat(ARRAY_TYPE.isSubtypeOf(objectOfString)).isTrue();
    assertThat(objectOfString.isSubtypeOf(ARRAY_TYPE)).isFalse();
    // TODO(johnlenz): should this be false?
    assertThat(ARRAY_TYPE.isSubtypeOf(objectOfString)).isTrue();

    assertThat(arrayOfString.isSubtypeOf(ARRAY_TYPE)).isTrue();
    assertThat(ARRAY_TYPE.isSubtypeOf(arrayOfString)).isTrue();
    assertThat(arrayOfString.isSubtypeOf(arrayOfUnknown)).isTrue();
    assertThat(arrayOfUnknown.isSubtypeOf(arrayOfString)).isTrue();

    assertThat(arrayOfString.isSubtypeOf(arrayOfNumber)).isFalse();
    assertThat(arrayOfNumber.isSubtypeOf(arrayOfString)).isFalse();

    assertThat(arrayOfNumber.isSubtypeOf(createUnionType(arrayOfNumber, NULL_VOID))).isTrue();
    assertThat(createUnionType(arrayOfNumber, NULL_VOID).isSubtypeOf(arrayOfNumber)).isFalse();
    assertThat(arrayOfString.isSubtypeOf(createUnionType(arrayOfNumber, NULL_VOID))).isFalse();
  }

  @Test
  public void testTemplatizedTypeRelations() {
    JSType objectOfString = createTemplatizedType(
        OBJECT_TYPE, STRING_TYPE);
    JSType arrayOfString = createTemplatizedType(
        ARRAY_TYPE, STRING_TYPE);
    JSType arrayOfNumber = createTemplatizedType(
        ARRAY_TYPE, NUMBER_TYPE);

    // Union and least super type cases:
    //
    // 1) alternate:Array<string> and current:Object ==> Object
    // 2) alternate:Array<string> and current:Array ==> Array
    // 3) alternate:Object<string> and current:Array ==> Array|Object<string>
    // 4) alternate:Object and current:Array<string> ==> Object
    // 5) alternate:Array and current:Array<string> ==> Array
    // 6) alternate:Array and current:Object<string> ==> Array|Object<string>
    // 7) alternate:Array<string> and current:Array<number> ==> Array<?>
    // 8) alternate:Array<string> and current:Array<string> ==> Array<string>
    // 9) alternate:Array<string> and
    //    current:Object<string> ==> Object<string>|Array<string>

    assertTypeEquals(
        OBJECT_TYPE,
        JSType.getLeastSupertype(arrayOfString, OBJECT_TYPE));
    assertTypeEquals(
        OBJECT_TYPE,
        JSType.getLeastSupertype(OBJECT_TYPE, arrayOfString));

    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getLeastSupertype(arrayOfString, ARRAY_TYPE));
    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getLeastSupertype(ARRAY_TYPE, arrayOfString));

    assertThat(JSType.getLeastSupertype(objectOfString, ARRAY_TYPE).toString())
        .isEqualTo("(Array|Object<string,?>)");
    assertThat(JSType.getLeastSupertype(ARRAY_TYPE, objectOfString).toString())
        .isEqualTo("(Array|Object<string,?>)");

    assertType(JSType.getLeastSupertype(arrayOfString, arrayOfNumber))
        .toStringIsEqualTo("Array<?>");
    assertType(JSType.getLeastSupertype(arrayOfNumber, arrayOfString))
        .toStringIsEqualTo("Array<?>");

    assertTypeEquals(
        arrayOfString,
        JSType.getLeastSupertype(arrayOfString, arrayOfString));

    assertThat(JSType.getLeastSupertype(objectOfString, arrayOfString).toString())
        .isEqualTo("(Array<string>|Object<string,?>)");
    assertThat(JSType.getLeastSupertype(arrayOfString, objectOfString).toString())
        .isEqualTo("(Array<string>|Object<string,?>)");

    assertTypeEquals(
        objectOfString,
        JSType.getGreatestSubtype(OBJECT_TYPE, objectOfString));

    assertTypeEquals(
        objectOfString,
        JSType.getGreatestSubtype(objectOfString, OBJECT_TYPE));

    assertTypeEquals(
        ARRAY_TYPE,
        JSType.getGreatestSubtype(objectOfString, ARRAY_TYPE));

    assertTypeEquals(
        JSType.getGreatestSubtype(objectOfString, arrayOfString),
        NO_OBJECT_TYPE);

    assertTypeEquals(
        JSType.getGreatestSubtype(OBJECT_TYPE, arrayOfString),
        arrayOfString);
  }

  @Test
  public void testTemplatizedTypesWithBoundedGenerics() {
    FunctionType templatizedCtor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "TestingType",
                    /* source= */ null,
                    null,
                    UNKNOWN_TYPE,
                    ImmutableList.of(
                        registry.createTemplateType("A", NUMBER_TYPE),
                        registry.createTemplateType("B", STRING_TYPE)),
                    /* isAbstract= */ false));
    JSType templatizedInstance = registry.createTemplatizedType(
        templatizedCtor.getInstanceType(),
        ImmutableList.of(NUMBER_TYPE));

    TemplateTypeMap ctrTypeMap = templatizedCtor.getTemplateTypeMap();
    TemplateType keyA = ctrTypeMap.getLastTemplateTypeKeyByName("A");
    assertThat(keyA).isNotNull();
    TemplateType keyB = ctrTypeMap.getLastTemplateTypeKeyByName("B");
    assertThat(keyB).isNotNull();
    TemplateType keyC = ctrTypeMap.getLastTemplateTypeKeyByName("C");
    assertThat(keyC).isNull();
    TemplateType unknownKey = registry.createTemplateType("C");

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();
    assertThat(templateTypeMap.hasTemplateKey(keyA)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(keyB)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(unknownKey)).isFalse();

    assertThat(templateTypeMap.getResolvedTemplateType(keyA)).isEqualTo(NUMBER_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(keyB)).isEqualTo(keyB);
    assertThat(templateTypeMap.getResolvedTemplateType(unknownKey)).isEqualTo(UNKNOWN_TYPE);
  }

  /**
   * Tests that the given chain of types has a total ordering defined
   * by the subtype relationship, with types at the top of the lattice
   * listed first.
   *
   * Also verifies that the infimum of any two types on the chain
   * is the lower type, and the supremum of any two types on the chain
   * is the higher type.
   */
  public void verifySubtypeChain(List<JSType> typeChain) throws Exception {
    verifySubtypeChain(typeChain, true);
  }

  @SuppressWarnings("SelfEquals")
  public void verifySubtypeChain(List<JSType> typeChain, boolean checkSubtyping) throws Exception {
    // Ugh. This wouldn't require so much copy-and-paste if we had a functional
    // programming language.
    for (int i = 0; i < typeChain.size(); i++) {
      for (int j = 0; j < typeChain.size(); j++) {
        JSType typeI = typeChain.get(i);
        JSType typeJ = typeChain.get(j);

        JSType namedTypeI = getNamedWrapper("TypeI", typeI);
        JSType namedTypeJ = getNamedWrapper("TypeJ", typeJ);
        JSType proxyTypeI = new ProxyObjectType(registry, typeI);
        JSType proxyTypeJ = new ProxyObjectType(registry, typeJ);

        if (i == j) {
          assertWithMessage("%s should equal itself", typeI).that(typeI.equals(typeI)).isTrue();
          assertWithMessage("Named %s should equal itself", typeI)
              .that(namedTypeI.equals(namedTypeI))
              .isTrue();
          assertWithMessage("Proxy %s should equal itself", typeI)
              .that(proxyTypeI.equals(proxyTypeI))
              .isTrue();
        } else {
          boolean shouldCheck = true;
          // due to structural interface matching and its updated equivalence
          // checking, a subtype interface could be considered as equivalent
          // to its super type interface (if they are structurally the same)
          // when this happens, the following checks are skipped.
          ObjectType objectI = typeI.toObjectType();
          ObjectType objectJ = typeJ.toObjectType();
          if (objectI != null && objectJ != null) {
            FunctionType constructorI = objectI.getConstructor();
            FunctionType constructorJ = objectJ.getConstructor();
            if (constructorI != null && constructorJ != null
                && constructorI.isStructuralInterface()
                && constructorJ.isStructuralInterface()) {
              if (constructorI.equals(constructorJ)) {
                shouldCheck = false;
              }
            }
          }
          if (shouldCheck) {
            assertWithMessage(typeI + " should not equal " + typeJ)
                .that(typeI.equals(typeJ))
                .isFalse();
            assertWithMessage("Named " + typeI + " should not equal " + typeJ)
                .that(namedTypeI.equals(namedTypeJ))
                .isFalse();
            assertWithMessage("Proxy " + typeI + " should not equal " + typeJ)
                .that(proxyTypeI.equals(proxyTypeJ))
                .isFalse();
          }
        }

        assertWithMessage(typeJ + " should be castable to " + typeI)
            .that(typeJ.canCastTo(typeI))
            .isTrue();
        assertWithMessage(typeJ + " should be castable to Named " + namedTypeI)
            .that(typeJ.canCastTo(namedTypeI))
            .isTrue();
        assertWithMessage(typeJ + " should be castable to Proxy " + proxyTypeI)
            .that(typeJ.canCastTo(proxyTypeI))
            .isTrue();

        assertWithMessage("Named " + typeJ + " should be castable to " + typeI)
            .that(namedTypeJ.canCastTo(typeI))
            .isTrue();
        assertWithMessage("Named " + typeJ + " should be castable to Named " + typeI)
            .that(namedTypeJ.canCastTo(namedTypeI))
            .isTrue();
        assertWithMessage("Named " + typeJ + " should be castable to Proxy " + typeI)
            .that(namedTypeJ.canCastTo(proxyTypeI))
            .isTrue();

        assertWithMessage("Proxy " + typeJ + " should be castable to " + typeI)
            .that(proxyTypeJ.canCastTo(typeI))
            .isTrue();
        assertWithMessage("Proxy " + typeJ + " should be castable to Named " + typeI)
            .that(proxyTypeJ.canCastTo(namedTypeI))
            .isTrue();
        assertWithMessage("Proxy " + typeJ + " should be castable to Proxy " + typeI)
            .that(proxyTypeJ.canCastTo(proxyTypeI))
            .isTrue();

        // due to structural interface matching, a subtype could be considered
        // as the super type of its super type (if they are structurally the same)
        // when this happens, the following checks are skipped.
        if (typeI.isSubtypeOf(typeJ) && typeJ.isSubtypeOf(typeI)) {
          continue;
        }

        if (checkSubtyping) {
          if (i <= j) {
            assertWithMessage(typeJ + " should be a subtype of " + typeI)
                .that(typeJ.isSubtypeOf(typeI))
                .isTrue();
            assertWithMessage("Named " + typeJ + " should be a subtype of Named " + typeI)
                .that(namedTypeJ.isSubtypeOf(namedTypeI))
                .isTrue();
            assertWithMessage("Proxy " + typeJ + " should be a subtype of Proxy " + typeI)
                .that(proxyTypeJ.isSubtypeOf(proxyTypeI))
                .isTrue();
          } else {
            assertWithMessage(typeJ + " should not be a subtype of " + typeI)
                .that(typeJ.isSubtypeOf(typeI))
                .isFalse();
            assertWithMessage("Named " + typeJ + " should not be a subtype of Named " + typeI)
                .that(namedTypeJ.isSubtypeOf(namedTypeI))
                .isFalse();
            assertWithMessage("Named " + typeJ + " should not be a subtype of Named " + typeI)
                .that(proxyTypeJ.isSubtypeOf(proxyTypeI))
                .isFalse();
          }

          JSType expectedSupremum = i < j ? typeI : typeJ;
          JSType expectedInfimum = i > j ? typeI : typeJ;
          JSType typeIleastSupertype = typeI.getLeastSupertype(typeJ);
          JSType typeJleastSupertype = typeJ.getLeastSupertype(typeI);

          // The `isSubtypeWithoutStructuralTyping` function now considers object literal typedefs
          // as structural types. To ensure accuracy, this check must be extended to accommodate
          // scenarios where the least supertype is a union type.
          //
          // For example, the least supertype of `{a: number}` and `{a: string, b: number}` is
          // now the union type `{a: number} | {a: string, b: number}`.

          if (!typeIleastSupertype.isSubtypeOf(typeJleastSupertype)) {
            assertTypeEquals(
                expectedSupremum + " should be the least supertype of " + typeI + " and " + typeJ,
                expectedSupremum,
                typeI.getLeastSupertype(typeJ));
          }

          // TODO(nicksantos): Should these tests pass?
          //assertTypeEquals(
          //    expectedSupremum + " should be the least supertype of Named " +
          //    typeI + " and Named " + typeJ,
          //    expectedSupremum, namedTypeI.getLeastSupertype(namedTypeJ));
          //assertTypeEquals(
          //    expectedSupremum + " should be the least supertype of Proxy " +
          //    typeI + " and Proxy " + typeJ,
          //    expectedSupremum, proxyTypeI.getLeastSupertype(proxyTypeJ));

          assertTypeEquals(
              expectedInfimum + " should be the greatest subtype of " + typeI +
              " and " + typeJ,
              expectedInfimum, typeI.getGreatestSubtype(typeJ));

          // TODO(nicksantos): Should these tests pass?
          //assertTypeEquals(
          //    expectedInfimum + " should be the greatest subtype of Named " +
          //    typeI + " and Named " + typeJ,
          //    expectedInfimum, namedTypeI.getGreatestSubtype(namedTypeJ));
          //assertTypeEquals(
          //    expectedInfimum + " should be the greatest subtype of Proxy " +
          //    typeI + " and Proxy " + typeJ,
          //    expectedInfimum, proxyTypeI.getGreatestSubtype(proxyTypeJ));
        }
      }
    }
  }

  JSType getNamedWrapper(String name, JSType jstype) {
    // Normally, there is no way to create a Named NoType alias so avoid confusing things by doing
    // it here explicitly.
    if (!jstype.isNoType()) {
      return NamedType.builder(registry, name)
          .setResolutionKind(ResolutionKind.NONE)
          .setReferencedType(jstype)
          .build();
    } else {
      return jstype;
    }
  }

  /** Tests the behavior of {@link JSType#getRestrictedTypeGivenOutcome(Outcome)} ()}. */
  @SuppressWarnings("checked")
  @Test
  public void testRestrictedTypeGivenOutcome() {
    // simple cases
    assertTypeEquals(BOOLEAN_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(BOOLEAN_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(NO_TYPE,
        NULL_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NULL_TYPE,
        NULL_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(STRING_TYPE,
        STRING_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(STRING_TYPE,
        STRING_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(STRING_OBJECT_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NO_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(NO_TYPE,
        VOID_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(VOID_TYPE,
        VOID_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(NO_OBJECT_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    assertTypeEquals(CHECKED_UNKNOWN_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    // unions
    UnionType nullableStringValue =
        (UnionType) createNullableType(STRING_TYPE);
    assertTypeEquals(STRING_TYPE,
        nullableStringValue.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(nullableStringValue,
        nullableStringValue.getRestrictedTypeGivenOutcome(Outcome.FALSE));

    UnionType nullableStringObject =
        (UnionType) createNullableType(STRING_OBJECT_TYPE);
    assertTypeEquals(STRING_OBJECT_TYPE,
        nullableStringObject.getRestrictedTypeGivenOutcome(Outcome.TRUE));
    assertTypeEquals(NULL_TYPE,
        nullableStringObject.getRestrictedTypeGivenOutcome(Outcome.FALSE));
  }

  @Test
  public void nullishOutcomeGetRestrictedTypeGivenOutcome() {
    // simple cases
    assertTypeEquals(NO_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NULL_TYPE,
        NULL_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        STRING_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(VOID_TYPE,
        VOID_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        ALL_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    assertTypeEquals(NO_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    // unions
    UnionType nullableStringValue =
        (UnionType) createNullableType(STRING_TYPE);
    assertTypeEquals(NULL_TYPE,
        nullableStringValue.getRestrictedTypeGivenOutcome(Outcome.NULLISH));

    UnionType nullableStringObject =
        (UnionType) createNullableType(STRING_OBJECT_TYPE);
    assertTypeEquals(NULL_TYPE,
        nullableStringObject.getRestrictedTypeGivenOutcome(Outcome.NULLISH));
  }

  @Test
  public void falseNotNullOutcomeGetRestrictedTypeGivenOutcome() {
    // simple cases
    assertTypeEquals(BOOLEAN_TYPE,
        BOOLEAN_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(NULL_TYPE,
        NULL_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(NUMBER_TYPE,
        NUMBER_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(STRING_TYPE,
        STRING_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(NO_TYPE,
        STRING_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(VOID_TYPE,
        VOID_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(NO_TYPE,
        NO_OBJECT_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(NO_TYPE,
        NO_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(ALL_TYPE,
        ALL_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    assertTypeEquals(UNKNOWN_TYPE,
        UNKNOWN_TYPE.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    // unions
    UnionType nullableStringValue =
        (UnionType) createNullableType(STRING_TYPE);
    assertTypeEquals(nullableStringValue,
        nullableStringValue.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));

    UnionType nullableStringObject =
        (UnionType) createNullableType(STRING_OBJECT_TYPE);
    assertTypeEquals(NULL_TYPE,
        nullableStringObject.getRestrictedTypeGivenOutcome(Outcome.FALSE_NOT_NULL));
  }

  @Test
  public void testGoodSetPrototypeBasedOn() {
    FunctionType fun =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "fun", /* source= */ null, null, null, null, /* isAbstract= */ false));
    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertThat(fun.getInstanceType().isUnknownType()).isTrue();
  }

  @Test
  public void testLateSetPrototypeBasedOn() {
    FunctionType fun =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "fun", /* source= */ null, null, null, null, /* isAbstract= */ false));
    assertThat(fun.getInstanceType().isUnknownType()).isFalse();

    fun.setPrototypeBasedOn(unresolvedNamedType);
    assertThat(fun.getInstanceType().isUnknownType()).isTrue();
  }

  @Test
  public void testGetTypeUnderEquality1() {
    for (JSType type : types) {
      testGetTypeUnderEquality(type, type, type, type);
    }
  }

  @Test
  public void testGetTypesUnderEquality2() {
    // objects can be equal to numbers
    testGetTypeUnderEquality(
        NUMBER_TYPE, OBJECT_TYPE,
        NUMBER_TYPE, OBJECT_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality3() {
    // null == undefined
    testGetTypeUnderEquality(
        NULL_TYPE, VOID_TYPE,
        NULL_TYPE, VOID_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderEquality4() {
    // (number,string) and number/string
    UnionType stringNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    testGetTypeUnderEquality(
        stringNumber, STRING_TYPE,
        stringNumber, STRING_TYPE);
    testGetTypeUnderEquality(
        stringNumber, NUMBER_TYPE,
        stringNumber, NUMBER_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality5() {
    // (number,null) and undefined
    JSType nullUndefined = createUnionType(VOID_TYPE, NULL_TYPE);
    testGetTypeUnderEquality(
        nullUndefined, NULL_TYPE,
        nullUndefined, NULL_TYPE);
    testGetTypeUnderEquality(
        nullUndefined, VOID_TYPE,
        nullUndefined, VOID_TYPE);
  }

  @Test
  public void testGetTypesUnderEquality6() {
    // (number,undefined,null) == null
    JSType optNullNumber = createUnionType(VOID_TYPE, NULL_TYPE, NUMBER_TYPE);
    testGetTypeUnderEquality(
        optNullNumber, NULL_TYPE,
        createUnionType(NULL_TYPE, VOID_TYPE), NULL_TYPE);
  }

  private void testGetTypeUnderEquality(
      JSType t1, JSType t2, JSType t1Eq, JSType t2Eq) {
    // creating the pairs
    TypePair p12 = t1.getTypesUnderEquality(t2);
    TypePair p21 = t2.getTypesUnderEquality(t1);

    // t1Eq
    assertTypeEquals(t1Eq, p12.typeA);
    assertTypeEquals(t1Eq, p21.typeB);

    // t2Eq
    assertTypeEquals(t2Eq, p12.typeB);
    assertTypeEquals(t2Eq, p21.typeA);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality1() {
    // objects can be not equal to numbers
    UnionType numberObject =
        (UnionType) createUnionType(NUMBER_TYPE, OBJECT_TYPE);
    testGetTypesUnderInequality(
        numberObject, NUMBER_TYPE,
        numberObject, NUMBER_TYPE);
    testGetTypesUnderInequality(
        numberObject, OBJECT_TYPE,
        numberObject, OBJECT_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality2() {
    // null == undefined
    UnionType nullUndefined =
        (UnionType) createUnionType(VOID_TYPE, NULL_TYPE);
    testGetTypesUnderInequality(
        nullUndefined, NULL_TYPE,
        NO_TYPE, NO_TYPE);
    testGetTypesUnderInequality(
        nullUndefined, VOID_TYPE,
        NO_TYPE, NO_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality3() {
    // (number,string)
    UnionType stringNumber =
        (UnionType) createUnionType(NUMBER_TYPE, STRING_TYPE);
    testGetTypesUnderInequality(
        stringNumber, NUMBER_TYPE,
        stringNumber, NUMBER_TYPE);
    testGetTypesUnderInequality(
        stringNumber, STRING_TYPE,
        stringNumber, STRING_TYPE);
  }

  @SuppressWarnings("checked")
  @Test
  public void testGetTypesUnderInequality4() {
    // (number,undefined,null) and null
    UnionType nullableOptionalNumber =
        (UnionType) createUnionType(NULL_TYPE, VOID_TYPE, NUMBER_TYPE);
    testGetTypesUnderInequality(
        nullableOptionalNumber, NULL_TYPE,
        NUMBER_TYPE, NULL_TYPE);
  }

  private void testGetTypesUnderInequality(
      JSType t1, JSType t2, JSType t1Eq, JSType t2Eq) {
    // creating the pairs
    TypePair p12 = t1.getTypesUnderInequality(t2);
    TypePair p21 = t2.getTypesUnderInequality(t1);

    // t1Eq
    assertTypeEquals(t1Eq, p12.typeA);
    assertTypeEquals(t1Eq, p21.typeB);

    // t2Eq
    assertTypeEquals(t2Eq, p12.typeB);
    assertTypeEquals(t2Eq, p21.typeA);
  }

  /** Tests the factory method {@link JSTypeRegistry#createOptionalType(JSType)}. */
  @Test
  public void testCreateOptionalType() {
    // number
    UnionType optNumber = (UnionType) registry.createOptionalType(NUMBER_TYPE);
    assertUnionContains(optNumber, NUMBER_TYPE);
    assertUnionContains(optNumber, VOID_TYPE);

    // union
    UnionType optUnion =
        (UnionType) registry.createOptionalType(
            createUnionType(STRING_OBJECT_TYPE, DATE_TYPE));
    assertUnionContains(optUnion, DATE_TYPE);
    assertUnionContains(optUnion, STRING_OBJECT_TYPE);
    assertUnionContains(optUnion, VOID_TYPE);
  }

  public void assertUnionContains(UnionType union, JSType type) {
    assertWithMessage(union + " should contain " + type).that(union.contains(type)).isTrue();
  }

  /** Tests the factory method {@link JSTypeRegistry#createAnonymousObjectType}}. */
  @Test
  public void testCreateAnonymousObjectType() {
    // anonymous
    ObjectType anonymous = registry.createAnonymousObjectType(null);
    assertTypeEquals(OBJECT_TYPE, anonymous.getImplicitPrototype());
    assertType(anonymous).getReferenceNameIsNull();
    assertThat(anonymous.toString()).isEqualTo("{}");
  }

  /**
   * Tests the factory method {@link JSTypeRegistry#createAnonymousObjectType}} and adds some
   * properties to it.
   */
  @Test
  public void testCreateAnonymousObjectType2() {
    // anonymous
    ObjectType anonymous = registry.createAnonymousObjectType(null);
    anonymous.defineDeclaredProperty(
        "a", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "b", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "c", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "d", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "e", NUMBER_TYPE, null);
    anonymous.defineDeclaredProperty(
        "f", NUMBER_TYPE, null);
    assertThat(anonymous.toString())
        .isEqualTo(
            LINE_JOINER.join(
                "{",
                "  a: number,",
                "  b: number,",
                "  c: number,",
                "  d: number,",
                "  e: number,",
                "  f: number",
                "}"));
  }

  /** Tests the factory method {@link JSTypeRegistry#createObjectType(String, ObjectType)}}. */
  @Test
  public void testCreateObjectType() {
    // simple
    ObjectType subDate =
        registry.createObjectType(null, DATE_TYPE.getImplicitPrototype());
    assertTypeEquals(DATE_TYPE.getImplicitPrototype(),
        subDate.getImplicitPrototype());
    assertType(subDate).getReferenceNameIsNull();
    assertThat(subDate.toString()).isEqualTo("{...}");

    // name, node, prototype
    ObjectType subArray = registry.createObjectType("Foo",
        ARRAY_TYPE.getImplicitPrototype());
    assertTypeEquals(ARRAY_TYPE.getImplicitPrototype(),
        subArray.getImplicitPrototype());
    assertType(subArray).getReferenceNameIsEqualTo("Foo");
  }

  /** Tests {@code (U2U_CONSTRUCTOR,undefined) <: (U2U_CONSTRUCTOR,undefined)}. */
  @SuppressWarnings("checked")
  @Test
  public void testBug903110() {
    UnionType union = (UnionType) createUnionType(FUNCTION_TYPE, VOID_TYPE);
    assertThat(VOID_TYPE.isSubtypeOf(union)).isTrue();
    assertThat(FUNCTION_TYPE.isSubtype(union)).isTrue();
    assertThat(union.isSubtype(union)).isTrue();
  }

  /**
   * Assert that a type can assign to itself.
   */
  private void assertTypeCanAssignToItself(JSType type) {
    assertThat(type.isSubtypeOf(type)).isTrue();
  }

  /**
   * Tests that hasOwnProperty returns true when a property is defined directly on a class and false
   * if the property is defined on the supertype or not at all.
   */
  @Test
  public void testHasOwnProperty() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);

    sup.defineProperty("base", getBottomType(), false, null);
    sub.defineProperty("sub", getBottomType(), false, null);

    assertThat(sup.hasProperty("base")).isTrue();
    assertThat(sup.hasProperty("sub")).isFalse();
    assertThat(sup.hasOwnProperty("base")).isTrue();
    assertThat(sup.hasOwnProperty("sub")).isFalse();
    assertThat(sup.hasOwnProperty("none")).isFalse();

    assertThat(sub.hasProperty("base")).isTrue();
    assertThat(sub.hasProperty("sub")).isTrue();
    assertThat(sub.hasOwnProperty("base")).isFalse();
    assertThat(sub.hasOwnProperty("sub")).isTrue();
    assertThat(sub.hasOwnProperty("none")).isFalse();
  }

  /**
   * Tests that hasOwnProperty returns true when a property is defined directly on a class and false
   * if the property is defined on the supertype or not at all.
   */
  @Test
  public void testHasOwnProperty_symbol() {
    ObjectType sup = registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);
    Property.Key baseSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "base"));
    Property.Key subSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "sub"));
    Property.Key noneSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "none"));

    sup.defineProperty(baseSymbol, getBottomType(), false, null);
    sub.defineProperty(subSymbol, getBottomType(), false, null);

    assertThat(sup.hasProperty(baseSymbol)).isTrue();
    assertThat(sup.hasProperty(subSymbol)).isFalse();
    assertThat(sup.hasOwnProperty(baseSymbol)).isTrue();
    assertThat(sup.hasOwnProperty(subSymbol)).isFalse();
    assertThat(sup.hasOwnProperty(noneSymbol)).isFalse();
    assertThat(sup.hasOwnProperty("none")).isFalse();

    assertThat(sub.hasProperty(baseSymbol)).isTrue();
    assertThat(sub.hasProperty(subSymbol)).isTrue();
    assertThat(sub.hasOwnProperty(baseSymbol)).isFalse();
    assertThat(sub.hasOwnProperty(subSymbol)).isTrue();
    assertThat(sub.hasOwnProperty(noneSymbol)).isFalse();
    assertThat(sub.hasOwnProperty("none")).isFalse();
  }

  @Test
  public void testNamedTypeHasOwnProperty() {
    namedGoogBar.getImplicitPrototype().defineProperty("base", getBottomType(), false, null);
    namedGoogBar.defineProperty("sub", getBottomType(), false, null);

    assertThat(namedGoogBar.hasOwnProperty("base")).isFalse();
    assertThat(namedGoogBar.hasProperty("base")).isTrue();
    assertThat(namedGoogBar.hasOwnProperty("sub")).isTrue();
    assertThat(namedGoogBar.hasProperty("sub")).isTrue();
  }

  @Test
  public void testInterfaceHasOwnProperty() {
    interfaceInstType.defineProperty("base", getBottomType(), false, null);
    subInterfaceInstType.defineProperty("sub", getBottomType(), false, null);

    assertThat(interfaceInstType.hasProperty("base")).isTrue();
    assertThat(interfaceInstType.hasProperty("sub")).isFalse();
    assertThat(interfaceInstType.hasOwnProperty("base")).isTrue();
    assertThat(interfaceInstType.hasOwnProperty("sub")).isFalse();
    assertThat(interfaceInstType.hasOwnProperty("none")).isFalse();

    assertThat(subInterfaceInstType.hasProperty("base")).isTrue();
    assertThat(subInterfaceInstType.hasProperty("sub")).isTrue();
    assertThat(subInterfaceInstType.hasOwnProperty("base")).isFalse();
    assertThat(subInterfaceInstType.hasOwnProperty("sub")).isTrue();
    assertThat(subInterfaceInstType.hasOwnProperty("none")).isFalse();
  }

  @Test
  public void testInterfaceHasOwnProperty_symbol() {
    Property.Key baseSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "base"));
    Property.Key subSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "sub"));
    Property.Key noneSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "none"));
    interfaceInstType.defineProperty(baseSymbol, getBottomType(), false, null);
    subInterfaceInstType.defineProperty(subSymbol, getBottomType(), false, null);

    assertThat(interfaceInstType.hasProperty(baseSymbol)).isTrue();
    assertThat(interfaceInstType.hasProperty(subSymbol)).isFalse();
    assertThat(interfaceInstType.hasOwnProperty(baseSymbol)).isTrue();
    assertThat(interfaceInstType.hasOwnProperty(subSymbol)).isFalse();
    assertThat(interfaceInstType.hasOwnProperty(noneSymbol)).isFalse();
    assertThat(interfaceInstType.hasOwnProperty("none")).isFalse();

    assertThat(subInterfaceInstType.hasProperty(baseSymbol)).isTrue();
    assertThat(subInterfaceInstType.hasProperty(subSymbol)).isTrue();
    assertThat(subInterfaceInstType.hasOwnProperty(baseSymbol)).isFalse();
    assertThat(subInterfaceInstType.hasOwnProperty(subSymbol)).isTrue();
    assertThat(subInterfaceInstType.hasOwnProperty(noneSymbol)).isFalse();
    assertThat(subInterfaceInstType.hasOwnProperty("none")).isFalse();
  }

  @Test
  public void testGetPropertyNames() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);
    final ObjectType bottomType = getBottomType();

    sup.defineProperty("base", bottomType, false, null);
    sub.defineProperty("sub", bottomType, false, null);

    assertThat(sub.getPropertyNames())
        .isEqualTo(
            ImmutableSet.of(
                "isPrototypeOf",
                "toLocaleString",
                "propertyIsEnumerable",
                "toString",
                "valueOf",
                "hasOwnProperty",
                "constructor",
                "base",
                "sub"));
    assertThat(sup.getPropertyNames())
        .isEqualTo(
            ImmutableSet.of(
                "isPrototypeOf",
                "toLocaleString",
                "propertyIsEnumerable",
                "toString",
                "valueOf",
                "hasOwnProperty",
                "constructor",
                "base"));

    assertThat(NO_OBJECT_TYPE.getPropertyNames()).isEmpty();
  }

  @Test
  public void testGetPropertyNames_symbol() {
    ObjectType sup = registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);
    final ObjectType bottomType = getBottomType();
    Property.Key baseSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "base"));
    Property.Key subSymbol = new Property.SymbolKey(new KnownSymbolType(registry, "sub"));

    sup.defineProperty(baseSymbol, bottomType, false, null);
    sub.defineProperty(subSymbol, bottomType, false, null);

    assertThat(sub.getPropertyNames())
        .isEqualTo(
            ImmutableSet.of(
                "isPrototypeOf",
                "toLocaleString",
                "propertyIsEnumerable",
                "toString",
                "valueOf",
                "hasOwnProperty",
                "constructor"));
    assertThat(sup.getPropertyNames())
        .isEqualTo(
            ImmutableSet.of(
                "isPrototypeOf",
                "toLocaleString",
                "propertyIsEnumerable",
                "toString",
                "valueOf",
                "hasOwnProperty",
                "constructor"));
    assertThat(sup.getAllKeys().knownSymbolKeys()).containsExactly(baseSymbol.symbol());
    assertThat(sub.getAllKeys().knownSymbolKeys())
        .containsExactly(baseSymbol.symbol(), subSymbol.symbol());
    assertThat(sub.getOwnPropertyKnownSymbols()).containsExactly(subSymbol.symbol());

    assertThat(NO_OBJECT_TYPE.getPropertyNames()).isEmpty();
  }

  private ObjectType getBottomType() {
    return registry.getNativeObjectType(JSTypeNative.NO_TYPE);
  }

  @Test
  public void testGetAndSetJSDocInfoWithNamedType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDeprecated();
    JSDocInfo info = builder.build();

    assertThat(namedGoogBar.getOwnPropertyJSDocInfo("X")).isNull();
    namedGoogBar.setPropertyJSDocInfo("X", info);
    assertThat(namedGoogBar.getOwnPropertyJSDocInfo("X").isDeprecated()).isTrue();
    assertPropertyTypeInferred(namedGoogBar, "X");
    assertTypeEquals(UNKNOWN_TYPE, namedGoogBar.getPropertyType("X"));
  }

  @Test
  public void testGetAndSetJSDocInfoWithObjectTypes() {
    ObjectType sup =
        registry.createObjectType(null, registry.createAnonymousObjectType(null));
    ObjectType sub = registry.createObjectType(null, sup);

    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDeprecated();
    JSDocInfo deprecated = builder.build();

    builder = JSDocInfo.builder();
    builder.recordVisibility(Visibility.PRIVATE);
    JSDocInfo privateInfo = builder.build();

    sup.defineProperty("X", NUMBER_TYPE, true, null);
    sup.setPropertyJSDocInfo("X", privateInfo);

    sub.defineProperty("X", NUMBER_TYPE, true, null);
    sub.setPropertyJSDocInfo("X", deprecated);

    assertThat(sup.getOwnPropertyJSDocInfo("X").isDeprecated()).isFalse();
    assertThat(sup.getOwnPropertyJSDocInfo("X").getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertTypeEquals(NUMBER_TYPE, sup.getPropertyType("X"));
    assertThat(sub.getOwnPropertyJSDocInfo("X").isDeprecated()).isTrue();
    assertThat(sub.getOwnPropertyJSDocInfo("X").getVisibility()).isEqualTo(Visibility.INHERITED);
    assertTypeEquals(NUMBER_TYPE, sub.getPropertyType("X"));
  }

  @Test
  public void testGetAndSetJSDocInfoWithNoType() {
    JSDocInfo.Builder builder = JSDocInfo.builder();
    builder.recordDeprecated();
    JSDocInfo deprecated = builder.build();

    NO_TYPE.setPropertyJSDocInfo("X", deprecated);
    assertThat(NO_TYPE.getOwnPropertyJSDocInfo("X")).isNull();
  }

  @Test
  public void testIsTemplatedType() {
    assertThat(new TemplateType(registry, "T").hasAnyTemplateTypes()).isTrue();
    assertThat(ARRAY_TYPE.hasAnyTemplateTypes()).isFalse();

    assertThat(
            createTemplatizedType(ARRAY_TYPE, new TemplateType(registry, "T"))
                .hasAnyTemplateTypes())
        .isTrue();
    assertThat(createTemplatizedType(ARRAY_TYPE, STRING_TYPE).hasAnyTemplateTypes()).isFalse();

    assertThat(
            FunctionType.builder(registry)
                .withReturnType(new TemplateType(registry, "T"))
                .build()
                .hasAnyTemplateTypes())
        .isTrue();
    assertThat(
            FunctionType.builder(registry)
                .withTypeOfThis(new TemplateType(registry, "T"))
                .build()
                .hasAnyTemplateTypes())
        .isTrue();
    assertThat(
            FunctionType.builder(registry)
                .withReturnType(STRING_TYPE)
                .build()
                .hasAnyTemplateTypes())
        .isFalse();

    assertThat(
            registry
                .createUnionType(NULL_TYPE, new TemplateType(registry, "T"), STRING_TYPE)
                .hasAnyTemplateTypes())
        .isTrue();
    assertThat(registry.createUnionType(NULL_TYPE, ARRAY_TYPE, STRING_TYPE).hasAnyTemplateTypes())
        .isFalse();
  }

  @Test
  public void testTemplatizedType() {

    FunctionType templatizedCtor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "TestingType",
                    /* source= */ null,
                    null,
                    UNKNOWN_TYPE,
                    ImmutableList.of(
                        registry.createTemplateType("A"), registry.createTemplateType("B")),
                    /* isAbstract= */ false));
    JSType templatizedInstance = registry.createTemplatizedType(
        templatizedCtor.getInstanceType(),
        ImmutableList.of(NUMBER_TYPE, STRING_TYPE));

    TemplateTypeMap ctrTypeMap = templatizedCtor.getTemplateTypeMap();
    TemplateType keyA = ctrTypeMap.getLastTemplateTypeKeyByName("A");
    assertThat(keyA).isNotNull();
    TemplateType keyB = ctrTypeMap.getLastTemplateTypeKeyByName("B");
    assertThat(keyB).isNotNull();
    TemplateType keyC = ctrTypeMap.getLastTemplateTypeKeyByName("C");
    assertThat(keyC).isNull();
    TemplateType unknownKey = registry.createTemplateType("C");

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();
    assertThat(templateTypeMap.hasTemplateKey(keyA)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(keyB)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(unknownKey)).isFalse();

    assertThat(templateTypeMap.getResolvedTemplateType(keyA)).isEqualTo(NUMBER_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(keyB)).isEqualTo(STRING_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(unknownKey)).isEqualTo(UNKNOWN_TYPE);

    assertThat(templatizedInstance.toString()).isEqualTo("TestingType<number,string>");
  }

  @Test
  public void testPartiallyTemplatizedType() {

    FunctionType templatizedCtor =
        withOpenRegistry(
            () ->
                registry.createConstructorType(
                    "TestingType",
                    /* source= */ null,
                    null,
                    UNKNOWN_TYPE,
                    ImmutableList.of(
                        registry.createTemplateType("A"), registry.createTemplateType("B")),
                    /* isAbstract= */ false));
    JSType templatizedInstance = registry.createTemplatizedType(
        templatizedCtor.getInstanceType(),
        ImmutableList.of(NUMBER_TYPE));

    TemplateTypeMap ctrTypeMap = templatizedCtor.getTemplateTypeMap();
    TemplateType keyA = ctrTypeMap.getLastTemplateTypeKeyByName("A");
    assertThat(keyA).isNotNull();
    TemplateType keyB = ctrTypeMap.getLastTemplateTypeKeyByName("B");
    assertThat(keyB).isNotNull();
    TemplateType keyC = ctrTypeMap.getLastTemplateTypeKeyByName("C");
    assertThat(keyC).isNull();
    TemplateType unknownKey = registry.createTemplateType("C");

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();
    assertThat(templateTypeMap.hasTemplateKey(keyA)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(keyB)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(unknownKey)).isFalse();

    assertThat(templateTypeMap.getResolvedTemplateType(keyA)).isEqualTo(NUMBER_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(keyB)).isEqualTo(UNKNOWN_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(unknownKey)).isEqualTo(UNKNOWN_TYPE);

    assertThat(templatizedInstance.toString()).isEqualTo("TestingType<number,?>");
  }

  @Test
  public void testTemplatizedTypeWithSubclass() {
    TemplateType parentKeyA = registry.createTemplateType("A");
    TemplateType childKeyA = registry.createTemplateType("A");
    TemplateType childKeyB = registry.createTemplateType("B");

    FunctionType templatizedCtor =
        withOpenRegistry(
            () -> {
              FunctionType parent =
                  FunctionType.builder(registry)
                      .withName("ParentType")
                      .forConstructor()
                      .withTemplateKeys(ImmutableList.of(parentKeyA))
                      .build();
              ObjectType baseType =
                  registry.createTemplatizedType(
                      parent.getInstanceType(), ImmutableList.of(NULL_TYPE));
              // create a subclass that `@extends {ParentType<null>}`
              // and has a template key with the same name 'A' as the parent.
              FunctionType child =
                  FunctionType.builder(registry)
                      .withPrototypeBasedOn(baseType)
                      .withName("TestingType")
                      .forConstructor()
                      .withTemplateKeys(ImmutableList.of(childKeyA, childKeyB))
                      .build();
              child.getInstanceType().mergeSupertypeTemplateTypes(baseType);
              return child;
            });

    JSType templatizedInstance =
        registry.createTemplatizedType(
            templatizedCtor.getInstanceType(), ImmutableList.of(NUMBER_TYPE, STRING_TYPE));

    TemplateTypeMap templateTypeMap = templatizedInstance.getTemplateTypeMap();

    assertThat(templateTypeMap.getLastTemplateTypeKeyByName("A")).isEqualTo(childKeyA);
    assertThat(templateTypeMap.getLastTemplateTypeKeyByName("B")).isEqualTo(childKeyB);

    assertThat(templateTypeMap.hasTemplateKey(parentKeyA)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(childKeyA)).isTrue();
    assertThat(templateTypeMap.hasTemplateKey(childKeyB)).isTrue();

    assertThat(templateTypeMap.getResolvedTemplateType(parentKeyA)).isEqualTo(NULL_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(childKeyA)).isEqualTo(NUMBER_TYPE);
    assertThat(templateTypeMap.getResolvedTemplateType(childKeyB)).isEqualTo(STRING_TYPE);

    assertThat(templatizedInstance.toString()).isEqualTo("TestingType<number,string>");
  }

  @Test
  public void testTemplateTypeValidator() {
    // The template type setValidator() will see the TemplateType, not the referenced unknown type
    // like other ProxyObjectTypes do.
    TemplateType t = new TemplateType(registry, "T");

    assertThat(t.setValidator(type -> type.isTemplateType())).isTrue();
    assertThat(t.setValidator(type -> !type.isTemplateType())).isFalse();
  }

  @Test
  public void testTemplateTypeHasReferenceName() {
    TemplateType t = new TemplateType(registry, "T");

    assertType(t).getReferenceNameIsEqualTo("T");
  }

  @Test
  public void testCanCastTo() {
    assertThat(ALL_TYPE.canCastTo(NULL_TYPE)).isTrue();
    assertThat(ALL_TYPE.canCastTo(VOID_TYPE)).isTrue();
    assertThat(ALL_TYPE.canCastTo(STRING_TYPE)).isTrue();
    assertThat(ALL_TYPE.canCastTo(NUMBER_TYPE)).isTrue();
    assertThat(ALL_TYPE.canCastTo(BOOLEAN_TYPE)).isTrue();
    assertThat(ALL_TYPE.canCastTo(OBJECT_TYPE)).isTrue();

    assertThat(NUMBER_TYPE.canCastTo(NULL_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canCastTo(VOID_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canCastTo(STRING_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canCastTo(NUMBER_TYPE)).isTrue();
    assertThat(NUMBER_TYPE.canCastTo(BOOLEAN_TYPE)).isFalse();
    assertThat(NUMBER_TYPE.canCastTo(OBJECT_TYPE)).isFalse();

    assertThat(STRING_TYPE.canCastTo(NULL_TYPE)).isFalse();
    assertThat(STRING_TYPE.canCastTo(VOID_TYPE)).isFalse();
    assertThat(STRING_TYPE.canCastTo(STRING_TYPE)).isTrue();
    assertThat(STRING_TYPE.canCastTo(NUMBER_TYPE)).isFalse();
    assertThat(STRING_TYPE.canCastTo(BOOLEAN_TYPE)).isFalse();
    assertThat(STRING_TYPE.canCastTo(OBJECT_TYPE)).isFalse();

    assertThat(BOOLEAN_TYPE.canCastTo(NULL_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canCastTo(VOID_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canCastTo(STRING_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canCastTo(NUMBER_TYPE)).isFalse();
    assertThat(BOOLEAN_TYPE.canCastTo(BOOLEAN_TYPE)).isTrue();
    assertThat(BOOLEAN_TYPE.canCastTo(OBJECT_TYPE)).isFalse();

    assertThat(OBJECT_TYPE.canCastTo(NULL_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canCastTo(VOID_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canCastTo(STRING_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canCastTo(NUMBER_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canCastTo(BOOLEAN_TYPE)).isFalse();
    assertThat(OBJECT_TYPE.canCastTo(OBJECT_TYPE)).isTrue();

    assertThat(BOOLEAN_TYPE.canCastTo(OBJECT_NUMBER_STRING)).isFalse();
    assertThat(OBJECT_NUMBER_STRING.canCastTo(BOOLEAN_TYPE)).isFalse();

    assertThat(ARRAY_TYPE.canCastTo(FUNCTION_TYPE)).isFalse();
    assertThat(FUNCTION_TYPE.canCastTo(ARRAY_TYPE)).isFalse();

    assertThat(NULL_VOID.canCastTo(ARRAY_TYPE)).isFalse();
    assertThat(NULL_VOID.canCastTo(createUnionType(ARRAY_TYPE, NULL_TYPE))).isTrue();

    // We currently allow any function to be cast to any other function type
    assertThat(ARRAY_FUNCTION_TYPE.canCastTo(BOOLEAN_OBJECT_FUNCTION_TYPE)).isTrue();
  }

  @Test
  public void testEqualityOfClassTypes_withSameReferenceName_preResolution() {
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      FunctionType classACtor =
          FunctionType.builder(registry).forConstructor().withName("Foo").build();

      FunctionType classBCtor =
          FunctionType.builder(registry).forConstructor().withName("Foo").build();

      // Currently, to handle NamedTypes, we treat unresolved type equality as purely based on
      // reference name.
      assertType(classACtor.getInstanceType()).isEqualTo(classBCtor.getInstanceType());
    }
  }

  @Test
  public void testEqualityOfClassTypes_withSameReferenceName_differentGoogModuleId_preResolution() {
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      FunctionType classACtor =
          FunctionType.builder(registry)
              .forConstructor()
              .withName("Foo")
              .setGoogModuleId("module1")
              .build();

      FunctionType classBCtor =
          FunctionType.builder(registry).forConstructor().withName("Foo").build();

      // the different goog module id indicate the types are not equal.
      assertType(classACtor.getInstanceType()).isNotEqualTo(classBCtor.getInstanceType());
    }
  }

  @Test
  public void testEqualityOfClassTypes_withSameReferenceName_sameGoogModuleId_preResolution() {
    try (JSTypeResolver.Closer closer = registry.getResolver().openForDefinition()) {
      FunctionType classACtor =
          FunctionType.builder(registry)
              .forConstructor()
              .withName("Foo")
              .setGoogModuleId("module1")
              .build();

      FunctionType classBCtor =
          FunctionType.builder(registry)
              .forConstructor()
              .withName("Foo")
              .setGoogModuleId("module1")
              .build();

      // Currently, to handle NamedTypes, we treat unresolved type equality as purely based on
      // reference name & goog module id.
      assertType(classACtor.getInstanceType()).isEqualTo(classBCtor.getInstanceType());
    }
  }

  @Test
  public void testEqualityOfClassTypes_withSameReferenceName_postResolution() {
    FunctionType classACtor =
        withOpenRegistry(
            () -> FunctionType.builder(registry).forConstructor().withName("Foo").build());
    FunctionType classBCtor =
        withOpenRegistry(
            () -> FunctionType.builder(registry).forConstructor().withName("Foo").build());

    assertType(classACtor).isNotEqualTo(classBCtor);
    assertType(classACtor.getInstanceType()).isNotEqualTo(classBCtor.getInstanceType());
    assertType(classACtor.getPrototype()).isNotEqualTo(classBCtor.getPrototype());
  }

  @Test
  public void testEqualityOfInterfaceTypes_withSameReferenceName_postResolution() {
    FunctionType interfaceACtor =
        FunctionType.builder(registry).forInterface().withName("Foo").build();

    FunctionType interfaceBCtor =
        FunctionType.builder(registry).forInterface().withName("Foo").build();


    assertType(interfaceACtor).isNotEqualTo(interfaceBCtor);
    assertType(interfaceACtor.getInstanceType()).isNotEqualTo(interfaceBCtor.getInstanceType());
    assertType(interfaceACtor.getPrototype()).isNotEqualTo(interfaceBCtor.getPrototype());
  }

  @Test
  public void testRecordTypeEquality() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    JSType secondType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));

    assertType(firstType).isNotSameInstanceAs(secondType);
    assertType(firstType).isEqualTo(secondType);
    assertType(firstType).isEqualTo(secondType);
  }

  @Test
  public void testRecordAndInterfaceObjectTypeNotEqualWithSameProperties() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    // /** @interface */
    // function Foo() {}
    // /** @type {number} */
    // Foo.prototype.x;
    FunctionType secondTypeConstructor =
        FunctionType.builder(registry).forInterface().withName("Foo").build();

    secondTypeConstructor.getPrototype().defineProperty("x", NUMBER_TYPE, false, null);
    secondTypeConstructor.setImplicitMatch(true);
    JSType secondType = secondTypeConstructor.getInstanceType();

    // These are not equal but are structurally equivalent
    assertType(firstType).isNotEqualTo(secondType);
  }

  @Test
  public void testRecordAndObjectLiteralWithSameProperties() {
    // {x: number}
    JSType firstType = registry.createRecordType(ImmutableMap.of("x", NUMBER_TYPE));
    assertType(firstType).toStringIsEqualTo("{x: number}");
    // the type inferred for `const obj = {x: 1};`
    ObjectType secondType = registry.createAnonymousObjectType(null);
    secondType.defineDeclaredProperty("x", NUMBER_TYPE, null);
    assertType(secondType).toStringIsEqualTo("{x: number}");

    // These are neither equal nor structurally equivalent because the second type is not a
    // structural type.
    assertType(firstType).isNotEqualTo(secondType);

    // The second type is a subtype of the first type but not vice versa because only the first type
    // is structural.
    assertType(firstType).isNotSubtypeOf(secondType);
    assertType(secondType).isSubtypeOf(firstType);
  }

  private <T> T withOpenRegistry(Supplier<T> cb) {
    try (JSTypeResolver.Closer closer = this.registry.getResolver().openForDefinition()) {
      return cb.get();
    }
  }
}
