package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ConnectionConfig;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class CreatorSiteMap extends RecursiveAction
{
    private final String url;
    private final SiteEntity siteEntity;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final ConnectionConfig connect;
    private final String attr;
    private final AtomicBoolean indexingProcess;
    private static final ArrayList<String> urls = new ArrayList<>();

    @Override
    protected void compute()
    {
        if (indexingProcess.get()) {
            List<CreatorSiteMap> siteMaps = new ArrayList<>();
            Page indexingPage = new Page();
            indexingPage.setPath(attr);
            indexingPage.setSiteEntity(siteEntity);
            String content = "";
            try {
                Thread.sleep(100);
                Document document = Jsoup.connect(url).userAgent(connect.getUserAgent()).referrer(connect.getReferer()).get();
                content = document.toString();
                indexingPage.setCode(document.connection().response().statusCode());
                Elements elements = document.getElementsByTag("a");
                for (Element element : elements) {
                    String attr = element.attr("href");
                    String absUrl = element.absUrl("href");
                    if (!attr.isEmpty() && !urls.contains(absUrl) && attr.charAt(0) == '/') {
                        urls.add(absUrl);
                        CreatorSiteMap siteMap = new CreatorSiteMap(absUrl, siteEntity, pageRepository,
                                siteRepository, connect, attr, indexingProcess);
                        siteMap.fork();
                        siteMaps.add(siteMap);
                    }
                }
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
            if (!content.isEmpty() && !attr.isEmpty()) {
                indexingPage.setContent(content);
                pageRepository.save(indexingPage);
            }
            SiteEntity sitePage = siteRepository.findById(siteEntity.getId()).orElseThrow();
            sitePage.setStatusTime(Instant.now());
            siteRepository.save(sitePage);

            for (CreatorSiteMap siteMap : siteMaps)
                siteMap.join();
        }
    }

}
