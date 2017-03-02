package fromWikipedia;

import java.io.File;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import basics.Fact;
import basics.FactComponent;
import basics.YAGO;
import extractors.MultilingualWikipediaExtractor;
import followUp.EntityTranslator;
import followUp.FollowUpExtractor;
import fromOtherSources.PatternHardExtractor;
import fromOtherSources.WordnetExtractor;
import javatools.datatypes.FinalSet;
import javatools.filehandlers.FileLines;
import javatools.parsers.Char17;
import javatools.util.FileUtils;
import utils.MultilingualTheme;
import utils.Theme;
import utils.TitleExtractor;

/** Extracts entity description from wikipedia.
 * 
This class is part of the YAGO project at the Max Planck Institute
for Informatics/Germany and Télécom ParisTech University/France:
http://yago-knowledge.org

This class is copyright 2017 Ghazaleh Haratinezhad Torbati.

YAGO is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published
by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version.

YAGO is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License
along with YAGO.  If not, see <http://www.gnu.org/licenses/>.
*/

public class WikipediaEntityDescriptionExtractor extends MultilingualWikipediaExtractor {

  public static final MultilingualTheme WIKIPEDIAENTITYDESCRIPTIONS = new MultilingualTheme("wikipediaEntityDescriptions", 
      "Description extracted from wikipedia for entities");

  public static final MultilingualTheme WIKIPEDIAENTITYDESCRIPTIONSNEEDSTRANSLATION = new MultilingualTheme("wikipediaEntityDescriptionsNeedsTranslation", 
      "Description extracted from wikipedia for entities");


  private static Pattern firstParagraph = Pattern.compile("^(.+?)\\n(.*)");
  private static final int MINTEXTLENGTH = 15;

  public WikipediaEntityDescriptionExtractor(String language, File wikipedia) {
    super(language, wikipedia);
  }

  @Override
  public Set<Theme> input() {
    return new TreeSet<Theme>(Arrays.asList(PatternHardExtractor.TITLEPATTERNS, WordnetExtractor.PREFMEANINGS,
        RedirectExtractor.REDIRECTFACTSDIRTY.inLanguage(language)));
  }

  @Override
  public Set<Theme> output() {
    if (isEnglish())
      return (new FinalSet<>(WIKIPEDIAENTITYDESCRIPTIONS.inLanguage(language)));
    else
      return (new FinalSet<>(WIKIPEDIAENTITYDESCRIPTIONSNEEDSTRANSLATION.inLanguage(language)));
  }
  
  @Override
  public Set<FollowUpExtractor> followUp() {
    if (isEnglish()) return (Collections.emptySet());
    return (new FinalSet<FollowUpExtractor>(
        new EntityTranslator(WIKIPEDIAENTITYDESCRIPTIONSNEEDSTRANSLATION.inLanguage(this.language), WIKIPEDIAENTITYDESCRIPTIONS.inLanguage(this.language), this)));
  }

  @Override
  public void extract() throws Exception {
    TitleExtractor titleExtractor = new TitleExtractor(language);
    
    Set<String>  redirects = new HashSet<>();
    Set<Fact> redirectFacts = RedirectExtractor.REDIRECTFACTSDIRTY.inLanguage(language).factCollection().getFactsWithRelation("<redirectedFrom>");
    for(Fact f:redirectFacts) {
      String entity = titleExtractor.createTitleEntity(FactComponent.stripQuotesAndLanguage(f.getObject()));
      redirects.add(entity);
    }
    Reader in = FileUtils.getBufferedUTF8Reader(wikipedia);
    
    while(FileLines.findIgnoreCase(in, "<title>") != -1) {
      String titleEntity = titleExtractor.getTitleEntity(in);
      // If title is not a named entity or is a redirect, continue.
      if (titleEntity == null || redirects.contains(titleEntity))  continue;
      String page = FileLines.readBetween(in, "<text", "</text>");
      String description = getDescription(page);
      // Write description to themes. If the language is not English, write it to a theme that needs translation which is done in follow up extractor.
      if(description != null) {
        if(isEnglish())
          WIKIPEDIAENTITYDESCRIPTIONS.inLanguage(language).write(new Fact(titleEntity, YAGO.hasLongDescription, FactComponent.forString(description)));
        else
          WIKIPEDIAENTITYDESCRIPTIONSNEEDSTRANSLATION.inLanguage(language).write(new Fact(titleEntity, YAGO.hasLongDescription, FactComponent.forString(description)));
      }
    }
  }

  // Return the clean description.
  private static String getDescription(String page) {
    int start = page.indexOf(">");
    page = page.substring(start + 1);
    page = Char17.decodeAmpersand(page);
    page = removePatterns(page);
    // Choose the first paragraph:
    Matcher matcher = firstParagraph.matcher(page);
    if (matcher.find())
      return cleanText(matcher.group(1));
    return null;
  }
  
// Remove lines such as: {{ text... }} 
 private static String removeBrackets(String page) {
   StringBuilder result = new StringBuilder();
   int brackets = 0;
   for(int i = 0; i < page.length(); i++){
     char current = page.charAt(i);
     if(current == '{'){
       brackets++;
     }
     else if (current == '}') {
       brackets--;
     }
     else if( brackets == 0)
       result.append(current);
   }
   return result.toString().trim();
 }
 
 // Remove links to Files, Images, .... [[File:....]]
 private static String preRemoveUselessLinks(String page) {
   StringBuilder result = new StringBuilder(page);
   String removePatterns[] = {"[[File:","[[Datei", "[[Image:", "[[Bild:", "[[wp:"};
   for(int r=0; r < removePatterns.length; r++) {
     int idx = result.indexOf(removePatterns[r]);
     if(idx != -1) {
       r--;
       int brackets = 0;
       for(int i = idx ; i < result.length(); i++) {
         char current = result.charAt(i);
         if(current == '['){
           brackets++;
         }
         else if (current == ']') {
           brackets--;
         }
         else if( brackets == 0) {
           result.replace(idx, i, "");
           break;
         }
       }
       
     }
   }
  
   return result.toString().trim();
 }
 
 
//Remove parenthesis. They were not useful in description.
 private static String removeParenthesis(String page) {
  StringBuilder result = new StringBuilder();
  int parenthes = 0;
  for(int i = 0; i < page.length(); i++){
    char current = page.charAt(i);
    if(current == '('){
      parenthes++;
    }
    else if (current == ')') {
      parenthes--;
    }
    else if( parenthes == 0)
      result.append(current);
  }
  return result.toString().trim();
}


 // Cleaning the gloss before returning it as output.
 private static String cleanText(String inputText){
   // Replace links with text:
   // examlpe: "[[Cavalier|Royalists]]" replace it with "Royalists"
   //          "[[Robert Owen]]" replace it with "Robert Owen".
   inputText = inputText.replaceAll("\\[\\[[^\\]\\n]+?\\|([^\\]\\n]+?)\\]\\]", "$1");
   inputText = inputText.replaceAll("\\[\\[([^\\]\\n]+?)\\]\\]", "$1");
   inputText = inputText.replaceAll("\\{\\{[^\\}\\n]+?\\|([^\\}\\n]+?)\\}\\}", "$1");
   inputText = inputText.replaceAll("\\{\\{([^\\}\\n]+?)\\}\\}", "$1");
   
   inputText = inputText.replaceAll("\\*", "");
   inputText = inputText.replaceAll( ":{2,}", "");
   inputText = inputText.replaceAll("'{2,}", "");
   inputText = inputText.replaceAll("\\d+px", "");

   // Remove whitespace before the punctuations:
   inputText = inputText.replaceAll("\\s+([\\.!;,\\?])", "$1");
   
   // Remove Urls:
   inputText = inputText.replaceAll("\\[(http|https)://[^\\p{Zl}\\p{Zs}\\p{Zp}]+[\\p{Zl}\\p{Zs}\\p{Zp}](.*?)\\]", "$2");
   inputText = inputText.replaceAll("[\\[\\]]", "");
   
   // Remove empty parantesis
   inputText = inputText.replaceAll("\\([\\p{Zl}\\p{Zs}\\p{Zp}]*\\)", "");
   
   // Remove punctuations from the beginning of the gloss.
   inputText = inputText.replaceAll("^[\\.!;:,\\?]+", "");
   // Remove Whitespaces from beginning and end of gloss:
   inputText = inputText.replaceAll("^[\\p{Zl}\\p{Zs}\\p{Zp}\\n]+", "");
   inputText = inputText.replaceAll("[\\p{Zl}\\p{Zs}\\p{Zp}\\n]+$", "");
   // Change any more than 1 whitespace to only 1 whitespace:
   inputText = inputText.replaceAll("[\\p{Zl}\\p{Zs}\\p{Zp}\\n]+", " ");
   inputText = inputText.replaceAll("&nbsp;", " ");

   
   if(inputText.length() < MINTEXTLENGTH)
     return null;
   
   return inputText;
 }
 
 // Extracting gloss text by removing some patterns that are observed to not have clean and good information
 private static String removePatterns(String inputText) {
   // Remove everything in curly brackets.
   inputText = removeBrackets(inputText);
   // Remove everything in parenthesis:
   inputText = removeParenthesis(inputText);
   // Remove links to files and images.
   inputText = preRemoveUselessLinks(inputText);
   // Remove some language specific texts such as: see also:...
   inputText = inputText.replaceAll("(([Ss]ee [Aa]lso.*?)|(Note:)|([Ff]or more.*?)|([Ff]or specific.*?)|([Ss]ee [Tt]he)|([Ss]ee:)|(For .+?[,-] see)|([Cc]lick [Oo]n))(.*)", "");
   inputText = inputText.replaceAll("(([Ss]iehe [Aa]uch)|(Hinweis:))(.*)", "");
// This text appeared in some wikipedia articles, and has no information. Remove it.
   inputText = inputText.replaceAll("The (.*?)magic word(.*?) <nowiki>__NOGALLERY__</nowiki> is used in this category to turn off thumbnail display since this category list unfree images, the display of which is restricted to certain areas of Wikipedia.", "");
   // Remove line breake in form of <br>
   inputText = inputText.replaceAll("<br */>", "\n");
   inputText = inputText.replaceAll("<br *>", "\n");
   inputText = inputText.replaceAll("</ *br *>", "\n");
   // Remove HTML tags and what is inside them such as:
   inputText = inputText.replaceAll("<(.*?)/>", "");
   inputText = inputText.replaceAll("<table.*?>(.*?)</table>", "");
   inputText = inputText.replaceAll("<gallery>(.*?)</gallery>", "");
   inputText = inputText.replaceAll("<imagemap>(.*?)</imagemap>", "");
   inputText = inputText.replaceAll("<ref>(.*?)</ref>", "");
   inputText = inputText.replaceAll("<nowiki>(.*?)</nowiki>", "");
   // Remove patterns such as below. They appeared to be noise.
   inputText = inputText.replaceAll("<!--(.*?)-->", "");
   inputText = inputText.replaceAll("==(.*?)==", "");
   inputText = inputText.replaceAll("__(.*?)__", "");
   inputText = inputText.replaceAll("<(.*?)>", "");
   // Remove links to categories.
   inputText = inputText.replaceAll("\\[\\[Category:(.+?)\\]\\]", "");
   inputText = inputText.replaceAll("\\[\\[.{0,3}:[Cc]ategory:(.+?)\\]\\]", "");

   // Remove all white spaces at the beginning. 
   inputText = inputText.replaceAll("^[\\p{Zl}\\p{Zs}\\p{Zp}\\n]+", "");
   return inputText;
 }

}