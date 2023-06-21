/*
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

package io.accio.sqlrewrite;

import com.google.common.collect.ImmutableList;
import io.accio.base.AccioMDL;
import io.accio.base.SessionContext;
import io.accio.base.dto.Model;
import io.accio.sqlrewrite.analyzer.Analysis;
import io.accio.sqlrewrite.analyzer.Field;
import io.accio.sqlrewrite.analyzer.Scope;
import io.accio.sqlrewrite.analyzer.StatementAnalyzer;
import io.trino.sql.tree.DereferenceExpression;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.GroupBy;
import io.trino.sql.tree.GroupingElement;
import io.trino.sql.tree.Identifier;
import io.trino.sql.tree.LongLiteral;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.NodeRef;
import io.trino.sql.tree.SimpleGroupBy;
import io.trino.sql.tree.Statement;

import java.util.Optional;

import static io.accio.sqlrewrite.analyzer.Analysis.GroupByAnalysis;
import static io.trino.sql.QueryUtil.getQualifiedName;
import static java.lang.Math.toIntExact;

/**
 * If the grouping element is a relationship field,
 * rewrite it to the corresponding model's primary key.
 * <p>
 * SELECT count(*) FROM Book GROUP BY author -> SELECT count(*) FROM Book GROUP BY author.userId
 * <p>
 * If the grouping element is a ordinal field and the corresponding SelectItem is a relationship field,
 * rewrite it to the corresponding model's primary key.
 * <p>
 * SELECT author, count(*) FROM Book GROUP BY 1 -> SELECT author, count(*) FROM Book GROUP BY author.userId
 */
public class GroupByKeyRewrite
        implements AccioRule
{
    public static final GroupByKeyRewrite GROUP_BY_KEY_REWRITE = new GroupByKeyRewrite();

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, Analysis analysis, AccioMDL accioMDL)
    {
        return (Statement) new Rewriter(accioMDL, analysis).process(root);
    }

    @Override
    public Statement apply(Statement root, SessionContext sessionContext, AccioMDL accioMDL)
    {
        Analysis analysis = StatementAnalyzer.analyze(root, sessionContext, accioMDL);
        return apply(root, sessionContext, analysis, accioMDL);
    }

    static class Rewriter
            extends BaseRewriter<Void>
    {
        private final AccioMDL accioMDL;
        private final Analysis analysis;

        Rewriter(AccioMDL accioMDL, Analysis analysis)
        {
            this.accioMDL = accioMDL;
            this.analysis = analysis;
        }

        @Override
        protected Node visitGroupBy(GroupBy node, Void context)
        {
            GroupByAnalysis groupByAnalysis = analysis.getGroupByAnalysis().get(NodeRef.of(node));
            ImmutableList.Builder<GroupingElement> builder = ImmutableList.builder();
            node.getGroupingElements().forEach(groupingElement -> {
                if (groupingElement instanceof SimpleGroupBy) {
                    rewriteSimpleGroupBy((SimpleGroupBy) groupingElement, groupByAnalysis, builder);
                }
                else {
                    builder.add(groupingElement);
                }
            });
            if (node.getLocation().isPresent()) {
                return new GroupBy(
                        node.getLocation().get(),
                        node.isDistinct(),
                        builder.build());
            }
            return new GroupBy(
                    node.isDistinct(),
                    builder.build());
        }

        protected void rewriteSimpleGroupBy(SimpleGroupBy node, GroupByAnalysis context, ImmutableList.Builder<GroupingElement> groupingElementBuilder)
        {
            ImmutableList.Builder<Expression> simpleGroupByBuilder = ImmutableList.builder();
            for (Expression expression : node.getExpressions()) {
                analysis.tryGetScope(expression)
                        .ifPresent(scope -> rewriteGroupByKeyIfNeeded(scope, expression, context, simpleGroupByBuilder));
            }
            groupingElementBuilder.add(new SimpleGroupBy(simpleGroupByBuilder.build()));
        }

        private void rewriteGroupByKeyIfNeeded(Scope scope, Expression key, GroupByAnalysis groupByAnalysis, ImmutableList.Builder<Expression> builder)
        {
            if (key instanceof LongLiteral) {
                Expression expression = groupByAnalysis.getOriginalExpressions().get(toIntExact(((LongLiteral) key).getValue()) - 1);
                Optional<Field> field = scope.getRelationType().map(relationType -> relationType.resolveFields(getQualifiedName(expression)))
                        // If it can't be resolved, it means it could be a field of a relationship or ambiguous.
                        .map(fields -> fields.size() == 1 ? fields.get(0) : null);
                if (field.isPresent() && field.get().isRelationship()) {
                    accioMDL.getModel(field.get().getType()).map(Model::getPrimaryKey)
                            .map(primaryKey -> new DereferenceExpression(expression, new Identifier(primaryKey)))
                            .ifPresent(builder::add);
                }
                builder.add(key);
            }
            else {
                scope.getRelationType().map(relationType -> relationType.resolveFields(getQualifiedName(key)).get(0))
                        .filter(Field::isRelationship)
                        .map(field -> accioMDL.getModel(field.getType()).map(Model::getPrimaryKey)
                                .map(primaryKey -> new DereferenceExpression(key, new Identifier(primaryKey)))
                                .orElseThrow(() -> new IllegalStateException("No model found for " + field.getType())))
                        .ifPresent(builder::add);
                builder.add(key);
            }
        }
    }
}