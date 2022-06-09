package ru.yegor.siteSearchEngine.searchSystem;

import org.jsoup.Jsoup;
import ru.yegor.siteSearchEngine.ConnectionManager;
import ru.yegor.siteSearchEngine.lemmatizer.ConvertingTextToLemmas;
import ru.yegor.siteSearchEngine.model.Lemma;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SearchSystem {
    public List<SearchResult> getSearchResultList(String searchQuery) {
        List<SearchResult> searchResultList = new ArrayList<>();

        ConvertingTextToLemmas convertingTextToLemmas = new ConvertingTextToLemmas();
        Set<Lemma> lemmas = convertingTextToLemmas.getLemmas(searchQuery);
        List<Lemma> sortedLemmas = new ArrayList<>();
        for (Lemma lemma : lemmas) {
            sortedLemmas.add(new Lemma(lemma.getLemma(), getLemmaFrequencyFromTableLemma(lemma)));
        }
        sortedLemmas.sort(Comparator.comparing(Lemma::getFrequency));

        for (Lemma lemma : sortedLemmas) {
            System.out.println(lemma.getLemma() + " " + lemma.getFrequency());
        }

        List<Integer> pageIdListForOneLemma = new ArrayList<>(getPageIdListForFirstLemma(sortedLemmas.get(0)));

        for (int i = 1; i < sortedLemmas.size(); i++) {
            List<Integer> tempList = new ArrayList<>(getPageIdListForOtherLemmas(sortedLemmas.get(i), pageIdListForOneLemma));
            pageIdListForOneLemma.clear();
            pageIdListForOneLemma.addAll(tempList);
            tempList.clear();
        }
        List<Integer> pageIdListForAllLemmas = new ArrayList<>(pageIdListForOneLemma);

        if (!pageIdListForAllLemmas.isEmpty()) {
            List<Float> absoluteRelevanceList = new ArrayList<>();
            for (Integer integer : pageIdListForAllLemmas) {
                float absoluteRelevanceOfPage = 0;
                for (Lemma lemma : sortedLemmas) {
                    absoluteRelevanceOfPage = absoluteRelevanceOfPage + gerRankFromTableIndex(integer, lemma);
                }
                absoluteRelevanceList.add(absoluteRelevanceOfPage);
            }
            float maxAbsoluteRelevance = Collections.max(absoluteRelevanceList);

            for (int i = 0; i < pageIdListForAllLemmas.size(); i++) {
                float relativeRelevance = absoluteRelevanceList.get(i) / maxAbsoluteRelevance;
                searchResultList.add(getSearchResultFromTablePage(pageIdListForAllLemmas.get(i), sortedLemmas.get(0), relativeRelevance));
            }
        }
        searchResultList.sort(Comparator.comparing(SearchResult::getRelevance).reversed());
        return searchResultList;
    }

    private String getSnippet(String text, Lemma lemma) {
        String snippet = "";
        int indent = 50;
        if (text.indexOf(lemma.getLemma()) - indent >= 0 && text.indexOf(lemma.getLemma()) + indent < text.length()) {
            snippet = text.substring(text.indexOf(lemma.getLemma()) - indent, text.indexOf(lemma.getLemma()) + indent);
        }
        if (text.indexOf(lemma.getLemma()) - indent >= 0 && text.indexOf(lemma.getLemma()) + indent > text.length()) {
            snippet = text.substring(text.indexOf(lemma.getLemma()) - indent);
        }
        if (text.indexOf(lemma.getLemma()) - indent < 0 && text.indexOf(lemma.getLemma()) + indent < text.length()) {
            snippet = text.substring(0, text.indexOf(lemma.getLemma()) + indent);
        }
        if (text.indexOf(lemma.getLemma()) - indent < 0 && text.indexOf(lemma.getLemma()) + indent > text.length()) {
            snippet = text;
        }
        return snippet;
    }

    private SearchResult getSearchResultFromTablePage(Integer pageId, Lemma lemma, float relativeRelevance) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT * FROM search_engine.page WHERE id = ?";
        String uri = "";
        String title = "";
        String snippet = "";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setInt(1, pageId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                uri = resultSet.getString("path");
                title = Jsoup.parse(resultSet.getString("content")).select("title").text();
                String allText = Jsoup.parse(resultSet.getString("content")).text();
                snippet = "<b>" + getSnippet(allText, lemma) + "</b>";
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new SearchResult(uri, title, snippet, relativeRelevance);
    }

    private float gerRankFromTableIndex(Integer pageId, Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT `rank` FROM search_engine.index WHERE page_id = ? AND lemma_id = ?";
        float rank = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setInt(1, pageId);
            preparedStatement.setInt(2, getLemmaIdFromTableLemma(lemma));
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                rank = resultSet.getFloat("rank");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rank;
    }

    private List<Integer> getPageIdListForOtherLemmas(Lemma lemma, List<Integer> pageIdList) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT page_id FROM search_engine.index WHERE lemma_id = ? AND page_id = ?";
        List<Integer> pageIdForTwoLemmas = new ArrayList<>(pageIdList);
        try {
            for (Integer integer : pageIdList) {
                PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
                preparedStatement.setInt(1, getLemmaIdFromTableLemma(lemma));
                preparedStatement.setInt(2, integer);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (!resultSet.next()) {
                    System.out.println("Нет совпадения");
                    pageIdForTwoLemmas.remove(integer);
                } else {
                    System.out.println("Есть совпадение");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pageIdForTwoLemmas;
    }

    private List<Integer> getPageIdListForFirstLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT page_id FROM search_engine.index WHERE lemma_id = ?";
        List<Integer> pageIdForLemma = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setInt(1, getLemmaIdFromTableLemma(lemma));
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                pageIdForLemma.add(resultSet.getInt("page_id"));
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pageIdForLemma;
    }

    private int getLemmaIdFromTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT id FROM search_engine.lemma WHERE lemma = ?";
        int frequency = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setString(1, lemma.getLemma());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                frequency = resultSet.getInt("id");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return frequency;
    }

    private int getLemmaFrequencyFromTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT frequency FROM search_engine.lemma WHERE lemma = ?";
        int frequency = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setString(1, lemma.getLemma());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                frequency = resultSet.getInt("frequency");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return frequency;
    }
}