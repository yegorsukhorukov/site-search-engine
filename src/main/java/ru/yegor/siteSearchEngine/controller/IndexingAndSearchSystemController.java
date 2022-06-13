package ru.yegor.siteSearchEngine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.yegor.siteSearchEngine.ApplicationConfig;
import ru.yegor.siteSearchEngine.ConnectionManager;
import ru.yegor.siteSearchEngine.indexingSystem.WebPageIndexingSystem;
import ru.yegor.siteSearchEngine.model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@RestController
@Validated
public class IndexingAndSearchSystemController {
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private ApplicationConfig applicationConfig;

    private final ForkJoinPool pool = new ForkJoinPool();

    @GetMapping("/api/startIndexing")
    public ResponseEntity<?> startIndexing() {
        List<Site> siteList = getSiteList();
        if (siteList.stream().anyMatch(site -> site.getStatus().equals(Status.INDEXING))) {
            return new ResponseEntity<>("Индексация уже запущена", HttpStatus.BAD_REQUEST);
        } else {
            List<ApplicationConfig.SiteProp> sitePropList = applicationConfig.getSites();
            for (ApplicationConfig.SiteProp siteProp : sitePropList) {
                WebPageIndexingSystem webPageIndexingSystem = new WebPageIndexingSystem(siteProp.getUrl(), siteProp.getUrl());
                insertIntoTableSite(siteProp.getUrl(), siteProp.getName());
                pool.execute(webPageIndexingSystem);
                pool.shutdown();
                webPageIndexingSystem.join();
                updateSiteStatusInTableSite(siteProp);
            }
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }

    @GetMapping("/api/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        List<Site> siteList = getSiteList();
        if (siteList.stream().noneMatch(site -> site.getStatus().equals(Status.INDEXING))) {
            return new ResponseEntity<>("Индексация не запущена", HttpStatus.BAD_REQUEST);
        } else {
            pool.shutdownNow();
            if (pool.isShutdown()) {
                return new ResponseEntity<>("Индексация остановлена", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Не удалось остановить индексацию", HttpStatus.FORBIDDEN);
            }
        }
    }

    @PostMapping("/api/indexPage")
    public ResponseEntity<?> indexPage(@RequestBody String url) {
        List<Site> siteList = getSiteList();
        if (siteList.stream().noneMatch(site -> url.contains(site.getUrl()))) {
            return new ResponseEntity<>("Данная страница находится за пределами сайтов, указанных в конфигурационном файле", HttpStatus.BAD_REQUEST);
        } else {
            String urlSite = getUrlSite(url);
            int pageId = getPageIdFromTablePage(url);
            deleteIndexFromTableIndex(pageId);
            deletePageFromTablePage(pageId);
            ForkJoinPool pool = new ForkJoinPool();
            WebPageIndexingSystem webPageIndexingSystem = new WebPageIndexingSystem(urlSite, url);
            pool.execute(webPageIndexingSystem);
            pool.shutdown();
            webPageIndexingSystem.join();
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }

    @GetMapping("/api/statistics")
    public ResponseEntity<Statistics> getStatistics() {
        List<Site> siteList = getSiteList();
        List<Page> pageList = getPageList();
        List<Lemma> lemmaList = getLemmaList();
        Statistics statistics = new Statistics(siteList.size(), pageList.size(), lemmaList.size(), true, siteList);
        return !siteList.isEmpty() ? new ResponseEntity<>(statistics, HttpStatus.OK) : new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private void deleteIndexFromTableIndex(int siteId) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String deleteQuery = "DELETE FROM search_engine.index WHERE page_id = ?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(deleteQuery);
            preparedStatement.setInt(1, siteId);
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deletePageFromTablePage(int siteId) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String deleteQuery = "DELETE FROM search_engine.page WHERE id = ?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(deleteQuery);
            preparedStatement.setInt(1, siteId);
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getPageIdFromTablePage(String url) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT id FROM search_engine.page WHERE path = ?";
        int siteId = 0;
        String urlSite = getUrlSite(url);
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setString(1, url.substring(urlSite.length() - 1));
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

    private void updateSiteStatusInTableSite(ApplicationConfig.SiteProp siteProp) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String updateQuery = "UPDATE site SET status = ? WHERE url = ?";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(updateQuery);
            preparedStatement.setString(1, Status.INDEXED.name());
            preparedStatement.setString(2, siteProp.getUrl());
            preparedStatement.executeUpdate();

            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getUrlFromTableSite(String urlSite) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        String getQuery = "SELECT url FROM search_engine.site WHERE url = ?";
        String url = "";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(getQuery);
            preparedStatement.setString(1, urlSite);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                url = resultSet.getString("url");
            }

            resultSet.close();
            preparedStatement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return url;
    }

    private void insertIntoTableSite(String urlSite, String siteName) {
        java.sql.Connection connection = ConnectionManager.getConnection();
        if (!urlSite.equals(getUrlFromTableSite(urlSite))) {
            String insertQuery = "INSERT INTO search_engine.site (status, status_time, last_error, url, name) VALUES (?, ?, ?, ?, ?)";
            try {
                PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);
                preparedStatement.setString(1, Status.INDEXING.name());
                preparedStatement.setTimestamp(2, new Timestamp(new Date().getTime()));
                preparedStatement.setString(3, "NO_ERROR");
                preparedStatement.setString(4, urlSite);
                preparedStatement.setString(5, siteName);
                preparedStatement.executeUpdate();

                preparedStatement.close();
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String getUrlSite(String url) {
        List<ApplicationConfig.SiteProp> sitePropList = applicationConfig.getSites();
        String urlSite = "";
        for (ApplicationConfig.SiteProp siteProp : sitePropList) {
            if (url.startsWith(siteProp.getUrl())) {
                urlSite = siteProp.getUrl();
            }
        }
        return urlSite;
    }

    private List<Lemma> getLemmaList() {
        Iterable<Lemma> lemmaIterable = lemmaRepository.findAll();
        List<Lemma> lemmaList = new ArrayList<>();
        for (Lemma lemma : lemmaIterable) {
            lemmaList.add(lemma);
        }
        return lemmaList;
    }

    private List<Site> getSiteList() {
        Iterable<Site> siteIterable = siteRepository.findAll();
        List<Site> siteList = new ArrayList<>();
        for (Site site : siteIterable) {
            siteList.add(site);
        }
        return siteList;
    }

    private List<Page> getPageList() {
        Iterable<Page> pageIterable = pageRepository.findAll();
        List<Page> pageList = new ArrayList<>();
        for (Page page : pageIterable) {
            pageList.add(page);
        }
        return pageList;
    }
}