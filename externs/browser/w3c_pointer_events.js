/*
 * Copyright 2014 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Pointer Events specification.
 *  Created from
 *   http://www.w3.org/TR/pointerevents/
 *
 * @externs
 */


/**
 * @type {string}
 * @see http://www.w3.org/TR/pointerevents/#the-touch-action-css-property
 */
CSSProperties.prototype.touchAction;

/**
 * @type {boolean}
 * @see http://www.w3.org/TR/pointerevents/#widl-Navigator-pointerEnabled
 */
Navigator.prototype.pointerEnabled;

/**
 * @type {number}
 * @see http://www.w3.org/TR/pointerevents/#widl-Navigator-maxTouchPoints
 */
Navigator.prototype.maxTouchPoints;


/**
 * @param {number} pointerId
 * @see https://www.w3.org/TR/pointerevents/#widl-Element-setPointerCapture-void-long-pointerId
 */
Element.prototype.setPointerCapture = function(pointerId) {};

/**
 * @param {number} pointerId
 * @see https://www.w3.org/TR/pointerevents/#widl-Element-releasePointerCapture-void-long-pointerId
 */
Element.prototype.releasePointerCapture = function(pointerId) {};

/**
 * @param {number} pointerId
 * @see https://www.w3.org/TR/pointerevents/#dom-element-haspointercapture
 * @return {boolean}
 */
Element.prototype.hasPointerCapture = function(pointerId) {};


/**
 * @record
 * @extends {MouseEventInit}
 * @see https://www.w3.org/TR/pointerevents/#idl-def-PointerEventInit
 */
function PointerEventInit() {}

/** @type {undefined|number} */
PointerEventInit.prototype.pointerId;

/** @type {undefined|number} */
PointerEventInit.prototype.width;

/** @type {undefined|number} */
PointerEventInit.prototype.height;

/** @type {undefined|number} */
PointerEventInit.prototype.pressure;

/** @type {undefined|number} */
PointerEventInit.prototype.tiltX;

/** @type {undefined|number} */
PointerEventInit.prototype.tiltY;

/** @type {undefined|string} */
PointerEventInit.prototype.pointerType;

/** @type {undefined|boolean} */
PointerEventInit.prototype.isPrimary;

/** @type {undefined|number} */
PointerEventInit.prototype.altitudeAngle;

/** @type {undefined|number} */
PointerEventInit.prototype.azimuthAngle;

/** @type {undefined|number} */
PointerEventInit.prototype.tangentialPressure;

/** @type {undefined|number} */
PointerEventInit.prototype.twist;

/** @type {undefined|!Array<!PointerEvent>} */
PointerEventInit.prototype.coalescedEvents;

/** @type {undefined|!Array<!PointerEvent>} */
PointerEventInit.prototype.predictedEvents;


/**
 * @constructor
 * @extends {MouseEvent}
 * @param {string} type
 * @param {PointerEventInit=} opt_eventInitDict
 * @see http://www.w3.org/TR/pointerevents/#pointerevent-interface
 */
function PointerEvent(type, opt_eventInitDict) {}

/** @type {number} */
PointerEvent.prototype.pointerId;

/** @type {number} */
PointerEvent.prototype.width;

/** @type {number} */
PointerEvent.prototype.height;

/** @type {number} */
PointerEvent.prototype.pressure;

/** @type {number} */
PointerEvent.prototype.tiltX;

/** @type {number} */
PointerEvent.prototype.tiltY;

/** @type {string} */
PointerEvent.prototype.pointerType;

/** @type {boolean} */
PointerEvent.prototype.isPrimary;

// Microsoft pointerType values
/** @const {string} */
PointerEvent.prototype.MSPOINTER_TYPE_TOUCH;

/** @const {string} */
PointerEvent.prototype.MSPOINTER_TYPE_PEN;

/** @const {string} */
PointerEvent.prototype.MSPOINTER_TYPE_MOUSE;

/**
 * @see https://w3c.github.io/pointerevents/extension.html
 * @return {!Array<!PointerEvent>}
 */
PointerEvent.prototype.getCoalescedEvents = function() {};

/** @return {!Array<!PointerEvent>} */
PointerEvent.prototype.getPredictedEvents = function() {};
