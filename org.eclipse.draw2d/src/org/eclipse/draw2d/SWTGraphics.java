/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.draw2d;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;

import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

/**
 * A concrete implementation of <code>Graphics</code> using an SWT
 * <code>GC</code>. There are 2 states contained in this graphics class -- the
 * applied state which is the actual state of the GC and the current state which
 * is the current state of this graphics object.  Certain properties can be
 * changed multiple times and the GC won't be updated until it's actually used.
 * <P>
 * WARNING: This class is not intended to be subclassed.
 */
public class SWTGraphics
	extends Graphics
{

/**
 * An internal type used to represent and update the GC's clipping.
 * @since 3.1
 */
interface Clipping {
	/**
	 * Sets the clip's bounding rectangle into the provided argument and returns it for convenince.
	 * @param rect the rect
	 * @return the given rect
	 * @since 3.1
	 */
	Rectangle getBoundingBox(Rectangle rect);
	Clipping getCopy();
	void intersect(int left, int top, int right, int bottom);
	void scale(float horizontal, float vertical);
	void setOn(GC gc, int translateX, int translateY);
	void translate(float dx, float dy);
}

/**
 * Any state stored in this class is only applied when it is needed by a
 * specific graphics call.
 * @since 3.1
 */
static class LazyState {
	Color bgColor;
	Color fgColor;
	Font font;
	int graphicHints;
	int lineWidth;
	Clipping relativeClip;
}

static class RectangleClipping implements Clipping {
	private float top, left, bottom, right;
	
	RectangleClipping(float left, float top, float right, float bottom) {
		this.left = left;
		this.right = right;
		this.bottom = bottom;
		this.top = top;
	}
	
	RectangleClipping(org.eclipse.swt.graphics.Rectangle rect) {
		left = rect.x;
		top = rect.y;
		right = rect.x + rect.width;
		bottom = rect.y + rect.height;
	}
	
	RectangleClipping(Rectangle rect) {
		left = rect.x;
		top = rect.y;
		right = rect.right();
		bottom = rect.bottom();
	}
	
	public Rectangle getBoundingBox(Rectangle rect) {
		rect.x = (int)left;
		rect.y = (int)top;
		rect.width = (int)Math.ceil(right) - rect.x;
		rect.height = (int)Math.ceil(bottom) - rect.y;
		return rect;
	}
	
	public Clipping getCopy() {
		return new RectangleClipping(left, top, right, bottom);
	}
	
	public void intersect(int left, int top, int right, int bottom) {
		this.left = Math.max(this.left, left);
		this.right = Math.min(this.right, right);
		this.top = Math.max(this.top, top);
		this.bottom = Math.min(this.bottom, bottom);
		if (right < left || bottom < top) {
			//width and height of -1 to avoid ceiling function from re-adding a pixel.
			right = left - 1;
			bottom = top - 1;
		}
	}
	
	public void scale(float horz, float vert) {
		left /= horz;
		right /= horz;
		top /= vert;
		bottom /= vert;
	}
	
	public void setOn(GC gc, int translateX, int translateY) {
		int xInt = (int)Math.floor(left);
		int yInt = (int)Math.floor(top);
		gc.setClipping(xInt + translateX, yInt + translateY,
				(int)Math.ceil(right) - xInt,
				(int)Math.ceil(bottom) - yInt);
	}
	
	public void translate(float dx, float dy) {
		left += dx;
		right += dx;
		top += dy;
		bottom += dy;
	}
}

/**
 * Contains the entire state of the Graphics.
 */
static class State
	extends LazyState
	implements Cloneable
{
	float affineMatrix[];
	int alpha;
	Pattern bgPattern;
	int dx, dy;
	
	Pattern fgPattern;
	int lineDash[];
	
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
	/**
	 * Copies all state information from the given State to this State
	 * @param state The State to copy from
	 */
	public void copyFrom(State state) {
		bgColor = state.bgColor;
		fgColor = state.fgColor;
		lineWidth = state.lineWidth;
		dx = state.dx;
		dy = state.dy;
		bgPattern = state.bgPattern;
		fgPattern = state.fgPattern;
		font = state.font;
		lineDash = state.lineDash;
		graphicHints = state.graphicHints;
		affineMatrix = state.affineMatrix;
		relativeClip = state.relativeClip;
		alpha = state.alpha;
	}
}

static final int AA_MASK;
static final int AA_SHIFT;
static final int AA_WHOLE_NUMBER = 1;
static final int ADVANCED_GRAPHICS_MASK;
static final int ADVANCED_HINTS_DEFAULTS;
static final int ADVANCED_HINTS_MASK;
static final int ADVANCED_SHIFT;
static final int CAP_MASK;
static final int CAP_SHIFT;
static final int FILL_RULE_MASK;
static final int FILL_RULE_SHIFT;
static final int FILL_RULE_WHOLE_NUMBER = -1;
static final int INTERPOLATION_MASK;
static final int INTERPOLATION_SHIFT;
static final int INTERPOLATION_WHOLE_NUMBER = 1;
static final int JOIN_MASK;
static final int JOIN_SHIFT;
static final int LINE_STYLE_MASK;

static final int TEXT_AA_MASK;
static final int TEXT_AA_SHIFT;
static final int XOR_MASK;
static final int XOR_SHIFT;

static {
	XOR_SHIFT = 3;
	CAP_SHIFT = 4;
	JOIN_SHIFT = 6;
	AA_SHIFT = 8;
	TEXT_AA_SHIFT = 10;
	INTERPOLATION_SHIFT = 12;
	FILL_RULE_SHIFT = 14;
	ADVANCED_SHIFT = 15;

	LINE_STYLE_MASK = 7;
	AA_MASK = 3 << AA_SHIFT;
	CAP_MASK = 3 << CAP_SHIFT;
	FILL_RULE_MASK = 1 << FILL_RULE_SHIFT;
	INTERPOLATION_MASK = 3 << INTERPOLATION_SHIFT;
	JOIN_MASK = 3 << JOIN_SHIFT;
	TEXT_AA_MASK = 3 << TEXT_AA_SHIFT;
	XOR_MASK = 1 << XOR_SHIFT;
	ADVANCED_GRAPHICS_MASK = 1 << ADVANCED_SHIFT;
	
	ADVANCED_HINTS_MASK = TEXT_AA_MASK | AA_MASK | INTERPOLATION_MASK;
	ADVANCED_HINTS_DEFAULTS = ((SWT.DEFAULT + AA_WHOLE_NUMBER) << TEXT_AA_SHIFT)
			| ((SWT.DEFAULT + AA_WHOLE_NUMBER) << AA_SHIFT)
			| ((SWT.DEFAULT + INTERPOLATION_WHOLE_NUMBER) << INTERPOLATION_SHIFT);
}

private final LazyState appliedState = new LazyState();
private final State currentState = new State();

private boolean elementsNeedUpdate;
private GC gc;

private boolean sharedClipping;
private List stack = new ArrayList();

private int stackPointer = 0;
Transform transform;
private int translateX = 0;
private int translateY = 0;

/**
 * Constructs a new SWTGraphics that draws to the Canvas using the given GC.
 * @param gc the GC
 */
public SWTGraphics(GC gc) {
	this.gc = gc;
	init();
}

/**
 * If the background color has changed, this change will be pushed to the GC.  Also calls
 * {@link #checkGC()}.
 */
protected final void checkFill() {
	if (!appliedState.bgColor.equals(currentState.bgColor))
		gc.setBackground(appliedState.bgColor = currentState.bgColor);
	checkGC();
}

/**
 * If the rendering hints or the clip region has changed, these changes will be pushed to
 * the GC. Rendering hints include anti-alias, xor, join, cap, line style, fill rule,
 * interpolation, and other settings.
 */
protected final void checkGC() {
	if (appliedState.relativeClip != currentState.relativeClip) {
		appliedState.relativeClip = currentState.relativeClip;
		currentState.relativeClip.setOn(gc, translateX, translateY);
	}
	if (appliedState.graphicHints != currentState.graphicHints) {
		reconcileHints(appliedState.graphicHints, currentState.graphicHints);
		appliedState.graphicHints = currentState.graphicHints;
	}
}

/**
 * If the line width, line style, foreground or background colors have changed, these
 * changes will be pushed to the GC.  Also calls {@link #checkGC()}.
 */
protected final void checkPaint() {
	checkGC();
	if (!appliedState.fgColor.equals(currentState.fgColor))
		gc.setForeground(appliedState.fgColor = currentState.fgColor);
	if (appliedState.lineWidth != currentState.lineWidth)
		gc.setLineWidth(appliedState.lineWidth = currentState.lineWidth);
	if (!appliedState.bgColor.equals(currentState.bgColor))
		gc.setBackground(appliedState.bgColor = currentState.bgColor);
}

/**
 * @since 3.1
 */
private void checkSharedClipping() {
	if (sharedClipping) {
		sharedClipping = false;
		
		boolean previouslyApplied = (appliedState == currentState.relativeClip);
		currentState.relativeClip = currentState.relativeClip.getCopy();
		if (previouslyApplied)
			appliedState.relativeClip = currentState.relativeClip;
	}
}

/**
 * If the font has changed, this change will be pushed to the GC.  Also calls
 * {@link #checkPaint()} and {@link #checkFill()}.
 */
protected final void checkText() {
	checkPaint();
	if (!appliedState.font.equals(currentState.font))
		gc.setFont(appliedState.font = currentState.font);
}

/**
 * @see Graphics#clipRect(Rectangle)
 */
public void clipRect(Rectangle rect) {
	if (currentState.relativeClip == null)
		throw new IllegalStateException("The current clipping area does not " + //$NON-NLS-1$
		"support intersection."); //$NON-NLS-1$
	checkSharedClipping();
	currentState.relativeClip.intersect(rect.x, rect.y, rect.right(), rect.bottom());
	appliedState.relativeClip = null;
}

/**
 * @see Graphics#dispose()
 */
public void dispose() {
	while (stackPointer > 0)
		popState();
	if (transform != null)
		transform.dispose();
}

/**
 * @see Graphics#drawArc(int, int, int, int, int, int)
 */
public void drawArc(int x, int y, int width, int height, int offset, int length) {
	checkPaint();
	gc.drawArc(x + translateX, y + translateY, width, height, offset, length);
}

/**
 * @see Graphics#drawFocus(int, int, int, int)
 */
public void drawFocus(int x, int y, int w, int h) {
	checkPaint();
	gc.drawFocus(x + translateX, y + translateY, w + 1, h + 1);
}

/**
 * @see Graphics#drawImage(Image, int, int)
 */
public void drawImage(Image srcImage, int x, int y) {
	checkGC();
	gc.drawImage(srcImage, x + translateX, y + translateY);
}

/**
 * @see Graphics#drawImage(Image, int, int, int, int, int, int, int, int)
 */
public void drawImage(Image srcImage,
		int x1, int y1, int w1, int h1,
		int x2, int y2, int w2, int h2) {
	checkGC();
	gc.drawImage(srcImage, x1, y1, w1, h1, x2 + translateX, y2 + translateY, w2, h2);
}

/**
 * @see Graphics#drawLine(int, int, int, int)
 */
public void drawLine(int x1, int y1, int x2, int y2) {
	checkPaint();
	gc.drawLine(x1 + translateX, y1 + translateY, x2 + translateX, y2 + translateY);
}

/**
 * @see Graphics#drawOval(int, int, int, int)
 */
public void drawOval(int x, int y, int width, int height) {
	checkPaint();
	gc.drawOval(x + translateX, y + translateY, width, height);
}

/**
 * @see Graphics#drawPath(Path)
 */
public void drawPath(Path path) {
	checkPaint();
	initTransform(false);
	gc.drawPath(path);
}

/**
 * @see Graphics#drawPoint(int, int)
 */
public void drawPoint(int x, int y) {
	checkPaint();
	gc.drawPoint(x + translateX, y + translateY);
}

/**
 * @see Graphics#drawPolygon(int[])
 */
public void drawPolygon(int[] points) {
	checkPaint();
	try {
		translatePointArray(points, translateX, translateY);
		gc.drawPolygon(points);
	} finally {
		translatePointArray(points, -translateX, -translateY);
	}
}

/**
 * @see Graphics#drawPolygon(PointList)
 */
public void drawPolygon(PointList points) {
	drawPolygon(points.toIntArray());
}

/**
 * @see Graphics#drawPolyline(int[])
 */
public void drawPolyline(int[] points) {
	checkPaint();
	try {
		translatePointArray(points, translateX, translateY);
		gc.drawPolyline(points);
	} finally {
		translatePointArray(points, -translateX, -translateY);
	}	
}

/**
 * @see Graphics#drawPolyline(PointList)
 */
public void drawPolyline(PointList points) {
	drawPolyline(points.toIntArray());
}

/**
 * @see Graphics#drawRectangle(int, int, int, int)
 */
public void drawRectangle(int x, int y, int width, int height) {
	checkPaint();
	gc.drawRectangle(x + translateX, y + translateY, width, height);
}

/**
 * @see Graphics#drawRoundRectangle(Rectangle, int, int)
 */
public void drawRoundRectangle(Rectangle r, int arcWidth, int arcHeight) {
	checkPaint();
	gc.drawRoundRectangle(r.x + translateX, r.y + translateY, r.width, r.height, 
							arcWidth, arcHeight);
}

/**
 * @see Graphics#drawString(String, int, int)
 */
public void drawString(String s, int x, int y) {
	checkText();
	gc.drawString(s, x + translateX, y + translateY, true);
}

/**
 * @see Graphics#drawText(String, int, int)
 */
public void drawText(String s, int x, int y) {
	checkText();
	gc.drawText(s, x + translateX, y + translateY, true);
}

/**
 * @see Graphics#drawTextLayout(TextLayout, int, int, int, int, Color, Color)
 */
public void drawTextLayout(TextLayout layout, int x, int y, int selectionStart,
		int selectionEnd, Color selectionForeground, Color selectionBackground) {
	//$TODO probably just call checkPaint since Font and BG color don't apply
	checkText();
	layout.draw(gc, x + translateX, y + translateY, selectionStart, selectionEnd,
			selectionForeground, selectionBackground);
}

/**
 * @see Graphics#fillArc(int, int, int, int, int, int)
 */
public void fillArc(int x, int y, int width, int height, int offset, int length) {
	checkFill();
	gc.fillArc(x + translateX, y + translateY, width, height, offset, length);
}

/**
 * @see Graphics#fillGradient(int, int, int, int, boolean)
 */
public void fillGradient(int x, int y, int w, int h, boolean vertical) {
	checkPaint();
	gc.fillGradientRectangle(x + translateX, y + translateY, w, h, vertical);
}

/**
 * @see Graphics#fillOval(int, int, int, int)
 */
public void fillOval(int x, int y, int width, int height) {
	checkFill();
	gc.fillOval(x + translateX, y + translateY, width, height);
}

/**
 * @see Graphics#fillPath(Path)
 */
public void fillPath(Path path) {
	checkFill();
	initTransform(false);
	gc.fillPath(path);
}

/**
 * @see Graphics#fillPolygon(int[])
 */
public void fillPolygon(int[] points) {
	checkFill();
	try {
		translatePointArray(points, translateX, translateY);
		gc.fillPolygon(points);
	} finally {
		translatePointArray(points, -translateX, -translateY);
	}
}

/**
 * @see Graphics#fillPolygon(PointList)
 */
public void fillPolygon(PointList points) {
	fillPolygon(points.toIntArray());
}

/**
 * @see Graphics#fillRectangle(int, int, int, int)
 */
public void fillRectangle(int x, int y, int width, int height) {
	checkFill();
	gc.fillRectangle(x + translateX, y + translateY, width, height);
}

/**
 * @see Graphics#fillRoundRectangle(Rectangle, int, int)
 */
public void fillRoundRectangle(Rectangle r, int arcWidth, int arcHeight) {
	checkFill();
	gc.fillRoundRectangle(r.x + translateX, r.y + translateY, r.width, r.height, 
							arcWidth, arcHeight);
}

/**
 * @see Graphics#fillString(String, int, int)
 */
public void fillString(String s, int x, int y) {
	checkText();
	gc.drawString(s, x + translateX, y + translateY, false);
}

/**
 * @see Graphics#fillText(String, int, int)
 */
public void fillText(String s, int x, int y) {
	checkText();
	gc.drawText(s, x + translateX, y + translateY, false);
}

/**
 * @see Graphics#getAlpha()
 */
public int getAlpha() {
	return currentState.alpha;
}

/**
 * @see Graphics#getAntialias()
 */
public int getAntialias() {
	return ((currentState.graphicHints & AA_MASK) >> AA_SHIFT) - AA_WHOLE_NUMBER;
}

/**
 * @see Graphics#getBackgroundColor()
 */
public Color getBackgroundColor() {
	return currentState.bgColor;
}

/**
 * @see Graphics#getClip(Rectangle)
 */
public Rectangle getClip(Rectangle rect) {
	if (currentState.relativeClip != null) {
		currentState.relativeClip.getBoundingBox(rect);
		return rect;
	}
	throw new IllegalStateException(
			"Clipping can no longer be queried due to transformations"); //$NON-NLS-1$
}

/**
 * @see Graphics#getFillRule()
 */
public int getFillRule() {
	return ((currentState.graphicHints & FILL_RULE_MASK) >> FILL_RULE_SHIFT) - FILL_RULE_WHOLE_NUMBER; 
}

/**
 * @see Graphics#getFont()
 */
public Font getFont() {
	return currentState.font;
}

/**
 * @see Graphics#getFontMetrics()
 */
public FontMetrics getFontMetrics() {
	checkText();
	return gc.getFontMetrics();
}

/**
 * @see Graphics#getForegroundColor()
 */
public Color getForegroundColor() {
	return currentState.fgColor;
}

/**
 * @see Graphics#getInterpolation()
 */
public int getInterpolation() {
	return ((currentState.graphicHints & INTERPOLATION_MASK) >> INTERPOLATION_SHIFT)
			- INTERPOLATION_WHOLE_NUMBER;
}

/**
 * @see Graphics#getLineCap()
 */
public int getLineCap() {
	return (currentState.graphicHints & CAP_MASK) >> CAP_SHIFT;
}

/**
 * @see Graphics#getLineJoin()
 */
public int getLineJoin() {
	return (currentState.graphicHints & JOIN_MASK) >> JOIN_SHIFT;
}

/**
 * @see Graphics#getLineStyle()
 */
public int getLineStyle() {
	return currentState.graphicHints & LINE_STYLE_MASK;
}

/**
 * @see Graphics#getLineWidth()
 */
public int getLineWidth() {
	return currentState.lineWidth;
}

/**
 * @see Graphics#getTextAntialias()
 */
public int getTextAntialias() {
	return ((currentState.graphicHints & TEXT_AA_MASK) >> TEXT_AA_SHIFT) - AA_WHOLE_NUMBER;
}

/**
 * @see Graphics#getXORMode()
 */
public boolean getXORMode() {
	return (currentState.graphicHints & XOR_MASK) != 0;
}

/**
 * Called by constructor, initializes all State information for currentState
 */
protected void init() {
	currentState.bgColor = appliedState.bgColor = gc.getBackground();
	currentState.fgColor = appliedState.fgColor = gc.getForeground();
	currentState.font = appliedState.font = gc.getFont();
	currentState.lineWidth = appliedState.lineWidth = gc.getLineWidth();
	currentState.graphicHints |= gc.getLineStyle();
	currentState.graphicHints |= gc.getLineCap() << CAP_SHIFT;
	currentState.graphicHints |= gc.getLineJoin() << JOIN_SHIFT;
	currentState.graphicHints |= gc.getAdvanced() ? ADVANCED_GRAPHICS_MASK : 0;
	currentState.graphicHints |= gc.getXORMode() ? XOR_MASK : 0;
	
	appliedState.graphicHints = currentState.graphicHints;
	
	currentState.relativeClip = new RectangleClipping(gc.getClipping());
	currentState.lineDash = gc.getLineDash();
	currentState.alpha = gc.getAlpha();
}

private void initTransform(boolean force) {
	if (!force && translateX == 0 && translateY == 0)
		return;
	if (transform == null) {
		transform = new Transform(Display.getCurrent());
		elementsNeedUpdate = true;
		transform.translate(translateX, translateY);
		translateX = 0;
		translateY = 0;
		gc.setTransform(transform);
		currentState.graphicHints |= ADVANCED_GRAPHICS_MASK;
	}
}

/**
 * @see Graphics#popState()
 */
public void popState() {
	stackPointer--;
	restoreState((State)stack.get(stackPointer));
}

/**
 * @see Graphics#pushState()
 */
public void pushState() {
	if (currentState.relativeClip == null)
		throw new IllegalStateException("The clipping has been modified in" + //$NON-NLS-1$
				"a way that cannot be saved and restored."); //$NON-NLS-1$
	try {
		State s;
		currentState.dx = translateX;
		currentState.dy = translateY;
				
		if (elementsNeedUpdate) {
			elementsNeedUpdate = false;
			transform.getElements(currentState.affineMatrix = new float[6]);
		}
		if (stack.size() > stackPointer) {
			s = (State)stack.get(stackPointer);
			s.copyFrom(currentState);
		} else {
			stack.add(currentState.clone());
		}
		sharedClipping = true;
		stackPointer++;
	} catch (CloneNotSupportedException e) {
		throw new RuntimeException(e);
	}
}

private void reconcileHints(int applied, int hints) {
	if (applied != hints) {
		int changes = hints ^ applied;
		
		if ((changes & LINE_STYLE_MASK) != 0)
			gc.setLineStyle(hints & LINE_STYLE_MASK);
		
		if ((changes & XOR_MASK) != 0)
			gc.setXORMode((hints & XOR_MASK) != 0);
		
		//Check to see if there is anything remaining
		changes &= ~(XOR_MASK | LINE_STYLE_MASK);
		if (changes == 0)
			return;
		
		if ((changes & JOIN_MASK) != 0)
			gc.setLineJoin((hints & JOIN_MASK) >> JOIN_SHIFT);
		if ((changes & CAP_MASK) != 0)
			gc.setLineCap((hints & CAP_MASK) >> CAP_SHIFT);
		
		if ((changes & INTERPOLATION_MASK) != 0)
			gc.setInterpolation(((hints & INTERPOLATION_MASK) >> INTERPOLATION_SHIFT) - INTERPOLATION_WHOLE_NUMBER);
		
		if ((changes & FILL_RULE_MASK) != 0)
			gc.setFillRule(((hints & FILL_RULE_MASK) >> FILL_RULE_SHIFT) - FILL_RULE_WHOLE_NUMBER);
		
		if ((changes & AA_MASK) != 0)
			gc.setAntialias(((hints & AA_MASK) >> AA_SHIFT) - AA_WHOLE_NUMBER);
		if ((changes & TEXT_AA_MASK) != 0)
			gc.setTextAntialias(((hints & TEXT_AA_MASK) >> TEXT_AA_SHIFT) - AA_WHOLE_NUMBER);
		
		// If advanced was flagged, but none of the conditions which trigger advanced
		// actually got applied, force advanced graphics on.
		if ((changes & ADVANCED_GRAPHICS_MASK) != 0) {
			if ((hints & ADVANCED_GRAPHICS_MASK) != 0 && !gc.getAdvanced())
				gc.setAdvanced(true);
		}
	}
}

/**
 * @see Graphics#restoreState()
 */
public void restoreState() {
	restoreState((State)stack.get(stackPointer - 1));
}

/**
 * Sets all State information to that of the given State, called by restoreState()
 * @param s the State
 */
protected void restoreState(State s) {	
	/*
	 * We must set the transformation matrix first since it affects things like clipping
	 * regions and patterns.
	 */
	setAffineMatrix(s.affineMatrix);
	currentState.relativeClip = s.relativeClip;
	sharedClipping = true;
	
	//If the GC is currently advanced, but it was not when pushed, revert
	if (gc.getAdvanced() && (s.graphicHints & ADVANCED_GRAPHICS_MASK) == 0) {
		//Set applied clip to null to force a re-setting of the clipping.
		appliedState.relativeClip = null;
		gc.setAdvanced(false);
		appliedState.graphicHints &= ~ADVANCED_HINTS_MASK;
		appliedState.graphicHints |= ADVANCED_HINTS_DEFAULTS;
	}

	setBackgroundColor(s.bgColor);
	setBackgroundPattern(s.bgPattern);
	
	setForegroundColor(s.fgColor);
	setForegroundPattern(s.fgPattern);

	setAlpha(s.alpha);
	setLineWidth(s.lineWidth);
	setFont(s.font);
	//This method must come last because above methods will incorrectly set advanced state
	setGraphicHints(s.graphicHints);

	translateX = currentState.dx = s.dx;
	translateY = currentState.dy = s.dy;
}

/**
 * @see Graphics#rotate(float)
 */
public void rotate(float degrees) {
	//Flush clipping, patter, etc., before applying transform
	checkGC();
	initTransform(true);
	transform.rotate(degrees);
	gc.setTransform(transform);
	elementsNeedUpdate = true;

	//Can no longer operate or maintain clipping
	appliedState.relativeClip = currentState.relativeClip = null;
}

/**
 * @see Graphics#scale(double)
 */
public void scale(double factor) {
	scale((float)factor, (float)factor);
}

/**
 * @see org.eclipse.draw2d.Graphics#scale(float, float)
 */
public void scale(float horizontal, float vertical) {
	//Flush any clipping before scaling
	checkGC();

	initTransform(true);
	transform.scale(horizontal, vertical);
	gc.setTransform(transform);
	elementsNeedUpdate = true;

	checkSharedClipping();
	if (currentState.relativeClip != null)
		currentState.relativeClip.scale(horizontal, vertical);
}

private void setAffineMatrix(float[] m) {
	if (!elementsNeedUpdate && currentState.affineMatrix == m)
		return;
	currentState.affineMatrix = m;
	if (m != null)
		transform.setElements(m[0], m[1], m[2], m[3], m[4], m[5]);
	else if (transform != null) {
		transform.dispose();
		transform = null;
		elementsNeedUpdate = false;
	}
	gc.setTransform(transform);
}

/**
 * @see Graphics#setAlpha(int)
 */
public void setAlpha(int alpha) {
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK;
	if (currentState.alpha != alpha)
		gc.setAlpha(this.currentState.alpha = alpha);
}

/**
 * @see Graphics#setAntialias(int)
 */
public void setAntialias(int value) {
	currentState.graphicHints &= ~AA_MASK;
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK | (value + AA_WHOLE_NUMBER) << AA_SHIFT;
}

/**
 * @see Graphics#setBackgroundColor(Color)
 */
public void setBackgroundColor(Color color) {
	currentState.bgColor = color;
}

/**
 * @see Graphics#setBackgroundPattern(Pattern)
 */
public void setBackgroundPattern(Pattern pattern) {
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK;
	if (currentState.fgPattern == pattern)
		return;
	currentState.bgPattern = pattern;

	if (pattern != null) {
		initTransform(true);
		gc.setBackgroundPattern(pattern);
	}
}

/**
 * @see Graphics#setClip(Path)
 */
public void setClip(Path path) {
	initTransform(false);
	gc.setClipping(path);
	appliedState.relativeClip = currentState.relativeClip = null;
}

/**
 * @see Graphics#setClip(Rectangle)
 */
public void setClip(Rectangle rect) {
	currentState.relativeClip = new RectangleClipping(rect);
}

/**
 * @see Graphics#setFillRule(int)
 */
public void setFillRule(int rule) {
	currentState.graphicHints &= ~FILL_RULE_MASK;
	currentState.graphicHints |= (rule + FILL_RULE_WHOLE_NUMBER) << FILL_RULE_SHIFT;
}

/**
 * @see Graphics#setFont(Font)
 */
public void setFont(Font f) {
	currentState.font = f;
}

/**
 * @see Graphics#setForegroundColor(Color)
 */
public void setForegroundColor(Color color) {
	currentState.fgColor = color;
}

/**
 * @see Graphics#setForegroundPattern(Pattern)
 */
public void setForegroundPattern(Pattern pattern) {
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK;
	if (currentState.fgPattern == pattern)
		return;
	currentState.fgPattern = pattern;

	if (pattern != null) {
		initTransform(true);
		gc.setForegroundPattern(pattern);
	}
}

private void setGraphicHints(int hints) {
	currentState.graphicHints = hints;
}

/**
 * @see Graphics#setInterpolation(int)
 */
public void setInterpolation(int interpolation) {
	//values range [-1, 3]
	currentState.graphicHints &= ~INTERPOLATION_MASK;
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK | (interpolation + INTERPOLATION_WHOLE_NUMBER) << INTERPOLATION_SHIFT; 
}

/**
 * @see Graphics#setLineCap(int)
 */
public void setLineCap(int cap) {
	currentState.graphicHints &= ~CAP_MASK;
	currentState.graphicHints |= cap << CAP_SHIFT;
}

/**
 * @see Graphics#setLineDash(int[])
 */
public void setLineDash(int[] dashes) {
	if (dashes != null) {
		int copy[] = new int[dashes.length];
		for (int i = 0; i < dashes.length; i++) {
			int dash = dashes[i];
			if (dash <= 0)
				SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			copy[i] = dash;
		}
		currentState.lineDash = copy;
		setLineStyle(SWT.LINE_CUSTOM);
		gc.setLineDash(copy);
	} else {
		currentState.lineDash = null;
		setLineStyle(SWT.LINE_SOLID);
	}
}

/**
 * @see Graphics#setLineJoin(int)
 */
public void setLineJoin(int join) {
	currentState.graphicHints &= ~JOIN_MASK;
	currentState.graphicHints |= join << JOIN_SHIFT;
}

/**
 * @see Graphics#setLineStyle(int)
 */
public void setLineStyle(int style) {
	currentState.graphicHints &= ~LINE_STYLE_MASK;
	currentState.graphicHints |= style;
}

/**
 * @see Graphics#setLineWidth(int)
 */
public void setLineWidth(int width) {
	currentState.lineWidth = width;
}

/**
 * @see Graphics#setTextAntialias(int)
 */
public void setTextAntialias(int value) {
	currentState.graphicHints &= ~TEXT_AA_MASK;
	currentState.graphicHints |= ADVANCED_GRAPHICS_MASK | (value + AA_WHOLE_NUMBER) << TEXT_AA_SHIFT;
}

/**
 * @see Graphics#setXORMode(boolean)
 */
public void setXORMode(boolean xor) {
	currentState.graphicHints &= ~XOR_MASK;
	if (xor)
		currentState.graphicHints |= XOR_MASK;
}

/**
 * @see Graphics#shear(float, float)
 */
public void shear(float horz, float vert) {
	//Flush any clipping changes before shearing
	checkGC();
	initTransform(true);
	float matrix[] = new float[6];
	transform.getElements(matrix);
	transform.setElements(
			matrix[0] + matrix[2] * vert,
			matrix[1] + matrix[3] * vert,
			matrix[0] * horz + matrix[2],
			matrix[1] * horz + matrix[3],
			matrix[4],
			matrix[5]);
	
	gc.setTransform(transform);
	elementsNeedUpdate = true;
	//Can no longer track clipping changes
	appliedState.relativeClip = currentState.relativeClip = null;
}

/**
 * @see Graphics#translate(int, int)
 */
public void translate(int dx, int dy) {
	if (dx == 0 && dy == 0)
		return;
	if (transform != null) {
		//Flush clipping, pattern, etc. before applying transform
		checkGC();
		transform.translate(dx, dy);
		elementsNeedUpdate = true;
		gc.setTransform(transform);
	} else {
		translateX += dx;
		translateY += dy;
	}
	checkSharedClipping();
	if (currentState.relativeClip != null)
		currentState.relativeClip.translate(-dx, -dy);
}

/**
 * @see Graphics#translate(float, float)
 */
public void translate(float dx, float dy) {
	initTransform(true);
	checkGC();
	transform.translate(dx, dy);
	elementsNeedUpdate = true;
	gc.setTransform(transform);
	checkSharedClipping();
	if (currentState.relativeClip != null)
		currentState.relativeClip.translate(-dx, -dy);
}

private void translatePointArray(int[] points, int translateX, int translateY) {
	if (translateX == 0 && translateY == 0)
		return;
	for (int i = 0; (i + 1) < points.length; i += 2) {
		points[i] += translateX;
		points[i + 1] += translateY;
	}
}

}