/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.ast.visitors;

import com.google.common.collect.Iterables;
import com.google.j2cl.ast.AbstractRewriter;
import com.google.j2cl.ast.BinaryExpression;
import com.google.j2cl.ast.CompilationUnit;
import com.google.j2cl.ast.Expression;
import com.google.j2cl.ast.MultiExpression;
import com.google.j2cl.ast.Node;
import com.google.j2cl.ast.UnaryExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rewrites the tree to make sure that multiexpressions are never on the lhs of a side effecting
 * operator.
 */
public class NormalizeMultiExpressions {

  public static void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(new FlattenMultiExpressions());
    compilationUnit.accept(new SwitchMultiExpressionsAndSideEffectingExpressions());
  }

  private static class FlattenMultiExpressions extends AbstractRewriter {
    @Override
    public Node rewriteMultiExpression(MultiExpression multiExpression) {
      List<Expression> flattenedExpressions = new ArrayList<>();
      for (Expression expression : multiExpression.getExpressions()) {
        if (expression instanceof MultiExpression) {
          flattenedExpressions.addAll(((MultiExpression) expression).getExpressions());
        } else {
          flattenedExpressions.add(expression);
        }
      }
      return new MultiExpression(flattenedExpressions);
    }
  }

  private static class SwitchMultiExpressionsAndSideEffectingExpressions extends AbstractRewriter {
    @Override
    public Node rewriteBinaryExpression(BinaryExpression expression) {
      if (expression.getOperator().hasSideEffect()
          && expression.getLeftOperand() instanceof MultiExpression) {
        List<Expression> lhsExpressions =
            ((MultiExpression) expression.getLeftOperand()).getExpressions();
        Expression rightMostLhsExpression = Iterables.getLast(lhsExpressions);
        Expression innerExpression =
            BinaryExpression.Builder.from(expression).leftOperand(rightMostLhsExpression).build();
        return new MultiExpression(
            Iterables.concat(
                lhsExpressions.subList(0, lhsExpressions.size() - 1),
                Collections.singletonList(innerExpression)));
      }
      return expression;
    }

    @Override
    public Node rewriteUnaryExpression(UnaryExpression expression) {
      if (expression.getOperator().hasSideEffect()
          && expression.getOperand() instanceof MultiExpression) {
        List<Expression> expressions = ((MultiExpression) expression.getOperand()).getExpressions();
        Expression rightMostExpression = Iterables.getLast(expressions);
        Expression innerExpression =
            UnaryExpression.Builder.from(expression).operand(rightMostExpression).build();
        return new MultiExpression(
            Iterables.concat(
                expressions.subList(0, expressions.size() - 1),
                Collections.singletonList(innerExpression)));
      }
      return expression;
    }
  }
}