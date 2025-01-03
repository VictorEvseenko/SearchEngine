package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.ConnectionConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.exceptions.IncorrectUrlForIndexing;
import searchengine.exceptions.SiteNotIndexedYet;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingSiteService
{
    private AtomicBoolean indexingProcess;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;    
    private final SitesList sitesList;
    private final ConnectionConfig connect;
    private final LemmaService lemmaService;
    private final SiteService siteService;
    
    public void startIndexing(AtomicBoolean indexingProcess)
    {
        this.indexingProcess = indexingProcess;

        try {
            refreshDBBeforeIndexing();
            fillSitesDB();
            fillPagesDB();
            fillLemmasDB();
            fillIndexDB();
        } catch (RuntimeException | InterruptedException ex) {
            log.error("Error: ", ex);
        }
        finally {
            indexingProcess.set(false);
        }
    }

    private void refreshDBBeforeIndexing()
    {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    private void fillSitesDB()
    {
        for (Site site : sitesList.getSites()) {
            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setName(site.getName());
            siteEntity.setUrl(site.getUrl());
            siteEntity.setStatusTime(Instant.now());
            siteRepository.save(siteEntity);
        }
    }

    private void fillPagesDB() throws InterruptedException
    {
        List<Thread> threads = new ArrayList<>();
        for (Site site : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
            Runnable indexSite = () -> {
                try {
                    CreatorSiteMap siteMap = new CreatorSiteMap(siteEntity.getUrl(), siteEntity,
                            pageRepository, siteRepository, connect,"", indexingProcess);
                    new ForkJoinPool().invoke(siteMap);
                } catch (RuntimeException ex) {
                    siteEntity.setStatus(Status.FAILED);
                    siteEntity.setStatusTime(Instant.now());
                    siteEntity.setLastError(ex.getMessage());
                    siteRepository.save(siteEntity);
                }
                if (!indexingProcess.get()) {
                    siteEntity.setStatus(Status.FAILED);
                    siteEntity.setLastError("Indexing stopped by User");
                } else
                    siteEntity.setStatus(Status.INDEXED);
                siteEntity.setStatusTime(Instant.now());
                siteRepository.save(siteEntity);
            };
            Thread thread = new Thread(indexSite);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads)
            thread.join();
    }

    private void fillLemmasDB()
    {
        for (Site site : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl());
            Map<String, Integer> lemmas = lemmaService.getLemmasBySiteEntityForLemmasDB(siteEntity);
            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                Lemma lemma = new Lemma();
                lemma.setSiteEntity(siteEntity);
                lemma.setLemma(entry.getKey());
                lemma.setFrequency(entry.getValue());
                lemmaRepository.save(lemma);
            }
        }
    }

    private void fillIndexDB()
    {
        List<Page> pages = pageRepository.findAll();
        for(Page page : pages) {
            Map<String, Integer> indexes = lemmaService.getLemmasMap(page.getContent());
            for (Map.Entry<String, Integer> entry : indexes.entrySet()) {
                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemmaRepository.findByLemmaAndSiteEntity(entry.getKey(), page.getSiteEntity()));
                index.setCountLemma(entry.getValue());
                indexRepository.save(index);
            }
        }
    }

    public void indexingOnePage(String url)
    {
        if(!haveSiteInTheConfig(url))
            throw new IncorrectUrlForIndexing("Такого сайта нет в конфигурационном сайте!");
        else
            if (!isIndexedSite(url))
                throw new SiteNotIndexedYet("Сайт ещё не проиндексирован. Выполните индексацию!");
            else {
                SiteEntity siteEntity = siteService.findByAbsUrl(url);
                String relativeUrl = url.substring(siteEntity.getUrl().length() - 1);

                Page indexingPage = new Page();

                if (isIndexedPage(url)) {
                    indexingPage = pageRepository.findByPathAndSiteEntity(relativeUrl, siteEntity);
                    refreshDBBeforeIndexingOnePage(indexingPage);
                } else {
                    indexingPage.setPath(relativeUrl);
                    indexingPage.setSiteEntity(siteEntity);
                }

                try {
                    Document document = Jsoup.connect(url).get();
                    indexingPage.setCode(document.connection().response().statusCode());
                    indexingPage.setContent(document.toString());
                } catch (IOException ex) {
                    log.error("Error: ", ex);
                }

                pageRepository.save(indexingPage);
                updateLemmasAndIndexDB(pageRepository.findByPathAndSiteEntity(relativeUrl, siteEntity));
            }
    }

    private boolean haveSiteInTheConfig(String url)
    {
        boolean check = false;

        for (Site site : sitesList.getSites())
            if (url.startsWith(site.getUrl())) {
                check = true;
                break;
            }

        return check;
    }

    private boolean isIndexedSite(String url)
    {
        List<SiteEntity> siteEntities = siteRepository.findAll();
        boolean check = false;

        for (SiteEntity siteEntity : siteEntities)
            if (url.startsWith(siteEntity.getUrl())) {
                check = true;
                break;
            }

        return check;
    }

    private boolean isIndexedPage(String url)
    {
        boolean check = false;
        String relativeUrl = "";
        SiteEntity savedSiteEntity = new SiteEntity();
        List<SiteEntity> siteEntities = siteRepository.findAll();
        List<Page> pages = pageRepository.findAll();

        for (SiteEntity siteEntity : siteEntities)
            if (url.startsWith(siteEntity.getUrl())) {
                relativeUrl = url.substring(siteEntity.getUrl().length() - 1);
                savedSiteEntity = siteEntity;
            }

        for (Page page : pages)
            if (page.getPath().equals(relativeUrl)&&(savedSiteEntity.equals(page.getSiteEntity()))) {
                check = true;
                break;
            }

        return check;
    }

    private void refreshDBBeforeIndexingOnePage(Page page)
    {
        List<Index> indexes = indexRepository.findAllByPage(page);
        indexRepository.deleteAllByPage(page);
        for(Index index : indexes) {
            Long lemmaId = lemmaRepository.findByLemmaAndSiteEntity(index.getLemma().getLemma(), page.getSiteEntity()).getId();
            if (lemmaRepository.findById(lemmaId).get().getFrequency() == 1) {
                lemmaRepository.deleteById(lemmaId);
                continue;
            }
            Lemma refreshedLemma = new Lemma();
            refreshedLemma.setId(lemmaId);
            refreshedLemma.setLemma(index.getLemma().getLemma());
            refreshedLemma.setSiteEntity(page.getSiteEntity());
            refreshedLemma.setFrequency(lemmaRepository.findById(lemmaId).get().getFrequency() - 1);
            lemmaRepository.save(refreshedLemma);
        }
    }

    private void updateLemmasAndIndexDB(Page page)
    {
        Map<String, Integer> lemmas = lemmaService.getLemmasMap(page.getContent());

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemma = new Lemma();
            if (haveLemmaInLemmasDB(entry.getKey(), page.getSiteEntity())) {
                lemma = lemmaRepository.findByLemmaAndSiteEntity(entry.getKey(), page.getSiteEntity());
                lemma.setFrequency(lemma.getFrequency() + 1);
            } else {
                lemma.setSiteEntity(page.getSiteEntity());
                lemma.setLemma(entry.getKey());
                lemma.setFrequency(1);
            }
            lemmaRepository.save(lemma);

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemmaRepository.findByLemmaAndSiteEntity(entry.getKey(), page.getSiteEntity()));
            index.setCountLemma(entry.getValue());
            indexRepository.save(index);
        }
    }

    public boolean haveLemmaInLemmasDB(String word, SiteEntity siteEntity)
    {
        List<Lemma> lemmas = lemmaRepository.findAllBySiteEntity(siteEntity);
        boolean check = false;

        for(Lemma lemma : lemmas)
            if (word.equals(lemma.getLemma())) {
                check = true;
                break;
            }

        return check;
    }

}
