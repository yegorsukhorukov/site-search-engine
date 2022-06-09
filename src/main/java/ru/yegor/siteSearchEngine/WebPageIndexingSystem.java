package ru.yegor.siteSearchEngine;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ru.yegor.siteSearchEngine.model.Page;

import java.io.IOException;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class WebPageIndexingSystem extends RecursiveTask<Set<Page>> {
    private static final Set<String> uniqueUrls = new TreeSet<>(); //коллекция уникальных ссылок

    private final String[] extensions = {".pdf", ".png", ".jpeg", ".jpg"}; //массив расширений файлов, ссылки на которые не будут включены в итоговую карту сайта

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

            Page page = new Page(getUrl().substring(getUrlSite().length() - 1), response.statusCode(), document.toString());
            insertIntoTablePage(page);
            pages.add(page);

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
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return pages;
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

    private boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}