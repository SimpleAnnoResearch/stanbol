/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.rules.manager.atoms;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;

public class SameAtom extends ComparisonAtom {
    private ExpressionAtom stringFunctionAtom1;
    private ExpressionAtom stringFunctionAtom2;

    public SameAtom(ExpressionAtom stringFunctionAtom1, ExpressionAtom stringFunctionAtom2) {
        this.stringFunctionAtom1 = stringFunctionAtom1;
        this.stringFunctionAtom2 = stringFunctionAtom2;
    }

    public ExpressionAtom getStringFunctionAtom1() {
        return stringFunctionAtom1;
    }

    public ExpressionAtom getStringFunctionAtom2() {
        return stringFunctionAtom2;
    }

    @Override
    public String toString() {

        return "same(" + stringFunctionAtom1.toString() + ", " + stringFunctionAtom2.toString() + ")";

    }

    private OWLLiteral getOWLTypedLiteral(Object argument) {

        OWLDataFactory factory = OWLManager.createOWLOntologyManager().getOWLDataFactory();

        OWLLiteral owlLiteral;
        if (argument instanceof String) {
            owlLiteral = factory.getOWLLiteral((String) argument);
        } else if (argument instanceof Integer) {
            owlLiteral = factory.getOWLLiteral(((Integer) argument).intValue());
        } else if (argument instanceof Double) {
            owlLiteral = factory.getOWLLiteral(((Double) argument).doubleValue());
        } else if (argument instanceof Float) {
            owlLiteral = factory.getOWLLiteral(((Float) argument).floatValue());
        } else if (argument instanceof Boolean) {
            owlLiteral = factory.getOWLLiteral(((Boolean) argument).booleanValue());
        } else {
            owlLiteral = factory.getOWLLiteral(argument.toString());
        }

        return owlLiteral;
    }

    @Override
    public String prettyPrint() {
        return stringFunctionAtom1.prettyPrint() + "=" + stringFunctionAtom2.prettyPrint();
    }
}
