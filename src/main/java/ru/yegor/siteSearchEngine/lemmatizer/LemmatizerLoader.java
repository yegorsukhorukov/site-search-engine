package ru.yegor.siteSearchEngine.lemmatizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import ru.yegor.siteSearchEngine.model.Lemma;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class LemmatizerLoader {
    public static void main(String[] args) {
        String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа. Russian and English morphology for Java and Apache Lucene 8.7 framework based on open source dictionary from site AOT. 12000 $ # dollars.";

        try {
            LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
            List<String> wordBaseForm = luceneMorphology.getNormalForms("некоторых");
            System.out.println(wordBaseForm.get(0));
            wordBaseForm.forEach(System.out::println);

            LuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();
            List<String> invariablePartOfSpeechBaseForm = englishLuceneMorphology.getMorphInfo("on");
            System.out.println(invariablePartOfSpeechBaseForm.get(0));
            invariablePartOfSpeechBaseForm.forEach(System.out::println);

            ConvertingTextToLemmas convertingTextToLemmas = new ConvertingTextToLemmas();
            Set<Lemma> lemmas = convertingTextToLemmas.getLemmas(text);
            lemmas.forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}