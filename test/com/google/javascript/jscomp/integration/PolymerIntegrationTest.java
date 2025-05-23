/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for the compiler with the --polymer_pass enabled. */
@RunWith(JUnit4.class)
public final class PolymerIntegrationTest extends IntegrationTestCase {

  private static final String EXPORT_PROPERTY_DEF =
      """
      goog.exportProperty = function(object, publicName, symbol) {
        object[publicName] = symbol;
      };
      """;

  /** Creates a CompilerOptions object with google coding conventions. */
  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    options.setClosurePass(true);
    return options;
  }

  private void addPolymerExterns() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.add(
        new TestExternsBuilder()
            .addObject()
            .addClosureExterns()
            .addPolymer()
            .addExtra(
                """
                /**
                 * @see https://html.spec.whatwg.org/multipage/custom-elements.html#customelementregistry
                 * @constructor
                 */
                function CustomElementRegistry() {}
                """,
                """
                /**
                 * @param {string} tagName
                 * @param {function(new:HTMLElement)} klass
                 * @param {{extends: string}=} options
                 * @return {undefined}
                 */
                CustomElementRegistry.prototype.define = function (tagName, klass, options) {};
                """,
                """
                /**
                 * @param {string} tagName
                 * @return {function(new:HTMLElement)|undefined}
                 */
                CustomElementRegistry.prototype.get = function(tagName) {};
                """,
                """
                /**
                 * @param {string} tagName
                 * @return {!Promise<undefined>}
                 */
                CustomElementRegistry.prototype.whenDefined = function(tagName) {};
                """,
                """
                /** @type {!CustomElementRegistry} */
                var customElements;
                """)
            .buildExternsFile("polymer_externs.js"));
    externs = externsList.build();
  }

  @Test
  public void testPolymerCorrectlyResolvesGlobalTypedefs_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    addPolymerExterns();

    test(
        options,
        """
        /** @typedef {{foo: string}} */
        let MyTypedef;
        (function() {
        Polymer({
        is: 'x-foo',
        properties: {
        /** @type {!MyTypedef} */
        value: string,
        },
        });
        })();
        """,
        """
        var $jscomp = $jscomp || {};
        $jscomp.scope = {};
        $jscomp.reflectObject = function(type, object) { return object; };
        var XFooElement=function(){};
        var MyTypedef;
        (function(){
        XFooElement.prototype.value;
        Polymer({
          is:'x-foo',
          properties: $jscomp.reflectObject(XFooElement, {
            value:string})
          });
        })()
        """);
  }

  @Test
  public void testPolymerPropertiesDoNotGetRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setGeneratePseudoNames(true); // to make the expected output easier to follow
    addPolymerExterns();

    test(
        options,
        """
        (function() {
          Polymer({
            is: 'foo',
            properties: {
             /** @type {{randomProperty: string}} */
             value: Object
          }
          });
        })();

        const obj = {randomProperty: 0, otherProperty: 1};
        """,
        EMPTY_JOINER.join(
            "var $$jscomp$$ = $$jscomp$$ || {};",
            "$$jscomp$$.scope = {};",
            "$$jscomp$$.$reflectObject$ = function($type$$, $object$$) { return $object$$; };",
            "var $FooElement$$ = function(){};",
            "(function(){",
            "  $FooElement$$.prototype.value;",
            "  Polymer({",
            "    $is$:\"foo\",",
            "    $properties$: $$jscomp$$.$reflectObject$($FooElement$$, {value:Object})",
            "  });",
            "})();",
            // Note that randomProperty is not renamed (no '$' added) while otherProperty is
            "var $obj$$ = {randomProperty:0, $otherProperty$: 1};"));
  }

  @Test
  public void testPolymerImplicitlyUnknownPropertiesDoNotGetRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    options.setGeneratePseudoNames(true); // to make the expected output easier to follow
    addPolymerExterns();

    test(
        options,
        """
        (function() {
          Polymer({
            is: 'foo',
            properties: {
             /** @type {{randomProperty}} */
             value: Object
          }
          });
        })();

        const obj = {randomProperty: 0, otherProperty: 1};
        """,
        EMPTY_JOINER.join(
            "var $$jscomp$$ = $$jscomp$$ || {};",
            "$$jscomp$$.scope = {};",
            "$$jscomp$$.$reflectObject$ = function($type$$, $object$$) { return $object$$; };",
            "var $FooElement$$ = function(){};",
            "(function(){",
            "  $FooElement$$.prototype.value;",
            "  Polymer({",
            "    $is$:\"foo\",",
            "    $properties$: $$jscomp$$.$reflectObject$($FooElement$$, {value:Object})",
            "  });",
            "})();",
            // Note that randomProperty is not renamed (no '$' added) while otherProperty is
            "var $obj$$ = {randomProperty:0, $otherProperty$: 1};"));
  }

  @Test
  public void testPolymerCorrectlyResolvesUserDefinedLocalTypedefs_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    addPolymerExterns();

    test(
        options,
        """
        (function() {
        /** @typedef {{foo: string}} */
        let localTypeDef;
        Polymer({
        is: 'x-foo',
        properties: {
        /** @type {localTypeDef} */
        value: string,
        },
        });
        })();
        """,
        """
        var $jscomp = $jscomp || {};
        $jscomp.scope = {};
        $jscomp.reflectObject = function(type, object) { return object; };
        var XFooElement=function(){};
        (function(){
        XFooElement.prototype.value;
        var localTypeDef;
        Polymer({
          is:'x-foo',
          properties: $jscomp.reflectObject(XFooElement, {value:string})
        })})()
        """);
  }

  @Test
  public void testPolymerCorrectlyResolvesPrimitiveLocalTypes_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setClosurePass(true);
    addPolymerExterns();

    test(
        options,
        """
        (function() {
        Polymer({
        is: 'x-foo',
        properties: {
        /** @type {string} */
        value: string,
        },
        });
        })();
        """,
        """
        var $jscomp = $jscomp || {};
        $jscomp.scope = {};
        $jscomp.reflectObject = function(type, object) { return object; };
        var XFooElement=function(){};
        (function(){
        XFooElement.prototype.value;
        Polymer({
          is:'x-foo',
          properties: $jscomp.reflectObject(XFooElement, {value:string}) })
        })()
        """);
  }

  @Test
  public void testPolymerCorrectlyResolvesLocalTypes_forModules() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setClosurePass(true);
    addPolymerExterns();
    test(
        options,
        """
        goog.module('a');
        /** @typedef {{foo: number}} */
        let MyTypedef;
        Polymer({
        is: 'x-foo',
        properties: {
        /** @type {MyTypedef} */
        value: number,
        },
        });
        """,
        """
        var $jscomp = $jscomp || {};
        $jscomp.scope = {};
        $jscomp.reflectObject = function(type, object) { return object; };
        var XFooElement=function(){};
        var module$exports$a={};
        XFooElement.prototype.value;
        var module$contents$a_MyTypedef;
        Polymer({
        is:"x-foo",
        properties: $jscomp.reflectObject(XFooElement, {value:number})
        })
        """);
  }

  @Test
  public void testPolymerCallWithinES6Modules_CreatesDeclarationOutsideModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    String srcs =
        """
        Polymer({
          is: 'x',
        });
        export {}
        """;

    String compiledOut =
        """
        /** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */
        var XElement = function() {};
        Polymer(/** @lends {X.prototype} */ {
          is: 'x',
        });
        var module$i0={}
        """;

    test(options, srcs, compiledOut);
  }

  @Test
  public void testPolymer1() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        "var XFoo = Polymer({ is: 'x-foo' });",
        "var XFoo=function(){};XFoo=Polymer({is:'x-foo'})");
  }

  @Test
  public void testPolymerBehaviorLegacyGoogModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();

    test(
        options,
        new String[] {
          """
          goog.module('behaviors.FunBehavior');
          goog.module.declareLegacyNamespace();
          /** @polymerBehavior */
          exports = {};
          """,
          "var XFoo = Polymer({ is: 'x-foo', behaviors: [ behaviors.FunBehavior ] });"
        },
        new String[] {
          "var behaviors = {}; behaviors.FunBehavior = {};",
          """
          var XFoo=function(){};
          XFoo = Polymer({
            is:'x-foo',
            behaviors: [ behaviors.FunBehavior ]
          });
          """
        });
  }

  @Test
  public void testDuplicatePropertyNames_transitive() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();
    testNoWarnings(
        options,
        new String[] {
          """
          goog.module('A');
          /** @polymerBehavior */
          const FunBehavior = {
            properties: {
              isFun: Boolean
            },
          };

          /** @polymerBehavior */
          const RadBehavior = {
            properties: {
              howRad: Number
            },
          };

          /** @polymerBehavior */
          const SuperCoolBehaviors = [FunBehavior, RadBehavior];
          exports = {SuperCoolBehaviors, FunBehavior}
          """,
          """
          goog.module('B')
          const {SuperCoolBehaviors, FunBehavior} = goog.require('A')
          A = Polymer({
            is: 'x-element',
            properties: {
              isFun: {
                type: Array,
                notify: true,
              },
              name: String,
            },
            behaviors: [ SuperCoolBehaviors, FunBehavior ],
          });
          """
        });
  }

  /**
   * Tests that no reference to the 'Data' module's local variable 'Item' gets generated in the
   * 'Client' module by the PolymerClassRewriter.
   */
  @Test
  public void testNoUnrecognizedTypeErrorForBehaviorInsideGoogModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();
    testNoWarnings(
        options,
        new String[] {
          """
          goog.module('Data');
          class Item {
          }
          exports.Item = Item;
          /**
           * A Polymer behavior providing common data access and formatting methods.
           * @polymerBehavior
           */
          exports.SummaryDataBehavior = {
            /**
             * @param {?Item} item
             * @return {*}
             * @export
             */
            getValue(item) {
              return item;
            },
          };
          """,
          """
          goog.module('Client');
          const Data = goog.require('Data');
          var A = Polymer({
            is: 'x-element',
            behaviors: [ Data.SummaryDataBehavior ],
          });
          """
        });
  }

  @Test
  public void testConstPolymerElementAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    testNoWarnings(options, "const Foo = Polymer({ is: 'x-foo' });");
  }

  // Regression test for b/77650996
  @Test
  public void testPolymer2b() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        new String[] {
          """
          class DeviceConfigEditor extends Polymer.Element {

            static get is() {
              return 'device-config-editor';
            }

            static get properties() {
              return {};
            }
          }

          window.customElements.define(DeviceConfigEditor.is, DeviceConfigEditor);
          """,
          """
          (function() {
            /**
             * @customElement
             * @polymer
             * @memberof Polymer
             * @constructor
             * @implements {Polymer_ElementMixin}
             * @extends {HTMLElement}
             */
            const Element = Polymer.ElementMixin(HTMLElement);
          })();
          """,
        },
        (String[]) null);
  }

  @Test
  public void testPolymer1b() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        new String[] {
          """
          Polymer({
            is: 'paper-button'
          });
          """,
          """
          (function() {
            /**
             * @customElement
             * @polymer
             * @memberof Polymer
             * @constructor
             * @implements {Polymer_ElementMixin}
             * @extends {HTMLElement}
             */
            const Element = Polymer.ElementMixin(HTMLElement);
          })();
          """,
        },
        (String[]) null);
  }

  @Test
  public void testPolymer2a() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            """
            class XFoo extends Polymer.Element {
              get is() { return 'x-foo'; }
              static get properties() { return {}; }
            }
            """);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerElementImportedFromEsModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            new String[] {
              "export class PolymerElement {};",
              """
              import {PolymerElement} from './i0.js';
              class Foo extends PolymerElement {
                get is() { return 'foo-element'; }
                static get properties() { return { fooProp: String }; }
              }
              const foo = new Foo();
              // This property access would be an unknown property error unless the PolymerPass
              // had successfully parsed the element definition.
              foo.fooProp;
              """
            });
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerFunctionImportedFromEsModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            new String[] {
              "export function Polymer(def) {};",
              """
              import {Polymer} from './i0.js';
              Polymer({
                is: 'foo-element',
                properties: { fooProp: String },
              });
              // This interface cast and property access would be an error unless the
              // PolymerPass had successfully parsed the element definition.
              const foo = /** @type{!FooElementElement} */({});
              foo.fooProp;
              """
            });
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  /** See b/64389806. */
  @Test
  public void testPolymerBehaviorWithTypeCast() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        """
        Polymer({
          is: 'foo-element',
          behaviors: [
            ((/** @type {?} */ (Polymer))).SomeBehavior
          ]
        });
        /** @polymerBehavior */
        Polymer.SomeBehavior = {};
        """,
        """
        var FooElementElement=function(){};
        Polymer({
          is:"foo-element",
          behaviors:[Polymer.SomeBehavior]
        });
        Polymer.SomeBehavior={}
        """);
  }

  @Test
  public void testPolymerExportPolicyExportAllClassBased() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    addPolymerExterns();

    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);

    Compiler compiler =
        compile(
            options,
            EXPORT_PROPERTY_DEF
                + """
                class FooElement extends PolymerElement {
                  static get properties() {
                    return {
                      longUnusedProperty: String,
                    }
                  }
                  longUnusedMethod() {
                    return this.longUnusedProperty;
                  }
                }
                """);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected it against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerExportPolicyExportAllLegacyElement() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    addPolymerExterns();

    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);

    Compiler compiler =
        compile(
            options,
            EXPORT_PROPERTY_DEF
                + """
                Polymer({
                  is: "foo-element",
                  properties: {
                    longUnusedProperty: String,
                  },
                  longUnusedMethod: function() {
                    return this.longUnusedProperty;
                  },
                });
                """);
    String source = compiler.toSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected them against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerPropertyDeclarationsWithConstructor() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            EXPORT_PROPERTY_DEF
                + """
                class FooElement extends PolymerElement {
                  constructor() {
                    super();
                    /** @type {number} */
                    this.p1 = 0;
                    /** @type {string|undefined} */
                    this.p2;
                    if (condition) {
                      this.p3 = true;
                    }
                  }
                  static get properties() {
                    return {
                      /** @type {boolean} */
                      p1: String,
                      p2: String,
                      p3: Boolean,
                      p4: Object,
                      /** @type {number} */
                      p5: String,
                    };
                  }

                  // p1 has 3 possible types that could win out: 1) string (inferred from the
                  // Polymer attribute de-serialization function), 2) boolean (from the @type
                  // annotation in the properties configuration), 3) number (from the @type
                  // annotation in the constructor). We want the constructor annotation to win
                  // (number). If it didn't, this method signature would have a type error.
                  /** @return {number}  */ getP1() { return this.p1; }
                  /** @return {string|undefined}  */ getP2() { return this.p2; }
                  /** @return {boolean} */ getP3() { return this.p3; }
                  /** @return {!Object} */ getP4() { return this.p4; }
                  /** @return {number}  */ getP5() { return this.p5; }
                }
                """);

    assertThat(compiler.getErrors()).isEmpty();

    // We should have one warning: that property p1 shouldn't have any JSDoc inside the properties
    // configuration, because when a property is also declared in the constructor, the constructor
    // JSDoc will take precedence.
    ImmutableList<JSError> warnings = compiler.getWarnings();
    assertThat(warnings).hasSize(1);
    JSError warning = warnings.get(0);
    assertThat(warning.node().getString()).isEqualTo("p1");
  }

  @Test
  public void testDisambiguationForPolymerElementProperties() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setDisambiguateProperties(true);
    options.setGeneratePseudoNames(true);
    addPolymerExterns();
    externs =
        ImmutableList.<SourceFile>builder()
            .addAll(externs)
            .add(
                new TestExternsBuilder()
                    .addString()
                    .addConsole()
                    .buildExternsFile("extra_externs.js"))
            .build();

    String fooElement =
        """
        const FooElement = Polymer({
          is: "foo-element",
          properties: {
            longUnusedProperty: String,
          },
          longUnusedMethod: function() {
            return this.longUnusedProperty;
          },
        });
        class Other { longUnusedProperty() {} }
        console.log(new Other().longUnusedProperty());
        """;
    test(
        options,
        new String[] {fooElement, "function unused() { console.log(FooElement); }"},
        new String[] {
          """
          Polymer({
            $is$: 'foo-element',
          // Ensure the compiler doesn't rename the references to longUnusedProperty here and in
          // longUnusedMethod. They may be referenced from templates or computed property
          // definitions. It's ok to disambiguate/rename the longUnusedProperty method on the
          // `class Other {` though.
            $properties$: { longUnusedProperty: String },
            longUnusedMethod: function(){ return this.longUnusedProperty; }
          });
          console.log(void 0);
          """,
          ""
        });
  }
}
