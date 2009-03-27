/*************************************************************************
 * Copyright (c) 2009 University of Amsterdam, The Netherlands.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ************************************************************************/
package org.dancingbear.graphbrowser.editor.draw2d.figure.manipulator;

import org.dancingbear.graphbrowser.editor.draw2d.figure.manipulator.manipulations.BackGroundColorFigureManipulation;

/**
 * Manipulator of sub-graphs
 * 
 * @author Michel de Graaf
 * @author Jeroen van Schagen
 * @author Taco Witte
 */
class SubgraphFigureManipulator extends AbstractFigureManipulator {

    public SubgraphFigureManipulator() {
        addManipulator("bgcolor", new BackGroundColorFigureManipulation());
    }

}