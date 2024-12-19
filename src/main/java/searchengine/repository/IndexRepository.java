package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long>
{
    List<Index> findAllByPage(Page page);

    List<Index> findAllByLemma(Lemma lemma);

    Index findByLemmaAndPage(Lemma lemma, Page page);

    void deleteAllByPage(Page page);
}
