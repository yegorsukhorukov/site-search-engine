package ru.yegor.siteSearchEngine.lemmatizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import ru.yegor.siteSearchEngine.model.Lemma;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConvertingTextToLemmas {
    public Set<Lemma> getLemmas(String textToGetLemmas) {
        List<String> text = convertTextToLowerCaseLettersOnly(textToGetLemmas);
        List<String> firstLemmas = new ArrayList<>();
        Set<Lemma> lemmaSet = new LinkedHashSet<>();

        try {
            LuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
            LuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();
            for (String word : text) {
                if (isCyrillicWord(word)) {
                    firstLemmas.add(russianLuceneMorphology.getNormalForms(word).get(0));
                } if (isEnglishWord(word)) {
                    firstLemmas.add(englishLuceneMorphology.getNormalForms(word).get(0));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Set<String> uniqueLemmas = new LinkedHashSet<>(firstLemmas);
        for (String key : uniqueLemmas) {
            lemmaSet.add(new Lemma(key, Collections.frequency(firstLemmas, key)));
        }
        return lemmaSet;
    }

    private boolean isCyrillicWord(String word) {
        Pattern pattern = Pattern.compile("[А-Яа-яЁё]+");
        Matcher matcher = pattern.matcher(word);
        return matcher.matches();
    }

    private boolean isEnglishWord(String word) {
        Pattern pattern = Pattern.compile("[A-Za-z]+");
        Matcher matcher = pattern.matcher(word);
        return matcher.matches();
    }

    private List<String> convertTextToLowerCaseLettersOnly(String text) {
        String textConsistingOfLettersOnly = text.trim().replaceAll("[^A-Za-zА-Яа-яЁё\\s+]", "");
        String[] splitTextBySpace = textConsistingOfLettersOnly.split("\\s+");
        List<String> lowerCaseWords = Arrays.stream(splitTextBySpace).map(word -> word.toLowerCase(new Locale("ru"))).collect(Collectors.toList());
        return removeRussianAuxiliaryPartsOfSpeech(lowerCaseWords);
    }

    private List<String> removeRussianAuxiliaryPartsOfSpeech(List<String> words) {
        String[] auxiliaryPartsOfSpeech = {"ПРЕДЛ", "СОЮЗ", "ЧАСТ", "МЕЖД"};
        List<String> rightWords = new ArrayList<>();
        String firstWordBaseForm = "";
        try {
            LuceneMorphology russianLuceneMorphology = new RussianLuceneMorphology();
            for (String word : words) {
                if (isCyrillicWord(word)) {
                    firstWordBaseForm = russianLuceneMorphology.getMorphInfo(word).get(0);
                    firstWordBaseForm = firstWordBaseForm.replaceAll("[^А-ЯЁ]", "");
                }
                if (!Arrays.asList(auxiliaryPartsOfSpeech).contains(firstWordBaseForm)) {
                    rightWords.add(word);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rightWords;
    }
}