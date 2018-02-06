/*
 * Copyright 1997-2017 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.optimisation;

import static org.ojalgo.constant.BigMath.*;
import static org.ojalgo.function.BigFunction.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import org.ojalgo.access.Structure1D.IntIndex;
import org.ojalgo.constant.BigMath;
import org.ojalgo.function.BigFunction;

public abstract class Presolvers {

    /**
     * If an expression contains at least 1 binary varibale and all non-fixed variable weights are of the same
     * sign (positive or negative) then it is possible the check the validity of "1" for each of the binary
     * variables. (Doesn't seem to work and/or is not effcetive.)
     */
    public static final ExpressionsBasedModel.Presolver BINARY_VALUE = new ExpressionsBasedModel.Presolver(100) {

        @Override
        public boolean simplify(final Expression expression, final Set<IntIndex> fixedVariables, final Function<IntIndex, Variable> variableResolver) {

            boolean didFixVariable = false;

            final Set<Variable> binaryVariables = expression.getBinaryVariables(fixedVariables);

            if (binaryVariables.size() > 0) {

                final BigDecimal fixedValue = expression.calculateFixedValue(fixedVariables);

                BigDecimal compUppLim = expression.getUpperLimit();
                if (compUppLim != null) {
                    if (fixedValue.signum() != 0) {
                        compUppLim = compUppLim.subtract(fixedValue);
                    }
                }

                BigDecimal compLowLim = expression.getLowerLimit();
                if (compLowLim != null) {
                    if (fixedValue.signum() != 0) {
                        compLowLim = compLowLim.subtract(fixedValue);
                    }
                }

                if ((compUppLim != null) && expression.isPositive(fixedVariables)) {
                    for (final Variable binVar : binaryVariables) {
                        if (expression.get(binVar).compareTo(compUppLim) > 0) {
                            binVar.setFixed(ZERO);
                            didFixVariable = true;
                        }
                    }
                } else if ((compLowLim != null) && expression.isNegative(fixedVariables)) {
                    for (final Variable binVar : binaryVariables) {
                        if (expression.get(binVar).compareTo(compLowLim) < 0) {
                            binVar.setFixed(ZERO);
                            didFixVariable = true;
                        }
                    }
                }

            }

            return didFixVariable;
        }

    };

    public static final ExpressionsBasedModel.Presolver BIGSTUFF = new ExpressionsBasedModel.Presolver(99) {

        @Override
        public boolean simplify(final Expression expression, final Set<IntIndex> fixedVariables, final Function<IntIndex, Variable> variableResolver) {

            final Map<IntIndex, BigDecimal> max = new HashMap<>();
            final Map<IntIndex, BigDecimal> min = new HashMap<>();

            BigDecimal totMax = BigMath.ZERO;
            BigDecimal totMin = BigMath.ZERO;

            for (final Entry<IntIndex, BigDecimal> tmpEntry : expression.getLinearEntrySet()) {
                final IntIndex tmpIndex = tmpEntry.getKey();
                final Variable tmpVariable = variableResolver.apply(tmpIndex);
                final BigDecimal tmpFactor = tmpEntry.getValue();
                if (tmpVariable.isFixed()) {
                    final BigDecimal fixed = tmpVariable.getValue().multiply(tmpFactor);
                    max.put(tmpIndex, fixed);
                    min.put(tmpIndex, fixed);
                } else {
                    final BigDecimal tmpUL = tmpVariable.getUpperLimit();
                    final BigDecimal tmpLL = tmpVariable.getLowerLimit();
                    if (tmpFactor.signum() == 1) {
                        final BigDecimal tmpMaxVal = tmpUL != null ? tmpUL.multiply(tmpFactor) : BigMath.VERY_POSITIVE;
                        final BigDecimal tmpMinVal = tmpLL != null ? tmpLL.multiply(tmpFactor) : BigMath.VERY_NEGATIVE;
                        max.put(tmpIndex, tmpMaxVal);
                        totMax = totMax.add(tmpMaxVal);
                        min.put(tmpIndex, tmpMinVal);
                        totMin = totMin.add(tmpMinVal);
                    } else {
                        final BigDecimal tmpMaxVal = tmpLL != null ? tmpLL.multiply(tmpFactor) : BigMath.VERY_POSITIVE;
                        final BigDecimal tmpMinVal = tmpUL != null ? tmpUL.multiply(tmpFactor) : BigMath.VERY_NEGATIVE;
                        max.put(tmpIndex, tmpMaxVal);
                        totMax = totMax.add(tmpMaxVal);
                        min.put(tmpIndex, tmpMinVal);
                        totMin = totMin.add(tmpMinVal);
                    }
                }
            }

            BigDecimal tmpExprU = expression.getUpperLimit();
            if (tmpExprU == null) {
                tmpExprU = BigMath.VERY_POSITIVE;
            }

            BigDecimal tmpExprL = expression.getLowerLimit();
            if (tmpExprL == null) {
                tmpExprL = BigMath.VERY_NEGATIVE;
            }

            for (final Entry<IntIndex, BigDecimal> tmpEntry : expression.getLinearEntrySet()) {
                final IntIndex tmpIndex = tmpEntry.getKey();
                final Variable tmpVariable = variableResolver.apply(tmpIndex);
                final BigDecimal tmpFactor = tmpEntry.getValue();

                final BigDecimal tmpRemU = tmpExprU.subtract(totMin).add(min.get(tmpIndex));
                final BigDecimal tmpRemL = tmpExprL.subtract(totMax).add(max.get(tmpIndex));

                BigDecimal tmpVarU = expression.getUpperLimit();
                if (tmpVarU == null) {
                    tmpVarU = BigMath.VERY_POSITIVE;
                }

                BigDecimal tmpVarL = expression.getLowerLimit();
                if (tmpVarL == null) {
                    tmpVarL = BigMath.VERY_NEGATIVE;
                }

                if (tmpFactor.signum() == 1) {
                    tmpVarU = tmpVarU.min(BigFunction.DIVIDE.invoke(tmpRemU, tmpFactor));
                    tmpVarL = tmpVarL.max(BigFunction.DIVIDE.invoke(tmpRemL, tmpFactor));
                } else {
                    tmpVarU = tmpVarU.min(BigFunction.DIVIDE.invoke(tmpRemL, tmpFactor));
                    tmpVarL = tmpVarL.max(BigFunction.DIVIDE.invoke(tmpRemU, tmpFactor));
                }

                if (tmpVarU.compareTo(BigMath.VERY_POSITIVE) < 0) {
                    tmpVariable.upper(tmpVarU);
                }

                if (tmpVarL.compareTo(BigMath.VERY_NEGATIVE) > 0) {
                    tmpVariable.lower(tmpVarL);
                }
            }

            return false;
        }

    };

    /**
     * Checks the sign of the limits and the sign of the expression parameters to deduce variables that in
     * fact can only zero.
     */
    public static final ExpressionsBasedModel.Presolver OPPOSITE_SIGN = new ExpressionsBasedModel.Presolver(20) {

        @Override
        public boolean simplify(final Expression expression, final Set<IntIndex> fixedVariables, final Function<IntIndex, Variable> variableResolver) {

            boolean didFixVariable = false;

            final ExpressionsBasedModel model = expression.getModel();

            final BigDecimal fixedValue = expression.calculateFixedValue(fixedVariables);

            BigDecimal tmpCompLowLim = expression.getLowerLimit();
            if ((tmpCompLowLim != null) && (fixedValue.signum() != 0)) {
                tmpCompLowLim = tmpCompLowLim.subtract(fixedValue);
            }

            BigDecimal tmpCompUppLim = expression.getUpperLimit();
            if ((tmpCompUppLim != null) && (fixedValue.signum() != 0)) {
                tmpCompUppLim = tmpCompUppLim.subtract(fixedValue);
            }

            if ((tmpCompLowLim != null) && (tmpCompLowLim.signum() >= 0) && expression.isNegative(fixedVariables)) {

                if (tmpCompLowLim.signum() == 0) {

                    for (final IntIndex tmpLinear : expression.getLinearKeySet()) {
                        if (!fixedVariables.contains(tmpLinear)) {

                            final Variable tmpFreeVariable = variableResolver.apply(tmpLinear);

                            final boolean tmpValid = tmpFreeVariable.validate(ZERO, model.options.feasibility,
                                    model.options.logger_detailed ? model.options.logger_appender : null);
                            expression.setInfeasible(!tmpValid);

                            if (tmpValid) {
                                tmpFreeVariable.setFixed(ZERO);
                                didFixVariable = true;
                            }
                        }
                    }

                    expression.setRedundant(true);

                } else {

                    expression.setInfeasible(true);
                }
            }

            if ((tmpCompUppLim != null) && (tmpCompUppLim.signum() <= 0) && expression.isPositive(fixedVariables)) {

                if (tmpCompUppLim.signum() == 0) {

                    for (final IntIndex tmpLinear : expression.getLinearKeySet()) {
                        if (!fixedVariables.contains(tmpLinear)) {
                            final Variable tmpFreeVariable = model.getVariable(tmpLinear.index);

                            final boolean tmpValid = tmpFreeVariable.validate(ZERO, model.options.feasibility,
                                    model.options.logger_detailed ? model.options.logger_appender : null);
                            expression.setInfeasible(!tmpValid);

                            if (tmpValid) {
                                tmpFreeVariable.setFixed(ZERO);
                                didFixVariable = true;
                            }
                        }
                    }

                    expression.setRedundant(true);

                } else {

                    expression.setInfeasible(true);
                }
            }

            return didFixVariable;
        }

    };

    /**
     * Looks for constraint expressions with 0, 1 or 2 non-fixed variables. Transfers the constraints of the
     * expressions to the variables and then (if possible) marks the expression as redundant.
     */
    public static final ExpressionsBasedModel.Presolver ZERO_ONE_TWO = new ExpressionsBasedModel.Presolver(10) {

        @Override
        public boolean simplify(final Expression expression, final Set<IntIndex> fixedVariables, final Function<IntIndex, Variable> variableResolver) {

            boolean didFixVariable = false;

            if (expression.countLinearFactors() <= (fixedVariables.size() + 2)) {
                // This constraint can possibly be reduced to 0, 1 or 2 remaining linear factors

                final BigDecimal fixedValue = expression.calculateFixedValue(fixedVariables);

                final HashSet<IntIndex> remainingLinear = new HashSet<>(expression.getLinearKeySet());
                remainingLinear.removeAll(fixedVariables);

                switch (remainingLinear.size()) {

                case 0:

                    didFixVariable = Presolvers.doCase0(expression, fixedValue, remainingLinear, variableResolver);
                    break;

                case 1:

                    didFixVariable = Presolvers.doCase1(expression, fixedValue, remainingLinear, variableResolver);
                    break;

                case 2:

                    didFixVariable = Presolvers.doCase2(expression, fixedValue, remainingLinear, variableResolver);
                    break;

                default:

                    break;
                }

            }

            return didFixVariable;
        }

    };

    /**
     * This constraint expression has 0 remaining free variable. It is entirely redundant.
     */
    static boolean doCase0(final Expression expression, final BigDecimal fixedValue, final HashSet<IntIndex> remaining,
            final Function<IntIndex, Variable> variableResolver) {

        expression.setRedundant(true);

        final ExpressionsBasedModel tmpModel = expression.getModel();

        final boolean tmpValid = expression.validate(fixedValue, tmpModel.options.feasibility,
                tmpModel.options.logger_detailed ? tmpModel.options.logger_appender : null);
        if (tmpValid) {
            expression.setInfeasible(false);
            expression.level(fixedValue);
        } else {
            expression.setInfeasible(true);
        }

        return false;
    }

    /**
     * This constraint expression has 1 remaining free variable. The lower/upper limits can be transferred to
     * that variable, and the expression marked as redundant.
     */
    static boolean doCase1(final Expression expression, final BigDecimal fixedValue, final HashSet<IntIndex> remaining,
            final Function<IntIndex, Variable> variableResolver) {

        final ExpressionsBasedModel tmpModel = expression.getModel();

        final IntIndex tmpIndex = remaining.iterator().next();
        final Variable tmpVariable = variableResolver.apply(tmpIndex);
        final BigDecimal tmpFactor = expression.get(tmpIndex);

        if (expression.isEqualityConstraint()) {
            // Simple case with equality constraint

            final BigDecimal tmpCompensatedLevel = SUBTRACT.invoke(expression.getUpperLimit(), fixedValue);
            final BigDecimal tmpSolutionValue = DIVIDE.invoke(tmpCompensatedLevel, tmpFactor);

            expression.setRedundant(true);

            final boolean tmpValid = tmpVariable.validate(tmpSolutionValue, tmpModel.options.feasibility,
                    tmpModel.options.logger_detailed ? tmpModel.options.logger_appender : null);
            if (tmpValid) {
                expression.setInfeasible(false);
                tmpVariable.level(tmpSolutionValue);
            } else {
                expression.setInfeasible(true);
            }

        } else {
            // More general case

            final BigDecimal tmpLowerLimit = expression.getLowerLimit();
            final BigDecimal tmpUpperLimit = expression.getUpperLimit();

            final BigDecimal tmpCompensatedLower = tmpLowerLimit != null ? SUBTRACT.invoke(tmpLowerLimit, fixedValue) : tmpLowerLimit;
            final BigDecimal tmpCompensatedUpper = tmpUpperLimit != null ? SUBTRACT.invoke(tmpUpperLimit, fixedValue) : tmpUpperLimit;

            BigDecimal tmpLowerSolution = tmpCompensatedLower != null ? DIVIDE.invoke(tmpCompensatedLower, tmpFactor) : tmpCompensatedLower;
            BigDecimal tmpUpperSolution = tmpCompensatedUpper != null ? DIVIDE.invoke(tmpCompensatedUpper, tmpFactor) : tmpCompensatedUpper;
            if (tmpFactor.signum() < 0) {
                final BigDecimal tmpVal = tmpLowerSolution;
                tmpLowerSolution = tmpUpperSolution;
                tmpUpperSolution = tmpVal;
            }

            final BigDecimal tmpOldLower = tmpVariable.getLowerLimit();
            final BigDecimal tmpOldUpper = tmpVariable.getUpperLimit();

            BigDecimal tmpNewLower = tmpOldLower;
            if (tmpLowerSolution != null) {
                if (tmpOldLower != null) {
                    tmpNewLower = tmpOldLower.max(tmpLowerSolution);
                } else {
                    tmpNewLower = tmpLowerSolution;
                }
            }

            BigDecimal tmpNewUpper = tmpOldUpper;
            if (tmpUpperSolution != null) {
                if (tmpOldUpper != null) {
                    tmpNewUpper = tmpOldUpper.min(tmpUpperSolution);
                } else {
                    tmpNewUpper = tmpUpperSolution;
                }
            }

            if (tmpVariable.isInteger()) {
                if (tmpNewLower != null) {
                    tmpNewLower = tmpNewLower.setScale(0, RoundingMode.CEILING);
                }
                if (tmpNewUpper != null) {
                    tmpNewUpper = tmpNewUpper.setScale(0, RoundingMode.FLOOR);
                }
            }

            tmpVariable.lower(tmpNewLower).upper(tmpNewUpper);
            expression.setRedundant(true);

            final boolean tmpInfeasible = (tmpNewLower != null) && (tmpNewUpper != null) && (tmpNewLower.compareTo(tmpNewUpper) > 0);
            expression.setInfeasible(tmpInfeasible);
        }

        if (tmpVariable.isEqualityConstraint()) {
            tmpVariable.setValue(tmpVariable.getLowerLimit());
            return true;
        } else {
            return false;
        }
    }

    static boolean doCase2(final Expression expression, final BigDecimal fixedValue, final HashSet<IntIndex> remaining,
            final Function<IntIndex, Variable> variableResolver) {

        final Iterator<IntIndex> tmpIterator = remaining.iterator();

        final IntIndex tmpIndexA = tmpIterator.next();
        final Variable tmpVariableA = variableResolver.apply(tmpIndexA);
        final BigDecimal tmpFactorA = expression.get(tmpIndexA);
        BigDecimal tmpLowerA = tmpVariableA.getLowerLimit();
        BigDecimal tmpUpperA = tmpVariableA.getUpperLimit();

        final IntIndex tmpIndexB = tmpIterator.next();
        final Variable tmpVariableB = variableResolver.apply(tmpIndexB);
        final BigDecimal tmpFactorB = expression.get(tmpIndexB);
        BigDecimal tmpLowerB = tmpVariableB.getLowerLimit();
        BigDecimal tmpUpperB = tmpVariableB.getUpperLimit();

        final BigDecimal tmpLowerLimit = expression.getLowerLimit() != null ? SUBTRACT.invoke(expression.getLowerLimit(), fixedValue)
                : expression.getLowerLimit();
        final BigDecimal tmpUpperLimit = expression.getUpperLimit() != null ? SUBTRACT.invoke(expression.getUpperLimit(), fixedValue)
                : expression.getUpperLimit();

        if (tmpLowerLimit != null) {

            final BigDecimal tmpOtherUpperA = tmpFactorB.signum() == 1 ? tmpVariableB.getUpperLimit() : tmpVariableB.getLowerLimit();
            final BigDecimal tmpOtherUpperB = tmpFactorA.signum() == 1 ? tmpVariableA.getUpperLimit() : tmpVariableA.getLowerLimit();

            if (tmpOtherUpperA != null) {

                final BigDecimal tmpNewLimit = DIVIDE.invoke(tmpLowerLimit.subtract(tmpFactorB.multiply(tmpOtherUpperA)), tmpFactorA);

                if (tmpFactorA.signum() == 1) {
                    // New lower limit on A
                    tmpLowerA = tmpLowerA != null ? tmpLowerA.max(tmpNewLimit) : tmpNewLimit;
                } else {
                    // New upper limit on A
                    tmpUpperA = tmpUpperA != null ? tmpUpperA.min(tmpNewLimit) : tmpNewLimit;
                }
            }

            if (tmpOtherUpperB != null) {

                final BigDecimal tmpNewLimit = DIVIDE.invoke(tmpLowerLimit.subtract(tmpFactorA.multiply(tmpOtherUpperB)), tmpFactorB);

                if (tmpFactorB.signum() == 1) {
                    // New lower limit on B
                    tmpLowerB = tmpLowerB != null ? tmpLowerB.max(tmpNewLimit) : tmpNewLimit;
                } else {
                    // New upper limit on B
                    tmpUpperB = tmpUpperB != null ? tmpUpperB.min(tmpNewLimit) : tmpNewLimit;
                }
            }
        }

        if (tmpUpperLimit != null) {

            final BigDecimal tmpOtherLowerA = tmpFactorB.signum() == 1 ? tmpVariableB.getLowerLimit() : tmpVariableB.getUpperLimit();
            final BigDecimal tmpOtherLowerB = tmpFactorA.signum() == 1 ? tmpVariableA.getLowerLimit() : tmpVariableA.getUpperLimit();

            if (tmpOtherLowerA != null) {

                final BigDecimal tmpNewLimit = DIVIDE.invoke(tmpUpperLimit.subtract(tmpFactorB.multiply(tmpOtherLowerA)), tmpFactorA);

                if (tmpFactorA.signum() == 1) {
                    // New upper limit on A
                    tmpUpperA = tmpUpperA != null ? tmpUpperA.min(tmpNewLimit) : tmpNewLimit;
                } else {
                    // New lower limit on A
                    tmpLowerA = tmpLowerA != null ? tmpLowerA.max(tmpNewLimit) : tmpNewLimit;
                }
            }

            if (tmpOtherLowerB != null) {

                final BigDecimal tmpNewLimit = DIVIDE.invoke(tmpUpperLimit.subtract(tmpFactorA.multiply(tmpOtherLowerB)), tmpFactorB);

                if (tmpFactorB.signum() == 1) {
                    // New upper limit on B
                    tmpUpperB = tmpUpperB != null ? tmpUpperB.min(tmpNewLimit) : tmpNewLimit;
                } else {
                    // New lower limit on B
                    tmpLowerB = tmpLowerB != null ? tmpLowerB.max(tmpNewLimit) : tmpNewLimit;
                }
            }
        }

        if (tmpVariableA.isInteger()) {
            if (tmpLowerA != null) {
                tmpLowerA = tmpLowerA.setScale(0, RoundingMode.CEILING);
            }
            if (tmpUpperA != null) {
                tmpUpperA = tmpUpperA.setScale(0, RoundingMode.FLOOR);
            }
        }

        if (tmpVariableB.isInteger()) {
            if (tmpLowerB != null) {
                tmpLowerB = tmpLowerB.setScale(0, RoundingMode.CEILING);
            }
            if (tmpUpperB != null) {
                tmpUpperB = tmpUpperB.setScale(0, RoundingMode.FLOOR);
            }
        }

        tmpVariableA.lower(tmpLowerA).upper(tmpUpperA);
        tmpVariableB.lower(tmpLowerB).upper(tmpUpperB);

        return tmpVariableA.isEqualityConstraint() || tmpVariableB.isEqualityConstraint();
    }

}
