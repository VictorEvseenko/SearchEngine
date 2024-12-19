package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long>
{
    List<Page> findAllBySiteEntity(SiteEntity siteEntity);

    Page findByPathAndSiteEntity(String path, SiteEntity siteEntity);
}
