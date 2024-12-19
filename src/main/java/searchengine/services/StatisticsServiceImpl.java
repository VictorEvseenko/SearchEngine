package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService
{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics()
    {
        List<SiteEntity> sites = siteRepository.findAll();
        TotalStatistics total = new TotalStatistics();

        total.setIndexing(true);
        total.setSites(sites.size());
        total.setPages(pageRepository.findAll().size());
        total.setLemmas(lemmaRepository.findAll().size());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for(SiteEntity siteEntity : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(siteEntity.getUrl());
            item.setName(siteEntity.getName());
            item.setStatus(String.valueOf(siteEntity.getStatus()));
            item.setStatusTime(siteEntity.getStatusTime());

            if (siteEntity.getLastError() == null)
                item.setError("");
            else
                item.setError(siteEntity.getLastError());

            item.setPages(pageRepository.findAllBySiteEntity(siteEntity).size());
            item.setLemmas(lemmaRepository.findAllBySiteEntity(siteEntity).size());
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);

        return response;
    }
}
