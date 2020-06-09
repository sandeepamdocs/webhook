/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model.labels;

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Messages;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 * Boolean expression of labels.
 * 
 * @author Kohsuke Kawaguchi
 * @since  1.372
 */
public abstract class LabelExpression extends Label {
    protected LabelExpression(String name) {
        super(name);
    }

    @Override
    public String getExpression() {
        return getDisplayName();
    }

    public static class Not extends LabelExpression {
        public final Label base;

        public Not(Label base) {
            super('!'+paren(LabelOperatorPrecedence.NOT,base));
            this.base = base;
        }

        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return !base.matches(resolver);
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onNot(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.NOT;
        }
    }

    /**
     * No-op but useful for preserving the parenthesis in the user input.
     */
    public static class Paren extends LabelExpression {
        public final Label base;

        public Paren(Label base) {
            super('('+base.getExpression()+')');
            this.base = base;
        }

        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return base.matches(resolver);
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onParen(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.ATOM;
        }
    }

    /**
     * Puts the label name into a parenthesis if the given operator will have a higher precedence.
     */
    static String paren(LabelOperatorPrecedence op, Label l) {
        if (op.compareTo(l.precedence())<0)
            return '('+l.getExpression()+')';
        return l.getExpression();
    }

    public static abstract class Binary extends LabelExpression {
        public final Label lhs,rhs;

        public Binary(Label lhs, Label rhs, LabelOperatorPrecedence op) {
            super(combine(lhs, rhs, op));
            this.lhs = lhs;
            this.rhs = rhs;
        }

        private static String combine(Label lhs, Label rhs, LabelOperatorPrecedence op) {
            return paren(op,lhs)+op.str+paren(op,rhs);
        }

        /**
         * Note that we evaluate both branches of the expression all the time.
         * That is, it behaves like "a|b" not "a||b"
         */
        @Override
        public boolean matches(VariableResolver<Boolean> resolver) {
            return op(lhs.matches(resolver),rhs.matches(resolver));
        }

        protected abstract boolean op(boolean a, boolean b);
    }

    public static final class And extends Binary {
        public And(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.AND);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return a && b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onAnd(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.AND;
        }
    }

    public static final class Or extends Binary {
        public Or(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.OR);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return a || b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onOr(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.OR;
        }
    }

    public static final class Iff extends Binary {
        public Iff(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.IFF);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return a == b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onIff(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.IFF;
        }
    }

    public static final class Implies extends Binary {
        public Implies(Label lhs, Label rhs) {
            super(lhs, rhs, LabelOperatorPrecedence.IMPLIES);
        }

        @Override
        protected boolean op(boolean a, boolean b) {
            return !a || b;
        }

        @Override
        public <V, P> V accept(LabelVisitor<V, P> visitor, P param) {
            return visitor.onImplies(this, param);
        }

        @Override
        public LabelOperatorPrecedence precedence() {
            return LabelOperatorPrecedence.IMPLIES;
        }
    }

    //region Auto-Completion and Validation

    /**
     * Utility class for taking the current input value and computing a list of potential terms to match against the
     * list of defined labels.
     */
    static class AutoCompleteSeeder {
        private String source;

        AutoCompleteSeeder(String source) {
            this.source = source;
        }

        List<String> getSeeds() {
            ArrayList<String> terms = new ArrayList<>();
            boolean trailingQuote = source.endsWith("\"");
            boolean leadingQuote = source.startsWith("\"");
            boolean trailingSpace = source.endsWith(" ");

            if (trailingQuote || (trailingSpace && !leadingQuote)) {
                terms.add("");
            } else {
                if (leadingQuote) {
                    int quote = source.lastIndexOf('"');
                    if (quote == 0) {
                        terms.add(source.substring(1));
                    } else {
                        terms.add("");
                    }
                } else {
                    int space = source.lastIndexOf(' ');
                    if (space > -1) {
                        terms.add(source.substring(space+1));
                    } else {
                        terms.add(source);
                    }
                }
            }

            return terms;
        }
    }

    /**
     * Plugins may want to contribute additional restrictions on the use of specific labels for specific context items.
     * This extension point allows such restrictions.
     *
     * @since TODO
     */
    public interface LabelValidator extends ExtensionPoint {

        /**
         * Validates the use of a label within a particular context.
         *
         * @param item  The context item to be restricted by the label.
         * @param label The label that the job wants to restrict itself to.
         * @return The validation result.
         */
        @NonNull
        FormValidation check(@NonNull Item item, @NonNull Label label);

    }

    /**
     * Generates auto-completion candidates for a (partial) label.
     *
     * @param label The (partial) label for which auto-completion is being requested.
     * @return A set of auto-completion candidates.
     * @since TODO
     */
    public static AutoCompletionCandidates autoComplete(@Nullable String label) {
        AutoCompletionCandidates c = new AutoCompletionCandidates();
        Set<Label> labels = Jenkins.get().getLabels();
        List<String> queries = new AutoCompleteSeeder(label).getSeeds();
        for (String term : queries) {
            for (Label l : labels) {
                if (l.getName().startsWith(term)) {
                    c.add(l.getName());
                }
            }
        }
        return c;
    }

    /**
     * Validates a label expression.
     *
     * @param expression The expression to validate.
     * @return The validation result.
     * @since TODO
     */
    @NonNull
    public static FormValidation validate(@Nullable String expression) {
        return LabelExpression.validate(expression, null);
    }

    /**
     * Validates a label expression.
     *
     * @param expression The label expression to validate.
     * @param item       The context item (like a job or a folder), if applicable; used for potential additional
     *                   restrictions via {@link LabelValidator} instances.
     * @return The validation result.
     * @since TODO
     */
    // FIXME: Should the messages be moved, or kept where they are for backward compatibility?
    @NonNull
    public static FormValidation validate(@Nullable String expression, @CheckForNull Item item) {
        if (Util.fixEmptyAndTrim(expression) == null)
            return FormValidation.ok();
        try {
            Label.parseExpression(expression);
        } catch (ANTLRException e) {
            return FormValidation.error(e,
                    Messages.AbstractProject_AssignedLabelString_InvalidBooleanExpression(e.getMessage()));
        }
        final Jenkins j = Jenkins.get();
        Label l = j.getLabel(expression);
        if (l.isEmpty()) {
            for (LabelAtom a : l.listAtoms()) {
                if (a.isEmpty()) {
                    LabelAtom nearest = LabelAtom.findNearest(a.getName());
                    return FormValidation.warning(Messages.AbstractProject_AssignedLabelString_NoMatch_DidYouMean(a.getName(),nearest.getDisplayName()));
                }
            }
            return FormValidation.warning(Messages.AbstractProject_AssignedLabelString_NoMatch());
        }
        if (item != null) {
            if (item instanceof AbstractProject) { // Use any project-oriented label validators
                final AbstractProject<?, ?> project = (AbstractProject<?,?>) item;
                for (AbstractProject.LabelValidator v : j.getExtensionList(AbstractProject.LabelValidator.class)) {
                    FormValidation result = v.check(project, l);
                    if (!FormValidation.Kind.OK.equals(result.kind)) {
                        return result;
                    }
                }
            }
            for (LabelValidator v : j.getExtensionList(LabelValidator.class)) {
                FormValidation result = v.check(item, l);
                if (!FormValidation.Kind.OK.equals(result.kind)) {
                    return result;
                }
            }
        }
        return FormValidation.okWithMarkup(Messages.AbstractProject_LabelLink(
                j.getRootUrl(), Util.escape(l.getName()), l.getUrl(), l.getNodes().size(), l.getClouds().size())
        );
    }

    //endregion

}
