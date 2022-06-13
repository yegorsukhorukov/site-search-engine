package ru.yegor.siteSearchEngine.model;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Date;

@Entity
@Table(name = "site")
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "status_time")
    private Timestamp statusTime;
    @Column(name = "last_error")
    private String lastError;
    private String url;
    private String name;

    public Site() {
    }

    public Site(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public Site(Integer id, Status status, Timestamp statusTime, String lastError, String url, String name) {
        this.id = id;
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = lastError;
        this.url = url;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Date getStatusTime() {
        return statusTime;
    }

    public void setStatusTime(Timestamp statusTime) {
        this.statusTime = statusTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}