/* LanguageTool, a natural language style checker
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool.rules.ar;
import com.fasterxml.jackson.databind.JsonSerializer;
import org.languagetool.rules.AbstractSimpleReplaceRule2;
import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedToken;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.language.Arabic;
import org.languagetool.rules.*;
import org.languagetool.synthesis.ar.ArabicSynthesizer;
import org.languagetool.tagging.ar.ArabicTagManager;
import org.languagetool.tagging.ar.ArabicTagger;
import org.languagetool.tokenizers.ArabicWordTokenizer;
import org.languagetool.tools.StringTools;

import java.util.*;
import java.util.regex.Pattern;

import static org.languagetool.rules.ar.ArabicTransVerbData.getWordsRequiringA;
import static org.languagetool.rules.ar.ArabicTransVerbData.getWordsRequiringAn;

/**
 * Check if the determiner (if any) preceding a word is:
 * <ul>
 *   <li><i>an</i> if the next word starts with a vowel
 *   <li><i>a</i> if the next word does not start with a vowel
 * </ul>
 *  This rule loads some exceptions from external files {@code det_a.txt} and
 *  {@code det_an.txt} (e.g. for <i>an hour</i>).
 * 
 * @author Daniel Naber
 */
//public class ArabicTransVerbRule extends Rule {
public class ArabicTransVerbRule extends AbstractSimpleReplaceRule2 {
  private final ArabicTagger tagger;
  private final ArabicTagManager tagmanager;
  private final ArabicWordTokenizer tokenizer;
  private final ArabicSynthesizer synthesizer;
  public static final String AR_VERB_TRANS_INDIRECT_REPLACE = "AR_VERB_TRANSITIVE_IINDIRECT";

  private static List<Map<String, SuggestionWithMessage>> wrongWords;

  private static final String FILE_NAME = "/ar/verb_trans_to_untrans2.txt";
  private static final Locale AR_LOCALE = new Locale("ar");
//  enum Determiner {
//    A, AN, A_OR_AN, UNKNOWN
//  }

 // private static final Pattern cleanupPattern = Pattern.compile("[^αa-zA-Z0-9.;,:']");

  public ArabicTransVerbRule(ResourceBundle messages) {
    super(messages, new Arabic());
    tagger = new ArabicTagger();
    tagger.enableNewStylePronounTag();
    tokenizer = new ArabicWordTokenizer();
    tagmanager = new ArabicTagManager();
    synthesizer = new ArabicSynthesizer(new Arabic());

    super.setCategory(Categories.MISC.getCategory(messages));
    setLocQualityIssueType(ITSIssueType.Misspelling);
    addExamplePair(Example.wrong("The train arrived <marker>a hour</marker> ago."),
                   Example.fixed("The train arrived <marker>an hour</marker> ago."));

    // get wrong words from resource file
    wrongWords = getWrongWords(false);

  }

  @Override
  public String getId() {
    return AR_VERB_TRANS_INDIRECT_REPLACE;
  }

  @Override
  public String getDescription() {
    return "َTransitive verbs corrected to indirect transitive";
  }

//  @Override
//  public int estimateContextForSureMatch() {
//    return 1;
//  }

  @Override

  public final List<String> getFileNames() {
    return Collections.singletonList(FILE_NAME);

  }

  @Override
  public String getShort() {
    return "أفعال متعدية بحرف، يخطئ في تعديتها";
  }

  @Override

  public String getMessage() {
    return "'$match' الفعل خاطئ في التعدية بحرف: $suggestions";
  }

  @Override
  public String getSuggestionsSeparator() {
    return " أو ";
  }

  @Override
  public Locale getLocale() {
    return AR_LOCALE;
  }
  @Override
  public RuleMatch[] match(AnalyzedSentence sentence) {
    List<RuleMatch> ruleMatches = new ArrayList<>();
    if (wrongWords.size() == 0) {
      return toRuleMatchArray(ruleMatches);
    }
    AnalyzedTokenReadings[] tokens = sentence.getTokensWithoutWhitespace();
    int prevTokenIndex = 0;
    boolean isSentenceStart;
    boolean equalsA;
    boolean equalsAn;
    for (int i = 1; i < tokens.length; i++) {  // ignoring token 0, i.e., SENT_START
      AnalyzedTokenReadings token = tokens[i];
      AnalyzedTokenReadings prevToken = prevTokenIndex > 0 ? tokens[prevTokenIndex] : null;
      String prevTokenStr = prevTokenIndex > 0 ? tokens[prevTokenIndex].getToken() : null;
      String tokenStr = token.getToken();

      isSentenceStart = prevTokenIndex == 1;
      //System.out.printf("ArabicTransVerbRule: verb %s preposition %s\n", prevTokenStr,
        //tokenStr);
      if(prevTokenStr != null) {
        // test if the first token is a verb
        boolean is_attached_verb_transitive = isAttachedTransitiveVerb(prevToken);
        // test if the preposition token is suitable for verb token (previous)
        List<String> prepositions = getProperPrepositionForTransitiveVerb(prevToken);
        //System.out.printf("ArabicTransVerbRule:(match) verb %b prepositions %s\n", prevToken,
         // Arrays.toString(prepositions.toArray()));

//        boolean is_right_preposition = isRightPreposition(prevTokenStr, tokenStr, prepositions);
        boolean is_right_preposition = isRightPreposition(token, prepositions);

        //System.out.printf("ArabicTransVerbRule: verb %b preposition %b\n", is_attached_verb_transitive,
          //is_right_preposition);
        // the verb is attached and the next token is not the suitable preposition
        // we give the coorect new form
        if (is_attached_verb_transitive && !is_right_preposition) {
//      if( is_attached_verb_transitive && ! is_right_preposition) {
          String verb = getCorrectVerbForm(tokens[prevTokenIndex]);
          // generate suggestion according to suggested prepositions
          //FIXME: test all suggestions
          String newprepostion = prepositions.get(0);
//          String preposition = getCorrectPrepositionForm(token, prevToken);
          String preposition = getCorrectPrepositionForm(newprepostion, prevToken);

          //System.out.printf("ArabicTransVerbRule: verb %s preposition %s =>  verb %s preposition %s\n", prevTokenStr,
            //tokenStr, verb, preposition);
          String replacement = verb + " " + preposition;
          String msg = "قل <suggestion>" + replacement + "</suggestion> بدلا من '" + prevTokenStr + "' لأنّ الفعل " +
            " متعد بحرف  .";
          RuleMatch match = new RuleMatch(
            this, sentence, tokens[prevTokenIndex].getStartPos(), tokens[prevTokenIndex].getEndPos(),
            tokens[prevTokenIndex].getStartPos(), token.getEndPos(), msg, "خطأ في الفعل المتعدي بحرف");
          ruleMatches.add(match);

        }
      }

      if (isAttachedTransitiveVerb(token)) {
          prevTokenIndex = i;
      }
      else {
        prevTokenIndex = 0;
      }
    }
    return toRuleMatchArray(ruleMatches);
  }

  private boolean isAttachedTransitiveVerb(AnalyzedTokenReadings mytoken) {
    String word = mytoken.getToken();
    List<AnalyzedToken> verbTokenList = mytoken.getReadings();

//    // keep the suitable postags
//    List<String> rightPostags = new ArrayList<String>();
//    List<String> replacements = new ArrayList<>();

    for (AnalyzedToken verbTok : verbTokenList) {
      String verbLemma = verbTok.getLemma();
      String verbPostag = verbTok.getPOSTag();

      // if postag is attached
      // test if verb is in the verb list
      if (verbPostag != null)// && verbPostag.endsWith("H"))
      {
        //lookup in WrongWords
//      String crt = verbLemma;
        SuggestionWithMessage verbLemmaMatch = wrongWords.get(wrongWords.size() - 1).get(verbLemma);
        // The lemma is found in the dictionnary file
        if (verbLemmaMatch != null)
          return true;

//        if (verbLemmaMatch != null) {
//          replacements = Arrays.asList(verbLemmaMatch.getSuggestion().split("\\|"));
//          System.out.printf("AravicTransVerbRule: (isAttachedTransitiveVerb) wrong word: %s, suggestion: %s\n",
//            verbLemma, Arrays.toString(replacements.toArray()));
//          return !replacements.isEmpty();
//        }
        // to be removed
//        if(getWordsRequiringA().contains(verbLemma)) {
//          rightPostags.add(verbPostag);

        //System.out.printf("ArabicTransVerbRule:(isAttachedTransitiveVerb) verb lemma %s, postag %s\n", verbLemma, verbPostag);
//          return true;
//        }
      }

    }
    return false;
  }

  /* if the word is a transitive verb, we got proper preposition inorder to test it*/
    private List<String> getProperPrepositionForTransitiveVerb(AnalyzedTokenReadings mytoken) {
    String word = mytoken.getToken();
    List<AnalyzedToken> verbTokenList = mytoken.getReadings();

    // keep the suitable postags
    List<String> rightPostags = new ArrayList<String>();
    List<String> replacements = new ArrayList<>();

    for (AnalyzedToken verbTok : verbTokenList) {
      String verbLemma = verbTok.getLemma();
      String verbPostag = verbTok.getPOSTag();

      // if postag is attached
      // test if verb is in the verb list
      if (verbPostag != null)// && verbPostag.endsWith("H"))
      {
        //lookup in WrongWords
//      String crt = verbLemma;
        SuggestionWithMessage verbLemmaMatch = wrongWords.get(wrongWords.size() - 1).get(verbLemma);
        // The lemma is found in the dictionnary file
        if (verbLemmaMatch != null) {
          replacements = Arrays.asList(verbLemmaMatch.getSuggestion().split("\\|"));
          //System.out.printf("AravicTransVerbRule: (isAttachedTransitiveVerb) wrong word: %s, suggestion: %s\n",
           // verbLemma, Arrays.toString(replacements.toArray()));
          return replacements;
        }
      }


    }
    return replacements;
  }

  private static boolean isRightPreposition(AnalyzedTokenReadings nextToken, List<String> prepositionList)
  {
    //FIXME: test if the next token  is the suitable preposition for the previous token as verbtoken
//    List<AnalyzedToken> verbTokenList = nextToken.getReadings().get(0).getLemma();

    String nextTokenStr = nextToken.getReadings().get(0).getLemma();
//    String nextTokenStr = nextToken.getToken();
    return (prepositionList.contains(nextTokenStr));
//    return (nextToken.equals("في"));
  }
  private static boolean isRightPreposition( String verbToken, String nextToken, List<String> prepositionList)
  {
    //FIXME: test if the next token  is the suitable preposition for the previous token as verbtoken
    return (prepositionList.contains(nextToken));
//    return (nextToken.equals("في"));
  }
  private  String getCorrectVerbForm(AnalyzedTokenReadings token)
  {
//    return "verben";
    return generateUnattachedNewForm(token);
  }
  private String getCorrectPrepositionForm(String prepositionLemma, AnalyzedTokenReadings prevtoken)
  {

    return generateAttachedNewForm(prepositionLemma, prevtoken);
  };

  /* generate a new form according to a specific postag*/
  private String generateNewForm(String word, String posTag, char flag)
  {
    //      // generate new from word form
    String newposTag = tagmanager.setFlag(posTag, "PRONOUN", flag);
    // FIXME: remove the specific flag for option D
    if (flag != '-')
      newposTag = tagmanager.setFlag(newposTag, "OPTION", 'D');
    // generate the new preposition according to modified postag
    AnalyzedToken prepAToken = new AnalyzedToken(word, newposTag, word);
//    String newWord = Arrays.toString(synthesizer.synthesize(prepAToken, newposTag));
    String [] newwordList = synthesizer.synthesize(prepAToken, newposTag);
    String newWord = "";
    if (newwordList.length != 0)
       newWord= newwordList[0];

    return newWord;

  }
  /* generate a new form according to a specific postag, this form is Attached*/
  private String generateAttachedNewForm(String word, String posTag, char flag)
  {
    return generateNewForm(word, posTag,flag);

  }
  /* generate a new form according to a specific postag, this form is Un-Attached*/
  private String generateUnattachedNewForm(String word, String posTag)
  {
    return generateNewForm(word, posTag,'-');
  }
  /* generate a new form according to a specific postag, this form is Un-Attached*/
  private String generateUnattachedNewForm(AnalyzedTokenReadings token)
  {
    String lemma = token.getReadings().get(0).getLemma();
    String postag = token.getReadings().get(0).getPOSTag();
    return generateNewForm(lemma, postag,'-');
  }

  /* generate a new form according to a specific postag, this form is Attached*/
  private String generateAttachedNewForm(String prepositionLemma, AnalyzedTokenReadings prevtoken)
  {
    //FIXME ; generate multiple cases
//    String lemma = token.getReadings().get(0).getLemma();
//    String postag = token.getReadings().get(0).getPOSTag();
//    String lemma = "في";
    String lemma = prepositionLemma;
    String postag = "PR-;---;---";
    String prevpostag = prevtoken.getReadings().get(0).getPOSTag();
    char flag = tagmanager.getFlag(prevpostag,"PRONOUN");
    //System.out.printf("ArabicTransVerbRule:(generateAttachedNewForm) %s %s %c\n",lemma, postag, flag );
    return generateNewForm(lemma, postag,flag);
  }
}

