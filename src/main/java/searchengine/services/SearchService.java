package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.PageRank;
import searchengine.dto.responses.DataSearchResponse;
import searchengine.dto.responses.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService
{
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaService lemmaService;
    private final LemmaRepository lemmaRepository;
    private final IndexingSiteService indexingSiteService;
    private final IndexRepository indexRepository;

    public ResponseEntity<Object> search(String query, String site, Integer offset, Integer limit)
    {
        List<SiteEntity> siteEntities = new ArrayList<>();
        List<Lemma> foundLemmas = new ArrayList<>();
        List<Index> indexes = new ArrayList<>();
        Map<String, Integer> lemmasInQuery = lemmaService.getLemmasMap(query);

        if (site == null)
            siteEntities = siteRepository.findAll();
        else
            siteEntities.add(siteRepository.findByUrl(site));

        for (SiteEntity siteEntity : siteEntities) {
            List<Lemma> tempLemmasForSiteEntity = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : lemmasInQuery.entrySet())
                if (indexingSiteService.haveLemmaInLemmasDB(entry.getKey(), siteEntity))
                    tempLemmasForSiteEntity.add(lemmaRepository.findByLemmaAndSiteEntity(entry.getKey(), siteEntity));
                else {
                    tempLemmasForSiteEntity.clear();
                    break;
                }

            if (tempLemmasForSiteEntity.isEmpty())
                continue;

            tempLemmasForSiteEntity = tempLemmasForSiteEntity.stream().sorted(Comparator.comparingInt(Lemma::getFrequency)).toList();

            indexes.addAll(searchIndexesByFoundLemmas(tempLemmasForSiteEntity));
            foundLemmas.addAll(tempLemmasForSiteEntity);
        }

        if (foundLemmas.isEmpty() || (indexes.isEmpty()))
            return ResponseEntity.ok().body(new SearchResponse(true, 0, Collections.emptyList()));

        List<Page> pages = indexes.stream().map(Index::getPage).toList();

        List<DataSearchResponse> dataSearchResponses = createDataSearchResponsesWithSnippets(pages, foundLemmas).stream()
                .sorted(Comparator.comparingDouble(DataSearchResponse::getRelevance).reversed()).toList();

        List<DataSearchResponse> result = new ArrayList<>();

        for (int i = limit * offset / 10; i < limit * offset / 10 + limit; i++)
            try {
                result.add(dataSearchResponses.get(i));
            } catch (IndexOutOfBoundsException ex) {
                break;
            }

        return ResponseEntity.ok().body(new SearchResponse(true, dataSearchResponses.size(), result));
    }

    private boolean checkLemmaForPageMatches(Lemma lemma, List<Index> indexes)
    {
        boolean check = false;

        for(Index index : indexes)
            if (index.getLemma().equals(lemma)) {
                check = true;
                break;
            }

        return check;
    }

    public List<Index> searchIndexesByFoundLemmas(List<Lemma> foundLemmas)
    {
        List<Index> indexes = indexRepository.findAllByLemma(foundLemmas.get(0));

        for (int i = 1; i < foundLemmas.size(); i++) {
            if (foundLemmas.get(0).getSiteEntity().equals(foundLemmas.get(i).getSiteEntity())) {
                List<Index> changeIndexes = new ArrayList<>(indexes);
                for (Index index : changeIndexes) {
                    List<Index> indexesByPage = indexRepository.findAllByPage(index.getPage());
                    if (!checkLemmaForPageMatches(foundLemmas.get(i), indexesByPage))
                        indexes.remove(index);
                }
            }
        }
        return indexes;
    }

    private List<DataSearchResponse> createDataSearchResponsesWithSnippets(List<Page> pages, List<Lemma> foundLemmas)
    {
        List<String> simpleLemmasFromSearch = new ArrayList<>(foundLemmas.stream().map(Lemma::getLemma).toList());
        List<DataSearchResponse> dataSearchResponses = new ArrayList<>();
        List<PageRank> pageRankList = sortPageByRelevance(pages, foundLemmas);

        for (PageRank pageRank : pageRankList) {
            Document doc = Jsoup.parse(pageRank.getPage().getContent());
            List<String> sentences = doc.body().getElementsMatchingOwnText("[\\p{IsCyrillic}]").stream().map(Element::text).toList();
            StringBuilder finalText = new StringBuilder();
            for (String sentence : sentences) {
                StringBuilder textFromElement = new StringBuilder(sentence);
                List<String> words = List.of(sentence.split("[\s:punct]"));
                int searchWords = 0;
                for (String word : words) {
                    String lemmaFromWord = lemmaService.getLemmaByWord(word.replaceAll("\\p{Punct}", ""));
                    if (simpleLemmasFromSearch.contains(lemmaFromWord)) {
                        markWord(textFromElement, word, 0);
                        searchWords += 1;
                    }
                }
                if (searchWords != 0)
                    finalText.append(textFromElement).append("...");
            }

            SiteEntity entity = siteRepository.findById(pageRepository.findById(pageRank.getPage().getId()).get().getSiteEntity().getId()).get();
            dataSearchResponses.add(new DataSearchResponse(entity.getUrl(), entity.getName(),
                        pageRank.getPage().getPath(), doc.title(), finalText.toString(), pageRank.getRank()));
        }
        return dataSearchResponses;
    }

    private void markWord(StringBuilder textFromElement, String word, int startPosition)
    {
        int start = textFromElement.indexOf(word, startPosition);
        if (textFromElement.indexOf("<b>", start - 3) == (start - 3)) {
            markWord(textFromElement, word, start + word.length());
            return;
        }

        int end = start + word.length();
        textFromElement.insert(start, "<b>");
        if (end == -1)
            textFromElement.insert(textFromElement.length(), "</b>");
        else
            textFromElement.insert(end + 3, "</b>");
    }

    private List<PageRank> sortPageByRelevance(List<Page> pages, List<Lemma> foundLemmas)
    {
        List<PageRank> pageRankList = new ArrayList<>();
        float maxRank = 0;

        for (Page page : pages) {
            float sum = 0;
            for (Lemma lemma : foundLemmas)
                if (page.getSiteEntity().equals(lemma.getSiteEntity()))
                    sum += indexRepository.findByLemmaAndPage(lemma, page).getCountLemma();
            pageRankList.add(new PageRank(page, sum));
            if (sum > maxRank)
                maxRank = sum;
        }

        for (PageRank pageRank : pageRankList)
            pageRank.setRank(pageRank.getRank() / maxRank);

        return pageRankList.stream().sorted(Comparator.comparingDouble(PageRank::getRank).reversed()).toList();
    }

}
