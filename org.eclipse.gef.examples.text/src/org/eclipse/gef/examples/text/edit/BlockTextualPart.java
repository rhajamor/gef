/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/ 

package org.eclipse.gef.examples.text.edit;

import org.eclipse.gef.examples.text.TextLocation;

/**
 * @since 3.1
 */
public class BlockTextualPart extends CompoundTextualPart {

public BlockTextualPart(Object model) {
	super(model);
}

public TextLocation getNextLocation(CaretSearch search) {
	
	TextLocation result;
	switch (search.type) {
		case CaretSearch.ROW:
			if (search.isForward)
				result = searchLineBelow(search);
			else
				result = searchLineAbove(search);
			if (result == null && getParent() instanceof TextualEditPart)
				return getTextParent().getNextLocation(
						search.continueSearch(this, search.isForward ? getLength() : 0));
			return result;
		case CaretSearch.LINE_BOUNDARY:
			if (search.isForward)
				return searchLineEnd(search);
			return searchLineBegin(search);

		default:
			return super.getNextLocation(search);
	}
}

}
