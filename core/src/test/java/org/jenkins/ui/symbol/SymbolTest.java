package org.jenkins.ui.symbol;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SymbolTest {

    public static final String SCIENCE_PATH = "<path d=\"M13,11.33L18,18H6l5-6.67V6h2 M15.96,4H8.04C7.62,4,7.39,4.48,7.65,4.81L9,6.5v4.17L3.2,18.4C2.71,19.06,3.18,20,4,20h16 c0.82,0,1.29-0.94,0.8-1.6L15,10.67V6.5l1.35-1.69C16.61,4.48,16.38,4,15.96,4L15.96,4z\"/>";

    @Test
    @DisplayName("Get symbol should build the symbol with given attributes")
    void getSymbol() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("science")
                                           .withTitle("Title")
                                           .withTooltip("Tooltip")
                                           .withClasses("class1 class2")
                                           .withId("id")
                                           .build()
        );
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip\""));
        assertThat(symbol, containsString("class=\"class1 class2\""));
        assertThat(symbol, containsString("id=\"id\""));
    }

    @Test
    @DisplayName("Given a cached symbol, a new request should not return attributes from the cache")
    void getSymbol_cachedSymbolDoesntReturnAttributes() {
        Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withTooltip("Tooltip")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );
        String symbol = Symbol.get(new SymbolRequest.Builder().withName("science").build());

        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("<span class=\"jenkins-visually-hidden\">Title</span>")));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, not(containsString("class=\"class1 class2\"")));
        assertThat(symbol, not(containsString("id=\"id\"")));

    }

    @Test
    @DisplayName("Given a cached symbol, a new request can specify new attributes to use")
    void getSymbol_cachedSymbolAllowsSettingAllAttributes() {
        Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withTooltip("Tooltip")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("science")
                                           .withTitle("Title2")
                                           .withTooltip("Tooltip2")
                                           .withClasses("class3 class4")
                                           .withId("id2")
                                           .build()
        );

        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("<span class=\"jenkins-visually-hidden\">Title</span>")));
        assertThat(symbol, not(containsString("tooltip=\"Tooltip\"")));
        assertThat(symbol, not(containsString("class=\"class1 class2\"")));
        assertThat(symbol, not(containsString("id=\"id\"")));
        assertThat(symbol, containsString("<span class=\"jenkins-visually-hidden\">Title2</span>"));
        assertThat(symbol, containsString("tooltip=\"Tooltip2\""));
        assertThat(symbol, containsString("class=\"class3 class4\""));
        assertThat(symbol, containsString("id=\"id2\""));
    }

    /**
     * YUI tooltips require that the attribute not be set, otherwise a white rectangle will show on hover
     * TODO: This might be able to be removed when we move away from YUI tooltips to a better solution
     */
    @Test
    @DisplayName("When omitting tooltip from attributes, the symbol should not have a tooltip")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                           .withName("science")
                           .withTitle("Title")
                           .withClasses("class1 class2")
                           .withId("id")
                           .build()
        );

        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("tooltip")));
    }

    @Test
    @DisplayName("When resolving a missing symbol, a placeholder is generated instead")
    void missingSymbolDefaultsToPlaceholder() {
        String symbol = Symbol.get(new SymbolRequest.Builder()
                                           .withName("missing-icon")
                                           .build()
        );
        assertThat(symbol, not(containsString(SCIENCE_PATH)));
        assertThat(symbol, containsString(Symbol.PLACEHOLDER_MATCHER));
    }

    @Test
    @DisplayName("If tooltip is not provided symbol should never have a tooltip")
    void getSymbol_notSettingTooltipDoesntAddTooltipAttribute_evenWithAmpersand() {
        SymbolRequest.Builder builder = new SymbolRequest.Builder()
                .withName("science")
                .withTitle("Title")
                .withTooltip("With&Ampersand")
                .withClasses("class1 class2")
                .withId("id");
        String symbol = Symbol.get(builder.build());
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, containsString("tooltip"));
        // Remove tooltip
        builder.withTooltip(null);
        symbol = Symbol.get(builder.build());
        assertThat(symbol, containsString(SCIENCE_PATH));
        assertThat(symbol, not(containsString("tooltip")));
    }
}
