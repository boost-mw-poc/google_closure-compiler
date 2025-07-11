/*
 * Copyright 2008 The Closure Compiler Authors
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

/**
 * @fileoverview ECMAScript 3 Built-Ins. This include common extensions so this
 * is actually ES3+Reality.
 * @externs
 * @author stevey@google.com (Steve Yegge)
 * @author nicksantos@google.com (Nick Santos)
 * @author arv@google.com (Erik Arvidsson)
 * @author johnlenz@google.com (John Lenz)
 */


// START ES6 RETROFIT CODE
// symbol, Symbol and Symbol.iterator are actually ES6 types but some
// base types require them to be part of their definition (such as Array).


/**
 * @constructor
 * @param {*=} opt_description
 * @return {symbol}
 * @nosideeffects
 * Note: calling `new Symbol('x');` will always throw, but we mark this
 * nosideeffects because the compiler does not promise to preserve all coding
 * errors.
 */
function Symbol(opt_description) {}


/**
 * @const {string|undefined}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/description
 */
Symbol.prototype.description;


/**
 * @param {string} sym
 * @return {symbol}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/for
 */
Symbol.for = function(sym) {};


/**
 * @param {symbol} sym
 * @return {string|undefined}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/keyFor
 */
Symbol.keyFor = function(sym) {};


// Well known symbols

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/asyncIterator
 * @const {symbol}
 */
Symbol.asyncIterator;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/hasInstance
 * @const {symbol}
 */
Symbol.hasInstance;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/isConcatSpreadable
 * @const {symbol}
 */
Symbol.isConcatSpreadable;

/**
 * @const {symbol}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/iterator
 */
Symbol.iterator;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/match
 * @const {symbol}
 */
Symbol.match;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/matchAll
 * @const {symbol}
 */
Symbol.matchAll;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/replace
 * @const {symbol}
 */
Symbol.replace;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/search
 * @const {symbol}
 */
Symbol.search;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/species
 * @const {symbol}
 */
Symbol.species;

// /**
//  * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/split
//  * @const {symbol}
//  */
// Symbol.split;

/**
 * @const {symbol}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/toPrimitive
 */
Symbol.toPrimitive;

/**
 * @const {symbol}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/toStringTag
 */
Symbol.toStringTag;

/**
 * @const {symbol}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Symbol/unscopables
 */
Symbol.unscopables;


/**
 * @record
 * @template TYield
 */
function IIterableResult() {};

/** @type {boolean} */
IIterableResult.prototype.done;

/** @type {TYield} */
IIterableResult.prototype.value;



/**
 * @interface
 * @template T, TReturn, TNext
 */
function Iterable() {}

/**
 * @return {!Iterator<T, ?, *>}
 */
Iterable.prototype[Symbol.iterator] = function() {};



/**
 * TODO(b/142881197): TReturn and TNext are not yet used for anything.
 * https://github.com/google/closure-compiler/issues/3489
 * @interface
 * @template T, TReturn, TNext
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/The_Iterator_protocol
 */
function Iterator() {}

/**
 * @param {?=} opt_value
 * @return {!IIterableResult<T>}
 */
Iterator.prototype.next = function(opt_value) {};


/**
 * Use this to indicate a type is both an Iterator and an Iterable.
 *
 * @interface
 * @extends {Iterator<T, ?, *>}
 * @extends {Iterable<T, ?, *>}
 * @template T, TReturn, TNext
 */
function IteratorIterable() {}

// END ES6 RETROFIT CODE


/**
 * @interface
 * @template IOBJECT_KEY, IOBJECT_VALUE
 */
function IObject() {}

/**
 * @record
 * @extends {IObject<number, VALUE2>}
 * @template VALUE2
 */
function IArrayLike() {}

/** @type {number} */
IArrayLike.prototype.length;

/**
 * @constructor
 * @implements {IArrayLike<?>}
 * @implements {Iterable<?>}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Functions_and_function_scope/arguments
 */
function Arguments() {}

/** @override */
Arguments.prototype[Symbol.iterator] = function() {};

/**
 * @type {Function}
 * @see http://developer.mozilla.org/En/Core_JavaScript_1.5_Reference/Functions_and_function_scope/arguments/callee
 */
Arguments.prototype.callee;

/**
 * Use the non-standard {@see Function.prototype.caller} property of a function
 * object instead.
 * @type {Function}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Functions/arguments/caller
 * @deprecated
 */
Arguments.prototype.caller;

/**
 * @type {number}
 * @see http://developer.mozilla.org/En/Core_JavaScript_1.5_Reference/Functions_and_function_scope/arguments/length
 */
Arguments.prototype.length;

/**
 * Not actually a global variable, when running in a browser environment. But
 * we need it in order for the type checker to typecheck the "arguments"
 * variable in a function correctly.
 *
 * TODO(tbreisacher): There should be a separate 'arguments' variable of type
 * `Array<string>`, in the d8 externs.
 *
 * @type {!Arguments}
 * @see http://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Functions_and_function_scope/arguments
 */
var arguments;

/**
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Infinity
 * @const
 */
var Infinity;

/**
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/NaN
 * @const
 */
var NaN;

/**
 * @type {undefined}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/undefined
 * @const
 */
var undefined;

/**
 * @param {string} uri
 * @return {string}
 * @throws {URIError} when used wrongly.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/decodeURI
 */
function decodeURI(uri) {}

/**
 * @param {string} uri
 * @return {string}
 * @throws {URIError} when used wrongly.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/decodeURIComponent
 */
function decodeURIComponent(uri) {}

/**
 * @param {string} uri
 * @return {string}
 * @throws {URIError} if one attempts to encode a surrogate which is not part of
 * a high-low pair.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURI
 */
function encodeURI(uri) {}

/**
 * @param {string} uri
 * @return {string}
 * @throws {URIError} if one attempts to encode a surrogate which is not part of
 * a high-low pair.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent
 */
function encodeURIComponent(uri) {}

/**
 * Should only be used in browsers where encode/decodeURIComponent
 * are not present, as the latter handle fancy Unicode characters.
 * @param {string} str
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Guide/Predefined_Functions/escape_and_unescape_Functions
 */
function escape(str) {}

/**
 * Should only be used in browsers where encode/decodeURIComponent
 * are not present, as the latter handle fancy Unicode characters.
 * @param {string} str
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Guide/Predefined_Functions/escape_and_unescape_Functions
 */
function unescape(str) {}

/**
 * @param {*} num
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/isFinite
 */
function isFinite(num) {}

/**
 * @param {*} num
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/isNaN
 */
function isNaN(num) {}

/**
 * @param {*} num
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/parseFloat
 */
function parseFloat(num) {}

/**
 * Parse an integer. Use of `parseInt` without `base` is strictly
 * banned in Google. If you really want to parse octal or hex based on the
 * leader, then pass `undefined` as the base.
 *
 * @param {*} num
 * @param {number|undefined} base
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/parseInt
 */
function parseInt(num, base) {}


/**
 * Represents a string of JavaScript code that is known to have come from a
 * trusted source. Part of Trusted Types.
 *
 * The main body Trusted Types type definitions reside in the  file
 * `w3c_trusted_types.js`. This definition was placed here so that it would be
 * accessible to `eval()`.
 *
 * @constructor
 * @see https://w3c.github.io/webappsec-trusted-types/dist/spec/#trusted-script
 */
function TrustedScript() {}


/**
 * @param {string|!TrustedScript} code
 * @return {*}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/eval
 */
function eval(code) {}



/**
 * @constructor
 * @param {*=} opt_value
 * @return {!Object}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object
 */
function Object(opt_value) {}

/**
 * The constructor of the current object.
 * @type {Function}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/constructor
 */
Object.prototype.constructor = function() {};

/**
 * Binds an object's property to a function to be called when that property is
 * looked up.
 * Mozilla-only.
 *
 * @param {string} sprop
 * @param {Function} fun
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/defineGetter
 * @return {undefined}
 * @deprecated
 */
Object.prototype.__defineGetter__ = function(sprop, fun) {};

/**
 * Binds an object's property to a function to be called when an attempt is made
 * to set that property.
 * Mozilla-only.
 *
 * @param {string} sprop
 * @param {Function} fun
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/defineSetter
 * @return {undefined}
 * @deprecated
 */
Object.prototype.__defineSetter__ = function(sprop, fun) {};

/**
 * Returns whether the object has a property with the specified name.
 *
 * @param {*} propertyName Implicitly cast to a string.
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/hasOwnProperty
 */
Object.prototype.hasOwnProperty = function(propertyName) {};

/**
 * Returns whether an object exists in another object's prototype chain.
 *
 * @param {Object} other
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/isPrototypeOf
 */
Object.prototype.isPrototypeOf = function(other) {};

/**
 * Return the function bound as a getter to the specified property.
 * Mozilla-only.
 *
 * @param {string} sprop a string containing the name of the property whose
 * getter should be returned
 * @return {Function}
 * @nosideeffects
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/lookupGetter
 */
Object.prototype.__lookupGetter__ = function(sprop) {};

/**
 * Return the function bound as a setter to the specified property.
 * Mozilla-only.
 *
 * @param {string} sprop a string containing the name of the property whose
 *     setter should be returned.
 * @return {Function}
 * @nosideeffects
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/lookupSetter
 */
Object.prototype.__lookupSetter__ = function(sprop) {};

/**
 * Executes a function when a non-existent method is called on an object.
 * Mozilla-only.
 *
 * @param {Function} fun
 * @return {*}
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/noSuchMethod
 */
Object.prototype.__noSuchMethod__ = function(fun) {};

/**
 * Points to an object's context.  For top-level objects, this is the e.g. window.
 * Mozilla-only.
 *
 * @type {Object}
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/parent
 */
Object.prototype.__parent__;

/**
 * Points to the object which was used as prototype when the object was instantiated.
 * Mozilla-only.
 *
 * Will be null on Object.prototype.
 *
 * @type {Object}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/proto
 */
Object.prototype.__proto__;

/**
 * Determine whether the specified property in an object can be enumerated by a
 * for..in loop, with the exception of properties inherited through the
 * prototype chain.
 *
 * @param {string|symbol} propertyName
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/propertyIsEnumerable
 */
Object.prototype.propertyIsEnumerable = function(propertyName) {};

/**
 * Returns a localized string representing the object.
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/toLocaleString
 */
Object.prototype.toLocaleString = function() {};

/**
 * Returns a string representing the source code of the object.
 * Mozilla-only.
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/toSource
 */
Object.prototype.toSource = function() {};

/**
 * Returns a string representing the object.
 * @this {*}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/toString
 */
Object.prototype.toString = function() {};

/**
 * Returns the object's `this` value.
 * @return {*}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/valueOf
 */
Object.prototype.valueOf = function() {};

/**
 * @constructor
 * @param {...*} var_args
 * @throws {Error}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function
 */
function Function(var_args) {}

/**
 * @param {...*} var_args
 * @return {*}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/call
 */
Function.prototype.call = function(var_args) {};

/**
 * @param {...*} var_args
 * @return {*}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/apply
 */
Function.prototype.apply = function(var_args) {};

Function.prototype.arguments;

/**
 * @type {number}
 * @deprecated
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/arity
 */
Function.prototype.arity;

/**
 * Nonstandard; Mozilla and JScript only.
 * @type {Function}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/caller
 */
Function.prototype.caller;

/**
 * Nonstandard.
 * @type {?}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/displayName
 */
Function.prototype.displayName;

/**
 * Expected number of arguments.
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/length
 */
Function.prototype.length;

/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/name
 */
Function.prototype.name;

/**
 * @this {Function}
 * @return {string}
 * @nosideeffects
 * @override
 */
Function.prototype.toString = function() {};


/**
 * @record
 * @extends {IArrayLike<T>}
 * @extends {Iterable<T>}
 * @template T
 */
function ReadonlyArray() {}

/**
 * @return {!IteratorIterable<T>}
 * @override
 */
ReadonlyArray.prototype[Symbol.iterator] = function() {};

/**
 * Returns a new array comprised of this array joined with other array(s)
 * and/or value(s).
 *
 * @param {...*} var_args
 * @return {!Array<?>}
 * @this {*}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/concat
 */
ReadonlyArray.prototype.concat = function(var_args) {};

/**
 * Joins all elements of an array into a string.
 *
 * @param {*=} opt_separator Specifies a string to separate each element of the
 *     array. The separator is converted to a string if necessary. If omitted,
 *     the array elements are separated with a comma.
 * @return {string}
 * @this {IArrayLike<?>|string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/join
 */
ReadonlyArray.prototype.join = function(opt_separator) {};

/**
 * Extracts a section of an array and returns a new array.
 *
 * @param {?number=} begin Zero-based index at which to begin extraction.
 * @param {?number=} end Zero-based index at which to end extraction.  slice
 *     extracts up to but not including end.
 * @return {!Array<T>}
 * @this {IArrayLike<T>|string}
 * @template T
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/slice
 */
ReadonlyArray.prototype.slice = function(begin, end) {};

/**
 * @this {ReadonlyArray<?>}
 * @return {string}
 * @nosideeffects
 * @override
 */
ReadonlyArray.prototype.toString = function() {};

/**
 * Apply a function simultaneously against two values of the array (from
 * left-to-right) as to reduce it to a single value.
 *
 * @param {?function(?, T, number, !ReadonlyArray<T>) : R} callback
 * @param {*=} opt_initialValue
 * @return {R}
 * @this {IArrayLike<T>|string}
 * @template T,R
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reduce
 */
ReadonlyArray.prototype.reduce = function(callback, opt_initialValue) {};

/**
 * Apply a function simultaneously against two values of the array (from
 * right-to-left) as to reduce it to a single value.
 *
 * @param {?function(?, T, number, !ReadonlyArray<T>) : R} callback
 * @param {*=} opt_initialValue
 * @return {R}
 * @this {IArrayLike<T>|string}
 * @template T,R
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reduceRight
 */
ReadonlyArray.prototype.reduceRight = function(callback, opt_initialValue) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !ReadonlyArray<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {boolean}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/every
 */
ReadonlyArray.prototype.every = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !ReadonlyArray<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {!Array<T>}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/filter
 */
ReadonlyArray.prototype.filter = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !ReadonlyArray<T>): ?} callback
 * @param {S=} opt_thisobj
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach
 * @return {undefined}
 */
ReadonlyArray.prototype.forEach = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @this {IArrayLike<T>|string}
 * @nosideeffects
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/indexOf
 */
ReadonlyArray.prototype.indexOf = function(obj, opt_fromIndex) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @this {IArrayLike<T>|string}
 * @nosideeffects
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/lastIndexOf
 */
ReadonlyArray.prototype.lastIndexOf = function(obj, opt_fromIndex) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !ReadonlyArray<T>): R} callback
 * @param {S=} opt_thisobj
 * @return {!Array<R>}
 * @this {IArrayLike<T>|string}
 * @template T,S,R
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/map
 */
ReadonlyArray.prototype.map = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !ReadonlyArray<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {boolean}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/some
 */
ReadonlyArray.prototype.some = function(callback, opt_thisobj) {};

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/length
 */
ReadonlyArray.prototype.length;

/**
 * @constructor
 * @implements {IArrayLike<T>}
 * @implements {Iterable<T>}
 * @implements {ReadonlyArray<T>}
 * @param {...*} var_args
 * @return {!Array}
 * @nosideeffects
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
 */
function Array(var_args) {}

/**
 * @return {!IteratorIterable<T>}
 * @override
 */
Array.prototype[Symbol.iterator] = function() {};

// Functions:

/**
 * Returns a new array comprised of this array joined with other array(s)
 * and/or value(s).
 *
 * @param {...*} var_args
 * @return {!Array<?>}
 * @this {*}
 * @nosideeffects
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/concat
 */
Array.prototype.concat = function(var_args) {};

/**
 * Joins all elements of an array into a string.
 *
 * @param {*=} opt_separator Specifies a string to separate each element of the
 *     array. The separator is converted to a string if necessary. If omitted,
 *     the array elements are separated with a comma.
 * @return {string}
 * @this {IArrayLike<?>|string}
 * @nosideeffects
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/join
 */
Array.prototype.join = function(opt_separator) {};

/**
 * Removes the last element from an array and returns that element.
 *
 * @return {T}
 * @this {IArrayLike<T>}
 * @modifies {this}
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/pop
 */
Array.prototype.pop = function() {};

// TODO(bradfordcsmith): remove "undefined" from the var_args of push
/**
 * Mutates an array by appending the given elements and returning the new
 * length of the array.
 *
 * @param {...(T|undefined)} var_args
 * @return {number} The new length of the array.
 * @this {IArrayLike<T>}
 * @template T
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/push
 */
Array.prototype.push = function(var_args) {};

/**
 * Transposes the elements of an array in place: the first array element becomes the
 * last and the last becomes the first. The mutated array is also returned.
 *
 * @return {THIS} A reference to the original modified array.
 * @this {THIS}
 * @template THIS
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reverse
 */
Array.prototype.reverse = function() {};

/**
 * Removes the first element from an array and returns that element. This
 * method changes the length of the array.
 *
 * @this {IArrayLike<T>}
 * @modifies {this}
 * @return {T}
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/shift
 */
Array.prototype.shift = function() {};

/**
 * Extracts a section of an array and returns a new array.
 *
 * @param {?number=} begin Zero-based index at which to begin extraction.
 * @param {?number=} end Zero-based index at which to end extraction.  slice
 *     extracts up to but not including end.
 * @return {!Array<T>}
 * @this {IArrayLike<T>|string}
 * @template T
 * @nosideeffects
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/slice
 */
Array.prototype.slice = function(begin, end) {};

/**
 * Sorts the elements of an array in place.
 *
 * @param {function(T,T):number=} opt_compareFn Specifies a function that
 *     defines the sort order.
 * @this {IArrayLike<T>}
 * @template T
 * @modifies {this}
 * @return {!Array<T>}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/sort
 */
Array.prototype.sort = function(opt_compareFn) {};

/**
 * Changes the content of an array, adding new elements while removing old
 * elements.
 *
 * @param {?number=} index Index at which to start changing the array. If
 *     negative, will begin that many elements from the end.
 * @param {?number=} howMany An integer indicating the number of old array
 *     elements to remove.
 * @param {...T} var_args
 * @return {!Array<T>}
 * @this {IArrayLike<T>}
 * @modifies {this}
 * @template T
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/splice
 */
Array.prototype.splice = function(index, howMany, var_args) {};

/**
 * @return {string}
 * @this {Object}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/toSource
 */
Array.prototype.toSource;

/**
 * @this {Array<?>}
 * @return {string}
 * @nosideeffects
 * @override
 */
Array.prototype.toString = function() {};

/**
 * Adds one or more elements to the beginning of an array and returns the new
 * length of the array.
 *
 * @param {...*} var_args
 * @return {number} The new length of the array
 * @this {IArrayLike<?>}
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/unshift
 */
Array.prototype.unshift = function(var_args) {};

/**
 * Apply a function simultaneously against two values of the array (from
 * left-to-right) as to reduce it to a single value.
 *
 * @param {?function(?, T, number, !Array<T>) : R} callback
 * @param {*=} opt_initialValue
 * @return {R}
 * @this {IArrayLike<T>|string}
 * @template T,R
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reduce
 */
Array.prototype.reduce = function(callback, opt_initialValue) {};

/**
 * Apply a function simultaneously against two values of the array (from
 * right-to-left) as to reduce it to a single value.
 *
 * @param {?function(?, T, number, !Array<T>) : R} callback
 * @param {*=} opt_initialValue
 * @return {R}
 * @this {IArrayLike<T>|string}
 * @template T,R
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/reduceRight
 */
Array.prototype.reduceRight = function(callback, opt_initialValue) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !Array<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {boolean}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/every
 */
Array.prototype.every = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !Array<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {!Array<T>}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/filter
 */
Array.prototype.filter = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !Array<T>): ?} callback
 * @param {S=} opt_thisobj
 * @return {undefined}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach
 */
Array.prototype.forEach = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {T} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @this {IArrayLike<T>|string}
 * @nosideeffects
 * @template T
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/indexOf
 */
Array.prototype.indexOf = function(obj, opt_fromIndex) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {T} obj
 * @param {number=} opt_fromIndex
 * @return {number}
 * @this {IArrayLike<T>|string}
 * @nosideeffects
 * @template T
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/lastIndexOf
 */
Array.prototype.lastIndexOf = function(obj, opt_fromIndex) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !Array<T>): R} callback
 * @param {S=} opt_thisobj
 * @return {!Array<R>}
 * @this {IArrayLike<T>|string}
 * @template T,S,R
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/map
 */
Array.prototype.map = function(callback, opt_thisobj) {};

/**
 * Available in ECMAScript 5, Mozilla 1.6+.
 * @param {?function(this:S, T, number, !Array<T>): *} callback
 * @param {S=} opt_thisobj
 * @return {boolean}
 * @this {IArrayLike<T>|string}
 * @template T,S
 * @override
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/some
 */
Array.prototype.some = function(callback, opt_thisobj) {};

/**
 * @type {number}
 */
Array.prototype.index;

/**
 * @type {?string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/input
 */
Array.prototype.input;

/**
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/length
 */
Array.prototype.length;

/**
 * Introduced in 1.8.5.
 * @param {*} arr
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/Array/isArray
 */
Array.isArray = function(arr) {};

/**
 * @constructor
 * @param {*=} opt_value
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Boolean
 */
function Boolean(opt_value) {}

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Boolean/toSource
 * @override
 */
Boolean.prototype.toSource = function() {};

/**
 * @this {boolean|Boolean}
 * @return {string}
 * @nosideeffects
 * @override
 */
Boolean.prototype.toString = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Boolean/valueOf
 * @override
 */
Boolean.prototype.valueOf = function() {};

/**
 * @constructor
 * @param {*=} opt_value
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number
 */
function Number(opt_value) {}

/**
 * @this {Number|number}
 * @param {number=} opt_fractionDigits
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/toExponential
 */
Number.prototype.toExponential = function(opt_fractionDigits) {};

/**
 * @this {Number|number}
 * @param {number=} opt_digits
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/toFixed
 */
Number.prototype.toFixed = function(opt_digits) {};

/**
 * @this {Number|number}
 * @param {number=} opt_precision
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/toPrecision
 */
Number.prototype.toPrecision = function(opt_precision) {};

/**
 * Returns a string representing the number.
 * @this {Number|number}
 * @param {(number|Number)=} opt_radix An optional radix.
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/toString
 * @override
 */
Number.prototype.toString = function(opt_radix) {};

// Properties.
/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_VALUE
 */
Number.MAX_VALUE;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MIN_VALUE
 */
Number.MIN_VALUE;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/NaN
 */
Number.NaN;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/NEGATIVE_INFINITY
 */
Number.NEGATIVE_INFINITY;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/POSITIVE_INFINITY
 */
Number.POSITIVE_INFINITY;

/**
 * @constructor
 * @param {number|string|bigint} arg
 * @return {bigint}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt
 */
function BigInt(arg) {}

/**
 * Wraps a BigInt value to a signed integer between -2^(width-1) and
 * 2^(width-1)-1.
 * @param {number} width
 * @param {bigint} bigint
 * @return {bigint}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt/asIntN
 */
BigInt.asIntN = function(width, bigint) {};

/**
 * Wraps a BigInt value to an unsigned integer between 0 and (2^width)-1.
 * @param {number} width
 * @param {bigint} bigint
 * @return {bigint}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt/asUintN
 */
BigInt.asUintN = function(width, bigint) {};

/**
 * Returns a string with a language-sensitive representation of this BigInt.
 * @param {string|!Array<string>=} locales
 * @param {Object=} options
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt/toLocaleString
 * @override
 */
BigInt.prototype.toLocaleString = function(locales, options) {};

/**
 * Returns a string representing the specified BigInt object. The trailing "n"
 * is not part of the string.
 * @this {BigInt|bigint}
 * @param {number=} radix
 * @return {string}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt/toString
 * @override
 */
BigInt.prototype.toString = function(radix) {};

/**
 * Returns the wrapped primitive value of a BigInt object.
 * @return {bigint}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/BigInt/valueOf
 * @override
 */
BigInt.prototype.valueOf = function() {};


/**
 * @const
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math
 */
var Math = {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/abs
 */
Math.abs = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/acos
 */
Math.acos = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/asin
 */
Math.asin = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/atan
 */
Math.atan = function(x) {};

/**
 * @param {?} y
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/atan2
 */
Math.atan2 = function(y, x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/ceil
 */
Math.ceil = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/cos
 */
Math.cos = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/exp
 */
Math.exp = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/floor
 */
Math.floor = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/log
 */
Math.log = function(x) {};

/**
 * @param {...?} var_args
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/max
 */
Math.max = function(var_args) {};

/**
 * @param {...?} var_args
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/min
 */
Math.min = function(var_args) {};

/**
 * @param {?} x
 * @param {?} y
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/pow
 */
Math.pow = function(x, y) {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/random
 */
Math.random = function() {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/round
 */
Math.round = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/sin
 */
Math.sin = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/sqrt
 */
Math.sqrt = function(x) {};

/**
 * @param {?} x
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/tan
 */
Math.tan = function(x) {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/toSource
 */
Math.toSource = function() {};

// Properties:

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/E
 */
Math.E;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/LN2
 */
Math.LN2;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/LN10
 */
Math.LN10;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/LOG2E
 */
Math.LOG2E;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/LOG10E
 */
Math.LOG10E;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/PI
 */
Math.PI;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/SQRT1_2
 */
Math.SQRT1_2;

/**
 * @const {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math/SQRT2
 */
Math.SQRT2;


/**
 * @param {?=} opt_yr_num
 * @param {?=} opt_mo_num
 * @param {?=} opt_day_num
 * @param {?=} opt_hr_num
 * @param {?=} opt_min_num
 * @param {?=} opt_sec_num
 * @param {?=} opt_ms_num
 * @constructor
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date
 */
function Date(opt_yr_num, opt_mo_num, opt_day_num, opt_hr_num, opt_min_num,
    opt_sec_num, opt_ms_num) {}

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/now
 */
Date.now = function() {};

/**
 * Parses a string representation of a date, and returns the number
 * of milliseconds since January 1, 1970, 00:00:00, local time.
 * @param {*} date
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/parse
 */
Date.parse = function(date) {};

/**
 * @param {number} year
 * @param {number=} opt_month
 * @param {number=} opt_date
 * @param {number=} opt_hours
 * @param {number=} opt_minute
 * @param {number=} opt_second
 * @param {number=} opt_ms
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/UTC
 */
Date.UTC = function(year, opt_month,
                    opt_date, opt_hours, opt_minute, opt_second, opt_ms) {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getDate
 */
Date.prototype.getDate = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getDay
 */
Date.prototype.getDay = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getMonth
 */
Date.prototype.getMonth = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getFullYear
 */
Date.prototype.getFullYear = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getYear
 */
Date.prototype.getYear = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getHours
 */
Date.prototype.getHours = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getMinutes
 */
Date.prototype.getMinutes = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getSeconds
 */
Date.prototype.getSeconds = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getMilliseconds
 */
Date.prototype.getMilliseconds = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getTime
 */
Date.prototype.getTime = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getTimezoneOffset
 */
Date.prototype.getTimezoneOffset = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCDate
 */
Date.prototype.getUTCDate = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCDay
 */
Date.prototype.getUTCDay = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCMonth
 */
Date.prototype.getUTCMonth = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCFullYear
 */
Date.prototype.getUTCFullYear = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCHours
 */
Date.prototype.getUTCHours = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCMinutes
 */
Date.prototype.getUTCMinutes = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCSeconds
 */
Date.prototype.getUTCSeconds = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/getUTCMilliseconds
 */
Date.prototype.getUTCMilliseconds = function() {};

/**
 * Sets the day of the month for a specified date according to local time.
 *
 * @param {number} dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setDate
 * @return {number}
 */
Date.prototype.setDate = function(dayValue) {};

/**
 * Set the month for a specified date according to local time.
 *
 * @param {number} monthValue
 * @param {number=} opt_dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setMonth
 * @return {number}
 */
Date.prototype.setMonth = function(monthValue, opt_dayValue) {};

/**
 * Sets the full year for a specified date according to local time.
 *
 * @param {number} yearValue
 * @param {number=} opt_monthValue
 * @param {number=} opt_dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setFullYear
 * @return {number}
 */
Date.prototype.setFullYear =
    function(yearValue, opt_monthValue, opt_dayValue) {};

/**
 * Sets the year for a specified date according to local time.
 *
 * @param {number} yearValue
 * @deprecated
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setYear
 * @return {number}
 */
Date.prototype.setYear = function(yearValue) {};

/**
 * Sets the hours for a specified date according to local time.
 *
 * @param {number} hoursValue
 * @param {number=} opt_minutesValue
 * @param {number=} opt_secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setHours
 * @return {number}
 */
Date.prototype.setHours = function(hoursValue, opt_minutesValue,
                                   opt_secondsValue, opt_msValue) {};

/**
 * Sets the minutes for a specified date according to local time.
 *
 * @param {number} minutesValue
 * @param {number=} opt_secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setMinutes
 * @return {number}
 */
Date.prototype.setMinutes =
    function(minutesValue, opt_secondsValue, opt_msValue) {};

/**
 * Sets the seconds for a specified date according to local time.
 *
 * @param {number} secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setSeconds
 * @return {number}
 */
Date.prototype.setSeconds = function(secondsValue, opt_msValue) {};

/**
 * Sets the milliseconds for a specified date according to local time.
 *
 * @param {number} millisecondsValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setMilliseconds
 * @return {number}
 */
Date.prototype.setMilliseconds = function(millisecondsValue) {};

/**
 * Sets the Date object to the time represented by a number of milliseconds
 * since January 1, 1970, 00:00:00 UTC.
 *
 * @param {number} timeValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setTime
 * @return {number}
 */
Date.prototype.setTime = function(timeValue) {};

/**
 * Sets the day of the month for a specified date according to universal time.
 *
 * @param {number} dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCDate
 * @return {number}
 */
Date.prototype.setUTCDate = function(dayValue) {};

/**
 * Sets the month for a specified date according to universal time.
 *
 * @param {number} monthValue
 * @param {number=} opt_dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCMonth
 * @return {number}
 */
Date.prototype.setUTCMonth = function(monthValue, opt_dayValue) {};

/**
 * Sets the full year for a specified date according to universal time.
 *
 * @param {number} yearValue
 * @param {number=} opt_monthValue
 * @param {number=} opt_dayValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCFullYear
 * @return {number}
 */
Date.prototype.setUTCFullYear = function(yearValue, opt_monthValue,
                                         opt_dayValue) {};

/**
 * Sets the hour for a specified date according to universal time.
 *
 * @param {number} hoursValue
 * @param {number=} opt_minutesValue
 * @param {number=} opt_secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCHours
 * @return {number}
 */
Date.prototype.setUTCHours = function(hoursValue, opt_minutesValue,
                                      opt_secondsValue, opt_msValue) {};

/**
 * Sets the minutes for a specified date according to universal time.
 *
 * @param {number} minutesValue
 * @param {number=} opt_secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCMinutes
 * @return {number}
 */
Date.prototype.setUTCMinutes = function(minutesValue, opt_secondsValue,
                                        opt_msValue) {};


/**
 * Sets the seconds for a specified date according to universal time.
 *
 * @param {number} secondsValue
 * @param {number=} opt_msValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCSeconds
 * @return {number}
 */
Date.prototype.setUTCSeconds = function(secondsValue, opt_msValue) {};

/**
 * Sets the milliseconds for a specified date according to universal time.
 *
 * @param {number} millisecondsValue
 * @modifies {this}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/setUTCMilliseconds
 * @return {number}
 */
Date.prototype.setUTCMilliseconds = function(millisecondsValue) {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toSource
 * @override
 */
Date.prototype.toSource = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en/JavaScript/Reference/Global_Objects/Date/toDateString
 */
Date.prototype.toDateString = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toGMTString
 */
Date.prototype.toGMTString = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toTimeString
 */
Date.prototype.toTimeString = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toUTCString
 */
Date.prototype.toUTCString = function() {};

/**
 * @param {(string|Array<string>)=} opt_locales
 * @param {Object=} opt_options
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleDateString
 */
Date.prototype.toLocaleDateString = function(opt_locales, opt_options) {};

/**
 * @param {string} formatString
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleFormat
 */
Date.prototype.toLocaleFormat = function(formatString) {};

/**
 * @param {string|Array<string>=} opt_locales
 * @param {Object=} opt_options
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleString
 * @see http://www.ecma-international.org/ecma-402/1.0/#sec-13.3.1
 * @override
 */
Date.prototype.toLocaleString = function(opt_locales, opt_options) {};

/**
 * @param {(string|Array<string>)=} opt_locales
 * @param {Object=} opt_options
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toLocaleTimeString
 */
Date.prototype.toLocaleTimeString = function(opt_locales, opt_options) {};

/**
 * @this {Date}
 * @return {string}
 * @nosideeffects
 * @override
 */
Date.prototype.toString = function() {};

/**
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/valueOf
 */
Date.prototype.valueOf;

/**
 * @constructor
 * @implements {Iterable<string>}
 * @param {*=} opt_str
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String
 */
function String(opt_str) {}

/**
 * @override
 */
String.prototype[Symbol.iterator] = function() {};

/**
 * @param {...number} var_args
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/fromCharCode
 */
String.fromCharCode = function(var_args) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/anchor
 */
String.prototype.anchor = function() {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/big
 */
String.prototype.big = function() {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/blink
 */
String.prototype.blink = function() {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/bold
 */
String.prototype.bold = function() {};

/**
 * Returns the specified character from a string.
 *
 * @this {String|string}
 * @param {number} index
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/charAt
 */
String.prototype.charAt = function(index) {};

/**
 * Returns a number indicating the Unicode value of the character at the given
 * index.
 *
 * @this {String|string}
 * @param {number=} opt_index
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/charCodeAt
 */
String.prototype.charCodeAt = function(opt_index) {};

/**
 * Combines the text of two or more strings and returns a new string.
 *
 * @this {String|string}
 * @param {...*} var_args
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/concat
 */
String.prototype.concat = function(var_args) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/fixed
 */
String.prototype.fixed = function() {};

/**
 * @this {String|string}
 * @param {string} color
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/fontcolor
 */
String.prototype.fontcolor = function(color) {};

/**
 * @this {String|string}
 * @param {number} size
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/fontsize
 */
String.prototype.fontsize = function(size) {};

/**
 * Returns the index within the calling String object of the first occurrence
 * of the specified value, starting the search at fromIndex, returns -1 if the
 * value is not found.
 *
 * @this {String|string}
 * @param {string|null} searchValue
 * @param {(number|null)=} opt_fromIndex
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/indexOf
 */
String.prototype.indexOf = function(searchValue, opt_fromIndex) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/italics
 */
String.prototype.italics = function() {};

/**
 * Returns the index within the calling String object of the last occurrence of
 * the specified value, or -1 if not found. The calling string is searched
 * backward, starting at fromIndex.
 *
 * @this {String|string}
 * @param {string|null} searchValue
 * @param {(number|null)=} opt_fromIndex
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/lastIndexOf
 */
String.prototype.lastIndexOf = function(searchValue, opt_fromIndex) {};

/**
 * @this {String|string}
 * @param {string} hrefAttribute
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/link
 */
String.prototype.link = function(hrefAttribute) {};

/**
 * Returns a number indicating whether a reference string comes before or after
 * or is the same as the given string in sort order.
 *
 * @this {*}
 * @param {?string} compareString
 * @param {string|Array<string>=} locales
 * @param {Object=} options
 * @return {number}
 * @nosideeffects
 * @see http://developer.mozilla.org/En/Core_JavaScript_1.5_Reference/Objects/String/localeCompare
 * @see http://www.ecma-international.org/ecma-402/1.0/#sec-13.1.1
 */
String.prototype.localeCompare = function(compareString, locales, options) {};

/**
 * Used to retrieve the matches when matching a string against a regular
 * expression.
 *
 * @this {String|string}
 * @param {*} regexp
 * @return {RegExpResult} This should really return an Array with a few
 *     special properties, but we do not have a good way to model this in
 *     our type system. Also see Regexp.prototype.exec.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/match
 */
String.prototype.match = function(regexp) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/quote
 */
String.prototype.quote = function() {};

/**
 * Finds a match between a regular expression and a string, and replaces the
 * matched substring with a new substring.
 *
 * This may have side-effects if the replacement function has side-effects.
 *
 * @this {String|string}
 * @param {RegExp|string} pattern
 * @param {?string|function(string, ...?):*} replacement
 * @return {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/replace
 */
String.prototype.replace = function(pattern, replacement) {};

/**
 * Executes the search for a match between a regular expression and this String
 * object.
 *
 * @this {String|string}
 * @param {RegExp|string} pattern
 * @return {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/search
 */
String.prototype.search = function(pattern) {};

/**
 * @this {String|string}
 * @param {number} begin
 * @param {number=} opt_end
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/slice
 */
String.prototype.slice = function(begin, opt_end) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/small
 */
String.prototype.small = function() {};

/**
 * @this {String|string}
 * @param {*=} opt_separator
 * @param {number=} opt_limit
 * @return {!Array<string>}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/split
 */
String.prototype.split = function(opt_separator, opt_limit) {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/strike
 */
String.prototype.strike = function() {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/sub
 */
String.prototype.sub = function() {};

/**
 * @this {String|string}
 * @param {number} start
 * @param {number=} opt_length
 * @return {string} The specified substring.
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/substr
 */
String.prototype.substr = function(start, opt_length) {};

/**
 * @this {String|string}
 * @param {number} start
 * @param {number=} opt_end
 * @return {string} The specified substring.
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/substring
 */
String.prototype.substring = function(start, opt_end) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/sup
 */
String.prototype.sup = function() {};

/**
 * @this {String|string}
 * @param {(string|Array<string>)=} opt_locales
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toLocaleUpperCase
 */
String.prototype.toLocaleUpperCase = function(opt_locales) {};

/**
 * @this {String|string}
 * @param {(string|Array<string>)=} opt_locales
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toLocaleLowerCase
 */
String.prototype.toLocaleLowerCase = function(opt_locales) {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toLowerCase
 */
String.prototype.toLowerCase = function() {};

/**
 * @this {String|string}
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toUpperCase
 */
String.prototype.toUpperCase = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/toSource
 * @override
 */
String.prototype.toSource = function() {};

/**
 * @this {string|String}
 * @return {string}
 * @nosideeffects
 * @override
 */
String.prototype.toString = function() {};

/**
 * @return {string}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/valueOf
 */
String.prototype.valueOf;

/**
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/length
 */
String.prototype.length;

/**
 * @constructor
 * @param {*=} opt_pattern
 * @param {*=} opt_flags
 * @return {!RegExp}
 * @throws {SyntaxError} if opt_pattern is an invalid pattern.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
function RegExp(opt_pattern, opt_flags) {}

/**
 * @param {*} pattern
 * @param {*=} opt_flags
 * @return {void}
 * @modifies {this}
 * @deprecated
 * @see http://msdn.microsoft.com/en-us/library/x9cswe0z(v=VS.85).aspx
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/compile
 */
RegExp.prototype.compile = function(pattern, opt_flags) {};

/**
 * @param {*} str The string to search.
 * @return {?RegExpResult}
 * @see http://msdn.microsoft.com/en-us/library/z908hy33(VS.85).aspx
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/exec
 */
RegExp.prototype.exec = function(str) {};

/**
 * @param {*} str The string to search.
 * @return {boolean} Whether the string was matched.
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/test
 */
RegExp.prototype.test = function(str) {};

/**
 * @this {RegExp}
 * @return {string}
 * @nosideeffects
 * @override
 */
RegExp.prototype.toString = function() {};

/**
 * @constructor
 * @extends {Array<string>}
 */
var RegExpResult = function() {};


/** @type {number} */
RegExpResult.prototype.index;


/** @type {string} */
RegExpResult.prototype.input;


/** @type {number} */
RegExpResult.prototype.length;


/**
 * Not actually part of ES3; was added in 2018.
 * https://github.com/tc39/proposal-regexp-named-groups
 *
 * @type {!Object<string, string>}
 */
RegExpResult.prototype.groups;


/**
 * Not actually part of ES3; was added in 2022.
 * https://github.com/tc39/proposal-regexp-match-indices
 *
 * @constructor
 * @extends {Array<!Array<number>|undefined>}
 */
var RegExpResultIndices = function() {};


/** @type {!Object<string, !Array<number>|undefined>} */
RegExpResultIndices.prototype.groups;


/** @type {!RegExpResultIndices} */
RegExpResult.prototype.indices;


// Constructor properties:

/**
 * The string against which the last regexp was matched.
 * @type {string}
 * @see http://www.devguru.com/Technologies/Ecmascript/Quickref/regexp_input.html
 */
RegExp.input;

/**
 * The last matched characters.
 * @type {string}
 * @see http://www.devguru.com/Technologies/Ecmascript/Quickref/regexp_lastMatch.html
 */
RegExp.lastMatch;

/**
 * The last matched parenthesized substring, if any.
 * @type {string}
 * @see http://www.devguru.com/Technologies/Ecmascript/Quickref/regexp_lastParen.html
 */
RegExp.lastParen;

/**
 * The substring of the input up to the characters most recently matched.
 * @type {string}
 * @see http://www.devguru.com/Technologies/Ecmascript/Quickref/regexp_leftContext.html
 */
RegExp.leftContext;

/**
 * The substring of the input after the characters most recently matched.
 * @type {string}
 * @see http://www.devguru.com/Technologies/Ecmascript/Quickref/regexp_rightContext.html
 */
RegExp.rightContext;

/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$1;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$2;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$3;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$4;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$5;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$6;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$7;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$8;
/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp
 */
RegExp.$9;

// Prototype properties:

/**
 * Whether to test the regular expression against all possible matches
 * in a string, or only against the first.
 * @type {boolean}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/global
 */
RegExp.prototype.global;

/**
 * The dotAll property indicates whether or not the "s" flag is used with the regular expression.
 *
 * @type {boolean}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/dotAll
 */
RegExp.prototype.dotAll;

/**
 * Whether to ignore case while attempting a match in a string.
 * @type {boolean}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/ignoreCase
 */
RegExp.prototype.ignoreCase;

/**
 * The index at which to start the next match.
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/lastIndex
 */
RegExp.prototype.lastIndex;

/**
 * Whether or not the regular expression uses lastIndex.
 * @type {boolean}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/sticky
 */
RegExp.prototype.sticky;

/**
 * Whether or not to search in strings across multiple lines.
 * @type {boolean}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/multiline
 */
RegExp.prototype.multiline;

/**
 * The text of the pattern.
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/source
 */
RegExp.prototype.source;

/**
 * The flags the regex was created with.
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/flags
 */
RegExp.prototype.flags;

/**
 * The unicode property indicates whether or not the "u" flag is used with a regular expression.
 *
 * @type {boolean}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/unicode
 */
RegExp.prototype.unicode;

/**
 * @constructor
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!Error}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error
 */
function Error(message, fileNameOrOptions, line) {}


/**
 * Chrome/v8 specific, altering the maximum depth of the stack trace
 * (10 by default).
 * @type {number}
 * @see http://code.google.com/p/v8/wiki/JavaScriptStackTraceApi
 */
Error.stackTraceLimit;


/**
 * Chrome/v8 specific, adds a stack trace to the error object. The optional
 * constructorOpt parameter allows you to pass in a function value. When
 * collecting the stack trace all frames above the topmost call to this
 * function, including that call, will be left out of the stack trace.
 * @param {Object} error The object to add the stack trace to.
 * @param {Function=} opt_constructor A function in the stack trace
 * @see http://code.google.com/p/v8/wiki/JavaScriptStackTraceApi
 * @return {undefined}
 */
Error.captureStackTrace = function(error, opt_constructor){};

/**
 * New in ES 2022, adds a cause to the error which is useful for chaining errors
 * @type {*}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/cause
 */
Error.prototype.cause;


/**
 * IE-only.
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/2w6a45b5.aspx
 */
Error.prototype.description;


/**
 * Mozilla-only.
 * @type {number}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/lineNumber
 */
Error.prototype.lineNumber;

/**
 * Mozilla-only
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/fileName
 */
Error.prototype.fileName;

/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/name
 */
Error.prototype.name;

/**
 * @type {string}
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Error/message
 */
Error.prototype.message;

/**
 * Doesn't seem to exist, but closure/debug.js references it.
 */
Error.prototype.sourceURL;

/** @type {string} */
Error.prototype.stack;


/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!EvalError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/EvalError
 */
function EvalError(message, fileNameOrOptions, line) {}

/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!RangeError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RangeError
 */
function RangeError(message, fileNameOrOptions, line) {}

/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!ReferenceError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ReferenceError
 */
function ReferenceError(message, fileNameOrOptions, line) {}

/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!SyntaxError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/SyntaxError
 */
function SyntaxError(message, fileNameOrOptions, line) {}

/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!TypeError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypeError
 */
function TypeError(message, fileNameOrOptions, line) {}

/**
 * @constructor
 * @extends {Error}
 * @param {*=} message
 * @param {*=} fileNameOrOptions
 * @param {*=} line
 * @return {!URIError}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/URIError
 */
function URIError(message, fileNameOrOptions, line) {}

// JScript extensions.
// @see http://msdn.microsoft.com/en-us/library/894hfyb4(VS.80).aspx

/**
 * @see http://msdn.microsoft.com/en-us/library/7sw4ddf8.aspx
 * @type {function(new:?, string, string=)}
 * @deprecated
 */
function ActiveXObject(progId, opt_location) {}
