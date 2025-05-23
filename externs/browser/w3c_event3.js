/*
 * Copyright 2010 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's event Level 3 specification.
 *  This file depends on w3c_event.js.
 *  The whole file has been partially type annotated.
 *  Created from https://www.w3.org/TR/uievents/#ui-events-intro
 *
 * @externs
 */

/**
 * @param {string} typeArg
 * @param {boolean=} canBubbleArg
 * @param {boolean=} cancelableArg
 * @param {?Window=} viewArg
 * @param {string=} keyArg
 * @param {number=} locationArg
 * @param {boolean=} ctrlKey
 * @param {boolean=} altKey
 * @param {boolean=} shiftKey
 * @param {boolean=} metaKey
 * @return {undefined}
 */
KeyboardEvent.prototype.initKeyboardEvent = function(
    typeArg, canBubbleArg, cancelableArg, viewArg, keyArg, locationArg, ctrlKey,
    altKey, shiftKey, metaKey) {};

/** @type {string} */
KeyboardEvent.prototype.char;

/** @type {string} */
KeyboardEvent.prototype.code;

/** @type {string} */
KeyboardEvent.prototype.key;

/** @type {boolean} */
KeyboardEvent.prototype.isComposing;

/** @type {number} */
KeyboardEvent.prototype.location;

/** @type {boolean} */
KeyboardEvent.prototype.repeat;

/** @type {string} */
KeyboardEvent.prototype.locale;

/** @type {number} */
MouseEvent.prototype.buttons;

/**
 * @param {string} keyIdentifierArg
 * @return {boolean}
 */
MouseEvent.prototype.getModifierState = function(keyIdentifierArg) {};

/** @type {boolean} */
Event.prototype.defaultPrevented;

/** @type {string} */
Event.prototype.namespaceURI;

/** @return {undefined} */
Event.prototype.stopImmediatePropagation = function() {};
