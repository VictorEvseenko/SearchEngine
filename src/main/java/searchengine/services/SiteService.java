package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService
{
    private final SiteRepository siteRepository;

    public SiteEntity findByAbsUrl(String absUrl)
    {
        SiteEntity originSiteEntity = new SiteEntity();
        List<SiteEntity> siteEntities = siteRepository.findAll();

        for (SiteEntity siteEntity : siteEntities)
            if (absUrl.startsWith(siteEntity.getUrl()))
                originSiteEntity = siteEntity;

        return originSiteEntity;
    }
}
