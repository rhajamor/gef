/*****************************************************************************************
 * Copyright (c) 2004 IBM Corporation and others. All rights reserved. This program and
 * the accompanying materials are made available under the terms of the Common Public
 * License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ****************************************************************************************/

package org.eclipse.gef.examples.text.tools;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.graphics.Cursor;

import org.eclipse.jface.util.Assert;

import org.eclipse.draw2d.Cursors;
import org.eclipse.draw2d.geometry.Rectangle;

import org.eclipse.gef.DragTracker;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.tools.SelectionTool;
import org.eclipse.gef.tools.ToolUtilities;

import org.eclipse.gef.examples.text.AppendableCommand;
import org.eclipse.gef.examples.text.GraphicalTextViewer;
import org.eclipse.gef.examples.text.SelectionRange;
import org.eclipse.gef.examples.text.TextCommand;
import org.eclipse.gef.examples.text.TextLocation;
import org.eclipse.gef.examples.text.TextUtilities;
import org.eclipse.gef.examples.text.actions.StyleListener;
import org.eclipse.gef.examples.text.actions.StyleProvider;
import org.eclipse.gef.examples.text.actions.StyleService;
import org.eclipse.gef.examples.text.edit.CaretSearch;
import org.eclipse.gef.examples.text.edit.TextStyleManager;
import org.eclipse.gef.examples.text.edit.TextualEditPart;
import org.eclipse.gef.examples.text.requests.TextRequest;

/**
 * @since 3.1
 */
public class TextTool extends SelectionTool implements StyleProvider {

static final boolean IS_CARBON = "carbon".equals(SWT.getPlatform()); //$NON-NLS-1$

private static final int MODE_BS = 2;

private static final int MODE_DEL = 3;

private static final int MODE_TYPING = 1;

private StyleListener listener;

private AppendableCommand pendingCommand;
private List styleKeys = new ArrayList();
private List styleValues = new ArrayList();

private int textInputMode;

private final GraphicalTextViewer textViewer;

/**
 * @since 3.1
 */
public TextTool(GraphicalTextViewer viewer, StyleService service) {
	this.textViewer = viewer;
	service.setStyleProvider(this);
}

public void addStyleListener(StyleListener listener) {
	Assert.isTrue(this.listener == null);
	this.listener = listener;
}

protected Cursor calculateCursor() {
	EditPart target = getTargetEditPart();
	if (target instanceof TextualEditPart) {
		TextualEditPart textTarget = (TextualEditPart)target;
		if (textTarget.acceptsCaret())
			return Cursors.IBEAM;
	}
	return super.calculateCursor();
}

/**
 * Extended to set unfinished command to <code>null</code>.
 * @see org.eclipse.gef.tools.TargetingTool#deactivate()
 */
public void deactivate() {
	setTextInputMode(0);
	super.deactivate();
}

/**
 * @since 3.1
 * @param action
 * @param event
 */
private void doAction(int action, KeyEvent event) {
	boolean append = false;
	switch (action) {
		case ST.DELETE_PREVIOUS:
			doBackspace();
			break;
		case ST.DELETE_NEXT:
			doDelete();
			break;
//HOME
		case ST.SELECT_LINE_START:
			append = true;
		case ST.LINE_START:
			doSelect(CaretSearch.LINE_BOUNDARY, false, append);
			break;
//	WORD_PREV
		case ST.SELECT_WORD_NEXT:
			append = true;
		case ST.WORD_NEXT:
			doSelect(CaretSearch.WORD_BOUNDARY, true, append);
			break;
//	WORD_NEXT
		case ST.SELECT_WORD_PREVIOUS:
			append = true;
		case ST.WORD_PREVIOUS:
			doSelect(CaretSearch.WORD_BOUNDARY, false, append);
			break;
//END
		case ST.SELECT_LINE_END:
			append = true;
		case ST.LINE_END:
			doSelect(CaretSearch.LINE_BOUNDARY, true, append);
			break;
//LEFT
		case ST.SELECT_COLUMN_PREVIOUS:
			append = true;
		case ST.COLUMN_PREVIOUS:
			doSelect(CaretSearch.COLUMN, false, append);
			break;
//RIGHT
		case ST.SELECT_COLUMN_NEXT:
			append = true;
		case ST.COLUMN_NEXT:
			doSelect(CaretSearch.COLUMN, true, append);
			break;
//UP
		case ST.SELECT_LINE_UP:
			append = true;
		case ST.LINE_UP:
			doSelect(CaretSearch.ROW, false, append);
			break;
//DOWN
		case ST.SELECT_LINE_DOWN:
			append = true;
		case ST.LINE_DOWN:
			doSelect(CaretSearch.ROW, true, append);
			break;
//TAB
		case SWT.TAB | SWT.SHIFT:
			doUnindent();
			break;
		case SWT.TAB:
			if (!doIndent())
				doTyping(event);
			break;
//ENTER
		case SWT.CR:
			if (!doNewline())
				doTyping(event);
			break;
		default:
			break;
	}
}

/**
 * @since 3.1
 * @param e
 */
private boolean doBackspace() {
	setTextInputMode(MODE_BS);
	SelectionRange range = getTextualViewer().getSelectionRange();
	if (range.isEmpty()) {
		if (handleTextEdit(new TextRequest(TextRequest.REQ_BACKSPACE, range, pendingCommand)))
			return true;
		doSelect(CaretSearch.COLUMN, false, false);
		return false;
	} else
		return handleTextEdit(new TextRequest(TextRequest.REQ_REMOVE_RANGE, range));
}

private boolean doDelete() {
	setTextInputMode(MODE_DEL);
	SelectionRange range = getTextualViewer().getSelectionRange();

	if (range.isEmpty()) {
		if (handleTextEdit(new TextRequest(TextRequest.REQ_DELETE, range, pendingCommand)))
			return true;
		doSelect(CaretSearch.COLUMN, true, false);
		return false;
	} else
		return handleTextEdit(new TextRequest(TextRequest.REQ_REMOVE_RANGE, range));
}

/**
 * @since 3.1
 */
private boolean doIndent() {
	setTextInputMode(0);
	SelectionRange range = getTextualViewer().getSelectionRange();
	TextRequest edit;
	if (range.isEmpty())
		edit = new TextRequest(TextRequest.REQ_INDENT, range);
	else
		return false;
	return handleTextEdit(edit);
}

/**
 * @since 3.1
 * @param e
 */
private boolean doInsertContent(char c) {
	setTextInputMode(MODE_TYPING);
	TextRequest edit = new TextRequest(getTextualViewer().getSelectionRange(), Character
			.toString(c), pendingCommand);
	String keys[] = new String[styleKeys.size()];
	styleKeys.toArray(keys);
	edit.setStyles(keys, styleValues.toArray());
	return handleTextEdit(edit);
}

/**
 * @since 3.1
 * @param e
 */
private void doKeyDown(KeyEvent event) {
	int action = 0;
	if (event.keyCode != 0) {
		action = lookupAction(event.keyCode | event.stateMask);
	} else {
		action = lookupAction(event.character | event.stateMask);
		if (action == 0) {
			// see if we have a control character
			if ((event.stateMask & SWT.CTRL) != 0 && (event.character >= 0)
					&& event.character <= 31) {
				// get the character from the CTRL+char sequence, the control
				// key subtracts 64 from the value of the key that it modifies
				int c = event.character + 64;
				action = lookupAction(c | event.stateMask);
			}
		}
	}

	if (action == 0)
		doTyping(event);
	else
		doAction(action, event);
}

/**
 * @since 3.1
 */
private boolean doNewline() {
	setTextInputMode(MODE_BS);
	SelectionRange range = getTextualViewer().getSelectionRange();
	TextRequest edit;
	Assert.isTrue(range.isEmpty());
	edit = new TextRequest(TextRequest.REQ_NEWLINE, range, pendingCommand);
	return handleTextEdit(edit);
}

private void doSelect(int type, boolean isForward, boolean appendSelection) {
	TextLocation caretLocation = getTextualViewer().getCaretLocation();
	SelectionRange range = getTextualViewer().getSelectionRange();

	TextLocation otherEnd;
	if (range.isForward)
		otherEnd = range.begin;
	else
		otherEnd = range.end;

	Rectangle caretBounds =getTextualViewer().getCaretBounds();
	CaretSearch search = new CaretSearch();
	search.isForward = isForward;
	search.type = type;
	search.x = caretBounds.x;
	search.y = isForward ? caretBounds.bottom() : caretBounds.y;
	search.where = getTextualViewer().getCaretLocation();
	
	TextLocation newCaretLocation = caretLocation.part.getNextLocation(search);

	if (newCaretLocation == null)
		return;
	if (appendSelection) {
		if (TextUtilities.isForward(otherEnd, newCaretLocation))
			range = new SelectionRange(otherEnd, newCaretLocation, true);
		else
			range = new SelectionRange(newCaretLocation, otherEnd, false);
		getTextualViewer().setSelectionRange(range);
	} else {
		if (search.isForward)
			getTextualViewer().setSelectionRange(new SelectionRange(newCaretLocation, newCaretLocation, true));
		else
			getTextualViewer().setSelectionRange(new SelectionRange(newCaretLocation, newCaretLocation, false));
	}
}

/**
 * @since 3.1
 * @param event
 */
private void doTyping(KeyEvent event) {
	boolean ignore = false;

	if (IS_CARBON) {
		// Ignore accelerator key combinations (we do not want to
		// insert a character in the text in this instance). Do not
		// ignore COMMAND+ALT combinations since that key sequence
		// produces characters on the mac.
		ignore = (event.stateMask ^ SWT.COMMAND) == 0
				|| (event.stateMask ^ (SWT.COMMAND | SWT.SHIFT)) == 0;
	} else {
		// Ignore accelerator key combinations (we do not want to
		// insert a character in the text in this instance). Don't
		// ignore CTRL+ALT combinations since that is the Alt Gr
		// key on some keyboards.
		ignore = (event.stateMask ^ SWT.ALT) == 0 || (event.stateMask ^ SWT.CTRL) == 0
				|| (event.stateMask ^ (SWT.ALT | SWT.SHIFT)) == 0
				|| (event.stateMask ^ (SWT.CTRL | SWT.SHIFT)) == 0;
	}
	// -ignore anything below SPACE except for line delimiter keys and tab.
	// -ignore DEL
	if (!ignore && event.character > 31 && event.character != SWT.DEL
			|| event.character == SWT.CR || event.character == SWT.LF
			|| event.character == '\t') {
		doInsertContent(event.character);
	}
}

private boolean doUnindent() {
	setTextInputMode(0);
	SelectionRange range = getTextualViewer().getSelectionRange();
	TextRequest edit;
	if (range.isEmpty())
		edit = new TextRequest(TextRequest.REQ_UNINDENT, range);
	else
		return false;
	return handleTextEdit(edit);
}

private void flushStyles() {
	styleKeys.clear();
	styleValues.clear();
}

protected String getDebugName() {
	return "TextTool";
}

private Object getSelectionStyle(String styleID, boolean isState) {
	GraphicalTextViewer viewer = getTextualViewer();
	TextRequest req = new TextRequest(TextRequest.REQ_STYLE, viewer.getSelectionRange());
	req.setStyles(new String[] {styleID}, new Object[] {null});
	EditPart target = getTextTarget(viewer, req);
	if (target == null)
		return StyleService.UNDEFINED;
	TextStyleManager manager = (TextStyleManager)target
			.getAdapter(TextStyleManager.class);
	if (isState)
		return manager.getStyleState(styleID, viewer.getSelectionRange());
	return manager.getStyleValue(styleID, viewer.getSelectionRange());	
}

public Object getStyle(String styleID) {
	for (int i = 0; i < styleKeys.size(); i++)
		if (styleID.equals(styleKeys.get(i)))
			return styleValues.get(i);
	return getSelectionStyle(styleID, false);
}

public Object getStyleState(String styleID) {
	return getSelectionStyle(styleID, true);
}

private TextualEditPart getTextTarget(GraphicalTextViewer viewer, Request request) {
	SelectionRange range = viewer.getSelectionRange();
	if (range == null)
		return null;
	EditPart target, candidate = ToolUtilities.findCommonAncestor(range.begin.part,
			range.end.part);

	target = candidate.getTargetEditPart(request);
	while (target == null && candidate != null) {
		candidate = candidate.getParent();
		target = candidate.getTargetEditPart(request);
	}
	return (TextualEditPart)target;
}

GraphicalTextViewer getTextualViewer() {
	return textViewer;
}

protected boolean handleCommandStackChanged() {
	setTextInputMode(0);
	return super.handleCommandStackChanged();
}

protected boolean handleKeyDown(KeyEvent e) {
	if (isInState(STATE_INITIAL))
		doKeyDown(e);

	return super.handleKeyDown(e);
}

protected void handleKeyTraversed(TraverseEvent event) {
	if ((event.detail == SWT.TRAVERSE_TAB_PREVIOUS || event.detail == SWT.TRAVERSE_TAB_NEXT)
			&& (event.stateMask & SWT.CTRL) == 0)
		event.doit = false;
}

protected boolean handleMove() {
	super.handleMove();
	refreshCursor();
	return true;
}

private boolean handleTextEdit(TextRequest edit) {
	GraphicalTextViewer viewer = getTextualViewer();
	EditPart target = getTextTarget(viewer, edit);
	
	Command insert = null;
	if (target != null)
		insert = target.getCommand(edit);

	if (insert == null)
		return false;

	if (pendingCommand == null || insert != pendingCommand) {
		if (!insert.canExecute())
			return false;
		executeCommand(insert);
		if (insert instanceof AppendableCommand)
			pendingCommand = (AppendableCommand)insert;
		else
			pendingCommand = null;
	} else {
		if (!pendingCommand.canExecutePending())
			return false;
		pendingCommand.executePending();
		viewer.setSelectionRange(((TextCommand)pendingCommand).getExecuteSelectionRange(viewer));
	}

	return true;
}

/**
 * @since 3.1
 * @param i
 * @return
 */
private int lookupAction(int i) {
	switch (i) {
		//Left and Right
		case SWT.ARROW_LEFT:
			return ST.COLUMN_PREVIOUS;
		case SWT.ARROW_RIGHT:
			return ST.COLUMN_NEXT;
		case SWT.ARROW_RIGHT | SWT.SHIFT:
			return ST.SELECT_COLUMN_NEXT;
		case SWT.ARROW_LEFT | SWT.SHIFT:
			return ST.SELECT_COLUMN_PREVIOUS;
		case SWT.ARROW_RIGHT | SWT.CONTROL:
			return ST.WORD_NEXT;
		case SWT.ARROW_RIGHT | SWT.CONTROL | SWT.SHIFT:
			return ST.SELECT_WORD_NEXT;
		case SWT.ARROW_LEFT| SWT.CONTROL:
			return ST.WORD_PREVIOUS;
		case SWT.ARROW_LEFT| SWT.CONTROL | SWT.SHIFT:
			return ST.SELECT_WORD_PREVIOUS;
		
		case ST.LINE_END:
			return ST.LINE_END;
		case ST.SELECT_LINE_END:
			return ST.SELECT_LINE_END;
		case ST.LINE_START:
			return ST.LINE_START;
		case ST.SELECT_LINE_START:
			return ST.SELECT_LINE_START;

		//Up and Down keys
		case SWT.ARROW_UP:
			return ST.LINE_UP;
		case SWT.ARROW_DOWN:
			return ST.LINE_DOWN;
		case ST.PAGE_UP:
			return ST.PAGE_UP;
		case ST.SELECT_LINE_UP:
			return ST.SELECT_LINE_UP;
		case ST.SELECT_LINE_DOWN:
			return ST.SELECT_LINE_DOWN;

		case SWT.DEL:
			return ST.DELETE_NEXT;
		case SWT.BS:
			return ST.DELETE_PREVIOUS;

		case SWT.TAB | SWT.SHIFT:
			return SWT.TAB | SWT.SHIFT;
		case SWT.TAB:
			return SWT.TAB;

		case SWT.LF:
		case SWT.CR:
			return SWT.CR;
		default:
			break;
	}
	return 0;
}

public void removeStyleListener(StyleListener listener) {
	Assert.isTrue(this.listener == listener);
	this.listener = null;
}

public void setDragTracker(DragTracker newDragTracker) {
	if (getDragTracker() == newDragTracker)
		return;
	setTextInputMode(0);
	super.setDragTracker(newDragTracker);
}

public void setStyle(String styleID, Object newValue) {
	
	//Check for cancellations: lookup old style and remove any pending ones
	Object oldValue = getSelectionStyle(styleID, false);
	if (newValue.equals(oldValue)) {
		int prev = styleKeys.indexOf(styleID);
		if (prev != - 1) {
			styleKeys.remove(prev);
			styleValues.remove(prev);
			return;
		}
	}

	//Try to apply immediately, pend otherwise.
	GraphicalTextViewer viewer = getTextualViewer();
	TextRequest req = new TextRequest(TextRequest.REQ_STYLE, viewer.getSelectionRange());
	//$TODO should this be all pending styles or just the recently set?
	req.setStyles(new String[] {styleID}, new Object[] {newValue});
	EditPart target = getTextTarget(viewer, req);
	Command c = target.getCommand(req);
	if (c == null) {
		int prev = styleKeys.indexOf(styleID);
		if (prev != - 1) {
			styleKeys.remove(prev);
			styleValues.remove(prev);
		}
		styleKeys.add(0, styleID);
		styleValues.add(0, styleValues);
	} else if (c.canExecute()) {
		//$TODO cleanup any pending styles?
		executeCommand(c);
	}
}

/**
 * @since 3.1
 * @param mode the new input mode
 */
private void setTextInputMode(int mode) {
	if (textInputMode != mode)
		pendingCommand = null;
	if (textInputMode != MODE_TYPING)
		flushStyles();
	textInputMode = mode;
}

}