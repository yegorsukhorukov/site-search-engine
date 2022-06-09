package ru.yegor.siteSearchEngine.searchSystem;

public class SearchResult {
    private final String uri;
    private final String title;
    private final String snippet;
    private final float relevance;

    public SearchResult(String uri, String title, String snippet, float relevance) {
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }

    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public float getRelevance() {
        return relevance;
    }
}