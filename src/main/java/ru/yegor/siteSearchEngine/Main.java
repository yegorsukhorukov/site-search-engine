package ru.yegor.siteSearchEngine;

import ru.yegor.siteSearchEngine.model.Page;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String urlSite = "https://t-kit.ru/";

    public static void main(String[] args) {
        ForkJoinPool pool = new ForkJoinPool();

        WebPageIndexingSystem findingLinks = new WebPageIndexingSystem(urlSite, urlSite);

        pool.execute(findingLinks);

        //каждую секунду пишем в консоль информацию о состоянии пула пока задача не закончит своё выполнение
        do {
            System.out.print("******************************************\n");
            System.out.printf("Main: Parallelism: %d\n", pool.getParallelism());
            System.out.printf("Main: Active Threads: %d\n", pool.getActiveThreadCount());
            System.out.printf("Main: Task Count: %d\n", pool.getQueuedTaskCount());
            System.out.printf("Main: Steal Count: %d\n", pool.getStealCount());
            System.out.print("******************************************\n");

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (!findingLinks.isDone());

        pool.shutdown();

        Set<Page> results = findingLinks.join();
        System.out.println("Количество ссылок в карте сайта: " + results.size());

        for (Page page : results) {
            System.out.println(page.getPath() + " - " + page.getCode());
        }
    }
}
