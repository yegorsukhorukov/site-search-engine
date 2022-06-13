package ru.yegor.siteSearchEngine.model;

import java.util.List;

public class Statistics {
    private final int totalSites;
    private final int totalPages;
    private final int totalLemmas;
    private final boolean isIndexing;
    private final List<Site> siteList;

    public Statistics(int totalSites, int totalPages, int totalLemmas, boolean isIndexing, List<Site> siteList) {
        this.totalSites = totalSites;
        this.totalPages = totalPages;
        this.totalLemmas = totalLemmas;
        this.isIndexing = isIndexing;
        this.siteList = siteList;
    }

    public int getTotalSites() {
        return totalSites;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getTotalLemmas() {
        return totalLemmas;
    }

    public boolean isIndexing() {
        return isIndexing;
    }

    public List<Site> getSiteList() {
        return siteList;
    }
}
