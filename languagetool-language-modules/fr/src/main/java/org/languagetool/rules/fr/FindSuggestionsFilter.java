/* LanguageTool, a natural language style checker 
 * Copyright (C) 2020 Jaume Ortolà
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules.fr;

import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedToken;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.language.French;
import org.languagetool.rules.AbstractFindSuggestionsFilter;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.SpellingCheckRule;
import org.languagetool.synthesis.FrenchSynthesizer;
import org.languagetool.synthesis.Synthesizer;
import org.languagetool.tagging.Tagger;
import org.languagetool.tagging.fr.FrenchTagger;
import org.languagetool.tools.StringTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FindSuggestionsFilter extends AbstractFindSuggestionsFilter {

  private static final Pattern ENDS_IN_VOWEL = Pattern.compile("[aeioué]$");
  private static final Pattern PATTERN = Pattern.compile("^[smntl]'|^(nous|vous|le|la|les|me|te|se|leur|en|y) ");

  private final SpellingCheckRule morfologikRule;
  
  public FindSuggestionsFilter() throws IOException {
      morfologikRule = French.getInstance().getDefaultSpellingRule();
  }

  @Override
  protected Tagger getTagger() {
    return FrenchTagger.INSTANCE;
  }
  
  @Override
  protected Synthesizer getSynthesizer() {
    return FrenchSynthesizer.INSTANCE;
  }

  @Override
  protected List<String> getSpellingSuggestions(AnalyzedTokenReadings atr) throws IOException {
    String w;
    if (atr.isTagged()) {
      w = StringTools.makeWrong(atr.getToken());
    } else {
      w = atr.getToken();
    }
    List<String> suggestions = new ArrayList<>();
    List<String> wordsToCheck = new ArrayList<>();
    wordsToCheck.add(w);
    if (w.endsWith("s")) {
      wordsToCheck.add(w.substring(0, w.length() - 1));
    }
    if (ENDS_IN_VOWEL.matcher(w).matches()) {
      wordsToCheck.add(w + "s");
    }
    for (String word : wordsToCheck) {
      AnalyzedTokenReadings[] atk = new AnalyzedTokenReadings[1];
      AnalyzedToken token = new AnalyzedToken(word, null, null);
      atk[0] = new AnalyzedTokenReadings(token);
      AnalyzedSentence sentence = new AnalyzedSentence(atk);
      RuleMatch[] matches = morfologikRule.match(sentence);
      if (matches.length > 0) {
        suggestions.addAll(matches[0].getSuggestedReplacements());
      }  
    }
    return suggestions;
  }
  
  @Override
  protected String cleanSuggestion(String s) {
    //remove pronouns before verbs
    String output = PATTERN.matcher(s).replaceAll("");
    //check only first element 
    output = output.split(" ")[0];
    return output;
  }

}
