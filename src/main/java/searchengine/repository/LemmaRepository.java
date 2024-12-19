package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long>
{
    Lemma findByLemmaAndSiteEntity(String lemma, SiteEntity siteEntity);

    List<Lemma> findAllBySiteEntity(SiteEntity siteEntity);
}
