package ru.yegor.siteSearchEngine.searchSystem;

import java.util.List;

public class SearchLoader {
    private static final String searchQuery = "зеленый snapdragon samsung компас";

    public static void main(String[] args) {
        SearchSystem searchSystem = new SearchSystem();
        List<SearchResult> searchResultList = searchSystem.getSearchResultList(searchQuery);
        for (SearchResult searchResult : searchResultList) {
            System.out.println(searchResult.getUri() + "\n" +
                    searchResult.getTitle() + "\n" +
                    searchResult.getSnippet() + "\n" +
                    searchResult.getRelevance() + "\n");
        }
    }
}