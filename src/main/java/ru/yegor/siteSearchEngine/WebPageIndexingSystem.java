package ru.yegor.siteSearchEngine;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.yegor.siteSearchEngine.lemmatizer.ConvertingTextToLemmas;
import ru.yegor.siteSearchEngine.model.Lemma;
import ru.yegor.siteSearchEngine.model.Page;

import java.io.IOException;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class WebPageIndexingSystem extends RecursiveTask<Set<Page>> {
    private static final Set<String> uniqueUrls = new TreeSet<>();
    private static final Set<String> uniqueLemmas = new TreeSet<>();

    private final String[] extensions = {".pdf", ".png", ".jpeg", ".jpg"};

    private final String urlSite;
    private final String url;

    public WebPageIndexingSystem(String urlSite, String url) {
        this.urlSite = urlSite;
        this.url = url;
    }

    public String getUrlSite() {
        return urlSite;
    }

    public String getUrl() {
        return url;
    }

    @Override
    protected Set<Page> compute() {
        Set<Page> pages = new TreeSet<>(Comparator.comparing(Page::getPath));

        try {
            List<WebPageIndexingSystem> tasks = new ArrayList<>();

            Connection.Response response = Jsoup.connect(getUrl())
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreContentType(true)
                    .execute();

            Document document = response.parse();

            String titleText = getTextFromTitleOfPage(document);
            String bodyText = getTextFromBodyOfPage(document);

            ConvertingTextToLemmas convertingTextToLemmas = new ConvertingTextToLemmas();
            Set<Lemma> titleLemmas = convertingTextToLemmas.getLemmas(titleText);
            Set<Lemma> bodyLemmas = convertingTextToLemmas.getLemmas(bodyText);

            insertIntoTableLemma(titleLemmas);
            insertIntoTableLemma(bodyLemmas);

            Page page = new Page(getUrl().substring(getUrlSite().length() - 1), response.statusCode(), document.toString());
            insertIntoTablePage(page);
            pages.add(page);

            for (Lemma lemma : titleLemmas) {
                insertIntoTableIndex(page, lemma);
            }

            for (Lemma lemma : bodyLemmas) {
                if (getIndexIdFromTableIndex(page, lemma) > 0) {
                    updateRankInTableIndex(page, lemma);
                } else insertIntoTableIndex(page, lemma);
            }

            Elements elements = document.select("a");

            for (Element element : elements) {
                String childLink = element.absUrl("href");

                if (!(uniqueUrls.contains(childLink))) {
                    if (isValidUrl(getUrl())) {
                        if (childLink.startsWith(getUrlSite()) && !(childLink.contains("#")) && !(Arrays.asList(extensions).contains(childLink.substring(childLink.lastIndexOf('.'))))) {
                            uniqueUrls.add(childLink);
                            Thread.sleep(150);
                            WebPageIndexingSystem subtask = new WebPageIndexingSystem(getUrlSite(), childLink);
                            subtask.fork();
                            tasks.add(subtask);
                        }
                    }
                }
            }
            for (WebPageIndexingSystem task : tasks) {
                pages.addAll(task.join());
            }
        } catch (HttpStatusException e) {
            insertIntoTablePage(new Page(e.getUrl().substring(getUrlSite().length() - 1), e.getStatusCode(), ""));
            pages.add(new Page(e.getUrl().substring(getUrlSite().length() - 1), e.getStatusCode(), ""));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return pages;
    }

    private void insertIntoTableIndex(Page page, Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String insertQuery = "INSERT INTO search_engine.index (page_id, lemma_id, `rank`) VALUES (?, ?, ?)";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
            preparedStatement.setInt(1, getPageIdFromTablePage(page));
            preparedStatement.setInt(2, getLemmaIdFromTableLemma(lemma));
            preparedStatement.setFloat(3, (float) 1.0 * lemma.getFrequency());
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getPageIdFromTablePage(Page page) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String checkQuery = "SELECT id FROM search_engine.page WHERE path = ?";
        int pageId = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(checkQuery);
            preparedStatement.setString(1, page.getPath());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                pageId = resultSet.getInt("id");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pageId;
    }

    private int getLemmaIdFromTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String checkQuery = "SELECT id FROM search_engine.lemma WHERE lemma = ?";
        int lemmaId = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(checkQuery);
            preparedStatement.setString(1, lemma.getLemma());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                lemmaId = resultSet.getInt("id");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lemmaId;
    }

    private int getIndexIdFromTableIndex(Page page, Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT id FROM search_engine.index WHERE page_id = ? AND lemma_id = ?";
        int indexId = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setInt(1, getPageIdFromTablePage(page));
            preparedStatement.setInt(2, getLemmaIdFromTableLemma(lemma));
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                indexId = resultSet.getInt("id");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return indexId;
    }

    private void updateRankInTableIndex(Page page, Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String updateQuery = "UPDATE search_engine.index SET `rank` = ? WHERE id = ?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(updateQuery);
            preparedStatement.setFloat(1, (float) (getRankFromTableIndex(page, lemma) + 0.8 * lemma.getFrequency()));
            preparedStatement.setInt(2, getIndexIdFromTableIndex(page, lemma));
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private float getRankFromTableIndex(Page page, Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT `rank` FROM search_engine.index WHERE page_id = ? AND lemma_id = ?";
        float rank = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setInt(1, getPageIdFromTablePage(page));
            preparedStatement.setInt(2, getLemmaIdFromTableLemma(lemma));
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
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

    private void insertIntoTableLemma(Set<Lemma> lemmas) {
        for (Lemma lemma : lemmas) {
            if (!uniqueLemmas.contains(lemma.getLemma())) {
                uniqueLemmas.add(lemma.getLemma());
                insertUniqueLemmaIntoTableLemma(lemma);
            } else {
                updateFrequencyOfLemmaInTableLemma(lemma);
            }
        }
    }

    private void insertUniqueLemmaIntoTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        if (!lemma.getLemma().equals(getLemmaWordFromTableLemma(lemma))) {
            String insertQuery = "INSERT INTO search_engine.lemma (lemma, frequency, site_id) VALUES (?, ?, ?)";
            try {
                PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(1, lemma.getLemma());
                preparedStatement.setInt(2, 1);
                preparedStatement.setInt(3, getSiteIdFromTableSite(getUrlSite()));
                preparedStatement.executeUpdate();

                preparedStatement.close();
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateFrequencyOfLemmaInTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String updateQuery = "UPDATE search_engine.lemma SET frequency = ? WHERE lemma = ?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(updateQuery);
            preparedStatement.setInt(1, getLemmaFrequencyFromTableLemma(lemma) + 1);
            preparedStatement.setString(2, lemma.getLemma());
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getLemmaWordFromTableLemma(Lemma lemma) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String checkQuery = "SELECT lemma FROM search_engine.lemma WHERE lemma = ?";
        String lemmaWord = "";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(checkQuery);
            preparedStatement.setString(1, lemma.getLemma());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                lemmaWord = resultSet.getString("lemma");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lemmaWord;
    }

    private int getSiteIdFromTableSite(String urlSite) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT id FROM search_engine.site WHERE url = ?";
        int siteId = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setString(1, urlSite);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                siteId = resultSet.getInt("id");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return siteId;
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

    private String getPathFromTablePage(Page page) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String checkQuery = "SELECT path FROM search_engine.page WHERE path = ?";
        String path = "";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(checkQuery);
            preparedStatement.setString(1, page.getPath());
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                path = resultSet.getString("path");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return path;
    }

    private void insertIntoTablePage(Page page) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        if (!page.getPath().equals(getPathFromTablePage(page))) {
            String insertQuery = "INSERT INTO search_engine.page (path, code, content) VALUES (?, ?, ?)";
            try {
                PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(1, page.getPath());
                preparedStatement.setInt(2, page.getCode());
                preparedStatement.setString(3, page.getContent());
                preparedStatement.executeUpdate();

                preparedStatement.close();
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String getTextFromTitleOfPage(Document document) {
        return document.select("title").text();
    }

    private String getTextFromBodyOfPage(Document document) {
        return document.select("body").text();
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}