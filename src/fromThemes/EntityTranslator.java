package fromThemes;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javatools.datatypes.FinalSet;
import basics.Fact;
import basics.FactCollection;
import basics.FactComponent;
import basics.FactSource;
import basics.FactWriter;
import basics.Theme;
import fromWikipedia.Dictionary;
import fromWikipedia.Extractor;
import fromWikipedia.InfoboxExtractor;
import fromWikipedia.WikipediaTypeExtractor;


public class EntityTranslator extends Extractor {

  
  public static final Theme TRANSLATEDFACTS = new Theme("translatedFacts", "");
  
  
  private static Map<String, Map<String, String>> allDictionaries = new HashMap<String, Map<String, String>>(); 
  static{
    for(String s:Extractor.languages){
      if(s.equals("en")) continue; 
        try {
          allDictionaries.put( s, Dictionary.get(s));
        } catch (IOException e) {
          e.printStackTrace();
        }
    }
  }
  
  @Override
  public Set<Theme> input() {
    Set<Theme> result = new TreeSet<Theme>();
    for (String s : Extractor.languages) {
      result.add(WikipediaTypeExtractor.YAGOTYPES_MAP.get(s));
      result.add(InfoboxExtractor.INFOBOXTYPES_MAP.get(s));
    }
    return result;
  }

  @Override
  public Set<Theme> output() {
    return new FinalSet<Theme>(TRANSLATEDFACTS);
    
  }
  
  protected  FactCollection loadFacts(FactSource factSource, FactCollection temp) {
    for(Fact f: factSource){
      temp.add(f);
    }
    return(temp);
  }

  @Override
  public void extract(Map<Theme, FactWriter> output, Map<Theme, FactSource> input) throws Exception {
    FactCollection translatedFacts = new FactCollection();
  
    for(String s:Extractor.languages){
      if(s.equals("en")) continue;
      Map<String, String> tempDictionary= allDictionaries.get(s);
      FactCollection temp = new FactCollection();
      loadFacts(input.get(WikipediaTypeExtractor.YAGOTYPES_MAP.get(s)), temp);
      loadFacts(input.get(InfoboxExtractor.INFOBOXTYPES_MAP.get(s)), temp);
      for(Fact f: temp){

        String translatedSubject  =  FactComponent.stripBrackets(f.getArg(1));
        String translatedObject =  FactComponent.stripBrackets(f.getArg(2));


        if(tempDictionary.containsKey( FactComponent.stripBrackets(f.getArg(1)))){
          translatedSubject = tempDictionary.get(FactComponent.stripBrackets(f.getArg(1)));
        }
        if(tempDictionary.containsKey( FactComponent.stripBrackets(f.getArg(2)))){
          translatedObject = tempDictionary.get(FactComponent.stripBrackets(f.getArg(2)));
        }
        
        translatedFacts.add(new Fact (FactComponent.forYagoEntity(translatedSubject),  f.getRelation(), FactComponent.forYagoEntity(translatedObject)));

        

      }
    }
    for(Fact f: new FactCollection(input.get(WikipediaTypeExtractor.YAGOTYPES_MAP.get("de"))))
        translatedFacts.add(f);
    
    
    for(Fact fact:translatedFacts){
      output.get(TRANSLATEDFACTS).write(fact);
    }
    
  }
  
  public static void main(String[] args) throws Exception {
    new EntityTranslator().extract(new File("D:/data2/yago2s"), null); 
  }

}
